package app.trazo.gpx

import java.util.Locale
import kotlin.math.abs

/** Pendiente media de un tramo de la ruta, en % (positiva: sube). */
data class Grade(val startMeters: Double, val endMeters: Double, val percent: Double)

data class SlopeSummary(
    val maxUpPercent: Double,
    val maxUpAtMeters: Double,
    val maxDownPercent: Double,
    /** Metros de subida por encima del 10 % y del 15 %. */
    val upOver10Meters: Double,
    val upOver15Meters: Double,
)

/**
 * Pendiente por tramos de ~100 m: suficiente para ver las rampas sin que el
 * ruido del GPS o del modelo de altitud las invente.
 */
object Slope {

    /** Colores de menos a más empinado: llano, suave, medio, duro, muro. */
    val COLORS = listOf("#2E7D32", "#9E9D24", "#F9A825", "#EF6C00", "#B71C1C")
    val LABELS = listOf("< 5 %", "5–10 %", "10–15 %", "15–20 %", "> 20 %")

    fun bucket(percent: Double): Int = when (abs(percent)) {
        in 0.0..<5.0 -> 0
        in 5.0..<10.0 -> 1
        in 10.0..<15.0 -> 2
        in 15.0..<20.0 -> 3
        else -> 4
    }

    fun grades(profile: List<ProfilePoint>, windowMeters: Double = 100.0): List<Grade> {
        if (profile.size < 2) return emptyList()
        val out = mutableListOf<Grade>()
        var i = 0
        while (i < profile.lastIndex) {
            var j = i + 1
            while (j < profile.lastIndex && profile[j].distanceMeters - profile[i].distanceMeters < windowMeters) j++
            val d = profile[j].distanceMeters - profile[i].distanceMeters
            if (d > 0) out += Grade(profile[i].distanceMeters, profile[j].distanceMeters, (profile[j].ele - profile[i].ele) / d * 100)
            i = j
        }
        // Un último trozo muy corto da pendientes absurdas: se une al anterior
        if (out.size >= 2 && out.last().endMeters - out.last().startMeters < windowMeters / 3) {
            val a = out[out.size - 2]
            val b = out.removeAt(out.lastIndex)
            val eleA = a.percent / 100 * (a.endMeters - a.startMeters)
            val eleB = b.percent / 100 * (b.endMeters - b.startMeters)
            out[out.lastIndex] = Grade(a.startMeters, b.endMeters, (eleA + eleB) / (b.endMeters - a.startMeters) * 100)
        }
        return out
    }

    fun summary(grades: List<Grade>): SlopeSummary? {
        if (grades.isEmpty()) return null
        val up = grades.maxBy { it.percent }
        fun over(t: Double) = grades.filter { it.percent >= t }.sumOf { it.endMeters - it.startMeters }
        return SlopeSummary(
            maxUpPercent = up.percent.coerceAtLeast(0.0),
            maxUpAtMeters = up.startMeters,
            maxDownPercent = grades.minOf { it.percent }.coerceAtMost(0.0),
            upOver10Meters = over(10.0),
            upOver15Meters = over(15.0),
        )
    }

    /** Pendiente en [meters] del inicio (la del tramo que lo contiene). */
    fun at(grades: List<Grade>, meters: Double): Double? {
        if (grades.isEmpty()) return null
        var lo = 0
        var hi = grades.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (grades[mid].startMeters <= meters) lo = mid else hi = mid - 1
        }
        return grades[lo].percent
    }

    /**
     * La ruta partida en trozos del mismo color de pendiente, como GeoJSON con
     * la propiedad "b" (0–4) para pintarla en el mapa.
     */
    fun lines(segments: List<List<GeoPoint>>, grades: List<Grade>): String {
        val features = mutableListOf<String>()
        var dist = 0.0
        for (seg in segments) {
            if (seg.size < 2) continue
            var run = mutableListOf(seg[0])
            var runBucket = -1
            for (i in 1 until seg.size) {
                val step = TrackStatsCalculator.haversineMeters(seg[i - 1], seg[i])
                val b = bucket(at(grades, dist + step / 2) ?: 0.0)
                dist += step
                if (runBucket != -1 && b != runBucket) {
                    features += feature(run, runBucket)
                    run = mutableListOf(seg[i - 1])
                }
                runBucket = b
                run += seg[i]
            }
            if (run.size >= 2) features += feature(run, runBucket.coerceAtLeast(0))
        }
        return features.joinToString(prefix = """{"type":"FeatureCollection","features":[""", postfix = "]}")
    }

    private fun feature(points: List<GeoPoint>, b: Int): String {
        val coords = points.joinToString(",") { String.format(Locale.ROOT, "[%.6f,%.6f]", it.lon, it.lat) }
        return """{"type":"Feature","properties":{"b":$b},"geometry":{"type":"LineString","coordinates":[$coords]}}"""
    }
}
