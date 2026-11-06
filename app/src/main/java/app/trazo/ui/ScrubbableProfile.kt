package app.trazo.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import app.trazo.gpx.ElevationProfile
import app.trazo.gpx.ProfilePoint
import app.trazo.importer.ES

/**
 * Perfil de desnivel que se recorre con el dedo: tocar o arrastrar marca un
 * punto y enseña su km y su altitud. [onScrub] recibe los metros desde el
 * inicio para que, por ejemplo, el mapa mueva un punto por la ruta.
 */
@Composable
fun ScrubbableProfile(
    profile: List<ProfilePoint>,
    scrubMeters: Double?,
    onScrub: (Double) -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 110.dp,
) {
    val total = profile.lastOrNull()?.distanceMeters ?: 0.0
    fun toMeters(x: Float, width: Int) = (x / width).coerceIn(0f, 1f) * total

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val m = scrubMeters
            if (m == null) {
                Text("Desliza el dedo por el perfil", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("km ${String.format(ES, "%.1f", m / 1000)}", fontWeight = FontWeight.Bold)
                ElevationProfile.eleAt(profile, m)?.let {
                    Text(String.format(ES, "%.0f m", it), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        ElevationChart(
            profile,
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .pointerInput(profile) { detectTapGestures { onScrub(toMeters(it.x, size.width)) } }
                .pointerInput(profile) {
                    detectHorizontalDragGestures(onDragStart = { onScrub(toMeters(it.x, size.width)) }) { change, _ ->
                        change.consume()
                        onScrub(toMeters(change.position.x, size.width))
                    }
                },
            positionMeters = scrubMeters,
        )
    }
}
