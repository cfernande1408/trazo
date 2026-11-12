package app.trazo.map

import app.trazo.gpx.i18n.L
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.material.icons.filled.Terrain
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
import app.trazo.gpx.nav.Rejoin
import app.trazo.gpx.nav.Rerouted
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
import androidx.compose.material3.CircularProgressIndicator
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
/** Tras este tiempo fuera de la ruta se recalcula cómo volver a ella. */
private const val REROUTE_AFTER_MS = 20_000L
/** Si no se pudo (sin cobertura), se vuelve a probar pasado este tiempo. */
private const val REROUTE_RETRY_MS = 60_000L

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
    /** Ruta recalculada al salirte (camino de vuelta + resto de la ruta); null si vas por la ruta. */
    var rejoin by remember { mutableStateOf<Rerouted?>(null) }
    var rerouting by remember { mutableStateOf(false) }
    var rerouteNote by remember { mutableStateOf<String?>(null) }
    var showReturn by remember { mutableStateOf(false) }
    var showSos by remember { mutableStateOf(false) }
    val routeSegments = remember(state.gpx, startAlong) { RouteStart.from(state.gpx.segments, startAlong) }
    val navSegments = returnSegs ?: rejoin?.segments ?: routeSegments
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
    /** Navegando, la tarjeta de abajo va recogida (tres cifras) para que se vea la ruta. */
    var navExpanded by remember { mutableStateOf(prefs.getBoolean("nav_expanded", false)) }
    /** Los botones del mapa, recogidos tras uno al navegar. */
    var moreOpen by remember { mutableStateOf(false) }
    // Lo que tapan arriba y abajo las tarjetas, para centrar tu posición en el hueco libre
    var rootPx by remember { mutableIntStateOf(0) }
    var topPx by remember { mutableIntStateOf(0) }
    var bottomPx by remember { mutableIntStateOf(0) }
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
    var marking by rememberSaveable { mutableStateOf(false) }

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
    val partnerGap = navState?.let { st ->
        partner?.gapMs(rejoin?.routeAlong(st.alongMeters) ?: st.alongMeters, System.currentTimeMillis() - ride.startedAtMs)
    }

    // Navegar = grabar la salida: el servicio mantiene el GPS y los avisos con la pantalla apagada
    val startNav = {
        RideSession.start(context, routeName = state.title())
        rejoin = null
        rerouteNote = null
        navigating = true
    }
    val stopNav = {
        navigating = false
        rejoin = null
        rerouteNote = null
        RideSession.stop(context)?.let(onRideSaved)
        Unit
    }
    // Navegando, Atrás pregunta antes de terminar: es fácil darle sin querer
    var confirmStop by remember { mutableStateOf(false) }
    BackHandler { if (navigating) confirmStop = true else onBack() }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(L("¿Terminar la ruta?", "Finish the route?")) },
            text = { Text(L("Se para la navegación y se guarda lo que llevas grabado.", "Navigation stops and what you've recorded so far is saved.")) },
            confirmButton = { TextButton(onClick = { confirmStop = false; stopNav() }) { Text(L("Terminar y guardar", "Finish and save")) } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(L("Seguir", "Continue")) } },
        )
    }

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
    // Al analizar una ruta realizada, la pendiente sale puesta (sin tocar tus capas)
    val analysis = state.done != null
    var slopeOverride by remember(state.gpx) { mutableStateOf<Boolean?>(if (analysis) true else null) }
    val slopeOn = slopeOverride ?: layers.slope
    var showLayers by remember { mutableStateOf(false) }
    LaunchedEffect(ready, layers) { ready?.second?.applyLayers(layers, AppMode.current) }

    // Camino de vuelta en azul discontinuo, encima de la ruta
    LaunchedEffect(ready, returnSegs, rejoin) {
        val (_, style) = ready ?: return@LaunchedEffect
        // El camino para volver al inicio o, si se ha recalculado, el de vuelta a la ruta
        val json = GeoJson.track(returnSegs ?: rejoin?.path?.let { listOf(it) }.orEmpty())
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
                PropertyFactory.circleRadius(5.5f),
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
            ).apply { minZoom = 15f }
        )
    }

    // Ruta coloreada por pendiente, encima de la línea normal
    val slopeLines = remember(state) { Slope.lines(state.gpx.segments, Slope.grades(ElevationProfile.of(state.gpx.segments))) }
    LaunchedEffect(ready, slopeOn) {
        val (_, style) = ready ?: return@LaunchedEffect
        style.getLayer(SLOPE)?.let { style.removeLayer(it) }
        style.getSource(SLOPE)?.let { style.removeSource(it) }
        if (!slopeOn) return@LaunchedEffect
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

    // Navegando, tu posición va en el hueco entre la tarjeta de arriba y la de abajo
    LaunchedEffect(ready, navigating, topPx, bottomPx) {
        val (map, _) = ready ?: return@LaunchedEffect
        map.moveCamera(
            CameraUpdateFactory.paddingTo(
                0.0, if (navigating) topPx.toDouble() else 0.0,
                0.0, if (navigating) bottomPx.toDouble() else 0.0,
            )
        )
    }

    // El punto del perfil bajo el dedo se dibuja en el mapa y la cámara lo sigue: en cuanto
    // se acerca al borde del hueco libre (entre los botones de arriba y la tarjeta de abajo),
    // el mapa se mueve para dejarlo en el centro de ese hueco
    val scrubDensity = LocalDensity.current
    LaunchedEffect(ready, scrub, navigating) {
        val (map, style) = ready ?: return@LaunchedEffect
        val p = scrub?.takeIf { !navigating }?.let { RouteGeometry.pointAt(state.gpx.segments, it) }
        style.getSourceAs<GeoJsonSource>(TrazoMapStyle.CURSOR)
            ?.setGeoJson(GeoJson.points(listOfNotNull(p?.let { it.lat to it.lon })))
        if (p == null) return@LaunchedEffect
        val w = mapView.width.toFloat()
        val h = mapView.height.toFloat()
        if (w <= 0f || h <= 0f) return@LaunchedEffect
        val top = with(scrubDensity) { 120.dp.toPx() }
        val bottom = (h - bottomPx - with(scrubDensity) { 16.dp.toPx() }).coerceAtLeast(top + 1f)
        val s = map.projection.toScreenLocation(LatLng(p.lat, p.lon))
        // Margen: una quinta parte del hueco por cada lado
        val mx = w * 0.15f
        val my = (bottom - top) * 0.2f
        if (s.x in mx..(w - mx) && s.y in (top + my)..(bottom - my)) return@LaunchedEffect
        // Centro nuevo para que el punto quede en mitad del hueco libre
        val fy = (top + bottom) / 2
        val center = map.projection.fromScreenLocation(android.graphics.PointF(s.x, h / 2 + s.y - fy))
        map.animateCamera(CameraUpdateFactory.newLatLng(center), 250)
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
        var offSince: Long? = null
        var lastRerouteTry = Long.MIN_VALUE / 2
        val guide = VoiceGuide(turns, climbs, pois, activity)
        // Ritmo real frente al estimado, para calcular cuánto te queda de verdad
        var startT: Long? = null
        var startAlongNav = 0.0
        var startRemainingGain = 0.0
        val personalPace = app.trazo.ui.UserProfile.pace(activity).factor
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
                // Hasta llevar 1,5 km, tu ritmo de siempre (perfil y rutas realizadas)
                val pace = if (doneM > 1_500 && expectedDone > 0) ((t - startT!!).toDouble() / expectedDone).coerceIn(0.5, 2.5) else personalPace
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

                // Recalcular: tras un rato fuera, el camino más corto de vuelta a la ruta por delante
                if (s.offRoute) { if (offSince == null) offSince = t } else { offSince = null; rerouteNote = null }
                val since = offSince
                if (since != null && t - since >= REROUTE_AFTER_MS && t - lastRerouteTry >= REROUTE_RETRY_MS &&
                    returnSegs == null && !rerouting
                ) {
                    lastRerouteTry = t
                    val here = GeoPoint(loc.latitude, loc.longitude)
                    val target = Rejoin.target(navSegments, s.alongMeters, here)
                    if (target != null && target.straightMeters in 30.0..3_000.0) {
                        rerouting = true
                        val segsNow = navSegments
                        val previous = rejoin
                        val leftAt = s.alongMeters
                        scope.launch {
                            runCatching {
                                val (code, body) = Http.getWithStatus(BRouter.url(listOf(here, target.point), activity))
                                check(code in 200..299) { BRouter.friendlyError(body) }
                                GpxParser.parse(body).segments.flatten().also { check(it.size >= 2) { L("No hay camino", "No path") } }
                            }.onSuccess { path ->
                                val r = Rejoin.reroute(path, segsNow, target, leftAt, previous)
                                rejoin = r
                                rerouteNote = null
                                if (voiceOn) voice.say(Rejoin.phrase(r))
                            }.onFailure {
                                rerouteNote = L("No se pudo recalcular: ${Http.friendly(it)}. Vuelve hacia la ruta o usa «Volver al inicio».", "Could not recalculate: ${Http.friendly(it)}. Head back to the route or use “Back to start”.")
                            }
                            rerouting = false
                        }
                    }
                }
            }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootPx = it.size.height }) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        if (navigating) {
            Column(
                Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
                    .onGloballyPositioned { topPx = it.boundsInRoot().bottom.toInt() },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NavBanner(
                    navState, extras, Modifier.fillMaxWidth(),
                    showChart = navExpanded,
                    voiceOn = voiceOn,
                    onToggleVoice = {
                        voiceOn = !voiceOn
                        prefs.edit().putBoolean("voice", voiceOn).apply()
                    },
                )
                NavChips(extras)
                RerouteStatus(
                    rerouting = rerouting,
                    // Lo que queda del camino de vuelta: cuando llegas a la ruta, el aviso se va
                    toRouteMeters = rejoin?.takeIf { returnSegs == null }?.let { it.pathMeters - (navState?.alongMeters ?: 0.0) },
                    note = rerouteNote,
                    onDismiss = { rejoin = null; rerouteNote = null },
                )
            }
        } else {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.statusBarsPadding().padding(16.dp).size(56.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = L("Volver", "Back"))
            }
            FilledTonalButton(
                onClick = onOpen3D,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).height(56.dp),
            ) {
                Text("3D", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            // La pendiente a la vista: antes solo estaba dentro de Capas
            val toggleSlope: () -> Unit = {
                // Al analizar se cambia solo aquí; si no, en tus capas
                val o = slopeOverride
                if (o != null) slopeOverride = !o
                if (o == null) MapPrefs.set(context, layers.copy(slope = !slopeOn))
            }
            val slopeMod = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp).height(56.dp)
            if (slopeOn) {
                Button(onClick = toggleSlope, modifier = slopeMod) {
                    Icon(Icons.Filled.Terrain, contentDescription = null)
                    Text(L(" Pendiente", " Gradient"), style = MaterialTheme.typography.titleSmall)
                }
            } else {
                FilledTonalButton(onClick = toggleSlope, modifier = slopeMod) {
                    Icon(Icons.Filled.Terrain, contentDescription = null)
                    Text(L(" Pendiente", " Gradient"), style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        if (navigating) {
            // Navegando: SOS siempre a mano y el resto recogido tras un botón, justo encima de la tarjeta
            val density = LocalDensity.current
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = with(density) { bottomPx.toDp() } + 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (moreOpen) {
                    LayersButton(onClick = { showLayers = true; moreOpen = false })
                    SensorsButton(onClick = { showSensors = true; moreOpen = false })
                    ReportButton(onClick = { reporting = true; moreOpen = false })
                    // Navegando se graba la salida: se pueden marcar puntos como al grabar
                    app.trazo.ride.MarkButton(onClick = { marking = true; moreOpen = false })
                }
                SmallFloatingActionButton(
                    onClick = { moreOpen = !moreOpen },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Icon(if (moreOpen) Icons.Filled.Close else Icons.Filled.MoreVert, contentDescription = if (moreOpen) L("Recoger botones", "Hide buttons") else L("Más botones", "More buttons"))
                }
                SosButton(onClick = { showSos = true })
            }
        } else {
            Column(
                // Bajo el botón de 3D
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 92.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LayersButton(onClick = { showLayers = true })
                SensorsButton(onClick = { showSensors = true })
                ReportButton(onClick = { reporting = true })
                SosButton(onClick = { showSos = true })
            }
        }
        if (marking) app.trazo.ride.MarkDialog(onDismiss = { marking = false })
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
                    // Con la ruta recalculada, sus metros se pasan a los de la original
                    val along = navState?.alongMeters?.let { rejoin?.routeAlong(it) ?: it } ?: 0.0
                    Retrace.backToStart(routeSegments, along).takeIf { it.isNotEmpty() }?.let { returnSegs = it; rejoin = null }
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
                            }.onSuccess { returnSegs = it; rejoin = null }
                                .onFailure { returnError = it.message ?: L("No se pudo calcular. Prueba «por donde he venido».", "Could not calculate. Try “the way I came”.") }
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
        if (slopeOn && !navigating) {
            // A la izquierda, bajo el botón de volver: a la derecha están los botones del mapa
            SlopeLegend(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 92.dp, start = 16.dp))
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(if (navigating) 8.dp else 16.dp)
                .fillMaxWidth()
                .onGloballyPositioned { bottomPx = (rootPx - it.boundsInRoot().top).toInt().coerceAtLeast(0) },
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(if (navigating) 10.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(if (navigating) 8.dp else 12.dp)) {
                val s = navState
                if (navigating) {
                    // Seis cifras a tu gusto (toca una para cambiarla). Recogida, solo las tres primeras
                    val values = NavValues(s, extras, ride, sensorTick.takeIf { BleSensors.fresh() != null }, partnerGap, fullProfile, grades)
                    (if (navExpanded) fields else fields.take(3)).chunked(3).forEachIndexed { row, chunk ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            chunk.forEachIndexed { col, f ->
                                Stat(f.label, values.text(f), Modifier.weight(1f).clickable { picking = row * 3 + col })
                            }
                            if (row == 0) {
                                IconButton(onClick = {
                                    navExpanded = !navExpanded
                                    prefs.edit().putBoolean("nav_expanded", navExpanded).apply()
                                }) {
                                    Icon(
                                        if (navExpanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                                        contentDescription = if (navExpanded) L("Recoger la tarjeta", "Collapse the card") else L("Ver más datos, perfil y terminar", "More data, profile and finish"),
                                    )
                                }
                            }
                        }
                    }
                    if (navExpanded && navProfile.size >= 2) {
                        ElevationChart(
                            navProfile,
                            Modifier.fillMaxWidth().height(72.dp),
                            positionMeters = s?.alongMeters,
                            highlight = s?.nextClimb?.let { it.startMeters..it.endMeters },
                        )
                    }
                    if (navExpanded) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { if (returnSegs != null) returnSegs = null else showReturn = true },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) {
                            Text(if (returnSegs != null) L("Seguir la ruta", "Follow the route") else L("Volver al inicio", "Back to start"))
                        }
                        FilledTonalButton(onClick = stopNav, modifier = Modifier.weight(1f).height(56.dp)) {
                            Text(L("Terminar y guardar", "Finish and save"))
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
                        Stat(L("Distancia", "Distance"), km(state.stats.distanceMeters), Modifier.weight(1f))
                        Stat(L("Desnivel +", "Ascent"), metros(state.stats.elevationGainMeters), Modifier.weight(1f))
                    }
                    if (state.profile.size >= 2) {
                        ScrubbableProfile(state.profile, scrub, onScrub = { scrub = it }, chartHeight = 72.dp)
                    }
                    navError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    returnError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (returnSegs != null) Text(L("Volviendo al inicio", "Heading back to start"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    // Una ruta realizada se mira; para hacerla otra vez, «Repetir esta ruta»
                    if (!analysis) Button(
                        onClick = { showStartSheet = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(L("Empezar ruta", "Start route"), style = MaterialTheme.typography.titleMedium)
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
            Text(L("¿Desde dónde empiezas?", "Where are you starting from?"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                if (isLoop) L("Es circular: acabarás en el mismo sitio donde empieces.", "It's a loop: you'll finish where you start.")
                else L("No es circular: lo anterior al punto elegido se salta.", "It's not a loop: anything before the chosen point is skipped."),
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
                                if (o.alongMeters == 0.0) L("Punto de salida del GPX", "Start point of the GPX") else L("En el km ${km(o.alongMeters).removeSuffix(" km")} de la ruta", "At km ${km(o.alongMeters).removeSuffix(" km")} of the route"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(onClick = { onDirections(o) }) {
                            Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(L("Cómo llegar", "Directions"))
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
    /** El perfil de la subida en curso: solo con la tarjeta de abajo desplegada. */
    showChart: Boolean = true,
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
        s == null -> L("Buscando GPS…", "Looking for GPS…") to L("Sal a cielo abierto si tarda", "Move to open sky if it takes long")
        s.offRoute -> L("Fuera de ruta", "Off route") to L("A ${corta(s.distanceToTrackMeters)} del track", "${corta(s.distanceToTrackMeters)} from the track")
        // Subida en directo, como el ClimbPro de los Garmin
        climb != null ->
            L("Subida ${climb.number} de ${climb.total} · ", "Climb ${climb.number} of ${climb.total} · ") + app.trazo.gpx.i18n.percent(String.format(ES, "%.0f", climb.gradePercent)) to
                L("Quedan ${corta(climb.remainingMeters)} y +${metros(climb.remainingGainMeters)}", "${corta(climb.remainingMeters)} and +${metros(climb.remainingGainMeters)} to go")
        s.nextClimb != null -> {
            val c = s.nextClimb!!
            L("Próxima subida en ${corta(s.metersToNextClimb ?: 0.0)}", "Next climb in ${corta(s.metersToNextClimb ?: 0.0)}") to
                "${corta(c.lengthMeters)} " + L("al", "at") + " " + app.trazo.gpx.i18n.percent(String.format(ES, "%.0f", c.avgGradePercent)) + " · +${metros(c.gainMeters)}"
        }
        else -> L("Sin más subidas", "No more climbs") to L("Quedan ${km(s.remainingMeters)}", "${km(s.remainingMeters)} to go")
    }
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s?.offRoute == true) Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, style = MaterialTheme.typography.bodyLarge)
                }
                IconButton(onClick = onToggleVoice) {
                    Icon(
                        if (voiceOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (voiceOn) L("Silenciar avisos de voz", "Mute voice prompts") else L("Activar avisos de voz", "Turn on voice prompts"),
                    )
                }
            }
            if (showChart && climb != null && climb.profile.size >= 2) {
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
                    "${p.poi.kind.emoji} ${p.poi.kind.label} " + L("en", "in") + " ${corta(ahead.coerceAtLeast(0.0))}",
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
                    if (late) L("🌙 Anochece ${durationText(-m)} antes de terminar", "🌙 Dark ${durationText(-m)} before you finish") else L("☀️ Te sobran ${durationText(m)} de luz", "☀️ ${durationText(m)} of daylight to spare"),
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
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(L("Pendiente", "Gradient"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            // De más empinado a menos, como se lee una leyenda de mapa
            Slope.COLORS.zip(Slope.LABELS).reversed().forEach { (c, l) ->
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
        title = { Text(L("Volver al inicio", "Back to start")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onRetrace, modifier = Modifier.fillMaxWidth()) { Text(L("Por donde he venido", "The way I came")) }
                Text(L("La ruta al revés hasta el inicio. Funciona sin conexión.", "The route in reverse back to the start. Works offline."), style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(onClick = onShortest, modifier = Modifier.fillMaxWidth()) { Text(L("Por el camino más corto", "The shortest way")) }
                Text(L("Calculado por pistas y senderos desde donde estás. Necesita conexión.", "Worked out along tracks and trails from where you are. Needs a connection."), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L("Cancelar", "Cancel")) } },
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
            DataField.HEART_RATE -> sensors?.hr?.let { L("$it ppm", "$it bpm") }
            DataField.CADENCE -> sensors?.cadence?.let { "$it rpm" }
            DataField.POWER -> sensors?.power?.let { "$it W" }
            DataField.PARTNER -> partnerGapMs?.let { g ->
                val sec = kotlin.math.abs(g) / 1000
                (if (g >= 0) "+" else "−") + String.format(java.util.Locale.ROOT, "%d:%02d", sec / 60, sec % 60)
            }
            // Con tu peso (en Info) y la potencia si llevas potenciómetro
            DataField.KCAL -> app.trazo.gpx.ride.Calories.format(app.trazo.ui.UserProfile.kcal(ride.stats, ride.activity))
        } ?: "—"
    }
}

/** Elegir qué cifra va en un hueco de la pantalla de navegación. */
@Composable
private fun FieldPicker(current: DataField, onDismiss: () -> Unit, onPick: (DataField) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("¿Qué quieres ver aquí?", "What do you want to see here?")) },
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
                            DataField.PARTNER -> L(" (tu salida anterior)", " (your last outing)")
                            else -> ""
                        })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L("Cerrar", "Close")) } },
    )
}

/** Debajo del aviso de navegación: si se está recalculando, si ya se ha hecho o si no se pudo. */
@Composable
private fun RerouteStatus(rerouting: Boolean, toRouteMeters: Double?, note: String?, onDismiss: () -> Unit) {
    val text = when {
        rerouting -> L("Calculando cómo volver a la ruta…", "Working out how to rejoin the route…")
        note != null -> note
        toRouteMeters != null && toRouteMeters > 15 -> L("Ruta recalculada: vuelves a ella en ${corta(toRouteMeters)} (en azul)", "Route recalculated: you rejoin it in ${corta(toRouteMeters)} (in blue)")
        else -> return
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (note != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (rerouting) CircularProgressIndicator(Modifier.size(16.dp).padding(end = 0.dp), strokeWidth = 2.dp)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(start = if (rerouting) 10.dp else 0.dp, top = 8.dp, bottom = 8.dp),
            )
            if (!rerouting) TextButton(onClick = onDismiss) { Text(if (note != null) L("Vale", "OK") else L("Quitar", "Dismiss")) }
        }
    }
}
