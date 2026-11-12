package app.trazo.community

import app.trazo.gpx.i18n.L
import android.content.Context
import app.trazo.BuildConfig
import app.trazo.gpx.osm.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente mínimo de Supabase: sesión anónima (sin registro, un usuario por
 * móvil) y peticiones a la API REST. La URL y la clave pública vienen de
 * BuildConfig; si faltan, la comunidad está apagada.
 */
object Supabase {
    private const val PREFS = "trazo"
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val lock = Mutex()
    private var access: String? = null
    private var expiresAtMs = 0L

    val configured: Boolean get() = url.startsWith("https://") && key.isNotBlank()

    class Error(message: String) : IOException(message)

    private suspend fun token(context: Context): String = lock.withLock {
        access?.takeIf { System.currentTimeMillis() < expiresAtMs - 60_000 }?.let { return it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val refresh = prefs.getString("sb_refresh", null)
        // Renovar la sesión guardada o, la primera vez, crear un usuario anónimo
        val (code, body) = if (refresh != null) {
            raw("POST", "/auth/v1/token?grant_type=refresh_token", """{"refresh_token":"$refresh"}""", null)
        } else {
            raw("POST", "/auth/v1/signup", "{}", null)
        }
        if (code !in 200..299) {
            prefs.edit().remove("sb_refresh").apply()
            throw Error(if (refresh != null) L("La sesión ha caducado, vuelve a intentarlo.", "The session expired, try again.") else L("No se pudo entrar en la comunidad ($code).", "Could not sign in to the community ($code)."))
        }
        val m = Json.parse(body) as Map<*, *>
        val t = m["access_token"] as String
        access = t
        expiresAtMs = System.currentTimeMillis() + ((m["expires_in"] as? Double)?.toLong() ?: 3600) * 1000
        (m["refresh_token"] as? String)?.let { prefs.edit().putString("sb_refresh", it).apply() }
        t
    }

    /** Petición autenticada; devuelve el cuerpo o lanza [Error] con un mensaje para el usuario. */
    suspend fun request(context: Context, method: String, path: String, body: String? = null): String {
        if (!configured) throw Error(L("La comunidad no está configurada en esta versión.", "The community is not set up in this version."))
        val (code, text) = raw(method, path, body, token(context))
        if (code == 401) { access = null; return raw(method, path, body, token(context)).second }
        if (code !in 200..299) throw Error(friendly(code, text))
        return text
    }

    private fun friendly(code: Int, body: String) = when {
        code == 409 || body.contains("duplicate key") -> L("Ya habías valorado esta ruta.", "You had already rated this route.")
        code == 429 -> L("Demasiadas peticiones seguidas. Espera un poco.", "Too many requests in a row. Wait a moment.")
        code >= 500 -> L("El servidor de la comunidad no responde. Inténtalo luego.", "The community server is not responding. Try later.")
        else -> L("No se pudo guardar ($code).", "Could not save ($code).")
    }

    private suspend fun raw(method: String, path: String, body: String?, bearer: String?): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val c = URL(url + path).openConnection() as HttpURLConnection
            try {
                c.requestMethod = method
                c.connectTimeout = 15_000
                c.readTimeout = 20_000
                c.setRequestProperty("apikey", key)
                c.setRequestProperty("Authorization", "Bearer ${bearer ?: key}")
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("Prefer", "return=representation")
                if (body != null) {
                    c.doOutput = true
                    c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                code to (stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
            } finally {
                c.disconnect()
            }
        }
}
