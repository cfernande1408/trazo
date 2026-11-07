package app.trazo.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.trazo.gpx.Season

/**
 * El icono del escritorio cambia con la estación: hay un alias de la
 * actividad por estación (en el manifiesto) y se deja activo solo el que
 * toca. Se llama al salir de la app para que el cambio no se note mientras
 * la usas; algunos lanzadores tardan unos segundos en refrescarlo.
 */
object SeasonIcon {

    private fun alias(s: Season) = "app.trazo.Launcher" + when (s) {
        Season.SPRING -> "Spring"
        Season.SUMMER -> "Summer"
        Season.AUTUMN -> "Autumn"
        Season.WINTER -> "Winter"
    }

    fun apply(context: Context, season: Season) {
        val pm = context.packageManager
        val wanted = ComponentName(context.packageName, alias(season))
        // Primero se enciende el nuevo y luego se apagan los demás: nunca se queda sin icono
        if (pm.getComponentEnabledSetting(wanted) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(wanted, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
        Season.entries.filter { it != season }.forEach { other ->
            val c = ComponentName(context.packageName, alias(other))
            // El de verano viene activo de serie: "por defecto" también cuenta como encendido
            val state = pm.getComponentEnabledSetting(c)
            val on = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && other == Season.SUMMER)
            if (on) pm.setComponentEnabledSetting(c, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
