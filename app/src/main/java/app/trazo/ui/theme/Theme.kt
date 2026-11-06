package app.trazo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFFC5F26B),
    onPrimary = Color(0xFF1F3700),
    primaryContainer = Color(0xFF2E4F00),
    onPrimaryContainer = Color(0xFFD8FF9A),
    secondary = Color(0xFFC1CAAB),
    onSecondary = Color(0xFF2B331D),
    secondaryContainer = Color(0xFF414A31),
    onSecondaryContainer = Color(0xFFDDE6C6),
    tertiary = Color(0xFFA0D0CB),
    onTertiary = Color(0xFF003734),
    tertiaryContainer = Color(0xFF1F4E4B),
    onTertiaryContainer = Color(0xFFBCECE7),
    background = Color(0xFF121410),
    onBackground = Color(0xFFE3E4DA),
    surface = Color(0xFF121410),
    onSurface = Color(0xFFE3E4DA),
    onSurfaceVariant = Color(0xFFC4C8B9),
    outline = Color(0xFF8E9284),
    surfaceContainer = Color(0xFF1E201A),
    surfaceContainerHigh = Color(0xFF282B24),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Modo sol: más contraste y superficies blancas para leer a pleno sol. */
private val Light = lightColorScheme(
    primary = Color(0xFF4A6800),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC5F26B),
    onPrimaryContainer = Color(0xFF142600),
    secondary = Color(0xFF586249),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE6C8),
    onSecondaryContainer = Color(0xFF161E0B),
    tertiary = Color(0xFF386663),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCECE7),
    onTertiaryContainer = Color(0xFF00201E),
    background = Color(0xFFF9FAEF),
    onBackground = Color(0xFF1A1C16),
    surface = Color(0xFFF9FAEF),
    onSurface = Color(0xFF1A1C16),
    onSurfaceVariant = Color(0xFF44483D),
    outline = Color(0xFF75796C),
    surfaceContainer = Color(0xFFEEEFE3),
    surfaceContainerHigh = Color(0xFFE8E9DE),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/** Escala de dificultad técnica S0–S3: distinta también en luminosidad. */
object Dificultad {
    fun color(nivel: Int, dark: Boolean): Color = when (nivel) {
        0 -> if (dark) Color(0xFFA8D8FF) else Color(0xFF1F6FB2)
        1 -> if (dark) Color(0xFFFFD27A) else Color(0xFFE0A100)
        2 -> if (dark) Color(0xFFFF9548) else Color(0xFFC2410C)
        else -> if (dark) Color(0xFFF2B8B5) else Color(0xFFB3261E)
    }
}

/** Senderismo: tonos tierra y ámbar en lugar del lima de la bici. */
private val HikeDark = darkColorScheme(
    primary = Color(0xFFFFB870),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF6A3C00),
    onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFE2C0A4),
    onSecondary = Color(0xFF412C18),
    secondaryContainer = Color(0xFF5A4330),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFFB5CEA4),
    onTertiary = Color(0xFF223518),
    tertiaryContainer = Color(0xFF384C2C),
    onTertiaryContainer = Color(0xFFD1EABF),
    background = Color(0xFF17130F),
    onBackground = Color(0xFFECE0D8),
    surface = Color(0xFF17130F),
    onSurface = Color(0xFFECE0D8),
    onSurfaceVariant = Color(0xFFD5C3B5),
    outline = Color(0xFF9E8E81),
    surfaceContainer = Color(0xFF241F1A),
    surfaceContainerHigh = Color(0xFF2F2924),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val HikeLight = lightColorScheme(
    primary = Color(0xFF8A5100),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFF735944),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF2A1707),
    tertiary = Color(0xFF4F6545),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1EABF),
    onTertiaryContainer = Color(0xFF0D2007),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF201B16),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF201B16),
    onSurfaceVariant = Color(0xFF52443A),
    outline = Color(0xFF85746A),
    surfaceContainer = Color(0xFFF7ECE4),
    surfaceContainerHigh = Color(0xFFF1E6DE),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun TrazoTheme(
    activity: app.trazo.gpx.Activity? = null,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val hike = activity == app.trazo.gpx.Activity.HIKE
    val scheme = when {
        hike && dark -> HikeDark
        hike -> HikeLight
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
