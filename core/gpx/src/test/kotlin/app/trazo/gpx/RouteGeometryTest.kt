package app.trazo.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeometryTest {

    @Test
    fun `bounds de varios segmentos`() {
        val b = RouteGeometry.bounds(listOf(listOf(GeoPoint(40.0, -4.0)), listOf(GeoPoint(41.0, -3.0))))!!
        assertEquals(Bounds(40.0, 41.0, -4.0, -3.0), b)
        assertNull(RouteGeometry.bounds(emptyList()))
    }

    @Test
    fun `proyecta dentro del recuadro con el norte arriba`() {
        val sur = GeoPoint(40.0, -4.0)
        val norte = GeoPoint(40.01, -4.0)
        val este = GeoPoint(40.005, -3.99)
        val xy = RouteGeometry.project(listOf(listOf(sur, norte, este)), 200f, 100f, padding = 10f).single()
        xy.forEach {
            assertTrue(it.x in 10f..190f)
            assertTrue(it.y in 10f..90f)
        }
        assertTrue("el norte va arriba", xy[1].y < xy[0].y)
        assertTrue("el este va a la derecha", xy[2].x > xy[0].x)
    }

    @Test
    fun `no deforma la ruta`() {
        // Cuadrado de ~1 km de lado a 40 grados de latitud
        val dLat = 0.009
        val dLon = 0.009 / kotlin.math.cos(Math.toRadians(40.0))
        val sq = listOf(GeoPoint(40.0, 0.0), GeoPoint(40.0, dLon), GeoPoint(40.0 + dLat, dLon))
        val xy = RouteGeometry.project(listOf(sq), 300f, 100f).single()
        val ancho = xy[1].x - xy[0].x
        val alto = xy[1].y - xy[2].y
        assertEquals(1.0, (ancho / alto).toDouble(), 0.01)
        assertEquals(100.0, alto.toDouble(), 0.5)
    }

    @Test
    fun `un unico punto queda centrado`() {
        val xy = RouteGeometry.project(listOf(listOf(GeoPoint(40.0, -4.0))), 100f, 50f).single().single()
        assertEquals(XY(50f, 25f), xy)
    }

    @Test
    fun `downsample conserva extremos y tamano`() {
        val r = RouteGeometry.downsample((0..999).toList(), 10)
        assertEquals(10, r.size)
        assertEquals(0, r.first())
        assertEquals(999, r.last())
        assertEquals((0..4).toList(), RouteGeometry.downsample((0..4).toList(), 10))
        assertThrows(IllegalArgumentException::class.java) { RouteGeometry.downsample(listOf(1), 1) }
    }
}
