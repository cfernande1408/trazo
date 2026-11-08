package app.trazo.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Las tarjetas de «Descubre Trazo» de Inicio: cuáles has marcado como vistas
 * y cuáles has quitado deslizándolas. Se guarda en el móvil.
 */
object Tips {
    private const val SEEN = "tips_seen"
    private const val DISMISSED = "tips_dismissed"

    var seen by mutableStateOf(emptySet<String>())
        private set
    var dismissed by mutableStateOf(emptySet<String>())
        private set

    fun init(context: Context) {
        val p = prefs(context)
        seen = p.getStringSet(SEEN, null).orEmpty().toSet()
        dismissed = p.getStringSet(DISMISSED, null).orEmpty().toSet()
    }

    /** Marca o desmarca una tarjeta como vista. */
    fun toggleSeen(context: Context, id: String) {
        seen = if (id in seen) seen - id else seen + id
        prefs(context).edit().putStringSet(SEEN, seen).apply()
    }

    fun markSeen(context: Context, id: String) {
        if (id in seen) return
        seen = seen + id
        prefs(context).edit().putStringSet(SEEN, seen).apply()
    }

    /** La quita de Inicio (cuenta también como vista). */
    fun dismiss(context: Context, id: String) {
        dismissed = dismissed + id
        seen = seen + id
        prefs(context).edit().putStringSet(DISMISSED, dismissed).putStringSet(SEEN, seen).apply()
    }

    /** Vuelve a sacarlas todas, como la primera vez. */
    fun reset(context: Context) {
        seen = emptySet()
        dismissed = emptySet()
        prefs(context).edit().remove(SEEN).remove(DISMISSED).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("trazo", Context.MODE_PRIVATE)
}
