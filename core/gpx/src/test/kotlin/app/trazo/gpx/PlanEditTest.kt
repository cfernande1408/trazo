package app.trazo.gpx

import app.trazo.gpx.plan.PlanEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanEditTest {

    private fun p(northMeters: Double, eastMeters: Double = 0.0) =
        GeoPoint(40.0 + northMeters / 110_574.0, -3.9 + eastMeters / (111_320.0 * kotlin.math.cos(Math.toRadians(40.0))))

    /** Ruta recta de 3 km al norte con puntos en 0, 1 y 3 km. */
    private val route = listOf((0..300).map { p(it * 10.0) })
    private val points = listOf(p(0.0), p(1000.0), p(3000.0))

    @Test
    fun `punto tocado mas cercano`() {
        assertEquals(1, PlanEdit.nearestIndex(points, p(1010.0, 5.0), 30.0))
        assertNull(PlanEdit.nearestIndex(points, p(2000.0), 30.0))
    }

    @Test
    fun `insertar en el tramo que toca`() {
        assertEquals(2, PlanEdit.insertIndex(points, route, p(2000.0, 8.0))) // entre el 2º y el 3º
        assertEquals(1, PlanEdit.insertIndex(points, route, p(400.0, -6.0))) // entre el 1º y el 2º
        assertTrue(PlanEdit.distanceToRoute(route, p(2000.0, 8.0)) < 10)
        assertTrue(PlanEdit.distanceToRoute(route, p(2000.0, 300.0)) > 250)
    }

    @Test
    fun `insertar en una circular donde la ruta pasa dos veces`() {
        // Ida al norte y vuelta por el mismo sitio: el toque a 500 m es del primer tramo
        val loop = listOf((0..100).map { p(it * 10.0) } + (99 downTo 0).map { p(it * 10.0) })
        val pts = listOf(p(0.0), p(1000.0), p(0.0))
        assertEquals(1, PlanEdit.insertIndex(pts, loop, p(500.0, 3.0)))
    }

    @Test
    fun `ida y vuelta`() {
        val ob = PlanEdit.outAndBack(points)
        assertEquals(5, ob.size)
        assertEquals(points[1], ob[3])
        assertEquals(points[0], ob.last())
        assertEquals(listOf(points[0]), PlanEdit.outAndBack(listOf(points[0])))
    }
}
