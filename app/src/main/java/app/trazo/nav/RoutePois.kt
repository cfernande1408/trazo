package app.trazo.nav

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.offline.OfflineArea
import app.trazo.gpx.osm.PoiOnRoute
import app.trazo.gpx.osm.Pois
import app.trazo.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fuentes y refugios de la ruta, pedidos a Overpass una sola vez por ruta
 * (el resumen y el mapa usan los mismos).
 */
object RoutePois {
    private val cache = mutableMapOf<String, List<PoiOnRoute>>()

    suspend fun load(segments: List<List<GeoPoint>>): List<PoiOnRoute> {
        val key = OfflineArea.routeKey(segments)
        synchronized(cache) { cache[key] }?.let { return it }
        val pois = Pois.parse(Http.overpass(Pois.query(segments)))
        val onRoute = withContext(Dispatchers.Default) { Pois.alongRoute(pois, segments) }
        synchronized(cache) { cache[key] = onRoute }
        return onRoute
    }
}
