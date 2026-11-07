package app.trazo.gpx.osm

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.RouteGeometry
import app.trazo.gpx.TrackStatsCalculator
import java.util.Locale
import kotlin.math.cos

enum class PoiKind(val label: String, val emoji: String, val color: String) {
    WATER("Fuente", "💧", "#1E88E5"),
    SPRING("Manantial", "💧", "#4FC3F7"),
    SHELTER("Refugio", "🛖", "#8D6E63"),
    HUT("Refugio guardado", "🏠", "#6D4C41"),
}

data class Poi(val kind: PoiKind, val name: String?, val lat: Double, val lon: Double)

/** Un punto de interés a lo largo de la ruta: en qué km queda y a cuánto del track. */
data class PoiOnRoute(val poi: Poi, val alongMeters: Double, val offsetMeters: Double)

/** Fuentes, manantiales y refugios de OpenStreetMap cerca de la ruta. */
object Pois {

    /** Recuadros pequeños a lo largo del track, como en [Difficulty.query]. */
    fun query(segments: List<List<GeoPoint>>, marginMeters: Double = 200.0): String {
        val pts = RouteGeometry.downsample(segments.flatten(), 400)
        val boxes = pts.chunked(40).map { chunk ->
            val lat = chunk.map { it.lat }
            val lon = chunk.map { it.lon }
            val dLat = marginMeters / 110_540
            val dLon = marginMeters / (111_320 * cos(Math.toRadians(lat.average())))
            String.format(Locale.ROOT, "%.5f,%.5f,%.5f,%.5f", lat.min() - dLat, lon.min() - dLon, lat.max() + dLat, lon.max() + dLon)
        }
        val parts = boxes.joinToString("") { b ->
            "node[\"amenity\"=\"drinking_water\"]($b);node[\"natural\"=\"spring\"]($b);" +
                "nwr[\"amenity\"=\"shelter\"]($b);nwr[\"tourism\"~\"^(alpine_hut|wilderness_hut)$\"]($b);"
        }
        return "[out:json][timeout:25];($parts);out center tags;"
    }

    fun parse(json: String): List<Poi> {
        val root = Json.parse(json) as? Map<*, *> ?: return emptyList()
        return (root["elements"] as? List<*>).orEmpty().mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            val tags = m["tags"] as? Map<*, *> ?: return@mapNotNull null
            val center = m["center"] as? Map<*, *>
            val lat = (m["lat"] ?: center?.get("lat")) as? Double ?: return@mapNotNull null
            val lon = (m["lon"] ?: center?.get("lon")) as? Double ?: return@mapNotNull null
            val kind = when {
                tags["tourism"] == "alpine_hut" -> PoiKind.HUT
                tags["tourism"] == "wilderness_hut" || tags["amenity"] == "shelter" -> PoiKind.SHELTER
                tags["amenity"] == "drinking_water" -> PoiKind.WATER
                tags["natural"] == "spring" -> PoiKind.SPRING
                else -> return@mapNotNull null
            }
            // Las fuentes marcadas como no potables no sirven para beber
            if (tags["drinking_water"] == "no" && kind != PoiKind.SHELTER && kind != PoiKind.HUT) return@mapNotNull null
            Poi(kind, tags["name"] as? String, lat, lon)
        }.distinctBy { "${it.kind}${(it.lat * 1e5).toLong()}${(it.lon * 1e5).toLong()}" }
    }

    /**
     * Coloca cada punto en la ruta (km donde te pilla más cerca) y descarta
     * los que quedan a más de [maxOffsetMeters]. Ordenados por km.
     */
    fun alongRoute(pois: List<Poi>, segments: List<List<GeoPoint>>, maxOffsetMeters: Double = 150.0): List<PoiOnRoute> {
        val pts = segments.flatMap { RouteGeometry.downsample(it, 3000) }
        if (pts.isEmpty()) return emptyList()
        val along = DoubleArray(pts.size)
        for (i in 1 until pts.size) along[i] = along[i - 1] + TrackStatsCalculator.haversineMeters(pts[i - 1], pts[i])
        return pois.mapNotNull { p ->
            val gp = GeoPoint(p.lat, p.lon)
            var best = Double.MAX_VALUE
            var at = 0.0
            for (i in 1 until pts.size) {
                val d = Difficulty.distanceToLine(gp, listOf(pts[i - 1], pts[i]))
                if (d < best) { best = d; at = along[i - 1] }
            }
            if (best <= maxOffsetMeters) PoiOnRoute(p, at, best) else null
        }.sortedBy { it.alongMeters }
    }
}
