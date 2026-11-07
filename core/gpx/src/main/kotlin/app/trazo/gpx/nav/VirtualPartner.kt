package app.trazo.gpx.nav

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.ride.TimedPoint

/**
 * Compañero virtual: tu salida anterior por la misma ruta. Para cada km
 * de la ruta sabe cuánto tardaste entonces, y te dice si vas por delante
 * o por detrás (como el Virtual Partner de Garmin).
 */
class VirtualPartner(previous: List<List<TimedPoint>>, route: List<List<GeoPoint>>) {

    private val along: DoubleArray
    private val elapsed: LongArray

    init {
        val matcher = TrackMatcher(TrackLine(route))
        val pts = previous.flatten()
        val t0 = pts.firstOrNull()?.timeMs ?: 0L
        val a = mutableListOf<Double>()
        val e = mutableListOf<Long>()
        var last: Double? = null
        for (p in pts) {
            val pos = matcher.locate(p.lat, p.lon, last)
            if (pos.distanceToTrackMeters > 60) continue
            // Solo avanza: si entonces retrocediste un poco, no cuenta
            if (last != null && pos.alongMeters <= last) continue
            last = pos.alongMeters
            a += pos.alongMeters
            e += p.timeMs - t0
        }
        along = a.toDoubleArray()
        elapsed = e.toLongArray()
    }

    /** true si la salida anterior cubre de verdad la ruta (al menos 1 km). */
    val usable: Boolean get() = along.size >= 10 && along.last() - along.first() >= 1_000

    /** Lo que tardaste entonces hasta [alongMeters], o null si no llegaste ahí. */
    fun elapsedAt(alongMeters: Double): Long? {
        if (!usable || alongMeters < along.first() || alongMeters > along.last()) return null
        var lo = 0
        var hi = along.lastIndex
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (along[mid] <= alongMeters) lo = mid else hi = mid
        }
        val span = along[hi] - along[lo]
        val t = if (span > 0) (alongMeters - along[lo]) / span else 0.0
        return (elapsed[lo] + t * (elapsed[hi] - elapsed[lo])).toLong()
    }

    /** Ventaja en ms: positiva si vas por delante de tu salida anterior. */
    fun gapMs(alongMeters: Double, elapsedNowMs: Long): Long? = elapsedAt(alongMeters)?.let { it - elapsedNowMs }
}
