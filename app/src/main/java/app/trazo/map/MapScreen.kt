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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.trazo.gpx.RouteGeometry
import app.trazo.gpx.nav.NavState
import app.trazo.gpx.nav.Navigator
import app.trazo.importer.ES
import app.trazo.importer.ImportState
import app.trazo.importer.Stat
import app.trazo.importer.km
import app.trazo.importer.metros
import app.trazo.importer.title
import app.trazo.nav.Alerts
import app.trazo.nav.locationUpdates
import app.trazo.ui.ElevationChart
import kotlinx.coroutines.flow.catch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** Precisión mínima para usar una posición: con peor señal daría falsos desvíos. */
private const val MAX_ACCURACY_M = 50f
private const val REPEAT_ALERT_MS = 20_000L

@Composable
fun MapScreen(state: ImportState.Loaded, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var navigating by rememberSaveable { mutableStateOf(false) }
    var navState by remember { mutableStateOf<NavState?>(null) }
    var navError by remember { mutableStateOf<String?>(null) }
    BackHandler { if (navigating) navigating = false else onBack() }

    var hasLocation by remember { mutableStateOf(context.hasLocationPermission()) }
    var startWhenGranted by remember { mutableStateOf(false) }
    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasLocation = granted.values.any { it }
        if (hasLocation && startWhenGranted) navigating = true
        startWhenGranted = false
    }
    val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    LaunchedEffect(Unit) { if (!hasLocation) askLocation.launch(locationPermissions) }

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
            // Cámara en la ruta desde el principio, sin esperar a que cargue el estilo
            RouteGeometry.bounds(state.gpx.segments)?.let { b ->
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng((b.minLat + b.maxLat) / 2, (b.minLon + b.maxLon) / 2))
                    .zoom(12.0)
                    .build()
            }
            map.setStyle(TrazoMapStyle.builder(context, state)) { style ->
                mapView.post { fitRoute(map, state, context.resources.displayMetrics.density) }
                ready = map to style
            }
        }
    }

    // Posición en el mapa y cámara: siguiéndote al navegar, libre si no.
    var wasNavigating by remember { mutableStateOf(false) }
    LaunchedEffect(ready, hasLocation, navigating) {
        val (map, style) = ready ?: return@LaunchedEffect
        if (!hasLocation) return@LaunchedEffect
        map.showUserLocation(context, style)
        map.locationComponent.apply {
            if (navigating) {
                cameraMode = CameraMode.TRACKING_GPS
                renderMode = RenderMode.GPS
                zoomWhileTracking(16.0)
            } else {
                cameraMode = CameraMode.NONE
                renderMode = RenderMode.COMPASS
            }
        }
        if (wasNavigating && !navigating) fitRoute(map, state, context.resources.displayMetrics.density)
        wasNavigating = navigating
    }

    // Pantalla encendida mientras navegas
    val view = LocalView.current
    DisposableEffect(navigating) {
        view.keepScreenOn = navigating
        onDispose { view.keepScreenOn = false }
    }

    val alerts = remember { Alerts(context) }
    DisposableEffect(Unit) { onDispose { alerts.release() } }

    LaunchedEffect(navigating, hasLocation) {
        if (!navigating || !hasLocation) return@LaunchedEffect
        navError = null
        navState = null
        val navigator = Navigator(state.gpx.segments)
        var lastAlert = 0L
        var wasOff = false
        context.locationUpdates()
            .catch { navError = it.message; navigating = false }
            .collect { loc ->
                if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return@collect
                val t = loc.elapsedRealtimeNanos / 1_000_000
                val s = navigator.update(loc.latitude, loc.longitude, t)
                navState = s
                when {
                    s.offRouteStarted -> { alerts.offRoute(); lastAlert = t }
                    s.offRoute && t - lastAlert >= REPEAT_ALERT_MS -> { alerts.offRoute(); lastAlert = t }
                    wasOff && !s.offRoute -> alerts.backOnRoute()
                }
                wasOff = s.offRoute
            }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        if (navigating) {
            NavBanner(navState, Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth())
        } else {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.statusBarsPadding().padding(16.dp).size(56.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
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
                val s = navState
                if (navigating) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("Quedan", s?.let { km(it.remainingMeters) } ?: "—", Modifier.weight(1f))
                        Stat("Por subir", s?.let { metros(it.remainingGainMeters) } ?: "—", Modifier.weight(1f))
                        Stat("Recorrido", s?.let { km(it.alongMeters) } ?: "—", Modifier.weight(1f))
                    }
                    if (state.profile.size >= 2) {
                        ElevationChart(
                            state.profile,
                            Modifier.fillMaxWidth().height(72.dp),
                            positionMeters = s?.alongMeters,
                            highlight = s?.nextClimb?.let { it.startMeters..it.endMeters },
                        )
                    }
                    FilledTonalButton(onClick = { navigating = false }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Terminar", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
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
                    navError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = {
                            if (hasLocation) navigating = true
                            else { startWhenGranted = true; askLocation.launch(locationPermissions) }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Empezar ruta", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/** Tarjeta de arriba al navegar: desvío, subida en curso o próxima subida. */
@Composable
private fun NavBanner(s: NavState?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when {
        s?.offRoute == true -> Color(0xFFBA1A1A) to Color.White
        s == null -> scheme.surfaceContainer to scheme.onSurface
        else -> scheme.primaryContainer to scheme.onPrimaryContainer
    }
    val (title, subtitle) = when {
        s == null -> "Buscando GPS…" to "Sal a cielo abierto si tarda"
        s.offRoute -> "Fuera de ruta" to "A ${corta(s.distanceToTrackMeters)} del track"
        s.nextClimb != null && s.metersToNextClimb == 0.0 -> {
            val c = s.nextClimb!!
            "Subiendo" to "Quedan ${corta(c.endMeters - s.alongMeters)} · ${String.format(ES, "%.0f", c.avgGradePercent)} % de media"
        }
        s.nextClimb != null -> {
            val c = s.nextClimb!!
            "Próxima subida en ${corta(s.metersToNextClimb ?: 0.0)}" to
                "${corta(c.lengthMeters)} al ${String.format(ES, "%.0f", c.avgGradePercent)} % · +${metros(c.gainMeters)}"
        }
        else -> "Sin más subidas" to "Quedan ${km(s.remainingMeters)}"
    }
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (s?.offRoute == true) Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(40.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** 450 m / 1,8 km */
private fun corta(m: Double) = if (m < 1000) String.format(ES, "%.0f m", m) else km(m)

private fun fitRoute(map: MapLibreMap, state: ImportState.Loaded, density: Float) {
    val bounds = TrazoMapStyle.bounds(state) ?: return
    val pad = (48 * density).toInt()
    // Arriba el botón de volver, abajo la tarjeta de datos
    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad, pad * 2, pad, pad * 5))
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
