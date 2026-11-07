package app.trazo.gpx

import app.trazo.gpx.offline.OfflineArea
import app.trazo.gpx.offline.OfflineMeta
import app.trazo.gpx.osm.Difficulty
import app.trazo.gpx.osm.Json
import app.trazo.gpx.osm.RatedWay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlopeDifficultyOfflineTest {

    /** 2 km hacia el norte: 1 km llano y luego 1 km al 12 %, con una rampa del 25 % en medio. */
    private fun climb(): List<List<GeoPoint>> {
        val pts = mutableListOf<GeoPoint>()
        var ele = 1000.0
        for (i in 0..200) {
            val d = i * 10.0
            if (i > 0 && d > 1000) ele += if (d in 1400.0..1500.0) 2.5 else 1.2
            pts += GeoPoint(40.0 + d / 110_574.0, -3.9, ele)
        }
        return listOf(pts)
    }

    @Test
    fun `pendiente por tramos de 100 m`() {
        val g = Slope.grades(ElevationProfile.of(climb()))
        assertEquals(20, g.size)
        assertEquals(0.0, g.first().percent, 0.5)
        val s = Slope.summary(g)!!
        assertEquals(25.0, s.maxUpPercent, 1.0)
        assertEquals(1400.0, s.maxUpAtMeters, 15.0)
        assertEquals(1000.0, s.upOver10Meters, 20.0)
        assertEquals(100.0, s.upOver15Meters, 20.0)
        assertEquals(0.0, s.maxDownPercent, 0.0)
        assertEquals(12.0, Slope.at(g, 1800.0)!!, 0.6)
    }

    @Test
    fun `colores por pendiente y lineas para el mapa`() {
        assertEquals(0, Slope.bucket(3.0))
        assertEquals(1, Slope.bucket(-7.0))
        assertEquals(2, Slope.bucket(12.0))
        assertEquals(3, Slope.bucket(19.9))
        assertEquals(4, Slope.bucket(30.0))
        val seg = climb()
        val fc = Json.parse(Slope.lines(seg, Slope.grades(ElevationProfile.of(seg)))) as Map<*, *>
        val buckets = (fc["features"] as List<*>).map { ((it as Map<*, *>)["properties"] as Map<*, *>)["b"] as Double }
        // llano → 10–15 % → muro → 10–15 %
        assertEquals(listOf(0.0, 2.0, 4.0, 2.0), buckets)
    }

    @Test
    fun `dificultad de los caminos de OSM junto al track`() {
        val json = """{"elements":[
          {"type":"way","id":1,"tags":{"highway":"track","mtb:scale":"1+"},
           "geometry":[{"lat":40.0,"lon":-3.90002},{"lat":40.009,"lon":-3.90002}]},
          {"type":"way","id":2,"tags":{"highway":"path","mtb:scale":"3","sac_scale":"mountain_hiking"},
           "geometry":[{"lat":40.0091,"lon":-3.89998},{"lat":40.0181,"lon":-3.89998}]},
          {"type":"way","id":3,"tags":{"highway":"path","mtb:scale":"2"},
           "geometry":[{"lat":40.0,"lon":-3.95},{"lat":40.02,"lon":-3.95}]},
          {"type":"way","id":4,"tags":{"highway":"path"},"geometry":[{"lat":40.0,"lon":-3.9},{"lat":40.1,"lon":-3.9}]}
        ]}"""
        val ways = Difficulty.parse(json)
        assertEquals(3, ways.size) // el 4 no tiene dificultad
        assertEquals(1, ways[0].mtb)
        assertEquals(2, ways[1].sac)
        val bike = Difficulty.summarize(climb(), ways, mtb = true)
        assertEquals(2000.0, bike.routeMeters, 20.0)
        assertEquals(setOf(1, 3), bike.metersByGrade.keys) // el camino 3 está a 4 km
        assertEquals(1000.0, bike.metersByGrade[1]!!, 30.0)
        assertEquals(3, bike.maxGrade)
        val hike = Difficulty.summarize(climb(), ways, mtb = false)
        assertEquals(setOf(2), hike.metersByGrade.keys)
        assertEquals("S3" to "bloques, escalones y curvas cerradas", Difficulty.label(3, mtb = true))
        assertEquals("T2", Difficulty.label(2, mtb = false).first)
    }

    @Test
    fun `consulta de overpass con el track reducido`() {
        val q = Difficulty.query(climb())
        assertTrue(q.startsWith("[out:json][timeout:25];way(around:15,40.00000,-3.90000,"))
        assertTrue(q.endsWith("[~\"^(mtb:scale|sac_scale)$\"~\".\"];out tags geom;"))
        assertTrue(q.count { it == ',' } < 260)
    }

    @Test
    fun `distancia de un punto a un camino`() {
        val line = listOf(GeoPoint(40.0, -3.9), GeoPoint(40.01, -3.9))
        assertEquals(0.0, Difficulty.distanceToLine(GeoPoint(40.005, -3.9), line), 0.5)
        assertEquals(85.0, Difficulty.distanceToLine(GeoPoint(40.005, -3.899), line), 2.0)
    }

    @Test
    fun `zona a descargar y lo que ocupa`() {
        val b = OfflineArea.around(climb())!!
        assertTrue(b.minLat < 40.0 && b.maxLat > 40.018 && b.minLon < -3.92 && b.maxLon > -3.88)
        // A zoom 8 toda la zona cabe en una tesela; a 14, en unas pocas
        assertEquals(1L, OfflineArea.tileCount(b, 8, 8))
        val z14 = OfflineArea.tileCount(b, 14, 14)
        assertTrue("$z14", z14 in 4..12)
        val noTopo = OfflineArea.estimateBytes(b, withTopo = false)
        val topo = OfflineArea.estimateBytes(b, withTopo = true)
        assertTrue(topo > noTopo)
        assertTrue("${topo / 1024 / 1024} MB", topo < 10L * 1024 * 1024)
        assertEquals(8023 to 6177, OfflineArea.tile(40.4168, -3.7038, 14)) // Puerta del Sol
    }

    @Test
    fun `metadatos y huella de la ruta`() {
        val m = OfflineMeta("Peñalara \"circular\"", OfflineArea.routeKey(climb()), true, 1_800_000_000_000)
        assertEquals(m, OfflineMeta.decode(m.encode()))
        assertNull(OfflineMeta.decode("basura"))
        assertEquals(OfflineArea.routeKey(climb()), OfflineArea.routeKey(climb()))
        assertNotNull(OfflineArea.routeKey(emptyList()))
    }
}
