package app.trazo.map

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.trazo.gpx.Activity
import app.trazo.gpx.layers.BaseMap
import app.trazo.gpx.layers.LayerPrefs
import app.trazo.gpx.layers.MapLayers
import app.trazo.gpx.layers.RasterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/** Capas elegidas (tipo de mapa y detalles). Las comparten todos los mapas y se recuerdan. */
object MapPrefs {
    private const val PREFS = "trazo"
    private const val KEY = "layers"
    private val _state = MutableStateFlow(LayerPrefs())
    val state: StateFlow<LayerPrefs> = _state.asStateFlow()

    fun init(context: Context) {
        _state.value = LayerPrefs.decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    }

    fun set(context: Context, prefs: LayerPrefs) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, prefs.encode()).apply()
        _state.value = prefs
    }
}

/** Aplica las capas al estilo ya cargado. Se puede llamar las veces que haga falta. */
fun Style.applyLayers(prefs: LayerPrefs, activity: Activity) {
    val plan = MapLayers.plan(prefs, activity)
    // Fuera las capas raster de la vez anterior
    listOf(BaseMap.IGN_TOPO, BaseMap.SATELLITE).mapNotNull { it.raster?.id }
        .plus(MapLayers.routesOverlay(Activity.BIKE).id)
        .forEach { id ->
            getLayer(id)?.let { removeLayer(it) }
            getSource(id)?.let { removeSource(it) }
        }
    MapLayers.STYLE_LAYERS.forEach { id ->
        getLayer(id)?.setProperties(visibility(if (id in plan.hidden) Property.NONE else Property.VISIBLE))
    }
    var top = MapLayers.TOP
    plan.base?.let { spec ->
        addRaster(spec, plan.baseAbove ?: MapLayers.TOP)
        if (plan.baseAbove == MapLayers.TOP) top = spec.id
    }
    plan.overlays.forEach { spec ->
        addRaster(spec, top)
        top = spec.id
    }
}

private fun Style.addRaster(spec: RasterSpec, above: String) {
    val tiles = TileSet("2.2.0", spec.tiles).apply {
        attribution = spec.attribution
        maxZoom = spec.maxZoom.toFloat()
    }
    addSource(RasterSource(spec.id, tiles, 256))
    val layer = RasterLayer(spec.id, spec.id).withProperties(rasterOpacity(spec.opacity))
    if (getLayer(above) != null) addLayerAbove(layer, above) else addLayer(layer)
}

/** Botón redondo de capas, como el de Google Maps. */
@Composable
fun LayersButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Icon(Icons.Filled.Layers, contentDescription = "Capas del mapa")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersSheet(
    prefs: LayerPrefs,
    activity: Activity,
    onChange: (LayerPrefs) -> Unit,
    onDismiss: () -> Unit,
    /** Solo en el mapa de una ruta: colorearla por pendiente. */
    showSlope: Boolean = false,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Tipo de mapa", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BaseMap.entries.forEach { b ->
                    LayerTile(b.label, b.note, selected = prefs.base == b, onClick = { onChange(prefs.copy(base = b)) }, modifier = Modifier.weight(1f)) {
                        BaseThumb(b)
                    }
                }
            }
            HorizontalDivider()
            Text("Detalles del mapa", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // El topo del IGN ya trae su relieve y sus bosques, y en satélite los árboles son los de verdad
                LayerTile(
                    "Relieve", null, selected = prefs.hillshade && prefs.base != BaseMap.IGN_TOPO,
                    enabled = prefs.base != BaseMap.IGN_TOPO,
                    onClick = { onChange(prefs.copy(hillshade = !prefs.hillshade)) },
                    modifier = Modifier.weight(1f),
                ) { IconThumb(Icons.Filled.Terrain) }
                LayerTile(
                    "Árboles", null, selected = prefs.trees && prefs.base == BaseMap.TRAZO,
                    enabled = prefs.base == BaseMap.TRAZO,
                    onClick = { onChange(prefs.copy(trees = !prefs.trees)) },
                    modifier = Modifier.weight(1f),
                ) { IconThumb(Icons.Filled.Park) }
                LayerTile(
                    if (activity == Activity.HIKE) "Senderos" else "Rutas MTB", "señalizados", selected = prefs.routes,
                    onClick = { onChange(prefs.copy(routes = !prefs.routes)) },
                    modifier = Modifier.weight(1f),
                ) { IconThumb(Icons.Filled.Route) }
                if (showSlope) {
                    LayerTile(
                        "Pendiente", "de la ruta", selected = prefs.slope,
                        onClick = { onChange(prefs.copy(slope = !prefs.slope)) },
                        modifier = Modifier.weight(1f),
                    ) { SlopeThumb() }
                }
            }
            Text(
                "Topo y satélite: © Instituto Geográfico Nacional (CC BY 4.0). " +
                    "Rutas: © waymarkedtrails.org. Mapa base: © colaboradores de OpenStreetMap.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LayerTile(
    label: String,
    note: String?,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    /** El ancho lo reparte la fila (weight): caben tres o cuatro sin partir los nombres. */
    modifier: Modifier = Modifier,
    thumb: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.alpha(if (enabled) 1f else 0.4f).clip(shape).clickable(enabled = enabled, onClick = onClick),
    ) {
        Surface(
            shape = shape,
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier.widthIn(max = 80.dp).fillMaxWidth().aspectRatio(1f).padding(2.dp),
        ) { thumb() }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        note?.let {
            Text(
                it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun IconThumb(icon: ImageVector) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxSize()) {}
        Icon(icon, contentDescription = null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Miniatura de la pendiente: una línea que sube cambiando de verde a rojo. */
@Composable
private fun SlopeThumb() {
    Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))) {
        drawRect(Color(0xFFF0EADB))
        val w = size.width
        val h = size.height
        val colors = app.trazo.gpx.Slope.COLORS.map { Color(android.graphics.Color.parseColor(it)) }
        val pts = listOf(0.08f to 0.85f, 0.3f to 0.78f, 0.5f to 0.62f, 0.7f to 0.4f, 0.92f to 0.12f)
        pts.zipWithNext().forEachIndexed { i, (a, b) ->
            drawLine(colors[i + 1], Offset(w * a.first, h * a.second), Offset(w * b.first, h * b.second),
                strokeWidth = w * 0.08f, cap = StrokeCap.Round)
        }
    }
}

