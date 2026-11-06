package app.trazo.ride

import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.core.content.ContextCompat
import app.trazo.gpx.Activity
import app.trazo.gpx.ride.RideRecorder
import app.trazo.ui.AppMode
import app.trazo.gpx.ride.RideStats
import app.trazo.gpx.ride.TimedPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class RideState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val startedAtMs: Long = 0,
    val stats: RideStats = RideStats.EMPTY,
    /** Velocidad del último punto, en km/h. */
    val currentKmh: Double? = null,
    /** Lo grabado hasta ahora, para dibujarlo. */
    val points: List<List<TimedPoint>> = emptyList(),
    /** Si se graba siguiendo una ruta, su nombre. */
    val routeName: String? = null,
    val activity: Activity = Activity.BIKE,
)

/**
 * La salida en curso. La mantiene viva [RideService] (servicio en primer
 * plano), así que sigue grabando con la pantalla apagada. Las pantallas
 * solo la observan.
 */
object RideSession {

    private val _state = MutableStateFlow(RideState())
    val state: StateFlow<RideState> = _state.asStateFlow()

    /** Todas las posiciones del GPS mientras hay salida, también las no grabadas (para navegar). */
    private val _fixes = MutableSharedFlow<Location>(extraBufferCapacity = 32)
    val fixes: SharedFlow<Location> = _fixes.asSharedFlow()

    private var recorder = RideRecorder()

    fun start(context: Context, routeName: String? = null, activity: Activity = AppMode.current) {
        if (_state.value.active) return
        recorder = RideRecorder(activity)
        _state.value = RideState(active = true, startedAtMs = System.currentTimeMillis(), routeName = routeName, activity = activity)
        ContextCompat.startForegroundService(context, Intent(context, RideService::class.java))
    }

    fun pause() {
        recorder.pause()
        _state.value = _state.value.copy(paused = true, currentKmh = null)
    }

    fun resume() {
        recorder.resume()
        _state.value = _state.value.copy(paused = false)
    }

    /** Para y guarda. Devuelve el archivo de la salida, o null si no había nada que guardar. */
    fun stop(context: Context): File? {
        if (!_state.value.active) return null
        context.stopService(Intent(context, RideService::class.java))
        val points = recorder.points
        val activity = _state.value.activity
        val name = _state.value.routeName ?: if (activity == Activity.HIKE) "Excursión" else "Salida"
        _state.value = RideState()
        return if (points.sumOf { it.size } >= 2) RideStore.save(context, name, points, activity) else null
    }

    /** Lo llama el servicio con cada posición del GPS. */
    internal fun onLocation(loc: Location) {
        _fixes.tryEmit(loc)
        val s = _state.value
        if (!s.active) return
        val accepted = recorder.add(
            TimedPoint(
                loc.latitude, loc.longitude,
                if (loc.hasAltitude()) loc.altitude else null,
                loc.time,
                if (loc.hasAccuracy()) loc.accuracy else null,
            )
        )
        _state.value = s.copy(
            stats = if (accepted) recorder.stats() else s.stats,
            currentKmh = if (loc.hasSpeed()) loc.speed * 3.6 else s.currentKmh,
            points = if (accepted) recorder.points else s.points,
        )
    }
}
