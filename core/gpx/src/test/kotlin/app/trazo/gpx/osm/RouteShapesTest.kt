package app.trazo.gpx.osm

import app.trazo.gpx.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteShapesTest {

    private val lat0 = 40.8
    private val mLon = 111_195.0 * Math.cos(Math.toRadians(lat0))

    /** Punto a [e] m al este y [n] m al norte del origen. */
    private fun p(e: Double, n: Double) = GeoPoint(lat0 + n / 111_195.0, -4.0 + e / mLon)

    /** Tramo recto de a a b con un punto cada 100 m. */
    private fun line(a: GeoPoint, b: GeoPoint): List<GeoPoint> = (0..10).map { k ->
        val t = k / 10.0
        GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    /** Zona de 10 x 10 km alrededor de todo. */
    private val area = Bbox(lat0 - 5_000 / 111_195.0, -4.0 - 5_000 / mLon, lat0 + 5_000 / 111_195.0, -4.0 + 5_000 / mLon)

    @Test
    fun `cuadrado en cuatro tramos desordenados es circular`() {
        val a = p(0.0, 0.0); val b = p(1000.0, 0.0); val c = p(1000.0, 1000.0); val d = p(0.0, 1000.0)
        // Desordenados y uno al revés, como llegan de Waymarked Trails
        assertEquals(RouteShape.LOOP, RouteShapes.of(listOf(line(c, d), line(a, b), line(c, b), line(d, a)), area))
    }

    @Test
    fun `un solo tramo cerrado es circular`() {
        val ring = line(p(0.0, 0.0), p(1000.0, 0.0)) + line(p(1000.0, 0.0), p(1000.0, 1000.0)).drop(1) +
            line(p(1000.0, 1000.0), p(0.0, 20.0)).drop(1)
        assertEquals(RouteShape.LOOP, RouteShapes.of(listOf(ring), area))
    }

    @Test
    fun `de un pueblo a otro es lineal`() {
        assertEquals(RouteShape.LINEAR, RouteShapes.of(listOf(line(p(0.0, 0.0), p(2000.0, 500.0)), line(p(2000.0, 500.0), p(3000.0, 0.0))), area))
    }

    @Test
    fun `circular con tramo de acceso cuenta como circular`() {
        val a = p(0.0, 0.0); val b = p(1000.0, 0.0); val c = p(1000.0, 1000.0); val d = p(0.0, 1000.0)
        val access = line(p(-1000.0, -500.0), a)
        assertEquals(RouteShape.LOOP, RouteShapes.of(listOf(line(a, b), line(b, c), line(c, d), line(d, a), access), area))
    }

    @Test
    fun `si se sale de la zona no se sabe`() {
        // Termina en el borde este de la zona: puede seguir fuera
        assertEquals(RouteShape.UNKNOWN, RouteShapes.of(listOf(line(p(0.0, 0.0), p(4_950.0, 0.0))), area))
        // Sin zona, se juzga con lo que hay
        assertEquals(RouteShape.LINEAR, RouteShapes.of(listOf(line(p(0.0, 0.0), p(4_950.0, 0.0)))))
        assertEquals(RouteShape.UNKNOWN, RouteShapes.of(emptyList(), area))
    }

    @Test
    fun `etiqueta roundtrip de OSM`() {
        assertEquals(RouteShape.LOOP, RouteShapes.fromTag("yes"))
        assertEquals(RouteShape.LINEAR, RouteShapes.fromTag("no"))
        assertEquals(RouteShape.UNKNOWN, RouteShapes.fromTag(null))
    }
}
