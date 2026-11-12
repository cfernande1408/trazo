package app.trazo.community

import android.content.Context
import android.graphics.RectF
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.trazo.gpx.Activity
import app.trazo.gpx.Gpx
import app.trazo.gpx.community.Community
import app.trazo.gpx.community.Hazard
import app.trazo.gpx.community.HazardKind
import app.trazo.gpx.community.Review
import app.trazo.nav.locationUpdates
import app.trazo.ride.RideSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.time.Duration
import kotlin.math.roundToInt

// ── Avisos en el mapa ──────────────────────────────────────────────────────

private const val HAZARDS = "hazards"

/** Pinta los avisos (círculo rojo con borde blanco y su tipo al acercarte). */
fun Style.showHazards(hazards: List<Hazard>) {
    val json = Community.hazardsGeoJson(hazards)
    getSourceAs<GeoJsonSource>(HAZARDS)?.let { it.setGeoJson(json); return }
    addSource(GeoJsonSource(HAZARDS, json))
    addLayer(
        CircleLayer(HAZARDS, HAZARDS).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor("#B3261E"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        )
    )
    addLayer(
        SymbolLayer("$HAZARDS-name", HAZARDS).withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(11f),
            PropertyFactory.textOffset(arrayOf(0f, 1.4f)),
            PropertyFactory.textColor("#B3261E"),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(1.5f),
        ).apply { minZoom = 12f }
    )
}

/** El aviso bajo el dedo, si hay. */
fun MapLibreMap.hazardAt(ll: LatLng, hazards: List<Hazard>, density: Float): Hazard? {
    val p = projection.toScreenLocation(ll)
    val r = 22 * density
    val id = queryRenderedFeatures(RectF(p.x - r, p.y - r, p.x + r, p.y + r), HAZARDS)
        .firstOrNull()?.getNumberProperty("id")?.toLong() ?: return null
    return hazards.firstOrNull { it.id == id }
}

@Composable
fun ReportButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Icon(Icons.Filled.ReportProblem, contentDescription = "Avisar de algo en el camino")
    }
}

/** "Hace 3 h", "hace 2 días". */
fun ago(ms: Long): String {
    val d = Duration.ofMillis((System.currentTimeMillis() - ms).coerceAtLeast(0))
    return when {
        d.toMinutes() < 60 -> "hace ${d.toMinutes().coerceAtLeast(1)} min"
        d.toHours() < 24 -> "hace ${d.toHours()} h"
        else -> "hace ${d.toDays()} días"
    }
}

/** Ver un aviso y decir si sigue ahí o ya no está. */
@Composable
fun HazardDialog(hazard: Hazard, onDismiss: () -> Unit, onVoted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.ReportProblem, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(hazard.kind.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hazard.note?.let { Text(it) }
                Text(
                    "Avisado ${ago(hazard.createdAtMs)}" + if (hazard.confirms > 0) " · ${hazard.confirms} lo han confirmado" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    runCatching { CommunityRepo.vote(context, hazard, true) }
                        .onSuccess { onVoted() }.onFailure { error = it.message }
                }
            }) { Text("Sigue ahí") }
        },
        dismissButton = {
            TextButton(onClick = {
                scope.launch {
                    runCatching { CommunityRepo.vote(context, hazard, false) }
                        .onSuccess { onVoted() }.onFailure { error = it.message }
                }
            }) { Text("Ya no está") }
        },
    )
}

