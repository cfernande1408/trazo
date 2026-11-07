package app.trazo.gpx.weather

import app.trazo.gpx.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherDetailTest {

    private val t0 = 1_762_502_400L // en segundos, una hora en punto

    /** Dos puntos: la salida (km 0) y el final (km 20), al que llegas en 2 h. */
    private val samples = listOf(
        RouteSample(0.0, 40.7, -3.9, 1000.0, 0),
        RouteSample(20_000.0, 40.8, -3.9, 2000.0, 2 * 3_600_000L),
    )

    private fun loc(temps: List<Double>, feels: List<Double>, precip: List<Double> = temps.map { 0.0 }, isDay: List<Int> = temps.map { 1 }, uv: Double = 2.0, gust: Double = 20.0) = """
        {"hourly":{"time":[${temps.indices.joinToString(",") { "${t0 + it * 3600}" }}],
         "temperature_2m":[${temps.joinToString(",")}],
         "apparent_temperature":[${feels.joinToString(",")}],
         "precipitation_probability":[${precip.joinToString(",") { if (it > 0) "70" else "5" }}],
         "precipitation":[${precip.joinToString(",")}],
         "weather_code":[${precip.joinToString(",") { if (it > 0) "61" else "1" }}],
         "wind_speed_10m":[${temps.joinToString(",") { "12.0" }}],
         "wind_gusts_10m":[${temps.joinToString(",") { "$gust" }}],
         "cloud_cover":[${temps.joinToString(",") { "40" }}],
         "uv_index":[${temps.joinToString(",") { "$uv" }}],
         "is_day":[${isDay.joinToString(",")}]}}"""

    @Test
    fun `una fila cada 15 minutos donde estaras`() {
        val json = "[" + loc(listOf(10.0, 12.0, 14.0, 16.0), listOf(8.0, 10.0, 12.0, 14.0)) + "," +
            loc(listOf(4.0, 6.0, 8.0, 10.0), listOf(2.0, 4.0, 6.0, 8.0)) + "]"
        val rows = WeatherDetail.rows(json, samples, t0 * 1000)
        assertEquals(9, rows.size) // 2 h: 0, 15, 30… 120 min
        assertEquals(0.0, rows.first().distanceMeters, 0.1)
        assertEquals(20_000.0, rows.last().distanceMeters, 0.1)
        assertEquals(10_000.0, rows[4].distanceMeters, 0.1)
        // Salida: 10 °C en el punto de abajo. Final (2 h después): 8 °C en el de arriba
        assertEquals(10.0, rows.first().tempC, 0.01)
        assertEquals(8.0, rows.last().tempC, 0.01)
        // A la hora (km 10): mitad entre 12 °C abajo y 6 °C arriba
        assertEquals(9.0, rows[4].tempC, 0.01)
        // A los 30 min: entre horas y entre puntos
        val f = 0.25
        val expected = (11.0) + (5.0 - 11.0) * f
        assertEquals(expected, rows[2].tempC, 0.01)
        assertTrue(rows.zipWithNext().all { (a, b) -> b.timeMs - a.timeMs == 15 * 60_000L })
    }

    @Test
    fun `se salta lo que queda fuera de la prevision`() {
        val json = "[" + loc(listOf(10.0, 12.0), listOf(8.0, 10.0)) + "," + loc(listOf(4.0, 6.0), listOf(2.0, 4.0)) + "]"
        val rows = WeatherDetail.rows(json, samples, t0 * 1000)
        assertEquals(5, rows.size) // solo la primera hora tiene previsión
        assertTrue(WeatherDetail.rows("[]", samples, t0 * 1000).isEmpty())
    }

    @Test
    fun `que ponerse con frio y lluvia`() {
        val cold = "[" + loc(listOf(2.0, 2.0, 2.0, 2.0), listOf(-1.0, -1.0, -1.0, -1.0), precip = listOf(0.0, 2.5, 3.0, 0.0), isDay = listOf(1, 1, 0, 0)) + "," +
            loc(listOf(0.0, 0.0, 0.0, 0.0), listOf(-3.0, -3.0, -3.0, -3.0), precip = listOf(0.0, 2.5, 3.0, 0.0), isDay = listOf(1, 1, 0, 0)) + "]"
        val wear = WeatherDetail.whatToWear(WeatherDetail.rows(cold, samples, t0 * 1000), Activity.BIKE)
        val text = wear.joinToString("\n")
        assertTrue(text, text.contains("tres capas"))
        assertTrue(text, text.contains("culotte largo"))
        assertTrue(text, text.contains("Guantes de invierno"))
        assertTrue(text, text.contains("Chubasquero puesto"))
        assertTrue(text, text.contains("luces"))
    }

    @Test
    fun `que ponerse con calor y sol`() {
        val hot = "[" + loc(listOf(26.0, 28.0, 30.0, 31.0), listOf(27.0, 29.0, 31.0, 32.0), uv = 8.0) + "," +
            loc(listOf(24.0, 26.0, 28.0, 29.0), listOf(25.0, 27.0, 29.0, 30.0), uv = 8.0) + "]"
        val wear = WeatherDetail.whatToWear(WeatherDetail.rows(hot, samples, t0 * 1000), Activity.HIKE)
        val text = wear.joinToString("\n")
        assertTrue(text, text.contains("manga corta transpirable"))
        assertTrue(text, text.contains("pantalón corto"))
        assertTrue(text, text.contains("UV 8"))
        assertTrue(text, text.contains("agua de sobra"))
        assertFalse(text, text.contains("Chubasquero"))
        assertFalse(text, text.contains("Guantes"))
    }

    @Test
    fun `en bici se cuenta el aire de la marcha`() {
        val mild = "[" + loc(listOf(17.0, 17.0, 17.0), listOf(16.0, 16.0, 16.0)) + "," + loc(listOf(17.0, 17.0, 17.0), listOf(16.0, 16.0, 16.0)) + "]"
        val rows = WeatherDetail.rows(mild, samples, t0 * 1000)
        assertTrue(WeatherDetail.whatToWear(rows, Activity.HIKE).first().contains("manga corta"))
        assertTrue(WeatherDetail.whatToWear(rows, Activity.BIKE).first().contains("manga larga"))
    }
}
