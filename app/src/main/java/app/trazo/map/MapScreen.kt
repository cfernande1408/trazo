package app.trazo.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.trazo.gpx.ElevationProfile
import app.trazo.gpx.RouteGeometry
import app.trazo.gpx.nav.NavState
import app.trazo.gpx.nav.Navigator
import app.trazo.gpx.nav.RouteStart
import app.trazo.gpx.nav.StartOption
import app.trazo.ui.openDirections
import app.trazo.ui.ScrubbableProfile
import app.trazo.gpx.GeoJson
import org.maplibre.android.style.sources.GeoJsonSource
import app.trazo.importer.ES
import app.trazo.importer.ImportState
import app.trazo.importer.Stat
import app.trazo.importer.km
import app.trazo.importer.metros
import app.trazo.importer.title
import app.trazo.nav.Alerts
import app.trazo.ride.RideSession
import app.trazo.ui.AppMode
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import app.trazo.gpx.osm.Bbox
import app.trazo.gpx.community.Hazard
import app.trazo.community.showHazards
import app.trazo.community.hazardAt
import app.trazo.community.Supabase
import app.trazo.community.ReportDialog
import app.trazo.community.ReportButton
import app.trazo.community.HazardDialog
import app.trazo.community.CommunityRepo
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import app.trazo.sensors.SensorsSheet
import app.trazo.sensors.SensorsButton
import app.trazo.sensors.BleSensors
import app.trazo.ride.formatDuration
import app.trazo.ride.RideStore
import app.trazo.gpx.nav.VirtualPartner
import app.trazo.gpx.nav.DataField
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import app.trazo.safety.SosSheet
import app.trazo.safety.SosButton
import app.trazo.net.Http
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.GpxParser
import app.trazo.gpx.plan.BRouter
import app.trazo.gpx.nav.Retrace
import androidx.compose.runtime.produceState
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.CircleLayer
import app.trazo.nav.Voice
import app.trazo.nav.RoutePois
import app.trazo.gpx.osm.PoiOnRoute
import app.trazo.gpx.osm.PoiKind
import app.trazo.gpx.nav.VoiceGuide
import app.trazo.gpx.nav.Turns
import app.trazo.gpx.nav.ClimbProgress
import app.trazo.gpx.nav.ClimbLive
import app.trazo.gpx.nav.ClimbDetector
import app.trazo.gpx.Daylight
import androidx.compose.material3.Surface
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.expressions.Expression
import app.trazo.gpx.Slope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trazo.ride.ridePermissions
import java.io.File
import app.trazo.ui.ElevationChart
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Por encima de este error se descarta la posición. Por debajo se usa y el
 * navegador descuenta el error antes de decidir si te has desviado.
 */
private const val MAX_ACCURACY_M = 1_000f
private const val REPEAT_ALERT_MS = 20_000L

