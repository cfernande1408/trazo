package app.trazo.gpx.nav

import app.trazo.gpx.Activity
import app.trazo.gpx.osm.PoiOnRoute
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Qué decir en voz alta mientras navegas. No sabe nada de Android: recibe
 * el estado de la navegación y devuelve las frases nuevas, sin repetirlas.
 */
class VoiceGuide(
    private val turns: List<Turn>,
    private val climbs: List<Climb>,
    private val pois: List<PoiOnRoute>,
    activity: Activity,
) {
    /** A qué distancia se avisa antes y "ya" (en bici vas más rápido). */
    private val early = if (activity == Activity.HIKE) 60.0 else 150.0
    private val now = if (activity == Activity.HIKE) 15.0 else 40.0
    private val said = mutableSetOf<String>()
    private var wasOff = false
    private var lightWarned = false

    fun update(s: NavState, lightMarginMinutes: Long? = null): List<String> {
        val out = mutableListOf<String>()
        if (s.offRouteStarted) out += "Te has salido de la ruta"
        if (wasOff && !s.offRoute) out += "Vuelves a estar en la ruta"
        wasOff = s.offRoute
        if (s.offRoute) return out

        for ((i, t) in turns.withIndex()) {
            val ahead = t.alongMeters - s.alongMeters
            if (ahead < -20 || ahead > early + 20) continue
            when {
                ahead <= now && said.add("t$i-now") -> { said += "t$i-early"; out += capitalize(t.kind.phrase) }
                ahead in (now + 20)..(early + 20) && said.add("t$i-early") -> out += "En ${meters(ahead)}, ${t.kind.phrase}"
            }
        }
        climbs.withIndex().firstOrNull { (_, c) -> c.startMeters - s.alongMeters in 0.0..120.0 }?.let { (i, c) ->
            if (said.add("c$i")) {
                out += "Empieza una subida de ${meters(c.lengthMeters)} al ${c.avgGradePercent.roundToInt()} por ciento, " +
                    "${c.gainMeters.roundToInt()} metros de desnivel"
            }
        }
        pois.withIndex().firstOrNull { (_, p) -> p.alongMeters - s.alongMeters in 0.0..250.0 }?.let { (i, p) ->
            if (said.add("p$i")) out += "${p.poi.kind.label} en ${meters(p.alongMeters - s.alongMeters)}"
        }
        if (lightMarginMinutes != null && lightMarginMinutes < 15 && !lightWarned) {
            lightWarned = true
            out += if (lightMarginMinutes < 0) "Al ritmo actual, se te hará de noche antes de terminar"
            else "Quedan pocos minutos de luz para terminar la ruta"
        }
        return out
    }

    private fun capitalize(s: String) = s.replaceFirstChar { it.titlecase(Locale.ROOT) }

    /** "200 metros", "1,5 kilómetros": redondeado para que suene natural. */
    private fun meters(m: Double): String =
        if (m < 1000) "${((m / 10).roundToInt() * 10).coerceAtLeast(10)} metros"
        else String.format(Locale.forLanguageTag("es-ES"), "%.1f kilómetros", m / 1000).replace(",0 ", " ")
}
