package app.trazo.map

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.trazo.gpx.GeoJson
import app.trazo.gpx.RouteGeometry
import app.trazo.importer.ImportState

/**
 * Vista 3D con relieve. MapLibre para Android aún no tiene terreno 3D, así
 * que esta pantalla usa MapLibre GL JS dentro de un WebView con el mismo
 * estilo topográfico y la ruta encima.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Terrain3DScreen(state: ImportState.Loaded, onBack: () -> Unit) {
    val context = LocalContext.current
    var flying by remember { mutableStateOf(false) }

    val webView = remember {
        val style = context.assets.open("trazo_topo.json").bufferedReader().use { it.readText() }
        val html = context.assets.open("terrain3d.html").bufferedReader().use { it.readText() }
        // Track reducido: el vuelo y la línea no necesitan miles de puntos
        val track = GeoJson.track(state.gpx.segments.map { RouteGeometry.downsample(it, 1500) })
        val b = RouteGeometry.bounds(state.gpx.segments)
        val bounds = b?.let { "[[${it.minLon},${it.minLat}],[${it.maxLon},${it.maxLat}]]" } ?: "null"

        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onFlyEnd() {
                    Handler(Looper.getMainLooper()).post { flying = false }
                }
            }, "Trazo")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    view.evaluateJavascript("init($style, $track, $bounds);", null)
                }
            }
            // Los mensajes de la página van al logcat: adb logcat | grep Trazo3D
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    Log.d("Trazo3D", "${m.messageLevel()} ${m.message()} (${m.sourceId()}:${m.lineNumber()})")
                    return true
                }
            }
            loadDataWithBaseURL("https://trazo.app/", html, "text/html", "utf-8", null)
        }
    }
    DisposableEffect(webView) { onDispose { webView.destroy() } }

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(16.dp).size(56.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
        }

        val controls = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth().height(56.dp)
        if (flying) {
            FilledTonalButton(
                onClick = { webView.evaluateJavascript("overview();", null); flying = false },
                modifier = controls,
            ) { Text("Parar y ver toda la ruta", style = MaterialTheme.typography.titleMedium) }
        } else {
            Button(
                onClick = { webView.evaluateJavascript("fly();", null); flying = true },
                modifier = controls,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Recorrido virtual", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
