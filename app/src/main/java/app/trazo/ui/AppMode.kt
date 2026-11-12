package app.trazo.ui

import app.trazo.gpx.i18n.L
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.ui.graphics.vector.ImageVector
import app.trazo.gpx.Activity
import app.trazo.gpx.Season
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ¿Biker o senderista? Se elige al abrir la app por primera vez, se guarda y
 * se puede cambiar cuando quieras. Toda la app se adapta a esto.
 */
object AppMode {
    private const val PREFS = "trazo"
    private const val KEY = "activity"

    private val _activity = MutableStateFlow<Activity?>(null)
    /** null hasta que el usuario elige. */
    val activity: StateFlow<Activity?> = _activity.asStateFlow()

    /** La actividad actual (bici si aún no ha elegido). */
    val current: Activity get() = _activity.value ?: Activity.BIKE

    fun init(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _activity.value = saved?.let { runCatching { Activity.valueOf(it) }.getOrNull() }
        loadSeason(context)
    }

    fun set(context: Context, activity: Activity) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, activity.name).apply()
        _activity.value = activity
    }

    private const val KEY_SEASON = "season"
    private val _seasonOverride = MutableStateFlow<Season?>(null)
    /** Estación elegida a mano, o null para que vaya con el calendario. */
    val seasonOverride: StateFlow<Season?> = _seasonOverride.asStateFlow()

    /** La estación que se usa ahora: la elegida o la de hoy. */
    val season: Season get() = _seasonOverride.value ?: todaySeason()

    fun todaySeason(): Season = LocalDate.now().let { Season.of(it.monthValue, it.dayOfMonth) }

    fun setSeason(context: Context, season: Season?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SEASON, season?.name).apply()
        _seasonOverride.value = season
    }

    private fun loadSeason(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SEASON, null)
        _seasonOverride.value = saved?.let { runCatching { Season.valueOf(it) }.getOrNull() }
    }
}

// Textos e iconos según la actividad

val Activity.label: String get() = if (this == Activity.BIKE) L("Bici de montaña", "Mountain biking") else L("Senderismo", "Hiking")
val Activity.who: String get() = if (this == Activity.BIKE) "Biker" else L("Senderista", "Hiker")

@Suppress("DEPRECATION")
val Activity.icon: ImageVector get() = if (this == Activity.BIKE) Icons.Filled.DirectionsBike else Icons.Filled.Hiking

/** L("salida", "ride") / L("excursión", "hike") */
val Activity.outing: String get() = if (this == Activity.BIKE) L("salida", "ride") else L("excursión", "hike")
/** Lo que has grabado, en bici o a pie. */
val Activity.outingsTitle: String get() = L("Rutas realizadas", "Completed routes")
val Activity.recordCta: String get() = if (this == Activity.BIKE) L("Grabar salida", "Record ride") else L("Grabar excursión", "Record hike")
val Activity.exploreHint: String get() =
    if (this == Activity.BIKE) L("Toca una bici para ver su recorrido", "Tap a bike to see its route") else L("Toca un excursionista para ver el sendero", "Tap a hiker to see the trail")
val Activity.routesWord: String get() = if (this == Activity.BIKE) L("rutas", "routes") else L("senderos", "trails")

/** Etiqueta del tipo de ruta en Explorar. */
fun Activity.badge(isMtb: Boolean): String = when {
    this == Activity.HIKE -> L("Sendero", "Trail")
    isMtb -> "MTB"
    else -> L("Bici", "Bike")
}
