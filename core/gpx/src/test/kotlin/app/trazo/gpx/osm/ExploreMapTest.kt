package app.trazo.gpx.osm

import app.trazo.gpx.GeoJson
import app.trazo.gpx.MapMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreMapTest {

    @Test
    fun `consulta por recuadro sur oeste norte este`() {
        val q = Overpass.bboxQuery(Bbox(40.3, -3.9, 40.5, -3.5))
        assertTrue(q.contains("""(40.30000,-3.90000,40.50000,-3.50000)"""))
    }

    @Test
    fun `bbox contiene y se agranda`() {
        val b = Bbox(40.0, -4.0, 41.0, -3.0)
        assertTrue(b.contains(Bbox(40.2, -3.8, 40.8, -3.2)))
        assertFalse(b.contains(Bbox(39.9, -3.8, 40.8, -3.2)))
        assertEquals(Bbox(39.75, -4.25, 41.25, -2.75), b.expand(0.25))
    }

    @Test
    fun `marcadores con nombres escapados que siguen siendo json valido`() {
        val json = GeoJson.markers(listOf(MapMarker(7, 40.5, -3.7, "Ruta \"La Peña\"\\2", mtb = false)))
        val root = Json.parse(json) as Map<*, *>
        val f = (root["features"] as List<*>).single() as Map<*, *>
        val props = f["properties"] as Map<*, *>
        assertEquals(7.0, props["id"])
        assertEquals("Ruta \"La Peña\"\\2", props["name"])
        assertEquals(false, props["mtb"])
        assertEquals(listOf(-3.7, 40.5), (f["geometry"] as Map<*, *>)["coordinates"])
    }
}
