package app.trazo.sensors

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Botón de sensores para los mapas. */
@Composable
fun SensorsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Icon(Icons.Filled.MonitorHeart, contentDescription = "Sensores Bluetooth")
    }
}

/** Buscar, emparejar y ver los sensores Bluetooth (pulso, cadencia, potencia). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val found by BleSensors.found.collectAsStateWithLifecycle()
    val scanning by BleSensors.scanning.collectAsStateWithLifecycle()
    val connected by BleSensors.connected.collectAsStateWithLifecycle()
    val readings by BleSensors.readings.collectAsStateWithLifecycle()
    var saved by remember { mutableStateOf(BleSensors.saved(context)) }
    var allowed by remember { mutableStateOf(BleSensors.hasPermissions(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allowed = BleSensors.hasPermissions(context)
        if (allowed) BleSensors.scan(context)
    }
    DisposableEffect(Unit) { onDispose { BleSensors.stopScan(context) } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sensores", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(
                        "En pruebas",
                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Text(
                "Para ver pulso, cadencia o potencia mientras grabas, con una banda de pulso, un sensor de cadencia " +
                    "o un potenciómetro Bluetooth de cualquier marca. Si no tienes ninguno, no hace falta.",
                style = MaterialTheme.typography.bodyMedium,
            )
            // Cómo se usa, paso a paso
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cómo se usa", style = MaterialTheme.typography.titleSmall)
                    listOf(
                        "Despierta el sensor: ponte la banda de pulso (humedécela un poco) o gira la biela.",
                        "Pulsa «Buscar» y, cuando salga, «Añadir». No hace falta emparejarlo en los ajustes del móvil.",
                        "Listo: se conecta solo cada vez que empiezas una ruta o una grabación.",
                        "En la tarjeta de navegación, toca una cifra y elige Pulso, Cadencia o Potencia para verla.",
                    ).forEachIndexed { i, step ->
                        Row {
                            Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                            Text(step, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text(
                        "Si no aparece, cierra otras apps que lo estén usando (Garmin Connect, Zwift, Strava): " +
                            "muchos sensores solo admiten una conexión a la vez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val fresh = BleSensors.fresh()
            if (fresh != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Reading("Pulso", fresh.hr?.let { "$it ppm" }, Modifier.weight(1f))
                    Reading("Cadencia", fresh.cadence?.let { "$it rpm" }, Modifier.weight(1f))
                    Reading("Potencia", fresh.power?.let { "$it W" }, Modifier.weight(1f))
                }
            }

            if (saved.isNotEmpty()) {
                Text("Tus sensores", style = MaterialTheme.typography.titleMedium)
                saved.forEach { address ->
                    val name = found.firstOrNull { it.address == address }?.name ?: address
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (address in connected) "Conectado" else "Se conectará al grabar",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (address in connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { BleSensors.forget(context, address); saved = BleSensors.saved(context) }) { Text("Quitar") }
                    }
                }
                HorizontalDivider()
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cerca de ti", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else FilledTonalButton(onClick = {
                    if (allowed) BleSensors.scan(context) else ask.launch(BleSensors.permissions())
                }) { Text("Buscar") }
            }
            val newOnes = found.filter { it.address !in saved }
            if (newOnes.isEmpty()) {
                Text(
                    if (scanning) "Buscando durante 12 s… despierta el sensor si no sale." else "Pulsa «Buscar» con el sensor despierto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            newOnes.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.bodyLarge)
                        Text(s.kinds.joinToString(" · ") { it.label }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { BleSensors.pair(context, s.address); saved = BleSensors.saved(context) }) { Text("Añadir") }
                }
            }
            // Para que se recomponga con cada lectura
            readings.atMs
        }
    }
}

@Composable
private fun Reading(label: String, value: String?, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
