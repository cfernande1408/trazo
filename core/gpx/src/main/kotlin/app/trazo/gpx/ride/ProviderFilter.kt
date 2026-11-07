package app.trazo.gpx.ride

/**
 * Android da posiciones del GPS y de la red (wifi y antenas). La de la red
 * sirve para empezar rápido o bajo techo, pero va saltando decenas de metros
 * y su altitud no vale: mezclada con la del GPS hace picos en el perfil,
 * desnivel inventado y velocidades imposibles.
 *
 * Aquí la red solo cuenta mientras no hay GPS (o si se pierde un rato).
 */
class ProviderFilter(private val gpsTimeoutMs: Long = 10_000) {
    private var lastGpsMs: Long? = null

    /** ¿Se usa esta posición? [timeMs] es un reloj que no salta (elapsedRealtime). */
    fun accept(provider: String?, timeMs: Long): Boolean {
        if (provider == GPS) {
            lastGpsMs = timeMs
            return true
        }
        val g = lastGpsMs
        return g == null || timeMs - g > gpsTimeoutMs
    }

    /** La altitud solo se usa si viene del GPS. */
    fun trustAltitude(provider: String?) = provider == GPS

    companion object {
        const val GPS = "gps"
    }
}
