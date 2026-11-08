package app.trazo.importer

import android.content.Context
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.offline.OfflineArea
import app.trazo.gpx.osm.Surface
import app.trazo.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Los caminos de OpenStreetMap junto a una ruta, para la dificultad y para
 * "Por dónde va": una sola consulta a Overpass para las dos tarjetas (antes
 * eran dos a la vez y se estorbaban) y guardada en el móvil, así que al
 * volver a abrir la ruta sale al momento.
 */
internal object RouteOsm {

    /** Los caminos cambian poco: un mes guardados. */
    private const val MAX_AGE_MS = 30L * 24 * 3600 * 1000

    private val lock = Mutex()
    private var lastKey: String? = null
    private var lastJson: String? = null

    /** El JSON de Overpass con todos los caminos (`highway`) pegados a la ruta, con sus etiquetas. */
    suspend fun ways(context: Context, segments: List<List<GeoPoint>>): String = lock.withLock {
        val key = OfflineArea.routeKey(segments)
        if (key == lastKey) return@withLock lastJson!!
        val file = File(context.cacheDir, "osm-ways/$key.json")
        val cached = withContext(Dispatchers.IO) {
            file.takeIf { it.isFile && it.length() > 0 && System.currentTimeMillis() - it.lastModified() < MAX_AGE_MS }?.readText()
        }
        val json = cached ?: Http.overpass(Surface.query(segments)).also { fresh ->
            withContext(Dispatchers.IO) {
                runCatching {
                    file.parentFile?.mkdirs()
                    file.writeText(fresh)
                }
            }
        }
        lastKey = key
        lastJson = json
        json
    }
}
