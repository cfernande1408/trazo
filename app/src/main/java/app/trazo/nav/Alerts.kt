package app.trazo.nav

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Avisos que se notan en la bici: vibración larga y pitido. */
class Alerts(context: Context) {

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull()

    fun offRoute() {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 200, 600), -1))
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
    }

    fun backOnRoute() {
        vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 200)
    }

    fun release() {
        tone?.release()
    }
}
