package app.trazo.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import app.trazo.gpx.Season
import app.trazo.gpx.layers.BaseMap
import app.trazo.gpx.offline.OfflineArea
import app.trazo.ui.AppMode
import org.json.JSONObject
import java.io.File
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
    private const val STYLE_ASSET = "trazo_topo.json"

    /**
     * El estilo topo con los colores de la estación: se reescribe una vez
     * por estación en la caché y se carga por URI file:// (igual de seguro
     * que asset:// para MapSnapshotter).
     */
    fun styleJson(context: Context, season: Season = AppMode.season): String =
        styleFile(context, season).readText()

    private fun styleFile(context: Context, season: Season): File {
        val asset = context.assets.open(STYLE_ASSET).bufferedReader().use { it.readText() }
        // El nombre lleva una huella del estilo original: si cambia con una actualización, se regenera
        val f = File(context.cacheDir, "trazo_topo_${season.name.lowercase()}_${Integer.toHexString(asset.hashCode())}.json")
        if (!f.exists()) {
            val root = JSONObject(asset)
            val layers = root.getJSONArray("layers")
            for (i in 0 until layers.length()) {
                val l = layers.getJSONObject(i)
                val paint = l.optJSONObject("paint") ?: continue
                when (l.getString("id")) {
                    "background" -> paint.put("background-color", season.background)
                    "landcover-grass" -> paint.put("fill-color", season.grass)
                    "landcover-wood" -> paint.put("fill-color", season.wood)
                    "park" -> paint.put("fill-color", season.park)
                }
            }
            f.writeText(root.toString())
        }
        return f
    }

    /**
     * Estilo para descargar mapas sin conexión: el de la estación y, si se
     * pide, el topo IGN como una capa más, para que también se descarguen sus
     * teselas. Luego MapLibre las sirve por URL en cualquier estilo.
     * Devuelve un archivo: quien descarga lo sirve por HTTP local
     * (MapLibre en Android no descarga con estilos file://).
     */
    fun offlineStyleFile(context: Context, withTopo: Boolean): File {
        val base = styleFile(context, AppMode.season)
        if (!withTopo) return base
        val topo = BaseMap.IGN_TOPO.raster!!
        val root = JSONObject(base.readText())
        root.getJSONObject("sources").put(topo.id, JSONObject()
            .put("type", "raster")
            .put("tiles", org.json.JSONArray().put(topo.tiles))
            .put("tileSize", 256)
            .put("maxzoom", OfflineArea.TOPO_MAX_ZOOM)
            .put("attribution", topo.attribution))
        root.getJSONArray("layers").put(JSONObject().put("id", topo.id).put("type", "raster").put("source", topo.id))
        val f = File(context.cacheDir, "trazo_offline_topo.json")
        f.writeText(root.toString())
        return f
    }

    /** Solo el mapa topo con sus árboles, sin ruta. */
    fun baseBuilder(context: Context, season: Season = AppMode.season): Style.Builder =
        Style.Builder()
            .fromUri(Uri.fromFile(styleFile(context, season)).toString())
            .withImage("pino", treeTile(context.resources.displayMetrics.density, season))

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
     * Baldosa de 68 dp que se repite sobre los bosques: tres árboles al
     * tresbolillo (dos pinos y, según la estación, un árbol de hoja ancha
     * verde, en flor u otoñal; en invierno, tres pinos nevados). Algo más
     * de bosque que antes, sin tapar el relieve ni los caminos.
     */
    private fun treeTile(density: Float, season: Season): Bitmap {
        val s = (68 * density).toInt().coerceAtLeast(24)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = TreePaints(season)
        drawPine(c, p, cx = s * 0.22f, baseY = s * 0.38f, h = s * 0.25f)
        if (season.broadleafShare > 0) drawBroadleaf(c, p, cx = s * 0.72f, baseY = s * 0.62f, h = s * 0.22f)
        else drawPine(c, p, cx = s * 0.72f, baseY = s * 0.62f, h = s * 0.23f)
        drawPine(c, p, cx = s * 0.40f, baseY = s * 0.95f, h = s * 0.23f)
        return bmp
    }

    private class TreePaints(val season: Season) {
        private fun paint(hex: String) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor(hex) }
        val light = paint(season.pineLight)
        val shade = paint(season.pineShade)
        val trunk = paint("#7A5C3E")
        val leaf = paint(season.broadleaf)
        val leafShade = paint(season.broadleafShade)
        val blossom = season.blossom?.let(::paint)
        val snow = paint("#FFFFFF")
    }

    /** Mismo dibujo que el diseño: copa de dos pisos sobre un tronco, en un cuadro de 24×44. */
    private fun drawPine(c: Canvas, p: TreePaints, cx: Float, baseY: Float, h: Float) {
        val k = h / 44f
        fun x(v: Float) = cx + v * k
        fun y(v: Float) = baseY - 4 * k + v * k
        c.drawRect(x(-1.5f), y(-6f), x(1.5f), y(4f), p.trunk)
        val full = Path().apply {
            moveTo(x(0f), y(-40f)); lineTo(x(9f), y(-25f)); lineTo(x(5f), y(-25f)); lineTo(x(12f), y(-8f))
            lineTo(x(-12f), y(-8f)); lineTo(x(-5f), y(-25f)); lineTo(x(-9f), y(-25f)); close()
        }
        val half = Path().apply {
            moveTo(x(0f), y(-40f)); lineTo(x(9f), y(-25f)); lineTo(x(5f), y(-25f)); lineTo(x(12f), y(-8f))
            lineTo(x(0f), y(-8f)); close()
        }
        c.drawPath(full, p.light)
        c.drawPath(half, p.shade)
        if (p.season.snow) {
            // Nieve en la punta y en el borde de cada piso
            c.drawPath(Path().apply {
                moveTo(x(0f), y(-40f)); lineTo(x(4.5f), y(-32.5f)); lineTo(x(-4.5f), y(-32.5f)); close()
            }, p.snow)
            c.drawPath(Path().apply {
                moveTo(x(-9f), y(-25f)); lineTo(x(9f), y(-25f)); lineTo(x(7.5f), y(-22.5f)); lineTo(x(-7.5f), y(-22.5f)); close()
                moveTo(x(-12f), y(-8f)); lineTo(x(12f), y(-8f)); lineTo(x(10.5f), y(-5.5f)); lineTo(x(-10.5f), y(-5.5f)); close()
            }, p.snow)
        }
    }

    /** Árbol de copa redonda (encina, roble, haya), en el mismo cuadro de 24×44. */
    private fun drawBroadleaf(c: Canvas, p: TreePaints, cx: Float, baseY: Float, h: Float) {
        val k = h / 44f
        fun x(v: Float) = cx + v * k
        fun y(v: Float) = baseY - 4 * k + v * k
        c.drawRect(x(-1.8f), y(-12f), x(1.8f), y(4f), p.trunk)
        c.drawCircle(x(0f), y(-24f), 12 * k, p.leaf)
        c.drawCircle(x(-6f), y(-17f), 8 * k, p.leaf)
        c.drawCircle(x(6f), y(-17f), 8 * k, p.leafShade)
        c.drawCircle(x(4f), y(-27f), 8 * k, p.leafShade)
        p.blossom?.let { b ->
            listOf(-6f to -27f, 2f to -32f, 7f to -21f, -3f to -18f, -8f to -21f).forEach { (dx, dy) ->
                c.drawCircle(x(dx), y(dy), 1.8f * k, b)
            }
        }
    }
}
