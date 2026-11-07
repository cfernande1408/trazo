package app.trazo.ride

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trazo.gpx.GeoJson
import app.trazo.map.TrazoMapStyle
import app.trazo.map.rememberMapView
import app.trazo.ui.AppMode
import app.trazo.ui.outing
import app.trazo.ui.outingsTitle
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File

private const val LIVE = "live"

/**
 * Grabar una salida sin ruta. El mapa te sigue y dibuja lo que llevas; la
 * grabación vive en el servicio, así que puedes salir de esta pantalla o
 * bloquear el móvil y sigue grabando.
 */
@SuppressLint("MissingPermission")
@Composable
fun RecordScreen(onClose: () -> Unit, onSaved: (File) -> Unit) {
    val context = LocalContext.current
    val state by RideSession.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)

    var hasLocation by remember { mutableStateOf(context.hasLocation()) }
    var startWhenGranted by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocation = context.hasLocation()
        if (hasLocation && startWhenGranted) RideSession.start(context)
        startWhenGranted = false
    }
    LaunchedEffect(Unit) { if (!hasLocation) ask.launch(ridePermissions()) }

    // Reloj del tiempo total, que corre aunque no lleguen posiciones
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.active) {
        while (state.active) { now = System.currentTimeMillis(); delay(1_000) }
    }

    val mapView = rememberMapView()
    var ready by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }
    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.cameraPosition = CameraPosition.Builder().target(LatLng(40.4168, -3.7038)).zoom(12.0).build()
            map.setStyle(
                TrazoMapStyle.baseBuilder(context)
                    .withSource(GeoJsonSource(LIVE, GeoJson.track(emptyList())))
                    .withLayer(LineLayer("$LIVE-casing", LIVE).withProperties(lineColor("#FFFFFF"), lineWidth(8f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
                    .withLayer(LineLayer("$LIVE-line", LIVE).withProperties(lineColor("#4A6800"), lineWidth(4.5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
            ) { style -> ready = map to style }
        }
    }
    LaunchedEffect(ready, hasLocation) {
        val (map, style) = ready ?: return@LaunchedEffect
        if (!hasLocation) return@LaunchedEffect
        map.locationComponent.apply {
            if (!isLocationComponentActivated) activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
            isLocationComponentEnabled = true
            cameraMode = CameraMode.TRACKING_GPS
            renderMode = RenderMode.GPS
            zoomWhileTracking(15.5)
        }
    }
    val pointCount = state.points.sumOf { it.size }
    LaunchedEffect(ready, pointCount) {
        val (_, style) = ready ?: return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>(LIVE)?.setGeoJson(GeoJson.track(state.points.map { seg -> seg.map { it.geo() } }))
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        FilledTonalIconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(16.dp).size(56.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
        }
        var showSos by remember { mutableStateOf(false) }
        var showSensors by remember { mutableStateOf(false) }
        Column(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            app.trazo.safety.SosButton(onClick = { showSos = true })
            app.trazo.sensors.SensorsButton(onClick = { showSensors = true })
        }
        if (showSos) app.trazo.safety.SosSheet(onDismiss = { showSos = false })
        if (showSensors) app.trazo.sensors.SensorsSheet(onDismiss = { showSensors = false })
        Card(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.active) {
                    Text(
                        if (state.paused) "En pausa" else state.routeName?.let { "Navegando · $it" } ?: "Grabando",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                    RideStatsGrid(state.stats, currentKmh = state.currentKmh ?: 0.0)
                    Text("Total ${formatDuration(now - state.startedAtMs)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = { if (state.paused) RideSession.resume() else RideSession.pause() },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { Text(if (state.paused) "Reanudar" else "Pausa", style = MaterialTheme.typography.titleMedium) }
                        Button(
                            onClick = { RideSession.stop(context)?.let(onSaved) ?: onClose() },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { Text("Terminar", style = MaterialTheme.typography.titleMedium) }
                    }
                } else {
                    Text("Graba tu ${AppMode.current.outing} aunque no sigas ninguna ruta.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Sigue grabando con la pantalla apagada. Al terminar verás distancia, tiempo y velocidades, y se guarda en ${AppMode.current.outingsTitle}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            if (hasLocation) RideSession.start(context)
                            else { startWhenGranted = true; ask.launch(ridePermissions()) }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Empezar a grabar", style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }
}
