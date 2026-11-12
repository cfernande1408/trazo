package app.trazo.gpx.nav

import app.trazo.gpx.i18n.L
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.TrackStatsCalculator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos

enum class TurnKind(private val es: String, private val en: String) {
    LEFT("gira a la izquierda", "turn left"),
    RIGHT("gira a la derecha", "turn right"),
    SHARP_LEFT("giro cerrado a la izquierda", "sharp left"),
    SHARP_RIGHT("giro cerrado a la derecha", "sharp right"),
    U_TURN("media vuelta", "make a U-turn"),
    SWITCHBACKS("zona de curvas cerradas", "switchbacks ahead"),
    ;

    val phrase: String get() = L(es, en)
}

data class Turn(val alongMeters: Double, val kind: TurnKind, val angleDegrees: Double)

/**
 * Giros sacados del propio track: donde el rumbo de los 30 m siguientes
 * cambia más de 45° respecto a los 30 m anteriores. Las lazadas seguidas
 * (zetas de sendero) se juntan en un único aviso de "curvas cerradas".
 */
object Turns {

    private const val STEP = 10.0
    private const val WINDOW = 30.0
    private const val MIN_ANGLE = 45.0

    fun detect(segments: List<List<GeoPoint>>): List<Turn> {
        val pts = resample(segments.flatten())
        if (pts.size < 8) return emptyList()
        val w = (WINDOW / STEP).toInt()
        val angles = DoubleArray(pts.size)
        for (i in w until pts.size - w) {
            angles[i] = diff(bearing(pts[i - w], pts[i]), bearing(pts[i], pts[i + w]))
        }
        // Máximos locales por encima del umbral, separados al menos una ventana
        val raw = mutableListOf<Turn>()
        var i = w
        while (i < pts.size - w) {
            if (abs(angles[i]) >= MIN_ANGLE) {
                var j = i
                var best = i
                while (j < pts.size - w && abs(angles[j]) >= MIN_ANGLE && j - i < 2 * w) {
                    if (abs(angles[j]) > abs(angles[best])) best = j
                    j++
                }
                raw += Turn(best * STEP, kindOf(angles[best]), angles[best])
                i = j + w
            } else {
                i++
            }
        }
        // Tres o más giros en 150 m: zetas, un solo aviso al empezar
        val out = mutableListOf<Turn>()
        var k = 0
        while (k < raw.size) {
            var end = k
            while (end + 1 < raw.size && raw[end + 1].alongMeters - raw[k].alongMeters <= 150) end++
            if (end - k >= 2) {
                out += Turn(raw[k].alongMeters, TurnKind.SWITCHBACKS, 0.0)
                // Las lazadas que siguen enganchadas también entran en la misma zona
                var last = end
                while (last + 1 < raw.size && raw[last + 1].alongMeters - raw[last].alongMeters <= 70) last++
                k = last + 1
            } else {
                out += raw[k]
                k++
            }
        }
        return out
    }

    private fun kindOf(a: Double): TurnKind {
        val left = a < 0
        return when (abs(a)) {
            in 0.0..<100.0 -> if (left) TurnKind.LEFT else TurnKind.RIGHT
            in 100.0..<150.0 -> if (left) TurnKind.SHARP_LEFT else TurnKind.SHARP_RIGHT
            else -> TurnKind.U_TURN
        }
    }

    /** Puntos cada [STEP] m a lo largo del track, interpolados. */
    private fun resample(points: List<GeoPoint>): List<GeoPoint> {
        if (points.size < 2) return points
        val out = mutableListOf(points.first())
        var need = STEP // metros hasta el siguiente punto a poner
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val d = TrackStatsCalculator.haversineMeters(a, b)
            var pos = 0.0
            while (d - pos >= need) {
                pos += need
                val t = pos / d
                out += GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
                need = STEP
            }
            need -= d - pos
        }
        return out
    }

    /** Rumbo en grados (0 = norte, 90 = este). */
    private fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val dx = (b.lon - a.lon) * cos(Math.toRadians(a.lat))
        val dy = b.lat - a.lat
        return Math.toDegrees(atan2(dx, dy))
    }

    /** De −180 a 180: negativo gira a la izquierda. */
    private fun diff(from: Double, to: Double): Double {
        var d = to - from
        while (d > 180) d -= 360
        while (d < -180) d += 360
        return d
    }
}
