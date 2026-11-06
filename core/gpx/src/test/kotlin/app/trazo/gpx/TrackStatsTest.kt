package app.trazo.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackStatsTest {

    private fun line(vararg eles: Double?) = eles.mapIndexed { i, e -> GeoPoint(40.0 + i * 0.001, -4.0, e) }

    @Test
    fun `un grado de latitud son unos 111,2 km`() {
        val d = TrackStatsCalculator.haversineMeters(GeoPoint(40.0, -4.0), GeoPoint(41.0, -4.0))
        assertEquals(111_195.0, d, 1.0)
    }

    @Test
    fun `no suma el hueco entre segmentos`() {
        val a = listOf(GeoPoint(40.0, -4.0), GeoPoint(40.001, -4.0))
        val b = listOf(GeoPoint(41.0, -4.0), GeoPoint(41.001, -4.0))
        val one = TrackStatsCalculator.compute(listOf(a)).distanceMeters
        val both = TrackStatsCalculator.compute(listOf(a, b)).distanceMeters
        assertEquals(one * 2, both, 0.5)
    }

    @Test
    fun `el ruido del gps no cuenta como desnivel`() {
        val s = TrackStatsCalculator.compute(listOf(line(1000.0, 1001.5, 999.0, 1002.0, 1000.0, 1001.0)))
        assertEquals(0.0, s.elevationGainMeters, 1e-9)
        assertEquals(0.0, s.elevationLossMeters, 1e-9)
    }

    @Test
    fun `sube y baja de verdad`() {
        val up = (0..10).map { 1000.0 + it * 10 }
        val down = (1..5).map { 1100.0 - it * 10 }
        val s = TrackStatsCalculator.compute(listOf(line(*(up + down).toTypedArray())))
        assertEquals(100.0, s.elevationGainMeters, 1e-9)
        assertEquals(50.0, s.elevationLossMeters, 1e-9)
        assertEquals(1000.0, s.minElevation!!, 1e-9)
        assertEquals(1100.0, s.maxElevation!!, 1e-9)
    }

    @Test
    fun `puntos sin elevacion no rompen el calculo`() {
        val s = TrackStatsCalculator.compute(listOf(line(null, 1000.0, null, 1020.0)))
        assertEquals(20.0, s.elevationGainMeters, 1e-9)
        assertEquals(4, s.pointCount)
    }

    @Test
    fun `track vacio da ceros`() {
        val s = TrackStatsCalculator.compute(emptyList())
        assertEquals(0.0, s.distanceMeters, 0.0)
        assertNull(s.maxElevation)
        assertEquals(0, s.pointCount)
    }

    @Test
    fun `estadisticas del gpx de ejemplo`() {
        val gpx = javaClass.getResourceAsStream("/wikiloc_sample.gpx")!!.use(GpxParser::parse)
        val s = TrackStatsCalculator.compute(gpx.segments)
        assertEquals(40.0, s.elevationGainMeters, 1e-9)
        assertEquals(10.0, s.elevationLossMeters, 1e-9)
        assertEquals(5, s.pointCount)
    }
}
