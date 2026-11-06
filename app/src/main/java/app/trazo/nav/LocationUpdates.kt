package app.trazo.nav

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Posiciones del GPS con el LocationManager del sistema: funciona también
 * en ROMs sin servicios de Google. Si el GPS está apagado usa la red.
 */
@SuppressLint("MissingPermission")
fun Context.locationUpdates(intervalMs: Long = 1_000): Flow<Location> = callbackFlow {
    val lm = getSystemService(LocationManager::class.java)
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            trySend(location)
        }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .firstOrNull { lm.isProviderEnabled(it) }
    if (provider == null) {
        close(IllegalStateException("Activa la ubicación del móvil"))
    } else {
        lm.requestLocationUpdates(provider, intervalMs, 0f, listener, Looper.getMainLooper())
    }
    awaitClose { lm.removeUpdates(listener) }
}
