package app.trazo.gpx.plan

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.TrackStatsCalculator
import app.trazo.gpx.osm.Difficulty

/** Editar los puntos de una ruta en el planificador: tocar, mover, insertar e ida y vuelta. */
object PlanEdit {

    /** El punto más cercano a [tap] a menos de [maxMeters], o null. */
    fun nearestIndex(points: List<GeoPoint>, tap: GeoPoint, maxMeters: Double): Int? =
        points.indices
            .map { it to TrackStatsCalculator.haversineMeters(points[it], tap) }
            .filter { it.second <= maxMeters }
            .minByOrNull { it.second }?.first

    /** Distancia de [tap] a la ruta calculada. */
    fun distanceToRoute(route: List<List<GeoPoint>>, tap: GeoPoint): Double =
        route.filter { it.size >= 2 }.minOfOrNull { Difficulty.distanceToLine(tap, it) } ?: Double.MAX_VALUE

    /**
     * Dónde meter un punto tocado sobre la ruta: entre los dos puntos del
     * tramo por el que pasa. Se mira en qué km de la ruta cae el toque y en
     * qué km queda cada punto.
     */
    fun insertIndex(points: List<GeoPoint>, route: List<List<GeoPoint>>, tap: GeoPoint): Int {
        if (points.size < 2) return points.size
        val line = route.flatten()
        val tapAlong = along(line, tap)
        // Los puntos van en orden por la ruta: se busca el primero que queda más allá del toque
        var from = 0.0
        for (i in 1 until points.size) {
            val a = along(line, points[i], from)
            if (a >= tapAlong) return i
            from = a
        }
        return points.size - 1
    }

    /** Ida y vuelta por el mismo camino: los puntos y luego los mismos al revés. */
    fun outAndBack(points: List<GeoPoint>): List<GeoPoint> =
        if (points.size < 2) points else points + points.reversed().drop(1)

    /** Km de la ruta (en metros) donde [p] queda más cerca, buscando a partir de [fromMeters]. */
    private fun along(line: List<GeoPoint>, p: GeoPoint, fromMeters: Double = 0.0): Double {
        var acc = 0.0
        var best = Double.MAX_VALUE
        var at = 0.0
        for (i in 1 until line.size) {
            val seg = TrackStatsCalculator.haversineMeters(line[i - 1], line[i])
            if (acc + seg >= fromMeters) {
                val d = Difficulty.distanceToLine(p, listOf(line[i - 1], line[i]))
                if (d < best - 0.5) {
                    best = d
                    // Dentro del tramo, por proporción a lo cerca que está de cada extremo
                    val da = TrackStatsCalculator.haversineMeters(line[i - 1], p)
                    val db = TrackStatsCalculator.haversineMeters(line[i], p)
                    at = acc + if (da + db > 0) seg * da / (da + db) else 0.0
                }
            }
            acc += seg
        }
        return at
    }
}
