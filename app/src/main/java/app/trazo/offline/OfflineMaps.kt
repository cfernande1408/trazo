package app.trazo.offline

import app.trazo.gpx.i18n.L
import android.content.Context
import android.util.Log
import app.trazo.gpx.Bounds
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.offline.OfflineArea
import app.trazo.gpx.offline.OfflineMeta
import app.trazo.map.TrazoMapStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/** Un mapa descargado: la zona de una ruta, con su tamaño en disco. */
data class SavedMap(val region: OfflineRegion, val meta: OfflineMeta, val bytes: Long)

/** Descarga en curso: de 0 a 1, o el error si falló. */
data class Download(val progress: Float, val error: String? = null)

/**
 * Mapas sin conexión con la base de datos offline de MapLibre: se descargan
 * las teselas de la zona de la ruta (mapa, relieve y, si quieres, topo IGN) y
 * MapLibre las usa solas cuando no hay red, en cualquier mapa de la app.
 */
object OfflineMaps {

    private const val TAG = "TrazoOffline"

    private val _maps = MutableStateFlow<List<SavedMap>>(emptyList())
    val maps: StateFlow<List<SavedMap>> = _maps.asStateFlow()

    /** Descargas en curso, por huella de ruta. */
    private val _downloads = MutableStateFlow<Map<String, Download>>(emptyMap())
    val downloads: StateFlow<Map<String, Download>> = _downloads.asStateFlow()

    private fun manager(context: Context) = OfflineManager.getInstance(context.applicationContext).also {
        runCatching { it.setOfflineMapboxTileCountLimit(50_000) }
    }

    fun refresh(context: Context) {
        manager(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions.orEmpty().mapNotNull { r ->
                    OfflineMeta.decode(String(r.metadata, Charsets.UTF_8))?.let { r to it }
                }
                if (regions.isEmpty()) { _maps.value = emptyList(); return }
                val found = mutableListOf<SavedMap>()
                var skipped = 0
                regions.forEach { (r, meta) ->
                    r.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            // Descarga que se quedó a medias (app cerrada, fallo): se borra para poder repetirla
                            if (status?.isComplete != true && meta.routeKey !in _downloads.value) {
                                r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                                    override fun onDelete() {}
                                    override fun onError(error: String) {}
                                })
                                pending(r)
                                return
                            }
                            add(SavedMap(r, meta, status!!.completedResourceSize))
                        }
                        override fun onError(error: String?) = add(SavedMap(r, meta, 0))
                        private fun add(m: SavedMap) {
                            found += m
                            done()
                        }
                        private fun pending(@Suppress("UNUSED_PARAMETER") r: OfflineRegion) {
                            skipped++
                            done()
                        }
                        private fun done() {
                            if (found.size + skipped == regions.size) {
                                _maps.value = found.filter { it.bytes > 0 || it.meta.routeKey !in _downloads.value }
                                    .sortedByDescending { it.meta.createdAtMs }
                            }
                        }
                    })
                }
            }

            override fun onError(error: String) {
                Log.w(TAG, "No se pudieron listar los mapas: $error")
            }
        })
    }

    fun isSaved(routeKey: String) = _maps.value.firstOrNull { it.meta.routeKey == routeKey }

    fun download(context: Context, name: String, segments: List<List<GeoPoint>>, withTopo: Boolean) {
        val b = OfflineArea.around(segments) ?: return
        downloadArea(context, name, OfflineArea.routeKey(segments), b, withTopo)
    }

    /** Cualquier zona (la que ves en Explorar), con su propia huella [key]. */
    fun downloadArea(context: Context, name: String, key: String, b: Bounds, withTopo: Boolean) {
        if (_downloads.value[key]?.error == null && key in _downloads.value) return
        _downloads.value = _downloads.value + (key to Download(0f))
        val definition = OfflineTilePyramidRegionDefinition(
            LocalStyleServer.urlFor(TrazoMapStyle.offlineStyleFile(context, withTopo)),
            LatLngBounds.from(b.maxLat, b.maxLon, b.minLat, b.minLon),
            OfflineArea.MIN_ZOOM.toDouble(),
            (if (withTopo) OfflineArea.TOPO_MAX_ZOOM else OfflineArea.MAX_ZOOM).toDouble(),
            context.resources.displayMetrics.density,
        )
        val meta = OfflineMeta(name, key, withTopo, System.currentTimeMillis())
        manager(context).createOfflineRegion(definition, meta.encode().toByteArray(Charsets.UTF_8),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val p = if (status.requiredResourceCount > 0)
                                status.completedResourceCount.toFloat() / status.requiredResourceCount else 0f
                            if (status.isComplete) {
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                _downloads.value = _downloads.value - key
                                refresh(context)
                            } else {
                                _downloads.value = _downloads.value + (key to Download(p))
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            Log.w(TAG, "Error descargando: ${error.reason} ${error.message}")
                            // Cortes sueltos los reintenta MapLibre solo; solo se avisa
                            val d = _downloads.value[key] ?: Download(0f)
                            _downloads.value = _downloads.value + (key to d.copy(error = L("Conexión inestable, sigo intentándolo…", "Unstable connection, still trying…")))
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            _downloads.value = _downloads.value + (key to Download(0f, L("La zona es demasiado grande para descargarla.", "The area is too big to download.")))
                        }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    _downloads.value = _downloads.value + (key to Download(0f, L("No se pudo empezar la descarga: $error", "Could not start the download: $error")))
                }
            })
    }

    fun delete(context: Context, map: SavedMap) {
        map.region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        map.region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() = refresh(context)
            override fun onError(error: String) {
                Log.w(TAG, "No se pudo borrar: $error")
            }
        })
    }

    fun clearError(key: String) {
        _downloads.value = _downloads.value - key
    }
}
