package app.trazo.safety

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import app.trazo.R
import app.trazo.gpx.safety.FallDetector
import app.trazo.ride.RideService
import app.trazo.ride.RideSession
import kotlin.math.sqrt

/**
 * Vigila el acelerómetro mientras grabas o navegas (vive dentro del
 * servicio de grabación). Si detecta una caída, suena y vibra con una
 * notificación "¿Estás bien?"; si en [COUNTDOWN_S] segundos no tocas
 * "Estoy bien", manda tu posición por SMS al contacto de emergencia.
 */
class FallMonitor(private val context: Context) : SensorEventListener {

    private val detector = FallDetector()
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var countdown = 0
    private var running = false

    fun start() {
        if (!Safety.settings.value.fallDetection) return
        val accel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensors.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        running = true
    }

    fun stop() {
        if (running) sensors.unregisterListener(this)
        running = false
        cancelAlarm()
    }

    override fun onSensorChanged(e: SensorEvent) {
        RideSession.lastLocation?.let { if (it.hasSpeed()) detector.onSpeed(it.speed.toDouble()) }
        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
        val event = detector.onSample(System.currentTimeMillis(), sqrt((x * x + y * y + z * z).toDouble()))
        if (event == FallDetector.Event.FALL && countdown == 0) startAlarm()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun startAlarm() {
        countdown = COUNTDOWN_S
        context.getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0))
        tick()
    }

    private fun tick() {
        if (countdown <= 0) return
        notify("¿Estás bien?", "Has podido caerte. Aviso a tu contacto en $countdown s si no respondes.", ongoing = true)
        countdown--
        if (countdown == 0) {
            sendHelp()
        } else {
            handler.postDelayed(::tick, 1_000)
        }
    }

    private fun sendHelp() {
        context.getSystemService(Vibrator::class.java)?.cancel()
        val loc = RideSession.lastLocation
        val sent = loc != null && Safety.sendSms(context, Safety.message(loc, "Trazo ha detectado una posible caída y no respondo."))
        notify(
            if (sent) "Aviso enviado" else "No se pudo avisar",
            when {
                sent -> "Se ha mandado tu posición por SMS a ${Safety.settings.value.contactName.ifBlank { Safety.settings.value.contactPhone }}."
                loc == null -> "No hay posición del GPS todavía. Llama al 112 si necesitas ayuda."
                else -> "Falta el contacto de emergencia o el permiso de SMS. Llama al 112 si necesitas ayuda."
            },
            ongoing = false,
        )
    }

    /** "Estoy bien": para la cuenta atrás y la vibración. */
    fun cancelAlarm() {
        handler.removeCallbacksAndMessages(null)
        countdown = 0
        context.getSystemService(Vibrator::class.java)?.cancel()
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        detector.reset()
    }

    private fun notify(title: String, text: String, ongoing: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Caídas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Aviso de posible caída y cuenta atrás"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
            }
        )
        val ok = PendingIntent.getService(
            context, 1, Intent(context, RideService::class.java).setAction(RideService.ACTION_IM_OK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_ride)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
        if (ongoing) b.addAction(0, "Estoy bien", ok)
        nm.notify(NOTIFICATION_ID, b.build())
    }

    companion object {
        const val COUNTDOWN_S = 60
        private const val CHANNEL = "falls"
        private const val NOTIFICATION_ID = 7
    }
}
