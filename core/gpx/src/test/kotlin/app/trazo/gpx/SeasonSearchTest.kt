package app.trazo.gpx

import app.trazo.gpx.osm.Bbox
import app.trazo.gpx.osm.PlaceSearch
import app.trazo.gpx.osm.WaymarkedTrails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonSearchTest {

    @Test
    fun `estacion segun la fecha`() {
        assertEquals(Season.WINTER, Season.of(1, 15))
        assertEquals(Season.WINTER, Season.of(3, 19))
        assertEquals(Season.SPRING, Season.of(3, 20))
        assertEquals(Season.SPRING, Season.of(6, 20))
        assertEquals(Season.SUMMER, Season.of(6, 21))
        assertEquals(Season.SUMMER, Season.of(9, 22))
        assertEquals(Season.AUTUMN, Season.of(9, 23))
        assertEquals(Season.AUTUMN, Season.of(11, 6))
        assertEquals(Season.AUTUMN, Season.of(12, 20))
        assertEquals(Season.WINTER, Season.of(12, 21))
    }

    @Test
    fun `paleta 3D es JSON valido con nieve solo en invierno`() {
        val w = app.trazo.gpx.osm.Json.parse(Season.WINTER.paletteJson()) as Map<*, *>
        assertEquals(true, w["snow"])
        assertNull(w["blossom"])
        val s = app.trazo.gpx.osm.Json.parse(Season.SPRING.paletteJson()) as Map<*, *>
        assertEquals(false, s["snow"])
        assertEquals("#F4A7C0", s["blossom"])
        assertEquals(0.35, s["broadleafShare"] as Double, 1e-9)
    }

    @Test
    fun `colores de todas las estaciones con formato de estilo`() {
        val hex = Regex("#[0-9A-F]{6}")
        for (s in Season.entries) {
            listOf(s.background, s.grass, s.wood, s.park, s.pineLight, s.pineShade, s.broadleaf, s.broadleafShade)
                .forEach { assertTrue("${s.name} $it", hex.matches(it)) }
        }
    }

    @Test
    fun `lee lugares de nominatim`() {
        val json = """[
          {"place_id":1,"lat":"40.7853","lon":"-3.9786","category":"natural","type":"peak",
           "name":"Bola del Mundo","display_name":"Bola del Mundo, Navacerrada, Comunidad de Madrid, España",
           "boundingbox":["40.7803","40.7903","-3.9836","-3.9736"]},
          {"place_id":2,"lat":"40.41","lon":"-3.70","type":"city","name":"",
           "display_name":"Madrid, Comunidad de Madrid, España","boundingbox":["40.31","40.64","-3.89","-3.51"]},
          {"place_id":3,"lat":"x","lon":"-3"}
        ]"""
        val p = PlaceSearch.parse(json)
        assertEquals(2, p.size)
        assertEquals("Bola del Mundo", p[0].name)
        assertEquals("Navacerrada, Comunidad de Madrid, España", p[0].detail)
        assertEquals("peak", p[0].kind)
        assertEquals(Bbox(40.7803, -3.9836, 40.7903, -3.9736), p[0].bbox)
        assertEquals("Madrid", p[1].name)
        assertEquals(13.0, PlaceSearch.zoomFor(p[0]), 0.0)
        assertEquals(11.0, PlaceSearch.zoomFor(p[1]), 0.0)
    }

    @Test
    fun `urls de busqueda codifican el texto`() {
        val u = PlaceSearch.url("  Bola del Mundo ")
        assertTrue(u.endsWith("&q=Bola+del+Mundo"))
        assertTrue(u.contains("accept-language=es"))
        val w = WaymarkedTrails.searchUrl(WaymarkedTrails.Flavour.HIKING, "PR-M 13")
        assertEquals("https://hiking.waymarkedtrails.org/api/v1/list/search?limit=15&query=PR-M+13", w)
    }

    @Test
    fun `centro de recuadro mercator`() {
        val (x1, y1) = WaymarkedTrails.toMerc(40.7, -4.0)
        val (x2, y2) = WaymarkedTrails.toMerc(40.9, -3.8)
        val c = WaymarkedTrails.mercCenter("$x1,$y1,$x2,$y2")!!
        assertEquals(-3.9, c.lon, 1e-6)
        assertEquals(40.8, c.lat, 0.01)
        assertNull(WaymarkedTrails.mercCenter("1,2,3"))
        assertFalse(c.lat.isNaN())
    }
}
