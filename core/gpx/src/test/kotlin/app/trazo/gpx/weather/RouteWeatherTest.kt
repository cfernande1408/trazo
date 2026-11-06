package app.trazo.gpx.weather

import app.trazo.gpx.Activity
import app.trazo.gpx.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteWeatherTest {

    /** Subida recta de ~11 km hacia el norte, de 1.000 a 2.000 m. */
    private val climb = listOf((0..100).map { i -> GeoPoint(40.70 + i * 0.001, -3.90, 1000.0 + i * 10) })

    @Test
    fun `puntos repartidos con la hora de paso creciente`() {
        val s = RouteWeather.samples(climb, Activity.HIKE)
        assertEquals(3, s.size) // ~11 km → uno cada ~5 km
        assertEquals(0.0, s.first().distanceMeters, 0.0)
        assertEquals(0L, s.first().offsetMs)
        assertEquals(1000.0, s.first().ele!!, 0.5)
        assertTrue(s.last().ele!! > 1900)
        assertTrue(s.zipWithNext().all { (a, b) -> b.offsetMs > a.offsetMs })
        // A pie se tarda más que en bici en llegar al final
        val bike = RouteWeather.samples(climb, Activity.BIKE)
        assertTrue(s.last().offsetMs > bike.last().offsetMs)
    }

    @Test
    fun `url con todos los puntos y su altitud`() {
        val u = RouteWeather.url(RouteWeather.samples(climb, Activity.BIKE, count = 2))
        assertTrue(u, u.startsWith("https://api.open-meteo.com/v1/forecast?latitude=40.7000,40.8000&longitude=-3.9000,-3.9000"))
        assertTrue(u, u.contains("&elevation=1000,2000"))
        assertTrue(u.contains("timeformat=unixtime"))
        assertTrue(u.contains("weather_code"))
    }

    private fun loc(t0: Long, temps: List<Double>, codes: List<Int>, gusts: List<Double>, probs: List<Int>) = """
        {"hourly":{"time":[${temps.indices.joinToString(",") { "${t0 + it * 3600}" }}],
         "temperature_2m":[${temps.joinToString(",")}],
         "precipitation_probability":[${probs.joinToString(",")}],
         "precipitation":[${temps.joinToString(",") { "0.0" }}],
         "weather_code":[${codes.joinToString(",")}],
         "wind_speed_10m":[${temps.joinToString(",") { "10.0" }}],
         "wind_gusts_10m":[${gusts.joinToString(",")}]}}"""

    @Test
    fun `elige la hora en que pasas por cada punto`() {
        val t0 = 1_800_000_000L // segundos
        val samples = listOf(
            RouteSample(0.0, 40.7, -3.9, 1000.0, 0),
            RouteSample(10_000.0, 40.8, -3.9, 2000.0, 2 * 3_600_000L + 20 * 60_000L), // 2 h 20 min después
        )
        val json = "[" + loc(t0, listOf(8.0, 9.0, 10.0, 11.0), listOf(0, 0, 1, 3), listOf(10.0, 10.0, 10.0, 10.0), listOf(0, 0, 5, 10)) + "," +
            loc(t0, listOf(2.0, 1.0, -1.0, -2.0), listOf(3, 3, 71, 95), listOf(20.0, 30.0, 55.0, 60.0), listOf(10, 20, 70, 90)) + "]"
        val w = RouteWeather.parse(json, samples, t0 * 1000)
        assertEquals(2, w.size)
        assertEquals(8.0, w[0].tempC, 0.0)
        assertEquals(t0 * 1000 + 2 * 3_600_000L, w[1].timeMs) // 2:20 → la hora 2
        assertEquals(-1.0, w[1].tempC, 0.0)
        assertEquals(71, w[1].code)

        val warn = RouteWeather.warnings(w)
        assertTrue(warn.toString(), warn.any { "Nieve" in it && "km 10" in it })
        assertTrue(warn.any { "Bajo cero" in it })
        assertTrue(warn.any { "Rachas de 55" in it })
        assertTrue(warn.any { "Lluvia probable (70 %)" in it })
        assertTrue(warn.none { "Tormenta" in it })
    }

    @Test
    fun `fuera de la prevision no devuelve nada`() {
        val t0 = 1_800_000_000L
        val json = loc(t0, listOf(8.0, 9.0), listOf(0, 0), listOf(5.0, 5.0), listOf(0, 0))
        val s = listOf(RouteSample(0.0, 40.7, -3.9, 1000.0, 0))
        assertEquals(1, RouteWeather.parse(json, s, t0 * 1000).size) // un solo punto: objeto, no lista
        assertTrue(RouteWeather.parse(json, s, (t0 + 10 * 3600) * 1000).isEmpty())
    }

    @Test
    fun `codigos del tiempo`() {
        assertEquals("Despejado", RouteWeather.describe(0).first)
        assertEquals("Tormenta", RouteWeather.describe(96).first)
        assertEquals("Nieve", RouteWeather.describe(73).first)
        assertEquals("Niebla", RouteWeather.describe(45).first)
    }
}
