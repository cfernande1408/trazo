package app.trazo.gpx.nav

import app.trazo.gpx.ElevationProfile
import app.trazo.gpx.GeoPoint

data class NavState(
    val alongMeters: Double,
    val remainingMeters: Double,
    val remainingGainMeters: Double,
    val distanceToTrackMeters: Double,
    val offRoute: Boolean,
    /** true solo en la actualización en la que empieza el desvío (para avisar una vez). */
    val offRouteStarted: Boolean,
    val nextClimb: Climb?,
    /** 0 si ya estás dentro de la subida. */
    val metersToNextClimb: Double?,
)

/**
 * Sigue al ciclista sobre el track. Para no dar falsas alarmas por saltos
 * del GPS, el desvío solo se declara si pasas [offRouteMeters] durante
 * [offRouteDelayMs] seguidos, y solo se da por terminado al volver a menos
 * de [backOnRouteMeters].
 */
class Navigator(
    segments: List<List<GeoPoint>>,
    private val offRouteMeters: Double = 40.0,
    private val backOnRouteMeters: Double = 25.0,
    private val offRouteDelayMs: Long = 10_000,
    elevationThreshold: Double = 3.0,
) {
    private val line = TrackLine(segments)
    private val matcher = TrackMatcher(line)
    val climbs: List<Climb> = ClimbDetector.detect(ElevationProfile.of(segments))

    /** Desnivel positivo acumulado hasta cada punto, con la misma histéresis que las estadísticas. */
    private val gainAt: DoubleArray = DoubleArray(line.size).also { g ->
        var ref = Double.NaN
        var acc = 0.0
        for (i in 0 until line.size) {
            val e = line.ele[i]
            if (!e.isNaN()) {
                when {
                    ref.isNaN() -> ref = e
                    e - ref >= elevationThreshold -> { acc += e - ref; ref = e }
                    ref - e >= elevationThreshold -> ref = e
                }
            }
            g[i] = acc
        }
    }
    private val totalGain = gainAt.lastOrNull() ?: 0.0

    private var lastAlong: Double? = null
    private var farSince: Long? = null
    private var offRoute = false

    val lengthMeters get() = line.length

    /**
     * [accuracyMeters] es el radio de error de la posición. Se descuenta antes
     * de decidir si te has salido: con mala señal solo avisa si estás lejos
     * del track incluso en el mejor caso.
     */
    fun update(lat: Double, lon: Double, timeMs: Long, accuracyMeters: Double = 0.0): NavState {
        val pos = matcher.locate(lat, lon, lastAlong)
        // Solo avanzamos sobre el track cuando de verdad estás en él y la posición es fiable
        if (pos.distanceToTrackMeters <= offRouteMeters && accuracyMeters <= MAX_TRACKING_ACCURACY) {
            lastAlong = pos.alongMeters
        }
        val surelyAway = (pos.distanceToTrackMeters - accuracyMeters).coerceAtLeast(0.0)

        var started = false
        if (!offRoute) {
            if (surelyAway > offRouteMeters) {
                val since = farSince ?: timeMs.also { farSince = it }
                if (timeMs - since >= offRouteDelayMs) { offRoute = true; started = true }
            } else {
                farSince = null
            }
        } else if (pos.distanceToTrackMeters < backOnRouteMeters) {
            offRoute = false
            farSince = null
        }

        val along = lastAlong ?: pos.alongMeters
        val next = climbs.firstOrNull { it.endMeters > along }
        return NavState(
            alongMeters = along,
            remainingMeters = (line.length - along).coerceAtLeast(0.0),
            remainingGainMeters = (totalGain - gainAtDistance(along)).coerceAtLeast(0.0),
            distanceToTrackMeters = pos.distanceToTrackMeters,
            offRoute = offRoute,
            offRouteStarted = started,
            nextClimb = next,
            metersToNextClimb = next?.let { (it.startMeters - along).coerceAtLeast(0.0) },
        )
    }

    private companion object {
        /** Con más error que esto la posición no hace avanzar sobre el track. */
        const val MAX_TRACKING_ACCURACY = 50.0
    }

    private fun gainAtDistance(d: Double): Double {
        if (line.size == 0) return 0.0
        val i = line.indexAt(d)
        if (i >= line.size - 1) return gainAt[line.size - 1]
        val span = line.along[i + 1] - line.along[i]
        val t = if (span > 0) ((d - line.along[i]) / span).coerceIn(0.0, 1.0) else 0.0
        return gainAt[i] + t * (gainAt[i + 1] - gainAt[i])
    }
}
