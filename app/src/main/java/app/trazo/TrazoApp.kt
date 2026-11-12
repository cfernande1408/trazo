package app.trazo

import android.app.Application
import app.trazo.ui.AppLanguage

/** Lo primero al arrancar el proceso (también para el servicio de grabar): el idioma. */
class TrazoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLanguage.init(this)
    }
}
