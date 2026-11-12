package app.trazo.gpx.nav

import app.trazo.gpx.i18n.L
/** Cifras que puedes poner en la pantalla de navegación, como en un GPS de bici. */
enum class DataField(private val es: String, private val en: String) {
    REMAINING("Quedan", "To go"),
    REMAINING_GAIN("Por subir", "Climb left"),
    DONE("Recorrido", "Done"),
    SPEED("Velocidad", "Speed"),
    AVG_SPEED("Media", "Average"),
    MOVING_TIME("En marcha", "Moving"),
    ALTITUDE("Altitud", "Altitude"),
    GRADE("Pendiente", "Gradient"),
    ETA("Llegada", "Arrival"),
    LIGHT("Luz", "Daylight"),
    HEART_RATE("Pulso", "Heart rate"),
    CADENCE("Cadencia", "Cadence"),
    POWER("Potencia", "Power"),
    PARTNER("Vs. anterior", "Vs. last time"),
    KCAL("Kcal", "Kcal");

    val label: String get() = L(es, en)

    companion object {
        val DEFAULT = listOf(REMAINING, REMAINING_GAIN, DONE, SPEED, MOVING_TIME, ETA)
        const val SLOTS = 6

        fun encode(fields: List<DataField>) = fields.joinToString(",") { it.name }

        /** Lo guardado, completando o recortando a [SLOTS]; lo que no se entienda vuelve a lo de serie. */
        fun decode(s: String?): List<DataField> {
            val parsed = s?.split(",")?.mapNotNull { n -> entries.firstOrNull { it.name == n } }.orEmpty()
            return (parsed + DEFAULT.filter { it !in parsed }).take(SLOTS)
        }
    }
}
