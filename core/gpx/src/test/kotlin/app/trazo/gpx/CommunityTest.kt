package app.trazo.gpx

import app.trazo.gpx.community.Community
import app.trazo.gpx.community.HazardKind
import app.trazo.gpx.osm.Bbox
import app.trazo.gpx.osm.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityTest {

    private val seg = listOf(listOf(GeoPoint(40.0, -3.9), GeoPoint(40.01, -3.9)))

    @Test
    fun `clave de la ruta segun de donde venga`() {
        val wikiloc = Gpx("R", listOf(Track("R", seg)), emptyList(), null,
            listOf("https://es.wikiloc.com/rutas-mountain-bike/ruta-de-los-puentes-12345678"))
        assertEquals("wikiloc:12345678", Community.reviewKey(wikiloc))
        val osm = Gpx("R", listOf(Track("R", seg)), emptyList(), null, listOf("https://www.openstreetmap.org/relation/987"))
        assertEquals("osm:987", Community.reviewKey(osm))
        val mine = Gpx("R", listOf(Track("R", seg)), emptyList(), null, emptyList())
        assertTrue(Community.reviewKey(mine).startsWith("trazo:"))
    }

    @Test
    fun `cuerpos JSON validos y recortados`() {
        val r = Json.parse(Community.reviewBody("wikiloc:1", 4, " Muy \"técnica\" ", "", "BIKE")) as Map<*, *>
        assertEquals(4.0, r["rating"])
        assertEquals("Muy \"técnica\"", r["comment"])
        assertNull(r["author"])
        val h = Json.parse(Community.hazardBody(HazardKind.ICE, 40.78, -3.97, null, "2026-11-09T10:00:00Z")) as Map<*, *>
        assertEquals("ICE", h["kind"])
        assertEquals(40.78, h["lat"])
    }

    @Test
    fun `leer valoraciones y avisos de supabase`() {
        val reviews = Community.parseReviews("""[
          {"id":1,"route_key":"osm:9","rating":5,"comment":"Genial","author":"Carlos","activity":"BIKE","created_at":"2026-11-07T10:20:30.123456+00:00"},
          {"id":2,"route_key":"osm:9","rating":3,"comment":null,"author":null,"activity":"HIKE","created_at":"2026-11-06T09:00:00+00:00"},
          {"id":3,"route_key":"osm:9","rating":9}
        ]""")
        assertEquals(2, reviews.size)
        assertEquals(1_794_046_830_123L, reviews[0].createdAtMs)
        val s = Community.summary(reviews)!!
        assertEquals(4.0, s.average, 1e-9)
        assertEquals(mapOf(5 to 1, 3 to 1), s.byStars)
        assertNull(Community.summary(emptyList()))

        val hz = Community.parseHazards("""[
          {"id":7,"kind":"FALLEN_TREE","lat":40.8,"lon":-3.9,"note":"En la bajada","created_at":"2026-11-07T08:00:00+00:00","expires_at":"2026-11-28T08:00:00+00:00","confirms":2,"gone":1},
          {"id":8,"kind":"RARO","lat":40.8,"lon":-3.9,"created_at":"2026-11-07T08:00:00+00:00","expires_at":"2026-11-10T08:00:00+00:00","confirms":0,"gone":3}
        ]""")
        assertEquals(HazardKind.FALLEN_TREE, hz[0].kind)
        assertTrue(hz[0].active)
        assertEquals(HazardKind.OTHER, hz[1].kind)
        assertTrue(!hz[1].active)
        val fc = Json.parse(Community.hazardsGeoJson(hz)) as Map<*, *>
        assertEquals(1, (fc["features"] as List<*>).size) // el que ya no está no sale
    }

    @Test
    fun `rutas de la api y caducidad`() {
        assertEquals("/rest/v1/route_reviews?route_key=eq.wikiloc%3A1&select=*&order=created_at.desc&limit=50", Community.reviewsPath("wikiloc:1"))
        val p = Community.hazardsPath(Bbox(40.7, -4.0, 40.9, -3.8), "2026-11-07T10:00:00Z")
        assertTrue(p, p.contains("lat=gte.40.70000&lat=lte.40.90000&lon=gte.-4.00000&lon=lte.-3.80000"))
        assertTrue(p.contains("expires_at=gt.2026-11-07T10%3A00%3A00Z"))
        assertEquals(2 * 86_400_000L, Community.expiresAt(HazardKind.ICE, 0))
    }
}
