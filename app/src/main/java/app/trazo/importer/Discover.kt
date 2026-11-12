package app.trazo.importer

import app.trazo.gpx.i18n.L
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trazo.gpx.Activity
import app.trazo.ui.Tips
import app.trazo.ui.openUrl

private const val WEB = "https://cfernande1408.github.io/trazo/"

/** Cuántas tarjetas se ven sin pulsar «Ver todas». */
private const val FOLDED = 3

/** Una función de la app explicada en una tarjeta, con su botón para probarla. */
internal class Tip(
    val id: String,
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val text: String,
    val action: String? = null,
    val run: (() -> Unit)? = null,
)

/** Todas las tarjetas, en el orden en que conviene descubrirlas. */
internal fun tips(activity: Activity, a: HomeActions, openWeb: () -> Unit): List<Tip> {
    val bike = activity == Activity.BIKE
    return listOf(
        Tip(
            "explore", Icons.Filled.Place, Color(0xFF2F9E44), L("Explora rutas cerca", "Explore routes nearby"),
            if (bike) L("Las rutas MTB señalizadas de tu zona en el mapa. Toca una y ves su recorrido, desnivel y perfil; filtra circulares o lineales.", "The waymarked MTB routes around you on the map. Tap one to see its track, climbing and profile; filter loops or one-way routes.")
            else L("Los senderos señalizados de tu zona en el mapa. Toca uno y ves su recorrido, desnivel y perfil; filtra circulares o lineales.", "The waymarked trails around you on the map. Tap one to see its track, climbing and profile; filter loops or one-way routes."),
            L("Explorar", "Explore"), a.onExplore,
        ),
        Tip(
            "for-you", Icons.Filled.AccountCircle, Color(0xFF099268), L("Trazo a tu medida", "Trazo made for you"),
            L("Dinos cuánto sales y, con tus rutas realizadas, los tiempos van a tu ritmo y cada ruta te dice si es fácil, ", "Tell us how often you go out and, with your completed routes, times follow your pace and each route tells you if it is easy, ") +
                L("a tu medida o exigente para ti. Con tu peso y altura, también tus kcal.", "right for you or hard for you. With your weight and height, your kcal too."),
            L("Abrir perfil", "Open profile"), a.onProfile,
        ),
        Tip(
            "wikiloc", Icons.Filled.FileOpen, Color(0xFF1C7ED6), L("Tus rutas de Wikiloc", "Your Wikiloc routes"),
            L("Descarga el GPX en Wikiloc y dale a Compartir → Trazo, o búscala desde la pestaña Wikiloc. Sale con mapa, perfil y puntos del autor.", "Download the GPX on Wikiloc and tap Share → Trazo, or search for it in the Wikiloc tab. It comes with map, profile and the author's waypoints."),
            L("Abrir Wikiloc", "Open Wikiloc"), a.onWikiloc,
        ),
        Tip(
            "create", Icons.Filled.Route, Color(0xFFE8590C), L("Crea tu ruta", "Create your route"),
            L("Toca puntos en el mapa y la ruta se ajusta sola a pistas y senderos. Circular, ida y vuelta, mover o borrar puntos.", "Tap points on the map and the route snaps to tracks and trails. Loop, out and back, move or delete points."),
            L("Crear ruta", "Create route"), a.onCreateRoute,
        ),
        Tip(
            "offline", Icons.Filled.CloudDownload, Color(0xFF0C8599), L("Mapas sin cobertura", "Maps without signal"),
            L("En el resumen de una ruta, Descargar mapa: la zona y 2 km alrededor. En la sierra, el mapa y la navegación siguen sin datos.", "In a route summary, Download map: the area plus 2 km around it. Up in the hills, the map and navigation keep working without data."),
        ),
        Tip(
            "weather", Icons.Filled.WbSunny, Color(0xFFF59F00), L("El tiempo por el camino", "Weather along the way"),
            L("En la pestaña Tiempo de cada ruta: qué hará en cada tramo cuando pases, cada 15 minutos, y qué llevarte puesto.", "In each route's Weather tab: the weather on every stretch when you pass, every 15 minutes, and what to wear."),
        ),
        Tip(
            "terrain", Icons.Filled.Terrain, Color(0xFF8F5A2B), L("Pendiente y tipo de suelo", "Gradient and surface"),
            if (bike) L("Rampas más duras, dificultad MTB (S0–S6) y cuánto va por asfalto, pista o sendero. En el mapa, la capa Pendiente.", "Steepest ramps, MTB difficulty (S0–S6) and how much is paved, track or trail. On the map, the Gradient layer.")
            else L("Rampas más duras, dificultad SAC (T1–T6) y cuánto va por asfalto, pista o sendero. En el mapa, la capa Pendiente.", "Steepest ramps, SAC difficulty (T1–T6) and how much is paved, track or trail. On the map, the Gradient layer."),
        ),
        Tip(
            "3d", Icons.Filled.ViewInAr, Color(0xFF7048E8), L("La ruta en 3D", "The route in 3D"),
            L("Mírala sobre el relieve antes de salir, con un vuelo virtual que la recorre entera.", "See it over the terrain before you go, with a virtual flyover along the whole route."),
        ),
        Tip(
            "voice", Icons.AutoMirrored.Filled.VolumeUp, Color(0xFF1971C2), L("Te avisa por voz", "Voice prompts"),
            L("Al seguir una ruta: «En 150 metros, gira a la derecha», las subidas que empiezan, las fuentes y si te sales.", "When following a route: “In 150 metres, turn right”, climbs as they start, water points and when you go off route."),
        ),
        Tip(
            "reroute", Icons.Filled.SyncAlt, Color(0xFF5C940D), L("Si te sales, te devuelve", "Off route? It brings you back"),
            L("Tras 20 segundos fuera de la ruta, calcula el camino más corto para volver a ella y te lleva por voz.", "After 20 seconds off the route, it works out the shortest way back and guides you by voice."),
        ),
        Tip(
            "sos", Icons.Filled.Sos, Color(0xFFE03131), L("SOS y caídas", "SOS and falls"),
            L("Botón SOS con tu posición en UTM, llamar al 112 o mandar un SMS. Si te caes y no te mueves, avisa a tu contacto.", "SOS button with your UTM position, call 112 or send a text. If you fall and don't move, it alerts your contact."),
        ),
        Tip(
            "marks", Icons.Filled.AddLocationAlt, Color(0xFFC2255C), L("Marca puntos con foto", "Mark points with a photo"),
            L("Mientras grabas, la chincheta guarda una fuente, un mirador o un peligro con nota y foto. Van en el GPX.", "While recording, the pin saves a water point, a viewpoint or a hazard with a note and photo. They go in the GPX."),
            if (bike) L("Grabar salida", "Record ride") else L("Grabar excursión", "Record hike"), a.onRecord,
        ),
        Tip(
            "library", Icons.Filled.Bookmarks, Color(0xFF495057), L("Todo en Guardadas", "Everything in Saved"),
            L("Tus rutas, lo que has grabado y los mapas descargados, en una pestaña, como tu biblioteca de música.", "Your routes, what you have recorded and your downloaded maps, in one tab, like your music library."),
            L("Ver Guardadas", "Open Saved"), a.onLibrary,
        ),
        Tip(
            "sensors", Icons.Filled.MonitorHeart, Color(0xFFD6336C), L("Pulso y potencia (en pruebas)", "Heart rate and power (beta)"),
            L("Banda de pulso, sensor de cadencia o potenciómetro por Bluetooth, de cualquier marca. Se graban en el GPX para Strava.", "Heart rate strap, cadence sensor or power meter over Bluetooth, any brand. They are saved in the GPX for Strava."),
        ),
        Tip(
            "web", Icons.Filled.Language, Color(0xFF364FC7), L("Trazo en el ordenador", "Trazo on your computer"),
            L("Abre tus GPX en la web para revisarlos en pantalla grande antes de salir.", "Open your GPX files on the web to review them on a big screen before you go."),
            L("Abrir la web", "Open the website"), openWeb,
        ),
    )
}

