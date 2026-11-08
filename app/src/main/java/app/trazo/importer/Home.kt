package app.trazo.importer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trazo.gpx.Activity
import app.trazo.gpx.Season
import app.trazo.gpx.ride.Calories
import app.trazo.plan.SavedRoute
import app.trazo.ride.RideStore
import app.trazo.ride.SavedRide
import app.trazo.ui.UserProfile
import app.trazo.ui.recordCta
import app.trazo.ui.seasonIconRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Lo que se puede hacer desde Inicio. */
internal class HomeActions(
    val onRecord: () -> Unit,
    val onExplore: () -> Unit,
    val onWikiloc: () -> Unit,
    val onPickFile: () -> Unit,
    val onCreateRoute: () -> Unit,
    val onLibrary: () -> Unit,
    val onOpenSaved: (SavedRoute) -> Unit,
)

/** Tu semana: salidas desde el lunes. */
private data class Week(val outings: Int, val meters: Double, val gain: Double, val movingMs: Long, val kcal: Int)

/**
 * Portada: el paisaje de la estación con lo que más se hace (grabar y
 * explorar), tu semana, tus rutas a mano y el resto de opciones.
 */
@Composable
internal fun Home(activity: Activity, season: Season, routes: List<SavedRoute>, a: HomeActions) {
    val context = LocalContext.current
    val week by produceState<Week?>(null, activity) {
        value = withContext(Dispatchers.IO) {
            val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val rides: List<SavedRide> = runCatching { RideStore.list(context) }.getOrDefault(emptyList()).filter { it.startedAtMs >= monday }
            Week(
                rides.size,
                rides.sumOf { it.stats.distanceMeters },
                rides.sumOf { it.stats.gainMeters },
                rides.sumOf { it.stats.movingMs },
                rides.sumOf { UserProfile.kcal(it.stats, it.activity) },
            )
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Hero(activity, season, a) }
        item { WeekCard(week, activity) }
        item { Shortcuts(a) }
        if (routes.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tus rutas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = a.onLibrary) { Text("Ver todas") }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(routes.take(10), key = { it.file.absolutePath }) { r -> RouteTile(r) { a.onOpenSaved(r) } }
                }
            }
        }
        item {
            Text(
                "Desde Wikiloc: en la ruta, Descargar → GPX y luego Compartir → Trazo. O ábrelo con «Importar GPX».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** Saludo según la hora. */
private fun greeting(): String = when (LocalTime.now().hour) {
    in 6..13 -> "Buenos días"
    in 14..20 -> "Buenas tardes"
    else -> "Buenas noches"
}

@Composable
private fun Hero(activity: Activity, season: Season, a: HomeActions) {
    val (bg, fg) = remember(season) { seasonIconRes(season) }
    Card(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            // El paisaje del icono de la estación, a lo ancho
            // Los dos con el mismo recorte para que encajen (un poco por debajo del centro: las cumbres)
            val crop = androidx.compose.ui.BiasAlignment(0f, 0.25f)
            Image(painterResource(bg), contentDescription = null, contentScale = ContentScale.Crop, alignment = crop, modifier = Modifier.fillMaxSize())
            Image(painterResource(fg), contentDescription = null, contentScale = ContentScale.Crop, alignment = crop, modifier = Modifier.fillMaxSize())
            // Velo oscuro abajo para que se lea el texto
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color(0x33000000), 1f to Color(0xCC000000)),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(greeting(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(season.tip, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = a.onRecord, modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text(" ${activity.recordCta}", maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = a.onExplore,
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.9f), contentColor = Color(0xFF1A1C16)),
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = null)
                        Text(" Explorar", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCard(week: Week?, activity: Activity) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tu semana", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val w = week
            when {
                w == null -> Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                w.outings == 0 -> Text(
                    if (activity == Activity.BIKE) "Aún no has salido esta semana. Hay rutas esperando." else "Aún no has salido esta semana. Hay senderos esperando.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeekStat(if (activity == Activity.BIKE) "Salidas" else "Excursiones", "${w.outings}", Modifier.weight(1f))
                        WeekStat("Distancia", km(w.meters), Modifier.weight(1f))
                        WeekStat("Desnivel +", metros(w.gain), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val min = (w.movingMs / 60_000.0).roundToInt()
                        WeekStat("En movimiento", "${min / 60} h ${"%02d".format(min % 60)}", Modifier.weight(1f))
                        WeekStat("Kcal", Calories.format(w.kcal).removeSuffix(" kcal"), Modifier.weight(1f))
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun Shortcuts(a: HomeActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Shortcut(Icons.Filled.FileOpen, "Importar GPX", a.onPickFile, Modifier.weight(1f))
        Shortcut(Icons.Filled.Edit, "Crear ruta", a.onCreateRoute, Modifier.weight(1f))
        Shortcut(Icons.Filled.Search, "Wikiloc", a.onWikiloc, Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Shortcut(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteTile(r: SavedRoute, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(200.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(40.dp))
            Text("${km(r.distanceMeters)} · +${metros(r.gainMeters)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Abrir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(start = 2.dp))
            }
        }
    }
}
