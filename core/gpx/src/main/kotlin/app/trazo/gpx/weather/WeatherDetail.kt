package app.trazo.gpx.weather

import app.trazo.gpx.i18n.L
import app.trazo.gpx.Activity
import app.trazo.gpx.osm.Json
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** El tiempo en un momento de la salida, donde estarás entonces. */
data class DetailRow(
    val timeMs: Long,
    val distanceMeters: Double,
    val tempC: Double,
    /** Sensación térmica: con el viento y la humedad. */
    val feelsC: Double,
    val windKmh: Double,
    val gustKmh: Double,
    /** Lluvia en esa hora, en mm. */
    val precipMm: Double,
    val precipProbability: Int?,
    val cloudPct: Int?,
    val uv: Double?,
    val isDay: Boolean,
    val code: Int,
)

/**
 * Previsión al detalle a lo largo de la ruta: una fila cada [STEP_MIN]
 * minutos con dónde estarás y el tiempo que hará ahí, y qué ponerse.
 *
 * Open-Meteo da la previsión hora a hora en cada punto de [RouteWeather.samples]:
 * entre horas se interpola en el tiempo, y entre dos puntos de la ruta se
 * mezclan según lo cerca que estés de cada uno.
 */
object WeatherDetail {

    const val STEP_MIN = 15

    private class Series(val times: LongArray, val values: Map<String, List<Double?>>) {
        /** Valor a [at] interpolado entre las dos horas que lo rodean; null si queda fuera. */
        fun at(key: String, at: Long): Double? {
            val v = values[key] ?: return null
            if (times.isEmpty() || at < times.first() || at > times.last()) return null
            var i = times.indexOfLast { it <= at }.coerceAtLeast(0)
            if (i == times.lastIndex) i-- // el último punto: con la hora anterior
            if (i < 0) return v.getOrNull(0)
            val a = v.getOrNull(i) ?: return v.getOrNull(i + 1)
            val b = v.getOrNull(i + 1) ?: return a
            val f = (at - times[i]).toDouble() / (times[i + 1] - times[i])
            return a + (b - a) * f
        }

        /** Valor de la hora más cercana (para códigos y día/noche, que no se interpolan). */
        fun nearest(key: String, at: Long): Double? {
            val v = values[key] ?: return null
            if (times.isEmpty()) return null
            return v.getOrNull(times.indices.minBy { abs(times[it] - at) })
        }

        fun covers(at: Long) = times.isNotEmpty() && at >= times.first() && at <= times.last()
    }

    private fun series(json: String): List<Series> {
        val locations = when (val root = Json.parse(json)) {
            is List<*> -> root
            is Map<*, *> -> listOf(root)
            else -> emptyList()
        }
        return locations.map { loc ->
            val hourly = (loc as? Map<*, *>)?.get("hourly") as? Map<*, *>
            val times = (hourly?.get("time") as? List<*>)?.map { ((it as Double).toLong()) * 1000 }?.toLongArray() ?: LongArray(0)
            val values = hourly.orEmpty().entries
                .filter { it.key != "time" }
                .associate { (k, v) -> k as String to (v as? List<*>).orEmpty().map { it as? Double } }
            Series(times, values)
        }
    }

    /**
     * Una fila cada [stepMinutes] desde la salida ([startMs]) hasta el final de
     * la ruta. Se salta lo que quede fuera de la previsión.
     */
    fun rows(json: String, samples: List<RouteSample>, startMs: Long, stepMinutes: Int = STEP_MIN): List<DetailRow> {
        val locs = series(json)
        if (samples.isEmpty() || locs.size < samples.size) return emptyList()
        val total = samples.last().offsetMs
        val step = stepMinutes * 60_000L
        val offsets = (0..total step step).toMutableList()
        if (offsets.last() != total) offsets += total
        return offsets.mapNotNull { t ->
            // Entre qué dos puntos de la ruta estarás, y cuánto de cerca de cada uno
            val k = samples.indexOfLast { it.offsetMs <= t }.coerceIn(0, samples.lastIndex)
            val k2 = (k + 1).coerceAtMost(samples.lastIndex)
            val span = (samples[k2].offsetMs - samples[k].offsetMs).toDouble()
            val f = if (span <= 0) 0.0 else ((t - samples[k].offsetMs) / span).coerceIn(0.0, 1.0)
            val at = startMs + t
            val a = locs[k]
            val b = locs[k2]
            if (!a.covers(at) || !b.covers(at)) return@mapNotNull null
            fun mix(key: String): Double? {
                val va = a.at(key, at)
                val vb = b.at(key, at)
                return when {
                    va == null -> vb
                    vb == null -> va
                    else -> va + (vb - va) * f
                }
            }
            val near = if (f < 0.5) a else b
            val temp = mix("temperature_2m") ?: return@mapNotNull null
            DetailRow(
                timeMs = at,
                distanceMeters = samples[k].distanceMeters + (samples[k2].distanceMeters - samples[k].distanceMeters) * f,
                tempC = temp,
                feelsC = mix("apparent_temperature") ?: temp,
                windKmh = mix("wind_speed_10m") ?: 0.0,
                gustKmh = mix("wind_gusts_10m") ?: 0.0,
                precipMm = mix("precipitation") ?: 0.0,
                precipProbability = mix("precipitation_probability")?.roundToInt(),
                cloudPct = mix("cloud_cover")?.roundToInt(),
                uv = mix("uv_index"),
                isDay = (near.nearest("is_day", at) ?: 1.0) >= 0.5,
                code = near.nearest("weather_code", at)?.toInt() ?: 0,
            )
        }
    }

