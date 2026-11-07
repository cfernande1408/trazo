package app.trazo.gpx.nav

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.TrackStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RejoinTest {

    private val lat0 = 40.6
    private val mPerDegLon = 111_195.0 * Math.cos(Math.toRadians(lat0))

    /** Punto a [east] m al este del origen y [north] m al norte. */
    private fun p(east: Double, north: Double = 0.0) = GeoPoint(lat0 + north / 111_195.0, -3.9 + east / mPerDegLon)

    /** Ruta recta de 3 km hacia el este, con un punto cada 100 m. */
    private val route = listOf((0..30).map { p(it * 100.0) })

    private fun length(segs: List<List<GeoPoint>>) =
        segs.sumOf { s -> (1 until s.size).sumOf { TrackStatsCalculator.haversineMeters(s[it - 1], s[it]) } }

    @Test
    fun `vuelve al punto mas cercano por delante`() {
        // Dejaste la ruta en el km 0,3 y vas por un camino paralelo, 200 m al norte, a la altura del 0,8
        val t = Rejoin.target(route, lastAlongMeters = 300.0, here = p(800.0, 200.0))
        assertNotNull(t)
        assertEquals(800.0, t!!.alongMeters, 15.0)
        assertEquals(200.0, t.straightMeters, 10.0)
    }

    @Test
    fun `no te manda hacia atras`() {
        // Estás a la altura del 0,2, pero la dejaste en el 0,5: se vuelve al 0,5
        val t = Rejoin.target(route, lastAlongMeters = 500.0, here = p(200.0, 100.0))!!
        assertEquals(500.0, t.alongMeters, 1.0)
    }

    @Test
    fun `solo mira hasta 1,5 km por delante`() {
        val t = Rejoin.target(route, lastAlongMeters = 0.0, here = p(2500.0, 50.0))!!
        assertEquals(1500.0, t.alongMeters, 30.0)
    }

    @Test
    fun `si ya has pasado el final vuelve al final`() {
        val t = Rejoin.target(route, lastAlongMeters = 2900.0, here = p(3200.0, 30.0))!!
        assertEquals(3000.0, t.alongMeters, 15.0)
        assertNull(Rejoin.target(emptyList(), 0.0, p(0.0)))
    }

    @Test
    fun `une el camino de vuelta con el resto de la ruta`() {
        val back = listOf(p(800.0, 200.0), p(800.0, 100.0), p(800.0))
        val joined = Rejoin.join(back, route, 800.0)
        assertEquals(1, joined.size)
        assertEquals(back.first(), joined.first().first())
        assertEquals(route.first().last(), joined.last().last())
        // 200 m de vuelta y los 2,2 km que quedan, sin repetir el punto de unión
        assertEquals(2400.0, length(joined), 5.0)
        assertTrue(joined.first().zipWithNext().none { (a, b) -> a == b })
    }

    @Test
    fun `el resto empieza justo en el punto`() {
        val rest = Rejoin.tail(route, 1250.0)
        assertEquals(1750.0, length(rest), 2.0)
        assertTrue(TrackStatsCalculator.haversineMeters(rest.first().first(), p(1250.0)) < 1.0)
        assertTrue(Rejoin.tail(route, 5000.0).isEmpty())
    }

    @Test
    fun `los metros de la ruta recalculada pasan a los de la original`() {
        val back = listOf(p(800.0, 200.0), p(800.0))
        val t = Rejoin.target(route, 300.0, p(800.0, 200.0))!!
        val r = Rejoin.reroute(back, route, t, leftAt = 300.0)
        assertEquals(200.0, r.pathMeters, 2.0)
        // Por el camino de vuelta sigues "donde la dejaste"; ya en la ruta, avanza con ella
        assertEquals(300.0, r.routeAlong(100.0), 0.1)
        assertEquals(1300.0, r.routeAlong(700.0), 20.0)
        assertEquals("Ruta recalculada. Vuelves a la ruta en 200 metros.", Rejoin.phrase(r))

        // Te vuelves a salir sobre la recalculada, 500 m después de volver (km 1,3 de la original)
        val t2 = Rejoin.target(r.segments, 700.0, p(1600.0, 150.0))!!
        val r2 = Rejoin.reroute(listOf(p(1600.0, 150.0), p(1600.0)), r.segments, t2, leftAt = 700.0, previous = r)
        assertEquals(1300.0, r2.leftAt, 20.0)
        assertEquals(1600.0, r2.joinAt, 20.0)
        assertEquals(3000.0 - 1600.0 + 150.0, length(r2.segments), 25.0)
    }
}
