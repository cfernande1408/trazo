package app.trazo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trazo.importer.ImportScreen
import app.trazo.importer.ImportState
import app.trazo.importer.ImportViewModel
import app.trazo.map.MapScreen
import app.trazo.ui.theme.TrazoTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {

    private val importer: ImportViewModel by viewModels()

    private val pickGpx = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(importer::import)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            TrazoTheme {
                val state by importer.state.collectAsStateWithLifecycle()
                var showMap by rememberSaveable { mutableStateOf(false) }
                val loaded = state as? ImportState.Loaded
                if (showMap && loaded != null) {
                    MapScreen(loaded, onBack = { showMap = false })
                } else {
                    ImportScreen(
                        state,
                        onPickFile = { pickGpx.launch(arrayOf("*/*")) },
                        onOpenMap = { showMap = true },
                    )
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
        uri?.let(importer::import)
    }
}
