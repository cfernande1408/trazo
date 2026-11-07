package app.trazo.gpx.layers

import app.trazo.gpx.Activity

/** Teselas raster de un servicio WMTS/XYZ, con {z}/{x}/{y} en la URL. */
data class RasterSpec(
    val id: String,
    val tiles: String,
    val attribution: String,
    val maxZoom: Int,
    val opacity: Float = 1f,
)

/** Tipo de mapa, como en Google Maps: el de Trazo, el topográfico oficial o satélite. */
enum class BaseMap(val label: String, val raster: RasterSpec?, val note: String?) {
    TRAZO("Trazo", null, null),
    IGN_TOPO(
        "Topo IGN",
        RasterSpec(
            "base-ign-topo",
            wmts("mapa-raster", "MTN", "image/jpeg"),
            "Mapa topográfico © Instituto Geográfico Nacional",
            17,
        ),
        "Solo España",
    ),
    SATELLITE(
        "Satélite",
        RasterSpec(
            "base-pnoa",
            wmts("pnoa-ma", "OI.OrthoimageCoverage", "image/jpeg"),
            "Ortofoto PNOA © Instituto Geográfico Nacional",
            19,
        ),
        "Solo España",
    ),
}

/**
 * Capas de un vistazo, guardadas como texto en las preferencias.
 * [routes]: rutas MTB (en bici) o senderos señalizados (a pie) de Waymarked Trails.
 */
data class LayerPrefs(
    val base: BaseMap = BaseMap.TRAZO,
    val hillshade: Boolean = true,
    val trees: Boolean = true,
    val routes: Boolean = false,
) {
    fun encode(): String = listOf(base.name, hillshade, trees, routes).joinToString(";")

    companion object {
        fun decode(s: String?): LayerPrefs {
            val p = s?.split(";") ?: return LayerPrefs()
            return runCatching {
                LayerPrefs(BaseMap.valueOf(p[0]), p[1].toBooleanStrict(), p[2].toBooleanStrict(), p[3].toBooleanStrict())
            }.getOrDefault(LayerPrefs())
        }
    }
}

/**
 * Qué hacer con el estilo de Trazo para unas capas dadas: qué capas suyas
 * ocultar y qué teselas raster añadir y dónde.
 */
data class LayerPlan(
    /** Capas del estilo base que hay que ocultar (el resto, visibles). */
    val hidden: Set<String>,
    /** Mapa base raster y la capa del estilo encima de la cual va. */
    val base: RasterSpec?,
    val baseAbove: String?,
    /** Capas raster superpuestas, por encima de todo el estilo base. */
    val overlays: List<RasterSpec>,
)

object MapLayers {

    /** Capas del estilo trazo_topo.json, de abajo arriba. */
    val STYLE_LAYERS = listOf(
        "background", "landuse-residential", "landcover-grass", "park", "landcover-wood", "landcover-wood-pines",
        "hillshade", "water", "waterway", "building", "road-path", "road-track", "road-minor-casing", "road-minor",
        "road-major-casing", "road-major", "road-motorway", "place-label", "peak-label",
    )
    const val TOP = "peak-label"

    /** Relleno del suelo, que la foto de satélite sustituye. */
    private val GROUND = setOf("landuse-residential", "landcover-grass", "park", "landcover-wood", "landcover-wood-pines", "building")

    fun plan(p: LayerPrefs, activity: Activity): LayerPlan {
        val hidden = mutableSetOf<String>()
        if (!p.trees) hidden += "landcover-wood-pines"
        if (!p.hillshade) hidden += "hillshade"
        val (base, above) = when (p.base) {
            BaseMap.TRAZO -> null to null
            // El topo del IGN ya trae su relieve, curvas y nombres: va por encima de todo
            BaseMap.IGN_TOPO -> p.base.raster to TOP
            // Satélite "híbrido": foto bajo el sombreado, caminos y nombres de Trazo encima
            BaseMap.SATELLITE -> {
                hidden += GROUND
                p.base.raster to "landcover-wood-pines"
            }
        }
        val overlays = if (p.routes) listOf(routesOverlay(activity)) else emptyList()
        return LayerPlan(hidden, base, above, overlays)
    }

    fun routesOverlay(activity: Activity): RasterSpec {
        val flavour = if (activity == Activity.HIKE) "hiking" else "mtb"
        return RasterSpec(
            "overlay-routes",
            "https://tile.waymarkedtrails.org/$flavour/{z}/{x}/{y}.png",
            "Rutas © waymarkedtrails.org, © colaboradores de OpenStreetMap",
            17,
            0.9f,
        )
    }
}

/** URL de teselas WMTS del IGN (España) en la rejilla de Google/OSM. */
private fun wmts(service: String, layer: String, format: String) =
    "https://www.ign.es/wmts/$service?layer=$layer&style=default&tilematrixset=GoogleMapsCompatible" +
        "&Service=WMTS&Request=GetTile&Version=1.0.0&Format=$format&TileMatrix={z}&TileCol={x}&TileRow={y}"
