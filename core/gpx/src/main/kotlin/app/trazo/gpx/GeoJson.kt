package app.trazo.gpx

/** Un icono en el mapa con datos para saber qué se ha tocado. */
data class MapMarker(val id: Long, val lat: Double, val lon: Double, val name: String, val mtb: Boolean)

/** GeoJSON mínimo para pasar el track a MapLibre. Coordenadas en orden [lon, lat]. */
object GeoJson {

    fun markers(items: List<MapMarker>): String =
        items.joinToString(prefix = """{"type":"FeatureCollection","features":[""", postfix = "]}") { m ->
            """{"type":"Feature","properties":{"id":${m.id},"name":"${escape(m.name)}","mtb":${m.mtb}},""" +
                """"geometry":{"type":"Point","coordinates":[${m.lon},${m.lat}]}}"""
        }

    /**
     * Recorrido grabado partido en trocitos, cada uno con su velocidad en km/h
     * (propiedad "kmh"), para colorear la línea por velocidad en el mapa.
     * Cada trozo va de un punto al que está ~[stepMeters] más allá.
     */
    fun speedLines(segments: List<List<app.trazo.gpx.ride.TimedPoint>>, stepMeters: Double = 50.0): String {
        val features = mutableListOf<String>()
        for (seg in segments) {
            var start = 0
            var acc = 0.0
            for (i in 1 until seg.size) {
                acc += TrackStatsCalculator.haversineMeters(GeoPoint(seg[i - 1].lat, seg[i - 1].lon), GeoPoint(seg[i].lat, seg[i].lon))
                if (acc >= stepMeters || i == seg.lastIndex) {
                    val dt = (seg[i].timeMs - seg[start].timeMs) / 1000.0
                    val kmh = if (dt > 0) acc / dt * 3.6 else 0.0
                    val coords = seg.subList(start, i + 1).joinToString(",") { "[${it.lon},${it.lat}]" }
                    features += """{"type":"Feature","properties":{"kmh":${String.format(java.util.Locale.ROOT, "%.1f", kmh)}},""" +
                        """"geometry":{"type":"LineString","coordinates":[$coords]}}"""
                    start = i
                    acc = 0.0
                }
            }
        }
        return features.joinToString(prefix = """{"type":"FeatureCollection","features":[""", postfix = "]}")
    }

    /** Escapa un texto para meterlo entre comillas en JSON. */
    fun escape(s: String): String = buildString {
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append(String.format("\\u%04x", c.code))
                else -> append(c)
            }
        }
    }

    fun track(segments: List<List<GeoPoint>>): String =
        segments.filter { it.size >= 2 }.joinToString(
            prefix = """{"type":"Feature","properties":{},"geometry":{"type":"MultiLineString","coordinates":[""",
            postfix = "]}}",
        ) { seg -> seg.joinToString(",", "[", "]") { "[${it.lon},${it.lat}]" } }

    fun points(points: List<Pair<Double, Double>>): String =
        points.joinToString(
            prefix = """{"type":"FeatureCollection","features":[""",
            postfix = "]}",
        ) { (lat, lon) ->
            """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[$lon,$lat]}}"""
        }
}
