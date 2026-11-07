package app.trazo.gpx.ride

import kotlin.math.abs
import kotlin.math.pow

/**
 * Altímetro barométrico, como el de los GPS de Garmin.
 *
 * La presión mide muy bien cuánto subes o bajas, pero no la altitud real
 * (depende del tiempo que haga). El GPS es al revés: acierta de media pero
 * salta 10 o 20 m de un punto a otro, y eso infla el desnivel. Aquí se
 * combinan: la forma la da el barómetro y el GPS fija el nivel poco a poco,
 * así que también se corrige la deriva cuando cambia el tiempo.
 *
 * Sin barómetro (o si deja de dar lecturas) devuelve la altitud del GPS tal cual.
 */
class BaroAltimeter(
    /** Lecturas buenas del GPS antes de dar altitudes del barómetro. */
    private val calibrationFixes: Int = 5,
    /**
     * Cuánto corrige el nivel cada lectura buena del GPS cuando ya está fijado
     * (con una por segundo, unos 3 min). Al principio es la media de todas.
     */
    private val gpsWeight: Double = 0.005,
    /** Suavizado de la presión, que tiembla unas décimas de hPa. */
    private val pressureSmoothing: Double = 0.1,
) {
    private var pressure: Double? = null
    private var pressureAtMs = 0L
    private var offset = 0.0
    private var fixes = 0

    /** true cuando ya da altitudes del barómetro. */
    val active: Boolean get() = fixes >= calibrationFixes && pressure != null

    /** Una lectura del sensor de presión, en hPa. */
    fun onPressure(hPa: Double, timeMs: Long) {
        if (hPa !in 300.0..1100.0) return
        val p = pressure
        // Tras un hueco largo se empieza de nuevo el suavizado
        pressure = if (p == null || timeMs - pressureAtMs > STALE_MS) hPa else p + pressureSmoothing * (hPa - p)
        pressureAtMs = timeMs
    }

    /**
     * Altitud para la posición del GPS que acaba de llegar. [gpsVerticalAccuracy]
     * es la precisión vertical que da Android, si la da.
     */
    fun altitude(gpsAlt: Double?, gpsVerticalAccuracy: Float?, timeMs: Long): Double? {
        val p = pressure
        if (p == null || timeMs - pressureAtMs > STALE_MS) return gpsAlt
        val baro = pressureAltitude(p)
        val good = gpsAlt != null && (gpsVerticalAccuracy == null || gpsVerticalAccuracy <= MAX_VERTICAL_ACCURACY)
        // Ya fijado, un GPS que dice algo muy distinto no mueve el nivel (reflejos, mala señal)
        if (good && (fixes < calibrationFixes || abs(gpsAlt!! - baro - offset) < MAX_GPS_JUMP)) {
            fixes++
            offset += maxOf(1.0 / fixes, gpsWeight) * (gpsAlt!! - baro - offset)
        }
        return if (fixes >= calibrationFixes) baro + offset else gpsAlt
    }

    companion object {
        /** Sin lecturas de presión en este tiempo, se vuelve al GPS. */
        const val STALE_MS = 10_000L
        const val MAX_VERTICAL_ACCURACY = 20f
        const val MAX_GPS_JUMP = 100.0

        /** Atmósfera estándar: altitud para una presión, con 1013,25 hPa a nivel del mar. */
        fun pressureAltitude(hPa: Double): Double = 44_330.0 * (1 - (hPa / 1013.25).pow(1 / 5.255))
    }
}
