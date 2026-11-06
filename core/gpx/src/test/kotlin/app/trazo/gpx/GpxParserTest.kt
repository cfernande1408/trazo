package app.trazo.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxParserTest {

    private fun sample(): Gpx =
        javaClass.getResourceAsStream("/wikiloc_sample.gpx")!!.use(GpxParser::parse)

    @Test
    fun `lee el nombre de metadata`() {
        assertEquals("Wikiloc - Circular por la Fuenfría", sample().name)
    }

    @Test
    fun `lee track con dos segmentos y sus puntos`() {
        val gpx = sample()
        assertEquals(1, gpx.tracks.size)
        assertEquals("Circular por la Fuenfría", gpx.tracks[0].name)
        assertEquals(listOf(3, 2), gpx.segments.map { it.size })
        val first = gpx.segments[0][0]
        assertEquals(40.7780, first.lat, 1e-9)
        assertEquals(-4.0600, first.lon, 1e-9)
        assertEquals(1500.0, first.ele!!, 1e-9)
        assertEquals("2026-10-12T08:00:00Z", first.time)
    }

    @Test
    fun `punto autocerrado sin elevacion queda con ele nulo`() {
        val last = sample().segments[1][1]
        assertNull(last.ele)
        assertEquals(40.7820, last.lat, 1e-9)
    }

    @Test
    fun `waypoints con CDATA y entidades decodificadas`() {
        val wpts = sample().waypoints
        assertEquals(2, wpts.size)
        assertEquals("Fuente", wpts[0].name)
        assertEquals("Agua <buena> todo el año", wpts[0].desc)
        assertEquals("Raíces & piedra suelta", wpts[1].desc)
    }

    @Test
    fun `el nombre del autor no pisa el nombre de la ruta`() {
        assertTrue(sample().name!!.contains("Fuenfría"))
    }

    @Test
    fun `ruta planificada rte se convierte en track`() {
        val gpx = GpxParser.parse(
            """<gpx><rte><name>Plan</name>
               <rtept lat="1" lon="2"><ele>10</ele></rtept>
               <rtept lat="1.001" lon="2"/></rte></gpx>"""
        )
        assertEquals("Plan", gpx.name)
        assertEquals(listOf(2), gpx.segments.map { it.size })
        assertEquals(10.0, gpx.segments[0][0].ele!!, 1e-9)
    }

    @Test
    fun `acepta prefijos de namespace y BOM`() {
        val gpx = GpxParser.parse(
            "﻿<gpx:gpx xmlns:gpx='x'><gpx:trk><gpx:trkseg>" +
                "<gpx:trkpt lat='1' lon='2'/></gpx:trkseg></gpx:trk></gpx:gpx>"
        )
        assertEquals(1, gpx.segments.single().size)
    }

    @Test
    fun `ignora extensiones con name dentro del punto`() {
        val gpx = GpxParser.parse(
            """<gpx><trk><name>T</name><trkseg><trkpt lat="1" lon="2">
               <extensions><x><name>no</name></x></extensions></trkpt></trkseg></trk></gpx>"""
        )
        assertEquals("T", gpx.name)
    }

    @Test
    fun `falla si no es un gpx`() {
        val e = assertThrows(GpxParseException::class.java) { GpxParser.parse("<kml></kml>") }
        assertEquals("El archivo no es un GPX", e.message)
    }

    @Test
    fun `falla con archivo vacio`() {
        assertThrows(GpxParseException::class.java) { GpxParser.parse("   ") }
    }

    @Test
    fun `falla con etiquetas sin cerrar`() {
        assertThrows(GpxParseException::class.java) {
            GpxParser.parse("<gpx><trk><trkseg><trkpt lat='1' lon='2'></trkseg></trk></gpx>")
        }
        assertThrows(GpxParseException::class.java) { GpxParser.parse("<gpx><trk>") }
    }

    @Test
    fun `falla con punto sin latitud o fuera de rango`() {
        assertThrows(GpxParseException::class.java) {
            GpxParser.parse("<gpx><wpt lon='2'/></gpx>")
        }
        assertThrows(GpxParseException::class.java) {
            GpxParser.parse("<gpx><wpt lat='91' lon='2'/></gpx>")
        }
    }
}
