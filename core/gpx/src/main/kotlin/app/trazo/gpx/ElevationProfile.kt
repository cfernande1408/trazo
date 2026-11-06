package app.trazo.gpx

data class ProfilePoint(val distanceMeters: Double, val ele: Double)

object ElevationProfile {

    /**
     * Elevación frente a distancia acumulada. El hueco entre segmentos no suma
     * distancia; los puntos sin elevación cuentan para la distancia pero no
     * aparecen en el perfil.
     */
    fun of(segments: List<List<GeoPoint>>): List<ProfilePoint> {
        val out = mutableListOf<ProfilePoint>()
        var dist = 0.0
        for (seg in segments) {
            for ((i, p) in seg.withIndex()) {
                if (i > 0) dist += TrackStatsCalculator.haversineMeters(seg[i - 1], p)
                p.ele?.let { out.add(ProfilePoint(dist, it)) }
            }
        }
        return out
    }
}
