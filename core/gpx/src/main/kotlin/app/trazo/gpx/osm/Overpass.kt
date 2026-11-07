package app.trazo.gpx.osm

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.Gpx
import app.trazo.gpx.GpxParseException
import app.trazo.gpx.Track
import java.util.Locale

/** Una ruta MTB de OpenStreetMap encontrada en la búsqueda (sin su geometría). */
data class OsmRouteSummary(
    val id: Long,
    val name: String,
    val ref: String?,
    val network: String?,
    val lat: Double,
    val lon: Double,
    /** Longitud según la etiqueta `distance` de OSM, si la tiene. */
    val distanceKm: Double?,
    val description: String?,
    /** true si OSM la marca como ruta de montaña (route=mtb); false si es ciclista general. */
    val isMtb: Boolean = true,
    /** Circular, lineal o sin saber (si se sale de la zona cargada). */
    val shape: RouteShape = RouteShape.UNKNOWN,
)

/** Consultas a la API Overpass de OpenStreetMap y lectura de sus respuestas. */
object Overpass {

    /** Servidor principal y espejos públicos, por si alguno falla o está saturado. */
    val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )

    private fun f(v: Double) = String.format(Locale.ROOT, "%.5f", v)

    /** Rutas MTB y ciclistas (muchas pistas de montaña están etiquetadas solo como bicycle). */
    fun searchQuery(lat: Double, lon: Double, radiusMeters: Int): String =
        "[out:json][timeout:25];relation[\"route\"~\"^(mtb|bicycle)$\"](around:$radiusMeters,${f(lat)},${f(lon)});out tags center qt 150;"

    /**
     * Rutas que pasan por el recuadro. Mucho más rápida que la búsqueda por
     * radio: el mapa de Explorar pide solo la zona que estás viendo.
     */
    fun bboxQuery(b: Bbox, routes: String = "mtb|bicycle"): String =
        "[out:json][timeout:20];relation[\"route\"~\"^($routes)$\"](${f(b.south)},${f(b.west)},${f(b.north)},${f(b.east)});out tags center qt 200;"

    fun routeQuery(id: Long): String =
        "[out:json][timeout:60];relation($id);out tags;way(r);out geom;"

    fun osmUrl(id: Long) = "https://www.openstreetmap.org/relation/$id"

    fun parseSearch(json: String): List<OsmRouteSummary> =
        elements(json).mapNotNull { e ->
            if (e["type"] != "relation") return@mapNotNull null
            val id = (e["id"] as? Double)?.toLong() ?: return@mapNotNull null
            val center = e["center"] as? Map<*, *> ?: return@mapNotNull null
            val lat = center["lat"] as? Double ?: return@mapNotNull null
            val lon = center["lon"] as? Double ?: return@mapNotNull null
            val tags = e["tags"] as? Map<*, *> ?: emptyMap<String, Any?>()
            OsmRouteSummary(
                id = id,
                name = nameOf(tags, id),
                ref = tags["ref"] as? String,
                network = tags["network"] as? String,
                lat = lat,
                lon = lon,
                distanceKm = (tags["distance"] as? String)?.let(::parseKm),
                description = (tags["description"] ?: tags["note"]) as? String,
                isMtb = tags["route"] != "bicycle",
                shape = RouteShapes.fromTag(tags["roundtrip"] as? String),
            )
        }

    /** Convierte la respuesta de [routeQuery] en un Gpx como si viniera de un archivo. */
    fun parseRoute(json: String, id: Long): Gpx {
        val els = elements(json)
        val tags = els.firstOrNull { it["type"] == "relation" }?.get("tags") as? Map<*, *> ?: emptyMap<String, Any?>()
        val ways = els.filter { it["type"] == "way" }.mapNotNull { w ->
            (w["geometry"] as? List<*>)?.mapNotNull { p ->
                val m = p as? Map<*, *> ?: return@mapNotNull null
                val lat = m["lat"] as? Double ?: return@mapNotNull null
                val lon = m["lon"] as? Double ?: return@mapNotNull null
                GeoPoint(lat, lon)
            }
        }
        val segments = WayStitcher.stitch(ways)
        if (segments.isEmpty()) throw GpxParseException("La ruta no tiene geometría en OpenStreetMap")
        val name = nameOf(tags, id)
        return Gpx(
            name = name,
            tracks = listOf(Track(name, segments)),
            waypoints = emptyList(),
            description = (tags["description"] ?: tags["note"]) as? String,
            links = listOf(osmUrl(id)),
        )
    }

    private fun elements(json: String): List<Map<*, *>> {
        val root = Json.parse(json) as? Map<*, *> ?: throw JsonException("Respuesta de Overpass no válida")
        return (root["elements"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    }

    private fun nameOf(tags: Map<*, *>, id: Long): String =
        (tags["name:es"] ?: tags["name"] ?: tags["ref"]) as? String ?: "Ruta MTB $id"

    /** "25", "25 km", "25,5km" → 25.0 / 25.5 */
    internal fun parseKm(s: String): Double? =
        Regex("""\d+(?:[.,]\d+)?""").find(s)?.value?.replace(',', '.')?.toDoubleOrNull()
}
