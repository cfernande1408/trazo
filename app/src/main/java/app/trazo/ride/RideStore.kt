package app.trazo.ride

import android.content.Context
import app.trazo.gpx.GpxParser
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
)

/** Las salidas se guardan como GPX en la memoria interna de la app (carpeta rides/). */
object RideStore {

    private fun dir(context: Context) = File(context.filesDir, "rides").apply { mkdirs() }

    fun save(context: Context, name: String, points: List<List<TimedPoint>>): File {
        val start = points.first().first().timeMs
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(start))
        val file = File(dir(context), "$stamp.gpx")
        file.writeText(GpxWriter.write(name, points))
        return file
    }

    fun load(file: File): SavedRide? = runCatching {
        val gpx = GpxParser.parse(file.readText())
        val points = RideCalculator.fromGpx(gpx)
        SavedRide(file, gpx.name ?: "Salida", points.first().first().timeMs, RideCalculator.compute(points), points)
    }.getOrNull()

    /** Más recientes primero. */
    fun list(context: Context): List<SavedRide> =
        dir(context).listFiles { f -> f.extension == "gpx" }.orEmpty()
            .sortedByDescending { it.name }
            .mapNotNull(::load)

    fun delete(ride: SavedRide) {
        ride.file.delete()
    }
}
