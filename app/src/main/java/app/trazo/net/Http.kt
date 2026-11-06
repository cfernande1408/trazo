package app.trazo.net

import app.trazo.gpx.Gpx
import app.trazo.gpx.Track
import app.trazo.gpx.osm.ElevationFill
import app.trazo.gpx.osm.Json
import app.trazo.gpx.osm.Overpass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Peticiones HTTP sencillas sin dependencias extra. */
object Http {

    /** Overpass y Open-Meteo piden identificar a la app. */
    private const val USER_AGENT = "Trazo/0.1 (app personal de navegacion MTB)"

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String = request(url, headers, body = null)

    suspend fun postForm(url: String, form: String): String =
        request(url, mapOf("Content-Type" to "application/x-www-form-urlencoded"), body = form)

    private suspend fun request(url: String, headers: Map<String, String>, body: String?): String =
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15_000
                c.readTimeout = 35_000
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", USER_AGENT)
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                if (body != null) {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = c.responseCode
                if (code == 429 || code in 500..599) throw BusyException(code)
                if (code !in 200..299) throw IOException("Error del servidor ($code)")
                c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                c.disconnect()
            }
        }

    /** El servidor respondió pero está lleno (429/5xx): merece la pena reintentar. */
    class BusyException(val code: Int) : IOException("Servidor saturado ($code)")

    /** Si un servidor no ha contestado en este tiempo, se pregunta también al siguiente. */
    private const val HEDGE_MS = 3_000L

    /**
     * Petición "escalonada": empieza por el servidor principal y, si a los
     * 3 s no ha contestado (o falla), lanza la misma consulta al siguiente
     * espejo sin cancelar la anterior. Gana la primera respuesta buena. Así
     * un servidor lento o saturado no bloquea, sin cargar a todos a la vez.
     */
    suspend fun overpass(query: String): String {
        val form = "data=" + URLEncoder.encode(query, "UTF-8")
        val result = CompletableDeferred<String>()
        val failures = mutableListOf<Exception>()
        val next = Channel<Unit>(Channel.UNLIMITED) // "lanza el siguiente ya" cuando uno falla

        // Fuera de la corrutina que llama: así no hay que esperar a que acaben las peticiones perdedoras
        val launcher = background.launch {
            for ((i, endpoint) in Overpass.ENDPOINTS.withIndex()) {
                if (i > 0) withTimeoutOrNull(HEDGE_MS) { next.receive() }
                if (result.isCompleted) break
                launch {
                    try {
                        result.complete(attempt(endpoint, form))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val all = synchronized(failures) { failures += e; failures.size == Overpass.ENDPOINTS.size }
                        if (all) {
                            val busy = failures.any { it is BusyException }
                            result.completeExceptionally(
                                IOException(
                                    if (busy) "Los servidores de rutas de OpenStreetMap están saturados. Prueba en un par de minutos."
                                    else friendly(failures.last()),
                                    e,
                                )
                            )
                        } else {
                            next.trySend(Unit)
                        }
                    }
                }
            }
        }
        try {
            return result.await()
        } finally {
            launcher.cancel() // cancela también las peticiones que siguen en marcha
        }
    }

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Un servidor, con un reintento si responde que está saturado. */
    private suspend fun attempt(endpoint: String, form: String): String {
        try {
            return postForm(endpoint, form)
        } catch (e: BusyException) {
            delay(1_500)
            return postForm(endpoint, form)
        }
    }

    /** Mensaje para el usuario en lugar del error técnico. */
    fun friendly(e: Throwable?): String = when (e) {
        is BusyException -> "El servidor está saturado. Prueba en un par de minutos."
        is UnknownHostException -> "Sin conexión con el servidor de rutas. Comprueba internet y vuelve a probar."
        is SocketTimeoutException -> "El servidor de rutas tarda demasiado. Prueba en un rato."
        // "Software caused connection abort", "Connection reset"…: el sistema cortó la conexión
        is SocketException -> "Se cortó la conexión con el servidor de rutas."
        is IOException -> when (val cause = e.cause) {
            is SocketException, is UnknownHostException, is SocketTimeoutException -> friendly(cause)
            else -> e.message ?: "No se pudo conectar"
        }
        null -> "No se pudo conectar"
        else -> e.message ?: "No se pudo conectar"
    }

    /**
     * Añade altitud a una ruta que no la trae (las de OSM) con la API de
     * elevación de Open-Meteo, de 100 en 100 puntos. Si falla, devuelve la
     * ruta tal cual: se puede navegar igual, solo sin perfil.
     */
    suspend fun withElevation(gpx: Gpx): Gpx {
        val segs = gpx.segments
        if (segs.any { seg -> seg.any { it.ele != null } }) return gpx
        return runCatching {
            val samples = ElevationFill.sampleIndices(segs)
            val elevations = samples.chunked(100).flatMap { chunk ->
                val lats = chunk.joinToString(",") { (s, j) -> String.format(Locale.ROOT, "%.5f", segs[s][j].lat) }
                val lons = chunk.joinToString(",") { (s, j) -> String.format(Locale.ROOT, "%.5f", segs[s][j].lon) }
                val json = get("https://api.open-meteo.com/v1/elevation?latitude=$lats&longitude=$lons")
                ((Json.parse(json) as Map<*, *>)["elevation"] as List<*>).map { (it as Number).toDouble() }
            }
            val filled = ElevationFill.fill(segs, samples, elevations)
            gpx.copy(tracks = listOf(Track(gpx.name, filled)))
        }.getOrDefault(gpx)
    }
}
