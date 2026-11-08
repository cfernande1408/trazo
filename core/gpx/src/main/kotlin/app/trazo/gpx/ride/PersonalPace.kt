package app.trazo.gpx.ride

import app.trazo.gpx.Activity
import kotlin.math.roundToLong

/** Cuánto sales, según tú: ajusta el ritmo mientras Trazo no tenga rutas tuyas. */
enum class Fitness(
    val label: String,
    val hint: String,
    /** Tu tiempo frente al estimado de serie (MIDE a pie, 14 km/h y 600 m/h en bici). */
    val paceFactor: Double,
    /** Horas en marcha que se te hacen cómodas. */
    val comfortHours: Double,
) {
    LOW("Salgo poco", "Menos de una vez al mes", 1.3, 2.0),
    CASUAL("De vez en cuando", "Una o dos veces al mes", 1.1, 3.0),
    REGULAR("Cada semana", "Una o dos salidas a la semana", 1.0, 4.0),
    TRAINED("Entreno en serio", "Varias veces por semana", 0.85, 5.5),
}

/** Lo que pesa una ruta para ti, según lo que se te hace cómodo. */
enum class Effort(val label: String) {
    EASY("Fácil para ti"),
    MODERATE("A tu medida"),
    HARD("Exigente para ti"),
    VERY_HARD("Muy dura para ti"),
}

/** Una ruta que has hecho: lo que hiciste y lo que tardaste en marcha. */
data class DoneRoute(
    val activity: Activity,
    val distanceMeters: Double,
    val gainMeters: Double,
    val lossMeters: Double,
    val movingMs: Long,
)

/**
 * Tu ritmo: cuánto tardas frente a la estimación de serie ([factor]: 1,2 es
 * un 20 % más) y cuánto tiempo en marcha se te hace cómodo.
 *
 * Sale de tus rutas realizadas de esa actividad, mezcladas con lo que dices
 * de tu forma física como si valiera por [PRIOR_WEIGHT] rutas: con una o dos
 * salidas pesa más lo que dices; a partir de tres, lo que haces.
 */
data class PersonalPace(
    val factor: Double,
    /** Rutas tuyas que se han tenido en cuenta. */
    val rides: Int,
    val comfortMs: Long,
) {
    /** Tiempo en marcha estimado a tu ritmo. */
    fun estimateMs(activity: Activity, distanceMeters: Double, gainMeters: Double, lossMeters: Double): Long =
        (activity.estimateMs(distanceMeters, gainMeters, lossMeters) * factor).roundToLong()

    /** Lo dura que es una ruta para ti, por lo que tardarías en hacerla. */
    fun effort(estimatedMs: Long): Effort {
        val r = estimatedMs.toDouble() / comfortMs
        return when {
            r <= 0.5 -> Effort.EASY
            r <= 1.0 -> Effort.MODERATE
            r <= 1.6 -> Effort.HARD
            else -> Effort.VERY_HARD
        }
    }

    companion object {
        const val PRIOR_WEIGHT = 2.0
        /** Las más recientes; si mejoras, lo antiguo no debe pesar. */
        const val MAX_RIDES = 10
        /** Rutas más cortas que esto (o más breves) dicen poco de tu ritmo. */
        const val MIN_METERS = 2_000.0
        const val MIN_MOVING_MS = 20 * 60_000L
        private const val HOUR = 3_600_000.0

        /**
         * Tu ritmo para [activity]. [done] va de la más reciente a la más
         * antigua; sin forma física se cuenta como alguien que sale de vez en cuando.
         */
        fun of(activity: Activity, fitness: Fitness?, done: List<DoneRoute>): PersonalPace {
            val prior = fitness ?: Fitness.CASUAL
            val useful = done
                .filter { it.activity == activity && it.distanceMeters >= MIN_METERS && it.movingMs >= MIN_MOVING_MS }
                .take(MAX_RIDES)
            val estimated = useful.sumOf { activity.estimateMs(it.distanceMeters, it.gainMeters, it.lossMeters) }
            val n = useful.size
            val factor = if (n == 0 || estimated <= 0) {
                prior.paceFactor
            } else {
                // Tiempo real frente al estimado, sumando todas: las largas pesan más
                val k = (useful.sumOf { it.movingMs }.toDouble() / estimated).coerceIn(0.4, 2.5)
                (n * k + PRIOR_WEIGHT * prior.paceFactor) / (n + PRIOR_WEIGHT)
            }
            // Cómodo: con tres o más rutas, lo que duran tres de cada cuatro de las tuyas
            val comfort = if (n >= 3) {
                val sorted = useful.map { it.movingMs }.sorted()
                sorted[((sorted.size - 1) * 0.75).toInt()].toDouble().coerceAtLeast(HOUR)
            } else {
                prior.comfortHours * HOUR
            }
            return PersonalPace(factor, n, comfort.roundToLong())
        }
    }
}
