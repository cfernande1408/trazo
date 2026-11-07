package app.trazo.gpx

import app.trazo.gpx.nav.Climb
import app.trazo.gpx.nav.ClimbLive
import app.trazo.gpx.nav.NavState
import app.trazo.gpx.nav.TurnKind
import app.trazo.gpx.nav.Turns
import app.trazo.gpx.nav.VoiceGuide
import app.trazo.gpx.osm.Poi
import app.trazo.gpx.osm.PoiKind
import app.trazo.gpx.osm.PoiOnRoute
import app.trazo.gpx.osm.Pois
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavExtrasTest {

    private val mLat = 1 / 110_574.0
    private val mLon = 1 / (111_320.0 * kotlin.math.cos(Math.toRadians(40.0)))

    /** 300 m al norte, giro de 90° a la derecha y 300 m al este. */
    private fun ell(): List<List<GeoPoint>> {
        val north = (0..30).map { GeoPoint(40.0 + it * 10 * mLat, -3.9) }
        val east = (1..30).map { GeoPoint(40.0 + 300 * mLat, -3.9 + it * 10 * mLon) }
        return listOf(north + east)
    }

    @Test
    fun `detecta un giro a la derecha donde esta`() {
        val t = Turns.detect(ell())
        assertEquals(1, t.size)
        assertEquals(TurnKind.RIGHT, t[0].kind)
        assertEquals(300.0, t[0].alongMeters, 20.0)
        assertEquals(90.0, t[0].angleDegrees, 15.0)
    }

    @Test
    fun `una recta no tiene giros y las zetas se juntan en un aviso`() {
        val straight = listOf((0..100).map { GeoPoint(40.0 + it * 10 * mLat, -3.9) })
        assertTrue(Turns.detect(straight).isEmpty())
        // Zetas: tramos de 40 m alternando este-oeste mientras se sube al norte
        val pts = mutableListOf(GeoPoint(40.0, -3.9))
        var lat = 40.0
        var lon = -3.9
        repeat(8) { k ->
            repeat(4) { lon += (if (k % 2 == 0) 10 else -10) * mLon; pts += GeoPoint(lat, lon) }
            repeat(2) { lat += 10 * mLat; pts += GeoPoint(lat, lon) }
        }
        val z = Turns.detect(listOf(pts))
        assertEquals(TurnKind.SWITCHBACKS, z.first().kind)
        assertTrue(z.toString(), z.size <= 2)
    }

    @Test
    fun `subida en directo`() {
        val profile = (0..100).map { ProfilePoint(it * 20.0, if (it < 50) 1000.0 else 1000.0 + (it - 50) * 2.0) }
        val climbs = listOf(Climb(1000.0, 2000.0, 100.0))
        val grades = Slope.grades(profile)
        val p = ClimbLive.progress(climbs, profile, grades, 1500.0)!!
        assertEquals(1, p.number)
        assertEquals(500.0, p.doneMeters, 0.1)
        assertEquals(500.0, p.remainingMeters, 0.1)
        assertEquals(50.0, p.remainingGainMeters, 1.0)
        assertEquals(10.0, p.gradePercent, 0.5)
        assertTrue(p.profile.first().distanceMeters >= 1000.0)
        assertNull(ClimbLive.progress(climbs, profile, grades, 500.0))
    }

    @Test
    fun `puesta de sol en Madrid`() {
        // 7 de noviembre de 2026: puesta hacia las 18:03 en Madrid (17:03 UTC)
        val day = 20_764L
        val sunset = Daylight.sunsetMs(40.4168, -3.7038, day)!!
        val utcMin = (sunset - day * 86_400_000) / 60_000
        assertEquals(17 * 60 + 3.0, utcMin.toDouble(), 6.0)
        // 21 de junio: hacia las 21:48 (19:48 UTC)
        val june = 20_625L
        val s2 = (Daylight.sunsetMs(40.4168, -3.7038, june)!! - june * 86_400_000) / 60_000
        assertEquals(19 * 60 + 48.0, s2.toDouble(), 6.0)
        val rise = (Daylight.sunriseMs(40.4168, -3.7038, day)!! - day * 86_400_000) / 60_000
        assertEquals(6 * 60 + 56.0, rise.toDouble(), 8.0) // ~7:56 en Madrid
        assertNull(Daylight.sunsetMs(78.0, 15.0, june)) // sol de medianoche en Svalbard
        assertEquals(-10L, Daylight.marginMinutes(0, 60 * 60_000, 70 * 60_000))
    }

    @Test
    fun `fuentes y refugios a lo largo de la ruta`() {
        val json = """{"elements":[
          {"type":"node","id":1,"lat":40.0009,"lon":-3.9,"tags":{"amenity":"drinking_water","name":"Fuente del Cura"}},
          {"type":"node","id":2,"lat":40.0005,"lon":-3.8,"tags":{"natural":"spring"}},
          {"type":"way","id":3,"center":{"lat":40.0027,"lon":-3.8995},"tags":{"amenity":"shelter","name":"Refugio Zabala"}},
          {"type":"node","id":4,"lat":40.001,"lon":-3.9,"tags":{"amenity":"drinking_water","drinking_water":"no"}},
          {"type":"node","id":5,"lat":40.002,"lon":-3.9,"tags":{"tourism":"alpine_hut"}}
        ]}"""
        val pois = Pois.parse(json)
        assertEquals(4, pois.size) // la no potable fuera
        assertEquals(PoiKind.SHELTER, pois.first { it.name == "Refugio Zabala" }.kind)
        assertEquals(PoiKind.HUT, pois.last().kind)
        val on = Pois.alongRoute(pois, ell())
        assertEquals(listOf("Fuente del Cura", null, "Refugio Zabala"), on.map { it.poi.name })
        assertEquals(100.0, on[0].alongMeters, 15.0)
        assertTrue(Pois.query(ell()).contains("node[\"amenity\"=\"drinking_water\"]("))
        // Diez fuentes seguidas cada 40 m: se queda una cada 300 m
        val many = (0..9).map { Poi(PoiKind.WATER, "F$it", 40.0 + it * 40 / 110_574.0, -3.9) }
        val straight = listOf((0..100).map { GeoPoint(40.0 + it * 10 / 110_574.0, -3.9) })
        val thinned = Pois.alongRoute(many, straight)
        assertEquals(listOf("F0", "F8"), thinned.map { it.poi.name })
    }

    private fun state(along: Double, off: Boolean = false, started: Boolean = false) =
        NavState(along, 1000.0, 0.0, 0.0, off, started, null, null)

    @Test
    fun `la voz avisa una vez de cada cosa`() {
        val turns = Turns.detect(ell())
        val pois = listOf(PoiOnRoute(Poi(PoiKind.WATER, null, 0.0, 0.0), 500.0, 10.0))
        val climbs = listOf(Climb(450.0, 900.0, 40.0))
        val v = VoiceGuide(turns, climbs, pois, Activity.BIKE)
        assertEquals(listOf("En 150 metros, gira a la derecha"), v.update(state(150.0)))
        assertTrue(v.update(state(160.0)).isEmpty())
        // La fuente se anuncia a 250 m o menos, una sola vez
        assertEquals(listOf("Gira a la derecha", "Fuente en 230 metros"), v.update(state(270.0)))
        val later = v.update(state(340.0))
        assertEquals(listOf("Empieza una subida de 450 metros al 9 por ciento, 40 metros de desnivel"), later)
        assertEquals(listOf("Te has salido de la ruta"), v.update(state(340.0, off = true, started = true)))
        assertEquals(listOf("Vuelves a estar en la ruta"), v.update(state(345.0)))
        assertEquals(listOf("Al ritmo actual, se te hará de noche antes de terminar"), v.update(state(350.0), lightMarginMinutes = -20))
        assertTrue(v.update(state(360.0), lightMarginMinutes = -25).isEmpty())
    }
}
