package app.trazo.plan

import android.content.Context
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.Gpx
import app.trazo.gpx.GpxParser
import app.trazo.gpx.TrackStatsCalculator
import app.trazo.gpx.ride.GpxWriter
import java.io.File

data class SavedRoute(
    val file: File,
    val name: String,
    val distanceMeters: Double,
    val gainMeters: Double,
)

/**
 * "Tus rutas": las que creas con el planificador o guardas desde Explorar
 * y Wikiloc. GPX en la memoria interna de la app (carpeta routes/).
 */
object RoutesLibrary {

    private fun dir(context: Context) = File(context.filesDir, "routes").apply { mkdirs() }

    fun save(context: Context, name: String, segments: List<List<GeoPoint>>, description: String? = null): File {
        val slug = name.lowercase()
            .replace(Regex("[^a-z0-9áéíóúñü]+"), "-")
            .trim('-')
            .take(40)
            .ifEmpty { "ruta" }
        var file = File(dir(context), "$slug.gpx")
        var n = 2
        while (file.exists()) file = File(dir(context), "$slug-${n++}.gpx")
        file.writeText(GpxWriter.writeRoute(name, segments, description))
        return file
    }

    fun save(context: Context, gpx: Gpx): File =
        save(context, gpx.name?.removePrefix("Wikiloc - ") ?: L("Ruta", "Route"), gpx.segments, gpx.description)

    fun open(file: File): Gpx = GpxParser.parse(file.readText())

    /** Más recientes primero. */
    fun list(context: Context): List<SavedRoute> =
        dir(context).listFiles { f -> f.extension == "gpx" }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .mapNotNull { f ->
                runCatching {
                    val gpx = open(f)
                    val stats = TrackStatsCalculator.compute(gpx.segments)
                    SavedRoute(f, gpx.name ?: f.nameWithoutExtension, stats.distanceMeters, stats.elevationGainMeters)
                }.getOrNull()
            }

    /** La ruta guardada con esa huella (la de su mapa sin conexión), si está. */
    fun findByKey(context: Context, key: String): Gpx? =
        dir(context).listFiles { f -> f.extension == "gpx" }.orEmpty().firstNotNullOfOrNull { f ->
            runCatching { open(f) }.getOrNull()?.takeIf { app.trazo.gpx.offline.OfflineArea.routeKey(it.segments) == key }
        }

    /** Guarda la ruta si aún no está (por su huella). */
    fun saveIfMissing(context: Context, gpx: Gpx) {
        if (findByKey(context, app.trazo.gpx.offline.OfflineArea.routeKey(gpx.segments)) == null) save(context, gpx)
    }

    fun delete(route: SavedRoute) {
        route.file.delete()
    }
}
