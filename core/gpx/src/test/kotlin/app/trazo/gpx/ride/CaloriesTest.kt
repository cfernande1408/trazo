package app.trazo.gpx.ride

import app.trazo.gpx.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaloriesTest {

    private val h = 3_600_000L

    @Test
    fun `en llano sale del MET y el peso`() {
        // 2 h de bici por llano con 75 kg: 7 × 75 × 2
        assertEquals(1050, Calories.estimate(Activity.BIKE, 2 * h, 0.0, 75.0))
        assertEquals(750, Calories.estimate(Activity.HIKE, 2 * h, 0.0, 75.0))
    }

    @Test
    fun `subir cuesta mas y pesa la bici`() {
        val flat = Calories.estimate(Activity.BIKE, 2 * h, 0.0, 75.0)
        val climb = Calories.estimate(Activity.BIKE, 2 * h, 600.0, 75.0)
        // 88 kg subiendo 600 m al 22 %: unas 563 kcal más
        assertEquals(563.0, (climb - flat).toDouble(), 2.0)
        assertTrue(Calories.estimate(Activity.BIKE, 2 * h, 600.0, 90.0) > climb)
    }

    @Test
    fun `con potenciometro manda la potencia`() {
        // 180 W durante 2 h: 1.296 kJ ≈ 1.296 kcal
        assertEquals(1296, Calories.estimate(Activity.BIKE, 2 * h, 600.0, 75.0, avgPowerW = 180))
        // A pie no hay potencia que valga
        assertEquals(Calories.estimate(Activity.HIKE, h, 0.0, 75.0), Calories.estimate(Activity.HIKE, h, 0.0, 75.0, avgPowerW = 180))
    }

    @Test
    fun `sin moverse no hay gasto`() {
        assertEquals(0, Calories.estimate(Activity.BIKE, 0, 500.0, 75.0))
        assertEquals("1.240 kcal", Calories.format(1240))
    }

    @Test
    fun `metabolismo basal de Mifflin-St Jeor`() {
        // 75 kg, 175 cm, 35 años
        assertEquals(1673.75, Calories.bmr(75.0, 175.0, 35, Calories.Sex.MALE), 0.01)
        assertEquals(1507.75, Calories.bmr(75.0, 175.0, 35, Calories.Sex.FEMALE), 0.01)
        assertEquals(1590.75, Calories.bmr(75.0, 175.0, 35, null), 0.01)
    }

    @Test
    fun `con altura edad y sexo se usa tu metabolismo`() {
        val bmr = Calories.bmr(75.0, 175.0, 35, Calories.Sex.MALE)
        // 2 h en llano: 7 × (1673,75 / 24) × 2 = 976
        assertEquals(976, Calories.estimate(Activity.BIKE, 2 * h, 0.0, 75.0, bmrKcalPerDay = bmr))
        // Una persona mayor gasta menos en reposo y, por tanto, haciendo lo mismo
        val older = Calories.bmr(75.0, 175.0, 65, Calories.Sex.MALE)
        assertTrue(Calories.estimate(Activity.HIKE, 3 * h, 500.0, 75.0, bmrKcalPerDay = older) <
            Calories.estimate(Activity.HIKE, 3 * h, 500.0, 75.0, bmrKcalPerDay = bmr))
    }
}
