package com.example.bike_.data.sensors

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.example.bike_.data.ble.BleSensorsManager
import com.example.bike_.data.ble.model.BleConnectionState
import com.example.bike_.data.ble.model.BleConnectionStatus
import com.example.bike_.data.ble.model.BleSensorReadings
import com.example.bike_.data.ble.model.DiscoveredBleDevice
import com.example.bike_.data.settings.CyclingSettings
import com.example.bike_.data.settings.KnownSensorPref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Unified BLE sensor entry point for UI/service.
 * Wraps [BleSensorsManager], persists known sensors, and auto-reconnects remembered devices.
 */
class UnifiedSensorsManager(
    private val context: Context,
    private val settings: CyclingSettings
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val ble = BleSensorsManager(context, settings)

    val scanResults: StateFlow<List<DiscoveredBleDevice>> = ble.scanResults
    val sensorReadings: StateFlow<BleSensorReadings> = ble.sensorReadings
    val ftmsState = ble.ftmsState

    private val _connectionStatuses = MutableStateFlow<List<BleConnectionStatus>>(emptyList())
    val connectionStatuses: StateFlow<List<BleConnectionStatus>> = _connectionStatuses.asStateFlow()

    private val _knownSensors = MutableStateFlow(settings.knownSensors.value)
    val knownSensors: StateFlow<List<KnownSensorPref>> = _knownSensors.asStateFlow()

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> tryAutoConnectKnownDevices()
            }
        }
    }

    init {
        scope.launch { ble.connectionStatuses.collectLatest { mergeConnectionsAndPersist(it) } }
        scope.launch { settings.knownSensors.collectLatest { _knownSensors.value = it } }

        try {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(bluetoothStateReceiver, filter)
            }
        } catch (_: Throwable) {
        }

        tryAutoConnectKnownDevices()
    }

    /**
     * Best-effort attempt to reconnect all remembered BLE sensors marked for auto-connect.
     * Safe to call repeatedly (e.g. app start, BT turned on, permissions granted, screen open).
     */
    fun tryAutoConnectKnownDevices() {
        try { ble.autoConnectKnownDevices() } catch (_: Throwable) {}
    }

    fun startScan() {
        try { ble.startScan() } catch (_: Throwable) {}
    }

    fun stopScan() {
        try { ble.stopScan() } catch (_: Throwable) {}
    }

    fun setFtmsMode(mode: com.example.bike_.data.ble.model.FtmsTrainerMode) = try { ble.setFtmsMode(mode) } catch (_: Throwable) {}
    fun setFtmsTargetPower(watts: Int) = try { ble.setTargetPowerWatts(watts) } catch (_: Throwable) {}
    fun setFtmsTargetResistance(percent: Int) = try { ble.setTargetResistancePercent(percent) } catch (_: Throwable) {}
    fun requestFtmsControl() = try { ble.requestFtmsControl() } catch (_: Throwable) {}
    fun startOrResumeFtms() = try { ble.startOrResumeFtms() } catch (_: Throwable) {}
    fun stopPauseFtms() = try { ble.stopPauseFtms() } catch (_: Throwable) {}

    fun connect(device: DiscoveredBleDevice, autoReconnect: Boolean = true) {
        ble.connect(device.address, device.name, autoReconnect)
    }

    fun connectBle(address: String, name: String?, autoReconnect: Boolean = true) {
        ble.connect(address, name, autoReconnect)
    }

    fun disconnect(status: BleConnectionStatus) {
        try { ble.disconnect(status.address) } catch (_: Throwable) {}
        // User-initiated disconnect: keep remembered device but disable auto-connect.
        try {
            if (settings.getKnownSensor(status.stableId) != null) {
                settings.setKnownSensorAutoConnect(status.stableId, false)
            }
        } catch (_: Throwable) {
        }
    }

    fun disconnectAll() {
        try { ble.disconnectAll() } catch (_: Throwable) {}
    }

    fun forgetSensor(id: String) {
        val current = _connectionStatuses.value.firstOrNull { it.stableId == id }
        try {
            if (current != null) {
                disconnect(current)
            } else {
                try { ble.setAutoReconnect(id, false) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {
        }
        try { settings.removeKnownSensor(id) } catch (_: Throwable) {}
        _knownSensors.value = settings.knownSensors.value
    }

    fun setAutoReconnect(id: String, enabled: Boolean) {
        settings.setKnownSensorAutoConnect(id, enabled)
        val current = _connectionStatuses.value.firstOrNull { it.stableId == id }
        if (current != null) {
            try { ble.setAutoReconnect(current.address, enabled) } catch (_: Throwable) {}
        }
        if (enabled) reconnectKnown(id)
    }

    fun reconnectKnown(id: String) {
        val known = settings.getKnownSensor(id) ?: return
        ble.connect(known.address.ifBlank { known.id }, known.name, known.autoConnect)
    }

    private fun mergeConnectionsAndPersist(bleList: List<BleConnectionStatus>) {
        val merged = bleList.sortedWith(
            compareBy<BleConnectionStatus> { stateRank(it.state) }.thenBy { it.name ?: it.stableId }
        )
        _connectionStatuses.value = merged

        merged.forEach { st ->
            if (st.state == BleConnectionState.CONNECTED || st.state == BleConnectionState.CONNECTING || st.autoReconnect) {
                val existing = settings.getKnownSensor(st.stableId)
                val known = KnownSensorPref(
                    id = st.stableId,
                    address = st.address,
                    name = st.name ?: existing?.name,
                    sensorTypes = if (st.sensorTypes.isNotEmpty()) st.sensorTypes.map { it.name } else existing?.sensorTypes.orEmpty(),
                    autoConnect = st.autoReconnect,
                    lastConnectedMs = if (st.state == BleConnectionState.CONNECTED) System.currentTimeMillis() else (existing?.lastConnectedMs ?: 0L)
                )
                settings.upsertKnownSensor(known)
            }
        }
        _knownSensors.value = settings.knownSensors.value
    }

    private fun stateRank(state: BleConnectionState): Int = when (state) {
        BleConnectionState.CONNECTED -> 0
        BleConnectionState.CONNECTING -> 1
        BleConnectionState.DISCONNECTING -> 2
        BleConnectionState.DISCONNECTED -> 3
    }
}
