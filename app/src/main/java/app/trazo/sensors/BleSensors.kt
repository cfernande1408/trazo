package app.trazo.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import app.trazo.gpx.sensors.Ble
import app.trazo.gpx.sensors.CadenceMeter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class SensorKind(private val es: String, private val en: String, val service: String) {
    HEART_RATE("Pulso", "Heart rate", Ble.HEART_RATE_SERVICE),
    CADENCE("Cadencia", "Cadence", Ble.CSC_SERVICE),
    POWER("Potencia", "Power", Ble.POWER_SERVICE),
    ;

    val label: String get() = app.trazo.gpx.i18n.L(es, en)
}

data class FoundSensor(val address: String, val name: String, val kinds: Set<SensorKind>)

/** Últimos valores de los sensores; null si no hay o hace rato que no llegan. */
data class Readings(val hr: Int? = null, val cadence: Int? = null, val power: Int? = null, val atMs: Long = 0)

/**
 * Sensores Bluetooth estándar: banda de pulso, sensor de cadencia y
 * potenciómetro. Se buscan y emparejan desde la hoja de sensores y se
 * conectan solos al empezar a grabar o navegar.
 */
@SuppressLint("MissingPermission")
object BleSensors {
    private const val TAG = "TrazoBle"
    private const val PREFS = "trazo"
    private const val KEY = "ble_sensors"

    private val _readings = MutableStateFlow(Readings())
    val readings: StateFlow<Readings> = _readings.asStateFlow()

    private val _found = MutableStateFlow<List<FoundSensor>>(emptyList())
    val found: StateFlow<List<FoundSensor>> = _found.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Direcciones de los sensores guardados y si están conectados. */
    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    val connected: StateFlow<Set<String>> = _connected.asStateFlow()

    private val gatts = mutableMapOf<String, BluetoothGatt>()
    private val cadence = CadenceMeter()
    private val handler = Handler(Looper.getMainLooper())

    /** Permisos que hacen falta para buscar y conectar. */
    fun permissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermissions(context: Context) = permissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun saved(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty()

    private fun setSaved(context: Context, s: Set<String>) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, s).apply()

    /** Lo que estás leyendo ahora, si es reciente (menos de 5 s). */
    fun fresh(): Readings? = _readings.value.takeIf { System.currentTimeMillis() - it.atMs < 5_000 }

    private fun adapter(context: Context) = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val uuids = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid.toString() }
            val kinds = SensorKind.entries.filter { it.service in uuids }.toSet()
            if (kinds.isEmpty()) return
            val s = FoundSensor(result.device.address, result.scanRecord?.deviceName ?: result.device.name ?: "Sensor", kinds)
            _found.value = (_found.value.filter { it.address != s.address } + s).sortedBy { it.name }
        }
    }

    fun scan(context: Context) {
        if (!hasPermissions(context) || _scanning.value) return
        val scanner = adapter(context)?.bluetoothLeScanner ?: return
        _found.value = emptyList()
        val filters = SensorKind.entries.map { ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(it.service))).build() }
        scanner.startScan(filters, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        _scanning.value = true
        handler.postDelayed({ stopScan(context) }, 12_000)
    }

    fun stopScan(context: Context) {
        if (!_scanning.value) return
        runCatching { adapter(context)?.bluetoothLeScanner?.stopScan(scanCallback) }
        _scanning.value = false
    }

    /** Guarda el sensor y lo conecta. */
    fun pair(context: Context, address: String) {
        setSaved(context, saved(context) + address)
        connect(context, address)
    }

    fun forget(context: Context, address: String) {
        setSaved(context, saved(context) - address)
        gatts.remove(address)?.let { runCatching { it.disconnect(); it.close() } }
        _connected.value = _connected.value - address
    }

    /** Conecta todos los sensores guardados (al empezar a grabar o navegar). */
    fun connectSaved(context: Context) {
        if (!hasPermissions(context)) return
        saved(context).forEach { connect(context, it) }
    }

    fun disconnectAll() {
        gatts.values.forEach { runCatching { it.disconnect(); it.close() } }
        gatts.clear()
        _connected.value = emptySet()
    }

    private fun connect(context: Context, address: String) {
        if (address in gatts || !hasPermissions(context)) return
        val device: BluetoothDevice = runCatching { adapter(context)?.getRemoteDevice(address) }.getOrNull() ?: return
        // autoConnect: si el sensor se apaga o se aleja, vuelve a conectar solo
        gatts[address] = device.connectGatt(context.applicationContext, true, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val a = gatt.device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connected.value = _connected.value + a
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connected.value = _connected.value - a
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // Una suscripción cada vez: Android no admite dos escrituras GATT a la vez
            val chars = listOf(Ble.HEART_RATE_MEASUREMENT, Ble.CSC_MEASUREMENT, Ble.POWER_MEASUREMENT).mapNotNull { uuid ->
                gatt.services.firstNotNullOfOrNull { it.getCharacteristic(UUID.fromString(uuid)) }
            }
            pending[gatt.device.address] = chars.toMutableList()
            subscribeNext(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            subscribeNext(gatt)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            onValue(c.uuid.toString(), c.value ?: return)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            onValue(c.uuid.toString(), value)
        }
    }

    private val pending = mutableMapOf<String, MutableList<BluetoothGattCharacteristic>>()

    private fun subscribeNext(gatt: BluetoothGatt) {
        val c = pending[gatt.device.address]?.removeFirstOrNull() ?: return
        gatt.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(UUID.fromString(Ble.CCCD)) ?: return subscribeNext(gatt)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(d)
        }
    }

    private fun onValue(uuid: String, v: ByteArray) {
        val now = System.currentTimeMillis()
        val r = _readings.value
        _readings.value = when (uuid) {
            Ble.HEART_RATE_MEASUREMENT -> r.copy(hr = Ble.heartRate(v), atMs = now)
            Ble.CSC_MEASUREMENT -> Ble.cscCrank(v)?.let { (revs, t) -> r.copy(cadence = cadence.update(revs, t, now), atMs = now) } ?: r
            Ble.POWER_MEASUREMENT -> r.copy(
                power = Ble.power(v),
                cadence = Ble.powerCrank(v)?.let { (revs, t) -> cadence.update(revs, t, now) } ?: r.cadence,
                atMs = now,
            )
            else -> r
        }.also { if (it === r) Log.v(TAG, "Dato ignorado de $uuid") }
    }
}
