package app.trazo.gpx.layers

import app.trazo.gpx.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapLayersTest {

    @Test
    fun `por defecto no se toca el estilo`() {
        val p = MapLayers.plan(LayerPrefs(), Activity.BIKE)
        assertTrue(p.hidden.isEmpty())
        assertNull(p.base)
        assertTrue(p.overlays.isEmpty())
    }

    @Test
    fun `satelite oculta el suelo pero deja caminos, sombreado y nombres`() {
        val p = MapLayers.plan(LayerPrefs(base = BaseMap.SATELLITE), Activity.BIKE)
        assertEquals("base-pnoa", p.base!!.id)
        assertEquals("landcover-wood-pines", p.baseAbove)
        assertTrue("landcover-wood" in p.hidden && "building" in p.hidden)
        listOf("hillshade", "road-track", "road-path", "place-label", "peak-label").forEach { assertTrue(it !in p.hidden) }
    }

    @Test
    fun `topo IGN va encima de todo el estilo`() {
        val p = MapLayers.plan(LayerPrefs(base = BaseMap.IGN_TOPO), Activity.HIKE)
        assertEquals(MapLayers.TOP, p.baseAbove)
        assertTrue(p.base!!.tiles.contains("layer=MTN"))
        assertTrue(p.base!!.tiles.contains("TileMatrix={z}&TileCol={x}&TileRow={y}"))
    }

    @Test
    fun `detalles se apagan y la capa de rutas depende de la actividad`() {
        val p = MapLayers.plan(LayerPrefs(hillshade = false, trees = false, routes = true), Activity.HIKE)
        assertEquals(setOf("hillshade", "landcover-wood-pines"), p.hidden)
        assertEquals("https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png", p.overlays.single().tiles)
        assertTrue(MapLayers.routesOverlay(Activity.BIKE).tiles.contains("/mtb/"))
    }

    @Test
    fun `preferencias ida y vuelta, y valores rotos vuelven a lo de serie`() {
        val p = LayerPrefs(BaseMap.SATELLITE, hillshade = false, trees = true, routes = true)
        assertEquals(p, LayerPrefs.decode(p.encode()))
        assertEquals(LayerPrefs(), LayerPrefs.decode(null))
        assertEquals(LayerPrefs(), LayerPrefs.decode("PLUTON;true"))
    }

    @Test
    fun `la lista de capas coincide con el estilo real`() {
        val style = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/assets/trazo_topo.json") }.firstOrNull { it.exists() } ?: return
        val ids = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").findAll(style.readText().substringAfter("\"layers\"")).map { it.groupValues[1] }.toList()
        assertEquals(MapLayers.STYLE_LAYERS, ids)
    }
}
