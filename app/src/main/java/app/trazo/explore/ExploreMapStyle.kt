package app.trazo.explore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import app.trazo.gpx.GeoJson
import app.trazo.map.TrazoMapStyle
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/** Mapa topo de Explorar: una bici por ruta y, encima, la ruta seleccionada. */
object ExploreMapStyle {

    const val ROUTES = "routes"
    const val SELECTED = "selected"

    fun builder(context: Context): Style.Builder {
        val d = context.resources.displayMetrics.density
        return TrazoMapStyle.baseBuilder(context)
            .withImage("bici-mtb", bikeIcon(d, 0xFF3E5A00.toInt()))
            .withImage("bici", bikeIcon(d, 0xFF386663.toInt()))
            .withSource(GeoJsonSource(SELECTED, GeoJson.track(emptyList())))
            .withLayer(
                LineLayer("$SELECTED-casing", SELECTED).withProperties(
                    lineColor("#FFFFFF"), lineWidth(9f),
                    lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                )
            )
            .withLayer(
                LineLayer("$SELECTED-line", SELECTED).withProperties(
                    lineColor("#C2410C"), lineWidth(5f),
                    lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                )
            )
            .withSource(GeoJsonSource(ROUTES, GeoJson.markers(emptyList())))
            .withLayer(
                SymbolLayer(ROUTES, ROUTES).withProperties(
                    iconImage(
                        Expression.switchCase(
                            Expression.toBool(Expression.get("mtb")), Expression.literal("bici-mtb"),
                            Expression.literal("bici"),
                        )
                    ),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                )
            )
    }

    /** Círculo de color con una bici blanca, 34 dp. Verde para MTB, verde azulado para el resto. */
    private fun bikeIcon(density: Float, color: Int): Bitmap {
        val s = (34 * density).toInt()
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = s / 2f
        c.drawCircle(r, r, r - density, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        c.drawCircle(r, r, r - 1.5f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2 * density; this.color = 0xFFFFFFFF.toInt()
        })
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = 0xFFFFFFFF.toInt()
        }
        fun x(v: Float) = v * s
        val wheel = 0.15f * s
        c.drawCircle(x(0.31f), x(0.60f), wheel, line)
        c.drawCircle(x(0.69f), x(0.60f), wheel, line)
        c.drawPath(Path().apply {
            moveTo(x(0.31f), x(0.60f)); lineTo(x(0.46f), x(0.60f)); lineTo(x(0.60f), x(0.40f))
            lineTo(x(0.40f), x(0.40f)); close()
            moveTo(x(0.69f), x(0.60f)); lineTo(x(0.58f), x(0.34f)); lineTo(x(0.64f), x(0.34f))
            moveTo(x(0.40f), x(0.40f)); lineTo(x(0.37f), x(0.33f)); lineTo(x(0.43f), x(0.33f))
        }, line)
        return bmp
    }
}
