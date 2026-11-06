package app.trazo.gpx

import kotlin.math.cos
import kotlin.math.min

data class Bounds(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

data class XY(val x: Float, val y: Float)

object RouteGeometry {

    fun bounds(segments: List<List<GeoPoint>>): Bounds? {
        val all = segments.flatten()
        if (all.isEmpty()) return null
        return Bounds(all.minOf { it.lat }, all.maxOf { it.lat }, all.minOf { it.lon }, all.maxOf { it.lon })
    }

    /**
     * Proyecta la ruta a un recuadro de [width]×[height] (píxeles), centrada,
     * con el norte arriba y sin deformarla: corrige la longitud por cos(lat)
     * y usa la misma escala en los dos ejes.
     */
    fun project(
        segments: List<List<GeoPoint>>,
        width: Float,
        height: Float,
        padding: Float = 0f,
    ): List<List<XY>> {
        val b = bounds(segments) ?: return emptyList()
        val k = cos(Math.toRadians((b.minLat + b.maxLat) / 2))
        val spanX = (b.maxLon - b.minLon) * k
        val spanY = b.maxLat - b.minLat
        val availW = (width - 2 * padding).toDouble()
        val availH = (height - 2 * padding).toDouble()
        val scale = when {
            spanX == 0.0 && spanY == 0.0 -> 0.0
            spanX == 0.0 -> availH / spanY
            spanY == 0.0 -> availW / spanX
            else -> min(availW / spanX, availH / spanY)
        }
        val offX = padding + (availW - spanX * scale) / 2
        val offY = padding + (availH - spanY * scale) / 2
        return segments.map { seg ->
            seg.map { p ->
                XY(
                    x = (offX + (p.lon - b.minLon) * k * scale).toFloat(),
                    y = (offY + (b.maxLat - p.lat) * scale).toFloat(),
                )
            }
        }
    }

    /**
     * Punto de la ruta a [meters] del inicio, interpolado entre los dos puntos
     * que lo rodean (también la altitud). Misma distancia que el perfil: el
     * hueco entre segmentos no cuenta.
     */
    fun pointAt(segments: List<List<GeoPoint>>, meters: Double): GeoPoint? {
        var acc = 0.0
        var last: GeoPoint? = null
        for (seg in segments) {
            if (seg.isEmpty()) continue
            if (meters <= acc) return seg.first()
            for (j in 1 until seg.size) {
                val a = seg[j - 1]
                val b = seg[j]
                val d = TrackStatsCalculator.haversineMeters(a, b)
                if (acc + d >= meters) {
                    val t = if (d > 0) (meters - acc) / d else 0.0
                    val ele = if (a.ele != null && b.ele != null) a.ele + (b.ele - a.ele) * t else a.ele ?: b.ele
                    return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t, ele)
                }
                acc += d
            }
            last = seg.last()
        }
        return last
    }

    /** Se queda con como mucho [max] elementos repartidos uniformemente, conservando el primero y el último. */
    fun <T> downsample(items: List<T>, max: Int): List<T> {
        require(max >= 2) { "max debe ser al menos 2" }
        if (items.size <= max) return items
        val step = (items.size - 1).toDouble() / (max - 1)
        return List(max) { i -> items[Math.round(i * step).toInt()] }
    }
}
