package app.trazo.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.trazo.gpx.Activity
import app.trazo.gpx.ride.Calories
import app.trazo.gpx.ride.DoneRoute
import app.trazo.gpx.ride.Fitness
import app.trazo.gpx.ride.PersonalPace
import app.trazo.gpx.ride.RideStats
import app.trazo.ride.RideStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tu perfil: peso y, si quieres afinar, altura, edad y sexo para las kcal
 * (tu metabolismo basal), y tu forma física, que junto con tus rutas
 * realizadas ajusta los tiempos y lo dura que es cada ruta para ti.
 * Se queda en el móvil.
 */
object UserProfile {
    private const val WEIGHT = "weight_kg"
    private const val HEIGHT = "height_cm"
    private const val AGE = "age_years"
    private const val SEX = "sex"
    private const val FITNESS = "fitness"
    /** Rutas realizadas que se leen para tu ritmo (de las dos actividades). */
    private const val RECENT = 24

    var weightKg by mutableDoubleStateOf(Calories.DEFAULT_WEIGHT_KG)
        private set
    var heightCm by mutableStateOf<Double?>(null)
        private set
    var ageYears by mutableStateOf<Int?>(null)
        private set
    var sex by mutableStateOf<Calories.Sex?>(null)
        private set

    var fitness by mutableStateOf<Fitness?>(null)
        private set

    /** Tus rutas realizadas más recientes, para tu ritmo. */
    var done by mutableStateOf(emptyList<DoneRoute>())
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
        fitness = p.getString(FITNESS, null)?.let { f -> Fitness.entries.firstOrNull { it.name == f } }
    }

    /** Vuelve a leer tus rutas realizadas (al abrir la app y al grabar o borrar una). */
    suspend fun refreshDone(context: Context) {
        done = withContext(Dispatchers.IO) {
            runCatching {
                RideStore.recent(context, RECENT).map {
                    DoneRoute(it.activity, it.stats.distanceMeters, it.stats.gainMeters, it.stats.lossMeters, it.stats.movingMs)
                }
            }.getOrDefault(emptyList())
        }
    }

    /** Tu ritmo en [activity]: tus rutas y tu forma física. */
    fun pace(activity: Activity): PersonalPace = PersonalPace.of(activity, fitness, done)

    fun save(context: Context, weight: Double, height: Double?, age: Int?, sex: Calories.Sex?, fitness: Fitness?) {
        weightKg = weight.coerceIn(30.0, 200.0)
        heightCm = height?.coerceIn(120.0, 220.0)
        ageYears = age?.coerceIn(10, 100)
        this.sex = sex
        this.fitness = fitness
        weightSet = true
        prefs(context).edit()
            .putFloat(WEIGHT, weightKg.toFloat())
            .putFloat(HEIGHT, (heightCm ?: 0.0).toFloat())
            .putInt(AGE, ageYears ?: 0)
            .putString(SEX, sex?.name)
            .putString(FITNESS, fitness?.name)
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
