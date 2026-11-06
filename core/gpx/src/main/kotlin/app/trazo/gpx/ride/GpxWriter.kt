package app.trazo.gpx.ride

import java.time.Instant
import java.util.Locale

/** Escribe una salida grabada como GPX 1.1 (se puede subir a Strava, Wikiloc…). */
object GpxWriter {

    fun write(name: String, segments: List<List<TimedPoint>>, description: String? = null): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Trazo" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        append("  <metadata>\n    <name>").append(escape(name)).append("</name>\n")
        segments.firstOrNull()?.firstOrNull()?.let {
            append("    <time>").append(Instant.ofEpochMilli(it.timeMs)).append("</time>\n")
        }
        append("  </metadata>\n")
        append("  <trk>\n    <name>").append(escape(name)).append("</name>\n")
        description?.let { append("    <desc>").append(escape(it)).append("</desc>\n") }
        append("    <type>mountain_biking</type>\n")
        for (seg in segments.filter { it.isNotEmpty() }) {
            append("    <trkseg>\n")
            for (p in seg) {
                append("      <trkpt lat=\"").append(f(p.lat, 7)).append("\" lon=\"").append(f(p.lon, 7)).append("\">")
                p.ele?.let { append("<ele>").append(f(it, 1)).append("</ele>") }
                append("<time>").append(Instant.ofEpochMilli(p.timeMs)).append("</time></trkpt>\n")
            }
            append("    </trkseg>\n")
        }
        append("  </trk>\n</gpx>\n")
    }

    private fun f(v: Double, decimals: Int) = String.format(Locale.ROOT, "%.${decimals}f", v)

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
