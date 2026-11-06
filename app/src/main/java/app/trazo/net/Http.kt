package app.trazo.net

import app.trazo.gpx.Gpx
import app.trazo.gpx.Track
import app.trazo.gpx.osm.ElevationFill
import app.trazo.gpx.osm.Json
import app.trazo.gpx.osm.Overpass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
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

    /**
     * Prueba cada servidor con reintentos de espera creciente (1,5 s, 3 s)
     * cuando está saturado, como pide Overpass. Si todos fallan, el mensaje
     * dice si fue por saturación o por falta de conexión.
     */
    suspend fun overpass(query: String): String {
        val form = "data=" + URLEncoder.encode(query, "UTF-8")
        var last: Exception? = null
        var anyBusy = false
        for (endpoint in Overpass.ENDPOINTS) {
            var wait = 1_500L
            for (attempt in 0..2) {
                try {
                    return postForm(endpoint, form)
                } catch (e: BusyException) {
                    anyBusy = true
                    last = e
                    if (attempt < 2) { delay(wait); wait *= 2 }
                } catch (e: IOException) {
                    last = e
                    break // sin conexión con este servidor: al siguiente
                }
            }
        }
        val msg = if (anyBusy) "Los servidores de rutas de OpenStreetMap están saturados. Prueba en un par de minutos." else friendly(last)
        throw IOException(msg, last)
    }

    /** Mensaje para el usuario en lugar del error técnico. */
    fun friendly(e: Throwable?): String = when (e) {
        is BusyException -> "El servidor está saturado. Prueba en un par de minutos."
        is UnknownHostException -> "Sin conexión con el servidor de rutas. Comprueba internet y vuelve a probar."
        is SocketTimeoutException -> "El servidor de rutas tarda demasiado. Prueba en un rato."
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
