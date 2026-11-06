package app.trazo.gpx.nav

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.Gpx
import app.trazo.gpx.GpxParser
import app.trazo.gpx.TrackStatsCalculator
import app.trazo.gpx.Track
import app.trazo.gpx.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteStartTest {

    private val k = 111_195.0

    /** Cuadrado de 1 km de lado que vuelve al inicio, un punto cada 100 m. */
    private fun loop(): List<GeoPoint> {
        val kx = k * kotlin.math.cos(Math.toRadians(40.0))
        fun p(n: Double, e: Double) = GeoPoint(40.0 + n / k, -4.0 + e / kx)
        return (0..9).map { p(it * 100.0, 0.0) } + (0..9).map { p(1000.0, it * 100.0) } +
            (0..9).map { p(1000.0 - it * 100.0, 1000.0) } + (0..10).map { p(0.0, 1000.0 - it * 100.0) }
    }

    private fun length(segs: List<List<GeoPoint>>) = TrackStatsCalculator.compute(segs).distanceMeters

    @Test
    fun `detecta ruta circular y lineal`() {
        assertTrue(RouteStart.isLoop(listOf(loop())))
        assertFalse(RouteStart.isLoop(listOf(loop().take(15))))
    }

    @Test
    fun `circular empezando a mitad se recorre entera y acaba donde empieza`() {
        val original = listOf(loop())
        val r = RouteStart.from(original, 1500.0)
        assertEquals(1, r.size)
        val first = r.first().first()
        assertEquals(40.0 + 1000.0 / k, first.lat, 1e-6) // esquina norte, a 1,5 km
        assertEquals(length(original), length(r), 1.0)
        assertEquals(first, r.last().last())
    }

    @Test
    fun `lineal empezando a mitad recorta lo anterior`() {
        val linea = listOf(loop().take(15)) // 1,4 km
        val r = RouteStart.from(linea, 500.0)
        assertEquals(900.0, length(r), 1.0)
    }

    @Test
    fun `empezar en cero no cambia nada`() {
        val original = listOf(loop())
        assertEquals(original, RouteStart.from(original, 0.0))
    }

    @Test
    fun `opciones de inicio en orden de paso y sin puntos lejanos`() {
        val pts = loop()
        val gpx = Gpx(
            name = "r",
            tracks = listOf(Track("r", listOf(pts))),
            waypoints = listOf(
                Waypoint(pts[25].lat, pts[25].lon, name = "Mirador"),
                Waypoint(pts[5].lat, pts[5].lon, name = "PARKING"),
                Waypoint(41.0, -4.0, name = "Lejos"),
            ),
        )
        val o = RouteStart.options(gpx)
        assertEquals(listOf("Inicio de la ruta", "PARKING", "Mirador"), o.map { it.label })
        assertEquals(500.0, o[1].alongMeters, 1.0)
    }

    @Test
    fun `lee descripcion y enlace de Wikiloc del gpx`() {
        val gpx = javaClass.getResourceAsStream("/wikiloc_sample.gpx")!!.use(GpxParser::parse)
        assertEquals("Salida desde el aparcamiento. Bajada final técnica.", gpx.description)
        assertEquals("https://es.wikiloc.com/rutas-mountain-bike/circular-por-la-fuenfria-123456", gpx.wikilocUrl)
        assertEquals(1, gpx.links.size) // el enlace del autor no cuenta
    }
}
