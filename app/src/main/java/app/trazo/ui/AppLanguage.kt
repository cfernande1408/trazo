package app.trazo.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import app.trazo.gpx.i18n.I18n
import app.trazo.gpx.i18n.Lang
import java.util.Locale

/**
 * Idioma de la app: el del sistema (español si lo es; si no, inglés) o el
 * elegido en Info. Se fija al arrancar, antes de pintar nada.
 */
object AppLanguage {
    private const val KEY = "lang"

    /** Lo elegido en Info; null es el del sistema. */
    var choice: Lang? = null
        private set

    fun init(context: Context) {
        choice = prefs(context).getString(KEY, null)?.let { s -> Lang.entries.firstOrNull { it.name == s } }
        I18n.lang = choice ?: Lang.of(Locale.getDefault())
    }

    /**
     * Cambia el idioma y reinicia la app: así todo, también lo que se
     * calcula una sola vez, sale ya en el nuevo.
     */
    fun set(activity: Activity, lang: Lang?) {
        if (lang == choice) return
        prefs(activity).edit().apply { if (lang == null) remove(KEY) else putString(KEY, lang.name) }.commit()
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) activity.startActivity(intent)
        activity.finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("trazo", Context.MODE_PRIVATE)
}
