package app.trazo.gpx.nav

import app.trazo.gpx.ElevationProfile
import app.trazo.gpx.Grade
import app.trazo.gpx.ProfilePoint
import app.trazo.gpx.Slope

/** La subida en la que estás ahora, como el ClimbPro de los Garmin. */
data class ClimbProgress(
    val climb: Climb,
    /** 1 de [total]. */
    val number: Int,
    val total: Int,
    val doneMeters: Double,
    val remainingMeters: Double,
    val remainingGainMeters: Double,
    /** Pendiente del tramo de ~100 m en el que estás. */
    val gradePercent: Double,
    /** Perfil solo de esta subida, para dibujarlo con tu posición encima. */
    val profile: List<ProfilePoint>,
)

object ClimbLive {

    fun progress(climbs: List<Climb>, profile: List<ProfilePoint>, grades: List<Grade>, alongMeters: Double): ClimbProgress? {
        val i = climbs.indexOfFirst { alongMeters >= it.startMeters && alongMeters < it.endMeters }
        if (i < 0) return null
        val c = climbs[i]
        val eleNow = ElevationProfile.eleAt(profile, alongMeters) ?: return null
        val top = profile.filter { it.distanceMeters in alongMeters..c.endMeters }.maxOfOrNull { it.ele } ?: eleNow
        return ClimbProgress(
            climb = c,
            number = i + 1,
            total = climbs.size,
            doneMeters = alongMeters - c.startMeters,
            remainingMeters = c.endMeters - alongMeters,
            remainingGainMeters = (top - eleNow).coerceAtLeast(0.0),
            gradePercent = Slope.at(grades, alongMeters) ?: c.avgGradePercent,
            profile = profile.filter { it.distanceMeters in c.startMeters..c.endMeters },
        )
    }
}