/**
 * «Descubre Trazo»: una tarjeta por función. El círculo la marca como vista
 * (pasa al final, en gris) y deslizándola se quita. Las que faltan van primero.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun LazyListScope.discover(all: List<Tip>, expanded: Boolean, onExpand: (Boolean) -> Unit) {
    val left = all.filter { it.id !in Tips.dismissed }
    if (left.isEmpty()) return
    val pending = left.filter { it.id !in Tips.seen }
    val ordered = pending + left.filter { it.id in Tips.seen }
    val shown = when {
        expanded -> ordered
        pending.isEmpty() -> emptyList()
        else -> ordered.take(FOLDED)
    }
    item(key = "discover-head") {
        Column(Modifier.animateItem()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("Descubre Trazo", "Discover Trazo"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    L("${all.count { it.id in Tips.seen || it.id in Tips.dismissed }} de ${all.size} vistas", "${all.count { it.id in Tips.seen || it.id in Tips.dismissed }} of ${all.size} seen"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (pending.isEmpty() && !expanded) L("Ya las conoces todas.", "You know them all.")
                else L("Marca el círculo cuando la conozcas o desliza la tarjeta para quitarla.", "Tick the circle once you know it, or swipe the card away."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(shown, key = { "tip-" + it.id }) { tip ->
        TipCard(tip, seen = tip.id in Tips.seen, modifier = Modifier.animateItem())
    }
    if (ordered.size > shown.size || expanded) {
        item(key = "discover-more") {
            TextButton(onClick = { onExpand(!expanded) }, modifier = Modifier.animateItem()) {
                Text(if (expanded) L("Ver menos", "Show less") else if (pending.isEmpty()) L("Verlas otra vez", "See them again") else L("Ver las ${ordered.size}", "See all ${ordered.size}"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TipCard(tip: Tip, seen: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                Tips.dismiss(context, tip.id)
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val bg by animateColorAsState(
                if (state.targetValue == SwipeToDismissBoxValue.Settled) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.errorContainer,
                label = "tip-bg",
            )
            val start = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(bg).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (start) Arrangement.Start else Arrangement.End,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(L("  Quitar", "  Remove"), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        Card(
            onClick = {
                Tips.markSeen(context, tip.id)
                tip.run?.invoke()
            },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 14.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
                TipIcon(tip, Modifier.alpha(if (seen) 0.45f else 1f))
                Column(Modifier.weight(1f).padding(start = 14.dp).alpha(if (seen) 0.6f else 1f)) {
                    Text(tip.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        tip.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = if (tip.action == null) 8.dp else 0.dp),
                    )
                    if (tip.action != null && tip.run != null) {
                        TextButton(
                            onClick = {
                                Tips.markSeen(context, tip.id)
                                tip.run.invoke()
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                        ) {
                            Text(tip.action)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                        }
                    }
                }
                IconButton(onClick = { Tips.toggleSeen(context, tip.id) }) {
                    if (seen) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = L("Vista", "Seen"), tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = L("Marcar como vista", "Mark as seen"), tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

/** El dibujo de la función: su icono en blanco sobre un degradado de su color. */
@Composable
private fun TipIcon(tip: Tip, modifier: Modifier) {
    val dark = Color(
        red = tip.color.red * 0.7f,
        green = tip.color.green * 0.7f,
        blue = tip.color.blue * 0.7f,
    )
    Box(
        modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(tip.color, dark))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(tip.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

/** El enlace de la web, para la tarjeta de Trazo en el ordenador. */
internal fun webOpener(context: android.content.Context): () -> Unit = { context.openUrl(WEB) }
