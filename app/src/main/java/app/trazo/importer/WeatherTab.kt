package app.trazo.importer

import app.trazo.gpx.i18n.L
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.trazo.gpx.weather.WeatherDetail
import app.trazo.gpx.weather.DetailRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trazo.gpx.Activity
import app.trazo.gpx.weather.RouteWeather
import app.trazo.gpx.weather.WeatherAt
import app.trazo.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

private sealed interface Forecast {
    data object Loading : Forecast
    data class Ready(
        val points: List<WeatherAt>,
        val warnings: List<String>,
        /** Cada 15 min, donde estarás. */
        val rows: List<DetailRow> = emptyList(),
        /** Qué llevarte puesto. */
        val wear: List<String> = emptyList(),
    ) : Forecast
    data class Failed(val message: String) : Forecast
}

/** Salida por defecto: dentro de una hora si aún es pronto; si no, mañana a las 9. Devuelve (día, hora). */
private fun defaultStart(): Pair<Int, Int> {
    val now = java.time.LocalDateTime.now()
    return if (now.hour < 15) 0 to (now.hour + 1).coerceIn(6, 20) else 1 to 9
}

private fun startMs(dayOffset: Int, hour: Int): Long =
    LocalDate.now().plusDays(dayOffset.toLong()).atTime(LocalTime.of(hour, 0))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private suspend fun loadForecast(state: ImportState.Loaded, activity: Activity, startMs: Long): Forecast = runCatching {
    // Las horas de paso, a tu ritmo
    val factor = app.trazo.ui.UserProfile.pace(activity).factor
    val samples = withContext(Dispatchers.Default) { RouteWeather.samples(state.gpx.segments, activity, paceFactor = factor) }
    val json = Http.get(RouteWeather.url(samples))
    val points = RouteWeather.parse(json, samples, startMs)
    if (points.isEmpty()) Forecast.Failed(L("No hay previsión para ese día todavía (solo ${RouteWeather.FORECAST_DAYS} días).", "No forecast for that day yet (only ${RouteWeather.FORECAST_DAYS} days)."))
    else {
        val rows = withContext(Dispatchers.Default) { WeatherDetail.rows(json, samples, startMs) }
        Forecast.Ready(points, RouteWeather.warnings(points), rows, WeatherDetail.whatToWear(rows, activity))
    }
}.getOrElse { Forecast.Failed(Http.friendly(it)) }

/**
 * Resumen del tiempo en una línea, arriba del todo en el resumen de la
 * ruta, para la salida por defecto. Al tocarlo se abre la pestaña Tiempo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeatherPeek(state: ImportState.Loaded, activity: Activity, onOpen: () -> Unit) {
    val (day, hour) = remember { defaultStart() }
    val forecast by produceState<Forecast>(Forecast.Loading, state.gpx, activity) {
        value = loadForecast(state, activity, startMs(day, hour))
    }
    val f = forecast
    val warn = (f as? Forecast.Ready)?.warnings.orEmpty()
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(
            containerColor = if (warn.isNotEmpty()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val whenLabel = (if (day == 0) L("Hoy", "Today") else L("Mañana", "Tomorrow")) + L(" a las %02d:00", " at %02d:00").format(hour)
            when (f) {
                Forecast.Loading -> {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(L("  Mirando el tiempo en la ruta…", "  Checking the weather on the route…"), style = MaterialTheme.typography.bodyMedium)
                }
                is Forecast.Failed -> Text(L("🌡️  Tiempo en la ruta: toca para elegir día", "🌡️  Weather on the route: tap to pick a day"), style = MaterialTheme.typography.bodyMedium)
                is Forecast.Ready -> {
                    val worst = f.points.maxBy { severity(it.code) }
                    Text(RouteWeather.describe(worst.code).second, style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            "$whenLabel · " + tempRange(f.points.minOf { it.tempC }.roundToInt(), f.points.maxOf { it.tempC }.roundToInt()),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            if (warn.isEmpty()) L("Sin avisos en la ruta", "No warnings on the route") else warn.first() + if (warn.size > 1) " (+${warn.size - 1})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                        )
                    }
                    Text(L("Ver ›", "View ›"), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** "13 °C" si no cambia a lo largo de la ruta, "6–11 °C" si sí. */
private fun tempRange(min: Int, max: Int) = if (min == max) "$min °C" else "$min–$max °C"

/** Para elegir el icono del resumen: el peor tiempo de la ruta. */
private fun severity(code: Int): Int = when (code) {
    in 95..99 -> 6
    in 71..77, 85, 86 -> 5
    in 61..67, in 80..82 -> 4
    in 51..57 -> 3
    45, 48 -> 2
    3 -> 1
    else -> 0
}

/**
 * Pestaña Tiempo: eliges día y hora de salida y sale la previsión en
 * varios puntos de la ruta, a la hora en que pasarás por cada uno.
 */
