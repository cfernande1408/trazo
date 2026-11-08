package app.trazo.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import app.trazo.gpx.Activity
import app.trazo.gpx.ride.Calories
import app.trazo.gpx.ride.RideStats

/** Tus datos para las cuentas: de momento, el peso para las kcal. Se quedan en el móvil. */
object UserProfile {
    private const val KEY = "weight_kg"

    var weightKg by mutableDoubleStateOf(Calories.DEFAULT_WEIGHT_KG)
        private set

    /** false mientras no lo hayas dicho (se usa el de serie). */
    var weightSet = false
        private set

    fun init(context: Context) {
        val p = prefs(context)
        weightSet = p.contains(KEY)
        weightKg = p.getFloat(KEY, Calories.DEFAULT_WEIGHT_KG.toFloat()).toDouble()
    }

    fun setWeight(context: Context, kg: Double) {
        weightKg = kg.coerceIn(30.0, 200.0)
        weightSet = true
        prefs(context).edit().putFloat(KEY, weightKg.toFloat()).apply()
    }

    /** Kcal de una salida con tu peso (y la potencia, si llevabas potenciómetro). */
    fun kcal(stats: RideStats, activity: Activity): Int =
        Calories.estimate(activity, stats.movingMs, stats.gainMeters, weightKg, stats.avgPower)

    private fun prefs(context: Context) = context.getSharedPreferences("trazo", Context.MODE_PRIVATE)
}
