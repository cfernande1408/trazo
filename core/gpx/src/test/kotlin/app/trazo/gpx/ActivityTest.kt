package app.trazo.gpx

import app.trazo.gpx.osm.WaymarkedTrails
import app.trazo.gpx.plan.BRouter
import app.trazo.gpx.ride.GpxWriter
import app.trazo.gpx.ride.RideCalculator
import app.trazo.gpx.ride.TimedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityTest {

    private val k = 111_195.0
    private fun p(m: Double, t: Long) = TimedPoint(40.0 + m / k, -4.0, null, t, 5f)

    @Test
    fun `tiempo MIDE en senderismo`() {
        // 10 km llanos: 2 h
        assertEquals(2 * 3_600_000L, Activity.HIKE.estimateMs(10_000.0, 0.0, 0.0))
        // 10 km con +800 m y -800 m: horizontal 2 h, vertical 2 h + 1 h 20 = 3 h 20; total 3 h 20 + 1 h = 4 h 20
        val ms = Activity.HIKE.estimateMs(10_000.0, 800.0, 800.0)
        assertEquals(4 * 60 + 20, (ms / 60_000).toInt())
    }

    @Test
    fun `tiempo estimado en bici`() {
        // 28 km y +600 m: 2 h + 1 h
        assertEquals(3 * 3_600_000L, Activity.BIKE.estimateMs(28_000.0, 600.0, 600.0))
    }

    @Test
    fun `subir andando despacio cuenta como movimiento solo en senderismo`() {
        // 1,5 km/h: 10 m cada 24 s
        val slow = (0..10).map { p(it * 10.0, it * 24_000L) }
        assertEquals(0L, RideCalculator.compute(listOf(slow), Activity.BIKE).movingMs)
        assertEquals(240_000L, RideCalculator.compute(listOf(slow), Activity.HIKE).movingMs)
    }

    @Test
    fun `a pie un salto a 30 kmh es error del gps`() {
        val a = p(0.0, 0)
        val b = p(83.0, 10_000) // 30 km/h
        assertTrue(RideCalculator.accept(a, b, Activity.BIKE))
        assertFalse(RideCalculator.accept(a, b, Activity.HIKE))
    }

    @Test
    fun `url de brouter con perfil y lon lat`() {
        val u = BRouter.url(listOf(GeoPoint(40.5, -4.1), GeoPoint(40.6, -4.0)), Activity.HIKE)
        assertTrue(u.contains("lonlats=-4.100000,40.500000|-4.000000,40.600000"))
        assertTrue(u.contains("profile=hiking-mountain"))
        assertTrue(u.contains("format=gpx"))
        assertTrue(BRouter.url(listOf(GeoPoint(1.0, 1.0), GeoPoint(2.0, 2.0)), Activity.BIKE).contains("profile=mtb"))
        assertThrows(IllegalArgumentException::class.java) { BRouter.url(listOf(GeoPoint(1.0, 1.0)), Activity.BIKE) }
    }

    @Test
    fun `gpx de ruta creada se vuelve a leer igual`() {
        val segs = listOf(listOf(GeoPoint(40.5, -4.1, 900.0), GeoPoint(40.51, -4.1, 950.0)))
        val gpx = GpxParser.parse(GpxWriter.writeRoute("Mi ruta <1>", segs))
        assertEquals("Mi ruta <1>", gpx.name)
        assertEquals(950.0, gpx.segments.single()[1].ele!!, 1e-6)
    }

    @Test
    fun `capas de rutas segun actividad`() {
        assertEquals(listOf(WaymarkedTrails.Flavour.HIKING), WaymarkedTrails.flavoursFor(Activity.HIKE))
        assertEquals(2, WaymarkedTrails.flavoursFor(Activity.BIKE).size)
    }
}
