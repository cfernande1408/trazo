package app.trazo.gpx.osm

import app.trazo.gpx.i18n.L
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.RouteGeometry
import app.trazo.gpx.TrackStatsCalculator
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor

/** Por dónde va la ruta: lo que de verdad cambia la salida en bici o a pie. */
enum class SurfaceKind(private val es: String, private val en: String, val color: String) {
    PAVED("Asfalto", "Paved", "#8A8F98"),
    TRACK("Pista", "Track", "#C8963E"),
    PATH("Sendero", "Trail", "#4E8A2E"),
    STEPS("Escaleras", "Steps", "#B5532A"),
    ;

    val label: String get() = L(es, en)
}

data class SurfaceSummary(
    val routeMeters: Double,
    val metersByKind: Map<SurfaceKind, Double>,
) {
    val knownMeters: Double get() = metersByKind.values.sum()
    /** Parte de lo que tiene dato, de 0 a 1. */
    fun share(kind: SurfaceKind): Double = if (knownMeters > 0) (metersByKind[kind] ?: 0.0) / knownMeters else 0.0
}

/** Un camino de OSM ya clasificado. */
data class SurfacedWay(val points: List<GeoPoint>, val kind: SurfaceKind)

/**
 * Superficie de la ruta según OpenStreetMap, como la de Komoot: cuántos km
 * van por asfalto, pista, sendero o escaleras. Se piden los caminos junto al
 * track y se mira por cuál va cada tramo. Vale para cualquier GPX.
 */
object Surface {

    private val PAVED_SURFACES = setOf(
        "asphalt", "concrete", "concrete:plates", "concrete:lanes", "paved", "paving_stones",
        "sett", "cobblestone", "unhewn_cobblestone", "metal", "wood", "chipseal", "brick", "bricks",
    )
    private val UNPAVED_SURFACES = setOf(
        "unpaved", "compacted", "fine_gravel", "gravel", "pebblestone", "rock", "dirt", "earth",
        "ground", "mud", "sand", "grass", "grass_paver", "woodchips", "snow", "ice",
    )
    private val ROADS = setOf(
        "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential",
        "living_street", "service", "pedestrian", "road", "busway",
        "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link",
    )
    private val PATHS = setOf("path", "footway", "bridleway", "cycleway", "via_ferrata")

    /**
     * Qué es un camino según sus etiquetas, o null si no es un camino.
     * Manda la superficie si la tiene; si no, el tipo de vía.
     */
    fun classify(tags: Map<String, String>): SurfaceKind? {
        val highway = tags["highway"] ?: return null
        if (highway == "steps") return SurfaceKind.STEPS
        if (highway !in ROADS && highway !in PATHS && highway != "track") return null
        val surface = tags["surface"]?.substringBefore(';')
        val paved = when {
            surface in PAVED_SURFACES -> true
            surface in UNPAVED_SURFACES -> false
            highway == "track" -> tags["tracktype"] == "grade1"
            else -> null
        }
        return when {
            paved == true -> SurfaceKind.PAVED
            highway == "track" -> SurfaceKind.TRACK
            highway in PATHS -> when {
                // Aceras, pasos de peatones y carriles bici sin dato van asfaltados casi siempre
                paved == null && (tags["footway"] in setOf("sidewalk", "crossing") || highway == "cycleway") -> SurfaceKind.PAVED
                else -> SurfaceKind.PATH
            }
            // Calle o carretera de tierra: a efectos de la salida, pista
            paved == false -> SurfaceKind.TRACK
            else -> SurfaceKind.PAVED
        }
    }

    /**
     * Consulta: caminos en recuadros pequeños a lo largo del track. Mucho más
     * pequeños que en la de dificultad, porque aquí entran todas las calles.
     */
    fun query(segments: List<List<GeoPoint>>, marginMeters: Double = 25.0): String {
        val pts = RouteGeometry.downsample(segments.flatten(), 400)
        val boxes = pts.windowed(5, 4, partialWindows = true).filter { it.isNotEmpty() }.map { chunk ->
            val lat = chunk.map { it.lat }
            val lon = chunk.map { it.lon }
            val dLat = marginMeters / 110_540
            val dLon = marginMeters / (111_320 * cos(Math.toRadians(lat.average())))
            String.format(Locale.ROOT, "%.5f,%.5f,%.5f,%.5f", lat.min() - dLat, lon.min() - dLon, lat.max() + dLat, lon.max() + dLon)
        }
        return "[out:json][timeout:25];(" + boxes.joinToString("") { "way[highway]($it);" } + ");out tags geom;"
    }

