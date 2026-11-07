package app.trazo.safety

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import app.trazo.gpx.safety.Coordinates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Contacto de emergencia y detección de caídas, guardados en el móvil. */
data class SafetySettings(
    val contactName: String = "",
    val contactPhone: String = "",
    val fallDetection: Boolean = false,
) {
    val hasContact get() = contactPhone.count { it.isDigit() } >= 6
}

object Safety {
    private const val PREFS = "trazo"
    private const val TAG = "TrazoSafety"
    private val _settings = MutableStateFlow(SafetySettings())
    val settings: StateFlow<SafetySettings> = _settings.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _settings.value = SafetySettings(
            p.getString("sos_name", "") ?: "",
            p.getString("sos_phone", "") ?: "",
            p.getBoolean("sos_fall", false),
        )
    }

    fun set(context: Context, s: SafetySettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("sos_name", s.contactName)
            .putString("sos_phone", s.contactPhone)
            .putBoolean("sos_fall", s.fallDetection)
            .apply()
        _settings.value = s
    }

    fun canSendSms(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun message(loc: Location, reason: String? = null): String = Coordinates.sosMessage(
        loc.latitude, loc.longitude,
        if (loc.hasAltitude()) loc.altitude else null,
        if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
        DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(loc.time).atZone(ZoneId.systemDefault())),
        reason,
    )

    /**
     * Manda el SMS al contacto sin abrir nada (para la detección de caídas,
     * cuando no puedes tocar el móvil). Devuelve false si no se pudo.
     */
    fun sendSms(context: Context, text: String): Boolean {
        val phone = _settings.value.contactPhone
        if (!_settings.value.hasContact || !canSendSms(context)) return false
        return runCatching {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            sms.sendMultipartTextMessage(phone, null, sms.divideMessage(text), null, null)
            true
        }.getOrElse { Log.w(TAG, "No se pudo mandar el SMS: ${it.message}"); false }
    }
}
