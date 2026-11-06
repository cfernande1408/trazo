package app.trazo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trazo.community.CommunityScreen
import app.trazo.explore.ExploreScreen
import app.trazo.explore.WikilocBrowser
import app.trazo.importer.ImportScreen
import app.trazo.importer.ImportState
import app.trazo.importer.ImportViewModel
import app.trazo.map.MapScreen
import app.trazo.map.Terrain3DScreen
import app.trazo.ui.theme.TrazoTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {

    private val importer: ImportViewModel by viewModels()

    private val pickGpx = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(importer::import)
    }

    /** Pestaña seleccionada; también la cambia un GPX que llega por Abrir con / Compartir. */
    private var tab by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            TrazoTheme {
                val state by importer.state.collectAsStateWithLifecycle()
                var showMap by rememberSaveable { mutableStateOf(false) }
                var showWikiloc by rememberSaveable { mutableStateOf(false) }
                var show3D by rememberSaveable { mutableStateOf(false) }
                val loaded = state as? ImportState.Loaded
                val openRoute: (app.trazo.gpx.Gpx) -> Unit = { gpx ->
                    importer.importGpx(gpx)
                    showWikiloc = false
                    tab = 0
                }

                when {
                    show3D && loaded != null -> Terrain3DScreen(loaded, onBack = { show3D = false })
                    showMap && loaded != null -> MapScreen(loaded, onBack = { showMap = false }, onOpen3D = { show3D = true })
                    showWikiloc -> WikilocBrowser(onClose = { showWikiloc = false }, onGpx = openRoute)
                    else -> Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = tab == 0, onClick = { tab = 0 },
                                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                                    label = { Text("Mis rutas") },
                                )
                                NavigationBarItem(
                                    selected = tab == 1, onClick = { tab = 1 },
                                    icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    label = { Text("Explorar") },
                                )
                                NavigationBarItem(
                                    selected = tab == 2, onClick = { tab = 2 },
                                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    label = { Text("Comunidad") },
                                )
                            }
                        },
                    ) { padding ->
                        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                            when (tab) {
                                0 -> ImportScreen(
                                    state,
                                    onPickFile = { pickGpx.launch(arrayOf("*/*")) },
                                    onOpenMap = { showMap = true },
                                )
                                1 -> ExploreScreen(onRouteReady = openRoute, onOpenWikiloc = { showWikiloc = true })
                                else -> CommunityScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        uri?.let {
            tab = 0
            importer.import(it)
        }
    }
}