    fun parse(json: String): List<SurfacedWay> {
        val root = Json.parse(json) as? Map<*, *> ?: return emptyList()
        return (root["elements"] as? List<*>).orEmpty().mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            if (m["type"] != "way") return@mapNotNull null
            val tags = (m["tags"] as? Map<*, *>).orEmpty()
                .mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }.toMap()
            val kind = classify(tags) ?: return@mapNotNull null
            val geom = (m["geometry"] as? List<*>).orEmpty().mapNotNull { g ->
                val p = g as? Map<*, *> ?: return@mapNotNull null
                GeoPoint(p["lat"] as? Double ?: return@mapNotNull null, p["lon"] as? Double ?: return@mapNotNull null)
            }
            if (geom.size < 2) null else SurfacedWay(geom, kind)
        }
    }

    /**
     * Recorre la ruta tramo a tramo y apunta el tipo del camino más cercano (a
     * menos de [maxMeters]). Los caminos se guardan en una rejilla de ~200 m para
     * no medir contra todas las calles de la ciudad en cada tramo.
     */
    fun summarize(segments: List<List<GeoPoint>>, ways: List<SurfacedWay>, maxMeters: Double = 15.0): SurfaceSummary {
        // En la rejilla van índices: comparar los caminos enteros (con todos sus puntos) es lento
        val grid = HashMap<Long, MutableList<Int>>()
        for ((index, w) in ways.withIndex()) {
            val cells = w.points.map { cell(it) }.toMutableSet()
            // Una calle larga recta puede cruzar celdas sin vértices: se añaden las intermedias
            for (i in 1 until w.points.size) {
                val a = w.points[i - 1]; val b = w.points[i]
                val steps = (TrackStatsCalculator.haversineMeters(a, b) / 100).toInt()
                for (s in 1..steps) {
                    val t = s.toDouble() / (steps + 1)
                    cells += cell(GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t))
                }
            }
            cells.forEach { grid.getOrPut(it) { mutableListOf() } += index }
        }
        val byKind = mutableMapOf<SurfaceKind, Double>()
        var total = 0.0
        // Para no medir dos veces el mismo camino en las celdas vecinas
        val seenAt = IntArray(ways.size) { -1 }
        var step = 0
        for (seg in segments) {
            for (i in 1 until seg.size) {
                val a = seg[i - 1]
                val b = seg[i]
                val d = TrackStatsCalculator.haversineMeters(a, b)
                total += d
                if (d == 0.0 || grid.isEmpty()) continue
                val mid = GeoPoint((a.lat + b.lat) / 2, (a.lon + b.lon) / 2)
                var best: SurfaceKind? = null
                var bestDist = maxMeters
                val (cy, cx) = cellXY(mid)
                step++
                for (dy in -1..1) for (dx in -1..1) {
                    for (w in grid[key(cy + dy, cx + dx)].orEmpty()) {
                        if (seenAt[w] == step) continue
                        seenAt[w] = step
                        val dist = Difficulty.distanceToLine(mid, ways[w].points)
                        if (dist <= bestDist) { bestDist = dist; best = ways[w].kind }
                    }
                }
                best?.let { byKind[it] = (byKind[it] ?: 0.0) + d }
            }
        }
        return SurfaceSummary(total, SurfaceKind.entries.filter { it in byKind }.associateWith { byKind.getValue(it) })
    }

    private const val CELL_DEG = 0.002

    private fun cellXY(p: GeoPoint) = floor(p.lat / CELL_DEG).toLong() to floor(p.lon / CELL_DEG).toLong()
    private fun key(y: Long, x: Long) = (y shl 32) xor (x and 0xffffffffL)
    private fun cell(p: GeoPoint) = cellXY(p).let { (y, x) -> key(y, x) }
}
