package app.trazo.gpx.ride

import app.trazo.gpx.GeoJson
import app.trazo.gpx.GpxParser
import app.trazo.gpx.osm.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideTest {

    private val k = 111_195.0
    private fun p(northM: Double, t: Long, ele: Double? = null, acc: Float? = 5f) =
        TimedPoint(40.0 + northM / k, -4.0, ele, t, acc)

    /** [n] puntos cada 10 s avanzando 50 m (18 km/h). */
    private fun steady(n: Int, t0: Long = 0, m0: Double = 0.0) = (0 until n).map { p(m0 + it * 50.0, t0 + it * 10_000L) }

    @Test
    fun `ritmo constante a 18 kmh`() {
        val s = RideCalculator.compute(listOf(steady(10)))
        assertEquals(450.0, s.distanceMeters, 1.0)
        assertEquals(90_000L, s.movingMs)
        assertEquals(18.0, s.avgMovingKmh, 0.1)
        assertEquals(18.0, s.maxKmh, 0.1)
    }

    @Test
    fun `parado no cuenta como tiempo en movimiento`() {
        val moving = steady(5)
        val stopped = (1..6).map { p(200.0 + 1.0, 40_000L + it * 10_000L) } // un metro en un minuto
        val s = RideCalculator.compute(listOf(moving + stopped))
        assertEquals(40_000L, s.movingMs)
        assertEquals(100_000L, s.totalMs)
    }

    @Test
    fun `el filtro descarta saltos, temblor y mala precision`() {
        val a = p(0.0, 0)
        assertFalse("salto de 500 m en 2 s", RideCalculator.accept(a, p(500.0, 2_000)))
        assertFalse("temblor de 1 m", RideCalculator.accept(a, p(1.0, 5_000)))
        assertFalse("precisión de 60 m", RideCalculator.accept(a, p(50.0, 10_000, acc = 60f)))
        assertTrue(RideCalculator.accept(a, p(50.0, 10_000)))
    }

    @Test
    fun `la pausa abre otro tramo y no suma distancia`() {
        val r = RideRecorder()
        steady(3).forEach { r.add(it) }
        r.pause()
        assertFalse(r.add(p(5_000.0, 40_000)))
        r.resume()
        steady(3, t0 = 100_000, m0 = 3_000.0).forEach { r.add(it) }
        assertEquals(2, r.points.size)
        assertEquals(200.0, r.stats().distanceMeters, 1.0)
    }

    @Test
    fun `gpx de ida y vuelta conserva puntos y horas`() {
        val segs = listOf(steady(4).map { it.copy(ele = 600.0) })
        val xml = GpxWriter.write("Salida & vuelta", segs)
        val gpx = GpxParser.parse(xml)
        assertEquals("Salida & vuelta", gpx.name)
        val back = RideCalculator.fromGpx(gpx)
        assertEquals(segs.single().map { it.timeMs }, back.single().map { it.timeMs })
        assertEquals(segs.single()[2].lat, back.single()[2].lat, 1e-7)
        assertEquals(RideCalculator.compute(segs).distanceMeters, RideCalculator.compute(back).distanceMeters, 0.5)
    }

    @Test
    fun `lineas por velocidad con la propiedad kmh`() {
        val json = GeoJson.speedLines(listOf(steady(5)), stepMeters = 100.0)
        val features = (Json.parse(json) as Map<*, *>)["features"] as List<*>
        assertEquals(2, features.size)
        val kmh = ((features[0] as Map<*, *>)["properties"] as Map<*, *>)["kmh"] as Double
        assertEquals(18.0, kmh, 0.2)
    }
}
