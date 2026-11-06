package app.trazo.plan

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.trazo.gpx.ElevationProfile
import app.trazo.gpx.GeoJson
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.Gpx
import app.trazo.gpx.GpxParser
import app.trazo.gpx.MapMarker
import app.trazo.gpx.RouteGeometry
import app.trazo.gpx.TrackStatsCalculator
import app.trazo.gpx.Track
import app.trazo.gpx.plan.BRouter
import app.trazo.importer.km
import app.trazo.importer.metros
import app.trazo.map.TrazoMapStyle
import app.trazo.map.rememberMapView
import app.trazo.nav.locationUpdates
import app.trazo.net.Http
import app.trazo.ride.formatDuration
import app.trazo.ui.AppMode
import app.trazo.ui.ElevationChart
import app.trazo.ui.label
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

private val MADRID = LatLng(40.4168, -3.7038)
private const val ROUTE = "plan-route"
private const val GUIDE = "plan-guide"
private const val POINTS = "plan-points"

/**
 * Crear ruta: tocas el mapa para poner puntos y BRouter traza la ruta por
 * pistas y senderos (perfil MTB o de montaña según seas biker o senderista).
 * Al guardarla queda en "Tus rutas" y se abre como cualquier otra.
 */
@Composable
fun PlannerScreen(onClose: () -> Unit, onSaved: (Gpx) -> Unit) {
    val context = LocalContext.current
    val activity = AppMode.current
    val points = remember { mutableStateListOf<GeoPoint>() }
    var route by remember { mutableStateOf<Gpx?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var askName by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

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
            map.cameraPosition = CameraPosition.Builder().target(MADRID).zoom(11.0).build()
            map.setStyle(plannerStyle(context)) { style ->
                map.addOnMapClickListener { ll ->
                    points += GeoPoint(ll.latitude, ll.longitude)
                    true
                }
                ready = map to style
            }
        }
    }

    LaunchedEffect(ready, hasLocation) {
        val (map, style) = ready ?: return@LaunchedEffect
        if (!hasLocation) return@LaunchedEffect
        map.showUserLocation(context, style)
        val here = withTimeoutOrNull(10_000) { runCatching { context.locationUpdates().first() }.getOrNull() }
        if (here != null && points.isEmpty()) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(here.latitude, here.longitude), 13.0), 800)
        }
    }

    // Cada vez que cambian los puntos se recalcula la ruta (con un respiro por si sigues tocando)
    val snapshot = points.toList()
    LaunchedEffect(snapshot) {
        error = null
        if (snapshot.size < 2) {
            route = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(500)
        try {
            val (code, body) = Http.getWithStatus(BRouter.url(snapshot, activity))
            if (code !in 200..299 || !body.trimStart().startsWith("<")) {
                error = BRouter.friendlyError(body)
            } else {
                val parsed = withContext(Dispatchers.Default) { GpxParser.parse(body) }
                route = Http.withElevation(parsed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = Http.friendly(e)
        } finally {
            loading = false
        }
    }

    // Pinta puntos numerados, la ruta calculada y, mientras no la hay, una guía en línea recta
    LaunchedEffect(ready, snapshot, route) {
        val (_, style) = ready ?: return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>(POINTS)?.setGeoJson(
            GeoJson.markers(snapshot.mapIndexed { i, p -> MapMarker(i + 1L, p.lat, p.lon, "${i + 1}", false) })
        )
        style.getSourceAs<GeoJsonSource>(ROUTE)?.setGeoJson(GeoJson.track(route?.segments.orEmpty()))
        style.getSourceAs<GeoJsonSource>(GUIDE)?.setGeoJson(
            GeoJson.track(if (route == null && snapshot.size >= 2) listOf(snapshot) else emptyList())
        )
    }

    val stats = remember(route) { route?.let { TrackStatsCalculator.compute(it.segments) } }
    val profile = remember(route) { route?.let { RouteGeometry.downsample(ElevationProfile.of(it.segments), 300) }.orEmpty() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                Column(Modifier.weight(1f)) {
                    Text("Crear ruta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (points.isEmpty()) "Toca el mapa para poner la salida" else "Toca para añadir puntos · ${activity.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (loading) CircularProgressIndicator(Modifier.padding(end = 12.dp).size(22.dp), strokeWidth = 2.5.dp)
            }
        }

        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (stats != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Figure("Distancia", km(stats.distanceMeters))
                        Figure("Desnivel +", metros(stats.elevationGainMeters))
                        Figure(
                            "Tiempo est.",
                            formatDuration(activity.estimateMs(stats.distanceMeters, stats.elevationGainMeters, stats.elevationLossMeters)),
                        )
                    }
                    if (profile.size >= 2) ElevationChart(profile, Modifier.fillMaxWidth().height(64.dp))
                } else {
                    Text(
                        error ?: when (points.size) {
                            0 -> "Pon el primer punto donde empiezas."
                            1 -> "Ahora toca donde quieres ir. La ruta irá por caminos."
                            else -> "Calculando la ruta…"
                        },
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (stats != null && error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { points.removeLastOrNull() }, enabled = points.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Deshacer")
                    }
                    FilledTonalIconButton(
                        onClick = { points += points.first() },
                        enabled = points.size >= 2 && points.first() != points.last(),
                    ) {
                        Icon(Icons.Filled.Loop, contentDescription = "Cerrar circular")
                    }
                    FilledTonalIconButton(onClick = { points.clear() }, enabled = points.isNotEmpty()) {
                        Icon(Icons.Filled.Delete, contentDescription = "Borrar todo")
                    }
                    Box(Modifier.weight(1f))
                    Button(onClick = { askName = true }, enabled = route != null && !loading) { Text("Guardar") }
                }
            }
        }
    }

    if (askName) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askName = false },
            title = { Text("Nombre de la ruta") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    singleLine = true, placeholder = { Text("Mi ruta") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = route ?: return@TextButton
                    val finalName = name.trim().ifEmpty { "Mi ruta" }
                    RoutesLibrary.save(context, finalName, r.segments, "Creada con Trazo")
                    askName = false
                    onSaved(r.copy(name = finalName, tracks = listOf(Track(finalName, r.segments)), description = "Creada con Trazo"))
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { askName = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

private fun plannerStyle(context: Context): Style.Builder =
    TrazoMapStyle.baseBuilder(context)
        .withSource(GeoJsonSource(GUIDE, GeoJson.track(emptyList())))
        .withLayer(
            LineLayer(GUIDE, GUIDE).withProperties(
                lineColor("#1A1C16"), lineWidth(2.5f), lineDasharray(arrayOf(2f, 2f)),
            )
        )
        .withSource(GeoJsonSource(ROUTE, GeoJson.track(emptyList())))
        .withLayer(
            LineLayer("$ROUTE-casing", ROUTE).withProperties(
                lineColor("#FFFFFF"), lineWidth(9f),
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        .withLayer(
            LineLayer(ROUTE, ROUTE).withProperties(
                lineColor("#C2410C"), lineWidth(5f),
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        .withSource(GeoJsonSource(POINTS, GeoJson.markers(emptyList())))
        .withLayer(
            CircleLayer(POINTS, POINTS).withProperties(
                circleRadius(11f), circleColor("#1A1C16"),
                circleStrokeColor("#FFFFFF"), circleStrokeWidth(2.5f),
            )
        )
        .withLayer(
            SymbolLayer("$POINTS-num", POINTS).withProperties(
                textField(Expression.get("name")),
                textFont(arrayOf("Noto Sans Bold")),
                textSize(12f), textColor("#FFFFFF"),
                textAllowOverlap(true), textIgnorePlacement(true),
            )
        )

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
