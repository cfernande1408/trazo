package app.trazo.gpx.osm

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.TrackStatsCalculator
import kotlin.math.cos

/** Si una ruta acaba donde empieza o no, como el filtro de Wikiloc. */
enum class RouteShape(val label: String) {
    LOOP("Circular"),
    LINEAR("Lineal"),
    /** Se sale de la zona que se ha cargado y no se puede saber. */
    UNKNOWN(""),
}

object RouteShapes {

    /** Dos tramos que acaban a menos de esto se dan por unidos. */
    private const val JOIN_M = 60.0
    /** Un final a menos de esto del borde de la zona puede ser solo el recorte. */
    private const val BORDER_M = 150.0

    /**
     * Forma de una ruta por su trazado: [lines] son sus tramos (sin ordenar,
     * como los da Waymarked Trails) recortados a [area].
     *
     * Se cuentan los finales sueltos, los que no tocan otro tramo:
     * - ninguno: circular;
     * - uno: circular con un tramo de acceso de ida y vuelta (un "chupachups");
     * - dos o más: lineal;
     * - si alguno cae en el borde de [area], la ruta sigue fuera y no se sabe.
     */
    fun of(lines: List<List<GeoPoint>>, area: Bbox? = null): RouteShape {
        val ls = lines.filter { it.size >= 2 }
        if (ls.isEmpty()) return RouteShape.UNKNOWN
        val free = mutableListOf<GeoPoint>()
        for ((i, line) in ls.withIndex()) {
            val length = (1 until line.size).sumOf { TrackStatsCalculator.haversineMeters(line[it - 1], line[it]) }
            for ((end, other) in listOf(line.first() to line.last(), line.last() to line.first())) {
                // Un tramo que se cierra sobre sí mismo
                val closes = length > 3 * JOIN_M && TrackStatsCalculator.haversineMeters(end, other) <= JOIN_M
                val touches = closes || ls.withIndex().any { (j, l) -> j != i && Difficulty.distanceToLine(end, l) <= JOIN_M }
                if (!touches) free += end
            }
        }
        if (area != null && free.any { nearBorder(it, area) }) return RouteShape.UNKNOWN
        return if (free.size <= 1) RouteShape.LOOP else RouteShape.LINEAR
    }

    /** Lo que dice la etiqueta `roundtrip` de OSM, si la tiene. */
    fun fromTag(roundtrip: String?): RouteShape = when (roundtrip?.lowercase()) {
        "yes" -> RouteShape.LOOP
        "no" -> RouteShape.LINEAR
        else -> RouteShape.UNKNOWN
    }

    private fun nearBorder(p: GeoPoint, b: Bbox): Boolean {
        val mLat = 111_195.0
        val mLon = 111_195.0 * cos(Math.toRadians(p.lat))
        return (p.lat - b.south) * mLat < BORDER_M || (b.north - p.lat) * mLat < BORDER_M ||
            (p.lon - b.west) * mLon < BORDER_M || (b.east - p.lon) * mLon < BORDER_M
    }
}
