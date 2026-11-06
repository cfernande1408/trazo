package app.trazo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trazo.community.CommunityScreen
import app.trazo.explore.ExploreMapScreen
import androidx.compose.material.icons.filled.Place
import app.trazo.explore.WikilocBrowser
import app.trazo.importer.ImportScreen
import app.trazo.importer.ImportState
import app.trazo.importer.ImportViewModel
import app.trazo.map.MapScreen
import app.trazo.map.Terrain3DScreen
import app.trazo.ride.RecordScreen
import app.trazo.ride.RideDetailScreen
import app.trazo.ride.RideSession
import app.trazo.ride.RidesScreen
import app.trazo.ride.formatDuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale
import app.trazo.ui.theme.TrazoTheme
import app.trazo.plan.PlannerScreen
import app.trazo.plan.RoutesLibrary
import app.trazo.plan.SavedRoute
import app.trazo.ui.AppMode
import app.trazo.ui.OnboardingScreen
import app.trazo.ui.outing
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {

    private val importer: ImportViewModel by viewModels()

    private val pickGpx = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(importer::import)
    }

    /** Pestaña seleccionada; también la cambia un GPX que llega por Abrir con / Compartir. */
    private var tab by mutableIntStateOf(0)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        AppMode.init(this)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val chosen by AppMode.activity.collectAsStateWithLifecycle()
            val seasonOverride by AppMode.seasonOverride.collectAsStateWithLifecycle()
            val season = seasonOverride ?: AppMode.todaySeason()
            val context = LocalContext.current
            // Primera vez: ¿biker o senderista?
            val activity = chosen
            if (activity == null) {
                TrazoTheme { OnboardingScreen(onChoose = { AppMode.set(context, it) }) }
            } else TrazoTheme(activity, season) {
                val state by importer.state.collectAsStateWithLifecycle()
                var showMap by rememberSaveable { mutableStateOf(false) }
                var show3D by rememberSaveable { mutableStateOf(false) }
                var showRecord by rememberSaveable { mutableStateOf(false) }
                var showRides by rememberSaveable { mutableStateOf(false) }
                var showPlanner by rememberSaveable { mutableStateOf(false) }
                /** Se incrementa al guardar o borrar para volver a leer "Tus rutas". */
                var libVersion by remember { mutableIntStateOf(0) }
                val library by produceState(emptyList<SavedRoute>(), libVersion) {
                    value = withContext(Dispatchers.IO) { RoutesLibrary.list(context) }
                }
                val scope = rememberCoroutineScope()
                /** Ruta del archivo de la salida abierta en detalle. */
                var rideFile by rememberSaveable { mutableStateOf<String?>(null) }
                val ride by RideSession.state.collectAsStateWithLifecycle()
                val loaded = state as? ImportState.Loaded
                val openRoute: (app.trazo.gpx.Gpx) -> Unit = { gpx ->
                    importer.importGpx(gpx)
                    showRides = false
                    rideFile = null
                    tab = 0
                }
                val openRide: (File) -> Unit = { f ->
                    showMap = false
                    showRecord = false
                    rideFile = f.absolutePath
                }

                when {
                    rideFile != null -> RideDetailScreen(
                        File(rideFile!!),
                        onBack = { rideFile = null },
                        onUseAsRoute = openRoute,
                        onDeleted = { rideFile = null; showRides = true },
                    )
                    showPlanner -> PlannerScreen(
                        onClose = { showPlanner = false },
                        onSaved = { gpx ->
                            libVersion++
                            showPlanner = false
                            openRoute(gpx)
                        },
                    )
                    showRecord -> RecordScreen(onClose = { showRecord = false }, onSaved = openRide)
                    show3D && loaded != null -> Terrain3DScreen(loaded, onBack = { show3D = false })
                    showMap && loaded != null -> MapScreen(
                        loaded,
                        onBack = { showMap = false },
                        onOpen3D = { show3D = true },
                        onRideSaved = openRide,
                    )
                    showRides -> RidesScreen(
                        onBack = { showRides = false },
                        onOpen = openRide,
                        onRecord = { showRides = false; showRecord = true },
                    )
                    else -> Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            Column {
                                // Si hay una salida grabándose y no se ve, un aviso para volver a ella
                                if (ride.active) {
                                    Surface(
                                        onClick = { if (ride.routeName != null && loaded != null) showMap = true else showRecord = true },
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            String.format(
                                                Locale.forLanguageTag("es-ES"), "%s %s · %.1f km · %s — toca para verla",
                                                if (ride.paused) "En pausa" else "Grabando",
                                                activity.outing,
                                                ride.stats.distanceMeters / 1000, formatDuration(ride.stats.movingMs),
                                            ),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            NavigationBar {
                                NavigationBarItem(
                                    selected = tab == 0, onClick = { tab = 0 },
                                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                                    label = { Text("Mis rutas") },
                                )
                                NavigationBarItem(
                                    selected = tab == 1, onClick = { tab = 1 },
                                    icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                                    label = { Text("Explorar") },
                                )
                                NavigationBarItem(
                                    selected = tab == 2, onClick = { tab = 2 },
                                    icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    label = { Text("Wikiloc") },
                                )
                                NavigationBarItem(
                                    selected = tab == 3, onClick = { tab = 3 },
                                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    label = { Text("Comunidad") },
                                )
                            }
                            }
                        },
                    ) { padding ->
                        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                            when (tab) {
                                0 -> ImportScreen(
                                    state,
                                    onPickFile = { pickGpx.launch(arrayOf("*/*")) },
                                    onOpenMap = { showMap = true },
                                    onRecord = { showRecord = true },
                                    onRides = { showRides = true },
                                    activity = activity,
                                    onSwitchActivity = { AppMode.set(context, it) },
                                    season = season,
                                    seasonOverride = seasonOverride,
                                    onSeason = { AppMode.setSeason(context, it) },
                                    onCreateRoute = { showPlanner = true },
                                    library = library,
                                    onOpenSaved = { r ->
                                        scope.launch {
                                            val gpx = withContext(Dispatchers.IO) { runCatching { RoutesLibrary.open(r.file) }.getOrNull() }
                                            if (gpx != null) importer.importGpx(gpx)
                                        }
                                    },
                                    onDeleteSaved = { r ->
                                        RoutesLibrary.delete(r)
                                        libVersion++
                                    },
                                    onSaveCurrent = {
                                        loaded?.let { l ->
                                            scope.launch {
                                                withContext(Dispatchers.IO) { RoutesLibrary.save(context, l.gpx) }
                                                libVersion++
                                            }
                                        }
                                    },
                                )
                                1 -> ExploreMapScreen(onRouteReady = openRoute, activity = activity)
                                2 -> WikilocBrowser(onGpx = openRoute)
                                else -> CommunityScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        uri?.let {
            tab = 0
            importer.import(it)
        }
    }
}
