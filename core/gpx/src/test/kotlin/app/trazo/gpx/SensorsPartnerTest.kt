package app.trazo.gpx

import app.trazo.gpx.nav.DataField
import app.trazo.gpx.nav.VirtualPartner
import app.trazo.gpx.ride.GpxWriter
import app.trazo.gpx.ride.RideCalculator
import app.trazo.gpx.ride.TimedPoint
import app.trazo.gpx.sensors.Ble
import app.trazo.gpx.sensors.CadenceMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorsPartnerTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `pulso en 8 y 16 bits`() {
        assertEquals(72, Ble.heartRate(bytes(0x00, 72)))
        assertEquals(300, Ble.heartRate(bytes(0x01, 0x2C, 0x01)))
        assertNull(Ble.heartRate(ByteArray(0)))
    }

    @Test
    fun `potencia y cadencia del potenciometro`() {
        // flags 0x20 (datos de biela), 250 W, 1000 vueltas, hora 2048
        val b = bytes(0x20, 0x00, 0xFA, 0x00, 0xE8, 0x03, 0x00, 0x08)
        assertEquals(250, Ble.power(b))
        assertEquals(1000 to 2048, Ble.powerCrank(b))
        // Con balance de pedales (1 byte) delante de la biela
        val b2 = bytes(0x21, 0x00, 0x64, 0x00, 0x32, 0x0A, 0x00, 0x00, 0x04)
        assertEquals(100, Ble.power(b2))
        assertEquals(10 to 1024, Ble.powerCrank(b2))
        assertNull(Ble.powerCrank(bytes(0x00, 0x00, 0x64, 0x00)))
    }

    @Test
    fun `cadencia del sensor CSC con vueltas que se dan la vuelta`() {
        // flags 0x03: rueda (6 bytes) y biela
        val first = bytes(0x03, 1, 0, 0, 0, 0, 0, 0xFF, 0xFF, 0x00, 0xFC)
        assertEquals(0xFFFF to 0xFC00, Ble.cscCrank(first))
        val m = CadenceMeter()
        m.update(0xFFFF, 0xFC00, 0)
        // 2 vueltas en 1,5 s (1536/1024), pasando por 65535 → 1 y 0xFC00 → 0x0200
        assertEquals(80, m.update(1, 0x0200, 1_500))
        assertEquals(80, m.update(1, 0x0200, 2_500)) // misma lectura, aún dentro de 3 s
        assertEquals(0, m.update(1, 0x0200, 5_000)) // has dejado de pedalear
    }

    @Test
    fun `sensores en el GPX ida y vuelta y en las estadisticas`() {
        val pts = (0..10).map { TimedPoint(40.0 + it * 0.0005, -3.9, 800.0, 1_800_000_000_000 + it * 10_000L, hr = 140 + it, cad = if (it == 5) 0 else 80, power = 200) }
        val gpx = GpxParser.parse(GpxWriter.write("Prueba", listOf(pts), type = "hiking"))
        val back = RideCalculator.fromGpx(gpx).single()
        assertEquals(145, back[5].hr)
        assertEquals(200, back[3].power)
        val s = RideCalculator.compute(listOf(back))
        assertEquals(145, s.avgHr)
        assertEquals(150, s.maxHr)
        assertEquals(80, s.avgCadence) // los ratos a 0 no cuentan
        assertEquals(200, s.avgPower)
        val noSensors = RideCalculator.compute(listOf(pts.map { it.copy(hr = null, cad = null, power = null) }))
        assertNull(noSensors.avgHr)
    }

    @Test
    fun `compañero virtual`() {
        val route = listOf((0..300).map { GeoPoint(40.0 + it * 10 / 110_574.0, -3.9) })
        // Entonces: 3 km a 5 m/s (10 min)
        val before = listOf((0..300).map { TimedPoint(40.0 + it * 10 / 110_574.0, -3.9, null, 1_000_000L + it * 2_000L) })
        val vp = VirtualPartner(before, route)
        assertTrue(vp.usable)
        assertEquals(200_000.0, vp.elapsedAt(1_000.0)!!.toDouble(), 3_000.0)
        // Ahora llegas al km 1 en 3 min: 20 s por delante
        assertEquals(20_000.0, vp.gapMs(1_000.0, 180_000)!!.toDouble(), 2_000.0)
        assertNull(vp.gapMs(5_000.0, 0))
        assertTrue(!VirtualPartner(emptyList(), route).usable)
    }

    @Test
    fun `campos de datos guardados`() {
        assertEquals(DataField.DEFAULT, DataField.decode(null))
        val mine = listOf(DataField.HEART_RATE, DataField.POWER)
        val d = DataField.decode(DataField.encode(mine))
        assertEquals(6, d.size)
        assertEquals(mine, d.take(2))
        assertEquals(DataField.DEFAULT, DataField.decode("NADA,COSA"))
    }
}
