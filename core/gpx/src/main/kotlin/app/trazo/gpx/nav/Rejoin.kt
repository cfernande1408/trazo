package app.trazo.gpx.nav

import app.trazo.gpx.i18n.L
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.TrackStatsCalculator

/**
 * Recalcular al salirte, como los GPS de coche pero sin perder la ruta: se
 * busca el punto de la ruta, por delante de donde la dejaste, que te queda
 * más cerca, y se navega el camino hasta él y luego el resto de la ruta.
 */
/**
 * La ruta tras recalcular, y cómo pasar de sus metros a los de la ruta
 * original (para volver al inicio o el compañero virtual).
 */
data class Rerouted(
    val segments: List<List<GeoPoint>>,
    /** El camino de vuelta, para dibujarlo. */
    val path: List<GeoPoint>,
    val pathMeters: Double,
    /** Metros de la ruta original donde la dejaste y donde vuelves a ella. */
    val leftAt: Double,
    val joinAt: Double,
) {
    /** Metros de la ruta original que corresponden a [alongMeters] de la recalculada. */
    fun routeAlong(alongMeters: Double): Double =
        if (alongMeters < pathMeters) leftAt else joinAt + (alongMeters - pathMeters)
}

object Rejoin {

    /** Hasta dónde por delante se busca dónde volver. */
    const val LOOK_AHEAD_M = 1_500.0
    /** Cada cuánto se mira un punto de la ruta. */
    private const val STEP_M = 25.0

    data class Target(
        /** Metros desde el inicio de la ruta. */
        val alongMeters: Double,
        val point: GeoPoint,
        /** En línea recta desde donde estás. */
        val straightMeters: Double,
    )

    /**
     * El punto de la ruta al que volver: el más cercano a [here] entre
     * [lastAlongMeters] (donde la dejaste) y [lookAheadMeters] más adelante.
     */
    fun target(
        segments: List<List<GeoPoint>>,
        lastAlongMeters: Double,
        here: GeoPoint,
        lookAheadMeters: Double = LOOK_AHEAD_M,
    ): Target? {
        var best: Target? = null
        fun consider(along: Double, p: GeoPoint) {
            if (along < lastAlongMeters || along > lastAlongMeters + lookAheadMeters) return
            val d = TrackStatsCalculator.haversineMeters(here, p)
            if (best == null || d < best!!.straightMeters) best = Target(along, p, d)
        }
        walk(segments) { along, a, b, len ->
            var t = 0.0
            while (t < len) {
                consider(along + t, lerp(a, b, t / len))
                t += STEP_M
            }
            if (len == 0.0) consider(along, a)
        }
        // El último punto de la ruta también cuenta (por si volver es terminar)
        segments.lastOrNull()?.lastOrNull()?.let { consider(totalMeters(segments), it) }
        return best
    }

    /**
     * La ruta que se navega tras recalcular: [path] (de donde estás al punto de
     * la ruta) seguido de lo que queda de la ruta desde [targetAlongMeters].
     */
    fun join(path: List<GeoPoint>, segments: List<List<GeoPoint>>, targetAlongMeters: Double): List<List<GeoPoint>> {
        val rest = tail(segments, targetAlongMeters)
        if (path.isEmpty()) return rest
        if (rest.isEmpty()) return listOf(path)
        // El camino acaba en el punto de la ruta: no se repite
        return listOf(path + rest.first().drop(1)) + rest.drop(1)
    }

    /**
     * La ruta recalculada: [path] desde donde estás hasta [target] y luego el resto.
     * [leftAt] es dónde dejaste la ruta, en metros de [navSegments] (la que ibas
     * navegando, que puede ser ya una recalculada: [previous]).
     */
    fun reroute(
        path: List<GeoPoint>,
        navSegments: List<List<GeoPoint>>,
        target: Target,
        leftAt: Double,
        previous: Rerouted? = null,
    ): Rerouted {
        fun toRoute(a: Double) = previous?.routeAlong(a) ?: a
        return Rerouted(
            segments = join(path, navSegments, target.alongMeters),
            path = path,
            pathMeters = (1 until path.size).sumOf { TrackStatsCalculator.haversineMeters(path[it - 1], path[it]) },
            leftAt = toRoute(leftAt),
            joinAt = toRoute(target.alongMeters),
        )
    }

    /** Lo que se dice al recalcular. */
    fun phrase(r: Rerouted) = L(
        "Ruta recalculada. Vuelves a la ruta en ${spokenMeters(r.pathMeters)}.",
        "Route recalculated. You rejoin the route in ${spokenMeters(r.pathMeters)}.",
    )

    /** La ruta desde [alongMeters] hasta el final, empezando justo en ese punto. */
    internal fun tail(segments: List<List<GeoPoint>>, alongMeters: Double): List<List<GeoPoint>> {
        var acc = 0.0
        for ((k, seg) in segments.withIndex()) {
            for (j in 1 until seg.size) {
                val len = TrackStatsCalculator.haversineMeters(seg[j - 1], seg[j])
                if (acc + len >= alongMeters) {
                    val start = if (len == 0.0) seg[j] else lerp(seg[j - 1], seg[j], ((alongMeters - acc) / len).coerceIn(0.0, 1.0))
                    // Si cae justo en un punto de la ruta, no se repite
                    val rest = seg.subList(j, seg.size).let { r ->
                        if (TrackStatsCalculator.haversineMeters(start, r.first()) < 0.5) r.drop(1) else r
                    }
                    return (listOf(listOf(start) + rest) + segments.drop(k + 1)).filter { it.size >= 2 }
                }
                acc += len
            }
        }
        return emptyList()
    }

    private fun totalMeters(segments: List<List<GeoPoint>>): Double {
        var acc = 0.0
        walk(segments) { _, _, _, len -> acc += len }
        return acc
    }

    /** Cada tramo de la ruta con los metros recorridos hasta su inicio. */
    private inline fun walk(segments: List<List<GeoPoint>>, f: (along: Double, a: GeoPoint, b: GeoPoint, len: Double) -> Unit) {
        var acc = 0.0
        for (seg in segments) {
            for (j in 1 until seg.size) {
                val len = TrackStatsCalculator.haversineMeters(seg[j - 1], seg[j])
                f(acc, seg[j - 1], seg[j], len)
                acc += len
            }
        }
    }

    private fun lerp(a: GeoPoint, b: GeoPoint, t: Double) = GeoPoint(
        a.lat + (b.lat - a.lat) * t,
        a.lon + (b.lon - a.lon) * t,
        if (a.ele != null && b.ele != null) a.ele + (b.ele - a.ele) * t else a.ele ?: b.ele,
    )
}
