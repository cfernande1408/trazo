package app.trazo.gpx.nav

import app.trazo.gpx.i18n.L
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.Gpx
import app.trazo.gpx.TrackStatsCalculator

/** Un sitio desde el que empezar la ruta. */
data class StartOption(
    val label: String,
    val lat: Double,
    val lon: Double,
    /** Punto del track donde se empieza (metros desde el inicio original). */
    val alongMeters: Double,
    /** Lo que separa el sitio del track (0 para el inicio de la ruta). */
    val distanceToTrackMeters: Double,
)

object RouteStart {

    /** Un punto del autor más lejos que esto del track no se ofrece como inicio. */
    const val MAX_WAYPOINT_DISTANCE = 300.0

    /** Si el final queda a menos de esto del principio, la ruta es circular. */
    const val LOOP_THRESHOLD = 200.0

    /** El inicio de la ruta y los puntos del autor que están sobre el track, en orden de paso. */
    fun options(gpx: Gpx): List<StartOption> {
        val segs = gpx.segments
        val first = segs.firstOrNull()?.firstOrNull() ?: return emptyList()
        val matcher = TrackMatcher(segs)
        val start = StartOption(L("Inicio de la ruta", "Start of the route"), first.lat, first.lon, 0.0, 0.0)
        val wpts = gpx.waypoints.mapNotNull { w ->
            val pos = matcher.locate(w.lat, w.lon)
            if (pos.distanceToTrackMeters > MAX_WAYPOINT_DISTANCE) null
            else StartOption(w.name ?: L("Punto sin nombre", "Unnamed point"), w.lat, w.lon, pos.alongMeters, pos.distanceToTrackMeters)
        }.sortedBy { it.alongMeters }
        return listOf(start) + wpts
    }

    fun isLoop(segments: List<List<GeoPoint>>): Boolean {
        val a = segments.firstOrNull()?.firstOrNull() ?: return false
        val b = segments.lastOrNull()?.lastOrNull() ?: return false
        return segments.sumOf { it.size } > 2 && TrackStatsCalculator.haversineMeters(a, b) <= LOOP_THRESHOLD
    }

    /**
     * La ruta empezando en [alongMeters]. Si es circular se recorre entera y
     * se acaba donde se empezó (lo que había antes del punto pasa al final).
     * Si no lo es, se recorta lo anterior al punto.
     */
    fun from(segments: List<List<GeoPoint>>, alongMeters: Double): List<List<GeoPoint>> {
        if (alongMeters <= 0.0 || segments.isEmpty()) return segments
        val loop = isLoop(segments)

        var acc = 0.0
        for ((k, seg) in segments.withIndex()) {
            for (j in seg.indices) {
                if (j > 0) acc += TrackStatsCalculator.haversineMeters(seg[j - 1], seg[j])
                if (acc >= alongMeters) {
                    val tail = listOf(seg.subList(j, seg.size)) + segments.drop(k + 1)
                    if (!loop) return tail.filter { it.isNotEmpty() }
                    val head = segments.take(k) + listOf(seg.subList(0, j + 1))
                    // El final y el principio están juntos: se unen en un solo tramo
                    val joined = tail.dropLast(1) + listOf(tail.last() + head.first()) + head.drop(1)
                    return joined.filter { it.isNotEmpty() }
                }
            }
        }
        return segments
    }
}
