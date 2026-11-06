package app.trazo.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrubTest {

    private val k = 111_195.0
    private fun n(m: Double, ele: Double? = null) = GeoPoint(40.0 + m / k, -4.0, ele)

    @Test
    fun `punto a mitad de un tramo con altitud interpolada`() {
        val p = RouteGeometry.pointAt(listOf(listOf(n(0.0, 600.0), n(1000.0, 700.0))), 250.0)!!
        assertEquals(40.0 + 250.0 / k, p.lat, 1e-7)
        assertEquals(625.0, p.ele!!, 0.1)
    }

    @Test
    fun `fuera de rango devuelve los extremos`() {
        val segs = listOf(listOf(n(0.0), n(1000.0)))
        assertEquals(n(0.0), RouteGeometry.pointAt(segs, -5.0))
        assertEquals(n(1000.0), RouteGeometry.pointAt(segs, 99_999.0))
        assertNull(RouteGeometry.pointAt(emptyList(), 10.0))
    }

    @Test
    fun `el hueco entre segmentos no cuenta`() {
        val segs = listOf(listOf(n(0.0), n(500.0)), listOf(n(3000.0), n(3500.0)))
        assertEquals(40.0 + 3200.0 / k, RouteGeometry.pointAt(segs, 700.0)!!.lat, 1e-7)
    }

    @Test
    fun `altitud del perfil en un km cualquiera`() {
        val profile = listOf(ProfilePoint(0.0, 600.0), ProfilePoint(1000.0, 700.0), ProfilePoint(2000.0, 650.0))
        assertEquals(650.0, ElevationProfile.eleAt(profile, 500.0)!!, 1e-9)
        assertEquals(675.0, ElevationProfile.eleAt(profile, 1500.0)!!, 1e-9)
        assertEquals(650.0, ElevationProfile.eleAt(profile, 5000.0)!!, 1e-9)
        assertNull(ElevationProfile.eleAt(emptyList(), 1.0))
    }
}
