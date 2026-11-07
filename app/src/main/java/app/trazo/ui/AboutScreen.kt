package app.trazo.ui

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
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Button
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
                    Text("Versión ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Canal $channel · commit ${BuildConfig.COMMIT}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "Navegador de rutas para bici de montaña y senderismo. Abre los GPX de Wikiloc, explora las rutas " +
                    "de OpenStreetMap, crea las tuyas y llévalas sin conexión, con avisos por voz, subidas en directo " +
                    "y SOS. Gratis, sin cuentas y sin anuncios.",
                style = MaterialTheme.typography.bodyLarge,
            )

            if (DONATE_URL.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("¿Te gusta Trazo?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Lo hago en mi tiempo libre. Si te sirve en tus salidas, puedes invitarme a un café: " +
                                "ayuda a seguir añadiendo cosas.",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Button(onClick = { context.openUrl(DONATE_URL) }) {
                            Icon(Icons.Filled.Favorite, contentDescription = null)
                            Text("  Donar")
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Link(Icons.Filled.Code, "Código en GitHub", "cfernande1408/trazo") { context.openUrl(REPO) }
                HorizontalDivider()
                Link(Icons.Filled.NewReleases, "Novedades de cada versión", "Estable y beta, con su APK") { context.openUrl("$REPO/releases") }
                HorizontalDivider()
                Link(Icons.AutoMirrored.Filled.ListAlt, "Todo lo que hace", "La lista de funciones") { context.openUrl("$REPO/blob/main/FEATURES.md") }
                HorizontalDivider()
                Link(Icons.Filled.BugReport, "Avisar de un fallo o pedir algo", "Abre una incidencia en GitHub") {
                    context.openUrl("$REPO/issues/new")
                }
            }

            Text(
                "Datos: © colaboradores de OpenStreetMap, Instituto Geográfico Nacional (CC BY 4.0), Waymarked Trails, " +
                    "OpenFreeMap, Open-Meteo, BRouter y Nominatim.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Tus rutas, salidas y ajustes se quedan en el móvil. Solo sale lo que publicas en la comunidad.",
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
    val (bg, fg) = when (season) {
        Season.SPRING -> R.drawable.ic_launcher_bg_spring to R.drawable.ic_launcher_fg_spring
        Season.SUMMER -> R.drawable.ic_launcher_bg_summer to R.drawable.ic_launcher_fg_summer
        Season.AUTUMN -> R.drawable.ic_launcher_bg_autumn to R.drawable.ic_launcher_fg_autumn
        Season.WINTER -> R.drawable.ic_launcher_bg_winter to R.drawable.ic_launcher_fg_winter
    }
    // El icono adaptativo mide 108 y se ve el círculo central de 72: se dibuja a 120 dp
    // y se recorta a 80, como lo enseña el escritorio
    Box(Modifier.size(80.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        Image(painterResource(bg), contentDescription = null, modifier = Modifier.requiredSize(120.dp))
        Image(painterResource(fg), contentDescription = "Icono de Trazo", modifier = Modifier.requiredSize(120.dp))
    }
}
