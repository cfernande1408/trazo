package app.trazo.explore

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trazo.gpx.Gpx
import app.trazo.importer.ES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(onRouteReady: (Gpx) -> Unit, onOpenWikiloc: () -> Unit, vm: ExploreViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val radius by vm.radiusKm.collectAsStateWithLifecycle()
    val opening by vm.opening.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.search()
    }
    val search = {
        askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Explorar") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Buscar en Wikiloc", style = MaterialTheme.typography.titleLarge)
                        Text("Navega por Wikiloc con tu cuenta. Al pulsar Descargar GPX, la ruta se importa sola.")
                        Button(onClick = onOpenWikiloc) { Text("Abrir Wikiloc") }
                    }
                }
            }
            item {
                Column {
                    Text("Rutas MTB de OpenStreetMap", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Rutas libres mapeadas por la comunidad cerca de ti.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(10, 30, 60).forEach { km ->
                        FilterChip(selected = radius == km, onClick = { vm.setRadius(km) }, label = { Text("$km km") })
                    }
                }
            }
            message?.let { m ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::clearMessage) { Text("Vale") }
                    }
                }
            }
            when (val s = state) {
                ExploreState.Idle -> item {
                    Button(onClick = search, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Buscar rutas cerca de mí", style = MaterialTheme.typography.titleMedium)
                    }
                }
                ExploreState.Locating -> item { Busy("Buscando tu ubicación…") }
                ExploreState.Searching -> item { Busy("Buscando rutas en OpenStreetMap…") }
                is ExploreState.Error -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        FilledTonalButton(onClick = search) { Text("Reintentar") }
                    }
                }
                is ExploreState.Results -> {
                    if (s.routes.isEmpty()) {
                        item {
                            Text(
                                "No hay rutas MTB mapeadas a $radius km. Prueba con más radio.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(s.routes, key = { it.route.id }) { f ->
                        RouteCard(f, loading = opening == f.route.id, onClick = { vm.open(f.route.id, onRouteReady) })
                    }
                }
            }
            item {
                Text(
                    "Datos © colaboradores de OpenStreetMap (ODbL). Altitud: Open-Meteo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteCard(f: FoundRoute, loading: Boolean, onClick: () -> Unit) {
    val r = f.route
    Card(
        onClick = onClick,
        enabled = !loading,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(
                    "a ${String.format(ES, "%.0f", f.metersFromYou / 1000)} km de ti",
                    r.distanceKm?.let { String.format(ES, "%.1f km", it) },
                    r.ref,
                ).joinToString(" · ")
                Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant)
                r.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (loading) CircularProgressIndicator(Modifier.size(28.dp))
        }
    }
}

@Composable
private fun Busy(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
