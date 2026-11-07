package app.trazo.gpx.safety

import kotlin.math.abs

/**
 * Detección de caídas con el acelerómetro: un golpe fuerte (más de
 * [impactG] g) seguido de [stillMs] casi sin movimiento y sin avanzar.
 * Si te levantas o sigues pedaleando, no salta. Sin Android: recibe
 * muestras y devuelve si toca avisar.
 */
class FallDetector(
    private val impactG: Double = 3.2,
    private val stillMs: Long = 20_000,
    /** Tras un aviso, no vuelve a saltar en este tiempo. */
    private val cooldownMs: Long = 120_000,
) {
    enum class Event { NONE, IMPACT, FALL }

    private var impactAt: Long? = null
    private var movingSamples = 0
    private var samples = 0
    private var lastFall: Long = Long.MIN_VALUE / 2
    private var lastSpeedMps = 0.0

    /** Velocidad del GPS: si te mueves de verdad, no es una caída. */
    fun onSpeed(mps: Double) {
        lastSpeedMps = mps
    }

    /** [accelMs2]: módulo de la aceleración (con gravedad), en m/s². */
    fun onSample(timeMs: Long, accelMs2: Double): Event {
        if (timeMs - lastFall < cooldownMs) return Event.NONE
        val g = accelMs2 / G
        val start = impactAt
        if (start == null) {
            if (g >= impactG) {
                impactAt = timeMs
                samples = 0
                movingSamples = 0
                return Event.IMPACT
            }
            return Event.NONE
        }
        // Los 2 primeros segundos tras el golpe son el propio golpe y el rebote
        if (timeMs - start < 2_000) return Event.NONE
        samples++
        if (abs(g - 1.0) > 0.35) movingSamples++
        if (lastSpeedMps > 1.0 || (samples >= 50 && movingSamples > samples * 0.25)) {
            impactAt = null // te has levantado o sigues en marcha
            return Event.NONE
        }
        if (timeMs - start >= stillMs) {
            impactAt = null
            lastFall = timeMs
            return Event.FALL
        }
        return Event.NONE
    }

    fun reset() {
        impactAt = null
    }

    private companion object {
        const val G = 9.80665
    }
}
