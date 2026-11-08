package app.trazo.importer

import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import app.trazo.ui.openUrl
import androidx.compose.material.icons.Icons
import kotlin.math.roundToInt
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import app.trazo.ui.ScrubbableProfile
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trazo.gpx.Waypoint
import app.trazo.map.TopoPreview
import app.trazo.ui.ElevationChart
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import app.trazo.gpx.Activity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trazo.offline.OfflineMaps
import app.trazo.gpx.Season
import app.trazo.plan.SavedRoute
import app.trazo.ride.formatDuration
import app.trazo.ui.ActivitySwitchDialog
import app.trazo.ui.icon
import app.trazo.ui.outingsTitle
import app.trazo.ui.recordCta
import app.trazo.ui.who
import java.util.Locale

internal val ES: Locale = Locale.forLanguageTag("es-ES")

internal fun km(m: Double) = String.format(ES, "%.1f km", m / 1000)
internal fun metros(m: Double) = String.format(ES, "%,.0f m", m)

/** Wikiloc antepone "Wikiloc - " al nombre de la ruta en el GPX. */
internal fun ImportState.Loaded.title() = gpx.name?.removePrefix("Wikiloc - ") ?: "Ruta sin nombre"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    state: ImportState,
    onPickFile: () -> Unit,
    onOpenMap: () -> Unit,
    onRecord: () -> Unit = {},
    onRides: () -> Unit = {},
    activity: Activity = Activity.BIKE,
    onSwitchActivity: (Activity) -> Unit = {},
    onCreateRoute: () -> Unit = {},
    library: List<SavedRoute> = emptyList(),
    onOpenSaved: (SavedRoute) -> Unit = {},
    onDeleteSaved: (SavedRoute) -> Unit = {},
    onSaveCurrent: () -> Unit = {},
    season: Season = Season.AUTUMN,
    seasonOverride: Season? = null,
    onSeason: (Season?) -> Unit = {},
    onExplore: () -> Unit = {},
    onWikiloc: () -> Unit = {},
    onLibrary: () -> Unit = {},
    /** Cerrar la ruta abierta (vuelve a la portada o a la salida que analizabas). */
    onClose: () -> Unit = {},
    /** De una ruta realizada que analizas a prepararla para repetirla. */
    onPlan: () -> Unit = {},
) {
    BackHandler(enabled = state !is ImportState.Empty, onBack = onClose)
    var switching by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf(false) }
    val lib = Library(library, onOpenSaved, onDeleteSaved)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trazo", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (state !is ImportState.Empty) {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                    }
                },
                actions = {
                    // Tu perfil (peso, altura, edad: para las kcal)
                    IconButton(onClick = { profile = true }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Tu perfil")
                    }
                    // Chip con tu actividad: tocándolo cambias de biker a senderista y viceversa
                    AssistChip(
                        onClick = { switching = true },
                        label = { Text(activity.who) },
                        leadingIcon = { Icon(activity.icon, contentDescription = null, Modifier.size(18.dp)) },
                    )
                },
            )
        },
        floatingActionButton = {
            // En la portada ya está el botón grande de grabar
            // Ni al analizar una ruta realizada: ahí se mira lo hecho
            if (state !is ImportState.Empty && (state as? ImportState.Loaded)?.done == null) {
                ExtendedFloatingActionButton(
                    onClick = onRecord,
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    text = { Text(activity.recordCta) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ImportState.Empty -> Home(
                    activity, season, library,
                    HomeActions(onRecord, onExplore, onWikiloc, onPickFile, onCreateRoute, onLibrary, onOpenSaved, onProfile = { profile = true }),
                )
                ImportState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ImportState.Error -> ErrorState(state.message, onPickFile)
                is ImportState.Loaded -> Loaded(state, activity, season, onPickFile, onOpenMap, onCreateRoute, onSaveCurrent, lib, onPlan)
            }
        }
    }
    if (profile) app.trazo.ui.ProfileSheet(onDismiss = { profile = false })
    if (switching) {
        ActivitySwitchDialog(
            current = activity,
            onDismiss = { switching = false },
            onChoose = { switching = false; onSwitchActivity(it) },
            seasonOverride = seasonOverride,
            onSeason = onSeason,
        )
    }
}

