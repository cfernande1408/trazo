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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportState {
    data object Empty : ImportState
    data object Loading : ImportState
    data class Loaded(
        val gpx: Gpx,
        val stats: TrackStats,
        /** Track reducido para dibujar la silueta sin cargar miles de puntos. */
        val preview: List<List<GeoPoint>>,
        val profile: List<ProfilePoint>,
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
        }
    }

    /** Para rutas que no vienen de un archivo: Explorar (OSM) o el navegador de Wikiloc. */
    fun importGpx(gpx: Gpx) {
        _state.value = ImportState.Loading
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.Default) {
                runCatching { loaded(gpx) }.getOrElse { ImportState.Error(it.message ?: "Ruta no válida") }
            }
        }
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
