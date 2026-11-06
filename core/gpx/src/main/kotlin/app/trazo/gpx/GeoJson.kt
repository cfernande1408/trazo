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
