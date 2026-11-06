package app.trazo.gpx

/**
 * Estación del año (hemisferio norte). Cambia los colores del mapa, cómo se
 * dibujan los árboles y el consejo que da la app antes de salir.
 *
 * Colores como "#RRGGBB" para poder pasarlos tal cual al estilo del mapa y
 * a la vista 3D.
 */
enum class Season(
    val label: String,
    val background: String,
    val grass: String,
    val wood: String,
    val park: String,
    /** Cara iluminada y cara en sombra de los pinos. */
    val pineLight: String,
    val pineShade: String,
    /** Copa de los árboles de hoja ancha (encinas, robles, hayas). */
    val broadleaf: String,
    val broadleafShade: String,
    /** Flores sobre las copas en primavera. */
    val blossom: String?,
    /** Nieve sobre los pinos. */
    val snow: Boolean,
    /** Parte de los árboles del bosque que se dibujan de hoja ancha (0–1). */
    val broadleafShare: Double,
    val tip: String,
) {
    SPRING(
        "Primavera", "#EEF0DF", "#D2E7AE", "#B3D78B", "#C9E3A6",
        "#74B04A", "#4E8A2E", "#8CCB5E", "#5FA03A", "#F4A7C0", false, 0.35,
        "Primavera: barro tras las lluvias y arroyos crecidos. Ojo con los vados.",
    ),
    SUMMER(
        "Verano", "#F1EDD8", "#E4E0B0", "#B2CA8A", "#D3DCA8",
        "#5E9340", "#3F6F2A", "#6FA048", "#4D7F31", null, false, 0.3,
        "Verano: sal temprano, lleva agua de sobra y respeta las restricciones por riesgo de incendio.",
    ),
    AUTUMN(
        "Otoño", "#F0EADB", "#E5D7AA", "#C7CD93", "#DCD3A4",
        "#6E9F48", "#4F7F33", "#E08A2E", "#B5532A", null, false, 0.45,
        "Otoño: hojas y raíces mojadas resbalan y anochece pronto. Lleva luz.",
    ),
    WINTER(
        "Invierno", "#F2F4F2", "#E3E8DE", "#C5D3BD", "#D9E2D3",
        "#5D8A5A", "#3D6B44", "#9C7E62", "#7A5F48", null, true, 0.0,
        "Invierno: hielo en las umbrías y nieve en las cumbres. Mira el parte antes de salir.",
    );

    companion object {
        /** Por fecha: equinoccios y solsticios redondeados al día. */
        fun of(month: Int, day: Int): Season = when {
            month == 12 && day >= 21 || month <= 2 || month == 3 && day < 20 -> WINTER
            month == 3 || month in 4..5 || month == 6 && day < 21 -> SPRING
            month == 6 || month in 7..8 || month == 9 && day < 23 -> SUMMER
            else -> AUTUMN
        }
    }

    /** Lo que necesita la vista 3D para dibujar los árboles, como objeto JSON. */
    fun paletteJson(): String = buildString {
        append('{')
        append("\"pineLight\":\"$pineLight\",\"pineShade\":\"$pineShade\",")
        append("\"broadleaf\":\"$broadleaf\",\"broadleafShade\":\"$broadleafShade\",")
        append("\"blossom\":").append(blossom?.let { "\"$it\"" } ?: "null").append(',')
        append("\"snow\":$snow,\"broadleafShare\":$broadleafShare")
        append('}')
    }
}
