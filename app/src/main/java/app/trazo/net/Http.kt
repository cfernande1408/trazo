package app.trazo.net

import app.trazo.gpx.Gpx
import app.trazo.gpx.Track
import app.trazo.gpx.osm.ElevationFill
import app.trazo.gpx.osm.Json
import app.trazo.gpx.osm.Overpass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
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
                c.readTimeout = 90_000
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", USER_AGENT)
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                if (body != null) {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = c.responseCode
                if (code == 429 || code == 504) throw IOException("El servidor de mapas está saturado, prueba en un rato")
                if (code !in 200..299) throw IOException("Error del servidor ($code)")
                c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                c.disconnect()
            }
        }

    suspend fun overpass(query: String): String =
        postForm(Overpass.ENDPOINT, "data=" + URLEncoder.encode(query, "UTF-8"))

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
