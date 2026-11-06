package app.trazo.gpx.osm

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.TrackStatsCalculator

/**
 * Las rutas de OSM no traen altitud. Se consulta en unos pocos puntos (uno
 * cada [sampleIndices]) y el resto se interpola por distancia.
 */
object ElevationFill {

    /** Índices (segmento, punto) a consultar: el primero y el último de cada tramo y uno cada [everyMeters]. */
    fun sampleIndices(
        segments: List<List<GeoPoint>>,
        everyMeters: Double = 100.0,
        max: Int = 1000,
    ): List<Pair<Int, Int>> {
        val total = segments.sumOf { seg -> seg.zipWithNext { a, b -> TrackStatsCalculator.haversineMeters(a, b) }.sum() }
        val fixed = segments.count { it.isNotEmpty() } * 2
        val step = maxOf(everyMeters, if (max > fixed) total / (max - fixed) else total)
        val out = mutableListOf<Pair<Int, Int>>()
        for ((s, seg) in segments.withIndex()) {
            if (seg.isEmpty()) continue
            out.add(s to 0)
            var since = 0.0
            for (j in 1 until seg.size) {
                since += TrackStatsCalculator.haversineMeters(seg[j - 1], seg[j])
                if (j == seg.lastIndex) out.add(s to j)
                else if (since >= step) { out.add(s to j); since = 0.0 }
            }
        }
        return out
    }

    /** Pone a cada punto la altitud interpolada entre los puntos consultados de su tramo. */
    fun fill(
        segments: List<List<GeoPoint>>,
        samples: List<Pair<Int, Int>>,
        elevations: List<Double>,
    ): List<List<GeoPoint>> {
        require(samples.size == elevations.size) { "Faltan altitudes" }
        val bySeg = samples.zip(elevations).groupBy({ it.first.first }, { it.first.second to it.second })
        return segments.mapIndexed { s, seg ->
            val known = bySeg[s]?.sortedBy { it.first } ?: return@mapIndexed seg
            val along = DoubleArray(seg.size).also { a ->
                for (j in 1 until seg.size) a[j] = a[j - 1] + TrackStatsCalculator.haversineMeters(seg[j - 1], seg[j])
            }
            var k = 0
            seg.mapIndexed { j, p ->
                while (k < known.size - 1 && known[k + 1].first <= j) k++
                val (i0, e0) = known[k]
                val ele = when {
                    j <= i0 || k == known.size - 1 -> e0
                    else -> {
                        val (i1, e1) = known[k + 1]
                        val span = along[i1] - along[i0]
                        if (span <= 0) e0 else e0 + (e1 - e0) * (along[j] - along[i0]) / span
                    }
                }
                p.copy(ele = ele)
            }
        }
    }
}
