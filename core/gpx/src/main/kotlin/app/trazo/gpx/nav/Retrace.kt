package app.trazo.gpx.nav

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.TrackStatsCalculator

/**
 * Volver al inicio por donde has venido (el TracBack de los Garmin): el
 * track desde donde estás hasta el principio, al revés. Funciona sin
 * conexión porque solo usa la ruta que ya tienes.
 */
object Retrace {

    fun backToStart(segments: List<List<GeoPoint>>, alongMeters: Double): List<List<GeoPoint>> {
        val walked = mutableListOf<GeoPoint>()
        var acc = 0.0
        loop@ for (seg in segments) {
            for ((i, p) in seg.withIndex()) {
                if (i > 0) acc += TrackStatsCalculator.haversineMeters(seg[i - 1], p)
                if (acc > alongMeters) break@loop
                walked += p
            }
        }
        return if (walked.size >= 2) listOf(walked.reversed()) else emptyList()
    }
}
