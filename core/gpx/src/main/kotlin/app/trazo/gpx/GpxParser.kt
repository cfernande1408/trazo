package app.trazo.gpx

import app.trazo.gpx.i18n.L
import java.io.InputStream

object GpxParser {

    fun parse(input: InputStream): Gpx = parse(input.bufferedReader(Charsets.UTF_8).readText())

    fun parse(xml: String): Gpx {
        val stack = ArrayDeque<String>()
        val text = StringBuilder()
        var sawRoot = false

        var gpxName: String? = null
        var description: String? = null
        val links = mutableListOf<String>()
        val tracks = mutableListOf<Track>()
        val waypoints = mutableListOf<Waypoint>()

        var trackName: String? = null
        var segments = mutableListOf<List<GeoPoint>>()
        var segment = mutableListOf<GeoPoint>()
        var point: PointBuilder? = null

        for (e in XmlReader(xml.removePrefix("﻿")).events()) {
            when (e) {
                is XmlEvent.Start -> {
                    if (stack.isEmpty()) {
                        if (sawRoot) throw GpxParseException(L("Contenido tras el final del GPX", "Content after the end of the GPX"))
                        if (e.name != "gpx") throw GpxParseException(L("El archivo no es un GPX", "The file is not a GPX"))
                        sawRoot = true
                    }
                    val startParent = stack.lastOrNull()
                    stack.addLast(e.name)
                    text.clear()
                    when (e.name) {
                        "link" -> if (startParent in ROUTE_LEVEL) {
                            e.attrs["href"]?.takeIf { it.startsWith("http") }?.let(links::add)
                        } else point?.takeIf { it.tag == "wpt" && startParent == it.tag }?.let { it.link = e.attrs["href"] }
                        "trk" -> { trackName = null; segments = mutableListOf() }
                        "trkseg" -> segment = mutableListOf()
                        "rte" -> { trackName = null; segment = mutableListOf() }
                        "trkpt", "rtept", "wpt" -> point = PointBuilder.from(e)
                    }
                }

                is XmlEvent.Text -> text.append(e.text)

                is XmlEvent.End -> {
                    if (stack.lastOrNull() != e.name) {
                        throw GpxParseException("Etiqueta </${e.name}> inesperada")
                    }
                    val parent = stack.getOrNull(stack.size - 2)
                    val value = text.toString().trim().ifEmpty { null }
                    val p = point
                    when (e.name) {
                        "ele" -> if (p != null && parent == p.tag) p.ele = value?.toDoubleOrNull()
                        "time" -> if (p != null && parent == p.tag) p.time = value
                        "name" -> when {
                            p != null && parent == p.tag -> p.name = value
                            parent == "trk" || parent == "rte" -> trackName = value
                            parent == "metadata" || parent == "gpx" -> gpxName = gpxName ?: value
                        }
                        "desc" -> when {
                            p != null && parent == p.tag -> p.desc = value
                            parent in ROUTE_LEVEL -> description = description ?: value
                        }
                        "cmt" -> if (p != null && parent == p.tag) p.desc = p.desc ?: value
                        "trkpt", "rtept" -> { p?.let { segment.add(it.toGeoPoint()) }; point = null }
                        "wpt" -> { p?.let { waypoints.add(it.toWaypoint()) }; point = null }
                        "trkseg" -> if (segment.isNotEmpty()) segments.add(segment)
                        "trk" -> if (segments.isNotEmpty()) tracks.add(Track(trackName, segments))
                        "rte" -> if (segment.isNotEmpty()) tracks.add(Track(trackName, listOf(segment)))
                        // Extensiones de sensores dentro del punto (gpxtpx:hr, gpxtpx:cad, power…)
                        else -> if (p != null) when (e.name.substringAfter(':')) {
                            "hr" -> p.hr = value?.toDoubleOrNull()?.toInt()
                            "cad" -> p.cad = value?.toDoubleOrNull()?.toInt()
                            "power", "PowerInWatts" -> p.power = value?.toDoubleOrNull()?.toInt()
                        }
                    }
                    stack.removeLast()
                    text.clear()
                }
            }
        }

        if (!sawRoot) throw GpxParseException(L("El archivo no es un GPX", "The file is not a GPX"))
        if (stack.isNotEmpty()) throw GpxParseException(L("GPX incompleto: falta cerrar <${stack.last()}>", "Incomplete GPX: <${stack.last()}> is not closed"))

        return Gpx(
            name = gpxName ?: tracks.firstNotNullOfOrNull { it.name },
            tracks = tracks,
            waypoints = waypoints,
            description = description,
            links = links.distinct(),
        )
    }

    /** Elementos cuyo <desc> o <link> describen la ruta entera, no un punto. */
    private val ROUTE_LEVEL = setOf("metadata", "trk", "rte", "gpx")

    private class PointBuilder(val tag: String, val lat: Double, val lon: Double) {
        var ele: Double? = null
        var time: String? = null
        var name: String? = null
        var desc: String? = null
        var hr: Int? = null
        var cad: Int? = null
        var power: Int? = null
        var link: String? = null

        fun toGeoPoint() = GeoPoint(lat, lon, ele, time, hr, cad, power)
        fun toWaypoint() = Waypoint(lat, lon, ele, name, desc, link)

        companion object {
            fun from(e: XmlEvent.Start): PointBuilder {
                val lat = e.attrs["lat"]?.toDoubleOrNull()
                    ?: throw GpxParseException("<${e.name}> sin latitud válida")
                val lon = e.attrs["lon"]?.toDoubleOrNull()
                    ?: throw GpxParseException("<${e.name}> sin longitud válida")
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                    throw GpxParseException("<${e.name}> con coordenadas fuera de rango")
                }
                return PointBuilder(e.name, lat, lon)
            }
        }
    }
}
