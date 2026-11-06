package app.trazo.gpx.osm

import app.trazo.gpx.GeoPoint
import app.trazo.gpx.TrackStatsCalculator

/**
 * Las rutas de OSM son una lista de vías sin orden garantizado y cada una en
 * el sentido en que se dibujó. Esto las encadena por sus extremos, dándoles
 * la vuelta cuando hace falta. Lo que no se puede unir queda como otro tramo.
 */
object WayStitcher {

    fun stitch(ways: List<List<GeoPoint>>, toleranceMeters: Double = 30.0): List<List<GeoPoint>> {
        val pending = ways.filter { it.size >= 2 }.toMutableList()
        val out = mutableListOf<List<GeoPoint>>()
        fun near(a: GeoPoint, b: GeoPoint) = TrackStatsCalculator.haversineMeters(a, b) <= toleranceMeters

        while (pending.isNotEmpty()) {
            val chain = ArrayDeque(pending.removeAt(0))
            var grew = true
            while (grew) {
                grew = false
                val it = pending.iterator()
                while (it.hasNext()) {
                    val w = it.next()
                    when {
                        near(chain.last(), w.first()) -> w.drop(1).forEach(chain::addLast)
                        near(chain.last(), w.last()) -> w.reversed().drop(1).forEach(chain::addLast)
                        near(chain.first(), w.last()) -> w.dropLast(1).reversed().forEach(chain::addFirst)
                        near(chain.first(), w.first()) -> w.drop(1).forEach(chain::addFirst)
                        else -> continue
                    }
                    it.remove()
                    grew = true
                }
            }
            out.add(chain.toList())
        }
        // El tramo más largo primero: suele ser la ruta en sí
        return out.sortedByDescending { it.size }
    }
}
