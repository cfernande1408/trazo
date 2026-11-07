package app.trazo.gpx.safety

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Coordenada UTM (WGS84, que a efectos de rescate es igual que ETRS89). */
data class Utm(val zone: Int, val band: Char, val easting: Double, val northing: Double) {
    /** "30T 440290 4474257", como lo piden los equipos de rescate. */
    override fun toString() = "$zone$band ${easting.roundToInt()} ${northing.roundToInt()}"
}

/** Coordenadas para dictar al 112 o mandar por SMS. */
object Coordinates {

    private const val A = 6_378_137.0
    private const val F = 1 / 298.257223563
    private const val K0 = 0.9996

    fun toUtm(lat: Double, lon: Double): Utm {
        require(lat in -80.0..84.0) { "UTM no cubre los polos" }
        var zone = floor((lon + 180) / 6).toInt() + 1
        // Excepciones de Noruega y Svalbard
        if (lat in 56.0..<64.0 && lon in 3.0..<12.0) zone = 32
        if (lat >= 72) zone = when {
            lon in 0.0..<9.0 -> 31; lon in 9.0..<21.0 -> 33; lon in 21.0..<33.0 -> 35; lon in 33.0..<42.0 -> 37; else -> zone
        }
        val lon0 = Math.toRadians((zone - 1) * 6 - 180 + 3.0)
        val e2 = F * (2 - F)
        val ep2 = e2 / (1 - e2)
        val phi = Math.toRadians(lat)
        val n = A / sqrt(1 - e2 * sin(phi) * sin(phi))
        val t = tan(phi) * tan(phi)
        val c = ep2 * cos(phi) * cos(phi)
        val a = cos(phi) * (Math.toRadians(lon) - lon0)
        val m = A * ((1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256) * phi -
            (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2 * e2 * e2 / 1024) * sin(2 * phi) +
            (15 * e2 * e2 / 256 + 45 * e2 * e2 * e2 / 1024) * sin(4 * phi) -
            (35 * e2 * e2 * e2 / 3072) * sin(6 * phi))
        val easting = K0 * n * (a + (1 - t + c) * a * a * a / 6 + (5 - 18 * t + t * t + 72 * c - 58 * ep2) * a * a * a * a * a / 120) + 500_000
        var northing = K0 * (m + n * tan(phi) * (a * a / 2 + (5 - t + 9 * c + 4 * c * c) * a * a * a * a / 24 +
            (61 - 58 * t + t * t + 600 * c - 330 * ep2) * a * a * a * a * a * a / 720))
        if (lat < 0) northing += 10_000_000
        return Utm(zone, band(lat), easting, northing)
    }

    private fun band(lat: Double): Char {
        val bands = "CDEFGHJKLMNPQRSTUVWX"
        return bands[((lat + 80) / 8).toInt().coerceIn(0, bands.lastIndex)]
    }

    /** "40,41680, -3,70380" no: con punto, que es como lo entienden los mapas. */
    fun decimal(lat: Double, lon: Double) = String.format(Locale.ROOT, "%.5f, %.5f", lat, lon)

    /** 40°25'00.5"N 3°42'13.7"W */
    fun dms(lat: Double, lon: Double): String {
        fun part(v: Double, pos: Char, neg: Char): String {
            val a = abs(v)
            val d = floor(a).toInt()
            val mFull = (a - d) * 60
            val m = floor(mFull).toInt()
            val s = (mFull - m) * 60
            return String.format(Locale.ROOT, "%d°%02d'%04.1f\"%c", d, m, s, if (v >= 0) pos else neg)
        }
        return part(lat, 'N', 'S') + " " + part(lon, 'E', 'W')
    }

    /** Texto del SMS de ayuda: corto, que quepa en uno o dos mensajes y se entienda sin la app. */
    fun sosMessage(lat: Double, lon: Double, eleMeters: Double?, accuracyMeters: Double?, hhmm: String, reason: String? = null): String =
        buildString {
            append(reason ?: "Necesito ayuda.")
            append(" Mi posición a las ").append(hhmm).append(": ")
            append(decimal(lat, lon))
            append(" (UTM ").append(toUtm(lat, lon)).append(')')
            eleMeters?.let { append(", ").append(it.roundToInt()).append(" m de altitud") }
            accuracyMeters?.let { append(", ±").append(it.roundToInt()).append(" m") }
            append(". ")
            append(String.format(Locale.ROOT, "https://www.openstreetmap.org/?mlat=%.5f&mlon=%.5f#map=16/%.5f/%.5f", lat, lon, lat, lon))
            append(" (Trazo)")
        }
}
