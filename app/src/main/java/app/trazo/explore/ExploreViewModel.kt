package app.trazo.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.TrackStatsCalculator
import app.trazo.gpx.osm.OsmRouteSummary
import app.trazo.gpx.osm.Overpass
import app.trazo.importer.ImportState
import app.trazo.importer.loadedFrom
import app.trazo.nav.locationUpdates
import app.trazo.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class FoundRoute(val route: OsmRouteSummary, val metersFromYou: Double)

sealed interface ExploreState {
    data object Idle : ExploreState
    data object Locating : ExploreState
    data object Searching : ExploreState
    data class Results(val routes: List<FoundRoute>) : ExploreState
    data class Error(val message: String) : ExploreState
}

/** Vista previa de una ruta antes de abrirla: mientras se descarga, [loaded] es null. */
data class RoutePreview(
    val found: FoundRoute,
    val loaded: ImportState.Loaded? = null,
    val error: String? = null,
)

class ExploreViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<ExploreState>(ExploreState.Idle)
    val state: StateFlow<ExploreState> = _state.asStateFlow()

    private val _radiusKm = MutableStateFlow(30)
    val radiusKm: StateFlow<Int> = _radiusKm.asStateFlow()

    private val _onlyMtb = MutableStateFlow(false)
    val onlyMtb: StateFlow<Boolean> = _onlyMtb.asStateFlow()

    private val _preview = MutableStateFlow<RoutePreview?>(null)
    val preview: StateFlow<RoutePreview?> = _preview.asStateFlow()

    /** Rutas ya descargadas en esta sesión, para no repetir la descarga al volver a verlas. */
    private val cache = mutableMapOf<Long, ImportState.Loaded>()

    fun setRadius(km: Int) {
        if (_radiusKm.value == km) return
        _radiusKm.value = km
        if (_state.value !is ExploreState.Idle) search()
    }

    fun toggleOnlyMtb() {
        _onlyMtb.value = !_onlyMtb.value
    }

    fun search() {
        viewModelScope.launch {
            _state.value = ExploreState.Locating
            val here = withTimeoutOrNull(20_000) {
                runCatching { getApplication<Application>().locationUpdates().first() }.getOrNull()
            }
            if (here == null) {
                _state.value = ExploreState.Error("No consigo tu ubicación. Activa la ubicación y vuelve a probar.")
                return@launch
            }
            _state.value = ExploreState.Searching
            _state.value = runCatching {
                val json = Http.overpass(Overpass.searchQuery(here.latitude, here.longitude, _radiusKm.value * 1000))
                val me = GeoPoint(here.latitude, here.longitude)
                val routes = Overpass.parseSearch(json)
                    .map { FoundRoute(it, TrackStatsCalculator.haversineMeters(me, GeoPoint(it.lat, it.lon))) }
                    .sortedBy { it.metersFromYou }
                ExploreState.Results(routes)
            }.getOrElse { ExploreState.Error(Http.friendly(it)) }
        }
    }

    fun showPreview(found: FoundRoute) {
        val id = found.route.id
        cache[id]?.let { _preview.value = RoutePreview(found, it); return }
        _preview.value = RoutePreview(found)
        viewModelScope.launch {
            val result = runCatching {
                val gpx = Http.withElevation(Overpass.parseRoute(Http.overpass(Overpass.routeQuery(id)), id))
                withContext(Dispatchers.Default) { loadedFrom(gpx) }
            }
            // Si mientras tanto se cerró o se abrió otra, no pisamos nada
            if (_preview.value?.found?.route?.id != id) return@launch
            _preview.value = result.fold(
                onSuccess = { cache[id] = it; RoutePreview(found, it) },
                onFailure = { RoutePreview(found, error = Http.friendly(it)) },
            )
        }
    }

    fun retryPreview() {
        _preview.value?.found?.let(::showPreview)
    }

    fun closePreview() {
        _preview.value = null
    }
}
