package app.trazo.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReliefTest {

    @Test
    fun `mas exageracion en rutas suaves y menos en sierra`() {
        assertEquals(3.5, Relief.base(200.0), 1e-9)       // casi llana: tope
        assertEquals(1300.0 / 440, Relief.base(440.0), 1e-9) // Navafría–Peñalara: ~2,95
        assertEquals(2.0, Relief.base(1500.0), 1e-9)      // mucho desnivel: mínimo
        assertEquals(2.0, Relief.base(0.0), 1e-9)         // GPX sin altitudes
    }

    @Test
    fun `niveles del usuario`() {
        val normal = Relief.exaggeration(440.0)
        assertEquals(Relief.base(440.0), normal, 1e-9)
        assertTrue(Relief.exaggeration(440.0, 4) > normal)
        assertTrue(Relief.exaggeration(440.0, 0) < normal)
        assertEquals(Relief.exaggeration(440.0, 4), Relief.exaggeration(440.0, 99), 1e-9)
        assertEquals(Relief.exaggeration(440.0, 0), Relief.exaggeration(440.0, -3), 1e-9)
    }

    @Test
    fun `la nueva escala exagera mas que la de antes`() {
        // Antes: clamp(750 / relieve, 1.3, 2.8)
        for (r in listOf(200.0, 440.0, 800.0, 1500.0)) {
            assertTrue("$r m", Relief.exaggeration(r) > (750 / r).coerceIn(1.3, 2.8))
        }
    }
}