/** Miniatura dibujada de cada tipo de mapa. */
@Composable
private fun BaseThumb(b: BaseMap) {
    Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.1f, h * 0.9f)
            cubicTo(w * 0.4f, h * 0.8f, w * 0.3f, h * 0.45f, w * 0.6f, h * 0.4f)
            cubicTo(w * 0.8f, h * 0.36f, w * 0.75f, h * 0.15f, w * 0.9f, h * 0.1f)
        }
        when (b) {
            BaseMap.TRAZO -> {
                drawRect(Color(0xFFF0EADB))
                drawCircle(Color(0xFFC7CD93), w * 0.35f, Offset(w * 0.25f, h * 0.3f))
                drawCircle(Color(0xFFC7CD93), w * 0.3f, Offset(w * 0.85f, h * 0.8f))
                listOf(0.2f to 0.25f, 0.35f to 0.4f, 0.8f to 0.75f).forEach { (x, y) ->
                    drawPath(Path().apply {
                        moveTo(w * x, h * (y - 0.12f)); lineTo(w * (x + 0.06f), h * y); lineTo(w * (x - 0.06f), h * y); close()
                    }, Color(0xFF4F7F33))
                }
                drawPath(path, Color.White, style = Stroke(w * 0.09f, cap = StrokeCap.Round))
                drawPath(path, Color(0xFFC2410C), style = Stroke(w * 0.05f, cap = StrokeCap.Round))
            }
            BaseMap.IGN_TOPO -> {
                drawRect(Color(0xFFF7F4EC))
                drawRect(Color(0xFFD9E8C5), Offset(0f, h * 0.55f), Size(w * 0.6f, h * 0.45f))
                // Curvas de nivel
                for (i in 1..5) {
                    drawCircle(
                        Color(0xFFC08A52), w * 0.09f * i, Offset(w * 0.72f, h * 0.3f),
                        style = Stroke(if (i % 5 == 0) 2.5f else 1.2f),
                    )
                }
                drawLine(Color(0xFF3A7BD5), Offset(0f, h * 0.35f), Offset(w * 0.45f, h * 0.7f), strokeWidth = 3f)
                drawPath(path, Color(0xFFD7262F), style = Stroke(w * 0.035f, cap = StrokeCap.Round))
            }
            BaseMap.SATELLITE -> {
                drawRect(Color(0xFF55603A))
                drawRect(Color(0xFF7A7650), Offset(w * 0.5f, 0f), Size(w * 0.5f, h * 0.45f))
                drawRect(Color(0xFF3B4A2A), Offset(0f, h * 0.55f), Size(w * 0.55f, h * 0.45f))
                drawRect(Color(0xFF9A8F6A), Offset(w * 0.6f, h * 0.6f), Size(w * 0.4f, h * 0.4f))
                drawPath(path, Color(0xFFE8DCC0), style = Stroke(w * 0.04f, cap = StrokeCap.Round))
            }
        }
    }
}
