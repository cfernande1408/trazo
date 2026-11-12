package app.trazo.importer

import app.trazo.gpx.i18n.L
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import app.trazo.gpx.osm.Surface
import app.trazo.gpx.osm.SurfaceSummary
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trazo.gpx.Activity
import app.trazo.gpx.ElevationProfile
import app.trazo.gpx.Daylight
import app.trazo.gpx.osm.PoiOnRoute
import app.trazo.nav.RoutePois
import app.trazo.gpx.Slope
import app.trazo.gpx.offline.OfflineArea
import app.trazo.gpx.osm.Difficulty
import app.trazo.gpx.osm.DifficultySummary
import app.trazo.net.Http
import app.trazo.offline.OfflineMaps
import app.trazo.offline.SavedMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private fun color(hex: String) = Color(android.graphics.Color.parseColor(hex))

private fun mb(bytes: Long): String =
    if (bytes < 1024 * 1024) "${(bytes / 1024).coerceAtLeast(1)} kB"
    else "%.1f MB".format(ES, bytes / 1024.0 / 1024.0)

/** Rampas de la ruta: la más dura y cuánto va por encima del 10 % y del 15 %. */
@Composable
internal fun SlopeCard(state: ImportState.Loaded) {
    val summary = remember(state.gpx) { Slope.summary(Slope.grades(ElevationProfile.of(state.gpx.segments))) } ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(L("Pendiente", "Gradient"), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("●  ", color = color(Slope.COLORS[Slope.bucket(summary.maxUpPercent)]))
                Text(
                    L("Rampa más dura: ${summary.maxUpPercent.roundToInt()} % en el km ", "Steepest ramp: ${summary.maxUpPercent.roundToInt()}% at km ") +
                        "%.1f".format(ES, summary.maxUpAtMeters / 1000),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // Solo las cifras que dicen algo: sin "más del 15 %: 0,0 km"
            val details = listOfNotNull(
                summary.upOver10Meters.takeIf { it >= 50 }?.let { L("más del 10 %: ${km(it)}", "over 10%: ${km(it)}") },
                summary.upOver15Meters.takeIf { it >= 50 }?.let { L("más del 15 %: ${km(it)}", "over 15%: ${km(it)}") },
                (-summary.maxDownPercent).roundToInt().takeIf { it >= 5 }?.let { L("bajada más fuerte: $it %", "steepest descent: $it%") },
            ).joinToString(" · ").replaceFirstChar { it.uppercase() }
            if (details.isNotEmpty()) Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                L("En el mapa, el botón «Pendiente» de arriba la colorea tramo a tramo.", "On the map, the “Gradient” button at the top colours it stretch by stretch."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private sealed interface Rated {
    data object Loading : Rated
    data class Ready(val s: DifficultySummary) : Rated
    data class Failed(val message: String) : Rated
}

/** Dificultad según OpenStreetMap: escala MTB (S0–S6) en bici o SAC (T1–T6) a pie. */
@Composable
internal fun DifficultyCard(state: ImportState.Loaded, activity: Activity) {
    val context = LocalContext.current
    val mtb = activity == Activity.BIKE
    var attempt by remember { mutableIntStateOf(0) }
    val rated by produceState<Rated>(Rated.Loading, state.gpx, mtb, attempt) {
        value = Rated.Loading
        value = runCatching {
            val json = RouteOsm.ways(context, state.gpx.segments)
            Rated.Ready(withContext(Dispatchers.Default) { Difficulty.summarize(state.gpx.segments, Difficulty.parse(json), mtb) })
        }.getOrElse { Rated.Failed(Http.friendly(it)) }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (mtb) L("Dificultad técnica (escala MTB)", "Technical difficulty (MTB scale)") else L("Dificultad (escala SAC)", "Difficulty (SAC scale)"), style = MaterialTheme.typography.titleMedium)
            when (val r = rated) {
                Rated.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    OsmLoadingText()
                }
                is Rated.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { attempt++ }) { Text(L("Reintentar", "Retry")) }
                }
                is Rated.Ready -> {
                    val s = r.s
                    if (s.ratedMeters < 1) {
                        Text(L("Los caminos de esta ruta no tienen dificultad marcada en OpenStreetMap.", "The paths on this route have no difficulty rating in OpenStreetMap."), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        val main = Difficulty.label(s.mainGrade!!, mtb)
                        val hardest = Difficulty.label(s.maxGrade!!, mtb)
                        Text(
                            L("Sobre todo ${main.first} (${main.second})", "Mostly ${main.first} (${main.second})") +
                                if (s.maxGrade != s.mainGrade) L(". Lo más difícil: ${hardest.first}, ${hardest.second}.", ". Hardest: ${hardest.first}, ${hardest.second}.") else ".",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        s.metersByGrade.forEach { (g, m) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(Difficulty.label(g, mtb).first, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(32.dp))
                                LinearProgressIndicator(
                                    progress = { (m / s.ratedMeters).toFloat() },
                                    modifier = Modifier.weight(1f).height(8.dp),
                                )
                                Text(km(m), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 10.dp).width(64.dp))
                            }
                        }
                        Text(
                            L("Marcada en ${km(s.ratedMeters)} de ${km(s.routeMeters)}. El resto de caminos no tiene dato.", "Rated on ${km(s.ratedMeters)} of ${km(s.routeMeters)}. The other paths have no data."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private sealed interface Surfaced {
    data object Loading : Surfaced
    data class Ready(val s: SurfaceSummary) : Surfaced
    data class Failed(val message: String) : Surfaced
}

/** Por dónde va la ruta (asfalto, pista, sendero, escaleras), según OpenStreetMap. */
@Composable
internal fun SurfaceCard(state: ImportState.Loaded) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    val surfaced by produceState<Surfaced>(Surfaced.Loading, state.gpx, attempt) {
        value = Surfaced.Loading
        value = runCatching {
            val json = RouteOsm.ways(context, state.gpx.segments)
            Surfaced.Ready(withContext(Dispatchers.Default) { Surface.summarize(state.gpx.segments, Surface.parse(json)) })
        }.getOrElse { Surfaced.Failed(Http.friendly(it)) }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(L("Por dónde va", "Surface"), style = MaterialTheme.typography.titleMedium)
            when (val r = surfaced) {
                Surfaced.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    OsmLoadingText()
                }
                is Surfaced.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { attempt++ }) { Text(L("Reintentar", "Retry")) }
                }
                is Surfaced.Ready -> {
                    val s = r.s
                    if (s.knownMeters < 1) {
                        Text(L("La ruta no coincide con caminos de OpenStreetMap.", "The route does not follow any OpenStreetMap paths."), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        // Barra con cada tipo en su color, como en Komoot
                        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
                            s.metersByKind.forEach { (kind, m) ->
                                Box(Modifier.weight((m / s.knownMeters).toFloat().coerceAtLeast(0.001f)).height(12.dp).background(surfaceColor(kind.color)))
                            }
                        }
                        s.metersByKind.forEach { (kind, m) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("●", color = surfaceColor(kind.color))
                                Text(" ${kind.label}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    "${km(m)} · " + app.trazo.gpx.i18n.percent((s.share(kind) * 100).roundToInt()),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                        if (s.routeMeters - s.knownMeters > 200) {
                            Text(
                                L("Sin dato en ${km(s.routeMeters - s.knownMeters)}: por ahí no hay camino en OpenStreetMap.", "No data for ${km(s.routeMeters - s.knownMeters)}: there is no path in OpenStreetMap there."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun surfaceColor(hex: String) = Color(android.graphics.Color.parseColor(hex))

/** Descargar el mapa de la zona de la ruta para usarlo sin cobertura. */
@Composable
internal fun OfflineCard(state: ImportState.Loaded) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val maps by OfflineMaps.maps.collectAsStateWithLifecycle()
    val downloads by OfflineMaps.downloads.collectAsStateWithLifecycle()
    val key = remember(state.gpx) { OfflineArea.routeKey(state.gpx.segments) }
    val area = remember(state.gpx) { OfflineArea.around(state.gpx.segments) }
    val saved = maps.firstOrNull { it.meta.routeKey == key }
    val running = downloads[key]
    var asking by remember { mutableStateOf(false) }
    // Los cortes sueltos los reintenta MapLibre: la descarga sigue, solo se avisa
    val failed = running?.error?.startsWith(L("Conexión", "Connection")) == false
    val inProgress = running != null && !failed

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                inProgress -> {
                    val d = running!!
                    Text(L("Descargando el mapa… ${(d.progress * 100).roundToInt()} %", "Downloading the map… ${(d.progress * 100).roundToInt()}%"), style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth())
                    d.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                failed -> {
                    Text(running!!.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { OfflineMaps.clearError(key) }) { Text(L("Vale", "OK")) }
                }
                saved != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(L("Disponible sin conexión", "Available offline"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            mb(saved.bytes) + if (saved.meta.withTopo) L(" · con topo IGN", " · with IGN topo") else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { OfflineMaps.delete(context, saved) }) {
                        Icon(Icons.Filled.Delete, contentDescription = L("Borrar mapa descargado", "Delete downloaded map"))
                    }
                }
                else -> OutlinedButton(onClick = { asking = true }, modifier = Modifier.fillMaxWidth(), enabled = area != null) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, Modifier.size(18.dp))
                    Text(L("  Descargar mapa para usar sin conexión", "  Download map for offline use"))
                }
            }
        }
    }

    if (asking && area != null) {
        var topo by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { asking = false },
            icon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
            title = { Text(L("Mapa sin conexión", "Offline map")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(L("Se descarga el mapa y el relieve de la ruta y 2 km alrededor. Funciona en el mapa y al navegar; la vista 3D necesita conexión.", "Downloads the map and terrain for the route plus 2 km around it. Works on the map and when navigating; the 3D view needs a connection."))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = topo, onCheckedChange = { topo = it })
                        Text(L("Incluir topo IGN (solo España)", "Include IGN topo (Spain only)"))
                    }
                    Text(
                        L("Ocupará unos ${mb(OfflineArea.estimateBytes(area, topo))}. Mejor con wifi.", "It will take about ${mb(OfflineArea.estimateBytes(area, topo))}. Wi-Fi recommended."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    OfflineMaps.download(context, state.title(), state.gpx.segments, topo)
                    // Con su mapa descargado, la ruta también se guarda: así se abre desde Guardadas
                    scope.launch(Dispatchers.IO) { runCatching { app.trazo.plan.RoutesLibrary.saveIfMissing(context, state.gpx) } }
                }) { Text(L("Descargar", "Download")) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(L("Cancelar", "Cancel")) } },
        )
    }
}

/** Lista de mapas descargados, en "Mis rutas". */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun OfflineMapsList(maps: List<SavedMap>, title: Boolean = true, onOpen: ((SavedMap) -> Unit)? = null) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title) Text(L("Mapas sin conexión", "Offline maps"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        maps.forEach { m ->
            Card(
                onClick = { onOpen?.invoke(m) },
                enabled = onOpen != null,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(m.meta.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            mb(m.bytes) + if (m.meta.withTopo) L(" · con topo IGN", " · with IGN topo") else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { OfflineMaps.delete(context, m) }) {
                        Icon(Icons.Filled.Delete, contentDescription = L("Borrar mapa descargado", "Delete downloaded map"))
                    }
                }
            }
        }
    }
}

/** Fuentes, manantiales y refugios a lo largo de la ruta, con su km. */
@Composable
internal fun PoisSection(state: ImportState.Loaded) {
    val pois by produceState<List<PoiOnRoute>?>(null, state.gpx) {
        value = runCatching { RoutePois.load(state.gpx.segments) }.getOrDefault(emptyList())
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(L("Agua y refugios en la ruta", "Water and shelters on the route"), style = MaterialTheme.typography.titleMedium)
            val list = pois
            when {
                list == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(L("Buscando fuentes y refugios en OpenStreetMap…", "Looking for water points and shelters in OpenStreetMap…"), style = MaterialTheme.typography.bodyMedium)
                }
                list.isEmpty() -> Text(
                    L("No hay fuentes ni refugios marcados a menos de 150 m de la ruta. Lleva agua de sobra.", "No water points or shelters mapped within 150 m of the route. Carry plenty of water."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> list.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.poi.kind.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.poi.name ?: p.poi.kind.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                p.poi.kind.label + if (p.offsetMeters > 30) L(" · a ${p.offsetMeters.roundToInt()} m de la ruta", " · ${p.offsetMeters.roundToInt()} m from the route") else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("km " + "%.1f".format(ES, p.alongMeters / 1000), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** A qué hora anochece en el inicio y a qué hora terminarías si sales ya. */
@Composable
internal fun LightLine(state: ImportState.Loaded, activity: Activity) {
    val start = state.gpx.segments.firstOrNull()?.firstOrNull() ?: return
    val now = System.currentTimeMillis()
    val day = Math.floorDiv(now, 86_400_000L)
    val sunset = Daylight.sunsetMs(start.lat, start.lon, day) ?: return
    val s = state.stats
    val finish = now + app.trazo.ui.UserProfile.pace(activity).estimateMs(activity, s.distanceMeters, s.elevationGainMeters, s.elevationLossMeters)
    val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    fun hhmm(ms: Long) = fmt.format(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()))
    val late = now < sunset && finish > sunset
    Text(
        when {
            now >= sunset -> L("🌙 Hoy ya ha anochecido (a las ${hhmm(sunset)})", "🌙 It's already dark today (sunset was at ${hhmm(sunset)})")
            late -> L("🌙 Anochece a las ${hhmm(sunset)}: si sales ahora terminarías de noche, hacia las ${hhmm(finish)}", "🌙 Sunset at ${hhmm(sunset)}: if you leave now you'd finish in the dark, around ${hhmm(finish)}")
            else -> L("🌅 Anochece a las ${hhmm(sunset)} · si sales ahora terminas hacia las ${hhmm(finish)}", "🌅 Sunset at ${hhmm(sunset)} · if you leave now you finish around ${hhmm(finish)}")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Mientras llegan los caminos; si tarda, explica por qué (los servidores de OSM son gratuitos y a veces van saturados). */
@Composable
private fun OsmLoadingText() {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(12_000)
        slow = true
    }
    Text(
        if (slow) L("Los servidores de OpenStreetMap van lentos ahora. Sigue esperando o vuelve luego: la próxima vez saldrá al momento.", "The OpenStreetMap servers are slow right now. Keep waiting or come back later: next time it will show at once.")
        else L("Mirando los caminos en OpenStreetMap…", "Checking the paths in OpenStreetMap…"),
        style = MaterialTheme.typography.bodyMedium,
    )
}
