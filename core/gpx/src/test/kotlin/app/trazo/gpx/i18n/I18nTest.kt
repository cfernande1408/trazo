package app.trazo.gpx.i18n

import app.trazo.gpx.Season
import app.trazo.gpx.nav.Rejoin
import app.trazo.gpx.nav.TurnKind
import app.trazo.gpx.osm.Difficulty
import app.trazo.gpx.ride.Calories
import app.trazo.gpx.ride.Effort
import app.trazo.gpx.ride.Fitness
import app.trazo.gpx.weather.RouteWeather
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class I18nTest {

    private fun <T> inEnglish(block: () -> T): T {
        I18n.lang = Lang.EN
        try {
            return block()
        } finally {
            I18n.lang = Lang.ES
        }
    }

    @Test
    fun `idioma del sistema`() {
        assertEquals(Lang.ES, Lang.of(Locale.forLanguageTag("es-MX")))
        assertEquals(Lang.EN, Lang.of(Locale.forLanguageTag("en-US")))
        assertEquals(Lang.EN, Lang.of(Locale.forLanguageTag("fr-FR")))
    }

    @Test
    fun `textos en español por defecto`() {
        assertEquals("gira a la derecha", TurnKind.RIGHT.phrase)
        assertEquals("A tu medida", Effort.MODERATE.label)
        assertEquals("Otoño", Season.AUTUMN.label)
        assertEquals("15 %", percent(15))
        assertEquals("1.240 kcal", Calories.format(1240))
    }

    @Test
    fun `y en inglés`() = inEnglish {
        assertEquals("turn right", TurnKind.RIGHT.phrase)
        assertEquals("Right for you", Effort.MODERATE.label)
        assertEquals("Every week", Fitness.REGULAR.label)
        assertEquals("Autumn", Season.AUTUMN.label)
        assertEquals("S3" to "boulders, steps and tight switchbacks", Difficulty.label(3, mtb = true))
        assertEquals("Cloudy", RouteWeather.describe(3).first)
        assertEquals("15%", percent(15))
        assertEquals("1,240 kcal", Calories.format(1240))
    }

    @Test
    fun `la voz habla en el idioma elegido`() {
        val r = app.trazo.gpx.nav.Rerouted(emptyList(), emptyList(), 1500.0, 0.0, 0.0)
        assertEquals("Ruta recalculada. Vuelves a la ruta en 1,5 kilómetros.", Rejoin.phrase(r))
        inEnglish { assertEquals("Route recalculated. You rejoin the route in 1.5 kilometres.", Rejoin.phrase(r)) }
    }
}
