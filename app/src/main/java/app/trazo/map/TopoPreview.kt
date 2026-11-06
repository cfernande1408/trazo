package app.trazo.map

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.trazo.importer.ImportState
import app.trazo.ui.RouteShape
import org.maplibre.android.snapshotter.MapSnapshotter

/**
 * Foto estática del mapa topográfico con la ruta. Mientras se genera (o si
 * no hay conexión) enseña la silueta dibujada a mano.
 */
@Composable
fun TopoPreview(state: ImportState.Loaded, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(state.gpx) { mutableStateOf<Bitmap?>(null) }

    BoxWithConstraints(modifier) {
        val density = context.resources.displayMetrics.density
        val wDp = (constraints.maxWidth / density).toInt()
        val hDp = (constraints.maxHeight / density).toInt()

        DisposableEffect(state.gpx, wDp, hDp) {
            val region = TrazoMapStyle.bounds(state, margin = 0.15)
            val snapshotter = if (region != null && wDp > 0 && hDp > 0) {
                MapSnapshotter(
                    context,
                    MapSnapshotter.Options(wDp, hDp)
                        .withPixelRatio(density)
                        .withRegion(region)
                        .withLogo(false)
                        .withStyleBuilder(TrazoMapStyle.builder(context, state)),
                ).also { s ->
                    s.start(
                        { snapshot -> bitmap = snapshot.bitmap },
                        { error -> Log.w("Trazo", "Vista previa del mapa: $error") },
                    )
                }
            } else null
            onDispose { snapshotter?.cancel() }
        }

        val bmp = bitmap
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = "Mapa de la ruta", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Text(
                "© OpenStreetMap",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF44483D),
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        } else {
            RouteShape(state.preview, Modifier.fillMaxSize())
        }
    }
}
