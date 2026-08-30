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
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * BLE diagnostic layer for Joey.
 *
 * V1 deliberately performs READ-ONLY GATT discovery. We do not write to an
 * unknown characteristic until the Joey protocol has been verified on the
 * real unit. This avoids changing calibration/configuration accidentally.
 */
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
            val device = Device(
                address = result.device.address,
                name = result.device.name ?: result.scanRecord?.deviceName,
                rssi = result.rssi,
                device = result.device
            )
            _devices.value = (_devices.value.filterNot { it.address == device.address } + device)
                .sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = State.Error("Échec du scan BLE : $errorCode")
        }
    }

    fun hasScanPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasScanPermission()) {
            _state.value = State.Error("Permission Bluetooth requise")
            return
        }
        if (adapter?.isEnabled != true) {
            _state.value = State.Error("Bluetooth désactivé")
            return
        }
        _devices.value = emptyList()
        _state.value = State.Scanning
        scanner?.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (hasScanPermission()) scanner?.stopScan(scanCallback)
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
        gatt?.close()
        gatt = device.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.value = State.Idle
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
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
            if (status != BluetoothGatt.GATT_SUCCESS) return
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
