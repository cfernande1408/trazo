package app.trazo.nav

import app.trazo.gpx.i18n.L
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Una última posición conocida de hace más de esto no sirve para empezar. */
private const val MAX_LAST_KNOWN_AGE_MS = 2 * 60 * 1000L

/**
 * Posiciones del LocationManager del sistema (vale sin servicios de Google).
 * Escucha todas las fuentes activas —fusionada, GPS y red— para tener
 * posición también donde el GPS tarda, y empieza con la última conocida si
 * es reciente. Cada posición trae su precisión; quien la usa decide.
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

    val candidates = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
    }
    val providers = candidates.filter { p -> p in lm.allProviders && runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }

    if (providers.isEmpty()) {
        close(IllegalStateException(L("Activa la ubicación del móvil", "Turn on your phone's location")))
    } else {
        val now = SystemClock.elapsedRealtimeNanos()
        providers
            .mapNotNull { lm.getLastKnownLocation(it) }
            .filter { (now - it.elapsedRealtimeNanos) / 1_000_000 < MAX_LAST_KNOWN_AGE_MS }
            .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
            ?.let { trySend(it) }
        providers.forEach { lm.requestLocationUpdates(it, intervalMs, 0f, listener, Looper.getMainLooper()) }
    }
    awaitClose { lm.removeUpdates(listener) }
}
