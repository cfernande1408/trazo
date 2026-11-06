package app.trazo.gpx.osm

/** Recuadro geográfico (grados). */
data class Bbox(val south: Double, val west: Double, val north: Double, val east: Double) {

    fun contains(o: Bbox) = o.south >= south && o.north <= north && o.west >= west && o.east <= east

    /** Lo agranda un [fraction] de su tamaño por cada lado (para cargar algo más de lo que se ve). */
    fun expand(fraction: Double): Bbox {
        val dLat = (north - south) * fraction
        val dLon = (east - west) * fraction
        return Bbox(south - dLat, west - dLon, north + dLat, east + dLon)
    }
}
