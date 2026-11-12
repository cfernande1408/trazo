package app.trazo.importer

import app.trazo.gpx.i18n.L
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
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.ExtendedFloatingActionButton
import app.trazo.offline.SavedMap
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
    /** Elegir uno o varios GPX del móvil y guardarlos directamente. */
    onImport: () -> Unit,
    /** Ver en Explorar la zona de un mapa descargado. */
    onOpenMap: (SavedMap) -> Unit,
) {
    var shelf by rememberSaveable { mutableStateOf(Shelf.ROUTES) }
    val maps by OfflineMaps.maps.collectAsStateWithLifecycle()
    // Junto al chip de Rutas, basta con «Realizadas»
    val outings = L("Realizadas", "Completed")

    Scaffold(
        topBar = { TopAppBar(title = { Text(L("Guardadas", "Saved")) }) },
        floatingActionButton = {
            if (shelf == Shelf.ROUTES) {
                ExtendedFloatingActionButton(
                    onClick = onImport,
                    icon = { Icon(Icons.Filled.FileOpen, contentDescription = null) },
                    text = { Text(L("Importar GPX", "Import GPX")) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = shelf == Shelf.ROUTES, onClick = { shelf = Shelf.ROUTES }, label = { Text(L("Rutas · ${routes.size}", "Routes · ${routes.size}")) })
                FilterChip(selected = shelf == Shelf.RIDES, onClick = { shelf = Shelf.RIDES }, label = { Text(outings) })
                FilterChip(selected = shelf == Shelf.MAPS, onClick = { shelf = Shelf.MAPS }, label = { Text(L("Mapas · ${maps.size}", "Maps · ${maps.size}")) })
            }
            when (shelf) {
                Shelf.ROUTES ->
                    if (routes.isEmpty()) EmptyShelf(
                        L("Aún no has guardado ninguna ruta", "You haven't saved any routes yet"),
                        L("Pulsa «Importar GPX» para traer los que tengas en el móvil (puedes elegir varios), o abre una ruta ", "Tap “Import GPX” to bring in the ones on your phone (you can pick several), or open a route ") +
                            L("de Wikiloc o de Explorar y pulsa «Guardar en tus rutas». Las guardadas funcionan sin conexión.", "from Wikiloc or Explore and tap “Save to your routes”. Saved routes work offline."),
                    ) else LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(routes, key = { it.file.absolutePath }) { SavedRouteRow(it, onOpenRoute, onDeleteRoute) }
                    }
                Shelf.RIDES -> RidesList(onOpenRide, onRecord)
                Shelf.MAPS ->
                    if (maps.isEmpty()) EmptyShelf(
                        L("No tienes mapas descargados", "You have no downloaded maps"),
                        L("En el resumen de una ruta, «Descargar mapa»; o en Explorar, el botón de la nube para la zona que ves.", "In a route summary, “Download map”; or in Explore, the cloud button for the area on screen."),
                    ) else LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        item {
                            Text(
                                L("Toca un mapa para ver su zona en Explorar.", "Tap a map to see its area in Explore."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        item { OfflineMapsList(maps, title = false, onOpen = onOpenMap) }
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
                Icon(Icons.Filled.Delete, contentDescription = L("Borrar ruta", "Delete route"))
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(L("¿Borrar «${r.name}»?", "Delete “${r.name}”?")) },
            text = { Text(L("Se quita de tus rutas guardadas. Los mapas descargados de su zona se quedan.", "It is removed from your saved routes. Downloaded maps of its area stay.")) },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete(r) }) { Text(L("Borrar", "Delete")) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(L("Cancelar", "Cancel")) } },
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
