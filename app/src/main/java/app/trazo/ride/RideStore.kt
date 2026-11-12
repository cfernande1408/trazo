package app.trazo.ride

import app.trazo.gpx.i18n.L
import android.content.Context
import app.trazo.gpx.Activity
import app.trazo.gpx.GpxParser
import app.trazo.gpx.Waypoint
import app.trazo.gpx.ride.GpxWriter
import app.trazo.gpx.ride.RideCalculator
import app.trazo.gpx.ride.RideStats
import app.trazo.gpx.ride.TimedPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedRide(
    val file: File,
    val name: String,
    val startedAtMs: Long,
    val stats: RideStats,
    val points: List<List<TimedPoint>>,
    val activity: Activity,
    /** Puntos que marcaste al grabar. */
    val marks: List<Waypoint> = emptyList(),
)

/**
 * Las salidas se guardan como GPX en la memoria interna de la app (carpeta
 * rides/). Las de senderismo llevan "-hike" en el nombre del archivo. Las
 * fotos de los puntos marcados van en rides/photos/ y el GPX las enlaza.
 */
object RideStore {

    private fun dir(context: Context) = File(context.filesDir, "rides").apply { mkdirs() }

    /** Archivo nuevo para la foto de un punto marcado. */
    fun newPhoto(context: Context): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(Date())
        return File(File(dir(context), PHOTOS).apply { mkdirs() }, "$stamp.jpg")
    }

    /** Cómo se enlaza la foto desde el GPX. */
    fun photoLink(photo: File) = "$PHOTOS/${photo.name}"

    /** La foto de un punto, si la tiene y sigue en el móvil. */
    fun photo(context: Context, point: Waypoint): File? =
        point.localPhoto?.let { File(dir(context), it) }?.takeIf { it.isFile }

    fun deletePhoto(context: Context, point: Waypoint) {
        photo(context, point)?.delete()
    }

    fun save(
        context: Context,
        name: String,
        points: List<List<TimedPoint>>,
        activity: Activity = Activity.BIKE,
        marks: List<Waypoint> = emptyList(),
    ): File {
        val start = points.first().first().timeMs
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(start))
        val suffix = if (activity == Activity.HIKE) "-hike" else ""
        val file = File(dir(context), "$stamp$suffix.gpx")
        file.writeText(GpxWriter.write(name, points, type = if (activity == Activity.HIKE) "hiking" else "mountain_biking", waypoints = marks))
        return file
    }

    fun load(file: File): SavedRide? = runCatching {
        val activity = if (file.name.contains("-hike")) Activity.HIKE else Activity.BIKE
        val gpx = GpxParser.parse(file.readText())
        val points = RideCalculator.fromGpx(gpx)
        SavedRide(
            file, gpx.name ?: L("Salida", "Ride"), points.first().first().timeMs,
            RideCalculator.compute(points, activity), points, activity, gpx.waypoints,
        )
    }.getOrNull()

    /** Más recientes primero. */
    /** Las [n] salidas más recientes, sin leer las demás. */
    fun recent(context: Context, n: Int): List<SavedRide> =
        dir(context).listFiles { f -> f.extension == "gpx" }.orEmpty()
            .sortedByDescending { it.name }
            .take(n)
            .mapNotNull(::load)

    fun list(context: Context): List<SavedRide> =
        dir(context).listFiles { f -> f.extension == "gpx" }.orEmpty()
            .sortedByDescending { it.name }
            .mapNotNull(::load)

    fun delete(context: Context, ride: SavedRide) {
        ride.marks.forEach { deletePhoto(context, it) }
        ride.file.delete()
    }

    private const val PHOTOS = "photos"
}
