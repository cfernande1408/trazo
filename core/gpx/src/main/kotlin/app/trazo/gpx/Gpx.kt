package app.trazo.gpx

/** Punto de un track o de una ruta planificada. */
data class GeoPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val time: String? = null,
)

/** Punto de interés del autor (fuente, mirador, bajada técnica…). */
data class Waypoint(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val name: String? = null,
    val desc: String? = null,
)

/** Un `<trk>` (con sus segmentos) o un `<rte>` convertido a un único segmento. */
data class Track(
    val name: String?,
    val segments: List<List<GeoPoint>>,
)

data class Gpx(
    val name: String?,
    val tracks: List<Track>,
    val waypoints: List<Waypoint>,
    /** Descripción de la ruta escrita por el autor, si el GPX la trae. */
    val description: String? = null,
    /** Enlaces de la ruta (p. ej. su página en Wikiloc). */
    val links: List<String> = emptyList(),
) {
    /** Todos los segmentos de todos los tracks, en orden. */
    val segments: List<List<GeoPoint>> get() = tracks.flatMap { it.segments }

    /** Página de la ruta en Wikiloc, donde están los comentarios. */
    val wikilocUrl: String? get() = links.firstOrNull { "wikiloc." in it }
}

class GpxParseException(message: String) : Exception(message)
