package app.trazo.importer

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
import androidx.compose.material.icons.filled.PlayArrow
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
) {
    var switching by remember { mutableStateOf(false) }
    val lib = Library(library, onOpenSaved, onDeleteSaved)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis rutas") },
                actions = {
                    // Chip con tu actividad: tocándolo cambias de biker a senderista y viceversa
                    AssistChip(
                        onClick = { switching = true },
                        label = { Text(activity.who) },
                        leadingIcon = { Icon(activity.icon, contentDescription = null, Modifier.size(18.dp)) },
                    )
                    TextButton(onClick = onRides) { Text(activity.outingsTitle) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRecord,
                icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                text = { Text(activity.recordCta) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ImportState.Empty -> EmptyState(onPickFile, onCreateRoute, lib)
                ImportState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ImportState.Error -> ErrorState(state.message, onPickFile)
                is ImportState.Loaded -> Loaded(state, activity, season, onPickFile, onOpenMap, onCreateRoute, onSaveCurrent, lib)
            }
        }
    }
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

private fun LazyListScope.library(lib: Library) {
    if (lib.routes.isEmpty()) return
    item {
        Text(
            "Tus rutas guardadas",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    items(lib.routes, key = { it.file.absolutePath }) { r ->
        Card(
            onClick = { lib.onOpen(r) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${km(r.distanceMeters)} · +${metros(r.gainMeters)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { lib.onDelete(r) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Borrar ruta")
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onPickFile: () -> Unit, onCreateRoute: () -> Unit, lib: Library) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ImportCard(onPickFile) }
        item { CreateCard(onCreateRoute) }
        library(lib)
        item {
            val maps by OfflineMaps.maps.collectAsStateWithLifecycle()
            if (maps.isNotEmpty()) OfflineMapsList(maps)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCard(onCreateRoute: () -> Unit) {
    Card(
        onClick = onCreateRoute,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = null, Modifier.size(28.dp))
            Column {
                Text("Crear ruta", style = MaterialTheme.typography.titleLarge)
                Text("Toca puntos en el mapa y la ruta se ajusta a pistas y senderos")
            }
        }
    }
}

@Composable
private fun ImportCard(onPickFile: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Importar desde Wikiloc", style = MaterialTheme.typography.titleLarge)
            Text("1 · En la ruta, pulsa Descargar → GPX")
            Text("2 · Compartir → Trazo")
            Text("3 · O ábrelo desde aquí")
            Button(onClick = onPickFile) { Text("Abrir archivo GPX") }
        }
    }
}

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
) {
    val s = state.stats
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
        item {
            val est = activity.estimateMs(s.distanceMeters, s.elevationGainMeters, s.elevationLossMeters)
            Text(
                (if (activity == Activity.HIKE) "Tiempo estimado (MIDE): " else "Tiempo estimado en bici: ") + formatDuration(est),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { WeatherPeek(state, activity, onOpen = { tab = 2 }) }
        item { LightLine(state, activity) }
        item { SeasonTip(season) }
        item { OfflineCard(state) }
        item {
            Button(onClick = onOpenMap, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Ver en el mapa", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            PrimaryScrollableTabRow(selectedTabIndex = tab, containerColor = Color.Transparent, edgePadding = 0.dp) {
                val n = state.gpx.waypoints.size
                listOf("Perfil", if (n > 0) "Puntos ($n)" else "Puntos", "Tiempo", "Comentarios").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, maxLines = 1) })
                }
            }
        }
        when (tab) {
            0 -> {
                item { DifficultyCard(state, activity) }
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
                    item { Hint("El autor no marcó puntos en esta ruta.") }
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
        item {
            val saved = lib.routes.any { it.name == state.title() && kotlin.math.abs(it.distanceMeters - s.distanceMeters) < 1.0 }
            OutlinedButton(onClick = onSaveCurrent, enabled = !saved, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (saved) "Guardada en tus rutas" else "Guardar en tus rutas")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onPickFile, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("Importar otra")
                }
                FilledTonalButton(onClick = onCreateRoute, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("Crear ruta")
                }
            }
        }
        library(lib)
        item {
            val maps by OfflineMaps.maps.collectAsStateWithLifecycle()
            if (maps.isNotEmpty()) OfflineMapsList(maps)
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
