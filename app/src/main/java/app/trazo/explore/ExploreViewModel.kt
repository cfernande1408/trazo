package app.trazo.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.Gpx
import app.trazo.gpx.TrackStatsCalculator
import app.trazo.gpx.osm.OsmRouteSummary
import app.trazo.gpx.osm.Overpass
import app.trazo.nav.locationUpdates
import app.trazo.net.Http
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class FoundRoute(val route: OsmRouteSummary, val metersFromYou: Double)

sealed interface ExploreState {
    data object Idle : ExploreState
    data object Locating : ExploreState
    data object Searching : ExploreState
    data class Results(val routes: List<FoundRoute>) : ExploreState
    data class Error(val message: String) : ExploreState
}

class ExploreViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<ExploreState>(ExploreState.Idle)
    val state: StateFlow<ExploreState> = _state.asStateFlow()

    private val _radiusKm = MutableStateFlow(30)
    val radiusKm: StateFlow<Int> = _radiusKm.asStateFlow()

    /** Ruta que se está descargando, para enseñar el progreso en su tarjeta. */
    private val _opening = MutableStateFlow<Long?>(null)
    val opening: StateFlow<Long?> = _opening.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setRadius(km: Int) {
        if (_radiusKm.value == km) return
        _radiusKm.value = km
        if (_state.value !is ExploreState.Idle) search()
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
            }.getOrElse { ExploreState.Error(it.message ?: "No se pudo buscar") }
        }
    }

    fun open(id: Long, onReady: (Gpx) -> Unit) {
        if (_opening.value != null) return
        _opening.value = id
        viewModelScope.launch {
            runCatching { Http.withElevation(Overpass.parseRoute(Http.overpass(Overpass.routeQuery(id)), id)) }
                .onSuccess(onReady)
                .onFailure { _message.value = it.message ?: "No se pudo descargar la ruta" }
            _opening.value = null
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
