package app.trazo.ui

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
            Text("¿Cómo sales al monte?", style = MaterialTheme.typography.headlineSmall)
            Text(
                "La app se adapta: rutas, mapa, avisos y estadísticas. Puedes cambiarlo cuando quieras.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ChoiceCard(
                Activity.BIKE,
                "Rutas MTB, avisos de desvío a 40 m, velocidad media y máxima, enrutado por pistas y senderos ciclables.",
                onChoose,
            )
            ChoiceCard(
                Activity.HIKE,
                "Senderos GR/PR, tiempo estimado MIDE, avisos más finos, ritmo a pie y enrutado por caminos de montaña.",
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
                Text("Soy ${activity.who.lowercase()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(activity.label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Diálogo para cambiar de actividad más tarde. */
@Composable
fun ActivitySwitchDialog(current: Activity, onDismiss: () -> Unit, onChoose: (Activity) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(current.icon, contentDescription = null) },
        title = { Text("¿Cómo sales hoy?") },
        text = { Text("Ahora mismo: ${current.label}. Las rutas, avisos y estadísticas se adaptan.") },
        confirmButton = {
            val other = if (current == Activity.BIKE) Activity.HIKE else Activity.BIKE
            TextButton(onClick = { onChoose(other) }) { Text("Cambiar a ${other.label.lowercase()}") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Seguir así") } },
    )
}
