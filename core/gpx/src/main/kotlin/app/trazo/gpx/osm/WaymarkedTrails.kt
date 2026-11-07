package app.trazo.gpx.osm

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.RouteGeometry
import app.trazo.gpx.TrackStatsCalculator
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

/**
 * API de Waymarked Trails (la que usa su web): rutas de OSM ya procesadas en
 * su base de datos, así que listar las de una zona es casi instantáneo,
 * a diferencia de Overpass. Trabaja en coordenadas Web Mercator (EPSG:3857).
 * Los ids son los de las relaciones de OSM.
 */
object WaymarkedTrails {

    enum class Flavour(val host: String, val mtb: Boolean) {
        MTB("mtb", true),
        CYCLING("cycling", false),
        HIKING("hiking", false),
    }

    /** Qué capas de rutas se miran según la actividad. */
    fun flavoursFor(activity: app.trazo.gpx.Activity): List<Flavour> = when (activity) {
        app.trazo.gpx.Activity.BIKE -> listOf(Flavour.MTB, Flavour.CYCLING)
        app.trazo.gpx.Activity.HIKE -> listOf(Flavour.HIKING)
    }

    data class Listed(val id: Long, val name: String, val ref: String?)

    private const val R = 6_378_137.0

    fun toMerc(lat: Double, lon: Double): Pair<Double, Double> =
        R * Math.toRadians(lon) to R * ln(tan(PI / 4 + Math.toRadians(lat) / 2))

    fun fromMerc(x: Double, y: Double): GeoPoint =
        GeoPoint(Math.toDegrees(2 * atan(exp(y / R)) - PI / 2), Math.toDegrees(x / R))

    fun mercBbox(b: Bbox): String {
        val (x1, y1) = toMerc(b.south, b.west)
        val (x2, y2) = toMerc(b.north, b.east)
        return String.format(Locale.ROOT, "%.1f,%.1f,%.1f,%.1f", x1, y1, x2, y2)
    }

    private fun base(f: Flavour) = "https://${f.host}.waymarkedtrails.org/api/v1"

    fun byAreaUrl(f: Flavour, b: Bbox, limit: Int = 100) = "${base(f)}/list/by_area?bbox=${mercBbox(b)}&limit=$limit"

    fun segmentsUrl(f: Flavour, mercBbox: String, ids: List<Long>) =
        "${base(f)}/list/segments?bbox=$mercBbox&relations=${ids.joinToString(",")}"

    fun detailsUrl(f: Flavour, id: Long) = "${base(f)}/details/relation/$id"

    /** Busca rutas por nombre (o referencia, p. ej. "PR-M 13"). Devuelve el mismo formato que [byAreaUrl]. */
    fun searchUrl(f: Flavour, query: String, limit: Int = 15) =
        "${base(f)}/list/search?limit=$limit&query=" + java.net.URLEncoder.encode(query.trim(), "UTF-8")

    /** Centro de un recuadro Mercator "x1,y1,x2,y2" (el de [parseDetails]), en grados. */
    fun mercCenter(mercBbox: String): GeoPoint? {
        val v = mercBbox.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        if (v.size != 4) return null
        return fromMerc((v[0] + v[2]) / 2, (v[1] + v[3]) / 2)
    }

    fun parseList(json: String): List<Listed> {
        val root = Json.parse(json) as? Map<*, *> ?: return emptyList()
        return (root["results"] as? List<*>).orEmpty().mapNotNull { r ->
            val m = r as? Map<*, *> ?: return@mapNotNull null
            val id = (m["id"] as? Double)?.toLong() ?: return@mapNotNull null
            val ref = m["ref"] as? String
            Listed(id, (m["name"] as? String) ?: ref ?: "Ruta $id", ref)
        }
    }

    /** Trazados por id de relación (líneas sueltas, sin ordenar). */
    fun parseSegments(json: String): Map<Long, List<List<GeoPoint>>> {
        val root = Json.parse(json) as? Map<*, *> ?: return emptyMap()
        val out = mutableMapOf<Long, MutableList<List<GeoPoint>>>()
        for (f in (root["features"] as? List<*>).orEmpty()) {
            val feat = f as? Map<*, *> ?: continue
            val id = (feat["id"] as? Double)?.toLong() ?: continue
            val g = feat["geometry"] as? Map<*, *> ?: continue
            val coords = g["coordinates"] as? List<*> ?: continue
            val lines: List<List<*>> = when (g["type"]) {
                "LineString" -> listOf(coords)
                "MultiLineString" -> coords.filterIsInstance<List<*>>()
                else -> emptyList()
            }
            for (line in lines) {
                val pts = line.mapNotNull { c ->
                    val xy = c as? List<*> ?: return@mapNotNull null
                    val x = xy.getOrNull(0) as? Double ?: return@mapNotNull null
                    val y = xy.getOrNull(1) as? Double ?: return@mapNotNull null
                    fromMerc(x, y)
                }
                if (pts.size >= 2) out.getOrPut(id) { mutableListOf() }.add(pts)
            }
        }
        return out
    }

    /** Recuadro de la ruta entera (ya en Mercator, listo para [segmentsUrl]) y su descripción. */
    fun parseDetails(json: String): Pair<String?, String?> {
        val root = Json.parse(json) as? Map<*, *> ?: return null to null
        val bbox = (root["bbox"] as? List<*>)?.mapNotNull { it as? Double }
            ?.takeIf { it.size == 4 }
            ?.joinToString(",") { String.format(Locale.ROOT, "%.1f", it) }
        return bbox to (root["description"] as? String ?: root["note"] as? String)
    }

    /**
     * Une lo listado con su trazado: el icono va a mitad del tramo más largo que se ve.
     * Con [area] (la zona pedida) también se mira si cada ruta es circular o lineal.
     */
    fun summaries(f: Flavour, listed: List<Listed>, segments: Map<Long, List<List<GeoPoint>>>, area: Bbox? = null): List<OsmRouteSummary> =
        listed.mapNotNull { l ->
            val lines = segments[l.id] ?: return@mapNotNull null
            val longest = lines.maxByOrNull { TrackStatsCalculator.compute(listOf(it)).distanceMeters } ?: return@mapNotNull null
            val half = TrackStatsCalculator.compute(listOf(longest)).distanceMeters / 2
            val c = RouteGeometry.pointAt(listOf(longest), half) ?: longest.first()
            OsmRouteSummary(
                l.id, l.name, l.ref, null, c.lat, c.lon, distanceKm = null, description = null, isMtb = f.mtb,
                shape = if (area != null) RouteShapes.of(lines, area) else RouteShape.UNKNOWN,
            )
        }
}
