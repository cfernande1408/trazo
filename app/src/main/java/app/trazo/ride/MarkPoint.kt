package app.trazo.ride

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.trazo.gpx.Waypoint
import java.io.File

/** Tipos de punto que se marcan con un toque; el nombre del punto es el tipo. */
private val KINDS = listOf("Fuente", "Mirador", "Peligro", "Desvío", "Refugio", "Foto", "Otro")

/** Botón para marcar un punto mientras grabas. */
@Composable
fun MarkButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Icon(Icons.Filled.AddLocationAlt, contentDescription = "Marcar un punto aquí")
    }
}

/**
 * Marcar un punto donde estás, como en Wikiloc: tipo, nota y una foto.
 * La posición se coge al abrir, así que puedes seguir andando mientras escribes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Se guarda todo por si el sistema cierra la pantalla mientras está abierta la cámara
    // rememberSaveable no admite null directamente: va dentro de un estado
    val here: DoubleArray? by rememberSaveable {
        mutableStateOf(
            RideSession.lastLocation?.let { l ->
                doubleArrayOf(l.latitude, l.longitude, RideSession.lastAltitude ?: if (l.hasAltitude()) l.altitude else Double.NaN)
            }
        )
    }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val photo = photoPath?.let(::File)
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingPath?.let(::File)
        pendingPath = null
        if (ok && f != null && f.length() > 0) {
            photo?.delete()
            photoPath = f.path
            if (kind == null) kind = "Foto"
        } else f?.delete()
    }
    fun cancel() {
        photo?.delete()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::cancel,
        title = { Text("Marcar un punto") },
        text = {
            if (here == null) Text("Aún no hay posición del GPS. Espera unos segundos y vuelve a probar.")
            else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KINDS.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = if (kind == k) null else k }, label = { Text(k) })
                    }
                }
                OutlinedTextField(
                    note, { note = it },
                    label = { Text("Nota (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                photo?.let { f ->
                    val bitmap = remember(f) { thumbnail(f, 640) }
                    bitmap?.let {
                        Image(
                            it.asImageBitmap(), contentDescription = "Foto del punto",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
                FilledTonalButton(onClick = {
                    val f = RideStore.newPhoto(context)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                    pendingPath = f.path
                    runCatching { camera.launch(uri) }.onFailure {
                        pendingPath = null
                        f.delete()
                        Toast.makeText(context, "No hay ninguna app de cámara", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Text(if (photo == null) "  Hacer una foto" else "  Repetir la foto")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = here != null, onClick = {
                val h = here ?: return@TextButton
                RideSession.mark(
                    Waypoint(
                        h[0], h[1], h[2].takeUnless { it.isNaN() },
                        name = kind ?: "Punto",
                        desc = note.trim().ifEmpty { null },
                        link = photo?.let(RideStore::photoLink),
                    )
                )
                Toast.makeText(context, "Punto guardado", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = ::cancel) { Text("Cancelar") } },
    )
}

/**
 * La foto reducida a unos [maxPx] de lado, para no cargar 12 megapíxeles en
 * memoria, y girada como dice su EXIF (las cámaras guardan en apaisado).
 */
internal fun thumbnail(file: File, maxPx: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) bitmap
    else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
}.getOrNull()
