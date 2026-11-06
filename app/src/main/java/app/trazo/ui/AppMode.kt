package app.trazo.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.ui.graphics.vector.ImageVector
import app.trazo.gpx.Activity
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
    }

    fun set(context: Context, activity: Activity) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, activity.name).apply()
        _activity.value = activity
    }
}

// Textos e iconos según la actividad

val Activity.label: String get() = if (this == Activity.BIKE) "Bici de montaña" else "Senderismo"
val Activity.who: String get() = if (this == Activity.BIKE) "Biker" else "Senderista"

@Suppress("DEPRECATION")
val Activity.icon: ImageVector get() = if (this == Activity.BIKE) Icons.Filled.DirectionsBike else Icons.Filled.Hiking

/** "salida" / "excursión" */
val Activity.outing: String get() = if (this == Activity.BIKE) "salida" else "excursión"
val Activity.outingsTitle: String get() = if (this == Activity.BIKE) "Mis salidas" else "Mis excursiones"
val Activity.recordCta: String get() = if (this == Activity.BIKE) "Grabar salida" else "Grabar excursión"
val Activity.exploreHint: String get() =
    if (this == Activity.BIKE) "Toca una bici para ver su recorrido" else "Toca un excursionista para ver el sendero"
val Activity.routesWord: String get() = if (this == Activity.BIKE) "rutas" else "senderos"

/** Etiqueta del tipo de ruta en Explorar. */
fun Activity.badge(isMtb: Boolean): String = when {
    this == Activity.HIKE -> "Sendero"
    isMtb -> "MTB"
    else -> "Bici"
}
