package app.trazo.gpx.ride

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.TrackStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class BaroAltimeterTest {

    /** Presión a una altitud, con [weather] hPa de más o de menos por el tiempo que hace. */
    private fun pressureAt(h: Double, weather: Double = 0.0) = 1013.25 * (1 - h / 44_330.0).pow(5.255) + weather

    /** Ruido del GPS: ±15 m alternando, que es lo que infla el desnivel. */
    private fun noise(i: Int) = if (i % 2 == 0) 15.0 else -15.0

    private fun gain(eles: List<Double>) =
        TrackStatsCalculator.compute(listOf(eles.map { GeoPoint(40.0, -3.7, it) })).elevationGainMeters

    @Test
    fun `atmosfera estandar`() {
        assertEquals(0.0, BaroAltimeter.pressureAltitude(1013.25), 0.01)
        assertEquals(1000.0, BaroAltimeter.pressureAltitude(898.75), 1.0)
    }

    @Test
    fun `sin barometro devuelve el GPS`() {
        val b = BaroAltimeter()
        assertEquals(712.0, b.altitude(712.0, 5f, 0)!!, 0.0)
        assertFalse(b.active)
    }

    @Test
    fun `subida con GPS ruidoso`() {
        val b = BaroAltimeter()
        // 600 s subiendo de 800 a 900 m, con un día de bajas presiones (-6 hPa, unos 50 m)
        val fused = (0..600).map { t ->
            val h = 800 + t / 6.0
            b.onPressure(pressureAt(h, weather = -6.0), t * 1000L)
            b.altitude(h + noise(t), 8f, t * 1000L)!!
        }
        assertTrue(b.active)
        // Tras calibrar, la altitud va pegada a la real
        for (t in 10..600) assertTrue("t=$t: ${fused[t]}", abs(fused[t] - (800 + t / 6.0)) < 5)
        val gpsGain = gain((0..600).map { t -> 800 + t / 6.0 + noise(t) })
        // Desde que calibra (los primeros segundos son GPS): 98 m reales
        val baroGain = gain(fused.drop(10))
        assertTrue("barómetro $baroGain", abs(baroGain - 590 / 6.0) < 5)
        assertTrue("GPS $gpsGain", gpsGain > 1000)
    }

    @Test
    fun `corrige la deriva del tiempo`() {
        val b = BaroAltimeter()
        // Una hora parado a 1200 m mientras la presión baja 1 hPa (unos 9 m de deriva)
        var last = 0.0
        for (t in 0..3600) {
            b.onPressure(pressureAt(1200.0, weather = -t / 3600.0), t * 1000L)
            last = b.altitude(1200 + noise(t), 8f, t * 1000L)!!
        }
        assertEquals(1200.0, last, 3.0)
    }

    @Test
    fun `no calibra con GPS impreciso`() {
        val b = BaroAltimeter()
        for (t in 0..20) {
            b.onPressure(pressureAt(500.0), t * 1000L)
            assertEquals(900.0, b.altitude(900.0, 60f, t * 1000L)!!, 0.0)
        }
        assertFalse(b.active)
    }

    @Test
    fun `vuelve al GPS si el sensor se calla`() {
        val b = BaroAltimeter()
        for (t in 0..10) {
            b.onPressure(pressureAt(300.0), t * 1000L)
            b.altitude(300.0, 5f, t * 1000L)
        }
        assertEquals(300.0, b.altitude(300.0, 5f, 11_000)!!, 0.5)
        // 15 s sin presión: se fía del GPS
        assertEquals(350.0, b.altitude(350.0, 5f, 25_000)!!, 0.0)
    }

    @Test
    fun `ignora saltos enormes del GPS`() {
        val b = BaroAltimeter()
        for (t in 0..10) {
            b.onPressure(pressureAt(1000.0), t * 1000L)
            b.altitude(1000.0, 5f, t * 1000L)
        }
        b.onPressure(pressureAt(1000.0), 11_000)
        assertEquals(1000.0, b.altitude(1500.0, 5f, 11_000)!!, 0.5)
    }
}
