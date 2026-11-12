package app.trazo.importer

import app.trazo.gpx.i18n.L
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trazo.gpx.Activity
import app.trazo.gpx.Season
import app.trazo.gpx.ride.Calories
import app.trazo.plan.SavedRoute
import app.trazo.ride.RideStore
import app.trazo.ride.SavedRide
import app.trazo.ui.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Lo que se puede hacer desde Inicio. */
internal class HomeActions(
    val onRecord: () -> Unit,
    val onExplore: () -> Unit,
    val onWikiloc: () -> Unit,
    val onPickFile: () -> Unit,
    val onCreateRoute: () -> Unit,
    val onLibrary: () -> Unit,
    val onOpenSaved: (SavedRoute) -> Unit,
    val onProfile: () -> Unit,
)

/** Tu semana: salidas desde el lunes. */
private data class Week(val outings: Int, val meters: Double, val gain: Double, val movingMs: Long, val kcal: Int)

/**
 * Portada: el paisaje de la estación con lo que más se hace (grabar y
 * explorar), tu semana, tus rutas a mano y el resto de opciones.
 */
@Composable
internal fun Home(activity: Activity, season: Season, routes: List<SavedRoute>, a: HomeActions) {
    val context = LocalContext.current
    // Las salidas se leen del disco una vez; las kcal se calculan aquí, al
    // pintar, para que cambien en cuanto tocas el peso o la altura del perfil
    val rides by produceState<List<SavedRide>?>(null, activity) {
        value = withContext(Dispatchers.IO) {
            val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            runCatching { RideStore.list(context) }.getOrDefault(emptyList()).filter { it.startedAtMs >= monday }
        }
    }
    val week = rides?.let { list ->
        Week(
            list.size,
            list.sumOf { it.stats.distanceMeters },
            list.sumOf { it.stats.gainMeters },
            list.sumOf { it.stats.movingMs },
            list.sumOf { UserProfile.kcal(it.stats, it.activity) },
        )
    }
    val tipList = remember(activity, a) { tips(activity, a, webOpener(context)) }
    var discoverOpen by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Hero(activity, season, a) }
        item { WeekCard(week, activity) }
        item { Shortcuts(a) }
        discover(tipList, discoverOpen) { discoverOpen = it }
        if (routes.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L("Tus rutas", "Your routes"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = a.onLibrary) { Text(L("Ver todas", "See all")) }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(routes.take(10), key = { it.file.absolutePath }) { r -> RouteTile(r) { a.onOpenSaved(r) } }
                }
            }
        }
        item {
            Text(
                L("Desde Wikiloc: en la ruta, Descargar → GPX y luego Compartir → Trazo. O ábrelo con «Importar GPX».", "From Wikiloc: on the route, Download → GPX and then Share → Trazo. Or open it with “Import GPX”."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** Cielo de cada estación, de arriba abajo. */
private fun sky(season: Season): Pair<Color, Color> = when (season) {
    Season.SPRING -> Color(0xFF8FCBEA) to Color(0xFFE6F3D8)
    Season.SUMMER -> Color(0xFF6DBDEB) to Color(0xFFF5E6BC)
    Season.AUTUMN -> Color(0xFFE99A5B) to Color(0xFFF6D9B4)
    Season.WINTER -> Color(0xFFA9C1D8) to Color(0xFFEDF2F6)
}

private fun hex(s: String) = Color(android.graphics.Color.parseColor(s))

/**
 * Sierra de la estación dibujada a lo ancho: cielo, tres cordales cada vez
 * más cerca y oscuros, y un trazo de ruta subiendo a la cumbre, como el icono.
 */
@Composable
private fun Landscape(season: Season, modifier: Modifier) {
    val (top, bottom) = sky(season)
    // En invierno, las cumbres del fondo nevadas
    val far = if (season == Season.WINTER) Color(0xFFDDE6EE) else hex(season.pineLight).copy(alpha = 0.55f)
    val mid = hex(season.pineShade).copy(alpha = 0.8f)
    val near = hex(season.pineShade)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        // Sol (o luna clara en invierno), arriba a la derecha
        drawCircle(Color.White.copy(alpha = if (season == Season.WINTER) 0.7f else 0.55f), radius = h * 0.07f, center = Offset(w * 0.8f, h * 0.2f))
        /** Un cordal: suma de ondas fija (siempre igual), entre [base] y [base] − [amp]. */
        fun ridge(base: Float, amp: Float, phase: Float, color: Color) {
            val path = Path().apply { moveTo(0f, h) }
            val pts = (0..60).map { i ->
                val x = w * i / 60f
                val t = i / 60f * 6.283f
                val y = base - amp * (0.55f * kotlin.math.sin(t * 1.3f + phase) + 0.3f * kotlin.math.sin(t * 2.9f + phase * 2) + 0.15f * kotlin.math.sin(t * 6.1f + phase * 3)).let { (it + 1f) / 2f }
                Offset(x, y)
            }
            pts.forEach { path.lineTo(it.x, it.y) }
            path.lineTo(w, h)
            path.close()
            drawPath(path, color)
        }
        ridge(h * 0.62f, h * 0.38f, 0.4f, far)
        ridge(h * 0.78f, h * 0.30f, 2.1f, mid)
        ridge(h * 0.98f, h * 0.24f, 4.0f, near)
        // El trazo de la ruta, con su borde blanco, hasta un punto final
        val trail = Path().apply {
            moveTo(w * 0.04f, h * 0.97f)
            cubicTo(w * 0.28f, h * 0.9f, w * 0.3f, h * 0.68f, w * 0.48f, h * 0.62f)
            cubicTo(w * 0.6f, h * 0.58f, w * 0.62f, h * 0.42f, w * 0.7f, h * 0.36f)
        }
        drawPath(trail, Color.White.copy(alpha = 0.9f), style = Stroke(width = h * 0.045f, cap = StrokeCap.Round))
        drawPath(trail, Color(0xFFD9480F), style = Stroke(width = h * 0.025f, cap = StrokeCap.Round))
        drawCircle(Color.White, radius = h * 0.035f, center = Offset(w * 0.7f, h * 0.36f))
        drawCircle(Color(0xFFD9480F), radius = h * 0.022f, center = Offset(w * 0.7f, h * 0.36f))
    }
}

@Composable
private fun Hero(activity: Activity, season: Season, a: HomeActions) {
    Card(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            Landscape(season, Modifier.fillMaxSize())
            // Velo oscuro abajo para que se lea el texto
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color(0x22000000), 1f to Color(0xD9000000)),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(L("Bienvenido a Trazo", "Welcome to Trazo"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text(season.tip, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                // Los dos a partes iguales, para que quepan con la letra grande
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = a.onRecord, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text(L(" Grabar", " Record"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = a.onExplore,
                        modifier = Modifier.weight(1f).height(48.dp),
                        border = BorderStroke(1.5.dp, Color.White),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = null)
                        Text(L(" Explorar", " Explore"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCard(week: Week?, activity: Activity) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(L("Tu semana", "Your week"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val w = week
            when {
                w == null -> Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                w.outings == 0 -> Text(
                    if (activity == Activity.BIKE) L("Aún no has salido esta semana. Hay rutas esperando.", "You haven't been out this week yet. Routes are waiting.") else L("Aún no has salido esta semana. Hay senderos esperando.", "You haven't been out this week yet. Trails are waiting."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeekStat(if (activity == Activity.BIKE) L("Salidas", "Rides") else L("Excursiones", "Hikes"), "${w.outings}", Modifier.weight(1f))
                        WeekStat(L("Distancia", "Distance"), km(w.meters), Modifier.weight(1f))
                        WeekStat(L("Desnivel +", "Ascent"), metros(w.gain), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val min = (w.movingMs / 60_000.0).roundToInt()
                        WeekStat(L("En marcha", "Moving"), "${min / 60} h ${"%02d".format(min % 60)}", Modifier.weight(1f))
                        WeekStat("Kcal", Calories.format(w.kcal).removeSuffix(" kcal"), Modifier.weight(1f))
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Shortcuts(a: HomeActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Shortcut(Icons.Filled.FileOpen, L("Importar GPX", "Import GPX"), a.onPickFile, Modifier.weight(1f))
        Shortcut(Icons.Filled.Edit, L("Crear ruta", "Create route"), a.onCreateRoute, Modifier.weight(1f))
        Shortcut(Icons.Filled.Search, "Wikiloc", a.onWikiloc, Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Shortcut(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteTile(r: SavedRoute, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(200.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(40.dp))
            Text("${km(r.distanceMeters)} · +${metros(r.gainMeters)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("Abrir", "Open"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(start = 2.dp))
            }
        }
    }
}
