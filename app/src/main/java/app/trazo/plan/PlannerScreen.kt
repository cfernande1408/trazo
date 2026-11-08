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
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.runtime.rememberUpdatedState
import app.trazo.gpx.plan.PlanEdit
import app.trazo.importer.km
import app.trazo.importer.metros
import app.trazo.map.TrazoMapStyle
import app.trazo.map.LayersButton
import app.trazo.map.LayersSheet
import app.trazo.map.MapPrefs
import app.trazo.map.applyLayers
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
private const val SELECTED = "plan-selected"

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
    /** Punto tocado (para moverlo o borrarlo) y si el siguiente toque lo mueve. */
    var selected by remember { mutableStateOf<Int?>(null) }
    var moving by remember { mutableStateOf(false) }
    val density = context.resources.displayMetrics.density

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
    val layers by MapPrefs.state.collectAsStateWithLifecycle()
    var showLayers by remember { mutableStateOf(false) }
    LaunchedEffect(ready, layers) { ready?.second?.applyLayers(layers, activity) }

    // Toque en el mapa: mover el punto elegido, elegir un punto, insertar en la ruta o añadir al final
    val onTap = rememberUpdatedState({ map: MapLibreMap, ll: LatLng ->
        val tap = GeoPoint(ll.latitude, ll.longitude)
        val tol = 28 * density * map.projection.getMetersPerPixelAtLatitude(ll.latitude)
        val sel = selected
        val r = route
        when {
            sel != null && moving -> { if (sel < points.size) points[sel] = tap; selected = null; moving = false }
            sel != null -> selected = null
            else -> {
                val hit = PlanEdit.nearestIndex(points, tap, tol)
                when {
                    hit != null -> selected = hit
                    r != null && PlanEdit.distanceToRoute(r.segments, tap) <= tol ->
                        points.add(PlanEdit.insertIndex(points, r.segments, tap), tap)
                    else -> points += tap
                }
            }
        }
        Unit
    })


    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.cameraPosition = CameraPosition.Builder().target(MADRID).zoom(11.0).build()
            map.setStyle(plannerStyle(context)) { style ->
                map.addOnMapClickListener { ll -> onTap.value(map, ll); true }
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
    LaunchedEffect(ready, snapshot, route, selected) {
        val (_, style) = ready ?: return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>(POINTS)?.setGeoJson(
            GeoJson.markers(snapshot.mapIndexed { i, p -> MapMarker(i + 1L, p.lat, p.lon, "${i + 1}", false) })
        )
        style.getSourceAs<GeoJsonSource>(SELECTED)?.setGeoJson(
            GeoJson.points(listOfNotNull(selected?.let { snapshot.getOrNull(it) }?.let { it.lat to it.lon }))
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
                        when {
                            moving -> "Toca dónde quieres poner el punto ${(selected ?: 0) + 1}"
                            points.isEmpty() -> "Toca el mapa para poner la salida"
                            points.size < 3 -> "Toca para añadir puntos · ${activity.label}"
                            else -> "Toca un punto para moverlo o borrarlo, o la ruta para añadir uno en medio"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (loading) CircularProgressIndicator(Modifier.padding(end = 12.dp).size(22.dp), strokeWidth = 2.5.dp)
            }
        }

        LayersButton(
            onClick = { showLayers = true },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
        )
        if (showLayers) {
            LayersSheet(layers, activity, onChange = { MapPrefs.set(context, it) }, onDismiss = { showLayers = false })
        }

        val sel = selected
        if (sel != null && !moving) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 92.dp),
            ) {
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("  Punto ${sel + 1}", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { moving = true }) { Text("Mover") }
                    TextButton(onClick = {
                        if (sel < points.size) points.removeAt(sel)
                        selected = null
                    }) { Text("Borrar") }
                    TextButton(onClick = { selected = null }) { Text("Cancelar") }
                }
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
                            "A tu ritmo",
                            formatDuration(app.trazo.ui.UserProfile.pace(activity).estimateMs(activity, stats.distanceMeters, stats.elevationGainMeters, stats.elevationLossMeters)),
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
                    FilledTonalIconButton(
                        onClick = { val ob = PlanEdit.outAndBack(points.toList()); points.clear(); points.addAll(ob) },
                        enabled = points.size >= 2 && points.first() != points.last(),
                    ) {
                        Icon(Icons.Filled.SyncAlt, contentDescription = "Ida y vuelta")
                    }
                    FilledTonalIconButton(onClick = { points.clear(); selected = null; moving = false }, enabled = points.isNotEmpty()) {
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
        .withSource(GeoJsonSource(SELECTED, GeoJson.points(emptyList())))
        .withLayer(
            CircleLayer(SELECTED, SELECTED).withProperties(
                circleRadius(16f), circleColor("#C2410C"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
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
