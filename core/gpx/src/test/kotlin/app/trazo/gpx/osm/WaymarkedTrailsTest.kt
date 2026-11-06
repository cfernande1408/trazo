package app.trazo.gpx.osm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaymarkedTrailsTest {

    @Test
    fun `mercator ida y vuelta`() {
        val (x, y) = WaymarkedTrails.toMerc(40.4168, -3.7038)
        assertEquals(-412_303.0, x, 5.0)
        assertEquals(4_926_660.0, y, 50.0)
        val p = WaymarkedTrails.fromMerc(x, y)
        assertEquals(40.4168, p.lat, 1e-7)
        assertEquals(-3.7038, p.lon, 1e-7)
    }

    @Test
    fun `url por zona en mercator`() {
        val u = WaymarkedTrails.byAreaUrl(WaymarkedTrails.Flavour.MTB, Bbox(40.0, -4.0, 40.5, -3.5))
        assertTrue(u.startsWith("https://mtb.waymarkedtrails.org/api/v1/list/by_area?bbox="))
        assertTrue(u.endsWith("&limit=100"))
        val nums = u.substringAfter("bbox=").substringBefore("&").split(",").map { it.toDouble() }
        assertEquals(4, nums.size)
        assertTrue(nums[0] < nums[2] && nums[1] < nums[3])
    }

    @Test
    fun `lee el listado`() {
        val json = """{"bbox":[1,2,3,4],"results":[
            {"type":"relation","id":123,"name":"Senda del Arroyo","ref":"PR-M 1","group":"LOC"},
            {"type":"relation","id":7,"ref":"BTT-2"}]}"""
        val r = WaymarkedTrails.parseList(json)
        assertEquals(listOf(123L, 7L), r.map { it.id })
        assertEquals("BTT-2", r[1].name)
    }

    @Test
    fun `lee trazados simples y multiples y los pasa a lat lon`() {
        val (x0, y0) = WaymarkedTrails.toMerc(40.0, -4.0)
        val (x1, y1) = WaymarkedTrails.toMerc(40.01, -4.0)
        val json = """{"type":"FeatureCollection","crs":{"type":"name","properties":{"name":"EPSG:3857"}},"features":[
            {"type":"Feature","geometry":{"type":"LineString","coordinates":[[$x0,$y0],[$x1,$y1]]},"id":1},
            {"type":"Feature","geometry":{"type":"MultiLineString","coordinates":[[[$x0,$y0],[$x1,$y1]],[[$x1,$y1],[$x0,$y0]]]},"id":2}]}"""
        val s = WaymarkedTrails.parseSegments(json)
        assertEquals(1, s[1L]!!.size)
        assertEquals(2, s[2L]!!.size)
        assertEquals(40.01, s[1L]!![0][1].lat, 1e-7)
    }

    @Test
    fun `icono a mitad del tramo mas largo`() {
        val (x0, y0) = WaymarkedTrails.toMerc(40.0, -4.0)
        val (x1, y1) = WaymarkedTrails.toMerc(40.02, -4.0)
        val segs = mapOf(5L to listOf(listOf(WaymarkedTrails.fromMerc(x0, y0), WaymarkedTrails.fromMerc(x1, y1))))
        val r = WaymarkedTrails.summaries(WaymarkedTrails.Flavour.CYCLING, listOf(WaymarkedTrails.Listed(5, "R", null)), segs)
        assertEquals(40.01, r.single().lat, 1e-5)
        assertTrue(!r.single().isMtb)
        assertTrue(WaymarkedTrails.summaries(WaymarkedTrails.Flavour.MTB, listOf(WaymarkedTrails.Listed(9, "sin trazado", null)), segs).isEmpty())
    }

    @Test
    fun `detalles con recuadro y descripcion`() {
        val (bbox, desc) = WaymarkedTrails.parseDetails("""{"id":1,"bbox":[-412303.1,4926660.0,-400000.0,4930000.5],"description":"Por pinar"}""")
        assertEquals("-412303.1,4926660.0,-400000.0,4930000.5", bbox)
        assertEquals("Por pinar", desc)
        assertNull(WaymarkedTrails.parseDetails("""{"id":1}""").first)
    }
}
