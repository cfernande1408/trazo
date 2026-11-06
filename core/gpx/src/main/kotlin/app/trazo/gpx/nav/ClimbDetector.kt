package app.trazo.gpx.nav

import app.trazo.gpx.ProfilePoint

data class Climb(val startMeters: Double, val endMeters: Double, val gainMeters: Double) {
    val lengthMeters get() = endMeters - startMeters
    val avgGradePercent get() = if (lengthMeters > 0) gainMeters / lengthMeters * 100 else 0.0
}

object ClimbDetector {

    /**
     * Busca subidas en el perfil: tramos que ganan al menos [minGain] metros
     * con pendiente media ≥ [minGradePercent]. Una bajada de menos de
     * [dipTolerance] metros no corta la subida (rellanos, ruido del GPS).
     */
    fun detect(
        profile: List<ProfilePoint>,
        minGain: Double = 30.0,
        minGradePercent: Double = 3.0,
        dipTolerance: Double = 10.0,
    ): List<Climb> {
        if (profile.size < 2) return emptyList()
        val out = mutableListOf<Climb>()
        var start = 0
        var peak = 0

        fun close() {
            if (peak <= start) return
            val gain = profile[peak].ele - profile[start].ele
            val len = profile[peak].distanceMeters - profile[start].distanceMeters
            if (gain >= minGain && len > 0 && gain / len * 100 >= minGradePercent) {
                out.add(Climb(profile[start].distanceMeters, profile[peak].distanceMeters, gain))
            }
        }

        for (i in 1 until profile.size) {
            val e = profile[i].ele
            when {
                e > profile[peak].ele -> peak = i
                peak == start && e <= profile[start].ele -> { start = i; peak = i }
                profile[peak].ele - e > dipTolerance -> { close(); start = i; peak = i }
            }
        }
        close()
        return out
    }
}
