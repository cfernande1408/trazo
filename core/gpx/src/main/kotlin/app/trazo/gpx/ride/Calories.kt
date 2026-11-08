package app.trazo.gpx.ride

import app.trazo.gpx.Activity
import kotlin.math.roundToInt

/**
 * Kcal gastadas, estimadas como hacen los GPS sin pulsómetro:
 * - con potenciómetro (bici), del trabajo hecho: 1 kJ en los pedales ≈ 1 kcal
 *   (el cuerpo rinde un ~24 % y 1 kcal son 4,184 kJ);
 * - si no, el gasto en llano por MET (Compendio de Actividades Físicas: bici
 *   de montaña moderada ~7, senderismo ~5) más lo que cuesta subir el desnivel
 *   con tu peso y el de la bici o la mochila.
 */
object Calories {

    const val DEFAULT_WEIGHT_KG = 75.0

    private const val MET_BIKE = 7.0
    private const val MET_HIKE = 5.0
    /** Rendimiento del cuerpo al subir. */
    private const val EFFICIENCY = 0.22
    private const val BIKE_KG = 13.0
    private const val PACK_KG = 4.0

    fun estimate(activity: Activity, movingMs: Long, gainMeters: Double, weightKg: Double, avgPowerW: Int? = null): Int {
        if (movingMs <= 0) return 0
        if (activity == Activity.BIKE && avgPowerW != null && avgPowerW > 0) {
            return (avgPowerW * movingMs / 1_000_000.0).roundToInt()
        }
        val hours = movingMs / 3_600_000.0
        val met = if (activity == Activity.HIKE) MET_HIKE else MET_BIKE
        val carried = weightKg + if (activity == Activity.HIKE) PACK_KG else BIKE_KG
        val climb = carried * 9.81 * gainMeters.coerceAtLeast(0.0) / 4184.0 / EFFICIENCY
        return (met * weightKg * hours + climb).roundToInt()
    }

    /** "1.240 kcal" */
    fun format(kcal: Int): String = String.format(java.util.Locale.forLanguageTag("es-ES"), "%,d kcal", kcal)
}
