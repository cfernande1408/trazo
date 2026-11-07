package app.trazo.safety

import android.Manifest
import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trazo.gpx.safety.Coordinates
import app.trazo.nav.locationUpdates
import app.trazo.ride.RideSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** Botón rojo de SOS para los mapas. */
@Composable
fun SosButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Icon(Icons.Filled.Sos, contentDescription = "Emergencia: mi posición")
    }
}

/**
 * Mi posición para el 112: coordenadas en grande (UTM y grados), llamar,
 * mandar por SMS al contacto o compartir, y los ajustes de seguridad.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings by Safety.settings.collectAsStateWithLifecycle()
    var loc by remember { mutableStateOf(RideSession.lastLocation) }
    // Sin salida en marcha no hay posición reciente: se pide una al GPS
    LaunchedEffect(Unit) {
        if (loc == null || System.currentTimeMillis() - (loc?.time ?: 0) > 60_000) {
            withTimeoutOrNull(30_000) { runCatching { context.locationUpdates().first() }.getOrNull() }?.let { loc = it }
        }
    }
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) Safety.set(context, settings.copy(fallDetection = true))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Mi posición", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val l = loc
            if (l == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Buscando señal GPS… sal a cielo abierto si tarda.")
                }
            } else {
                PositionCard(l)
            }

            Button(
                onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Text("  Llamar al 112", style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        val text = l?.let { Safety.message(it) } ?: return@FilledTonalButton
                        // Abre la app de SMS con el texto puesto: tú decides si lo mandas
                        val to = if (settings.hasContact) settings.contactPhone else ""
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$to")).putExtra("sms_body", text))
                    },
                    enabled = l != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Sms, contentDescription = null, Modifier.size(18.dp))
                    Text(if (settings.hasContact) "  SMS a ${settings.contactName.ifBlank { "contacto" }}" else "  Por SMS")
                }
                FilledTonalButton(
                    onClick = {
                        val text = l?.let { Safety.message(it) } ?: return@FilledTonalButton
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Mandar mi posición"))
                    },
                    enabled = l != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, Modifier.size(18.dp))
                    Text("  Compartir")
                }
            }

            HorizontalDivider()
            Text("Contacto de emergencia", style = MaterialTheme.typography.titleMedium)
            var name by remember { mutableStateOf(settings.contactName) }
            var phone by remember { mutableStateOf(settings.contactPhone) }
            OutlinedTextField(
                value = name, onValueChange = { name = it; Safety.set(context, settings.copy(contactName = it)) },
                label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = phone, onValueChange = { phone = it; Safety.set(context, settings.copy(contactPhone = it)) },
                label = { Text("Teléfono") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Detección de caídas", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Mientras grabas o navegas: si te caes y no te mueves, suena una alarma y, si no respondes en " +
                            "${FallMonitor.COUNTDOWN_S} s, manda tu posición por SMS a tu contacto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.fallDetection,
                    enabled = settings.hasContact,
                    onCheckedChange = { on ->
                        if (on && !Safety.canSendSms(context)) askSms.launch(Manifest.permission.SEND_SMS)
                        else Safety.set(context, settings.copy(fallDetection = on))
                    },
                )
            }
            if (!settings.hasContact) {
                Text(
                    "Pon un teléfono de contacto para activar la detección de caídas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PositionCard(l: Location) {
    val utm = runCatching { Coordinates.toUtm(l.latitude, l.longitude) }.getOrNull()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            utm?.let {
                Text("UTM (huso ${it.zone}${it.band})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "X ${it.easting.roundToInt()}   Y ${it.northing.roundToInt()}",
                    style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                )
            }
            Text("Grados", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Coordinates.decimal(l.latitude, l.longitude), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
            Text(Coordinates.dms(l.latitude, l.longitude), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            Text(
                listOfNotNull(
                    if (l.hasAltitude()) "Altitud ${l.altitude.roundToInt()} m" else null,
                    if (l.hasAccuracy()) "precisión ±${l.accuracy.roundToInt()} m" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
