package app.trazo.importer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trazo.gpx.Activity
import app.trazo.plan.SavedRoute
import app.trazo.offline.OfflineMaps
import app.trazo.ride.RidesList
import app.trazo.ui.outingsTitle
import java.io.File

/** Las estanterías de Guardadas, como los filtros de la biblioteca de Spotify. */
private enum class Shelf { ROUTES, RIDES, MAPS }

/**
 * Pestaña Guardadas: todo lo que tienes en el móvil. Tus rutas, tus salidas
 * grabadas y los mapas sin conexión, cada cosa con su chip arriba.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    activity: Activity,
    routes: List<SavedRoute>,
    onOpenRoute: (SavedRoute) -> Unit,
    onDeleteRoute: (SavedRoute) -> Unit,
    onOpenRide: (File) -> Unit,
    onRecord: () -> Unit,
) {
    var shelf by rememberSaveable { mutableStateOf(Shelf.ROUTES) }
    val maps by OfflineMaps.maps.collectAsStateWithLifecycle()
    // "Mis salidas" → "Salidas"
    val outings = activity.outingsTitle.removePrefix("Mis ").replaceFirstChar { it.uppercase() }

    Scaffold(topBar = { TopAppBar(title = { Text("Guardadas") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = shelf == Shelf.ROUTES, onClick = { shelf = Shelf.ROUTES }, label = { Text("Rutas · ${routes.size}") })
                FilterChip(selected = shelf == Shelf.RIDES, onClick = { shelf = Shelf.RIDES }, label = { Text(outings) })
                FilterChip(selected = shelf == Shelf.MAPS, onClick = { shelf = Shelf.MAPS }, label = { Text("Mapas sin conexión · ${maps.size}") })
            }
            when (shelf) {
                Shelf.ROUTES ->
                    if (routes.isEmpty()) EmptyShelf(
                        "Aún no has guardado ninguna ruta",
                        "Abre una de Wikiloc o de Explorar, o crea la tuya, y pulsa «Guardar en tus rutas». Las guardadas funcionan sin conexión.",
                    ) else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(routes, key = { it.file.absolutePath }) { SavedRouteRow(it, onOpenRoute, onDeleteRoute) }
                    }
                Shelf.RIDES -> RidesList(onOpenRide, onRecord)
                Shelf.MAPS ->
                    if (maps.isEmpty()) EmptyShelf(
                        "No tienes mapas descargados",
                        "En el resumen de una ruta, «Descargar mapa»; o en Explorar, el botón de la nube para la zona que ves.",
                    ) else LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        item { OfflineMapsList(maps, title = false) }
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedRouteRow(r: SavedRoute, onOpen: (SavedRoute) -> Unit, onDelete: (SavedRoute) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Card(
        onClick = { onOpen(r) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${km(r.distanceMeters)} · +${metros(r.gainMeters)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { confirm = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Borrar ruta")
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Borrar «${r.name}»?") },
            text = { Text("Se quita de tus rutas guardadas. Los mapas descargados de su zona se quedan.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete(r) }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun EmptyShelf(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
