package app.trazo.explore

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.trazo.gpx.Gpx
import app.trazo.gpx.GpxParseException
import app.trazo.gpx.GpxParser
import app.trazo.net.Http
import kotlinx.coroutines.launch
import java.io.IOException

private const val START_URL = "https://es.wikiloc.com/"

/**
 * La web de Wikiloc dentro de la app. Navegas y descargas tú, con tu cuenta,
 * como en el navegador; la app solo recoge el GPX que descargas y lo importa.
 * No lista ni descarga nada por su cuenta.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikilocBrowser(onGpx: (Gpx) -> Unit, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    canGoBack = view.canGoBack()
                }
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            loadUrl(START_URL)
        }
    }

    DisposableEffect(webView) {
        webView.setDownloadListener { url, userAgent, _, _, _ ->
            scope.launch {
                busy = true
                error = null
                runCatching { downloadGpx(url, userAgent) }
                    .onSuccess(onGpx)
                    .onFailure { error = it.message }
                busy = false
            }
        }
        onDispose { webView.destroy() }
    }

    // Atrás navega dentro de Wikiloc; en la primera página lo deja pasar
    BackHandler(enabled = canGoBack || onClose != null) {
        if (webView.canGoBack()) webView.goBack() else onClose?.invoke()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wikiloc") },
                navigationIcon = {
                    if (canGoBack || onClose != null) {
                        IconButton(onClick = { if (webView.canGoBack()) webView.goBack() else onClose?.invoke() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (busy || error != null) {
                Card(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Text("Importando la ruta…")
                        } else {
                            Column(Modifier.weight(1f)) {
                                Text("No se pudo importar", style = MaterialTheme.typography.titleSmall)
                                Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { error = null }) { Text("Vale") }
                        }
                    }
                }
            }
        }
    }
}

/** Descarga el archivo con las cookies de tu sesión, como haría el navegador. */
private suspend fun downloadGpx(url: String, userAgent: String): Gpx {
    if (!url.startsWith("http")) {
        throw IOException("Esta descarga no se puede recoger desde aquí. Descárgala en el navegador y usa Compartir → Trazo.")
    }
    val headers = buildMap {
        put("User-Agent", userAgent)
        CookieManager.getInstance().getCookie(url)?.let { put("Cookie", it) }
    }
    val text = Http.get(url, headers)
    return try {
        GpxParser.parse(text)
    } catch (e: GpxParseException) {
        throw IOException("Lo descargado no es un GPX. Elige el formato GPX al descargar.")
    }
}