/** Avisar de algo donde estás: hielo, árbol caído, camino cortado… */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportDialog(onDismiss: () -> Unit, onSent: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf<HazardKind?>(null) }
    var note by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Avisar de algo aquí") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!Supabase.configured) {
                    Text("La comunidad no está configurada en esta versión de la app.", color = MaterialTheme.colorScheme.error)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HazardKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) })
                    }
                }
                OutlinedTextField(note, { note = it.take(300) }, label = { Text("Detalle (opcional)") }, modifier = Modifier.fillMaxWidth())
                kind?.let {
                    Text(
                        "Se pone en tu posición y desaparece solo en ${it.days} ${if (it.days == 1) "día" else "días"}, o antes si otros dicen que ya no está.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = kind != null && !sending && Supabase.configured,
                onClick = {
                    sending = true
                    scope.launch {
                        runCatching {
                            val loc = currentLocation(context) ?: throw Supabase.Error("Sin posición del GPS todavía.")
                            CommunityRepo.report(context, kind!!, loc.first, loc.second, note)
                        }.onSuccess { onSent() }.onFailure { error = it.message; sending = false }
                    }
                },
            ) { Text(if (sending) "Enviando…" else "Avisar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private suspend fun currentLocation(context: Context): Pair<Double, Double>? {
    RideSession.lastLocation?.takeIf { System.currentTimeMillis() - it.time < 120_000 }?.let { return it.latitude to it.longitude }
    return withTimeoutOrNull(20_000) { runCatching { context.locationUpdates().first() }.getOrNull() }?.let { it.latitude to it.longitude }
}

// ── Valoraciones de rutas ───────────────────────────────────────────────────

private sealed interface Loaded<out T> {
    data object Loading : Loaded<Nothing>
    data class Ok<T>(val value: T) : Loaded<T>
    data class Failed(val message: String) : Loaded<Nothing>
}

/** Valoraciones de los usuarios de Trazo de esta ruta, y la tuya. */
@Composable
fun ReviewsSection(gpx: Gpx, activity: Activity) {
    val context = LocalContext.current
    val key = remember(gpx) { Community.reviewKey(gpx) }
    var reload by remember { mutableIntStateOf(0) }
    var writing by remember { mutableStateOf(false) }
    val state by produceState<Loaded<List<Review>>>(Loaded.Loading, key, reload) {
        value = if (!Supabase.configured) Loaded.Failed("La comunidad no está configurada en esta versión.")
        else runCatching { Loaded.Ok(CommunityRepo.reviews(context, key)) }.getOrElse { Loaded.Failed(it.message ?: "Sin conexión") }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Valoraciones en Trazo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (Supabase.configured) TextButton(onClick = { writing = true }) { Text("Valorar") }
            }
            when (val s = state) {
                Loaded.Loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                is Loaded.Failed -> Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                is Loaded.Ok -> {
                    val summary = Community.summary(s.value)
                    if (summary == null) {
                        Text("Nadie la ha valorado todavía. ¡Sé el primero!", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Stars(summary.average.roundToInt())
                            Text(
                                "  " + "%.1f".format(app.trazo.gpx.i18n.I18n.locale, summary.average) +
                                    " · ${summary.count} ${if (summary.count == 1) "valoración" else "valoraciones"}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        s.value.filter { !it.comment.isNullOrBlank() }.take(10).forEach { r ->
                            Column(Modifier.padding(top = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Stars(r.rating, small = true)
                                    Text(
                                        "  ${r.author ?: "Anónimo"} · ${ago(r.createdAtMs)}" +
                                            if (r.activity == "HIKE") " · a pie" else if (r.activity == "BIKE") " · en bici" else "",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(r.comment!!, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
    if (writing) {
        ReviewDialog(key, activity, onDismiss = { writing = false }, onSent = { writing = false; reload++ })
    }
}

@Composable
private fun Stars(n: Int, small: Boolean = false, onPick: ((Int) -> Unit)? = null) {
    Row {
        (1..5).forEach { i ->
            val icon = if (i <= n) Icons.Filled.Star else Icons.Filled.StarBorder
            if (onPick != null) {
                IconButton(onClick = { onPick(i) }) {
                    Icon(icon, contentDescription = "$i estrellas", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(32.dp))
                }
            } else {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(if (small) 14.dp else 20.dp))
            }
        }
    }
}

@Composable
private fun ReviewDialog(key: String, activity: Activity, onDismiss: () -> Unit, onSent: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("trazo", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var rating by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var author by remember { mutableStateOf(prefs.getString("community_name", "") ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Valorar la ruta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Stars(rating, onPick = { rating = it })
                OutlinedTextField(comment, { comment = it.take(1000) }, label = { Text("Comentario (opcional)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it.take(40) }, label = { Text("Tu nombre (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    "Se publica para todos los usuarios de Trazo. No se guarda nada más de ti.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            FilledTonalButton(
                enabled = rating in 1..5 && !sending,
                onClick = {
                    sending = true
                    prefs.edit().putString("community_name", author.trim()).apply()
                    scope.launch {
                        runCatching { CommunityRepo.addReview(context, key, rating, comment, author, activity.name) }
                            .onSuccess { onSent() }.onFailure { error = it.message; sending = false }
                    }
                },
            ) { Text(if (sending) "Enviando…" else "Publicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
