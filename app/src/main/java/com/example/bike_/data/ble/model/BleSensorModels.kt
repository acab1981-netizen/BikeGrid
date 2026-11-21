package com.example.bike_.data.ble.model

import java.util.UUID

enum class BleSensorType {
    HEART_RATE,
    CYCLING_SPEED_AND_CADENCE,
    CYCLING_POWER,
    RUNNING_SPEED_AND_CADENCE,
    FITNESS_MACHINE
}

enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

data class DiscoveredBleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val serviceUuids: Set<UUID> = emptySet()
) {
    val stableId: String get() = address
}

data class BleConnectionStatus(
    val address: String,
    val name: String? = null,
    val sensorTypes: Set<BleSensorType> = emptySet(),
    val state: BleConnectionState = BleConnectionState.DISCONNECTED,
    val autoReconnect: Boolean = false,
    val lastError: String? = null,
    val batteryPercent: Int? = null
) {
    val stableId: String get() = address
}

data class BleSensorReadings(
    val heartRateBpm: Int? = null,
    val cadenceRpm: Float? = null,
    val sensorSpeedKmh: Float? = null,
    val powerWatts: Int? = null,
    /** Steps per minute from an RSC (0x1814) foot pod. */
    val stepCadenceSpm: Int? = null,
    /** Walking/running flag reported by the RSC sensor. */
    val rscIsRunning: Boolean? = null
)

enum class FtmsTrainerMode {
    ERG,
    RESISTANCE
}

data class FtmsTrainerState(
    val connected: Boolean = false,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val mode: FtmsTrainerMode = FtmsTrainerMode.ERG,
    val controlGranted: Boolean = false,
    val targetPowerWatts: Int? = null,
    val targetResistancePercent: Int? = null,
    val lastError: String? = null,
    val indoorBikeDataPowerWatts: Int? = null,
    val indoorBikeDataCadenceRpm: Float? = null,
    val indoorBikeDataSpeedKmh: Float? = null
)
