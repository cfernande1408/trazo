package app.trazo.gpx.ride

import app.trazo.gpx.GpxParser
import app.trazo.gpx.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarksTest {

    private val track = listOf(listOf(
        TimedPoint(40.5, -3.9, 1200.0, 1_700_000_000_000),
        TimedPoint(40.501, -3.901, 1210.0, 1_700_000_010_000),
    ))

    @Test
    fun `los puntos marcados se guardan y se leen del GPX`() {
        val marks = listOf(
            Waypoint(40.5005, -3.9005, 1205.0, "Fuente", "Agua fresca & buena", "photos/20261107-101500-123.jpg"),
            Waypoint(40.501, -3.901, null, "Peligro", null, null),
        )
        val gpx = GpxParser.parse(GpxWriter.write("Salida", track, waypoints = marks))
        assertEquals(marks, gpx.waypoints)
        assertEquals(2, gpx.segments.first().size)
        // El enlace de la foto no se confunde con los enlaces de la ruta
        assertEquals(emptyList<String>(), gpx.links)
        assertEquals("photos/20261107-101500-123.jpg", gpx.waypoints[0].localPhoto)
    }

    @Test
    fun `solo cuenta como foto un archivo junto al GPX`() {
        fun w(link: String?) = Waypoint(0.0, 0.0, link = link)
        assertEquals("photos/a.JPG", w("photos/a.JPG").localPhoto)
        assertNull(w("https://www.wikiloc.com/foto.jpg").localPhoto)
        assertNull(w("../../datos.jpg").localPhoto)
        assertNull(w("/sdcard/a.jpg").localPhoto)
        assertNull(w("photos/a.gpx").localPhoto)
        assertNull(w(null).localPhoto)
    }

    @Test
    fun `sin puntos el GPX no cambia`() {
        assertEquals(GpxWriter.write("Salida", track), GpxWriter.write("Salida", track, waypoints = emptyList()))
    }
}
