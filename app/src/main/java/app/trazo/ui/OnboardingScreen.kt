package app.trazo.ui

import app.trazo.gpx.i18n.L
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trazo.gpx.Activity
import app.trazo.gpx.Season
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip

/** Primera pantalla: ¿biker o senderista? */
@Composable
fun OnboardingScreen(onChoose: (Activity) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Trazo", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(L("¿Cómo sales al monte?", "How do you head out?"), style = MaterialTheme.typography.headlineSmall)
            Text(
                L("La app se adapta: rutas, mapa, avisos y estadísticas. Puedes cambiarlo cuando quieras.", "The app adapts: routes, map, alerts and stats. You can change it any time."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ChoiceCard(
                Activity.BIKE,
                L("Rutas MTB, avisos de desvío a 40 m, velocidad media y máxima, enrutado por pistas y senderos ciclables.", "MTB routes, off-route alerts at 40 m, average and top speed, routing on rideable tracks and trails."),
                onChoose,
            )
            ChoiceCard(
                Activity.HIKE,
                L("Senderos GR/PR, tiempo estimado MIDE, avisos más finos, ritmo a pie y enrutado por caminos de montaña.", "GR/PR trails, MIDE time estimates, finer alerts, walking pace and routing on mountain paths."),
                onChoose,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceCard(activity: Activity, text: String, onChoose: (Activity) -> Unit) {
    Card(
        onClick = { onChoose(activity) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(activity.icon, contentDescription = null, modifier = Modifier.padding(14.dp).size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(L("Soy ${activity.who.lowercase()}", "I'm a ${activity.who.lowercase()}"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(activity.label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Ajustes rápidos: cambiar de actividad y elegir la estación del mapa
 * (por defecto va con el calendario).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivitySwitchDialog(
    current: Activity,
    onDismiss: () -> Unit,
    onChoose: (Activity) -> Unit,
    seasonOverride: Season? = null,
    onSeason: (Season?) -> Unit = {},
) {
    val other = if (current == Activity.BIKE) Activity.HIKE else Activity.BIKE
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(current.icon, contentDescription = null) },
        title = { Text(L("¿Cómo sales hoy?", "How are you heading out today?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(L("Ahora mismo: ${current.label}. Las rutas, avisos y estadísticas se adaptan.", "Right now: ${current.label}. Routes, alerts and stats adapt."))
                FilledTonalButton(onClick = { onChoose(other) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(other.icon, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(L("Cambiar a ${other.label.lowercase()}", "Switch to ${other.label.lowercase()}"))
                }
                Text(L("Estación del mapa", "Map season"), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = seasonOverride == null,
                        onClick = { onSeason(null) },
                        label = { Text(L("Automática (${AppMode.todaySeason().label.lowercase()})", "Automatic (${AppMode.todaySeason().label.lowercase()})")) },
                    )
                    Season.entries.forEach { s ->
                        FilterChip(selected = seasonOverride == s, onClick = { onSeason(s) }, label = { Text(s.label) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L("Listo", "Done")) } },
    )
}
