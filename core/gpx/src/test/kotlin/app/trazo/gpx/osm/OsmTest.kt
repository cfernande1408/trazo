package app.trazo.gpx.osm

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.GpxParseException
import app.trazo.gpx.TrackStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val K = 111_195.0
private fun n(m: Double) = GeoPoint(40.0 + m / K, -4.0)

class JsonTest {

    @Test
    fun `lee objetos, listas, numeros y literales`() {
        val v = Json.parse("""{"a": [1, -2.5, 3e2], "b": {"c": true, "d": null}, "e": "x"}""") as Map<*, *>
        assertEquals(listOf(1.0, -2.5, 300.0), v["a"])
        assertEquals(true, (v["b"] as Map<*, *>)["c"])
        assertNull((v["b"] as Map<*, *>)["d"])
        assertEquals("x", v["e"])
    }

    @Test
    fun `decodifica escapes y unicode`() {
        assertEquals("Peña \"Alta\"\nñ", Json.parse("\"Pe\\u00f1a \\\"Alta\\\"\\nñ\""))
    }

    @Test
    fun `falla con json roto`() {
        assertThrows(JsonException::class.java) { Json.parse("""{"a": 1""") }
        assertThrows(JsonException::class.java) { Json.parse("""[1, 2] x""") }
    }
}

class OverpassTest {

    @Test
    fun `consulta de busqueda con coordenadas en formato ingles`() {
        val q = Overpass.searchQuery(40.4168, -3.7038, 30_000)
        assertTrue(q.contains("""relation["route"="mtb"](around:30000,40.41680,-3.70380)"""))
        assertTrue(q.startsWith("[out:json]"))
    }

    @Test
    fun `lee resultados de busqueda`() {
        val json = """{"elements":[
            {"type":"relation","id":1234567,"center":{"lat":40.7,"lon":-3.8},
             "tags":{"name":"Ruta BTT del Embalse","route":"mtb","ref":"BTT-3","network":"lcn","distance":"28,5 km"}},
            {"type":"relation","id":42,"center":{"lat":40.6,"lon":-3.9},"tags":{"route":"mtb"}},
            {"type":"relation","id":7,"tags":{"name":"sin centro"}}
        ]}"""
        val r = Overpass.parseSearch(json)
        assertEquals(2, r.size)
        assertEquals("Ruta BTT del Embalse", r[0].name)
        assertEquals(1234567L, r[0].id)
        assertEquals(28.5, r[0].distanceKm!!, 1e-9)
        assertEquals("Ruta MTB 42", r[1].name)
    }

    @Test
    fun `lee la geometria y une vias desordenadas`() {
        fun way(vararg m: Double) = m.joinToString(",", "[", "]") { """{"lat":${40.0 + it / K},"lon":-4.0}""" }
        val json = """{"elements":[
            {"type":"relation","id":9,"tags":{"name":"Vuelta","description":"Por pistas"}},
            {"type":"way","id":1,"geometry":${way(500.0, 1000.0)}},
            {"type":"way","id":2,"geometry":${way(0.0, 250.0, 500.0)}},
            {"type":"way","id":3,"geometry":${way(1500.0, 1000.0)}}
        ]}"""
        val gpx = Overpass.parseRoute(json, 9)
        assertEquals("Vuelta", gpx.name)
        assertEquals("Por pistas", gpx.description)
        assertEquals(listOf("https://www.openstreetmap.org/relation/9"), gpx.links)
        val seg = gpx.segments.single()
        assertEquals(5, seg.size)
        assertEquals(1500.0, TrackStatsCalculator.compute(gpx.segments).distanceMeters, 1.0)
    }

    @Test
    fun `ruta sin geometria falla con mensaje claro`() {
        assertThrows(GpxParseException::class.java) {
            Overpass.parseRoute("""{"elements":[{"type":"relation","id":1,"tags":{}}]}""", 1)
        }
    }

    @Test
    fun `km de la etiqueta distance`() {
        assertEquals(25.0, Overpass.parseKm("25")!!, 1e-9)
        assertEquals(12.3, Overpass.parseKm("12.3 km")!!, 1e-9)
        assertNull(Overpass.parseKm("larga"))
    }
}

class WayStitcherTest {

    @Test
    fun `lo que no se toca queda como otro tramo`() {
        val r = WayStitcher.stitch(listOf(listOf(n(0.0), n(100.0)), listOf(n(5000.0), n(5100.0), n(5200.0))))
        assertEquals(listOf(3, 2), r.map { it.size })
    }
}

class ElevationFillTest {

    private val seg = (0..20).map { n(it * 50.0) } // 1 km, un punto cada 50 m

    @Test
    fun `muestrea el principio, el final y cada 100 m`() {
        val s = ElevationFill.sampleIndices(listOf(seg))
        assertEquals(0 to 0, s.first())
        assertEquals(0 to 20, s.last())
        assertEquals(11, s.size)
    }

    @Test
    fun `respeta el maximo de consultas`() {
        val long = (0..2000).map { n(it * 50.0) } // 100 km
        assertTrue(ElevationFill.sampleIndices(listOf(long), max = 100).size <= 102)
    }

    @Test
    fun `interpola por distancia entre las altitudes consultadas`() {
        val samples = listOf(0 to 0, 0 to 20)
        val filled = ElevationFill.fill(listOf(seg), samples, listOf(600.0, 700.0)).single()
        assertEquals(600.0, filled[0].ele!!, 1e-6)
        assertEquals(650.0, filled[10].ele!!, 0.01)
        assertEquals(700.0, filled[20].ele!!, 1e-6)
    }
}
