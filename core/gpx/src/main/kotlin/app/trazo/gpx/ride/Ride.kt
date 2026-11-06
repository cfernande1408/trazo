package app.trazo.gpx.ride

import app.trazo.gpx.Activity
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.Gpx
import app.trazo.gpx.TrackStatsCalculator
import java.time.Instant

/** Una posición grabada, con su hora. */
data class TimedPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val timeMs: Long,
    val accuracy: Float? = null,
) {
    fun geo() = GeoPoint(lat, lon, ele, Instant.ofEpochMilli(timeMs).toString())
}

data class RideStats(
    val distanceMeters: Double,
    /** Desde el primer punto hasta el último, pausas incluidas. */
    val totalMs: Long,
    /** Solo el tiempo en que te movías. */
    val movingMs: Long,
    val avgMovingKmh: Double,
    val maxKmh: Double,
    val gainMeters: Double,
    val lossMeters: Double,
) {
    companion object {
        val EMPTY = RideStats(0.0, 0, 0, 0.0, 0.0, 0.0, 0.0)
    }
}

object RideCalculator {

    /** Más rápido que esto en bici es casi seguro un salto del GPS (90 km/h). */
    const val MAX_PLAUSIBLE_MPS = 25.0
    /** Por debajo de esto estás parado (menos de ~3 km/h). */
    const val MOVING_MPS = 0.8
    /** Un hueco mayor entre puntos no cuenta como tiempo en movimiento. */
    const val MAX_GAP_MS = 30_000L
    /** La velocidad máxima se mide sobre al menos este tiempo, para no fiarse de un punto suelto. */
    const val MAX_SPEED_WINDOW_MS = 5_000L

    /**
     * ¿Se acepta [p] detrás de [last]? Descarta posiciones imprecisas
     * (> 30 m), saltos imposibles y el temblor del GPS cuando estás parado.
     */
    fun accept(last: TimedPoint?, p: TimedPoint, activity: Activity = Activity.BIKE): Boolean {
        if (p.accuracy != null && p.accuracy > 30f) return false
        if (last == null) return true
        val dt = p.timeMs - last.timeMs
        if (dt <= 0) return false
        val d = dist(last, p)
        if (d / (dt / 1000.0) > activity.maxPlausibleMps) return false
        return d >= 3.0 || dt >= MAX_GAP_MS
    }

    fun compute(segments: List<List<TimedPoint>>, activity: Activity = Activity.BIKE): RideStats {
        val movingMps = activity.movingMps
        val pts = segments.filter { it.isNotEmpty() }
        if (pts.isEmpty()) return RideStats.EMPTY
        var distance = 0.0
        var moving = 0L
        var maxMps = 0.0
        for (seg in pts) {
            val cum = DoubleArray(seg.size)
            for (i in 1 until seg.size) {
                val d = dist(seg[i - 1], seg[i])
                val dt = seg[i].timeMs - seg[i - 1].timeMs
                cum[i] = cum[i - 1] + d
                distance += d
                if (dt in 1..MAX_GAP_MS && d / (dt / 1000.0) >= movingMps) moving += dt
            }
            // Ventana deslizante de al menos 5 s para la velocidad máxima
            var j = 0
            for (i in 1 until seg.size) {
                while (j < i - 1 && seg[i].timeMs - seg[j + 1].timeMs >= MAX_SPEED_WINDOW_MS) j++
                val dt = seg[i].timeMs - seg[j].timeMs
                if (dt in MAX_SPEED_WINDOW_MS..MAX_GAP_MS) maxMps = maxOf(maxMps, (cum[i] - cum[j]) / (dt / 1000.0))
            }
        }
        val total = pts.last().last().timeMs - pts.first().first().timeMs
        val ele = TrackStatsCalculator.compute(pts.map { seg -> seg.map { it.geo() } })
        return RideStats(
            distanceMeters = distance,
            totalMs = total,
            movingMs = moving,
            avgMovingKmh = if (moving > 0) distance / (moving / 1000.0) * 3.6 else 0.0,
            maxKmh = maxMps * 3.6,
            gainMeters = ele.elevationGainMeters,
            lossMeters = ele.elevationLossMeters,
        )
    }

    /** Puntos con hora de un GPX grabado (los que no tienen hora se ignoran). */
    fun fromGpx(gpx: Gpx): List<List<TimedPoint>> =
        gpx.segments.map { seg ->
            seg.mapNotNull { p ->
                val t = p.time?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return@mapNotNull null
                TimedPoint(p.lat, p.lon, p.ele, t)
            }
        }.filter { it.isNotEmpty() }

    private fun dist(a: TimedPoint, b: TimedPoint) =
        TrackStatsCalculator.haversineMeters(GeoPoint(a.lat, a.lon), GeoPoint(b.lat, b.lon))
}

/** Graba una salida: añade posiciones filtradas y admite pausas (cada pausa abre un tramo nuevo). */
class RideRecorder(val activity: Activity = Activity.BIKE) {
    private val segments = mutableListOf<MutableList<TimedPoint>>(mutableListOf())
    var paused = false
        private set

    val points: List<List<TimedPoint>> get() = segments.filter { it.isNotEmpty() }.map { it.toList() }

    /** true si la posición se ha guardado. */
    fun add(p: TimedPoint): Boolean {
        if (paused) return false
        val current = segments.last()
        if (!RideCalculator.accept(current.lastOrNull(), p, activity)) return false
        current.add(p)
        return true
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        if (!paused) return
        paused = false
        if (segments.last().isNotEmpty()) segments.add(mutableListOf())
    }

    fun stats(): RideStats = RideCalculator.compute(points, activity)
}
