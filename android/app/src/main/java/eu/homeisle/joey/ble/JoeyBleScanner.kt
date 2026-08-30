package eu.homeisle.joey.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class JoeyBleScanner(private val context: Context) {

    data class Device(
        val address: String,
        val name: String?,
        val rssi: Int,
        val device: BluetoothDevice
    )

    data class GattValue(
        val serviceUuid: UUID,
        val characteristicUuid: UUID,
        val hex: String
    )

    sealed interface State {
        data object Idle : State
        data object Scanning : State
        data class Connecting(val name: String) : State
        data class Connected(val name: String) : State
        data class Error(val message: String) : State
    }

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _gattValues = MutableStateFlow<List<GattValue>>(emptyList())
    val gattValues: StateFlow<List<GattValue>> = _gattValues.asStateFlow()

    private var gatt: BluetoothGatt? = null

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addResult(result)
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::addResult)
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = State.Error("Échec du scan BLE : code $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun addResult(result: ScanResult) {
        val name = try {
            result.device.name ?: result.scanRecord?.deviceName
        } catch (_: SecurityException) {
            result.scanRecord?.deviceName
        }
        val device = Device(
            address = result.device.address,
            name = name,
            rssi = result.rssi,
            device = result.device
        )
        _devices.value = (_devices.value.filterNot { it.address == device.address } + device)
            .sortedByDescending { it.rssi }
    }

    fun hasScanPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun bluetoothAvailable(): Boolean = adapter != null

    fun bluetoothEnabled(): Boolean = try {
        adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasScanPermission()) {
            _state.value = State.Error("Permission Appareils à proximité refusée")
            return
        }
        if (!bluetoothAvailable()) {
            _state.value = State.Error("Bluetooth non disponible sur ce téléphone")
            return
        }
        if (!bluetoothEnabled()) {
            _state.value = State.Error("Bluetooth désactivé")
            return
        }

        val bleScanner = scanner
        if (bleScanner == null) {
            _state.value = State.Error("Scanner Bluetooth LE indisponible")
            return
        }

        _devices.value = emptyList()
        _gattValues.value = emptyList()
        _state.value = State.Scanning

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            bleScanner.stopScan(scanCallback)
            bleScanner.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            _state.value = State.Error("Autorisation Bluetooth manquante : ${e.message ?: "accès refusé"}")
        } catch (e: IllegalStateException) {
            _state.value = State.Error("Bluetooth indisponible : ${e.message ?: "état invalide"}")
        } catch (e: Exception) {
            _state.value = State.Error("Impossible de démarrer le scan : ${e.javaClass.simpleName}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            if (hasScanPermission()) scanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        if (_state.value is State.Scanning) _state.value = State.Idle
    }

    @SuppressLint("MissingPermission")
    fun connect(device: Device) {
        if (!hasConnectPermission()) {
            _state.value = State.Error("Permission de connexion Bluetooth requise")
            return
        }
        stopScan()
        _state.value = State.Connecting(device.name ?: device.address)
        try {
            gatt?.close()
            gatt = device.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            _state.value = State.Error("Connexion GATT impossible : ${e.javaClass.simpleName}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        _state.value = State.Idle
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS && newState != android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                _state.value = State.Error("Erreur GATT : statut $status")
                return
            }
            when (newState) {
                android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = State.Connected(gatt.device.name ?: gatt.device.address)
                    gatt.discoverServices()
                }
                android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> _state.value = State.Idle
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = State.Error("Découverte des services GATT impossible : $status")
                return
            }
            readNextReadableCharacteristic(gatt.services, 0, 0)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) recordValue(characteristic)
            continueAfter(gatt, characteristic)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) recordValue(characteristic, value)
            continueAfter(gatt, characteristic)
        }
    }

    private fun recordValue(characteristic: BluetoothGattCharacteristic, bytes: ByteArray? = characteristic.value) {
        val serviceUuid = characteristic.service?.uuid ?: return
        val hex = (bytes ?: byteArrayOf()).joinToString(" ") { "%02X".format(it) }
        val value = GattValue(serviceUuid, characteristic.uuid, hex)
        _gattValues.value = _gattValues.value.filterNot {
            it.serviceUuid == serviceUuid && it.characteristicUuid == characteristic.uuid
        } + value
    }

    @SuppressLint("MissingPermission")
    private fun continueAfter(gatt: BluetoothGatt, current: BluetoothGattCharacteristic) {
        val services = gatt.services
        val serviceIndex = services.indexOf(current.service)
        val characteristicIndex = current.service.characteristics.indexOf(current)
        readNextReadableCharacteristic(services, serviceIndex, characteristicIndex + 1)
    }

    @SuppressLint("MissingPermission")
    private fun readNextReadableCharacteristic(services: List<BluetoothGattService>, startService: Int, startCharacteristic: Int) {
        for (si in startService until services.size) {
            val chars = services[si].characteristics
            val first = if (si == startService) startCharacteristic else 0
            for (ci in first until chars.size) {
                val characteristic = chars[ci]
                if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                    gatt?.readCharacteristic(characteristic)
                    return
                }
            }
        }
    }
}
