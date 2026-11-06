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

@Composable
fun TrazoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
