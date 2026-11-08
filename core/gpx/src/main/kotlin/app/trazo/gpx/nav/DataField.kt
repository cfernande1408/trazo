package app.trazo.gpx.nav

/** Cifras que puedes poner en la pantalla de navegación, como en un GPS de bici. */
enum class DataField(val label: String) {
    REMAINING("Quedan"),
    REMAINING_GAIN("Por subir"),
    DONE("Recorrido"),
    SPEED("Velocidad"),
    AVG_SPEED("Media"),
    MOVING_TIME("En movimiento"),
    ALTITUDE("Altitud"),
    GRADE("Pendiente"),
    ETA("Llegada"),
    LIGHT("Luz"),
    HEART_RATE("Pulso"),
    CADENCE("Cadencia"),
    POWER("Potencia"),
    PARTNER("Vs. anterior"),
    KCAL("Kcal");

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
