package app.trazo.importer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.trazo.gpx.ElevationProfile
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.Gpx
import app.trazo.gpx.GpxParser
import app.trazo.gpx.ProfilePoint
import app.trazo.gpx.RouteGeometry
import app.trazo.gpx.TrackStats
import app.trazo.gpx.TrackStatsCalculator
import app.trazo.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una ruta realizada abierta para analizarla. */
data class DoneRide(val startedAtMs: Long, val stats: app.trazo.gpx.ride.RideStats)

sealed interface ImportState {
    data object Empty : ImportState
    data object Loading : ImportState
    data class Loaded(
        val gpx: Gpx,
        val stats: TrackStats,
        /** Track reducido para dibujar la silueta sin cargar miles de puntos. */
        val preview: List<List<GeoPoint>>,
        val profile: List<ProfilePoint>,
        /** Si es una ruta que has hecho y la abres para analizarla: cuándo y cómo te fue. */
        val done: DoneRide? = null,
    ) : ImportState
    data class Error(val message: String) : ImportState
}

class ImportViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<ImportState>(ImportState.Empty)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    fun import(uri: Uri) {
        _state.value = ImportState.Loading
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) {
                runCatching {
                    val gpx = getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.use(GpxParser::parse)
                        ?: error("No se pudo abrir el archivo")
                    loaded(gpx)
                }.getOrElse { ImportState.Error(it.message ?: "Archivo no válido") }
            }
            fillElevation()
        }
    }

    /** Para rutas que no vienen de un archivo: Explorar (OSM) o el navegador de Wikiloc. */
    fun importGpx(gpx: Gpx, done: DoneRide? = null) {
        _state.value = ImportState.Loading
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.Default) {
                runCatching { loaded(gpx).copy(done = done) }.getOrElse { ImportState.Error(it.message ?: "Ruta no válida") }
            }
            fillElevation()
        }
    }

    /** De analizar una ruta realizada a prepararla para repetirla. */
    fun plan() {
        (_state.value as? ImportState.Loaded)?.let { _state.value = it.copy(done = null) }
    }

    /** Cierra la ruta abierta y vuelve a la portada de Inicio. */
    fun clear() {
        _state.value = ImportState.Empty
    }

    /**
     * Si el GPX no trae altitudes (rutas creadas en otras apps, algunos
     * exportes), se piden a Open-Meteo y se rehace el resumen con perfil.
     */
    private suspend fun fillElevation() {
        val current = _state.value as? ImportState.Loaded ?: return
        if (current.profile.isNotEmpty()) return
        val withEle = Http.withElevation(current.gpx)
        if (withEle === current.gpx) return
        // Solo si sigue abierta la misma ruta
        if (_state.value === current) _state.value = withContext(Dispatchers.Default) { loadedFrom(withEle).copy(done = current.done) }
    }

    private fun loaded(gpx: Gpx): ImportState.Loaded = loadedFrom(gpx)
}

/** Todo lo que necesitan el resumen, el mapa y las vistas previas a partir de un Gpx. */
fun loadedFrom(gpx: Gpx): ImportState.Loaded {
    if (gpx.segments.isEmpty()) error("El GPX no tiene ningún track")
    return ImportState.Loaded(
        gpx = gpx,
        stats = TrackStatsCalculator.compute(gpx.segments),
        preview = gpx.segments.map { RouteGeometry.downsample(it, 400) },
        profile = RouteGeometry.downsample(ElevationProfile.of(gpx.segments), 300),
    )
}
