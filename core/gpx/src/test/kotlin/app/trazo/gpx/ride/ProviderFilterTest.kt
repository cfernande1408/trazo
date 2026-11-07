package app.trazo.gpx.ride

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.TrackStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFilterTest {

    @Test
    fun `la red solo vale mientras no hay GPS`() {
        val f = ProviderFilter()
        assertTrue(f.accept("network", 0))
        assertTrue(f.accept("network", 2_000))
        assertTrue(f.accept("gps", 3_000))
        assertFalse(f.accept("network", 4_000))
        assertFalse(f.accept("network", 13_000))
        // Más de 10 s sin GPS (túnel, bajo techo): vuelve la red
        assertTrue(f.accept("network", 13_001))
        assertTrue(f.accept("gps", 20_000))
        assertFalse(f.accept("network", 21_000))
    }

    @Test
    fun `solo se fia de la altitud del GPS`() {
        val f = ProviderFilter()
        assertTrue(f.trustAltitude("gps"))
        assertFalse(f.trustAltitude("network"))
        assertFalse(f.trustAltitude(null))
    }

    @Test
    fun `sin la red alternando no hay picos`() {
        // Paseo llano a 650 m: el GPS dice 650 ± 2 y la red, intercalada, 0 m
        val fixes = (0 until 600).map { i -> if (i % 2 == 0) "gps" to 650.0 + (i % 4) - 1 else "network" to 0.0 }
        fun gain(eles: List<Double?>) =
            TrackStatsCalculator.compute(listOf(eles.map { GeoPoint(40.39, -3.75, it) })).elevationGainMeters
        assertTrue(gain(fixes.map { it.second }) > 100_000)
        val f = ProviderFilter()
        val kept = fixes.mapIndexedNotNull { i, (p, ele) ->
            if (f.accept(p, i * 1000L)) (if (f.trustAltitude(p)) ele else null) else null
        }
        assertEquals(300, kept.size)
        assertTrue(gain(kept) < 5)
    }
}
