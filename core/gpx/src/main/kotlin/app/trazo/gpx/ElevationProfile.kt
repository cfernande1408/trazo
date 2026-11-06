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

    /** Altitud interpolada a [meters] del inicio; null si el perfil está vacío. */
    fun eleAt(profile: List<ProfilePoint>, meters: Double): Double? {
        if (profile.isEmpty()) return null
        if (meters <= profile.first().distanceMeters) return profile.first().ele
        if (meters >= profile.last().distanceMeters) return profile.last().ele
        var lo = 0
        var hi = profile.lastIndex
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (profile[mid].distanceMeters <= meters) lo = mid else hi = mid
        }
        val a = profile[lo]
        val b = profile[hi]
        val span = b.distanceMeters - a.distanceMeters
        return if (span <= 0) a.ele else a.ele + (b.ele - a.ele) * (meters - a.distanceMeters) / span
    }
}
