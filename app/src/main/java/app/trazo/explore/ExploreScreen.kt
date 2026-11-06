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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trazo.gpx.Gpx
import app.trazo.gpx.osm.Overpass
import app.trazo.importer.ES
import app.trazo.importer.Stat
import app.trazo.importer.km
import app.trazo.importer.metros
import app.trazo.map.TopoPreview
import app.trazo.ui.ScrubbableProfile
import app.trazo.ui.openUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(onRouteReady: (Gpx) -> Unit, onOpenWikiloc: () -> Unit, vm: ExploreViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val radius by vm.radiusKm.collectAsStateWithLifecycle()
    val onlyMtb by vm.onlyMtb.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()

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
                    Text("Rutas de OpenStreetMap", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Rutas libres mapeadas por la comunidad cerca de ti. Toca una para verla.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(10, 30, 60).forEach { km ->
                        FilterChip(selected = radius == km, onClick = { vm.setRadius(km) }, label = { Text("$km km") })
                    }
                    FilterChip(selected = onlyMtb, onClick = vm::toggleOnlyMtb, label = { Text("Solo MTB") })
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
                    val shown = if (onlyMtb) s.routes.filter { it.route.isMtb } else s.routes
                    item {
                        Text(
                            if (shown.isEmpty()) "No hay rutas a $radius km. Prueba con más radio."
                            else "${shown.size} rutas a menos de $radius km",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(shown, key = { it.route.id }) { f -> RouteCard(f, onClick = { vm.showPreview(f) }) }
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

    preview?.let { p ->
        PreviewSheet(
            p,
            onDismiss = vm::closePreview,
            onRetry = vm::retryPreview,
            onOpen = { vm.closePreview(); onRouteReady(it) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteCard(f: FoundRoute, onClick: () -> Unit) {
    val r = f.route
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(if (r.isMtb) "MTB" else "Bici")
                Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(details(f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            r.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun details(f: FoundRoute) = listOfNotNull(
    "a ${String.format(ES, "%.0f", f.metersFromYou / 1000)} km de ti",
    f.route.distanceKm?.let { String.format(ES, "%.1f km", it) },
    f.route.ref,
).joinToString(" · ")

@Composable
private fun Badge(text: String) {
    val mtb = text == "MTB"
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (mtb) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (mtb) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** Vista previa de una ruta de OSM: mapa, datos y perfil antes de abrirla en Trazo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewSheet(p: RoutePreview, onDismiss: () -> Unit, onRetry: () -> Unit, onOpen: (Gpx) -> Unit) {
    val context = LocalContext.current
    val r = p.found.route
    var scrub by remember(r.id) { mutableStateOf<Double?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(if (r.isMtb) "MTB" else "Bici")
                Text(r.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(details(p.found), color = MaterialTheme.colorScheme.onSurfaceVariant)

            val loaded = p.loaded
            when {
                loaded != null -> {
                    Card(shape = MaterialTheme.shapes.extraLarge) {
                        TopoPreview(loaded, Modifier.fillMaxWidth().height(220.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("Distancia", km(loaded.stats.distanceMeters), Modifier.weight(1f))
                        Stat("Desnivel +", metros(loaded.stats.elevationGainMeters), Modifier.weight(1f))
                        Stat("Desnivel −", metros(loaded.stats.elevationLossMeters), Modifier.weight(1f))
                    }
                    if (loaded.profile.size >= 2) {
                        ScrubbableProfile(loaded.profile, scrub, onScrub = { scrub = it }, chartHeight = 90.dp)
                    }
                    r.description?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Button(onClick = { onOpen(loaded.gpx) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Abrir en Trazo", style = MaterialTheme.typography.titleMedium)
                    }
                }
                p.error != null -> {
                    Text(p.error, color = MaterialTheme.colorScheme.error)
                    FilledTonalButton(onClick = onRetry) { Text("Reintentar") }
                }
                else -> Busy("Descargando la ruta…")
            }
            FilledTonalButton(onClick = { context.openUrl(Overpass.osmUrl(r.id)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Ver en OpenStreetMap")
            }
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
