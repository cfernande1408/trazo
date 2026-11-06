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
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trazo.gpx.Waypoint
import app.trazo.map.TopoPreview
import app.trazo.ui.ElevationChart
import java.util.Locale

internal val ES: Locale = Locale.forLanguageTag("es-ES")

internal fun km(m: Double) = String.format(ES, "%.1f km", m / 1000)
internal fun metros(m: Double) = String.format(ES, "%,.0f m", m)

/** Wikiloc antepone "Wikiloc - " al nombre de la ruta en el GPX. */
internal fun ImportState.Loaded.title() = gpx.name?.removePrefix("Wikiloc - ") ?: "Ruta sin nombre"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(state: ImportState, onPickFile: () -> Unit, onOpenMap: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Mis rutas") }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ImportState.Empty -> EmptyState(onPickFile)
                ImportState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ImportState.Error -> ErrorState(state.message, onPickFile)
                is ImportState.Loaded -> Loaded(state, onPickFile, onOpenMap)
            }
        }
    }
}

@Composable
private fun EmptyState(onPickFile: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
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
private fun Loaded(state: ImportState.Loaded, onPickFile: () -> Unit, onOpenMap: () -> Unit) {
    val s = state.stats
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
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
            Button(onClick = onOpenMap, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Ver en el mapa", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                val n = state.gpx.waypoints.size
                listOf("Perfil", if (n > 0) "Puntos ($n)" else "Puntos", "Comentarios").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, maxLines = 1) })
                }
            }
        }
        when (tab) {
            0 -> if (state.profile.size >= 2) {
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
                            ElevationChart(state.profile, Modifier.fillMaxWidth().height(110.dp))
                        }
                    }
                }
            } else {
                item { Hint("El GPX no trae altitudes, así que no hay perfil.") }
            }
            1 -> if (state.gpx.waypoints.isEmpty()) {
                item { Hint("El autor no marcó puntos en esta ruta.") }
            } else {
                items(state.gpx.waypoints) { WaypointRow(it) }
            }
            else -> item { CommentsTab(state) }
        }
        item {
            FilledTonalButton(onClick = onPickFile, modifier = Modifier.height(48.dp)) {
                Text("Importar otra ruta")
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
