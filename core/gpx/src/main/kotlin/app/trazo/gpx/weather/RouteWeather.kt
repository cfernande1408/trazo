package app.trazo.gpx.weather

import app.trazo.gpx.i18n.L
import app.trazo.gpx.Activity
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.RouteGeometry
import app.trazo.gpx.TrackStatsCalculator
import app.trazo.gpx.osm.Json
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Un punto de la ruta y cuándo llegarás a él desde la salida. */
data class RouteSample(
    val distanceMeters: Double,
    val lat: Double,
    val lon: Double,
    val ele: Double?,
    val offsetMs: Long,
)

/** El tiempo previsto en un punto de la ruta a la hora en que pasarás por él. */
data class WeatherAt(
    val sample: RouteSample,
    val timeMs: Long,
    val tempC: Double,
    val precipProbability: Int?,
    val precipMm: Double,
    val code: Int,
    val windKmh: Double,
    val gustKmh: Double,
)

/**
 * Previsión a lo largo de la ruta con Open-Meteo (gratis, sin clave): se
 * eligen unos puntos repartidos, se estima a qué hora pasarás por cada uno
 * (según bici o a pie, con el desnivel) y se pide la previsión horaria de
 * todos a la vez, a su altitud real.
 */
object RouteWeather {

    const val FORECAST_DAYS = 7

    /**
     * Puntos repartidos por la ruta: uno cada ~5 km, entre 2 y 8. [paceFactor]
     * es tu ritmo frente al de serie (1,2 si tardas un 20 % más).
     */
    fun samples(segments: List<List<GeoPoint>>, activity: Activity, count: Int? = null, paceFactor: Double = 1.0): List<RouteSample> {
        val total = TrackStatsCalculator.compute(segments).distanceMeters
        if (total <= 0) return emptyList()
        val n = count ?: (total / 5_000).roundToInt().plus(1).coerceIn(2, 8)
        return (0 until n).map { k ->
            val d = total * k / (n - 1)
            val partial = TrackStatsCalculator.compute(upTo(segments, d))
            val p = RouteGeometry.pointAt(segments, d) ?: segments.first().first()
            RouteSample(
                d, p.lat, p.lon, p.ele ?: nearestEle(segments, p),
                if (k == 0) 0 else (activity.estimateMs(partial.distanceMeters, partial.elevationGainMeters, partial.elevationLossMeters) * paceFactor).roundToLong(),
            )
        }
    }

    /** La ruta desde el principio hasta [meters]. */
    private fun upTo(segments: List<List<GeoPoint>>, meters: Double): List<List<GeoPoint>> {
        val out = mutableListOf<List<GeoPoint>>()
        var acc = 0.0
        for (seg in segments) {
            val cur = mutableListOf<GeoPoint>()
            for ((i, p) in seg.withIndex()) {
                if (i > 0) acc += TrackStatsCalculator.haversineMeters(seg[i - 1], p)
                if (acc > meters) break
                cur += p
            }
            if (cur.isNotEmpty()) out += cur
            if (acc > meters) break
        }
        return out
    }

    private fun nearestEle(segments: List<List<GeoPoint>>, p: GeoPoint): Double? =
        segments.flatten().filter { it.ele != null }
            .minByOrNull { abs(it.lat - p.lat) + abs(it.lon - p.lon) }?.ele

    fun url(samples: List<RouteSample>): String {
        fun list(f: (RouteSample) -> Double) = samples.joinToString(",") { String.format(Locale.ROOT, "%.4f", f(it)) }
        val ele = if (samples.all { it.ele != null }) "&elevation=" + samples.joinToString(",") { String.format(Locale.ROOT, "%.0f", it.ele) } else ""
        return "https://api.open-meteo.com/v1/forecast?latitude=${list { it.lat }}&longitude=${list { it.lon }}$ele" +
            "&hourly=temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code," +
            "wind_speed_10m,wind_gusts_10m,cloud_cover,uv_index,is_day" +
            "&timeformat=unixtime&forecast_days=$FORECAST_DAYS"
    }

