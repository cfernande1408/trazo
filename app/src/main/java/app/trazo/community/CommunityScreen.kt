package app.trazo.community

import app.trazo.gpx.i18n.L
import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.TrackStatsCalculator
import app.trazo.gpx.community.Hazard
import app.trazo.gpx.osm.Bbox
import app.trazo.nav.locationUpdates
import app.trazo.ride.RideSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private sealed interface Nearby {
    data object Loading : Nearby
    data class Ok(val here: Location, val hazards: List<Hazard>) : Nearby
    data class Failed(val message: String) : Nearby
}

/**
 * Comunidad: avisos de otros usuarios cerca de ti (hielo, árbol caído,
 * camino cortado…) y para avisar tú. Las valoraciones de cada ruta están
 * en la pestaña Comentarios de la ruta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen() {
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    var reporting by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Hazard?>(null) }
    val nearby by produceState<Nearby>(Nearby.Loading, reload) {
        if (!Supabase.configured) { value = Nearby.Failed("off"); return@produceState }
        value = Nearby.Loading
        val here = RideSession.lastLocation
            ?: withTimeoutOrNull(20_000) { runCatching { context.locationUpdates().first() }.getOrNull() }
        if (here == null) { value = Nearby.Failed(L("Sin posición: activa la ubicación para ver los avisos de tu zona.", "No position: turn on location to see alerts in your area.")); return@produceState }
        // Unos 15 km alrededor
        val d = 0.14
        value = runCatching {
            Nearby.Ok(here, CommunityRepo.hazards(context, Bbox(here.latitude - d, here.longitude - d * 1.3, here.latitude + d, here.longitude + d * 1.3)))
        }.getOrElse { Nearby.Failed(it.message ?: L("Sin conexión", "Offline")) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(L("Comunidad", "Community")) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!Supabase.configured) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(L("Comunidad en preparación", "Community coming soon"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            L("Las valoraciones y los avisos se guardan en un proyecto de Supabase. ", "Ratings and alerts are stored in a Supabase project. ") +
                                L("Los pasos están en docs/DESARROLLO.md, en «Activar la comunidad»: crear el proyecto, pegar supabase/schema.sql ", "The steps are in docs/DESARROLLO.md, under “Activar la comunidad”: create the project, paste supabase/schema.sql ") +
                                L("y poner la URL y la clave pública en local.properties.", "and put the URL and public key in local.properties."),
                        )
                    }
                }
                return@Column
            }

            Button(onClick = { reporting = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.ReportProblem, contentDescription = null)
                Text(L("  Avisar de algo donde estoy", "  Report something where I am"))
            }
            Text(
                L("Hielo, barro, un árbol caído, un camino cortado, una batida… El aviso caduca solo y otros pueden decir si sigue ahí.", "Ice, mud, a fallen tree, a closed path, a hunt… The alert expires by itself and others can say if it's still there."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(L("Avisos cerca de ti", "Alerts near you"), style = MaterialTheme.typography.titleLarge)
            when (val n = nearby) {
                Nearby.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(L("Buscando avisos en tu zona…", "Looking for alerts in your area…"))
                }
                is Nearby.Failed -> Text(n.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                is Nearby.Ok -> {
                    val list = n.hazards.filter { it.active }
                        .map { it to TrackStatsCalculator.haversineMeters(GeoPoint(n.here.latitude, n.here.longitude), GeoPoint(it.lat, it.lon)) }
                        .sortedBy { it.second }
                    if (list.isEmpty()) Text(L("No hay avisos en unos 15 km. Buena señal.", "No alerts within about 15 km. Good news."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list.forEach { (h, d) ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth().clickable { open = h },
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ReportProblem, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(h.kind.label, style = MaterialTheme.typography.titleSmall)
                                    h.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                    Text(ago(h.createdAtMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(if (d < 1000) "${d.toInt()} m" else "%.1f km".format(d / 1000), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(L("Valoraciones de rutas", "Route ratings"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        L("Abre una ruta y entra en la pestaña Comentarios para ver las estrellas y comentarios de otros usuarios de Trazo, y dejar el tuyo.", "Open a route and go to the Comments tab to see the stars and comments from other Trazo users, and leave yours."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                L("Sin registro: la app crea un usuario anónimo en este móvil. Solo se publica lo que escribes y dónde está el aviso.", "No sign-up: the app creates an anonymous user on this phone. Only what you write and where the alert is gets published."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (reporting) ReportDialog(onDismiss = { reporting = false }, onSent = { reporting = false; reload++ })
    open?.let { h -> HazardDialog(h, onDismiss = { open = null }, onVoted = { open = null; reload++ }) }
}
