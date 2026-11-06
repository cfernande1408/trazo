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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trazo.gpx.Waypoint
import java.util.Locale

private val ES = Locale.forLanguageTag("es-ES")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(state: ImportState, onPickFile: () -> Unit) {
    Scaffold(topBar = { LargeTopAppBar(title = { Text("Mis rutas") }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ImportState.Empty -> EmptyState(onPickFile)
                ImportState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ImportState.Error -> ErrorState(state.message, onPickFile)
                is ImportState.Loaded -> Loaded(state, onPickFile)
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

@Composable
private fun Loaded(state: ImportState.Loaded, onPickFile: () -> Unit) {
    val s = state.stats
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                state.gpx.name?.removePrefix("Wikiloc - ") ?: "Ruta sin nombre",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Distancia", String.format(ES, "%.1f km", s.distanceMeters / 1000), Modifier.weight(1f))
                Stat("Desnivel +", String.format(ES, "%.0f m", s.elevationGainMeters), Modifier.weight(1f))
                Stat("Desnivel −", String.format(ES, "%.0f m", s.elevationLossMeters), Modifier.weight(1f))
            }
        }
        if (state.gpx.waypoints.isNotEmpty()) {
            item { Text("Puntos del autor", style = MaterialTheme.typography.titleMedium) }
            items(state.gpx.waypoints) { WaypointRow(it) }
        }
        item {
            FilledTonalButton(onClick = onPickFile, modifier = Modifier.height(48.dp)) {
                Text("Importar otra ruta")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
