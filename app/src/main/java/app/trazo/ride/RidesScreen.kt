package app.trazo.ride

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import app.trazo.gpx.ElevationProfile
import app.trazo.gpx.GeoJson
import app.trazo.gpx.Gpx
import app.trazo.gpx.GpxParser
import app.trazo.gpx.RouteGeometry
import app.trazo.map.TrazoMapStyle
import app.trazo.map.rememberMapView
import app.trazo.ui.AppMode
import app.trazo.ui.icon
import app.trazo.ui.label
import app.trazo.ui.outing
import app.trazo.ui.outingsTitle
import app.trazo.ui.recordCta
import app.trazo.ui.ElevationChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

private val DATE = SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", app.trazo.importer.ES)

/** Lista de salidas guardadas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RidesScreen(onBack: () -> Unit, onOpen: (File) -> Unit, onRecord: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    val rides by produceState<List<SavedRide>?>(null) {
        value = withContext(Dispatchers.IO) { RideStore.list(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppMode.current.outingsTitle) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
    ) { padding ->
        val list = rides
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Aún no has grabado ninguna ${AppMode.current.outing}.", style = MaterialTheme.typography.titleMedium)
                Text("Graba una suelta o empieza a navegar una ruta: se guarda sola al terminar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onRecord) { Text(AppMode.current.recordCta) }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.file.name }) { r ->
                    Card(onClick = { onOpen(r.file) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(r.activity.icon, contentDescription = r.activity.label, tint = MaterialTheme.colorScheme.primary)
                                Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Text(DATE.format(Date(r.startedAtMs)).replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${kmStr(r.stats.distanceMeters)} · ${formatDuration(r.stats.movingMs)} · ${kmh(r.stats.avgMovingKmh)} de media",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Una salida: mapa coloreado por velocidad, datos, perfil, exportar y usar como ruta. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideDetailScreen(file: File, onBack: () -> Unit, onUseAsRoute: (Gpx) -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    val ride by produceState<SavedRide?>(null, file) { value = withContext(Dispatchers.IO) { RideStore.load(file) } }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ride?.name ?: "Salida") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
    ) { padding ->
        val r = ride
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(shape = MaterialTheme.shapes.extraLarge) { SpeedMap(r, Modifier.fillMaxWidth().height(260.dp)) }
            }
            item { SpeedLegend(r.activity) }
            item {
                Text(DATE.format(Date(r.startedAtMs)).replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { RideStatsGrid(r.stats) }
            item {
                val profile = remember(r) { RouteGeometry.downsample(ElevationProfile.of(r.points.map { s -> s.map { it.geo() } }), 300) }
                if (profile.size >= 2) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        ElevationChart(profile, Modifier.fillMaxWidth().height(100.dp).padding(12.dp))
                    }
                }
            }
            item {
                Button(onClick = { context.shareGpx(r.file) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Exportar GPX (Strava, Wikiloc…)", style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onUseAsRoute(GpxParser.parse(r.file.readText())) }, modifier = Modifier.weight(1f)) {
                        Text("Usar como ruta")
                    }
                    TextButton(onClick = { confirmDelete = true }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar esta salida?") },
            text = { Text("No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { ride?.let(RideStore::delete); confirmDelete = false; onDeleted() }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** El recorrido grabado, coloreado de azul (despacio) a rojo (rápido). */
@Composable
private fun SpeedMap(ride: SavedRide, modifier: Modifier) {
    val context = LocalContext.current
    val mapView = rememberMapView()
    LaunchedEffect(mapView, ride) {
        mapView.getMapAsync { map ->
            map.uiSettings.isScrollGesturesEnabled = true
            map.setStyle(
                TrazoMapStyle.baseBuilder(context)
                    .withSource(GeoJsonSource("speed", GeoJson.speedLines(ride.points)))
                    .withLayer(LineLayer("speed-casing", "speed").withProperties(lineColor("#FFFFFF"), lineWidth(8f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
                    .withLayer(
                        LineLayer("speed-line", "speed").withProperties(
                            lineWidth(5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                            lineColor(
                                Expression.interpolate(
                                    Expression.linear(), Expression.get("kmh"),
                                    *ride.activity.speedStops.map { (kmh, c) -> Expression.stop(kmh, Expression.color(c.toInt())) }.toTypedArray(),
                                )
                            ),
                        )
                    )
            ) {
                val b = RouteGeometry.bounds(ride.points.map { s -> s.map { it.geo() } }) ?: return@setStyle
                if (b.minLat == b.maxLat && b.minLon == b.maxLon) return@setStyle
                val bounds = LatLngBounds.Builder().include(LatLng(b.minLat, b.minLon)).include(LatLng(b.maxLat, b.maxLon)).build()
                mapView.post {
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, (32 * context.resources.displayMetrics.density).toInt()))
                }
            }
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

@Composable
private fun SpeedLegend(activity: app.trazo.gpx.Activity) {
    val stops = activity.speedStops
    fun n(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(app.trazo.importer.ES, "%.1f", v)
    val labels = stops.mapIndexed { i, (v, c) ->
        val text = when (i) {
            0 -> "< ${n(stops[1].first)} km/h"
            stops.lastIndex -> "> ${n(v)}"
            else -> "${n(v)}–${n(stops[i + 1].first)}"
        }
        text to c
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        labels.forEach { (t, c) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("●", color = androidx.compose.ui.graphics.Color(c))
                Text(t, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Comparte el GPX con otras apps (Strava, Wikiloc, Drive…). */
private fun Context.shareGpx(file: File) {
    val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/gpx+xml")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(send, "Exportar salida"))
}
