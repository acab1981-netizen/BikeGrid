package com.example.bike_.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import androidx.core.content.ContextCompat
import com.example.bike_.data.ble.model.BleConnectionState
import com.example.bike_.data.ble.model.BleConnectionStatus
import com.example.bike_.data.ble.model.BleSensorReadings
import com.example.bike_.data.ble.model.BleSensorType
import com.example.bike_.data.ble.model.DiscoveredBleDevice
import com.example.bike_.data.ble.model.FtmsTrainerMode
import com.example.bike_.data.ble.model.FtmsTrainerState
import com.example.bike_.data.ble.parser.CscParser
import com.example.bike_.data.ble.parser.FtmsParser
import com.example.bike_.data.ble.parser.CyclingPowerParser
import com.example.bike_.data.ble.parser.HeartRateParser
import com.example.bike_.data.ble.parser.RscParser
import com.example.bike_.data.settings.CyclingSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class BleSensorsManager(
    private val context: Context,
    private val cyclingSettings: CyclingSettings
) {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _scanResults = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val scanResults: StateFlow<List<DiscoveredBleDevice>> = _scanResults.asStateFlow()

    private val _connectionStatuses = MutableStateFlow<List<BleConnectionStatus>>(emptyList())
    val connectionStatuses: StateFlow<List<BleConnectionStatus>> = _connectionStatuses.asStateFlow()

    private val _sensorReadings = MutableStateFlow(BleSensorReadings())
    val sensorReadings: StateFlow<BleSensorReadings> = _sensorReadings.asStateFlow()

    private val _ftmsState = MutableStateFlow(FtmsTrainerState())
    val ftmsState: StateFlow<FtmsTrainerState> = _ftmsState.asStateFlow()

    private val sessions = ConcurrentHashMap<String, DeviceSession>()
    @Volatile private var preferredFtmsAddress: String? = null
    private val discoveredMap = linkedMapOf<String, DiscoveredBleDevice>()
    @Volatile private var scanning: Boolean = false

    private val hrParser = HeartRateParser()
    private val powerParser = CyclingPowerParser()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
        }
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScan() {
        val btAdapter = adapter ?: return
        if (!btAdapter.isEnabled) return
        if (!hasScanPermission()) return
        val scanner = btAdapter.bluetoothLeScanner ?: return
        if (scanning) return

        discoveredMap.clear()
        publishScanResults()

        val filters: List<ScanFilter>? = if (cyclingSettings.bleUnfilteredScanEnabled.value) {
            null
        } else {
            listOf(
                ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.HEART_RATE_SERVICE)).build(),
                ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.CYCLING_SPEED_AND_CADENCE_SERVICE)).build(),
                ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.CYCLING_POWER_SERVICE)).build(),
                ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.RUNNING_SPEED_AND_CADENCE_SERVICE)).build(),
                ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.FITNESS_MACHINE_SERVICE)).build()
            )
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanning = true
            scanner.startScan(filters, settings, scanCallback)
        } catch (_: Exception) {
            scanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (!scanning) return
        scanning = false
        try {
            if (hasScanPermission()) {
                scanner.stopScan(scanCallback)
            }
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, name: String? = null, autoReconnect: Boolean = true) {
        val btAdapter = adapter ?: return
        if (!btAdapter.isEnabled || !hasConnectPermission()) return
        val device = try {
            btAdapter.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            return
        }

        val session = sessions.getOrPut(address) {
            DeviceSession(address = address, device = device)
        }
        synchronized(session) {
            session.name = name ?: session.name ?: device.name
            session.autoReconnect = autoReconnect
            session.userInitiatedDisconnect = false
            session.reconnectAttempts = 0
            if (session.state == BleConnectionState.CONNECTED || session.state == BleConnectionState.CONNECTING) {
                publishConnectionStatuses()
                return
            }
            session.state = BleConnectionState.CONNECTING
        }
        publishConnectionStatuses()
        mainHandler.post { connectGattInternal(session) }
    }

    fun disconnect(address: String) {
        val session = sessions[address] ?: return
        synchronized(session) {
            session.userInitiatedDisconnect = true
            session.autoReconnect = false
            session.state = BleConnectionState.DISCONNECTING
            session.pendingReconnectRunnable?.let { mainHandler.removeCallbacks(it) }
            session.pendingReconnectRunnable = null
        }
        publishConnectionStatuses()
        mainHandler.post { closeSessionGatt(session, fromDisconnect = true) }
    }

    fun setTargetPowerWatts(watts: Int) {
        ftmsSession()?.writeFtmsControl(FtmsParser.buildSetTargetPower(watts))
        updateFtms { it.copy(targetPowerWatts = watts.coerceIn(0, 4000), lastError = null) }
    }
    fun setTargetResistancePercent(percent: Int) {
        ftmsSession()?.writeFtmsControl(FtmsParser.buildSetTargetResistanceLevel(percent))
        updateFtms { it.copy(targetResistancePercent = percent.coerceIn(0, 100), lastError = null) }
    }
    fun requestFtmsControl() { ftmsSession()?.writeFtmsControl(FtmsParser.buildRequestControl()) }
    fun startOrResumeFtms() { ftmsSession()?.writeFtmsControl(FtmsParser.buildStartOrResume()) }
    fun stopPauseFtms() { ftmsSession()?.writeFtmsControl(FtmsParser.buildStopPause()) }
    fun setFtmsMode(mode: FtmsTrainerMode) { updateFtms { it.copy(mode = mode, lastError = null) } }

    private fun ftmsSession(): DeviceSession? {
        preferredFtmsAddress?.let { addr -> sessions[addr]?.let { if (it.hasFtmsConnected()) return it } }
        return sessions.values.firstOrNull { it.hasFtmsConnected() }
    }
    private fun updateFtms(transform: (FtmsTrainerState) -> FtmsTrainerState) { _ftmsState.value = transform(_ftmsState.value) }
    private fun publishFtmsStateFromSessions() {
        val s = ftmsSession()
        if (s == null) {
            val cur = _ftmsState.value
            if (cur.connected || cur.controlGranted || cur.deviceAddress != null) {
                _ftmsState.value = cur.copy(connected = false, controlGranted = false, deviceName = null, deviceAddress = null,
                    indoorBikeDataPowerWatts = null, indoorBikeDataCadenceRpm = null, indoorBikeDataSpeedKmh = null)
            }
            return
        }
        val snap = s.ftmsSnapshot()
        preferredFtmsAddress = s.address
        _ftmsState.value = _ftmsState.value.copy(
            connected = snap.connected, deviceName = snap.name, deviceAddress = s.address,
            controlGranted = snap.controlGranted,
            lastError = snap.lastError ?: _ftmsState.value.lastError?.takeIf { !snap.connected },
            indoorBikeDataPowerWatts = snap.indoorPower, indoorBikeDataCadenceRpm = snap.indoorCadence, indoorBikeDataSpeedKmh = snap.indoorSpeed
        )
    }

    fun disconnectAll() {
        sessions.keys().toList().forEach { disconnect(it) }
    }

    /**
     * Auto-connect remembered BLE sensors (persisted in settings) that are marked for auto-connect.
     * Safe to call multiple times.
     */
    @SuppressLint("MissingPermission")
    fun autoConnectKnownDevices() {
        try {
            val known = cyclingSettings.knownSensors.value
                .filter { it.autoConnect && (it.address.ifBlank { it.id }).isNotBlank() }
            known.forEach { k ->
                val addr = k.address.ifBlank { k.id }
                connect(addr, k.name, autoReconnect = true)
            }
        } catch (_: Exception) {
        }
    }

    fun setAutoReconnect(address: String, enabled: Boolean) {
        sessions[address]?.let { session ->
            synchronized(session) {
                session.autoReconnect = enabled
            }
            publishConnectionStatuses()
        }
    }

    fun clearReadings() {
        _sensorReadings.value = BleSensorReadings()
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        val record = result.scanRecord
        val uuids = (record?.serviceUuids?.map { it.uuid } ?: emptyList())
            .filter { BleUuids.SUPPORTED_SERVICES.contains(it) }
            .toSet()

        val effectiveUuids = uuids.ifEmpty {
            if (cyclingSettings.bleUnfilteredScanEnabled.value) emptySet() else BleUuids.SUPPORTED_SERVICES
        }
        val discovered = DiscoveredBleDevice(
            address = address,
            name = device.name ?: record?.deviceName,
            rssi = result.rssi,
            serviceUuids = effectiveUuids
        )
        synchronized(discoveredMap) {
            discoveredMap[address] = discovered
        }
        publishScanResults()
    }

    @SuppressLint("MissingPermission")
    private fun connectGattInternal(session: DeviceSession) {
        if (!hasConnectPermission()) {
            synchronized(session) {
                session.state = BleConnectionState.DISCONNECTED
                session.lastError = "Missing BLUETOOTH_CONNECT permission"
            }
            publishConnectionStatuses()
            return
        }
        val device = session.device
        synchronized(session) {
            try { session.gatt?.close() } catch (_: Exception) {}
            session.gatt = null
            session.subscribedChars.clear()
            session.pendingSubscribeQueue.clear()
            session.subscribing = false
        }
        val gatt = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, session.callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, session.callback)
            }
        } catch (_: Exception) {
            null
        }
        synchronized(session) {
            session.gatt = gatt
            if (gatt == null) {
                session.state = BleConnectionState.DISCONNECTED
                session.lastError = "connectGatt failed"
            }
        }
        publishConnectionStatuses()
        if (gatt == null) {
            scheduleReconnectIfNeeded(session)
        }
    }

    private fun scheduleReconnectIfNeeded(session: DeviceSession) {
        synchronized(session) {
            if (!session.autoReconnect || session.userInitiatedDisconnect || !isBluetoothEnabled()) return
            val attempt = session.reconnectAttempts + 1
            if (attempt > MAX_RECONNECT_ATTEMPTS) {
                session.pendingReconnectRunnable?.let { mainHandler.removeCallbacks(it) }
                session.pendingReconnectRunnable = null
                session.state = BleConnectionState.DISCONNECTED
            } else {
                session.reconnectAttempts = attempt
                session.state = BleConnectionState.CONNECTING
                // Первая попытка — сразу, дальше по секунде, всего не больше 5.
                val delayMs = if (attempt == 1) 0L else 1000L
                val runnable = Runnable {
                    if (!session.userInitiatedDisconnect && session.autoReconnect && isBluetoothEnabled()) {
                        connectGattInternal(session)
                    } else {
                        synchronized(session) { session.state = BleConnectionState.DISCONNECTED }
                        publishConnectionStatuses()
                    }
                }
                session.pendingReconnectRunnable?.let { mainHandler.removeCallbacks(it) }
                session.pendingReconnectRunnable = runnable
                mainHandler.postDelayed(runnable, delayMs)
            }
        }
        publishConnectionStatuses()
    }

    @SuppressLint("MissingPermission")
    private fun closeSessionGatt(session: DeviceSession, fromDisconnect: Boolean) {
        val gatt = synchronized(session) {
            val g = session.gatt
            session.gatt = null
            session.pendingSubscribeQueue.clear()
            session.subscribedChars.clear()
            session.subscribing = false
            g
        }
        try {
            if (fromDisconnect && gatt != null && hasConnectPermission()) {
                gatt.disconnect()
            }
        } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}

        synchronized(session) {
            if (session.userInitiatedDisconnect) {
                session.state = BleConnectionState.DISCONNECTED
            }
        }
        if (fromDisconnect) {
            recomputeReadingsFromSessions()
        }
        publishConnectionStatuses()
    }

    private fun publishScanResults() {
        val list = synchronized(discoveredMap) {
            discoveredMap.values.sortedByDescending { it.rssi }
        }
        _scanResults.value = list
    }

    private fun publishConnectionStatuses() {
        val list = sessions.values.map { s ->
            synchronized(s) {
                BleConnectionStatus(
                    address = s.address,
                    name = s.name ?: s.device.name,
                    sensorTypes = s.sensorTypes.toSet(),
                    state = s.state,
                    autoReconnect = s.autoReconnect,
                    lastError = s.lastError,
                    batteryPercent = s.batteryPercent
                )
            }
        }.sortedBy { it.address }
        _connectionStatuses.value = list
    }

    private fun recomputeReadingsFromSessions() {
        var hr: Int? = null
        var cadence: Float? = null
        var speed: Float? = null
        var power: Int? = null
        var stepCadence: Int? = null
        var rscRunning: Boolean? = null
        val now = System.currentTimeMillis()
        sessions.values.forEach { s ->
            synchronized(s) {
                if (s.state != BleConnectionState.CONNECTED) return@forEach
                s.lastHr?.let { if (now - it.second <= VALUE_STALE_MS) hr = it.first }
                s.lastCadence?.let { if (now - it.second <= VALUE_STALE_MS) cadence = it.first }
                s.lastSpeedKmh?.let { if (now - it.second <= VALUE_STALE_MS) speed = it.first }
                s.lastPower?.let { if (now - it.second <= VALUE_STALE_MS) power = it.first }
                s.lastStepCadence?.let { if (now - it.second <= VALUE_STALE_MS) stepCadence = it.first }
                if (s.lastRscRunning != null && s.lastRscRunningAtMs > 0L && now - s.lastRscRunningAtMs <= VALUE_STALE_MS) {
                    rscRunning = s.lastRscRunning
                }
            }
        }
        _sensorReadings.value = BleSensorReadings(
            heartRateBpm = hr,
            cadenceRpm = cadence,
            sensorSpeedKmh = speed,
            powerWatts = power,
            stepCadenceSpm = stepCadence,
            rscIsRunning = rscRunning
        )
    }

    private fun onMeasurementReceived(address: String) {
        recomputeReadingsFromSessions()
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun persistKnownSensor(session: DeviceSession, connectedNow: Boolean) {
        try {
            val snap = synchronized(session) {
                Quint(session.address, session.name ?: session.device.name, session.sensorTypes.map { it.name }, session.autoReconnect, session.state)
            }
            val (address, name, types, autoReconnect, state) = snap
            // Persist when user opted for auto-reconnect or device is/was connected; keep remembered devices manageable.
            if (!autoReconnect && state != BleConnectionState.CONNECTED && state != BleConnectionState.CONNECTING) return
            val existing = cyclingSettings.getKnownSensor(address)
            cyclingSettings.upsertKnownSensor(
                com.example.bike_.data.settings.KnownSensorPref(
                    id = address,
                    address = address,
                    name = name ?: existing?.name,
                    sensorTypes = if (types.isNotEmpty()) types else existing?.sensorTypes.orEmpty(),
                    autoConnect = autoReconnect,
                    lastConnectedMs = if (connectedNow) System.currentTimeMillis() else (existing?.lastConnectedMs ?: 0L)
                )
            )
        } catch (_: Exception) {
        }
    }

    private data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    private inner class DeviceSession(
        val address: String,
        val device: BluetoothDevice
    ) {
        @Volatile var name: String? = null
        @Volatile var gatt: BluetoothGatt? = null
        @Volatile var state: BleConnectionState = BleConnectionState.DISCONNECTED
        @Volatile var autoReconnect: Boolean = true
        @Volatile var userInitiatedDisconnect: Boolean = false
        @Volatile var lastError: String? = null
        @Volatile var reconnectAttempts: Int = 0
        var pendingReconnectRunnable: Runnable? = null

        val sensorTypes: MutableSet<BleSensorType> = mutableSetOf()
        val cscParser = CscParser()
        val pendingSubscribeQueue: ArrayDeque<BluetoothGattCharacteristic> = ArrayDeque()
        val subscribedChars: MutableSet<java.util.UUID> = mutableSetOf()
        var subscribing: Boolean = false

        var lastHr: Pair<Int, Long>? = null
        var lastCadence: Pair<Float, Long>? = null
        var lastSpeedKmh: Pair<Float, Long>? = null
        var lastPower: Pair<Int, Long>? = null
        var lastStepCadence: Pair<Int, Long>? = null
        var lastRscRunning: Boolean? = null
        var lastRscRunningAtMs: Long = 0L
        var batteryPercent: Int? = null

        // FTMS
        var ftmsControlPoint: BluetoothGattCharacteristic? = null
        var ftmsControlGranted: Boolean = false
        var ftmsLastError: String? = null
        var ftmsIndoorPower: Pair<Int, Long>? = null
        var ftmsIndoorCadence: Pair<Float, Long>? = null
        var ftmsIndoorSpeed: Pair<Float, Long>? = null
        val ftmsWriteQueue: ArrayDeque<ByteArray> = ArrayDeque()
        @Volatile var ftmsWriting: Boolean = false

        fun hasFtmsConnected(): Boolean = synchronized(this) { state == BleConnectionState.CONNECTED && sensorTypes.contains(BleSensorType.FITNESS_MACHINE) }
        fun ftmsSnapshot(): FtmsSnap = synchronized(this) {
            FtmsSnap(hasFtmsConnected(), name ?: device.name, ftmsControlGranted, ftmsLastError, ftmsIndoorPower?.first, ftmsIndoorCadence?.first, ftmsIndoorSpeed?.first)
        }

        val callback = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        synchronized(this@DeviceSession) {
                            state = BleConnectionState.CONNECTED
                            lastError = null
                            reconnectAttempts = 0
                            cscParser.reset()
                            lastStepCadence = null
                            lastRscRunning = null
                            lastRscRunningAtMs = 0L
                        }
                        persistKnownSensor(this@DeviceSession, connectedNow = true)
                        publishConnectionStatuses()
                        try {
                            if (hasConnectPermission()) gatt.discoverServices()
                        } catch (_: Exception) {
                            synchronized(this@DeviceSession) { lastError = "Service discovery failed" }
                            publishConnectionStatuses()
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        val wasIntentional: Boolean
                        synchronized(this@DeviceSession) {
                            wasIntentional = userInitiatedDisconnect
                            state = BleConnectionState.DISCONNECTED
                            if (status != BluetoothGatt.GATT_SUCCESS) {
                                lastError = "Disconnected (status=$status)"
                            }
                            lastHr = null; lastCadence = null; lastSpeedKmh = null; lastPower = null
                            lastStepCadence = null; lastRscRunning = null; lastRscRunningAtMs = 0L
                        }
                        try { gatt.close() } catch (_: Exception) {}
                        synchronized(this@DeviceSession) {
                            if (this@DeviceSession.gatt == gatt) this@DeviceSession.gatt = null
                            pendingSubscribeQueue.clear()
                            subscribedChars.clear()
                            subscribing = false
                            ftmsControlPoint = null
                            ftmsControlGranted = false
                            ftmsWriteQueue.clear()
                            ftmsWriting = false
                            ftmsIndoorPower = null; ftmsIndoorCadence = null; ftmsIndoorSpeed = null
                        }
                        persistKnownSensor(this@DeviceSession, connectedNow = false)
                        recomputeReadingsFromSessions()
                        publishConnectionStatuses()
                        publishFtmsStateFromSessions()
                        if (!wasIntentional) {
                            scheduleReconnectIfNeeded(this@DeviceSession)
                        }
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        synchronized(this@DeviceSession) { state = BleConnectionState.CONNECTING }
                        publishConnectionStatuses()
                    }
                    BluetoothProfile.STATE_DISCONNECTING -> {
                        synchronized(this@DeviceSession) { state = BleConnectionState.DISCONNECTING }
                        publishConnectionStatuses()
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    synchronized(this@DeviceSession) { lastError = "Service discovery status=$status" }
                    publishConnectionStatuses()
                    return
                }
                val toSubscribe = mutableListOf<BluetoothGattCharacteristic>()
                val types = mutableSetOf<BleSensorType>()
                var batteryChar: BluetoothGattCharacteristic? = null
                gatt.services.forEach { service ->
                    when (service.uuid) {
                        BleUuids.HEART_RATE_SERVICE -> {
                            service.getCharacteristic(BleUuids.HEART_RATE_MEASUREMENT)?.let {
                                if (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                                    toSubscribe += it
                                    types += BleSensorType.HEART_RATE
                                }
                            }
                        }
                        BleUuids.CYCLING_SPEED_AND_CADENCE_SERVICE -> {
                            service.getCharacteristic(BleUuids.CSC_MEASUREMENT)?.let {
                                if (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                                    toSubscribe += it
                                    types += BleSensorType.CYCLING_SPEED_AND_CADENCE
                                }
                            }
                        }
                        BleUuids.RUNNING_SPEED_AND_CADENCE_SERVICE -> {
                            service.getCharacteristic(BleUuids.RSC_MEASUREMENT)?.let {
                                if (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                                    toSubscribe += it
                                    types += BleSensorType.RUNNING_SPEED_AND_CADENCE
                                }
                            }
                        }
                        BleUuids.CYCLING_POWER_SERVICE -> {
                            service.getCharacteristic(BleUuids.CYCLING_POWER_MEASUREMENT)?.let {
                                if (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                                    toSubscribe += it
                                    types += BleSensorType.CYCLING_POWER
                                }
                            }
                        }
                        BleUuids.BATTERY_SERVICE -> {
                            batteryChar = service.getCharacteristic(BleUuids.BATTERY_LEVEL)
                        }
                        BleUuids.FITNESS_MACHINE_SERVICE -> {
                            service.getCharacteristic(BleUuids.INDOOR_BIKE_DATA)?.let {
                                if (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                                    toSubscribe += it
                                    types += BleSensorType.FITNESS_MACHINE
                                }
                            }
                            val cp = service.getCharacteristic(BleUuids.FITNESS_MACHINE_CONTROL_POINT)
                            if (cp != null && (cp.properties and (BluetoothGattCharacteristic.PROPERTY_INDICATE or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                                toSubscribe += cp
                                synchronized(this@DeviceSession) { ftmsControlPoint = cp }
                            }
                        }
                    }
                }
                synchronized(this@DeviceSession) {
                    sensorTypes.clear()
                    sensorTypes.addAll(types)
                    pendingSubscribeQueue.clear()
                    pendingSubscribeQueue.addAll(toSubscribe)
                    subscribing = false
                }
                persistKnownSensor(this@DeviceSession, connectedNow = false)
                publishConnectionStatuses()
                publishFtmsStateFromSessions()
                // Read battery if available, then subscribe to notifications
                val batteryReadStarted = try {
                    if (batteryChar != null && hasConnectPermission()) gatt.readCharacteristic(batteryChar) else false
                } catch (_: Exception) { false }
                if (!batteryReadStarted) {
                    subscribeNext(gatt)
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val value = characteristic.value
                    handleCharacteristicValue(characteristic.uuid, value ?: byteArrayOf())
                }
                if (characteristic.uuid == BleUuids.BATTERY_LEVEL) {
                    subscribeNext(gatt)
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    handleCharacteristicValue(characteristic.uuid, value)
                }
                if (characteristic.uuid == BleUuids.BATTERY_LEVEL) {
                    subscribeNext(gatt)
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                synchronized(this@DeviceSession) { subscribing = false }
                val charUuid = descriptor.characteristic?.uuid
                if (charUuid == BleUuids.FITNESS_MACHINE_CONTROL_POINT && status == BluetoothGatt.GATT_SUCCESS) {
                    // After control point indications enabled, request control then start/resume
                    mainHandler.postDelayed({
                        writeFtmsControl(FtmsParser.buildRequestControl())
                    }, 250)
                }
                subscribeNext(gatt)
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (characteristic.uuid == BleUuids.FITNESS_MACHINE_CONTROL_POINT) {
                    synchronized(this@DeviceSession) { ftmsWriting = false }
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        synchronized(this@DeviceSession) { ftmsLastError = "FTMS write status=$status" }
                        publishFtmsStateFromSessions()
                    }
                    flushFtmsWrite(gatt)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val value = characteristic.value ?: return
                handleCharacteristicValue(characteristic.uuid, value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleCharacteristicValue(characteristic.uuid, value)
            }
        }

        @SuppressLint("MissingPermission")
        fun subscribeNext(gatt: BluetoothGatt) {
            val next = synchronized(this) {
                if (subscribing) return
                val c = if (pendingSubscribeQueue.isNotEmpty()) pendingSubscribeQueue.removeFirst() else null
                if (c != null) subscribing = true
                c
            } ?: return

            if (!hasConnectPermission()) {
                synchronized(this) { subscribing = false }
                return
            }
            val enabled = try { gatt.setCharacteristicNotification(next, true) } catch (_: Exception) { false }
            if (!enabled) {
                synchronized(this) { subscribing = false }
                subscribeNext(gatt)
                return
            }
            val cccd = next.getDescriptor(BleUuids.CLIENT_CHARACTERISTIC_CONFIG)
            if (cccd == null) {
                synchronized(this) { subscribing = false }
                subscribeNext(gatt)
                return
            }
            val isIndicate = next.uuid == BleUuids.FITNESS_MACHINE_CONTROL_POINT ||
                (next.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 && next.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0)
            val cccdValue = if (isIndicate) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val writeInitiated = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, cccdValue) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        cccd.value = cccdValue
                        gatt.writeDescriptor(cccd)
                    }
                }
            } catch (_: Exception) { false }
            if (!writeInitiated) {
                synchronized(this) { subscribing = false }
                subscribeNext(gatt)
            }
        }

        fun handleCharacteristicValue(charUuid: java.util.UUID, value: ByteArray) {
            val now = System.currentTimeMillis()
            when (charUuid) {
                BleUuids.HEART_RATE_MEASUREMENT -> {
                    hrParser.parse(value)?.let { bpm ->
                        synchronized(this) { lastHr = bpm to now }
                        onMeasurementReceived(address)
                    }
                }
                BleUuids.CSC_MEASUREMENT -> {
                    val circumference = cyclingSettings.wheelCircumferenceMm.value
                    val m = cscParser.parse(value, circumference)
                    if (m != null) {
                        synchronized(this) {
                            m.cadenceRpm?.let { lastCadence = it to now }
                            m.speedKmh?.let { lastSpeedKmh = it to now }
                        }
                        if (m.cadenceRpm != null || m.speedKmh != null) {
                            onMeasurementReceived(address)
                        }
                    }
                }
                BleUuids.RSC_MEASUREMENT -> {
                    RscParser.parse(value)?.let { m ->
                        synchronized(this) {
                            lastSpeedKmh = m.speedKmh to now
                            lastStepCadence = m.cadenceSpm to now
                            lastRscRunning = m.isRunning
                            lastRscRunningAtMs = now
                        }
                        onMeasurementReceived(address)
                    }
                }
                BleUuids.CYCLING_POWER_MEASUREMENT -> {
                    powerParser.parse(value)?.let { watts ->
                        synchronized(this) { lastPower = watts to now }
                        onMeasurementReceived(address)
                    }
                }
                BleUuids.BATTERY_LEVEL -> {
                    if (value.isNotEmpty()) {
                        val level = value[0].toInt() and 0xFF
                        if (level in 0..100) {
                            synchronized(this) { batteryPercent = level }
                            publishConnectionStatuses()
                        }
                    }
                }
                BleUuids.INDOOR_BIKE_DATA -> {
                    FtmsParser.parseIndoorBikeData(value)?.let { d ->
                        synchronized(this) {
                            d.powerWatts?.let { ftmsIndoorPower = it to now; lastPower = it to now }
                            d.cadenceRpm?.let { ftmsIndoorCadence = it to now; lastCadence = it to now }
                            d.speedKmh?.let { ftmsIndoorSpeed = it to now; lastSpeedKmh = it to now }
                        }
                        if (d.powerWatts != null || d.cadenceRpm != null || d.speedKmh != null) onMeasurementReceived(address)
                        publishFtmsStateFromSessions()
                    }
                }
                BleUuids.FITNESS_MACHINE_CONTROL_POINT -> {
                    val resp = FtmsParser.parseControlPointResponse(value)
                    if (resp != null) {
                        val ok = FtmsParser.isSuccess(resp)
                        synchronized(this) {
                            if (ok) {
                                ftmsLastError = null
                                if (resp.requestOpCode == 0x00) {
                                    ftmsControlGranted = true
                                    // Auto-start once control granted
                                    mainHandler.post { writeFtmsControl(FtmsParser.buildStartOrResume()) }
                                }
                            } else {
                                ftmsLastError = "FTMS err req=0x${resp.requestOpCode.toString(16)} res=0x${resp.resultCode.toString(16)}"
                                if (resp.requestOpCode == 0x00) ftmsControlGranted = false
                            }
                        }
                        publishFtmsStateFromSessions()
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        fun writeFtmsControl(payload: ByteArray) {
            val g = gatt ?: return
            if (!hasConnectPermission()) return
            synchronized(this) { ftmsWriteQueue.addLast(payload) }
            flushFtmsWrite(g)
        }

        @SuppressLint("MissingPermission")
        fun flushFtmsWrite(gatt: BluetoothGatt) {
            val cp = synchronized(this) { ftmsControlPoint } ?: return
            val payload = synchronized(this) {
                if (ftmsWriting) return
                val next = if (ftmsWriteQueue.isNotEmpty()) ftmsWriteQueue.removeFirst() else return
                ftmsWriting = true
                next
            }
            try {
                val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(cp, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        cp.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        cp.value = payload
                        gatt.writeCharacteristic(cp)
                    }
                }
                if (!started) {
                    synchronized(this) { ftmsWriting = false; ftmsLastError = "FTMS write failed" }
                    publishFtmsStateFromSessions()
                }
            } catch (_: Exception) {
                synchronized(this) { ftmsWriting = false; ftmsLastError = "FTMS write exception" }
                publishFtmsStateFromSessions()
            }
        }
    }

    private data class FtmsSnap(val connected: Boolean, val name: String?, val controlGranted: Boolean, val lastError: String?, val indoorPower: Int?, val indoorCadence: Float?, val indoorSpeed: Float?)

    companion object {
        private const val VALUE_STALE_MS = 5000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }
}
