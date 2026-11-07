package app.trazo.ride

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.trazo.MainActivity
import app.trazo.R
import app.trazo.safety.FallMonitor
import app.trazo.sensors.BleSensors
import java.util.Locale

/**
 * Servicio en primer plano que mantiene el GPS encendido mientras grabas o
 * navegas, aunque bloquees el móvil. Muestra una notificación fija con la
 * distancia y el tiempo, que se actualiza cada pocos segundos.
 */
class RideService : Service() {

    private var lastNotifyMs = 0L
    private var falls: FallMonitor? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            RideSession.onLocation(location)
            val now = System.currentTimeMillis()
            if (now - lastNotifyMs > 5_000) {
                lastNotifyMs = now
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
            }
        }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Salida en curso", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mientras grabas o navegas una ruta"
            }
        )
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        startLocation()
        falls = FallMonitor(this).also { it.start() }
        BleSensors.connectSaved(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_IM_OK) falls?.cancelAlarm()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        val lm = getSystemService(LocationManager::class.java)
        // GPS para grabar con precisión; la red solo hasta que el GPS coja señal
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { it in lm.allProviders && runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            .forEach { lm.requestLocationUpdates(it, 1_000L, 0f, listener, Looper.getMainLooper()) }
    }

    override fun onDestroy() {
        getSystemService(LocationManager::class.java).removeUpdates(listener)
        falls?.stop()
        BleSensors.disconnectAll()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val s = RideSession.state.value
        val text = String.format(
            Locale.forLanguageTag("es-ES"), "%.1f km · %s%s",
            s.stats.distanceMeters / 1000, formatDuration(s.stats.movingMs), if (s.paused) " · en pausa" else "",
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_ride)
            .setContentTitle(s.routeName?.let { "Navegando · $it" } ?: if (s.activity == app.trazo.gpx.Activity.HIKE) "Grabando excursión" else "Grabando salida")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL = "ride"
        /** Botón "Estoy bien" de la notificación de caída. */
        const val ACTION_IM_OK = "app.trazo.IM_OK"
        private const val NOTIFICATION_ID = 1
    }
}

/** 1:05:09 o 12:34 */
fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
}