@Composable
internal fun WeatherTab(state: ImportState.Loaded, activity: Activity) {
    val zone = ZoneId.systemDefault()
    val (defDay, defHour) = remember { defaultStart() }
    var dayOffset by rememberSaveable { mutableIntStateOf(defDay) }
    var hour by rememberSaveable { mutableIntStateOf(defHour) }
    var sliding by rememberSaveable { mutableStateOf(hour.toFloat()) }
    /** Resumen (unos puntos de la ruta) o detalle (cada 15 min y qué ponerse). */
    var detail by rememberSaveable { mutableStateOf(false) }

    val start = startMs(dayOffset, hour)
    val forecast by produceState<Forecast>(Forecast.Loading, state.gpx, start, activity) {
        value = Forecast.Loading
        delay(250) // por si sigues moviendo la hora
        value = loadForecast(state, activity, start)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until RouteWeather.FORECAST_DAYS).forEach { d ->
                val date = LocalDate.now().plusDays(d.toLong())
                val label = when (d) {
                    0 -> L("Hoy", "Today")
                    1 -> L("Mañana", "Tomorrow")
                    else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, ES).removeSuffix(".") + " ${date.dayOfMonth}"
                }
                FilterChip(selected = dayOffset == d, onClick = { dayOffset = d }, label = { Text(label) })
            }
        }
        Column {
            Text(L("Salida a las %02d:00", "Start at %02d:00").format(sliding.roundToInt()), style = MaterialTheme.typography.titleSmall)
            Slider(
                value = sliding,
                onValueChange = { sliding = it },
                onValueChangeFinished = { hour = sliding.roundToInt() },
                valueRange = 5f..21f,
                steps = 15,
            )
        }

        when (val f = forecast) {
            Forecast.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(L("Mirando el tiempo a lo largo de la ruta…", "Checking the weather along the route…"))
            }
            is Forecast.Failed -> Text(f.message, color = MaterialTheme.colorScheme.error)
            is Forecast.Ready -> {
                if (f.warnings.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            f.warnings.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                } else {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Text(L("Sin avisos: buen día para salir.", "No warnings: a good day to go."), Modifier.fillMaxWidth().padding(14.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !detail, onClick = { detail = false }, label = { Text(L("Resumen", "Summary")) })
                    FilterChip(selected = detail, onClick = { detail = true }, label = { Text(L("Detalle cada 15 min", "Every 15 min")) })
                }
                if (!detail) f.points.forEach { WeatherRow(it, zone) }
                else WeatherDetailView(f.rows, f.wear, zone)
                Text(
                    L("Previsión de Open-Meteo a la altitud de cada punto. Las horas de paso son a tu ritmo ", "Open-Meteo forecast at the altitude of each point. Passing times are at your pace ") +
                        L("(tu forma física y tus rutas realizadas).", "(your fitness and your completed routes)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val HOUR_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun WeatherRow(w: WeatherAt, zone: ZoneId) {
    val (text, emoji) = RouteWeather.describe(w.code)
    val passAt = Instant.ofEpochMilli(w.timeMs).atZone(zone).toLocalTime()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.width(48.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "km %.1f · %s%s".format(ES, w.sample.distanceMeters / 1000, HOUR_FMT.format(passAt),
                        w.sample.ele?.let { " · " + metros(it) } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(
                        w.precipProbability?.let { "💧 " + app.trazo.gpx.i18n.percent(it) },
                        "💨 ${w.windKmh.roundToInt()} km/h" + if (w.gustKmh >= w.windKmh + 10) L(" (rachas ${w.gustKmh.roundToInt()})", " (gusts ${w.gustKmh.roundToInt()})") else "",
                    ).joinToString("   "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("${w.tempC.roundToInt()}°", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * El detalle: qué llevarte puesto y una fila cada 15 minutos con dónde estarás
 * y el tiempo que hará ahí (sensación térmica, viento, lluvia).
 */
@Composable
private fun WeatherDetailView(rows: List<DetailRow>, wear: List<String>, zone: ZoneId) {
    if (wear.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(L("Qué llevarte puesto", "What to wear"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                wear.forEach { line ->
                    Row {
                        Text("•", modifier = Modifier.width(14.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (rows.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            DetailLine(listOf(L("Hora", "Time"), "", L("Temp. (sens.)", "Temp. (feels)"), L("Viento", "Wind"), L("Lluvia", "Rain")), header = true)
            rows.forEach { r ->
                val (text, emoji) = RouteWeather.describe(r.code)
                // De noche, la luna en vez del sol
                val icon = if (!r.isDay && r.code <= 2) "🌙" else emoji
                DetailLine(
                    listOf(
                        HOUR_FMT.format(Instant.ofEpochMilli(r.timeMs).atZone(zone)) + "\nkm %.0f".format(ES, r.distanceMeters / 1000),
                        icon,
                        "${r.tempC.roundToInt()}° (${r.feelsC.roundToInt()}°)",
                        "${r.windKmh.roundToInt()}" + if (r.gustKmh >= r.windKmh + 10) "/${r.gustKmh.roundToInt()}" else "",
                        if (r.precipMm >= 0.1) "%.1f mm".format(ES, r.precipMm) else app.trazo.gpx.i18n.percent(r.precipProbability ?: 0),
                    ),
                    description = L("$text, ${r.tempC.roundToInt()} grados, sensación ${r.feelsC.roundToInt()}", "$text, ${r.tempC.roundToInt()} degrees, feels like ${r.feelsC.roundToInt()}"),
                )
            }
            Text(
                L("Viento en km/h (con rachas si son fuertes). Lluvia: mm en esa hora o, si no llueve, la probabilidad. ", "Wind in km/h (with gusts when strong). Rain: mm in that hour or, if it doesn't rain, the probability. ") +
                    L("Entre horas y entre puntos de la ruta, los valores se interpolan.", "Between hours and between route points, values are interpolated."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DetailLine(cells: List<String>, header: Boolean = false, description: String? = null) {
    val weights = listOf(1.1f, 0.6f, 1.3f, 1f, 1f)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { i, c ->
            Text(
                c,
                modifier = Modifier.weight(weights[i]),
                style = when {
                    header -> MaterialTheme.typography.labelSmall
                    i == 1 -> MaterialTheme.typography.titleMedium
                    i == 0 -> MaterialTheme.typography.labelMedium
                    else -> MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (i == 2 && !header) FontWeight.Bold else null,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
