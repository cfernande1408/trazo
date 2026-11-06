package app.trazo.gpx

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Bici de montaña o senderismo. Todo lo que depende de la actividad (cuándo
 * estás parado, qué es un salto del GPS, cuándo avisar de un desvío, cómo
 * calcula rutas el enrutador, cómo se colorea la velocidad) sale de aquí.
 */
enum class Activity(
    /** Por debajo de esta velocidad cuenta como parado. */
    val movingMps: Double,
    /** Más rápido que esto es un salto del GPS. */
    val maxPlausibleMps: Double,
    val offRouteMeters: Double,
    val backOnRouteMeters: Double,
    val offRouteDelayMs: Long,
    /** Perfil de BRouter para crear rutas. */
    val brouterProfile: String,
    /** km/h → color ARGB para pintar la velocidad, de lento a rápido. */
    val speedStops: List<Pair<Double, Long>>,
) {
    BIKE(
        movingMps = 0.8, maxPlausibleMps = 25.0,
        offRouteMeters = 40.0, backOnRouteMeters = 25.0, offRouteDelayMs = 10_000,
        brouterProfile = "mtb",
        speedStops = listOf(0.0 to 0xFF1F6FB2, 12.0 to 0xFFE0A100, 22.0 to 0xFFC2410C, 35.0 to 0xFFB3261E),
    ),
    HIKE(
        // Subiendo una cuesta a pie se va a 1–2 km/h: no es estar parado
        movingMps = 0.28, maxPlausibleMps = 5.0,
        offRouteMeters = 30.0, backOnRouteMeters = 18.0, offRouteDelayMs = 15_000,
        brouterProfile = "hiking-mountain",
        speedStops = listOf(0.0 to 0xFF1F6FB2, 2.5 to 0xFFE0A100, 4.5 to 0xFFC2410C, 6.5 to 0xFFB3261E),
    );

    /**
     * Tiempo estimado sin paradas.
     * - Senderismo: método MIDE (el de las fichas de los senderos): 5 km/h en
     *   llano, 400 m/h subiendo y 600 m/h bajando; se toma el mayor de
     *   horizontal y vertical más la mitad del menor.
     * - Bici de montaña: 14 km/h de media más 1 hora por cada 600 m de subida.
     */
    fun estimateMs(distanceMeters: Double, gainMeters: Double, lossMeters: Double): Long {
        val hours = when (this) {
            HIKE -> {
                val h = distanceMeters / 5_000.0
                val v = gainMeters / 400.0 + lossMeters / 600.0
                max(h, v) + min(h, v) / 2
            }
            BIKE -> distanceMeters / 14_000.0 + gainMeters / 600.0
        }
        return (hours * 3_600_000).roundToLong()
    }
}
