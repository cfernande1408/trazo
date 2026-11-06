package app.trazo.gpx.osm

import java.net.URLEncoder

/** Un lugar encontrado por nombre: un pico, un pueblo, un parking… */
data class Place(
    val name: String,
    /** Dónde está, para distinguir lugares con el mismo nombre. */
    val detail: String,
    val lat: Double,
    val lon: Double,
    val bbox: Bbox?,
    /** Tipo de OSM: peak, village, parking… */
    val kind: String?,
)

/**
 * Búsqueda de lugares con Nominatim (OpenStreetMap). Su política de uso pide
 * como mucho una consulta por segundo y nada de autocompletar en cada
 * letra: se busca al pulsar "Buscar".
 */
object PlaceSearch {

    fun url(query: String, limit: Int = 8): String =
        "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=$limit&accept-language=es&q=" +
            URLEncoder.encode(query.trim(), "UTF-8")

    fun parse(json: String): List<Place> =
        (Json.parse(json) as? List<*>).orEmpty().mapNotNull { r ->
            val m = r as? Map<*, *> ?: return@mapNotNull null
            val lat = (m["lat"] as? String)?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = (m["lon"] as? String)?.toDoubleOrNull() ?: return@mapNotNull null
            val display = m["display_name"] as? String ?: ""
            val name = (m["name"] as? String)?.takeIf { it.isNotBlank() } ?: display.substringBefore(",")
            val bb = (m["boundingbox"] as? List<*>)?.mapNotNull { (it as? String)?.toDoubleOrNull() }
            Place(
                name = name,
                // "Bola del Mundo, Navacerrada, Madrid, España" → "Navacerrada, Madrid, España"
                detail = display.split(", ").drop(1).take(3).joinToString(", "),
                lat = lat, lon = lon,
                bbox = bb?.takeIf { it.size == 4 }?.let { Bbox(it[0], it[2], it[1], it[3]) },
                kind = m["type"] as? String,
            )
        }

    /**
     * Zoom al que conviene ir para ver las rutas de alrededor: un pico o un
     * parking se ven de cerca; una provincia, de lejos pero sin bajar del
     * mínimo con el que Explorar carga rutas.
     */
    fun zoomFor(place: Place, minZoom: Double = 10.0): Double {
        val b = place.bbox ?: return 13.0
        val span = maxOf(b.north - b.south, b.east - b.west)
        return when {
            span < 0.02 -> 13.0
            span < 0.08 -> 12.5
            span < 0.3 -> 11.5
            else -> minZoom + 1
        }
    }
}
