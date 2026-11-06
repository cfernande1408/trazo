package app.trazo.importer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.trazo.gpx.Gpx
import app.trazo.gpx.GpxParser
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
    data class Loaded(val gpx: Gpx, val stats: TrackStats) : ImportState
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
                    if (gpx.segments.isEmpty()) error("El GPX no tiene ningún track")
                    ImportState.Loaded(gpx, TrackStatsCalculator.compute(gpx.segments))
                }.getOrElse { ImportState.Error(it.message ?: "Archivo no válido") }
            }
        }
    }
}
