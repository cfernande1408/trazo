package app.trazo.gpx.nav

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.ProfilePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val M_PER_DEG = 111_195.0

/** Punto [north] metros al norte y [east] metros al este de (40, -4). */
private fun at(north: Double, east: Double = 0.0, ele: Double? = null) =
    GeoPoint(40.0 + north / M_PER_DEG, -4.0 + east / (M_PER_DEG * kotlin.math.cos(Math.toRadians(40.0))), ele)

/** Línea recta hacia el norte de [length] m, un punto cada 100 m. */
private fun northLine(length: Int, ele: (Int) -> Double? = { null }) =
    (0..length step 100).map { at(it.toDouble(), ele = ele(it)) }

class TrackMatcherTest {

    @Test
    fun `proyecta un punto a un lado del track`() {
        val m = TrackMatcher(listOf(northLine(1000)))
        val p = at(500.0, 20.0)
        val pos = m.locate(p.lat, p.lon)
        assertEquals(500.0, pos.alongMeters, 1.0)
        assertEquals(20.0, pos.distanceToTrackMeters, 0.5)
    }

    @Test
    fun `en ida y vuelta se queda en el tramo por el que vas`() {
        val ida = northLine(1000)
        val vuelta = ida.reversed().drop(1)
        val m = TrackMatcher(listOf(ida + vuelta))
        val p = at(450.0, 5.0)
        assertEquals(450.0, m.locate(p.lat, p.lon).alongMeters, 1.0)
        assertEquals(1550.0, m.locate(p.lat, p.lon, previousAlong = 1450.0).alongMeters, 1.0)
    }

    @Test
    fun `no une el hueco entre segmentos`() {
        val a = northLine(500)
        val b = listOf(at(2000.0), at(2500.0))
        val m = TrackMatcher(listOf(a, b))
        val p = at(1200.0) // en medio del hueco
        val pos = m.locate(p.lat, p.lon)
        assertTrue("debe estar lejos del track", pos.distanceToTrackMeters > 600)
    }
}

class ClimbDetectorTest {

    private fun profile(vararg pts: Pair<Int, Int>) = pts.map { ProfilePoint(it.first.toDouble(), it.second.toDouble()) }

    @Test
    fun `detecta una subida con un rellano pequeno dentro`() {
        val p = profile(
            0 to 600, 1000 to 600,            // llano
            1400 to 640, 1500 to 635,         // rellano de 5 m: no corta
            2000 to 680,                      // cima
            2500 to 640, 2600 to 650, 3000 to 620, // repecho de 10 m: no es subida
        )
        val climbs = ClimbDetector.detect(p)
        assertEquals(1, climbs.size)
        val c = climbs.single()
        assertEquals(1000.0, c.startMeters, 1e-9)
        assertEquals(2000.0, c.endMeters, 1e-9)
        assertEquals(80.0, c.gainMeters, 1e-9)
        assertEquals(8.0, c.avgGradePercent, 1e-9)
    }

    @Test
    fun `una bajada grande separa dos subidas`() {
        val p = profile(0 to 600, 500 to 650, 700 to 620, 1200 to 680)
        val climbs = ClimbDetector.detect(p)
        assertEquals(listOf(0.0 to 500.0, 700.0 to 1200.0), climbs.map { it.startMeters to it.endMeters })
    }

    @Test
    fun `subida demasiado suave no cuenta`() {
        assertTrue(ClimbDetector.detect(profile(0 to 600, 5000 to 650)).isEmpty())
    }
}

class NavigatorTest {

    @Test
    fun `quedan distancia y desnivel desde donde estas`() {
        // 1 km subiendo de 600 a 700 m
        val nav = Navigator(listOf(northLine(1000) { 600.0 + it / 10.0 }))
        val p = at(500.0)
        val s = nav.update(p.lat, p.lon, 0)
        assertEquals(500.0, s.remainingMeters, 1.0)
        assertEquals(50.0, s.remainingGainMeters, 1.0)
        assertFalse(s.offRoute)
    }

    @Test
    fun `el desvio se declara tras 10 s lejos y avisa una sola vez`() {
        val nav = Navigator(listOf(northLine(1000)))
        val onTrack = at(100.0)
        val far = at(200.0, 60.0)
        nav.update(onTrack.lat, onTrack.lon, 0)

        val starts = (1..12).map { s -> nav.update(far.lat, far.lon, s * 1000L) }
        assertFalse("a los 9 s aún no", starts[8].offRoute)
        assertTrue("a los 11 s ya sí", starts[10].offRoute)
        assertEquals(1, starts.count { it.offRouteStarted })

        val back = at(300.0, 30.0) // a 30 m: aún no vuelve (umbral de vuelta 25 m)
        assertTrue(nav.update(back.lat, back.lon, 13_000).offRoute)
        val onAgain = at(300.0, 10.0)
        assertFalse(nav.update(onAgain.lat, onAgain.lon, 14_000).offRoute)
    }

    @Test
    fun `un salto corto del gps no es desvio`() {
        val nav = Navigator(listOf(northLine(1000)))
        val far = at(200.0, 60.0)
        val on = at(210.0, 5.0)
        nav.update(far.lat, far.lon, 0)
        nav.update(far.lat, far.lon, 5_000)
        nav.update(on.lat, on.lon, 6_000)
        assertFalse(nav.update(far.lat, far.lon, 12_000).offRoute)
    }

    @Test
    fun `con mala precision no avisa si podrias estar en el track`() {
        val nav = Navigator(listOf(northLine(1000)))
        val p = at(200.0, 60.0) // a 60 m, pero con 30 m de error podría estar a 30
        val s = (0..12).map { nav.update(p.lat, p.lon, it * 1000L, accuracyMeters = 30.0) }
        assertFalse(s.last().offRoute)
    }

    @Test
    fun `lejisimos avisa aunque la precision sea mala`() {
        val nav = Navigator(listOf(northLine(1000)))
        val p = at(200.0, 25_000.0)
        val s = (0..11).map { nav.update(p.lat, p.lon, it * 1000L, accuracyMeters = 500.0) }
        assertTrue(s.last().offRoute)
        assertEquals(25_000.0, s.last().distanceToTrackMeters, 50.0)
    }

    @Test
    fun `una posicion imprecisa no te hace avanzar`() {
        val nav = Navigator(listOf(northLine(1000)))
        val a = at(100.0)
        nav.update(a.lat, a.lon, 0)
        val b = at(600.0, 10.0)
        assertEquals(100.0, nav.update(b.lat, b.lon, 1000, accuracyMeters = 120.0).alongMeters, 1.0)
        assertEquals(600.0, nav.update(b.lat, b.lon, 2000, accuracyMeters = 8.0).alongMeters, 1.0)
    }

    @Test
    fun `informa de la proxima subida`() {
        val ele: (Int) -> Double = { d -> if (d < 2000) 600.0 else 600.0 + (d - 2000) / 10.0 }
        val nav = Navigator(listOf(northLine(3000, ele)))
        val p = at(500.0)
        val s = nav.update(p.lat, p.lon, 0)
        assertNotNull(s.nextClimb)
        assertEquals(1500.0, s.metersToNextClimb!!, 1.0)
        assertEquals(100.0, s.nextClimb!!.gainMeters, 1e-6)
    }
}
