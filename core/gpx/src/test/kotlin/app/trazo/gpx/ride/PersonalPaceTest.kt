package app.trazo.gpx.ride

import app.trazo.gpx.Activity
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalPaceTest {

    private val h = 3_600_000L

    /** Una ruta a pie de 10 km y 400 m: MIDE da 2 h 30 (2 h en llano y 1 h 40 en vertical). */
    private fun hike(movingMs: Long) = DoneRoute(Activity.HIKE, 10_000.0, 400.0, 400.0, movingMs)

    @Test
    fun `sin rutas manda la forma fisica`() {
        val p = PersonalPace.of(Activity.HIKE, Fitness.LOW, emptyList())
        assertEquals(1.3, p.factor, 1e-9)
        assertEquals(0, p.rides)
        assertEquals(2 * h, p.comfortMs)
        // Sin decir nada, como quien sale de vez en cuando
        assertEquals(1.1, PersonalPace.of(Activity.HIKE, null, emptyList()).factor, 1e-9)
    }

    @Test
    fun `con tus rutas manda lo que haces`() {
        val est = Activity.HIKE.estimateMs(10_000.0, 400.0, 400.0)
        // Tardas un 50 % más que MIDE en cuatro rutas; dices que entrenas en serio (0,85)
        val done = List(4) { hike((est * 1.5).toLong()) }
        val p = PersonalPace.of(Activity.HIKE, Fitness.TRAINED, done)
        assertEquals((4 * 1.5 + 2 * 0.85) / 6, p.factor, 1e-3)
        assertEquals(4, p.rides)
        // Con una sola ruta pesa más lo que dices
        val one = PersonalPace.of(Activity.HIKE, Fitness.TRAINED, done.take(1))
        assertEquals((1.5 + 2 * 0.85) / 3, one.factor, 1e-3)
    }

    @Test
    fun `no cuentan las cortas ni las de otra actividad`() {
        val done = listOf(
            DoneRoute(Activity.HIKE, 800.0, 10.0, 10.0, h),
            DoneRoute(Activity.HIKE, 5_000.0, 50.0, 50.0, 5 * 60_000),
            DoneRoute(Activity.BIKE, 30_000.0, 600.0, 600.0, 3 * h),
        )
        assertEquals(0, PersonalPace.of(Activity.HIKE, Fitness.REGULAR, done).rides)
    }

    @Test
    fun `lo comodo sale de lo que sueles durar`() {
        val done = listOf(1, 2, 3, 4, 5).map { hike(it * h) }
        // Tres de cada cuatro duran 4 h o menos
        assertEquals(4 * h, PersonalPace.of(Activity.HIKE, Fitness.LOW, done).comfortMs)
    }

    @Test
    fun `estimacion y esfuerzo a tu ritmo`() {
        val p = PersonalPace(1.2, 3, 4 * h)
        val base = Activity.BIKE.estimateMs(28_000.0, 600.0, 600.0) // 3 h
        assertEquals(base * 1.2, p.estimateMs(Activity.BIKE, 28_000.0, 600.0, 600.0).toDouble(), 1.0)
        assertEquals(Effort.EASY, p.effort(2 * h))
        assertEquals(Effort.MODERATE, p.effort(4 * h))
        assertEquals(Effort.HARD, p.effort(6 * h))
        assertEquals(Effort.VERY_HARD, p.effort(7 * h))
    }
}