/** "Tus rutas" guardadas en el móvil y qué hacer al tocarlas. */
private class Library(
    val routes: List<SavedRoute>,
    val onOpen: (SavedRoute) -> Unit,
    val onDelete: (SavedRoute) -> Unit,
)

@Composable
private fun ErrorState(message: String, onPickFile: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("No he podido leer la ruta", style = MaterialTheme.typography.titleLarge)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onPickFile) { Text("Probar con otro archivo") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Loaded(
    state: ImportState.Loaded,
    activity: Activity,
    season: Season,
    onPickFile: () -> Unit,
    onOpenMap: () -> Unit,
    onCreateRoute: () -> Unit,
    onSaveCurrent: () -> Unit,
    lib: Library,
    onPlan: () -> Unit,
) {
    val s = state.stats
    val done = state.done
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Al analizar solo hay Perfil y Puntos
    androidx.compose.runtime.LaunchedEffect(done) { if (done != null && tab > 1) tab = 0 }
    var scrub by remember(state.gpx) { mutableStateOf<Double?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                onClick = onOpenMap,
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                TopoPreview(state, Modifier.fillMaxWidth().height(220.dp))
            }
        }
        item {
            Text(
                state.title(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Distancia", km(s.distanceMeters), Modifier.weight(1f))
                Stat("Desnivel +", metros(s.elevationGainMeters), Modifier.weight(1f))
                Stat("Desnivel −", metros(s.elevationLossMeters), Modifier.weight(1f))
            }
        }
        if (done != null) {
            // Lo que hiciste: nada de previsión, luz ni descargas
            item { DoneSummary(s, done, activity) }
        } else {
            item { ForYou(s, activity) }
            item { WeatherPeek(state, activity, onOpen = { tab = 2 }) }
            item { LightLine(state, activity) }
            item { SeasonTip(season) }
            item { OfflineCard(state) }
        }
        item {
            Button(onClick = onOpenMap, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (done != null) "Analizar en el mapa" else "Ver en el mapa", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            PrimaryScrollableTabRow(selectedTabIndex = tab, containerColor = Color.Transparent, edgePadding = 0.dp) {
                val n = state.gpx.waypoints.size
                val tabs = listOf("Perfil", if (n > 0) "Puntos ($n)" else "Puntos", "Tiempo", "Comentarios")
                (if (done != null) tabs.take(2) else tabs).forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, maxLines = 1) })
                }
            }
        }
        when (tab) {
            0 -> {
                item { DifficultyCard(state, activity) }
                item { SurfaceCard(state) }
                if (state.profile.size >= 2) {
                item { SlopeCard(state) }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Altitud", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${metros(s.minElevation ?: 0.0)} – ${metros(s.maxElevation ?: 0.0)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            ScrubbableProfile(state.profile, scrub, onScrub = { scrub = it })
                        }
                    }
                }
            } else {
                item { Hint("El GPX no trae altitudes, así que no hay perfil.") }
            }
            }
            1 -> {
                item { PoisSection(state) }
                if (state.gpx.waypoints.isEmpty()) {
                    item { Hint(if (done != null) "No marcaste puntos en esta ruta." else "El autor no marcó puntos en esta ruta.") }
                } else {
                    items(state.gpx.waypoints) { WaypointRow(it) }
                }
            }
            2 -> item { WeatherTab(state, activity) }
            else -> {
                item { app.trazo.community.ReviewsSection(state.gpx, activity) }
                item { CommentsTab(state) }
            }
        }
        if (done != null) {
            item {
                Button(onClick = onPlan, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Filled.Replay, contentDescription = null)
                    Text("  Repetir esta ruta", style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                Text(
                    "Para planear la salida: tiempo a tu ritmo, previsión, luz, mapa sin conexión y navegación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            val saved = lib.routes.any { it.name == state.title() && kotlin.math.abs(it.distanceMeters - s.distanceMeters) < 1.0 }
            OutlinedButton(onClick = onSaveCurrent, enabled = !saved, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (saved) "Guardada en tus rutas" else "Guardar en tus rutas")
            }
        }
        if (done == null) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onPickFile, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("Importar otra")
                }
                FilledTonalButton(onClick = onCreateRoute, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("Crear ruta")
                }
            }
        }
    }
}

