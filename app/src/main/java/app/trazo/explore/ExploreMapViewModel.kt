package app.trazo.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.trazo.gpx.Gpx
import app.trazo.gpx.osm.Bbox
import app.trazo.gpx.osm.OsmRouteSummary
import app.trazo.gpx.osm.Overpass
import app.trazo.gpx.osm.WayStitcher
import app.trazo.gpx.osm.WaymarkedTrails
import app.trazo.gpx.Track
import app.trazo.gpx.Activity
import app.trazo.ui.AppMode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import app.trazo.importer.ImportState
import app.trazo.importer.loadedFrom
import app.trazo.net.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ruta tocada en el mapa: primero llega su geometría y luego, con la altitud, todo lo demás. */
data class SelectedRoute(
    val summary: OsmRouteSummary,
    val gpx: Gpx? = null,
    val loaded: ImportState.Loaded? = null,
    val error: String? = null,
)

class ExploreMapViewModel(app: Application) : AndroidViewModel(app) {

    private val _routes = MutableStateFlow<Map<Long, OsmRouteSummary>>(emptyMap())
    val routes: StateFlow<Map<Long, OsmRouteSummary>> = _routes.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _onlyMtb = MutableStateFlow(false)
    val onlyMtb: StateFlow<Boolean> = _onlyMtb.asStateFlow()

    private val _selected = MutableStateFlow<SelectedRoute?>(null)
    val selected: StateFlow<SelectedRoute?> = _selected.asStateFlow()

    /** Zonas ya pedidas: si lo que ves está dentro de una, no se vuelve a pedir. */
    private val covered = mutableListOf<Bbox>()
    private var loadJob: Job? = null
    private var selectJob: Job? = null

    fun toggleOnlyMtb() {
        _onlyMtb.value = !_onlyMtb.value
    }

    /** Última zona pedida, para poder reintentarla tocando el aviso. */
    private var lastView: Pair<Bbox, Double>? = null

    /** true si el aviso actual es un error que se puede reintentar tocándolo. */
    private val _canRetry = MutableStateFlow(false)
    val canRetry: StateFlow<Boolean> = _canRetry.asStateFlow()

    /** Se llama cada vez que el mapa se para. Carga las rutas de la zona visible si faltan. */
    fun onViewport(view: Bbox, zoom: Double) {
        lastView = view to zoom
        if (zoom < MIN_ZOOM) {
            loadJob?.cancel()
            _canRetry.value = false
            _status.value = ZOOM_HINT
            return
        }
        if (covered.any { it.contains(view) }) {
            if (_status.value == ZOOM_HINT) _status.value = null
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(350) // por si sigues moviendo el mapa
            _canRetry.value = false
            _status.value = "Buscando rutas en esta zona…"
            val area = view.expand(0.15)
            // Cortes de red sueltos (cambio wifi/datos, la app un momento en segundo plano): reintento solo
            for ((attempt, wait) in listOf(0L, 2_000L, 5_000L).withIndex()) {
                if (wait > 0) {
                    _status.value = "Se cortó la conexión, reintentando…"
                    delay(wait)
                }
                try {
                    val found = findRoutes(area)
                    _routes.value = _routes.value + found.associateBy { it.id }
                    covered += area
                    _status.value = if (_routes.value.isEmpty()) "No hay rutas mapeadas en esta zona" else null
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (attempt == 2) {
                        _status.value = Http.friendly(e) + " Toca para reintentar."
                        _canRetry.value = true
                    }
                }
            }
        }
    }

    fun retry() {
        lastView?.let { (v, z) -> onViewport(v, z) }
    }

    /**
     * Rutas de la zona: primero Waymarked Trails (MTB y ciclistas a la vez,
     * casi instantáneo) y, solo si falla, Overpass.
     */
    private suspend fun findRoutes(area: Bbox): List<OsmRouteSummary> {
        val wmt = coroutineScope {
            WaymarkedTrails.flavoursFor(AppMode.current).map { f ->
                async {
                    runCatching {
                        val listed = WaymarkedTrails.parseList(Http.get(WaymarkedTrails.byAreaUrl(f, area)))
                        if (listed.isEmpty()) return@runCatching emptyList()
                        val segs = WaymarkedTrails.parseSegments(
                            Http.get(WaymarkedTrails.segmentsUrl(f, WaymarkedTrails.mercBbox(area), listed.map { it.id }))
                        )
                        WaymarkedTrails.summaries(f, listed, segs)
                    }
                }
            }.awaitAll()
        }
        if (wmt.any { it.isSuccess }) {
            // Si una ruta sale en las dos listas, gana la de MTB (va primero)
            return wmt.flatMap { it.getOrDefault(emptyList()) }.distinctBy { it.id }
        }
        val routes = if (AppMode.current == Activity.HIKE) "hiking|foot" else "mtb|bicycle"
        return Overpass.parseSearch(Http.overpass(Overpass.bboxQuery(area, routes)))
    }

    /** Trazado simplificado de Waymarked Trails: llega en un momento y sirve para ver la ruta. */
    private suspend fun quickGpx(summary: OsmRouteSummary): Gpx? = runCatching {
        val f = when {
            AppMode.current == Activity.HIKE -> WaymarkedTrails.Flavour.HIKING
            summary.isMtb -> WaymarkedTrails.Flavour.MTB
            else -> WaymarkedTrails.Flavour.CYCLING
        }
        val (bbox, desc) = WaymarkedTrails.parseDetails(Http.get(WaymarkedTrails.detailsUrl(f, summary.id)))
        val lines = WaymarkedTrails.parseSegments(Http.get(WaymarkedTrails.segmentsUrl(f, bbox!!, listOf(summary.id))))[summary.id]
        val segs = WayStitcher.stitch(lines!!)
        Gpx(summary.name, listOf(Track(summary.name, segs)), emptyList(), desc, listOf(Overpass.osmUrl(summary.id)))
    }.getOrNull()

    fun select(id: Long) {
        val summary = _routes.value[id] ?: return
        selectJob?.cancel()
        _selected.value = SelectedRoute(summary)
        selectJob = viewModelScope.launch {
            try {
                // 1) Se dibuja ya con el trazado simplificado
                val quick = quickGpx(summary)
                if (quick != null) _selected.value = SelectedRoute(summary, quick)
                // 2) El trazado exacto de OSM, si Overpass contesta pronto; si no, vale el simplificado
                val exact = withTimeoutOrNull(if (quick != null) 8_000L else 30_000L) {
                    runCatching { Overpass.parseRoute(Http.overpass(Overpass.routeQuery(id)), id) }.getOrNull()
                }
                val gpx = exact ?: quick ?: throw java.io.IOException("No se pudo descargar el recorrido")
                _selected.value = SelectedRoute(summary, gpx)
                val withEle = Http.withElevation(gpx)
                val loaded = withContext(Dispatchers.Default) { loadedFrom(withEle) }
                _selected.value = SelectedRoute(summary, withEle, loaded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _selected.value = SelectedRoute(summary, error = Http.friendly(e))
            }
        }
    }

    fun clearSelection() {
        selectJob?.cancel()
        _selected.value = null
    }

    companion object {
        /** Más alejado que esto la zona es enorme y la consulta tardaría demasiado. */
        const val MIN_ZOOM = 10.0
        const val ZOOM_HINT = "Acerca el mapa para ver las rutas"
    }
}
