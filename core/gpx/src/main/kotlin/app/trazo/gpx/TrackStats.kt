package app.trazo.gpx

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class TrackStats(
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val minElevation: Double?,
    val maxElevation: Double?,
    val pointCount: Int,
)

object TrackStatsCalculator {

    private const val EARTH_RADIUS_M = 6_371_008.8

    /**
     * La distancia se suma dentro de cada segmento (no se une el hueco entre
     * segmentos). El desnivel usa histéresis: solo cuenta un cambio cuando
     * supera [elevationThreshold] respecto al último valor aceptado, para
     * no sumar el ruido del GPS como si fueran subidas.
     */
    fun compute(segments: List<List<GeoPoint>>, elevationThreshold: Double = 3.0): TrackStats {
        var distance = 0.0
        var gain = 0.0
        var loss = 0.0
        var minEle: Double? = null
        var maxEle: Double? = null
        var ref: Double? = null
        var count = 0

        for (seg in segments) {
            for ((idx, p) in seg.withIndex()) {
                count++
                if (idx > 0) distance += haversineMeters(seg[idx - 1], p)
                val ele = p.ele ?: continue
                minEle = minOf(minEle ?: ele, ele)
                maxEle = maxOf(maxEle ?: ele, ele)
                val r = ref
                when {
                    r == null -> ref = ele
                    ele - r >= elevationThreshold -> { gain += ele - r; ref = ele }
                    r - ele >= elevationThreshold -> { loss += r - ele; ref = ele }
                }
            }
        }
        return TrackStats(distance, gain, loss, minEle, maxEle, count)
    }

    fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }
}
