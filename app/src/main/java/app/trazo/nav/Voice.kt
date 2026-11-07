package app.trazo.nav

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Voz del sistema en español para los avisos de navegación. */
class Voice(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private val pending = mutableListOf<String>()

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.forLanguageTag("es-ES")
        ready = true
        pending.forEach(::say)
        pending.clear()
    }

    fun say(text: String) {
        if (!ready) { pending += text; return }
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "trazo-${text.hashCode()}")
    }

    fun release() {
        tts.stop()
        tts.shutdown()
    }
}