@Composable
private fun CommentsTab(state: ImportState.Loaded) {
    val context = LocalContext.current
    val url = state.gpx.wikilocUrl
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Descripción del autor", style = MaterialTheme.typography.titleMedium)
                Text(
                    state.gpx.description ?: "El autor no incluyó descripción en el GPX.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (url != null) {
            Button(onClick = { context.openUrl(url) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Ver comentarios en Wikiloc", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            FilledTonalButton(
                onClick = { context.openUrl("https://www.google.com/search?q=" + Uri.encode("wikiloc ${state.title()}")) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("Buscar la ruta en Wikiloc")
            }
        }
        Hint("Los comentarios se leen en Wikiloc: Trazo no los copia.")
    }
}

/** Consejo de la estación, con el color de acento de la estación. */
@Composable
private fun SeasonTip(season: Season) {
    Card(colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )) {
        Text(season.tip, Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun WaypointRow(w: Waypoint) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(w.name ?: "Punto sin nombre", style = MaterialTheme.typography.titleSmall)
            w.desc?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/**
 * Tiempo a tu ritmo (tu forma física y tus rutas realizadas) y lo dura que
 * es la ruta para ti, con la estimación de serie debajo para comparar.
 */
@Composable
private fun ForYou(s: app.trazo.gpx.TrackStats, activity: Activity) {
    val pace = app.trazo.ui.UserProfile.pace(activity)
    val mine = pace.estimateMs(activity, s.distanceMeters, s.elevationGainMeters, s.elevationLossMeters)
    val base = activity.estimateMs(s.distanceMeters, s.elevationGainMeters, s.elevationLossMeters)
    val effort = pace.effort(mine)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("A tu ritmo: ${formatDuration(mine)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            EffortBadge(effort)
        }
        Text(
            (if (activity == Activity.HIKE) "MIDE: " else "De serie en bici: ") + formatDuration(base) + " · " +
                if (pace.rides > 0) "ajustado con tus ${pace.rides} rutas realizadas" else "ajustado con tu forma física (tu perfil)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** «Fácil para ti», «A tu medida»… con el color de lo duro que es. */
@Composable
internal fun EffortBadge(effort: app.trazo.gpx.ride.Effort) {
    val (bg, fg) = when (effort) {
        app.trazo.gpx.ride.Effort.EASY -> Color(0xFFD3F9D8) to Color(0xFF1B5E20)
        app.trazo.gpx.ride.Effort.MODERATE -> Color(0xFFDBE4FF) to Color(0xFF1A3A8A)
        app.trazo.gpx.ride.Effort.HARD -> Color(0xFFFFE8CC) to Color(0xFF8A3B00)
        app.trazo.gpx.ride.Effort.VERY_HARD -> Color(0xFFFFE3E3) to Color(0xFF8B1A1A)
    }
    androidx.compose.material3.Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.small) {
        Text(effort.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

private val DONE_DATE = java.text.SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", java.util.Locale.forLanguageTag("es-ES"))

/**
 * Cómo te fue en una ruta realizada: cuándo, tiempo en marcha y total, media,
 * y lo que tardaste frente a la estimación de serie (MIDE a pie).
 */
@Composable
private fun DoneSummary(s: app.trazo.gpx.TrackStats, done: DoneRide, activity: Activity) {
    val r = done.stats
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Realizada el " + DONE_DATE.format(java.util.Date(done.startedAtMs)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("En marcha", formatDuration(r.movingMs), Modifier.weight(1f))
            Stat("Total", formatDuration(r.totalMs), Modifier.weight(1f))
            Stat("Media", String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f km/h", r.avgMovingKmh), Modifier.weight(1f))
        }
        val base = activity.estimateMs(s.distanceMeters, s.elevationGainMeters, s.elevationLossMeters)
        if (base > 0 && r.movingMs > 0) {
            val pct = ((r.movingMs.toDouble() / base - 1) * 100).roundToInt()
            val ref = if (activity == Activity.HIKE) "MIDE" else "La estimación de serie"
            Text(
                "$ref la daba en ${formatDuration(base)}: " + when {
                    kotlin.math.abs(pct) < 5 -> "fuiste justo a ese ritmo."
                    pct < 0 -> "fuiste un ${-pct} % más rápido."
                    else -> "tardaste un $pct % más."
                } + " Las paradas no cuentan.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
