package app.trazo.gpx.osm

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.RouteGeometry
import app.trazo.gpx.TrackStatsCalculator
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sqrt

/** Un camino de OSM con su dificultad: mtb:scale (0–6) y sac_scale (1–6). */
data class RatedWay(val points: List<GeoPoint>, val mtb: Int?, val sac: Int?)

/** Cuántos metros de la ruta van por cada grado de dificultad. */
data class DifficultySummary(
    val routeMeters: Double,
    val metersByGrade: Map<Int, Double>,
) {
    val ratedMeters: Double get() = metersByGrade.values.sum()
    val maxGrade: Int? get() = metersByGrade.keys.maxOrNull()
    /** El grado por el que va la mayor parte de lo que tiene dificultad marcada. */
    val mainGrade: Int? get() = metersByGrade.maxByOrNull { it.value }?.key
}

/**
 * Dificultad de la ruta según OpenStreetMap: se piden a Overpass los caminos
 * con `mtb:scale` o `sac_scale` junto al track y se mira por cuál va cada
 * tramo. Vale para cualquier GPX, no solo para rutas de OSM.
 */
object Difficulty {

    val MTB_LABELS = listOf(
        "S0" to "pista o sendero fácil",
        "S1" to "raíces y piedras pequeñas",
        "S2" to "escalones bajos y piedra suelta",
        "S3" to "bloques, escalones y curvas cerradas",
        "S4" to "muy técnico, pasos de trial",
        "S5" to "extremo",
        "S6" to "no ciclable",
    )
    /** Índice 1–6 → T1–T6. */
    val SAC_LABELS = listOf(
        "T1" to "senderismo",
        "T2" to "senderismo de montaña",
        "T3" to "montaña exigente",
        "T4" to "alpino",
        "T5" to "alpino exigente",
        "T6" to "alpino difícil",
    )
    private val SAC = mapOf(
        "hiking" to 1, "mountain_hiking" to 2, "demanding_mountain_hiking" to 3,
        "alpine_hiking" to 4, "demanding_alpine_hiking" to 5, "difficult_alpine_hiking" to 6,
    )

    fun label(grade: Int, mtb: Boolean): Pair<String, String> =
        if (mtb) MTB_LABELS[grade.coerceIn(0, 6)] else SAC_LABELS[(grade - 1).coerceIn(0, 5)]

    /** Consulta: caminos con dificultad a menos de 15 m del track (reducido a ~120 puntos). */
    fun query(segments: List<List<GeoPoint>>, radiusMeters: Int = 15): String {
        val pts = RouteGeometry.downsample(segments.flatten(), 120)
        val line = pts.joinToString(",") { String.format(Locale.ROOT, "%.5f,%.5f", it.lat, it.lon) }
        return "[out:json][timeout:25];way(around:$radiusMeters,$line)[~\"^(mtb:scale|sac_scale)$\"~\".\"];out tags geom;"
    }

    fun parse(json: String): List<RatedWay> {
        val root = Json.parse(json) as? Map<*, *> ?: return emptyList()
        return (root["elements"] as? List<*>).orEmpty().mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            if (m["type"] != "way") return@mapNotNull null
            val tags = m["tags"] as? Map<*, *> ?: return@mapNotNull null
            val geom = (m["geometry"] as? List<*>).orEmpty().mapNotNull { g ->
                val p = g as? Map<*, *> ?: return@mapNotNull null
                GeoPoint(p["lat"] as? Double ?: return@mapNotNull null, p["lon"] as? Double ?: return@mapNotNull null)
            }
            if (geom.size < 2) return@mapNotNull null
            // "2+" o "1-" se quedan en su número
            val mtb = (tags["mtb:scale"] as? String)?.firstOrNull()?.digitToIntOrNull()?.coerceIn(0, 6)
            val sac = SAC[tags["sac_scale"] as? String]
            if (mtb == null && sac == null) null else RatedWay(geom, mtb, sac)
        }
    }

    /**
     * Recorre la ruta cada ~25 m y apunta el grado del camino con dificultad
     * más cercano (a menos de [maxMeters]); si no hay ninguno, ese tramo no cuenta.
     */
    fun summarize(segments: List<List<GeoPoint>>, ways: List<RatedWay>, mtb: Boolean, maxMeters: Double = 15.0): DifficultySummary {
        val rated = ways.mapNotNull { w -> (if (mtb) w.mtb else w.sac)?.let { it to w.points } }
        val byGrade = mutableMapOf<Int, Double>()
        var total = 0.0
        for (seg in segments) {
            for (i in 1 until seg.size) {
                val a = seg[i - 1]
                val b = seg[i]
                val d = TrackStatsCalculator.haversineMeters(a, b)
                total += d
                if (rated.isEmpty() || d == 0.0) continue
                val mid = GeoPoint((a.lat + b.lat) / 2, (a.lon + b.lon) / 2)
                var best: Int? = null
                var bestDist = maxMeters
                for ((grade, pts) in rated) {
                    val dist = distanceToLine(mid, pts)
                    if (dist <= bestDist) { bestDist = dist; best = grade }
                }
                best?.let { byGrade[it] = (byGrade[it] ?: 0.0) + d }
            }
        }
        return DifficultySummary(total, byGrade.toSortedMap())
    }

    /** Distancia en metros de un punto a una polilínea (proyección plana local, vale a estas escalas). */
    internal fun distanceToLine(p: GeoPoint, line: List<GeoPoint>): Double {
        val k = cos(Math.toRadians(p.lat))
        fun x(g: GeoPoint) = (g.lon - p.lon) * 111_320 * k
        fun y(g: GeoPoint) = (g.lat - p.lat) * 110_540
        var best = Double.MAX_VALUE
        for (i in 1 until line.size) {
            val ax = x(line[i - 1]); val ay = y(line[i - 1])
            val bx = x(line[i]); val by = y(line[i])
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val cx = ax + t * dx; val cy = ay + t * dy
            val d = sqrt(cx * cx + cy * cy)
            if (d < best) best = d
        }
        return best
    }
}
