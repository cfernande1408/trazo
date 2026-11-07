package app.trazo.gpx.safety

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.TrackStatsCalculator
import app.trazo.gpx.nav.Retrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyTest {

    @Test
    fun `utm igual que la libreria de referencia`() {
        // Valores de la librería Python utm 0.7
        fun check(lat: Double, lon: Double, e: Double, n: Double, zone: Int, band: Char) {
            val u = Coordinates.toUtm(lat, lon)
            assertEquals(zone, u.zone)
            assertEquals(band, u.band)
            assertEquals(e, u.easting, 1.0)
            assertEquals(n, u.northing, 1.0)
        }
        check(40.4168, -3.7038, 440290.5, 4474257.4, 30, 'T') // Puerta del Sol
        check(40.7853, -3.9786, 417431.2, 4515384.4, 30, 'T') // Bola del Mundo
        check(-33.8688, 151.2093, 334368.6, 6250948.3, 56, 'H') // Sídney
        check(64.1466, -21.9426, 454138.4, 7113689.9, 27, 'W') // Reikiavik
        assertEquals("30T 440290 4474257", Coordinates.toUtm(40.4168, -3.7038).toString())
    }

    @Test
    fun `grados minutos y segundos y mensaje de ayuda`() {
        assertEquals("40°25'00.5\"N 3°42'13.7\"W", Coordinates.dms(40.4168, -3.7038))
        val sms = Coordinates.sosMessage(40.7853, -3.9786, 2262.4, 8.2, "12:34")
        assertTrue(sms, sms.startsWith("Necesito ayuda. Mi posición a las 12:34: 40.78530, -3.97860 (UTM 30T 417431 4515384), 2262 m de altitud, ±8 m."))
        assertTrue(sms.contains("https://www.openstreetmap.org/?mlat=40.78530&mlon=-3.97860"))
        assertTrue(sms.length < 300)
    }

    private fun feed(d: FallDetector, fromMs: Long, toMs: Long, g: Double): FallDetector.Event {
        var last = FallDetector.Event.NONE
        var t = fromMs
        while (t < toMs) {
            val e = d.onSample(t, g * 9.80665)
            if (e != FallDetector.Event.NONE) last = e
            t += 50
        }
        return last
    }

    @Test
    fun `golpe y quieto es una caida`() {
        val d = FallDetector()
        feed(d, 0, 5_000, 1.0)
        assertEquals(FallDetector.Event.IMPACT, d.onSample(5_000, 4.0 * 9.80665))
        assertEquals(FallDetector.Event.FALL, feed(d, 5_050, 26_000, 1.02))
        // No vuelve a saltar enseguida
        d.onSample(30_000, 5.0 * 9.80665)
        assertEquals(FallDetector.Event.NONE, feed(d, 30_050, 60_000, 1.0))
    }

    @Test
    fun `golpe y sigues en marcha no es caida`() {
        val d = FallDetector()
        d.onSample(0, 4.0 * 9.80665)
        // Bache: sigues pedaleando, la aceleración baila
        var t = 50L
        var e = FallDetector.Event.NONE
        while (t < 30_000) {
            val g = if ((t / 200) % 2 == 0L) 1.6 else 0.5
            d.onSample(t, g * 9.80665).let { if (it == FallDetector.Event.FALL) e = it }
            t += 50
        }
        assertEquals(FallDetector.Event.NONE, e)
        // Y si el GPS dice que avanzas, tampoco
        val d2 = FallDetector()
        d2.onSample(0, 4.0 * 9.80665)
        d2.onSpeed(4.0)
        assertEquals(FallDetector.Event.NONE, feed(d2, 50, 30_000, 1.0))
    }

    @Test
    fun `volver al inicio por donde has venido`() {
        val track = listOf((0..100).map { GeoPoint(40.0 + it * 0.0001, -3.9) })
        val back = Retrace.backToStart(track, 500.0).single()
        assertEquals(40.0, back.last().lat, 1e-9)
        assertEquals(500.0, TrackStatsCalculator.compute(listOf(back)).distanceMeters, 15.0)
        assertTrue(back.first().lat > back.last().lat)
        assertTrue(Retrace.backToStart(track, 0.0).isEmpty())
    }
}