@Composable
fun MapScreen(
    state: ImportState.Loaded,
    onBack: () -> Unit,
    onOpen3D: () -> Unit,
    onRideSaved: (File) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var scrub by remember(state.gpx) { mutableStateOf<Double?>(null) }

    var navigating by rememberSaveable { mutableStateOf(false) }
    /** Desde qué punto del track original se empieza (0 = inicio). */
    var startAlong by rememberSaveable { mutableStateOf(0.0) }
    var showStartSheet by remember { mutableStateOf(false) }
    val startOptions = remember(state.gpx) { RouteStart.options(state.gpx) }
    /** Vuelta al inicio (por donde has venido o por el camino más corto); null si sigues la ruta. */
    var returnSegs by remember { mutableStateOf<List<List<GeoPoint>>?>(null) }
    var showReturn by remember { mutableStateOf(false) }
    var showSos by remember { mutableStateOf(false) }
    val routeSegments = remember(state.gpx, startAlong) { RouteStart.from(state.gpx.segments, startAlong) }
    val navSegments = returnSegs ?: routeSegments
    val navProfile = remember(navSegments) { RouteGeometry.downsample(ElevationProfile.of(navSegments), 300) }
    var navState by remember { mutableStateOf<NavState?>(null) }
    var navError by remember { mutableStateOf<String?>(null) }
    var extras by remember { mutableStateOf(NavExtras()) }
    var returnError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Lo que se calcula una vez por ruta para la navegación: giros, subidas y pendientes
    val fullProfile = remember(navSegments) { ElevationProfile.of(navSegments) }
    val grades = remember(fullProfile) { Slope.grades(fullProfile) }
    val climbs = remember(fullProfile) { ClimbDetector.detect(fullProfile) }
    val turns = remember(navSegments) { Turns.detect(navSegments) }
    val pois by produceState(emptyList<PoiOnRoute>(), navSegments) {
        value = runCatching { RoutePois.load(navSegments) }.getOrDefault(emptyList())
    }
    val prefs = remember { context.getSharedPreferences("trazo", android.content.Context.MODE_PRIVATE) }
    var voiceOn by remember { mutableStateOf(prefs.getBoolean("voice", true)) }
    val voice = remember { Voice(context) }
    DisposableEffect(Unit) { onDispose { voice.release() } }

    // Avisos de la comunidad en la zona de la ruta
    var hazardsReload by remember { mutableIntStateOf(0) }
    val hazards by produceState(emptyList<Hazard>(), routeSegments, hazardsReload) {
        val b = RouteGeometry.bounds(routeSegments) ?: return@produceState
        if (!Supabase.configured) return@produceState
        value = runCatching { CommunityRepo.hazards(context, Bbox(b.minLat - 0.02, b.minLon - 0.02, b.maxLat + 0.02, b.maxLon + 0.02)) }
            .getOrDefault(emptyList())
    }
    val hazardsNow = rememberUpdatedState(hazards)
    var openHazard by remember { mutableStateOf<Hazard?>(null) }
    var reporting by remember { mutableStateOf(false) }

    // Pantalla de datos configurable y compañero virtual
    var fields by remember { mutableStateOf(DataField.decode(prefs.getString("nav_fields", null))) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var showSensors by remember { mutableStateOf(false) }
    val ride by RideSession.state.collectAsStateWithLifecycle()
    val sensorTick by BleSensors.readings.collectAsStateWithLifecycle()
    val partner by produceState<VirtualPartner?>(null, routeSegments) {
        value = withContext(Dispatchers.IO) {
            // La salida anterior más reciente con el mismo nombre de ruta que cubra el recorrido
            RideStore.list(context).asSequence()
                .filter { it.name == state.title() }
                .map { VirtualPartner(it.points, routeSegments) }
                .firstOrNull { it.usable }
        }
    }
    val partnerGap = navState?.let { st -> partner?.gapMs(st.alongMeters, System.currentTimeMillis() - ride.startedAtMs) }

    // Navegar = grabar la salida: el servicio mantiene el GPS y los avisos con la pantalla apagada
    val startNav = {
        RideSession.start(context, routeName = state.title())
        navigating = true
    }
    val stopNav = {
        navigating = false
        RideSession.stop(context)?.let(onRideSaved)
        Unit
    }
    BackHandler { if (navigating) stopNav() else onBack() }

    var hasLocation by remember { mutableStateOf(context.hasLocationPermission()) }
    var startWhenGranted by remember { mutableStateOf(false) }
    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasLocation = context.hasLocationPermission()
        if (hasLocation && startWhenGranted) startNav()
        startWhenGranted = false
    }
    val locationPermissions = ridePermissions()
    LaunchedEffect(Unit) { if (!hasLocation) askLocation.launch(locationPermissions) }

    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var ready by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }
    val layers by MapPrefs.state.collectAsStateWithLifecycle()
    var showLayers by remember { mutableStateOf(false) }
    LaunchedEffect(ready, layers) { ready?.second?.applyLayers(layers, AppMode.current) }

    // Camino de vuelta en azul discontinuo, encima de la ruta
    LaunchedEffect(ready, returnSegs) {
        val (_, style) = ready ?: return@LaunchedEffect
        val json = GeoJson.track(returnSegs.orEmpty())
        val src = style.getSourceAs<GeoJsonSource>(RETURN)
        if (src != null) { src.setGeoJson(json); return@LaunchedEffect }
        style.addSource(GeoJsonSource(RETURN, json))
        val line = LineLayer(RETURN, RETURN).withProperties(
            PropertyFactory.lineColor("#1565C0"),
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineDasharray(arrayOf(1.5f, 1f)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        )
        if (style.getLayer("track-line") != null) style.addLayerAbove(line, "track-line") else style.addLayer(line)
    }

    LaunchedEffect(ready, hazards) { ready?.second?.showHazards(hazards) }

    // Fuentes y refugios en el mapa
    LaunchedEffect(ready, pois) {
        val (_, style) = ready ?: return@LaunchedEffect
        val json = pois.joinToString(prefix = """{"type":"FeatureCollection","features":[""", postfix = "]}") { p ->
            """{"type":"Feature","properties":{"k":"${p.poi.kind.name}","name":"${GeoJson.escape(p.poi.name ?: p.poi.kind.label)}"},""" +
                """"geometry":{"type":"Point","coordinates":[${p.poi.lon},${p.poi.lat}]}}"""
        }
        val src = style.getSourceAs<GeoJsonSource>(POIS)
        if (src != null) { src.setGeoJson(json); return@LaunchedEffect }
        style.addSource(GeoJsonSource(POIS, json))
        val stops = PoiKind.entries.map { Expression.stop(it.name, Expression.color(android.graphics.Color.parseColor(it.color))) }
        style.addLayer(
            CircleLayer(POIS, POIS).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(Expression.match(Expression.get("k"), Expression.color(android.graphics.Color.GRAY), *stops.toTypedArray())),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2.5f),
            )
        )
        style.addLayer(
            SymbolLayer("$POIS-name", POIS).withProperties(
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textSize(11f),
                PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
                PropertyFactory.textColor("#1A1C16"),
                PropertyFactory.textHaloColor("#FFFFFF"),
                PropertyFactory.textHaloWidth(1.5f),
            ).apply { minZoom = 13f }
        )
    }

    // Ruta coloreada por pendiente, encima de la línea normal
    val slopeLines = remember(state) { Slope.lines(state.gpx.segments, Slope.grades(ElevationProfile.of(state.gpx.segments))) }
    LaunchedEffect(ready, layers.slope) {
        val (_, style) = ready ?: return@LaunchedEffect
        style.getLayer(SLOPE)?.let { style.removeLayer(it) }
        style.getSource(SLOPE)?.let { style.removeSource(it) }
        if (!layers.slope) return@LaunchedEffect
        style.addSource(GeoJsonSource(SLOPE, slopeLines))
        val stops = Slope.COLORS.mapIndexed { i, c -> Expression.stop(i, Expression.color(android.graphics.Color.parseColor(c))) }
        val line = LineLayer(SLOPE, SLOPE).withProperties(
            PropertyFactory.lineColor(
                Expression.match(Expression.get("b"), Expression.color(android.graphics.Color.parseColor(Slope.COLORS.last())), *stops.toTypedArray())
            ),
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        if (style.getLayer("track-line") != null) style.addLayerAbove(line, "track-line") else style.addLayer(line)
    }

    // El MapView necesita que le reenvíen el ciclo de vida.
    DisposableEffect(lifecycle, mapView) {
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> { mapView.onDestroy(); destroyed = true }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (!destroyed) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            // Cámara en la ruta desde el principio, sin esperar a que cargue el estilo
            RouteGeometry.bounds(state.gpx.segments)?.let { b ->
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng((b.minLat + b.maxLat) / 2, (b.minLon + b.maxLon) / 2))
                    .zoom(12.0)
                    .build()
            }
            map.setStyle(TrazoMapStyle.builder(context, state)) { style ->
                mapView.post { fitRoute(map, state, context.resources.displayMetrics.density) }
                map.addOnMapClickListener { ll ->
                    map.hazardAt(ll, hazardsNow.value, context.resources.displayMetrics.density)?.let { openHazard = it } != null
                }
                ready = map to style
            }
        }
    }

    // Posición en el mapa y cámara: siguiéndote al navegar, libre si no.
    var wasNavigating by remember { mutableStateOf(false) }
    LaunchedEffect(ready, hasLocation, navigating) {
        val (map, style) = ready ?: return@LaunchedEffect
        if (!hasLocation) return@LaunchedEffect
        map.showUserLocation(context, style)
        map.locationComponent.apply {
            if (navigating) {
                cameraMode = CameraMode.TRACKING_GPS
                renderMode = RenderMode.GPS
                zoomWhileTracking(16.0)
            } else {
                cameraMode = CameraMode.NONE
                renderMode = RenderMode.COMPASS
            }
        }
        if (wasNavigating && !navigating) fitRoute(map, state, context.resources.displayMetrics.density)
        wasNavigating = navigating
    }

    // El punto del perfil bajo el dedo se dibuja en el mapa y, si se sale, la cámara lo sigue
    LaunchedEffect(ready, scrub, navigating) {
        val (map, style) = ready ?: return@LaunchedEffect
        val p = scrub?.takeIf { !navigating }?.let { RouteGeometry.pointAt(state.gpx.segments, it) }
        style.getSourceAs<GeoJsonSource>(TrazoMapStyle.CURSOR)
            ?.setGeoJson(GeoJson.points(listOfNotNull(p?.let { it.lat to it.lon })))
        if (p != null) {
            val here = LatLng(p.lat, p.lon)
            if (!map.projection.visibleRegion.latLngBounds.contains(here)) {
                map.animateCamera(CameraUpdateFactory.newLatLng(here), 300)
            }
        }
    }

    // Pantalla encendida mientras navegas
    val view = LocalView.current
    DisposableEffect(navigating) {
        view.keepScreenOn = navigating
        onDispose { view.keepScreenOn = false }
    }

    val alerts = remember { Alerts(context) }
    DisposableEffect(Unit) { onDispose { alerts.release() } }

    // Con las fuentes ya cargadas se rehace la guía de voz (llegan unos segundos después)
    LaunchedEffect(navigating, hasLocation, navSegments, pois) {
        if (!navigating || !hasLocation) return@LaunchedEffect
        navError = null
        navState = null
        val activity = AppMode.current
        val navigator = Navigator(
            navSegments,
            offRouteMeters = activity.offRouteMeters,
            backOnRouteMeters = activity.backOnRouteMeters,
            offRouteDelayMs = activity.offRouteDelayMs,
        )
        var lastAlert = 0L
        var wasOff = false
        val guide = VoiceGuide(turns, climbs, pois, activity)
        // Ritmo real frente al estimado, para calcular cuánto te queda de verdad
        var startT: Long? = null
        var startAlongNav = 0.0
        var startRemainingGain = 0.0
        // Las posiciones llegan del servicio de grabación, que sigue vivo con la pantalla apagada
        // (SharedFlow: nunca termina ni lanza, no hace falta catch)
        RideSession.fixes
            .collect { loc ->
                // Posiciones absurdamente imprecisas (p. ej. solo por antena) no aportan nada
                if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return@collect
                val t = loc.elapsedRealtimeNanos / 1_000_000
                val accuracy = if (loc.hasAccuracy()) loc.accuracy.toDouble() else 0.0
                val s = navigator.update(loc.latitude, loc.longitude, t, accuracy)
                navState = s
                if (startT == null) { startT = t; startAlongNav = s.alongMeters; startRemainingGain = s.remainingGainMeters }
                // Cuánto falta: lo estimado para lo que queda, corregido por cómo vas hasta ahora
                val doneM = s.alongMeters - startAlongNav
                val expectedDone = activity.estimateMs(doneM, (startRemainingGain - s.remainingGainMeters).coerceAtLeast(0.0), 0.0)
                val pace = if (doneM > 1_500 && expectedDone > 0) ((t - startT!!).toDouble() / expectedDone).coerceIn(0.5, 2.5) else 1.0
                val eleNow = ElevationProfile.eleAt(fullProfile, s.alongMeters)
                val eleEnd = fullProfile.lastOrNull()?.ele
                val loss = s.remainingGainMeters + ((eleNow ?: 0.0) - (eleEnd ?: 0.0)).coerceAtLeast(-s.remainingGainMeters)
                val remainingMs = (activity.estimateMs(s.remainingMeters, s.remainingGainMeters, loss) * pace).toLong()
                val nowMs = System.currentTimeMillis()
                val sunset = Daylight.sunsetMs(loc.latitude, loc.longitude, Math.floorDiv(nowMs, 86_400_000L))
                // Ya de noche no tiene sentido avisar de la luz que queda
                val margin = sunset?.takeIf { it > nowMs }?.let { Daylight.marginMinutes(nowMs, it, remainingMs) }
                extras = NavExtras(
                    climb = ClimbLive.progress(climbs, fullProfile, grades, s.alongMeters),
                    nextPoi = pois.firstOrNull { it.alongMeters >= s.alongMeters - 20 }?.let { it to it.alongMeters - s.alongMeters },
                    lightMarginMinutes = margin,
                    finishAtMs = nowMs + remainingMs,
                )
                val phrases = guide.update(s, margin)
                if (voiceOn) phrases.forEach(voice::say)
                when {
                    s.offRouteStarted -> { alerts.offRoute(); lastAlert = t }
                    s.offRoute && t - lastAlert >= REPEAT_ALERT_MS -> { alerts.offRoute(); lastAlert = t }
                    wasOff && !s.offRoute -> alerts.backOnRoute()
                }
                wasOff = s.offRoute
            }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        if (navigating) {
            Column(Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NavBanner(
                    navState, extras, Modifier.fillMaxWidth(),
                    voiceOn = voiceOn,
                    onToggleVoice = {
                        voiceOn = !voiceOn
                        prefs.edit().putBoolean("voice", voiceOn).apply()
                    },
                )
                NavChips(extras)
            }
        } else {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.statusBarsPadding().padding(16.dp).size(56.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            FilledTonalButton(
                onClick = onOpen3D,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).height(56.dp),
            ) {
                Text("3D", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LayersButton(onClick = { showLayers = true })
            SensorsButton(onClick = { showSensors = true })
            ReportButton(onClick = { reporting = true })
            SosButton(onClick = { showSos = true })
        }
        if (reporting) ReportDialog(onDismiss = { reporting = false }, onSent = { reporting = false; hazardsReload++ })
        openHazard?.let { h -> HazardDialog(h, onDismiss = { openHazard = null }, onVoted = { openHazard = null; hazardsReload++ }) }
        if (showSensors) SensorsSheet(onDismiss = { showSensors = false })
        picking?.let { slot ->
            FieldPicker(
                current = fields[slot],
                onDismiss = { picking = null },
                onPick = { f ->
                    fields = fields.toMutableList().also { it[slot] = f }
                    prefs.edit().putString("nav_fields", DataField.encode(fields)).apply()
                    picking = null
                },
            )
        }
        if (showSos) SosSheet(onDismiss = { showSos = false })
        if (showReturn) {
            ReturnDialog(
                onDismiss = { showReturn = false },
                onRetrace = {
                    showReturn = false
                    val along = navState?.alongMeters ?: 0.0
                    Retrace.backToStart(routeSegments, along).takeIf { it.isNotEmpty() }?.let { returnSegs = it }
                },
                onShortest = {
                    showReturn = false
                    val here = RideSession.lastLocation
                    val start = routeSegments.firstOrNull()?.firstOrNull()
                    if (here != null && start != null) {
                        scope.launch {
                            returnError = null
                            val pts = listOf(GeoPoint(here.latitude, here.longitude), start)
                            runCatching {
                                val (code, body) = Http.getWithStatus(BRouter.url(pts, AppMode.current))
                                check(code in 200..299) { BRouter.friendlyError(body) }
                                GpxParser.parse(body).segments
                            }.onSuccess { returnSegs = it }
                                .onFailure { returnError = it.message ?: "No se pudo calcular. Prueba «por donde he venido»." }
                        }
                    }
                },
            )
        }
        if (showLayers) {
            LayersSheet(
                layers, AppMode.current, onChange = { MapPrefs.set(context, it) }, onDismiss = { showLayers = false },
                showSlope = true,
            )
        }
        if (layers.slope && !navigating) {
            SlopeLegend(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 84.dp))
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val s = navState
                if (navigating) {
                    // Seis cifras a tu gusto: toca una para cambiarla
                    val values = NavValues(s, extras, ride, sensorTick.takeIf { BleSensors.fresh() != null }, partnerGap, fullProfile, grades)
                    fields.chunked(3).forEachIndexed { row, chunk ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            chunk.forEachIndexed { col, f ->
                                Stat(f.label, values.text(f), Modifier.weight(1f).clickable { picking = row * 3 + col })
                            }
                        }
                    }
                    if (navProfile.size >= 2) {
                        ElevationChart(
                            navProfile,
                            Modifier.fillMaxWidth().height(72.dp),
                            positionMeters = s?.alongMeters,
                            highlight = s?.nextClimb?.let { it.startMeters..it.endMeters },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { if (returnSegs != null) returnSegs = null else showReturn = true },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) {
                            Text(if (returnSegs != null) "Seguir la ruta" else "Volver al inicio")
                        }
                        FilledTonalButton(onClick = stopNav, modifier = Modifier.weight(1f).height(56.dp)) {
                            Text("Terminar y guardar")
                        }
                    }
                } else {
                    Text(
                        state.title(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("Distancia", km(state.stats.distanceMeters), Modifier.weight(1f))
                        Stat("Desnivel +", metros(state.stats.elevationGainMeters), Modifier.weight(1f))
                    }
                    if (state.profile.size >= 2) {
                        ScrubbableProfile(state.profile, scrub, onScrub = { scrub = it }, chartHeight = 72.dp)
                    }
                    navError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    returnError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (returnSegs != null) Text("Volviendo al inicio", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Button(
                        onClick = { showStartSheet = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Empezar ruta", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (showStartSheet) {
        StartSheet(
            options = startOptions,
            isLoop = remember(state.gpx) { RouteStart.isLoop(state.gpx.segments) },
            onDismiss = { showStartSheet = false },
            onStart = { option ->
                showStartSheet = false
                startAlong = option.alongMeters
                if (hasLocation) startNav()
                else { startWhenGranted = true; askLocation.launch(locationPermissions) }
            },
            onDirections = { option -> context.openDirections(option.lat, option.lon, option.label) },
        )
    }
}

/** ¿Desde dónde empiezas? Cada opción se puede iniciar o abrir en el navegador del coche. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartSheet(
    options: List<StartOption>,
    isLoop: Boolean,
    onDismiss: () -> Unit,
    onStart: (StartOption) -> Unit,
    onDirections: (StartOption) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("¿Desde dónde empiezas?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                if (isLoop) "Es circular: acabarás en el mismo sitio donde empieces."
                else "No es circular: lo anterior al punto elegido se salta.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            options.forEach { o ->
                Card(
                    onClick = { onStart(o) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (o.alongMeters == 0.0) "Punto de salida del GPX" else "En el km ${km(o.alongMeters).removeSuffix(" km")} de la ruta",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(onClick = { onDirections(o) }) {
                            Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cómo llegar")
                        }
                    }
                }
            }
        }
    }
}

/** Tarjeta de arriba al navegar: desvío, subida en curso o próxima subida. */
/** Lo que acompaña a la navegación: subida en curso, próximo punto de agua y luz. */
private data class NavExtras(
    val climb: ClimbProgress? = null,
    /** Punto y metros hasta él. */
    val nextPoi: Pair<PoiOnRoute, Double>? = null,
    val lightMarginMinutes: Long? = null,
    val finishAtMs: Long? = null,
)

@Composable
private fun NavBanner(
    s: NavState?,
    extras: NavExtras,
    modifier: Modifier = Modifier,
    voiceOn: Boolean,
    onToggleVoice: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when {
        s?.offRoute == true -> Color(0xFFBA1A1A) to Color.White
        s == null -> scheme.surfaceContainer to scheme.onSurface
        else -> scheme.primaryContainer to scheme.onPrimaryContainer
    }
    val climb = extras.climb.takeIf { s?.offRoute != true }
    val (title, subtitle) = when {
        s == null -> "Buscando GPS…" to "Sal a cielo abierto si tarda"
        s.offRoute -> "Fuera de ruta" to "A ${corta(s.distanceToTrackMeters)} del track"
        // Subida en directo, como el ClimbPro de los Garmin
        climb != null ->
            "Subida ${climb.number} de ${climb.total} · ${String.format(ES, "%.0f", climb.gradePercent)} %" to
                "Quedan ${corta(climb.remainingMeters)} y +${metros(climb.remainingGainMeters)}"
        s.nextClimb != null -> {
            val c = s.nextClimb!!
            "Próxima subida en ${corta(s.metersToNextClimb ?: 0.0)}" to
                "${corta(c.lengthMeters)} al ${String.format(ES, "%.0f", c.avgGradePercent)} % · +${metros(c.gainMeters)}"
        }
        else -> "Sin más subidas" to "Quedan ${km(s.remainingMeters)}"
    }
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (s?.offRoute == true) Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = onToggleVoice) {
                    Icon(
                        if (voiceOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (voiceOn) "Silenciar avisos de voz" else "Activar avisos de voz",
                    )
                }
            }
            if (climb != null && climb.profile.size >= 2) {
                // El gráfico empieza en 0: perfil y posición relativos al pie de la subida
                val start = climb.climb.startMeters
                ElevationChart(
                    climb.profile.map { app.trazo.gpx.ProfilePoint(it.distanceMeters - start, it.ele) },
                    Modifier.padding(top = 10.dp, end = 12.dp).fillMaxWidth().height(56.dp),
                    positionMeters = climb.doneMeters,
                )
            }
        }
    }
}

/** Próxima fuente o refugio y cuánta luz queda, en pastillas bajo la tarjeta. */
@Composable
private fun NavChips(extras: NavExtras) {
    val poi = extras.nextPoi
    val light = extras.lightMarginMinutes
    if (poi == null && light == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        poi?.let { (p, ahead) ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    "${p.poi.kind.emoji} ${p.poi.kind.label} en ${corta(ahead.coerceAtLeast(0.0))}",
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        light?.let { m ->
            val late = m < 0
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (late) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (late) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    if (late) "🌙 Anochece ${durationText(-m)} antes de terminar" else "☀️ Te sobran ${durationText(m)} de luz",
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** 45 min / 1 h 20 min */
private fun durationText(minutes: Long) =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h" + if (minutes % 60 > 0) " ${minutes % 60} min" else ""

/** 450 m / 1,8 km */
private fun corta(m: Double) = if (m < 1000) String.format(ES, "%.0f m", m) else km(m)

private fun fitRoute(map: MapLibreMap, state: ImportState.Loaded, density: Float) {
    val bounds = TrazoMapStyle.bounds(state) ?: return
    val pad = (48 * density).toInt()
    // Arriba el botón de volver, abajo la tarjeta de datos
    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad, pad * 2, pad, pad * 5))
}

@SuppressLint("MissingPermission")
private fun MapLibreMap.showUserLocation(context: Context, style: Style) {
    locationComponent.apply {
        if (!isLocationComponentActivated) {
            activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
        }
        isLocationComponentEnabled = true
    }
}

private fun Context.hasLocationPermission() =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

private const val SLOPE = "slope"

/** Leyenda de colores de la pendiente. */
@Composable
private fun SlopeLegend(modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Slope.COLORS.zip(Slope.LABELS).forEach { (c, l) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("●", color = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(c)))
                    Text(" $l", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private const val POIS = "pois"

private const val RETURN = "return"

/** Volver al coche: por donde has venido (sin conexión) o por el camino más corto (BRouter). */
@Composable
private fun ReturnDialog(onDismiss: () -> Unit, onRetrace: () -> Unit, onShortest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Volver al inicio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onRetrace, modifier = Modifier.fillMaxWidth()) { Text("Por donde he venido") }
                Text("La ruta al revés hasta el inicio. Funciona sin conexión.", style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(onClick = onShortest, modifier = Modifier.fillMaxWidth()) { Text("Por el camino más corto") }
                Text("Calculado por pistas y senderos desde donde estás. Necesita conexión.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Todo lo que puede salir en las cifras de navegación. */
private class NavValues(
    val nav: NavState?,
    val extras: NavExtras,
    val ride: app.trazo.ride.RideState,
    val sensors: app.trazo.sensors.Readings?,
    val partnerGapMs: Long?,
    val profile: List<app.trazo.gpx.ProfilePoint>,
    val grades: List<app.trazo.gpx.Grade>,
) {
    fun text(f: DataField): String {
        val n = nav
        return when (f) {
            DataField.REMAINING -> n?.let { km(it.remainingMeters) }
            DataField.REMAINING_GAIN -> n?.let { metros(it.remainingGainMeters) }
            DataField.DONE -> n?.let { km(it.alongMeters) }
            DataField.SPEED -> ride.currentKmh?.let { String.format(ES, "%.1f km/h", it) }
            DataField.AVG_SPEED -> ride.stats.avgMovingKmh.takeIf { it > 0 }?.let { String.format(ES, "%.1f km/h", it) }
            DataField.MOVING_TIME -> formatDuration(ride.stats.movingMs)
            DataField.ALTITUDE -> n?.let { ElevationProfile.eleAt(profile, it.alongMeters) }?.let { metros(it) }
            DataField.GRADE -> n?.let { Slope.at(grades, it.alongMeters) }?.let { String.format(ES, "%.0f %%", it) }
            DataField.ETA -> extras.finishAtMs?.let {
                java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()))
            }
            DataField.LIGHT -> extras.lightMarginMinutes?.let { (if (it < 0) "−" else "+") + durationText(kotlin.math.abs(it)) }
            DataField.HEART_RATE -> sensors?.hr?.let { "$it ppm" }
            DataField.CADENCE -> sensors?.cadence?.let { "$it rpm" }
            DataField.POWER -> sensors?.power?.let { "$it W" }
            DataField.PARTNER -> partnerGapMs?.let { g ->
                val sec = kotlin.math.abs(g) / 1000
                (if (g >= 0) "+" else "−") + String.format(java.util.Locale.ROOT, "%d:%02d", sec / 60, sec % 60)
            }
        } ?: "—"
    }
}

/** Elegir qué cifra va en un hueco de la pantalla de navegación. */
@Composable
private fun FieldPicker(current: DataField, onDismiss: () -> Unit, onPick: (DataField) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Qué quieres ver aquí?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DataField.entries.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(f) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = f == current, onClick = { onPick(f) })
                        Text(f.label + when (f) {
                            DataField.HEART_RATE, DataField.CADENCE, DataField.POWER -> " (sensor)"
                            DataField.PARTNER -> " (tu salida anterior)"
                            else -> ""
                        })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
