package app.trazo.gpx.plan

import app.trazo.gpx.Activity
import app.trazo.gpx.GeoPoint
import java.util.Locale

/**
 * Enrutador BRouter (gratuito, datos de OSM). Dados unos puntos, devuelve la
 * ruta por pistas y senderos según el perfil de la actividad, en GPX y con
 * altitud. Servidor público: brouter.de.
 */
object BRouter {

    const val ENDPOINT = "https://brouter.de/brouter"

    fun url(points: List<GeoPoint>, activity: Activity): String {
        require(points.size >= 2) { "Hacen falta al menos dos puntos" }
        val lonlats = points.joinToString("|") { String.format(Locale.ROOT, "%.6f,%.6f", it.lon, it.lat) }
        return "$ENDPOINT?lonlats=$lonlats&profile=${activity.brouterProfile}&alternativeidx=0&format=gpx&trackname=Trazo"
    }

    /** BRouter responde con texto plano cuando no encuentra ruta; esto lo traduce. */
    fun friendlyError(body: String): String = when {
        "not mapped" in body || "no track found" in body.lowercase() ->
            "No hay camino por ahí. Pon el punto más cerca de una pista o un sendero."
        "operation killed" in body.lowercase() || "timeout" in body.lowercase() ->
            "La ruta es demasiado larga para calcularla de una vez. Añade puntos intermedios."
        else -> "No se pudo calcular la ruta."
    }
}
