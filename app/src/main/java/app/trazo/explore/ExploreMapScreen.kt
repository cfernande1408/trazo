package app.trazo.explore

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trazo.gpx.GeoJson
import app.trazo.gpx.Gpx
import app.trazo.gpx.MapMarker
import app.trazo.gpx.RouteGeometry
import app.trazo.gpx.TrackStatsCalculator
import app.trazo.gpx.osm.Bbox
import app.trazo.gpx.osm.Overpass
import app.trazo.importer.km
import app.trazo.importer.metros
import app.trazo.map.rememberMapView
import app.trazo.nav.locationUpdates
import app.trazo.ui.ElevationChart
import app.trazo.gpx.Activity
import app.trazo.ui.AppMode
import app.trazo.ui.badge
import app.trazo.ui.exploreHint
import app.trazo.ui.openUrl
import app.trazo.ui.icon
import app.trazo.ui.routesWord
import app.trazo.gpx.osm.OsmRouteSummary
import app.trazo.gpx.osm.Place
import app.trazo.gpx.osm.PlaceSearch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource

/** Si no hay ubicación, el mapa empieza en Madrid. */
private val MADRID = LatLng(40.4168, -3.7038)

/**
 * Explorar: mapa de tu zona con una bici por cada ruta de OpenStreetMap.
 * Las rutas se piden solo para la zona que ves y se van sumando al moverte.
 * Al tocar una bici se dibuja su recorrido y abajo salen sus datos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreMapScreen(
    onRouteReady: (Gpx) -> Unit,
    activity: Activity = AppMode.current,
    // Un ViewModel por actividad: las rutas de bici y los senderos no se mezclan
    vm: ExploreMapViewModel = viewModel(key = "explore-${activity.name}"),
) {
    val context = LocalContext.current
    val routes by vm.routes.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val onlyMtb by vm.onlyMtb.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val canRetry by vm.canRetry.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var showList by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = selected != null) { vm.clearSelection() }
    BackHandler(enabled = search != null && selected == null) { vm.clearSearch() }
    BackHandler(enabled = showList && search == null && selected == null) { showList = false }

    var hasLocation by remember { mutableStateOf(context.hasLocationPermission()) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocation = it.values.any { granted -> granted }
    }
    LaunchedEffect(Unit) {
        if (!hasLocation) askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val mapView = rememberMapView()
    var ready by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.cameraPosition = CameraPosition.Builder().target(MADRID).zoom(10.0).build()
            map.setStyle(ExploreMapStyle.builder(context, activity)) { style ->
                fun report() {
                    val v = map.projection.visibleRegion
                    val corners = listOfNotNull(v.farLeft, v.farRight, v.nearLeft, v.nearRight)
                    if (corners.isEmpty()) return
                    vm.onViewport(
                        Bbox(
                            corners.minOf { it.latitude }, corners.minOf { it.longitude },
                            corners.maxOf { it.latitude }, corners.maxOf { it.longitude },
                        ),
                        map.cameraPosition.zoom,
                    )
                }
                map.addOnCameraIdleListener { report() }
                map.addOnMapClickListener { ll ->
                    val p = map.projection.toScreenLocation(ll)
                    val r = 24 * context.resources.displayMetrics.density
                    val hit = map.queryRenderedFeatures(RectF(p.x - r, p.y - r, p.x + r, p.y + r), ExploreMapStyle.ROUTES)
                    val id = hit.firstOrNull()?.getNumberProperty("id")?.toLong()
                    if (id != null) vm.select(id)
                    id != null
                }
                report()
                ready = map to style
            }
        }
    }

    // Al tener permiso, se centra en ti y enseña tu posición
    LaunchedEffect(ready, hasLocation) {
        val (map, style) = ready ?: return@LaunchedEffect
        if (!hasLocation) return@LaunchedEffect
        map.showUserLocation(context, style)
        val here = withTimeoutOrNull(10_000) { runCatching { context.locationUpdates().first() }.getOrNull() }
        if (here != null && vm.selected.value == null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(here.latitude, here.longitude), 11.0), 800)
        }
    }

    LaunchedEffect(ready, routes, onlyMtb) {
        val (_, style) = ready ?: return@LaunchedEffect
        val markers = routes.values
            .filter { !onlyMtb || it.isMtb }
            .map { MapMarker(it.id, it.lat, it.lon, it.name, it.isMtb) }
        style.getSourceAs<GeoJsonSource>(ExploreMapStyle.ROUTES)?.setGeoJson(GeoJson.markers(markers))
    }

    // Dibuja la ruta tocada y encuadra la cámara en ella
    val selectedGpx = selected?.gpx
    LaunchedEffect(ready, selectedGpx) {
        val (map, style) = ready ?: return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>(ExploreMapStyle.SELECTED)
            ?.setGeoJson(GeoJson.track(selectedGpx?.segments.orEmpty()))
        val b = selectedGpx?.let { RouteGeometry.bounds(it.segments) } ?: return@LaunchedEffect
        if (b.minLat == b.maxLat && b.minLon == b.maxLon) return@LaunchedEffect
        val bounds = LatLngBounds.Builder().include(LatLng(b.minLat, b.minLon)).include(LatLng(b.maxLat, b.maxLon)).build()
        val pad = (48 * context.resources.displayMetrics.density).toInt()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad, pad * 3, pad, pad * 6), 700)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        // Ir a un lugar del buscador: se marca y se acerca lo justo para que carguen sus rutas
        val goTo: (Place) -> Unit = { p ->
            vm.clearSearch()
            showList = false
            ready?.let { (map, style) ->
                style.getSourceAs<GeoJsonSource>(ExploreMapStyle.PLACE)?.setGeoJson(GeoJson.points(listOf(p.lat to p.lon)))
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lon), PlaceSearch.zoomFor(p, ExploreMapViewModel.MIN_ZOOM)),
                    900,
                )
            }
        }

        Column(
            Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchField(
                query = query,
                onQuery = { query = it },
                placeholder = if (activity == Activity.HIKE) "Busca un pico, un pueblo o un sendero" else "Busca un sitio o una ruta",
                onSearch = {
                    focus.clearFocus()
                    keyboard?.hide()
                    vm.search(query)
                },
                onClear = { query = ""; vm.clearSearch() },
            )
            val results = search
            if (results != null) {
                SearchResultsCard(
                    results, activity,
                    onPlace = goTo,
                    onRoute = { r -> vm.clearSearch(); showList = false; vm.select(r.id) },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (activity == Activity.BIKE) {
                        FilterChip(
                            selected = onlyMtb,
                            onClick = vm::toggleOnlyMtb,
                            label = { Text("Solo MTB") },
                            colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                    status?.let { s ->
                        Surface(
                            onClick = { if (canRetry) vm.retry() },
                            enabled = canRetry,
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (s.startsWith("Buscando") || s.endsWith("reintentando…")) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Box(Modifier.size(8.dp))
                                }
                                Text(s, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }

        // Rutas que se ven ahora, de la más cercana al centro a la más lejana
        val visible = remember(routes, view, onlyMtb) {
            val v = view ?: return@remember emptyList<OsmRouteSummary>()
            val cLat = (v.south + v.north) / 2
            val cLon = (v.west + v.east) / 2
            val k = kotlin.math.cos(Math.toRadians(cLat))
            routes.values
                .filter { it.lat in v.south..v.north && it.lon in v.west..v.east && (!onlyMtb || it.isMtb) }
                .sortedBy { (it.lat - cLat).let { d -> d * d } + ((it.lon - cLon) * k).let { d -> d * d } }
        }

        val sel = selected
        Card(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            if (sel == null && showList && visible.isNotEmpty()) {
                RouteList(
                    visible, activity,
                    onPick = { r -> showList = false; vm.select(r.id) },
                    onClose = { showList = false },
                )
            } else if (sel == null) {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (routes.isEmpty()) "Mueve el mapa o busca la zona donde quieras salir"
                        else activity.exploreHint,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (visible.isNotEmpty()) {
                        FilledTonalButton(onClick = { showList = true }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, Modifier.size(18.dp))
                            Box(Modifier.size(6.dp))
                            Text("Lista (${visible.size})")
                        }
                    }
                }
            } else {
                SelectedCard(sel, activity, onClose = vm::clearSelection, onOpen = onRouteReady, onOsm = { context.openUrl(Overpass.osmUrl(sel.summary.id)) })
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Borrar") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SearchResultsCard(
    results: SearchResults,
    activity: Activity,
    onPlace: (Place) -> Unit,
    onRoute: (OsmRouteSummary) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        LazyColumn(Modifier.heightIn(max = 380.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (results.loading) {
                item {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Buscando «${results.query}»…")
                    }
                }
                return@LazyColumn
            }
            results.error?.let { e -> item { Text(e, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } }
            if (results.error == null && results.places.isEmpty() && results.routes.isEmpty()) {
                item { Text("No he encontrado nada para «${results.query}».", Modifier.padding(16.dp)) }
            }
            if (results.routes.isNotEmpty()) {
                item { SectionTitle(if (activity == Activity.HIKE) "Senderos" else "Rutas") }
                items(results.routes, key = { "r${it.id}" }) { r ->
                    ResultRow(activity.icon, r.name, listOfNotNull(activity.badge(r.isMtb), r.ref).joinToString(" · ")) { onRoute(r) }
                }
            }
            if (results.places.isNotEmpty()) {
                item { SectionTitle("Lugares") }
                items(results.places) { p -> ResultRow(Icons.Filled.LocationOn, p.name, p.detail) { onPlace(p) } }
            }
        }
    }
}

@Composable
private fun RouteList(routes: List<OsmRouteSummary>, activity: Activity, onPick: (OsmRouteSummary) -> Unit, onClose: () -> Unit) {
    Column {
        Row(Modifier.padding(start = 20.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${routes.size} ${activity.routesWord} en esta zona",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cerrar lista") }
        }
        LazyColumn(Modifier.heightIn(max = 340.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(routes, key = { it.id }) { r ->
                ResultRow(activity.icon, r.name, listOfNotNull(activity.badge(r.isMtb), r.ref, r.network).joinToString(" · ")) { onPick(r) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ResultRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SelectedCard(sel: SelectedRoute, activity: Activity, onClose: () -> Unit, onOpen: (Gpx) -> Unit, onOsm: () -> Unit) {
    val r = sel.summary
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (r.isMtb) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    activity.badge(r.isMtb),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(
                r.name,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cerrar") }
        }

        val gpx = sel.gpx
        val loaded = sel.loaded
        when {
            sel.error != null -> Text(sel.error, color = MaterialTheme.colorScheme.error)
            gpx == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Cargando el recorrido…")
            }
            else -> {
                val dist = loaded?.stats?.distanceMeters ?: TrackStatsCalculator.compute(gpx.segments).distanceMeters
                Text(
                    if (loaded != null) "${km(dist)} · +${metros(loaded.stats.elevationGainMeters)} · −${metros(loaded.stats.elevationLossMeters)}"
                    else "${km(dist)} · calculando desnivel…",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (loaded != null && loaded.profile.size >= 2) {
                    ElevationChart(loaded.profile, Modifier.fillMaxWidth().height(56.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { (loaded?.gpx ?: gpx)?.let(onOpen) },
                enabled = gpx != null,
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Text("Abrir en Trazo") }
            FilledTonalButton(onClick = onOsm, modifier = Modifier.height(48.dp)) { Text("OSM") }
        }
    }
}

@SuppressLint("MissingPermission")
private fun MapLibreMap.showUserLocation(context: Context, style: Style) {
    locationComponent.apply {
        if (!isLocationComponentActivated) {
            activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
        }
        isLocationComponentEnabled = true
    }
}

private fun Context.hasLocationPermission() =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
