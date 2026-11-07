package app.trazo.gpx

/**
 * Cuánto se exagera el relieve en la vista 3D. A escala real la sierra
 * parece una colina vista desde arriba, así que se exagera más en rutas con
 * poco desnivel y algo menos en las de mucho; el usuario puede subirlo o
 * bajarlo con [LEVELS].
 */
object Relief {

    /** Multiplicadores que elige el usuario: suave, menos, normal, más, extremo. */
    val LEVELS = listOf(0.6, 0.8, 1.0, 1.3, 1.7)
    const val DEFAULT_LEVEL = 2

    /** [reliefMeters]: altitud máxima menos mínima de la ruta. */
    fun base(reliefMeters: Double): Double =
        if (reliefMeters > 0) (1300.0 / reliefMeters).coerceIn(2.0, 3.5) else 2.0

    fun exaggeration(reliefMeters: Double, level: Int = DEFAULT_LEVEL): Double =
        base(reliefMeters) * LEVELS[level.coerceIn(0, LEVELS.lastIndex)]
}
