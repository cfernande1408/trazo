package app.trazo.ui

import app.trazo.gpx.i18n.L
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trazo.gpx.i18n.Lang
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trazo.BuildConfig
import app.trazo.R
import app.trazo.gpx.Season

private const val REPO = "https://github.com/cfernande1408/trazo"

/**
 * Enlace para donar (Ko-fi, PayPal.me, GitHub Sponsors…). Vacío, la tarjeta
 * de donar no sale.
 */
private const val DONATE_URL = ""

/** Pestaña Info: qué es Trazo, qué versión llevas, el código y cómo apoyarlo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val channel = when (BuildConfig.BUILD_TYPE) {
        "release" -> "estable"
        else -> BuildConfig.BUILD_TYPE
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Info") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SeasonLogo(AppMode.season)
                Column {
                    Text("Trazo", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(L("Versión ${BuildConfig.VERSION_NAME}", "Version ${BuildConfig.VERSION_NAME}"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        L("Canal $channel · commit ${BuildConfig.COMMIT}", "Channel $channel · commit ${BuildConfig.COMMIT}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                L("Navegador de rutas para bici de montaña y senderismo. Abre los GPX de Wikiloc, explora las rutas ", "Route navigator for mountain biking and hiking. Open Wikiloc GPX files, explore the routes ") +
                    L("de OpenStreetMap, crea las tuyas y llévalas sin conexión, con avisos por voz, subidas en directo ", "from OpenStreetMap, create your own and take them offline, with voice prompts, live climbs ") +
                    L("y SOS. Gratis, sin cuentas y sin anuncios.", "and SOS. Free, no accounts and no ads."),
                style = MaterialTheme.typography.bodyLarge,
            )

            if (DONATE_URL.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(L("¿Te gusta Trazo?", "Do you like Trazo?"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            L("Lo hago en mi tiempo libre. Si te sirve en tus salidas, puedes invitarme a un café: ", "I build it in my free time. If it helps on your outings, you can buy me a coffee: ") +
                                L("ayuda a seguir añadiendo cosas.", "it helps keep adding things."),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Button(onClick = { context.openUrl(DONATE_URL) }) {
                            Icon(Icons.Filled.Favorite, contentDescription = null)
                            Text(L("  Donar", "  Donate"))
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Link(Icons.Filled.Code, L("Código en GitHub", "Code on GitHub"), "cfernande1408/trazo") { context.openUrl(REPO) }
                HorizontalDivider()
                Link(Icons.Filled.NewReleases, L("Novedades de cada versión", "What's new in each version"), L("Estable y beta, con su APK", "Stable and beta, with their APK")) { context.openUrl("$REPO/releases") }
                HorizontalDivider()
                Link(Icons.AutoMirrored.Filled.ListAlt, L("Todo lo que hace", "Everything it does"), L("La lista de funciones", "The feature list")) { context.openUrl("$REPO/blob/main/FEATURES.md") }
                HorizontalDivider()
                Link(Icons.Filled.BugReport, L("Avisar de un fallo o pedir algo", "Report a bug or ask for something"), L("Abre una incidencia en GitHub", "Opens an issue on GitHub")) {
                    context.openUrl("$REPO/issues/new")
                }
            }

            OutlinedButton(onClick = { Tips.reset(context) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null)
                Text(L("  Volver a ver las tarjetas de Descubre Trazo", "  Show the Discover Trazo cards again"))
            }

            LanguageCard()

            Text(
                L("Datos: © colaboradores de OpenStreetMap, Instituto Geográfico Nacional (CC BY 4.0), Waymarked Trails, ", "Data: © OpenStreetMap contributors, Instituto Geográfico Nacional (CC BY 4.0), Waymarked Trails, ") +
                    L("OpenFreeMap, Open-Meteo, BRouter y Nominatim.", "OpenFreeMap, Open-Meteo, BRouter and Nominatim."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                L("Tus rutas, salidas y ajustes se quedan en el móvil. Solo sale lo que publicas en la comunidad.", "Your routes, outings and settings stay on your phone. Only what you post to the community leaves it."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Link(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** El icono de la app de la estación que toca, en un círculo como en el escritorio. */
@Composable
private fun SeasonLogo(season: Season) {
    val (bg, fg) = seasonIconRes(season)
    // El icono adaptativo mide 108 y se ve el círculo central de 72: se dibuja a 120 dp
    // y se recorta a 80, como lo enseña el escritorio
    Box(Modifier.size(80.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        Image(painterResource(bg), contentDescription = null, modifier = Modifier.requiredSize(120.dp))
        Image(painterResource(fg), contentDescription = L("Icono de Trazo", "Trazo icon"), modifier = Modifier.requiredSize(120.dp))
    }
}


/** Fondo y primer plano del icono de la app de cada estación (también la portada de Inicio). */
internal fun seasonIconRes(season: Season): Pair<Int, Int> = when (season) {
    Season.SPRING -> R.drawable.ic_launcher_bg_spring to R.drawable.ic_launcher_fg_spring
    Season.SUMMER -> R.drawable.ic_launcher_bg_summer to R.drawable.ic_launcher_fg_summer
    Season.AUTUMN -> R.drawable.ic_launcher_bg_autumn to R.drawable.ic_launcher_fg_autumn
    Season.WINTER -> R.drawable.ic_launcher_bg_winter to R.drawable.ic_launcher_fg_winter
}

/** Idioma de la app: el del sistema, español o inglés. Cambiarlo reinicia la app. */
@Composable
private fun LanguageCard() {
    val context = LocalContext.current
    val ride by app.trazo.ride.RideSession.state.collectAsStateWithLifecycle()
    val recording = ride.active
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(L("Idioma", "Language"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = listOf(null to L("Sistema", "System"), Lang.ES to "Español", Lang.EN to "English")
                for ((lang, label) in options) {
                    FilterChip(
                        selected = AppLanguage.choice == lang,
                        enabled = !recording,
                        onClick = { context.findActivity()?.let { AppLanguage.set(it, lang) } },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                if (recording) L("Termina la grabación para cambiar el idioma.", "Finish recording to change the language.")
                else L("La app se reinicia al cambiarlo. La voz habla en el idioma elegido.", "The app restarts when you change it. Voice prompts use the chosen language."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
