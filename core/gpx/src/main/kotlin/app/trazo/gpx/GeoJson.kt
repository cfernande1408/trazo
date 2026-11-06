package app.trazo.gpx

/** GeoJSON mínimo para pasar el track a MapLibre. Coordenadas en orden [lon, lat]. */
object GeoJson {

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
