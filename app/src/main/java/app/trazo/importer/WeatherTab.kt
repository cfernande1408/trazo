package app.trazo.importer

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
    data class Ready(val points: List<WeatherAt>, val warnings: List<String>) : Forecast
    data class Failed(val message: String) : Forecast
}

/**
 * Pestaña Tiempo: eliges día y hora de salida y sale la previsión en
 * varios puntos de la ruta, a la hora en que pasarás por cada uno.
 */
@Composable
internal fun WeatherTab(state: ImportState.Loaded, activity: Activity) {
    val zone = ZoneId.systemDefault()
    val now = java.time.LocalDateTime.now()
    // Por defecto: dentro de una hora si aún es pronto; si no, mañana a las 9
    var dayOffset by rememberSaveable { mutableIntStateOf(if (now.hour < 15) 0 else 1) }
    var hour by rememberSaveable { mutableIntStateOf(if (now.hour < 15) (now.hour + 1).coerceIn(6, 20) else 9) }
    var sliding by rememberSaveable { mutableStateOf(hour.toFloat()) }

    val startMs = LocalDate.now().plusDays(dayOffset.toLong()).atTime(LocalTime.of(hour, 0))
        .atZone(zone).toInstant().toEpochMilli()

    val forecast by produceState<Forecast>(Forecast.Loading, state.gpx, startMs, activity) {
        value = Forecast.Loading
        delay(250) // por si sigues moviendo la hora
        value = runCatching {
            val samples = withContext(Dispatchers.Default) { RouteWeather.samples(state.gpx.segments, activity) }
            val points = RouteWeather.parse(Http.get(RouteWeather.url(samples)), samples, startMs)
            if (points.isEmpty()) Forecast.Failed("No hay previsión para ese día todavía (solo ${RouteWeather.FORECAST_DAYS} días).")
            else Forecast.Ready(points, RouteWeather.warnings(points))
        }.getOrElse { Forecast.Failed(Http.friendly(it)) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until RouteWeather.FORECAST_DAYS).forEach { d ->
                val date = LocalDate.now().plusDays(d.toLong())
                val label = when (d) {
                    0 -> "Hoy"
                    1 -> "Mañana"
                    else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, ES).removeSuffix(".") + " ${date.dayOfMonth}"
                }
                FilterChip(selected = dayOffset == d, onClick = { dayOffset = d }, label = { Text(label) })
            }
        }
        Column {
            Text("Salida a las %02d:00".format(sliding.roundToInt()), style = MaterialTheme.typography.titleSmall)
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
                Text("Mirando el tiempo a lo largo de la ruta…")
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
                        Text("Sin avisos: buen día para salir.", Modifier.fillMaxWidth().padding(14.dp))
                    }
                }
                f.points.forEach { WeatherRow(it, zone) }
                Text(
                    "Previsión de Open-Meteo a la altitud de cada punto. Las horas de paso son estimadas " +
                        (if (activity == Activity.HIKE) "con el método MIDE." else "para bici de montaña."),
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
                        w.precipProbability?.let { "💧 $it %" },
                        "💨 ${w.windKmh.roundToInt()} km/h" + if (w.gustKmh >= w.windKmh + 10) " (rachas ${w.gustKmh.roundToInt()})" else "",
                    ).joinToString("   "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("${w.tempC.roundToInt()}°", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}
