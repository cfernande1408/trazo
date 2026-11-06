package app.trazo.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import app.trazo.gpx.GeoJson
import app.trazo.gpx.RouteGeometry
import app.trazo.importer.ImportState
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource

/** Estilo topográfico de Trazo + la ruta encima. Se usa en el mapa y en la vista previa. */
object TrazoMapStyle {

    private const val TRACK_COLOR = "#C2410C"
    const val CURSOR = "cursor"

    /**
     * Se carga por URI y no con fromJson: con fromJson el estilo termina de
     * cargar dentro del constructor de MapSnapshotter, antes de que exista su
     * objeto nativo, y al añadir la ruta falla con "invalid native peer".
     */
    private const val STYLE_URI = "asset://trazo_topo.json"

    /** Solo el mapa topo con sus pinos, sin ruta. */
    fun baseBuilder(context: Context): Style.Builder =
        Style.Builder()
            .fromUri(STYLE_URI)
            .withImage("pino", pineTile(context.resources.displayMetrics.density))

    fun builder(context: Context, state: ImportState.Loaded): Style.Builder {
        val segs = state.gpx.segments
        val ends = listOfNotNull(segs.firstOrNull()?.firstOrNull(), segs.lastOrNull()?.lastOrNull())
        val wpts = state.gpx.waypoints.map { it.lat to it.lon }

        return baseBuilder(context)
            .withSource(GeoJsonSource("track", GeoJson.track(segs)))
            .withLayer(
                LineLayer("track-casing", "track").withProperties(
                    lineColor("#FFFFFF"), lineWidth(9f),
                    lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                )
            )
            .withLayer(
                LineLayer("track-line", "track").withProperties(
                    lineColor(TRACK_COLOR), lineWidth(5f),
                    lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                )
            )
            .withSource(GeoJsonSource("waypoints", GeoJson.points(wpts)))
            .withLayer(
                CircleLayer("waypoints", "waypoints").withProperties(
                    circleRadius(6f), circleColor("#1F6FB2"),
                    circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f),
                )
            )
            .withSource(GeoJsonSource("ends", GeoJson.points(ends.map { it.lat to it.lon })))
            .withLayer(
                CircleLayer("ends", "ends").withProperties(
                    circleRadius(7f), circleColor("#1A1C16"),
                    circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
                )
            )
            // Punto que sigue al dedo en el perfil
            .withSource(GeoJsonSource(CURSOR, GeoJson.points(emptyList())))
            .withLayer(
                CircleLayer(CURSOR, CURSOR).withProperties(
                    circleRadius(9f), circleColor("#C5F26B"),
                    circleStrokeColor("#1A1C16"), circleStrokeWidth(3f),
                )
            )
    }

    /** Recuadro de la ruta ampliado un [margin] por cada lado, o null si es un único punto. */
    fun bounds(state: ImportState.Loaded, margin: Double = 0.0): LatLngBounds? {
        val b = RouteGeometry.bounds(state.gpx.segments) ?: return null
        if (b.minLat == b.maxLat && b.minLon == b.maxLon) return null
        val dLat = (b.maxLat - b.minLat) * margin
        val dLon = (b.maxLon - b.minLon) * margin
        return LatLngBounds.Builder()
            .include(LatLng(b.minLat - dLat, b.minLon - dLon))
            .include(LatLng(b.maxLat + dLat, b.maxLon + dLon))
            .build()
    }

    /**
     * Baldosa con dos pinos estilo Material (cara clara y cara en sombra) que
     * se repite como patrón sobre los bosques. 72 dp de lado con pinos de
     * unos 18 dp: deja ver el relieve y los caminos.
     */
    private fun pineTile(density: Float): Bitmap {
        val s = (72 * density).toInt().coerceAtLeast(24)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawPine(c, cx = s * 0.25f, baseY = s * 0.40f, h = s * 0.25f)
        drawPine(c, cx = s * 0.75f, baseY = s * 0.90f, h = s * 0.22f)
        return bmp
    }

    private val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6E9F48.toInt() }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4F7F33.toInt() }
    private val trunk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7A5C3E.toInt() }

    /** Mismo dibujo que el diseño: copa de dos pisos sobre un tronco, en un cuadro de 24×44. */
    private fun drawPine(c: Canvas, cx: Float, baseY: Float, h: Float) {
        val k = h / 44f
        fun x(v: Float) = cx + v * k
        fun y(v: Float) = baseY - 4 * k + v * k
        c.drawRect(x(-1.5f), y(-6f), x(1.5f), y(4f), trunk)
        val full = Path().apply {
            moveTo(x(0f), y(-40f)); lineTo(x(9f), y(-25f)); lineTo(x(5f), y(-25f)); lineTo(x(12f), y(-8f))
            lineTo(x(-12f), y(-8f)); lineTo(x(-5f), y(-25f)); lineTo(x(-9f), y(-25f)); close()
        }
        val half = Path().apply {
            moveTo(x(0f), y(-40f)); lineTo(x(9f), y(-25f)); lineTo(x(5f), y(-25f)); lineTo(x(12f), y(-8f))
            lineTo(x(0f), y(-8f)); close()
        }
        c.drawPath(full, light)
        c.drawPath(half, shade)
    }
}
