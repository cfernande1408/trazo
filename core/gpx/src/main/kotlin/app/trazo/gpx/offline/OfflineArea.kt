package app.trazo.gpx.offline

import app.trazo.gpx.Bounds
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.RouteGeometry
import app.trazo.gpx.TrackStatsCalculator
import app.trazo.gpx.osm.Json
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Lo que se guarda con cada mapa descargado para reconocerlo luego. */
data class OfflineMeta(val name: String, val routeKey: String, val withTopo: Boolean, val createdAtMs: Long, val bytes: Long = 0) {
    fun encode(): String =
        """{"name":"${app.trazo.gpx.GeoJson.escape(name)}","key":"$routeKey","topo":$withTopo,"at":$createdAtMs}"""

    companion object {
        fun decode(s: String): OfflineMeta? = runCatching {
            val m = Json.parse(s) as Map<*, *>
            OfflineMeta(m["name"] as String, m["key"] as String, m["topo"] as Boolean, (m["at"] as Double).toLong())
        }.getOrNull()
    }
}

/**
 * Zona a descargar alrededor de una ruta y cuánto ocupará, para decirlo
 * antes de empezar.
 */
object OfflineArea {

    const val MIN_ZOOM = 8
    /** Teselas vectoriales y relieve: más allá de 14 MapLibre amplía las de 14. */
    const val MAX_ZOOM = 14
    /** El topo IGN se lee bien a 15 (≈ 4 m/píxel). */
    const val TOPO_MAX_ZOOM = 15

    // Tamaños medios por tesela medidos en la sierra de Madrid
    private const val VECTOR_KB = 30
    private const val DEM_KB = 55
    private const val TOPO_KB = 28

    /** Recuadro de la ruta con [marginMeters] alrededor, para desviarte un poco sin quedarte sin mapa. */
    fun around(segments: List<List<GeoPoint>>, marginMeters: Double = 2_000.0): Bounds? {
        val b = RouteGeometry.bounds(segments) ?: return null
        val dLat = marginMeters / 110_540
        val dLon = marginMeters / (111_320 * cos(Math.toRadians((b.minLat + b.maxLat) / 2)))
        return Bounds(b.minLat - dLat, b.maxLat + dLat, b.minLon - dLon, b.maxLon + dLon)
    }

    fun tileCount(b: Bounds, minZoom: Int, maxZoom: Int): Long = (minZoom..maxZoom).sumOf { z ->
        val (x1, y1) = tile(b.maxLat, b.minLon, z)
        val (x2, y2) = tile(b.minLat, b.maxLon, z)
        (x2 - x1 + 1).toLong() * (y2 - y1 + 1)
    }

    fun estimateBytes(b: Bounds, withTopo: Boolean): Long {
        val base = tileCount(b, MIN_ZOOM, MAX_ZOOM) * (VECTOR_KB + DEM_KB)
        val topo = if (withTopo) tileCount(b, MIN_ZOOM, TOPO_MAX_ZOOM) * TOPO_KB else 0
        return (base + topo) * 1024
    }

    /** Más que esto no se descarga de una vez: la base offline se haría enorme. */
    const val MAX_AREA_BYTES = 300L * 1024 * 1024

    /** Huella de una zona del mapa (redondeada a ~100 m, para que la misma vista dé la misma). */
    fun areaKey(b: Bounds): String =
        "zona-" + listOf(b.minLat, b.minLon, b.maxLat, b.maxLon).joinToString("-") { Math.round(it * 1_000).toString() }

    /** Huella de la ruta para saber si ya está descargada (mismo inicio, final, puntos y distancia). */
    fun routeKey(segments: List<List<GeoPoint>>): String {
        val pts = segments.flatten()
        if (pts.isEmpty()) return "vacia"
        val d = TrackStatsCalculator.compute(segments).distanceMeters.toLong()
        fun r(v: Double) = Math.round(v * 10_000)
        return listOf(r(pts.first().lat), r(pts.first().lon), r(pts.last().lat), r(pts.last().lon), pts.size, d)
            .joinToString("-")
    }

    /** Tesela (x, y) de un punto a un zoom, en la rejilla de OSM/Google. */
    internal fun tile(lat: Double, lon: Double, z: Int): Pair<Int, Int> {
        val n = 1 shl z
        val x = floor((lon + 180) / 360 * n).toInt().coerceIn(0, n - 1)
        val r = Math.toRadians(lat)
        val y = floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * n).toInt().coerceIn(0, n - 1)
        return x to y
    }
}
