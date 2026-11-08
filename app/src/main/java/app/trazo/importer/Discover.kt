package app.trazo.importer

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
            "explore", Icons.Filled.Place, Color(0xFF2F9E44), "Explora rutas cerca",
            if (bike) "Las rutas MTB señalizadas de tu zona en el mapa. Toca una y ves su recorrido, desnivel y perfil; filtra circulares o lineales."
            else "Los senderos señalizados de tu zona en el mapa. Toca uno y ves su recorrido, desnivel y perfil; filtra circulares o lineales.",
            "Explorar", a.onExplore,
        ),
        Tip(
            "for-you", Icons.Filled.AccountCircle, Color(0xFF099268), "Trazo a tu medida",
            "Dinos cuánto sales y, con tus rutas realizadas, los tiempos van a tu ritmo y cada ruta te dice si es fácil, " +
                "a tu medida o exigente para ti. Con tu peso y altura, también tus kcal.",
            "Abrir perfil", a.onProfile,
        ),
        Tip(
            "wikiloc", Icons.Filled.FileOpen, Color(0xFF1C7ED6), "Tus rutas de Wikiloc",
            "Descarga el GPX en Wikiloc y dale a Compartir → Trazo, o búscala desde la pestaña Wikiloc. Sale con mapa, perfil y puntos del autor.",
            "Abrir Wikiloc", a.onWikiloc,
        ),
        Tip(
            "create", Icons.Filled.Route, Color(0xFFE8590C), "Crea tu ruta",
            "Toca puntos en el mapa y la ruta se ajusta sola a pistas y senderos. Circular, ida y vuelta, mover o borrar puntos.",
            "Crear ruta", a.onCreateRoute,
        ),
        Tip(
            "offline", Icons.Filled.CloudDownload, Color(0xFF0C8599), "Mapas sin cobertura",
            "En el resumen de una ruta, Descargar mapa: la zona y 2 km alrededor. En la sierra, el mapa y la navegación siguen sin datos.",
        ),
        Tip(
            "weather", Icons.Filled.WbSunny, Color(0xFFF59F00), "El tiempo por el camino",
            "En la pestaña Tiempo de cada ruta: qué hará en cada tramo cuando pases, cada 15 minutos, y qué llevarte puesto.",
        ),
        Tip(
            "terrain", Icons.Filled.Terrain, Color(0xFF8F5A2B), "Pendiente y tipo de suelo",
            if (bike) "Rampas más duras, dificultad MTB (S0–S6) y cuánto va por asfalto, pista o sendero. En el mapa, la capa Pendiente."
            else "Rampas más duras, dificultad SAC (T1–T6) y cuánto va por asfalto, pista o sendero. En el mapa, la capa Pendiente.",
        ),
        Tip(
            "3d", Icons.Filled.ViewInAr, Color(0xFF7048E8), "La ruta en 3D",
            "Mírala sobre el relieve antes de salir, con un vuelo virtual que la recorre entera.",
        ),
        Tip(
            "voice", Icons.AutoMirrored.Filled.VolumeUp, Color(0xFF1971C2), "Te avisa por voz",
            "Al seguir una ruta: «En 150 metros, gira a la derecha», las subidas que empiezan, las fuentes y si te sales.",
        ),
        Tip(
            "reroute", Icons.Filled.SyncAlt, Color(0xFF5C940D), "Si te sales, te devuelve",
            "Tras 20 segundos fuera de la ruta, calcula el camino más corto para volver a ella y te lleva por voz.",
        ),
        Tip(
            "sos", Icons.Filled.Sos, Color(0xFFE03131), "SOS y caídas",
            "Botón SOS con tu posición en UTM, llamar al 112 o mandar un SMS. Si te caes y no te mueves, avisa a tu contacto.",
        ),
        Tip(
            "marks", Icons.Filled.AddLocationAlt, Color(0xFFC2255C), "Marca puntos con foto",
            "Mientras grabas, la chincheta guarda una fuente, un mirador o un peligro con nota y foto. Van en el GPX.",
            if (bike) "Grabar salida" else "Grabar excursión", a.onRecord,
        ),
        Tip(
            "library", Icons.Filled.Bookmarks, Color(0xFF495057), "Todo en Guardadas",
            "Tus rutas, lo que has grabado y los mapas descargados, en una pestaña, como tu biblioteca de música.",
            "Ver Guardadas", a.onLibrary,
        ),
        Tip(
            "sensors", Icons.Filled.MonitorHeart, Color(0xFFD6336C), "Pulso y potencia (en pruebas)",
            "Banda de pulso, sensor de cadencia o potenciómetro por Bluetooth, de cualquier marca. Se graban en el GPX para Strava.",
        ),
        Tip(
            "web", Icons.Filled.Language, Color(0xFF364FC7), "Trazo en el ordenador",
            "Abre tus GPX en la web para revisarlos en pantalla grande antes de salir.",
            "Abrir la web", openWeb,
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
                Text("Descubre Trazo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "${all.count { it.id in Tips.seen || it.id in Tips.dismissed }} de ${all.size} vistas",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (pending.isEmpty() && !expanded) "Ya las conoces todas."
                else "Marca el círculo cuando la conozcas o desliza la tarjeta para quitarla.",
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
                Text(if (expanded) "Ver menos" else if (pending.isEmpty()) "Verlas otra vez" else "Ver las ${ordered.size}")
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
                Text("  Quitar", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge)
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
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Vista", tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = "Marcar como vista", tint = MaterialTheme.colorScheme.outline)
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
