package app.trazo.gpx.community

import app.trazo.gpx.Gpx
import app.trazo.gpx.GeoJson
import app.trazo.gpx.offline.OfflineArea
import app.trazo.gpx.osm.Bbox
import app.trazo.gpx.osm.Json
import java.net.URLEncoder
import java.util.Locale

/** Avisos en el camino, con lo que suelen durar antes de caducar solos. */
enum class HazardKind(val label: String, val days: Int) {
    ICE("Hielo", 2),
    SNOW("Nieve", 4),
    MUD("Barro", 4),
    FALLEN_TREE("Árbol caído", 21),
    CLOSED("Camino cortado", 30),
    WATER("Arroyo crecido", 3),
    LIVESTOCK("Ganado o perros", 1),
    HUNTING("Batida de caza", 1),
    OTHER("Otro", 3),
}

data class Hazard(
    val id: Long,
    val kind: HazardKind,
    val lat: Double,
    val lon: Double,
    val note: String?,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    /** Gente que ha confirmado que sigue ahí / que ya no está. */
    val confirms: Int = 0,
    val gone: Int = 0,
) {
    /** Se oculta si más gente dice que ya no está que la que lo confirma (contando a quien lo puso). */
    val active get() = gone <= confirms
}

data class Review(
    val id: Long,
    val routeKey: String,
    val rating: Int,
    val comment: String?,
    val author: String?,
    val activity: String?,
    val createdAtMs: Long,
)

data class RatingSummary(val average: Double, val count: Int, val byStars: Map<Int, Int>)

/**
 * Comunidad de Trazo sobre Supabase (PostgREST): valoraciones de rutas y
 * avisos en el mapa. Aquí solo las URLs, el JSON y las reglas; el cliente
 * HTTP está en la app. El esquema de la base está en supabase/schema.sql.
 */
object Community {

    /**
     * Clave para juntar las valoraciones de una misma ruta: el id de
     * Wikiloc si viene de allí, el de OSM si es de Explorar y, si no, la
     * huella del trazado.
     */
    fun reviewKey(gpx: Gpx): String {
        gpx.links.firstNotNullOfOrNull { Regex("wikiloc\\.com/.*-(\\d+)(?:$|[/?#])").find(it)?.groupValues?.get(1) }
            ?.let { return "wikiloc:$it" }
        gpx.links.firstNotNullOfOrNull { Regex("openstreetmap\\.org/relation/(\\d+)").find(it)?.groupValues?.get(1) }
            ?.let { return "osm:$it" }
        return "trazo:" + OfflineArea.routeKey(gpx.segments)
    }

    fun expiresAt(kind: HazardKind, nowMs: Long) = nowMs + kind.days * 86_400_000L

    fun summary(reviews: List<Review>): RatingSummary? {
        if (reviews.isEmpty()) return null
        return RatingSummary(reviews.map { it.rating }.average(), reviews.size, reviews.groupingBy { it.rating }.eachCount())
    }

    // ── URLs de PostgREST ────────────────────────────────────────────────

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun f(v: Double) = String.format(Locale.ROOT, "%.5f", v)

    fun reviewsPath(routeKey: String) =
        "/rest/v1/route_reviews?route_key=eq.${enc(routeKey)}&select=*&order=created_at.desc&limit=50"

    fun hazardsPath(b: Bbox, nowIso: String) =
        "/rest/v1/hazards?select=*&lat=gte.${f(b.south)}&lat=lte.${f(b.north)}&lon=gte.${f(b.west)}&lon=lte.${f(b.east)}" +
            "&expires_at=gt.${enc(nowIso)}&order=created_at.desc&limit=200"

    fun reviewBody(routeKey: String, rating: Int, comment: String?, author: String?, activity: String): String {
        require(rating in 1..5)
        return """{"route_key":"${GeoJson.escape(routeKey)}","rating":$rating,""" +
            """"comment":${comment?.takeIf { it.isNotBlank() }?.let { "\"${GeoJson.escape(it.trim().take(1000))}\"" } ?: "null"},""" +
            """"author":${author?.takeIf { it.isNotBlank() }?.let { "\"${GeoJson.escape(it.trim().take(40))}\"" } ?: "null"},""" +
            """"activity":"$activity"}"""
    }

    fun hazardBody(kind: HazardKind, lat: Double, lon: Double, note: String?, expiresIso: String): String =
        """{"kind":"${kind.name}","lat":${f(lat)},"lon":${f(lon)},""" +
            """"note":${note?.takeIf { it.isNotBlank() }?.let { "\"${GeoJson.escape(it.trim().take(300))}\"" } ?: "null"},""" +
            """"expires_at":"$expiresIso"}"""

    // ── Lectura del JSON ─────────────────────────────────────────────────

    fun parseReviews(json: String): List<Review> = rows(json).mapNotNull { m ->
        Review(
            id = (m["id"] as? Double)?.toLong() ?: return@mapNotNull null,
            routeKey = m["route_key"] as? String ?: return@mapNotNull null,
            rating = (m["rating"] as? Double)?.toInt()?.takeIf { it in 1..5 } ?: return@mapNotNull null,
            comment = m["comment"] as? String,
            author = m["author"] as? String,
            activity = m["activity"] as? String,
            createdAtMs = time(m["created_at"]),
        )
    }

    fun parseHazards(json: String): List<Hazard> = rows(json).mapNotNull { m ->
        Hazard(
            id = (m["id"] as? Double)?.toLong() ?: return@mapNotNull null,
            kind = HazardKind.entries.firstOrNull { it.name == m["kind"] } ?: HazardKind.OTHER,
            lat = m["lat"] as? Double ?: return@mapNotNull null,
            lon = m["lon"] as? Double ?: return@mapNotNull null,
            note = m["note"] as? String,
            createdAtMs = time(m["created_at"]),
            expiresAtMs = time(m["expires_at"]),
            confirms = (m["confirms"] as? Double)?.toInt() ?: 0,
            gone = (m["gone"] as? Double)?.toInt() ?: 0,
        )
    }

    private fun rows(json: String): List<Map<*, *>> = (Json.parse(json) as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()

    /** Fechas de Postgres ("2026-11-07T10:20:30.123456+00:00") a ms. */
    internal fun time(v: Any?): Long {
        val s = v as? String ?: return 0
        return runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            .recoverCatching { java.time.Instant.parse(s).toEpochMilli() }
            .getOrDefault(0)
    }

    /** Avisos como GeoJSON para el mapa (propiedad "k" con el tipo). */
    fun hazardsGeoJson(hazards: List<Hazard>): String =
        hazards.filter { it.active }.joinToString(prefix = """{"type":"FeatureCollection","features":[""", postfix = "]}") { h ->
            """{"type":"Feature","properties":{"id":${h.id},"k":"${h.kind.name}","name":"${GeoJson.escape(h.kind.label)}"},""" +
                """"geometry":{"type":"Point","coordinates":[${f(h.lon)},${f(h.lat)}]}}"""
        }
}
