package app.trazo.gpx.osm

import app.trazo.gpx.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceTest {

    private fun k(vararg tags: Pair<String, String>) = Surface.classify(tags.toMap())

    @Test
    fun `clasifica por superficie y tipo de via`() {
        assertEquals(SurfaceKind.PAVED, k("highway" to "residential"))
        assertEquals(SurfaceKind.PAVED, k("highway" to "track", "surface" to "asphalt"))
        assertEquals(SurfaceKind.PAVED, k("highway" to "track", "tracktype" to "grade1"))
        assertEquals(SurfaceKind.TRACK, k("highway" to "track"))
        assertEquals(SurfaceKind.TRACK, k("highway" to "track", "tracktype" to "grade3"))
        assertEquals(SurfaceKind.TRACK, k("highway" to "unclassified", "surface" to "gravel"))
        assertEquals(SurfaceKind.PATH, k("highway" to "path"))
        assertEquals(SurfaceKind.PATH, k("highway" to "footway", "surface" to "dirt"))
        assertEquals(SurfaceKind.PAVED, k("highway" to "path", "surface" to "concrete"))
        assertEquals(SurfaceKind.PAVED, k("highway" to "footway", "footway" to "sidewalk"))
        assertEquals(SurfaceKind.PAVED, k("highway" to "cycleway"))
        assertEquals(SurfaceKind.PATH, k("highway" to "cycleway", "surface" to "compacted"))
        assertEquals(SurfaceKind.STEPS, k("highway" to "steps"))
        // "asphalt;gravel": manda el primero
        assertEquals(SurfaceKind.PAVED, k("highway" to "track", "surface" to "asphalt;gravel"))
        assertNull(k("highway" to "construction"))
        assertNull(k("building" to "yes"))
    }

    @Test
    fun `lee los caminos de Overpass`() {
        val json = """
            {"elements":[
              {"type":"way","id":1,"tags":{"highway":"track","surface":"gravel"},
               "geometry":[{"lat":40.0,"lon":-3.0},{"lat":40.001,"lon":-3.0}]},
              {"type":"way","id":2,"tags":{"highway":"construction"},
               "geometry":[{"lat":40.0,"lon":-3.0},{"lat":40.001,"lon":-3.0}]},
              {"type":"way","id":3,"tags":{"highway":"path"},"geometry":[{"lat":40.0,"lon":-3.0}]}
            ]}
        """.trimIndent()
        val ways = Surface.parse(json)
        assertEquals(1, ways.size)
        assertEquals(SurfaceKind.TRACK, ways[0].kind)
    }

    @Test
    fun `reparte la ruta por superficie`() {
        // Ruta hacia el norte: 1 km por una pista, 500 m por un sendero y 300 m sin camino en OSM
        fun north(from: Double, meters: Double) = GeoPoint(from + meters / 111_195, -3.7)
        val start = 40.5
        val route = (0..180).map { north(start, it * 10.0) }
        val track = SurfacedWay(listOf(north(start, 0.0), north(start, 1000.0)), SurfaceKind.TRACK)
        // El sendero va a 8 m del track, que es lo que se desvía un GPS
        val path = SurfacedWay(
            listOf(GeoPoint(start + 1000 / 111_195.0, -3.7 + 8 / 85_000.0), GeoPoint(start + 1500 / 111_195.0, -3.7 + 8 / 85_000.0)),
            SurfaceKind.PATH,
        )
        // Una calle a 200 m, que no debe contar
        val far = SurfacedWay(listOf(GeoPoint(start, -3.7 + 200 / 85_000.0), GeoPoint(start + 0.02, -3.7 + 200 / 85_000.0)), SurfaceKind.PAVED)
        val s = Surface.summarize(listOf(route), listOf(track, path, far))
        assertEquals(1800.0, s.routeMeters, 5.0)
        assertEquals(1000.0, s.metersByKind.getValue(SurfaceKind.TRACK), 15.0)
        assertEquals(500.0, s.metersByKind.getValue(SurfaceKind.PATH), 15.0)
        assertNull(s.metersByKind[SurfaceKind.PAVED])
        assertEquals(1500.0, s.knownMeters, 20.0)
        assertEquals(2 / 3.0, s.share(SurfaceKind.TRACK), 0.02)
    }

    @Test
    fun `consulta en recuadros pequenos`() {
        val route = (0..400).map { GeoPoint(40.0 + it * 0.0002, -3.7) }
        val q = Surface.query(listOf(route))
        assertTrue(q.startsWith("[out:json]"))
        assertTrue(q.endsWith("out tags geom;"))
        // 400 puntos de 5 en 5 (solapando uno): unos 100 recuadros
        assertTrue(q.split("way[highway](").size - 1 in 95..105)
    }
}
