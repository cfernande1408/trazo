package app.trazo.gpx.nav

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.TrackStatsCalculator
import kotlin.math.cos
import kotlin.math.hypot

/** Dónde está el ciclista respecto al track. */
data class TrackPosition(
    /** Metros recorridos sobre el track hasta el punto más cercano. */
    val alongMeters: Double,
    /** Distancia en línea recta al track. */
    val distanceToTrackMeters: Double,
)

/**
 * Track aplanado con la distancia acumulada en cada punto. El hueco entre
 * segmentos no suma distancia ni se considera parte del camino.
 */
internal class TrackLine(segments: List<List<GeoPoint>>) {
    val lat: DoubleArray
    val lon: DoubleArray
    val ele: DoubleArray
    val along: DoubleArray
    /** true si el punto i empieza un segmento nuevo (no hay tramo i-1 → i). */
    val newSegment: BooleanArray

    init {
        val pts = segments.flatMap { seg -> seg.mapIndexed { i, p -> p to (i == 0) } }
        val n = pts.size
        lat = DoubleArray(n) { pts[it].first.lat }
        lon = DoubleArray(n) { pts[it].first.lon }
        ele = DoubleArray(n) { pts[it].first.ele ?: Double.NaN }
        newSegment = BooleanArray(n) { pts[it].second }
        along = DoubleArray(n)
        for (i in 1 until n) {
            along[i] = along[i - 1] +
                if (newSegment[i]) 0.0 else TrackStatsCalculator.haversineMeters(pts[i - 1].first, pts[i].first)
        }
    }

    val size get() = lat.size
    val length get() = if (size == 0) 0.0 else along[size - 1]

    /** Índice del último punto con along ≤ [d] (búsqueda binaria). */
    fun indexAt(d: Double): Int {
        var lo = 0
        var hi = size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (along[mid] <= d) lo = mid else hi = mid - 1
        }
        return lo
    }
}

class TrackMatcher internal constructor(private val line: TrackLine) {

    constructor(segments: List<List<GeoPoint>>) : this(TrackLine(segments))

    /**
     * Proyecta la posición sobre el track. Con [previousAlong] busca primero
     * cerca de donde ibas (de 300 m atrás a 3 km adelante): así en rutas
     * circulares o de ida y vuelta no salta al tramo equivocado cuando dos
     * partes del track pasan por el mismo sitio.
     */
    fun locate(lat: Double, lon: Double, previousAlong: Double? = null): TrackPosition {
        require(line.size > 0) { "Track vacío" }
        if (line.size == 1) {
            return TrackPosition(0.0, TrackStatsCalculator.haversineMeters(GeoPoint(lat, lon), GeoPoint(line.lat[0], line.lon[0])))
        }
        if (previousAlong != null) {
            val near = best(lat, lon, previousAlong - WINDOW_BACK, previousAlong + WINDOW_AHEAD)
            if (near != null && near.distanceToTrackMeters <= WINDOW_MAX_DISTANCE) return near
        }
        return best(lat, lon, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
            ?: TrackPosition(0.0, Double.MAX_VALUE)
    }

    private fun best(qLat: Double, qLon: Double, from: Double, to: Double): TrackPosition? {
        val kx = cos(Math.toRadians(qLat)) * M_PER_DEG
        var bestDist = Double.MAX_VALUE
        var bestAlong = 0.0
        for (i in 1 until line.size) {
            if (line.newSegment[i]) continue
            if (line.along[i] < from || line.along[i - 1] > to) continue
            val ax = (line.lon[i - 1] - qLon) * kx
            val ay = (line.lat[i - 1] - qLat) * M_PER_DEG
            val bx = (line.lon[i] - qLon) * kx
            val by = (line.lat[i] - qLat) * M_PER_DEG
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(ax + t * dx, ay + t * dy)
            if (d < bestDist) {
                bestDist = d
                bestAlong = line.along[i - 1] + t * (line.along[i] - line.along[i - 1])
            }
        }
        return if (bestDist == Double.MAX_VALUE) null else TrackPosition(bestAlong, bestDist)
    }

    private companion object {
        const val M_PER_DEG = 111_195.0
        const val WINDOW_BACK = 300.0
        const val WINDOW_AHEAD = 3_000.0
        const val WINDOW_MAX_DISTANCE = 80.0
    }
}