    /**
     * Qué llevarte puesto, por capas: según la sensación térmica más fría y
     * más cálida de la salida, la lluvia, el viento, el sol y si se hace de noche.
     * En bici se cuenta con unos grados menos por el aire de la marcha.
     */
    fun whatToWear(rows: List<DetailRow>, activity: Activity): List<String> {
        if (rows.isEmpty()) return emptyList()
        val bike = activity == Activity.BIKE
        // El aire de la marcha en bici (sobre todo bajando) enfría como unos 4 °C más
        val chill = if (bike) 4.0 else 0.0
        val coldest = rows.minOf { it.feelsC } - chill
        val warmest = rows.maxOf { it.feelsC } - chill / 2
        val out = mutableListOf<String>()
        fun km(r: DetailRow) = String.format(Locale.ROOT, "km %.0f", r.distanceMeters / 1000)

        out += when {
            coldest >= 20 -> L("Arriba: manga corta transpirable.", "Top: breathable short sleeves.")
            coldest >= 14 -> if (bike) L("Arriba: manga corta, con chaleco o manguitos para las bajadas.", "Top: short sleeves, with a gilet or arm warmers for the descents.")
            else L("Arriba: manga corta y una capa fina en la mochila.", "Top: short sleeves and a light layer in the pack.")
            coldest >= 8 -> L("Arriba: manga larga fina y cortavientos.", "Top: light long sleeves and a windproof.")
            coldest >= 2 -> L("Arriba: camiseta térmica de manga larga y chaqueta cortavientos o softshell.", "Top: long-sleeve thermal base layer and a windproof or softshell jacket.")
            else -> L("Arriba: tres capas: térmica, forro polar y chaqueta cortavientos o impermeable.", "Top: three layers: thermal, fleece and a windproof or waterproof jacket.")
        }
        out += when {
            coldest >= 14 -> if (bike) L("Abajo: culotte corto.", "Bottom: bib shorts.") else L("Abajo: pantalón corto o fino.", "Bottom: shorts or light trousers.")
            coldest >= 6 -> if (bike) L("Abajo: culotte corto con perneras o rodilleras.", "Bottom: bib shorts with leg or knee warmers.") else L("Abajo: pantalón largo fino.", "Bottom: light long trousers.")
            else -> if (bike) L("Abajo: culotte largo térmico.", "Bottom: thermal bib tights.") else L("Abajo: pantalón largo de abrigo.", "Bottom: warm long trousers.")
        }
        when {
            coldest < 2 -> out += if (bike) L("Guantes de invierno, gorro bajo el casco, braga de cuello y cubrezapatillas.", "Winter gloves, a cap under the helmet, a neck gaiter and overshoes.")
            else L("Guantes, gorro y braga de cuello.", "Gloves, a hat and a neck gaiter.")
            coldest < 10 -> out += if (bike) L("Guantes largos y braga de cuello.", "Full-finger gloves and a neck gaiter.") else L("Guantes finos.", "Light gloves.")
        }
        if (warmest - coldest >= 8) {
            out += L(
                "Por capas: la sensación va de ${coldest.roundToInt()} a ${warmest.roundToInt()} °C a lo largo de la salida.",
                "Layer up: it will feel like ${coldest.roundToInt()} to ${warmest.roundToInt()} °C over the outing.",
            )
        }
        val wet = rows.firstOrNull { it.precipMm >= 0.3 || (it.precipProbability ?: 0) >= 50 }
        if (wet != null) {
            val heavy = rows.any { it.precipMm >= 2 || (it.precipProbability ?: 0) >= 80 }
            out += if (heavy) {
                L("Chubasquero puesto desde el ${km(wet)}" + (if (bike) " y guardabarros." else " y funda para la mochila."),
                    "Rain jacket on from ${km(wet)}" + (if (bike) " and mudguards." else " and a pack cover."))
            } else {
                L("Chubasquero en la mochila: puede llover desde el ${km(wet)}.", "Rain jacket in the pack: it may rain from ${km(wet)}.")
            }
        }
        rows.firstOrNull { it.gustKmh >= 40 }?.let { if (coldest >= 14) out += L("Cortavientos: rachas de ${it.gustKmh.roundToInt()} km/h en el ${km(it)}.", "Windproof: gusts of ${it.gustKmh.roundToInt()} km/h at ${km(it)}.") }
        val uv = rows.mapNotNull { it.uv }.maxOrNull() ?: 0.0
        when {
            uv >= 6 -> out += L("Sol fuerte (UV ${uv.roundToInt()}): crema, gafas de sol y gorra o visera.", "Strong sun (UV ${uv.roundToInt()}): sunscreen, sunglasses and a cap or visor.")
            uv >= 3 -> out += L("Gafas de sol y crema.", "Sunglasses and sunscreen.")
        }
        if (rows.maxOf { it.feelsC } >= 28) out += L("Calor: agua de sobra (medio litro por hora) y sales.", "Heat: plenty of water (half a litre an hour) and electrolytes.")
        rows.firstOrNull { !it.isDay }?.let {
            out += L("Anochece por el camino (${km(it)}): " + if (bike) "luces delante y detrás." else "frontal.",
                "It gets dark on the way (${km(it)}): " + if (bike) "front and rear lights." else "a head torch.")
        }
        return out
    }
}