    /**
     * Para cada punto, la hora de la previsión más cercana a cuando pasarás
     * por él saliendo a [startMs]. Vacío si esa hora queda fuera de la previsión.
     */
    fun parse(json: String, samples: List<RouteSample>, startMs: Long): List<WeatherAt> {
        val root = Json.parse(json)
        val locations = when (root) {
            is List<*> -> root
            is Map<*, *> -> listOf(root)
            else -> emptyList()
        }
        return samples.zip(locations).mapNotNull { (s, loc) ->
            val hourly = (loc as? Map<*, *>)?.get("hourly") as? Map<*, *> ?: return@mapNotNull null
            val times = (hourly["time"] as? List<*>)?.map { (it as Double).toLong() * 1000 } ?: return@mapNotNull null
            val at = startMs + s.offsetMs
            if (times.isEmpty() || at < times.first() - HOUR || at > times.last() + HOUR) return@mapNotNull null
            val i = times.indices.minBy { abs(times[it] - at) }
            fun num(key: String) = ((hourly[key] as? List<*>)?.getOrNull(i) as? Double)
            WeatherAt(
                s, times[i],
                tempC = num("temperature_2m") ?: return@mapNotNull null,
                precipProbability = num("precipitation_probability")?.roundToInt(),
                precipMm = num("precipitation") ?: 0.0,
                code = num("weather_code")?.toInt() ?: 0,
                windKmh = num("wind_speed_10m") ?: 0.0,
                gustKmh = num("wind_gusts_10m") ?: 0.0,
            )
        }
    }

    private const val HOUR = 3_600_000L

    /** Código WMO del tiempo → texto y emoji. */
    fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> L("Despejado", "Clear") to "☀️"
        1, 2 -> L("Poco nuboso", "Partly cloudy") to "🌤️"
        3 -> L("Nublado", "Cloudy") to "☁️"
        45, 48 -> L("Niebla", "Fog") to "🌫️"
        in 51..57 -> L("Llovizna", "Drizzle") to "🌦️"
        in 61..67 -> L("Lluvia", "Rain") to "🌧️"
        in 71..77 -> L("Nieve", "Snow") to "🌨️"
        in 80..82 -> L("Chubascos", "Showers") to "🌦️"
        85, 86 -> L("Chubascos de nieve", "Snow showers") to "🌨️"
        in 95..99 -> L("Tormenta", "Thunderstorm") to "⛈️"
        else -> "—" to "🌡️"
    }

    /** Avisos para la salida: tormenta, hielo, viento o lluvia probable, con el km donde empieza. */
    fun warnings(points: List<WeatherAt>): List<String> {
        fun km(w: WeatherAt) = String.format(Locale.ROOT, "km %.0f", w.sample.distanceMeters / 1000)
        val out = mutableListOf<String>()
        points.firstOrNull { it.code in 95..99 }?.let { out += L("⛈️ Tormenta prevista desde el ${km(it)}. Mejor otro día.", "⛈️ Thunderstorm forecast from ${km(it)}. Better another day.") }
        points.firstOrNull { it.code in 71..77 || it.code in 85..86 }?.let { out += L("🌨️ Nieve desde el ${km(it)}.", "🌨️ Snow from ${km(it)}.") }
        points.firstOrNull { it.tempC <= 0.0 }?.let { out += L("🧊 Bajo cero en el ${km(it)}: puede haber hielo.", "🧊 Below zero at ${km(it)}: there may be ice.") }
        points.firstOrNull { it.gustKmh >= 50 }?.let {
            out += L(
                "💨 Rachas de ${it.gustKmh.roundToInt()} km/h en el ${km(it)}. Cuidado en crestas y collados.",
                "💨 Gusts of ${it.gustKmh.roundToInt()} km/h at ${km(it)}. Take care on ridges and passes.",
            )
        }
        points.firstOrNull { (it.precipProbability ?: 0) >= 60 && it.code !in 95..99 }?.let {
            out += L("🌧️ Lluvia probable (${it.precipProbability} %) desde el ${km(it)}.", "🌧️ Rain likely (${it.precipProbability}%) from ${km(it)}.")
        }
        points.firstOrNull { it.tempC >= 32 }?.let { out += L("🥵 ${it.tempC.roundToInt()} °C en el ${km(it)}: lleva agua de sobra.", "🥵 ${it.tempC.roundToInt()} °C at ${km(it)}: carry plenty of water.") }
        return out
    }
}
