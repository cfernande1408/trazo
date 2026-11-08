package app.trazo

import app.trazo.gpx.GpxParser
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Bookmarks
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
import androidx.compose.material.icons.filled.Info
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
import app.trazo.ui.SeasonIcon
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

    /** Se incrementa al guardar o borrar para volver a leer "Tus rutas". */
    private var libVersion by mutableIntStateOf(0)

    /** Importar GPX desde Guardadas: uno o varios, y se guardan directamente. */
    private val importToLibrary = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                uris.count { uri ->
                    runCatching {
                        val gpx = contentResolver.openInputStream(uri)!!.use(GpxParser::parse)
                        check(gpx.segments.any { it.size >= 2 }) { "Sin recorrido" }
                        RoutesLibrary.save(this@MainActivity, gpx)
                    }.isSuccess
                }
            }
            libVersion++
            val failed = uris.size - saved
            Toast.makeText(
                this@MainActivity,
                when {
                    failed == 0 -> if (saved == 1) "Ruta guardada" else "$saved rutas guardadas"
                    saved == 0 -> "No se pudo leer ningún GPX"
                    else -> "$saved guardadas, $failed no se pudieron leer"
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        AppMode.init(this)
        app.trazo.ui.UserProfile.init(this)
        app.trazo.map.MapPrefs.init(this)
        app.trazo.offline.OfflineMaps.refresh(this)
        app.trazo.safety.Safety.init(this)
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
                // También al entrar en Guardadas: descargar el mapa de una ruta la guarda por detrás
                val library by produceState(emptyList<SavedRoute>(), libVersion, tab == TAB_LIBRARY) {
                    value = withContext(Dispatchers.IO) { RoutesLibrary.list(context) }
                }
                val scope = rememberCoroutineScope()
                /** Zona a la que llevar Explorar (al tocar un mapa descargado en Guardadas). */
                var exploreFocus by remember { mutableStateOf<org.maplibre.android.geometry.LatLngBounds?>(null) }
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
                        // Si venías de Guardadas, vuelves allí; si no, a la lista de salidas
                        onDeleted = { rideFile = null; if (tab != TAB_LIBRARY) showRides = true },
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
                            ScrollingNavBar(TABS, selected = tab, onSelect = { tab = it })
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
                                    activity = activity,
                                    onSwitchActivity = { AppMode.set(context, it) },
                                    season = season,
                                    seasonOverride = seasonOverride,
                                    onSeason = { AppMode.setSeason(context, it) },
                                    onCreateRoute = { showPlanner = true },
                                    onExplore = { tab = 1 },
                                    onWikiloc = { tab = 2 },
                                    onLibrary = { tab = TAB_LIBRARY },
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
                                1 -> ExploreMapScreen(
                                    onRouteReady = openRoute, activity = activity,
                                    focus = exploreFocus, onFocused = { exploreFocus = null },
                                )
                                2 -> WikilocBrowser(onGpx = openRoute)
                                3 -> app.trazo.importer.LibraryScreen(
                                    activity = activity,
                                    routes = library,
                                    onOpenRoute = { r ->
                                        scope.launch {
                                            val gpx = withContext(Dispatchers.IO) { runCatching { RoutesLibrary.open(r.file) }.getOrNull() }
                                            if (gpx != null) openRoute(gpx)
                                        }
                                    },
                                    onDeleteRoute = { r ->
                                        RoutesLibrary.delete(r)
                                        libVersion++
                                    },
                                    onOpenRide = openRide,
                                    onRecord = { showRecord = true },
                                    onImport = { importToLibrary.launch(arrayOf("*/*")) },
                                    onOpenMap = { m ->
                                        scope.launch {
                                            // El mapa de una ruta abre esa ruta; el de una zona, la zona en Explorar
                                            val gpx = withContext(Dispatchers.IO) { runCatching { RoutesLibrary.findByKey(context, m.meta.routeKey) }.getOrNull() }
                                            if (gpx != null) openRoute(gpx)
                                            else (m.region.definition as? org.maplibre.android.offline.OfflineTilePyramidRegionDefinition)?.bounds?.let {
                                                if (!m.meta.routeKey.startsWith("zona-")) {
                                                    Toast.makeText(context, "La ruta de este mapa no está guardada: te enseño su zona", Toast.LENGTH_LONG).show()
                                                }
                                                exploreFocus = it
                                                tab = 1
                                            }
                                        }
                                    },
                                )
                                4 -> CommunityScreen()
                                else -> app.trazo.ui.AboutScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Al salir, el icono del escritorio pasa a la estación que toca (o la elegida a mano)
        runCatching { SeasonIcon.apply(this, AppMode.season) }
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

/** Pestaña de Guardadas (rutas, salidas y mapas). */
private const val TAB_LIBRARY = 3

private data class NavTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    NavTab("Inicio", Icons.Filled.Home),
    NavTab("Explorar", Icons.Filled.Place),
    NavTab("Wikiloc", Icons.Filled.Search),
    NavTab("Guardadas", Icons.Filled.Bookmarks),
    NavTab("Comunidad", Icons.Filled.Person),
    NavTab("Info", Icons.Filled.Info),
)

/**
 * Barra de abajo que se desliza en horizontal: caben todas las pestañas
 * aunque sean más de cinco o la letra del sistema sea grande. Se ven cinco
 * y media, para que se note que hay más.
 */
@androidx.compose.runtime.Composable
private fun ScrollingNavBar(tabs: List<NavTab>, selected: Int, onSelect: (Int) -> Unit) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding()) {
            val itemWidth = maxWidth / 5.5f
            // La pestaña elegida (p. ej. al abrir un GPX desde fuera) siempre a la vista
            androidx.compose.runtime.LaunchedEffect(selected) {
                val px = with(density) { itemWidth.toPx() }
                scroll.animateScrollTo(((selected - 2) * px).toInt().coerceAtLeast(0))
            }
            Row(Modifier.horizontalScroll(scroll).height(80.dp)) {
                tabs.forEachIndexed { i, t ->
                    val sel = i == selected
                    Column(
                        Modifier
                            .widthIn(min = itemWidth)
                            .fillMaxHeight()
                            .selectable(selected = sel, onClick = { onSelect(i) }, role = androidx.compose.ui.semantics.Role.Tab)
                            .padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier
                                .size(width = 56.dp, height = 32.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                t.icon, contentDescription = null,
                                tint = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            t.label,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (sel) androidx.compose.ui.text.font.FontWeight.Bold else null,
                            color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
