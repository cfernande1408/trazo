package app.trazo.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.trazo.gpx.GeoJson
import app.trazo.gpx.RouteGeometry
import app.trazo.importer.ImportState
import app.trazo.importer.Stat
import app.trazo.importer.km
import app.trazo.importer.metros
import app.trazo.importer.title
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource

/** Estilo vectorial gratuito y sin clave. Más adelante: estilo topo propio y offline. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val TRACK_COLOR = "#C2410C"

@Composable
fun MapScreen(state: ImportState.Loaded, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var hasLocation by remember { mutableStateOf(context.hasLocationPermission()) }
    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> hasLocation = granted.values.any { it } }
    LaunchedEffect(Unit) {
        if (!hasLocation) {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var ready by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }

    // El MapView necesita que le reenvíen el ciclo de vida.
    DisposableEffect(lifecycle, mapView) {
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> { mapView.onDestroy(); destroyed = true }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (!destroyed) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                addRoute(style, state)
                fitRoute(map, state, density = context.resources.displayMetrics.density)
                ready = map to style
            }
        }
    }

    LaunchedEffect(ready, hasLocation) {
        val (map, style) = ready ?: return@LaunchedEffect
        if (hasLocation) map.showUserLocation(context, style)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(16.dp).size(56.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    state.title(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Distancia", km(state.stats.distanceMeters), Modifier.weight(1f))
                    Stat("Desnivel +", metros(state.stats.elevationGainMeters), Modifier.weight(1f))
                }
            }
        }
    }
}

private fun addRoute(style: Style, state: ImportState.Loaded) {
    style.addSource(GeoJsonSource("track", GeoJson.track(state.gpx.segments)))
    style.addLayer(
        LineLayer("track-casing", "track").withProperties(
            lineColor("#FFFFFF"), lineWidth(9f),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    style.addLayer(
        LineLayer("track-line", "track").withProperties(
            lineColor(TRACK_COLOR), lineWidth(5f),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        )
    )

    val wpts = state.gpx.waypoints.map { it.lat to it.lon }
    if (wpts.isNotEmpty()) {
        style.addSource(GeoJsonSource("waypoints", GeoJson.points(wpts)))
        style.addLayer(
            CircleLayer("waypoints", "waypoints").withProperties(
                circleRadius(6f), circleColor("#1F6FB2"),
                circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f),
            )
        )
    }

    val segs = state.gpx.segments
    val ends = listOfNotNull(segs.firstOrNull()?.firstOrNull(), segs.lastOrNull()?.lastOrNull())
    style.addSource(GeoJsonSource("ends", GeoJson.points(ends.map { it.lat to it.lon })))
    style.addLayer(
        CircleLayer("ends", "ends").withProperties(
            circleRadius(7f), circleColor("#1A1C16"),
            circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
        )
    )
}

private fun fitRoute(map: MapLibreMap, state: ImportState.Loaded, density: Float) {
    val b = RouteGeometry.bounds(state.gpx.segments) ?: return
    if (b.minLat == b.maxLat && b.minLon == b.maxLon) {
        map.cameraPosition = CameraPosition.Builder().target(LatLng(b.minLat, b.minLon)).zoom(14.0).build()
        return
    }
    val bounds = LatLngBounds.Builder()
        .include(LatLng(b.minLat, b.minLon))
        .include(LatLng(b.maxLat, b.maxLon))
        .build()
    val pad = (48 * density).toInt()
    // Abajo dejamos sitio para la tarjeta de datos
    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad, pad * 2, pad, pad * 4))
}

@SuppressLint("MissingPermission")
private fun MapLibreMap.showUserLocation(context: Context, style: Style) {
    locationComponent.apply {
        activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
        isLocationComponentEnabled = true
        renderMode = RenderMode.COMPASS
    }
}

private fun Context.hasLocationPermission() =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
