package app.trazo.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.trazo.gpx.Activity
import app.trazo.gpx.ride.Calories
import app.trazo.gpx.ride.RideStats

/**
 * Tu perfil para las cuentas de kcal: peso y, si quieres afinar, altura, edad
 * y sexo (para tu metabolismo basal). Se queda en el móvil.
 */
object UserProfile {
    private const val WEIGHT = "weight_kg"
    private const val HEIGHT = "height_cm"
    private const val AGE = "age_years"
    private const val SEX = "sex"

    var weightKg by mutableDoubleStateOf(Calories.DEFAULT_WEIGHT_KG)
        private set
    var heightCm by mutableStateOf<Double?>(null)
        private set
    var ageYears by mutableStateOf<Int?>(null)
        private set
    var sex by mutableStateOf<Calories.Sex?>(null)
        private set

    /** false mientras no hayas puesto el peso (se usa el de serie). */
    var weightSet by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = prefs(context)
        weightSet = p.contains(WEIGHT)
        weightKg = p.getFloat(WEIGHT, Calories.DEFAULT_WEIGHT_KG.toFloat()).toDouble()
        heightCm = p.getFloat(HEIGHT, 0f).takeIf { it > 0 }?.toDouble()
        ageYears = p.getInt(AGE, 0).takeIf { it > 0 }
        sex = p.getString(SEX, null)?.let { s -> Calories.Sex.entries.firstOrNull { it.name == s } }
    }

    fun save(context: Context, weight: Double, height: Double?, age: Int?, sex: Calories.Sex?) {
        weightKg = weight.coerceIn(30.0, 200.0)
        heightCm = height?.coerceIn(120.0, 220.0)
        ageYears = age?.coerceIn(10, 100)
        this.sex = sex
        weightSet = true
        prefs(context).edit()
            .putFloat(WEIGHT, weightKg.toFloat())
            .putFloat(HEIGHT, (heightCm ?: 0.0).toFloat())
            .putInt(AGE, ageYears ?: 0)
            .putString(SEX, sex?.name)
            .apply()
    }

    /** Tu metabolismo basal, si sabemos altura y edad. */
    val bmr: Double?
        get() {
            val h = heightCm ?: return null
            val a = ageYears ?: return null
            return Calories.bmr(weightKg, h, a, sex)
        }

    /** Kcal de una salida con tus datos (y la potencia, si llevabas potenciómetro). */
    fun kcal(stats: RideStats, activity: Activity): Int =
        Calories.estimate(activity, stats.movingMs, stats.gainMeters, weightKg, stats.avgPower, bmr)

    private fun prefs(context: Context) = context.getSharedPreferences("trazo", Context.MODE_PRIVATE)
}
