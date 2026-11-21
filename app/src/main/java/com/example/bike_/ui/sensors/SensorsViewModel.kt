package com.example.bike_.ui.sensors

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.bike_.BikeApplication
import com.example.bike_.data.ble.model.BleConnectionStatus
import com.example.bike_.data.ble.model.DiscoveredBleDevice
import com.example.bike_.data.settings.CyclingSettings
import com.example.bike_.data.settings.KnownSensorPref
import kotlinx.coroutines.flow.StateFlow

class SensorsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BikeApplication
    private val sensors = app.sensorsManager
    private val settings: CyclingSettings = app.cyclingSettings

    val scanResults: StateFlow<List<DiscoveredBleDevice>> = sensors.scanResults
    val connections: StateFlow<List<BleConnectionStatus>> = sensors.connectionStatuses
    val knownSensors: StateFlow<List<KnownSensorPref>> = sensors.knownSensors
    val wheelCircumferenceMm: StateFlow<Float> = settings.wheelCircumferenceMm
    val bleUnfilteredScanEnabled: StateFlow<Boolean> = settings.bleUnfilteredScanEnabled

    fun tryAutoConnectKnown() = sensors.tryAutoConnectKnownDevices()
    fun startScan() = sensors.startScan()
    fun stopScan() = sensors.stopScan()
    fun connect(device: DiscoveredBleDevice) = sensors.connect(device, autoReconnect = true)
    fun disconnect(status: BleConnectionStatus) = sensors.disconnect(status)
    fun reconnectKnown(id: String) = sensors.reconnectKnown(id)
    fun forgetKnown(id: String) = sensors.forgetSensor(id)
    fun setAutoReconnect(id: String, enabled: Boolean) = sensors.setAutoReconnect(id, enabled)
    fun setWheelCircumference(mm: Float) = settings.setWheelCircumferenceMm(mm)
    fun setBleUnfilteredScanEnabled(enabled: Boolean) = settings.setBleUnfilteredScanEnabled(enabled)
}
