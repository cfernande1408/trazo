package app.trazo.gpx.ride

import java.time.Instant
import java.util.Locale

/** Escribe una salida grabada como GPX 1.1 (se puede subir a Strava, Wikiloc…). */
object GpxWriter {

    fun write(
        name: String,
        segments: List<List<TimedPoint>>,
        description: String? = null,
        type: String = "mountain_biking",
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Trazo" xmlns="http://www.topografix.com/GPX/1/1" """)
        append("""xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">""").append('\n')
        append("  <metadata>\n    <name>").append(escape(name)).append("</name>\n")
        segments.firstOrNull()?.firstOrNull()?.let {
            append("    <time>").append(Instant.ofEpochMilli(it.timeMs)).append("</time>\n")
        }
        append("  </metadata>\n")
        append("  <trk>\n    <name>").append(escape(name)).append("</name>\n")
        description?.let { append("    <desc>").append(escape(it)).append("</desc>\n") }
        append("    <type>").append(type).append("</type>\n")
        for (seg in segments.filter { it.isNotEmpty() }) {
            append("    <trkseg>\n")
            for (p in seg) {
                append("      <trkpt lat=\"").append(f(p.lat, 7)).append("\" lon=\"").append(f(p.lon, 7)).append("\">")
                p.ele?.let { append("<ele>").append(f(it, 1)).append("</ele>") }
                append("<time>").append(Instant.ofEpochMilli(p.timeMs)).append("</time>")
                // Pulso y cadencia como los escribe Garmin (Strava los lee); potencia aparte
                if (p.hr != null || p.cad != null || p.power != null) {
                    append("<extensions>")
                    if (p.hr != null || p.cad != null) {
                        append("<gpxtpx:TrackPointExtension>")
                        p.hr?.let { append("<gpxtpx:hr>").append(it).append("</gpxtpx:hr>") }
                        p.cad?.let { append("<gpxtpx:cad>").append(it).append("</gpxtpx:cad>") }
                        append("</gpxtpx:TrackPointExtension>")
                    }
                    p.power?.let { append("<power>").append(it).append("</power>") }
                    append("</extensions>")
                }
                append("</trkpt>\n")
            }
            append("    </trkseg>\n")
        }
        append("  </trk>\n</gpx>\n")
    }

    /** Una ruta (sin horas), por ejemplo una creada en el planificador. */
    fun writeRoute(name: String, segments: List<List<app.trazo.gpx.GeoPoint>>, description: String? = null): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Trazo" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        append("  <metadata>\n    <name>").append(escape(name)).append("</name>\n  </metadata>\n")
        append("  <trk>\n    <name>").append(escape(name)).append("</name>\n")
        description?.let { append("    <desc>").append(escape(it)).append("</desc>\n") }
        for (seg in segments.filter { it.isNotEmpty() }) {
            append("    <trkseg>\n")
            for (p in seg) {
                append("      <trkpt lat=\"").append(f(p.lat, 7)).append("\" lon=\"").append(f(p.lon, 7)).append("\">")
                p.ele?.let { append("<ele>").append(f(it, 1)).append("</ele>") }
                append("</trkpt>\n")
            }
            append("    </trkseg>\n")
        }
        append("  </trk>\n</gpx>\n")
    }

    private fun f(v: Double, decimals: Int) = String.format(Locale.ROOT, "%.${decimals}f", v)

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
