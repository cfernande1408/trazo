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
import app.trazo.ui.openUrl
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
fun ExploreMapScreen(onRouteReady: (Gpx) -> Unit, vm: ExploreMapViewModel = viewModel()) {
    val context = LocalContext.current
    val routes by vm.routes.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val onlyMtb by vm.onlyMtb.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val canRetry by vm.canRetry.collectAsStateWithLifecycle()

    BackHandler(enabled = selected != null) { vm.clearSelection() }

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
            map.setStyle(ExploreMapStyle.builder(context)) { style ->
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

        Row(
            Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = onlyMtb,
                onClick = vm::toggleOnlyMtb,
                label = { Text("Solo MTB") },
                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            )
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

        val sel = selected
        Card(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            if (sel == null) {
                Text(
                    if (routes.isEmpty()) "Mueve el mapa por la zona donde quieras rodar"
                    else "Toca una bici para ver su recorrido",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                SelectedCard(sel, onClose = vm::clearSelection, onOpen = onRouteReady, onOsm = { context.openUrl(Overpass.osmUrl(sel.summary.id)) })
            }
        }
    }
}

@Composable
private fun SelectedCard(sel: SelectedRoute, onClose: () -> Unit, onOpen: (Gpx) -> Unit, onOsm: () -> Unit) {
    val r = sel.summary
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (r.isMtb) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    if (r.isMtb) "MTB" else "Bici",
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
