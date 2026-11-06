package app.trazo.gpx

import org.junit.Assert.assertEquals
import org.junit.Test

class ElevationProfileTest {

    @Test
    fun `distancia acumulada y puntos sin elevacion fuera del perfil`() {
        val seg = listOf(GeoPoint(40.0, -4.0, 1000.0), GeoPoint(40.001, -4.0, null), GeoPoint(40.002, -4.0, 1010.0))
        val p = ElevationProfile.of(listOf(seg))
        assertEquals(2, p.size)
        assertEquals(0.0, p[0].distanceMeters, 1e-9)
        assertEquals(222.4, p[1].distanceMeters, 0.5)
        assertEquals(1010.0, p[1].ele, 1e-9)
    }

    @Test
    fun `el hueco entre segmentos no suma distancia`() {
        val a = listOf(GeoPoint(40.0, -4.0, 1.0), GeoPoint(40.001, -4.0, 2.0))
        val b = listOf(GeoPoint(45.0, -4.0, 3.0), GeoPoint(45.001, -4.0, 4.0))
        val p = ElevationProfile.of(listOf(a, b))
        assertEquals(p[1].distanceMeters, p[2].distanceMeters, 1e-9)
        assertEquals(222.4, p[3].distanceMeters, 0.5)
    }
}
