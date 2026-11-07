package app.trazo.ride

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.trazo.gpx.ride.RideStats
import app.trazo.importer.ES

internal fun kmh(v: Double) = String.format(ES, "%.1f km/h", v)
internal fun kmStr(m: Double) = String.format(ES, "%.2f km", m / 1000)

/** Permisos para grabar: ubicación y, en Android 13+, notificaciones (la del servicio). */
internal fun ridePermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

internal fun Context.hasLocation() =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

/** Cuadrícula 3×2 con los datos de una salida. */
@Composable
fun RideStatsGrid(stats: RideStats, currentKmh: Double? = null, elapsedMs: Long? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Big("En movimiento", formatDuration(stats.movingMs), Modifier.weight(1f))
            Big("Distancia", kmStr(stats.distanceMeters), Modifier.weight(1f))
            if (currentKmh != null) Big("Ahora", kmh(currentKmh), Modifier.weight(1f))
            else Big("Total", formatDuration(elapsedMs ?: stats.totalMs), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Big("Media", kmh(stats.avgMovingKmh), Modifier.weight(1f))
            Big("Máxima", kmh(stats.maxKmh), Modifier.weight(1f))
            Big("Desnivel +", String.format(ES, "%.0f m", stats.gainMeters), Modifier.weight(1f))
        }
        // Solo si llevabas sensores
        val sensors = listOfNotNull(
            stats.avgHr?.let { "Pulso medio" to "$it ppm" },
            stats.maxHr?.let { "Pulso máx." to "$it ppm" },
            stats.avgCadence?.let { "Cadencia" to "$it rpm" },
            stats.avgPower?.let { "Potencia" to "$it W" },
        )
        if (sensors.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sensors.take(3).forEach { (l, v) -> Big(l, v, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Big(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
