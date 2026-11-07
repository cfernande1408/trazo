package app.trazo.gpx.sensors

/**
 * Lectura de los sensores Bluetooth estándar (perfiles del Bluetooth SIG),
 * los que llevan las bandas de pulso, sensores de cadencia y potenciómetros
 * de cualquier marca. Solo bytes: sin Android, para poder probarlo.
 */
object Ble {
    const val HEART_RATE_SERVICE = "0000180d-0000-1000-8000-00805f9b34fb"
    const val HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb"
    const val CSC_SERVICE = "00001816-0000-1000-8000-00805f9b34fb"
    const val CSC_MEASUREMENT = "00002a5b-0000-1000-8000-00805f9b34fb"
    const val POWER_SERVICE = "00001818-0000-1000-8000-00805f9b34fb"
    const val POWER_MEASUREMENT = "00002a63-0000-1000-8000-00805f9b34fb"
    /** Descriptor para pedir notificaciones. */
    const val CCCD = "00002902-0000-1000-8000-00805f9b34fb"

    /** Pulsaciones por minuto (Heart Rate Measurement, 0x2A37). */
    fun heartRate(b: ByteArray): Int? {
        if (b.isEmpty()) return null
        val sixteen = b[0].toInt() and 0x01 != 0
        return if (sixteen) b.u16(1) else b.getOrNull(1)?.toInt()?.and(0xFF)
    }

    /** Potencia instantánea en vatios (Cycling Power Measurement, 0x2A63). */
    fun power(b: ByteArray): Int? = if (b.size >= 4) b.s16(2) else null

    /** Vueltas de biela acumuladas y hora del último evento (1/1024 s) de un CSC (0x2A5B). */
    fun cscCrank(b: ByteArray): Pair<Int, Int>? {
        if (b.isEmpty()) return null
        val flags = b[0].toInt()
        var i = 1
        if (flags and 0x01 != 0) i += 6 // datos de rueda: uint32 vueltas + uint16 hora
        if (flags and 0x02 == 0 || b.size < i + 4) return null
        return b.u16(i) to b.u16(i + 2)
    }

    /** Igual que [cscCrank] pero en un potenciómetro, que la lleva más adentro según los flags. */
    fun powerCrank(b: ByteArray): Pair<Int, Int>? {
        if (b.size < 4) return null
        val flags = b.u16(0)
        var i = 4 // flags + potencia
        if (flags and 0x01 != 0) i += 1 // balance de pedales
        if (flags and 0x04 != 0) i += 2 // par acumulado
        if (flags and 0x10 != 0) i += 6 // datos de rueda
        if (flags and 0x20 == 0 || b.size < i + 4) return null
        return b.u16(i) to b.u16(i + 2)
    }

    private fun ByteArray.u16(i: Int) = (this[i].toInt() and 0xFF) or ((this[i + 1].toInt() and 0xFF) shl 8)
    private fun ByteArray.s16(i: Int) = u16(i).toShort().toInt()
}

/**
 * Cadencia (rpm) a partir de vueltas de biela acumuladas, que se dan la
 * vuelta a 65536, igual que la hora (en 1/1024 s). Si dejas de pedalear
 * más de 3 s, vuelve a 0.
 */
class CadenceMeter {
    private var lastRevs: Int? = null
    private var lastTime: Int? = null
    private var lastChangeMs = 0L
    var rpm: Int = 0
        private set

    fun update(revs: Int, eventTime: Int, nowMs: Long): Int {
        val pr = lastRevs
        val pt = lastTime
        lastRevs = revs
        lastTime = eventTime
        if (pr != null && pt != null) {
            val dRevs = (revs - pr) and 0xFFFF
            val dTime = (eventTime - pt) and 0xFFFF
            if (dRevs > 0 && dTime > 0) {
                rpm = (dRevs * 60 * 1024 / dTime).coerceAtMost(250)
                lastChangeMs = nowMs
            } else if (nowMs - lastChangeMs > 3_000) {
                rpm = 0
            }
        }
        return rpm
    }
}
