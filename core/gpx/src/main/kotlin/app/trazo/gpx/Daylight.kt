package app.trazo.gpx

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Salida y puesta de sol (algoritmo del Almanac for Computers, error de un
 * par de minutos), y cuánta luz te queda para lo que falta de ruta.
 */
object Daylight {

    /** Puesta de sol del día [epochDay] (días desde 1970-01-01, UTC) en ms UTC; null si no se pone (o no sale). */
    fun sunsetMs(lat: Double, lon: Double, epochDay: Long): Long? = event(lat, lon, epochDay, rising = false)

    fun sunriseMs(lat: Double, lon: Double, epochDay: Long): Long? = event(lat, lon, epochDay, rising = true)

    private fun event(lat: Double, lon: Double, epochDay: Long, rising: Boolean): Long? {
        val rad = Math.PI / 180
        val n = dayOfYear(epochDay)
        val lngHour = lon / 15
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sin(m * rad) + 0.020 * sin(2 * m * rad) + 282.634
        l = norm(l, 360.0)
        var ra = norm(Math.toDegrees(kotlin.math.atan(0.91764 * tan(l * rad))), 360.0)
        ra += floor(l / 90) * 90 - floor(ra / 90) * 90
        ra /= 15
        val sinDec = 0.39782 * sin(l * rad)
        val cosDec = cos(asin(sinDec))
        // 90,833°: refracción y radio del sol
        val cosH = (cos(90.833 * rad) - sinDec * sin(lat * rad)) / (cosDec * cos(lat * rad))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (rising) 360 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))) / 15
        val localT = h + ra - 0.06571 * t - 6.622
        val utHours = norm(localT - lngHour, 24.0)
        return epochDay * DAY_MS + (utHours * 3_600_000).roundToLong()
    }

    /**
     * Minutos de luz que te sobran (negativo: te faltan) si sales ahora y
     * tardas [remainingMs] en terminar.
     */
    fun marginMinutes(nowMs: Long, sunsetMs: Long, remainingMs: Long): Long = (sunsetMs - nowMs - remainingMs) / 60_000

    private const val DAY_MS = 86_400_000L

    private fun norm(v: Double, m: Double) = ((v % m) + m) % m

    /** Día del año (1–366) de un epochDay, sin java.time para seguir siendo Kotlin puro. */
    private fun dayOfYear(epochDay: Long): Int {
        var y = 1970
        var d = epochDay
        while (true) {
            val len = if (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) 366 else 365
            if (d < len) return d.toInt() + 1
            d -= len
            y++
        }
    }
}
