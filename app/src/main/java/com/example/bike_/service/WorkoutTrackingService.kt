package com.example.bike_.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.bike_.MainActivity
import com.example.bike_.BuildConfig
import com.example.bike_.R
import com.example.bike_.data.ble.model.BleSensorReadings
import com.example.bike_.data.repository.WorkoutRepository
import com.example.bike_.data.sensors.UnifiedSensorsManager
import com.example.bike_.data.settings.CyclingSettings
import com.example.bike_.data.tracking.RouteStateHolder
import com.example.bike_.data.tracking.WorkoutStateHolder
import com.example.bike_.domain.model.LapMetrics
import com.example.bike_.domain.model.TrackLatLng
import com.example.bike_.domain.model.SpeedLimits
import com.example.bike_.domain.model.WorkoutMetrics
import com.example.bike_.data.db.entity.LapEntity
import com.example.bike_.domain.util.RouteNavigation
import com.example.bike_.domain.analytics.TrainingAnalytics
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

class WorkoutTrackingService : Service() {

    private lateinit var stateHolder: WorkoutStateHolder
    private lateinit var routeStateHolder: RouteStateHolder
    private lateinit var sensorsManager: UnifiedSensorsManager
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var cyclingSettings: com.example.bike_.data.settings.CyclingSettings
    private lateinit var simulatorStateHolder: com.example.bike_.data.tracking.SimulatorStateHolder

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var tickerJob: Job? = null
    private var bleCollectorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var latestBleReadings: BleSensorReadings = BleSensorReadings()

    // Tracking state (owned by service; published to stateHolder)
    private var isTracking: Boolean = false
    private var isAutoPaused: Boolean = false
    @Volatile private var userPaused: Boolean = false
    @Volatile private var speedPaused: Boolean = false
    private var elapsedTimeMs: Long = 0L
    private var movingTimeMs: Long = 0L
    private var distanceMeters: Double = 0.0
    private var currentSpeedKmh: Float = 0f
    private var maxSpeedKmh: Float = 0f
    private var elevationGainMeters: Double = 0.0
    private var lastAcceptedLocation: Location? = null
    private var lastAltitudeM: Double? = null
    private var lastGoodGpsElapsedMs: Long = 0L
    private var lastSensorDistanceTickElapsedMs: Long = 0L
    private var lastGpsSpeedKmh: Float = 0f

    // Live route & laps (for UI)
    private val liveTrackPoints = ArrayList<TrackLatLng>()
    private val laps = ArrayList<LapMetrics>()
    private var lapStartElapsedMs: Long = 0L
    private var lapStartDistanceMeters: Double = 0.0
    private var lapStartEpochMs: Long = 0L
    private var lapHrSum: Long = 0L
    private var lapHrCount: Int = 0
    private var lapHrMax: Int = 0
    private var lapPowerSum: Long = 0L
    private var lapPowerCount: Int = 0
    private val lapPowerSamples = ArrayList<Int>(256)
    private var lastCompletedLap: LapMetrics? = null
    private var lastCompletedLapAtMs: Long = 0L
    private var autoLapNextDistanceMeters: Double = 0.0

    // Structured workout player
    private var structuredWorkout: com.example.bike_.domain.workout.StructuredWorkout? = null
    private var structuredWorkoutElapsedSec: Int = 0
    private var structuredWorkoutStepIndex: Int = 0
    private var structuredWorkoutBeepedStep: Int = -1
    private val structuredPrefs by lazy { getSharedPreferences("structured_runtime", Context.MODE_PRIVATE) }

    // FTMS ERG
    private var ftmsMode: com.example.bike_.data.ble.model.FtmsTrainerMode = com.example.bike_.data.ble.model.FtmsTrainerMode.ERG
    private var ftmsTargetPowerWatts: Int? = null
    private var ftmsTargetResistancePercent: Int = 20
    private var ftmsErgOffsetWatts: Int = 0
    private var ftmsLastAppliedStep: Int = -1
    private var ftmsLastAppliedPower: Int? = null

    // Persistence
    @Volatile private var currentWorkoutId: Long? = null
    @Volatile private var workoutStartEpochMs: Long = 0L
    @Volatile private var workoutType: String = DEFAULT_WORKOUT_TYPE
    @Volatile private var isStopping: Boolean = false
    private var startDbJob: Job? = null
    private var locationUpdatesPausedMode: Boolean = false

    // Route navigation
    private var offRouteMeters: Float? = null
    private var isOffRoute: Boolean = false
    private var routeRemainingDistanceMeters: Double? = null
    private var routeRemainingElevationGainMeters: Double? = null
    private var routeProgressDistanceMeters: Double? = null
    private var routeBearingToReturnDeg: Float? = null
    @Volatile private var lastOffRouteNotifyAtMs: Long = 0L

    // Pedometer (walking/running)
    private var stepSensorManager: SensorManager? = null
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var stepListener: SensorEventListener? = null
    private var stepCounterBaseline: Float? = null
    private var stepDetectorTotal: Int = 0
    private var totalSteps: Int = 0
    private var stepCadenceSpm: Int = 0
    private var bleStepAccumulator: Float = 0f
    private var lastStepAtElapsedMs: Long = 0L
    private var stepDistanceMeters: Double = 0.0
    private var lastStepDistanceTickElapsedMs: Long = 0L
    private val stepCadenceWindow = ArrayDeque<Long>()

    // Advanced analytics collection (Stage 9)
    private val powerSamplesWatts = ArrayList<Int>(3600)
    private val hrZoneSeconds = IntArray(5)
    private val powerZoneSeconds = IntArray(7)
    private val powerWindow3s = ArrayDeque<Int>()
    private var smoothedPower3sWatts: Int? = null
    private var hrSum: Long = 0L
    private var hrCount: Int = 0
    private var powerSum: Long = 0L
    private var powerCount: Int = 0
    private var maxPowerWattsLive: Int = 0

    private fun isFootWorkout(): Boolean =
        workoutType == WORKOUT_TYPE_WALKING || workoutType == WORKOUT_TYPE_RUNNING

    // Auto-pause is intentionally limited to cycling. At walking/running pace GPS speed is
    // too noisy, and the pedometer already tells a real stop from slow movement.
    private fun autoPauseAllowed(): Boolean =
        !isFootWorkout() && (if (::cyclingSettings.isInitialized) cyclingSettings.autoPauseEnabled.value else true)

    private fun simulatorActive(): Boolean =
        BuildConfig.ENABLE_TEST_TOOLS && ::simulatorStateHolder.isInitialized && simulatorStateHolder.state.value.enabled

    // A single noisy GPS fix must not be stored as an impossible running/walking speed.
    private fun maxPlausibleSpeedKmh(): Float = SpeedLimits.forWorkoutType(workoutType)

    private fun strideMeters(): Double {
        val cm = if (workoutType == WORKOUT_TYPE_RUNNING) cyclingSettings.runningStrideCm.value else cyclingSettings.walkingStrideCm.value
        return (cm.coerceIn(CyclingSettings.MIN_STRIDE_CM, CyclingSettings.MAX_STRIDE_CM) / 100.0)
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as com.example.bike_.BikeApplication
        stateHolder = app.workoutStateHolder
        routeStateHolder = app.routeStateHolder
        sensorsManager = app.sensorsManager
        workoutRepository = app.workoutRepository
        cyclingSettings = app.cyclingSettings
        simulatorStateHolder = app.simulatorStateHolder
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        bleCollectorJob = serviceScope.launch {
            sensorsManager.sensorReadings.collect { readings ->
                latestBleReadings = readings
                withContext(Dispatchers.Main) {
                    if (isTracking) {
                        publishState()
                        updateNotification(currentMetrics())
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val continueId = intent.getLongExtra(EXTRA_CONTINUE_WORKOUT_ID, -1L).takeIf { it > 0 }
                val type = intent.getStringExtra(EXTRA_WORKOUT_TYPE) ?: DEFAULT_WORKOUT_TYPE
                val structuredId = intent.getLongExtra(EXTRA_STRUCTURED_WORKOUT_ID, -1L).takeIf { it > 0 }
                startWorkout(type, continueId, structuredId)
            }
            ACTION_STOP -> stopWorkout()
            ACTION_PAUSE -> pauseWorkoutManual()
            ACTION_RESUME -> resumeWorkoutManual()
            ACTION_LAP -> markLap()
            ACTION_SKIP_STEP -> skipStructuredStep()
            ACTION_REPEAT_STEP -> repeatStructuredStep()
            ACTION_FTMS_SET_MODE -> {
                val mode = intent.getStringExtra(EXTRA_FTMS_MODE)
                ftmsMode = if (mode == "RESISTANCE") com.example.bike_.data.ble.model.FtmsTrainerMode.RESISTANCE else com.example.bike_.data.ble.model.FtmsTrainerMode.ERG
                sensorsManager.setFtmsMode(ftmsMode)
                applyFtmsForCurrentState(force = true)
                publishState()
            }
            ACTION_FTMS_ADJUST -> {
                val delta = intent.getIntExtra(EXTRA_FTMS_DELTA, 0)
                if (ftmsMode == com.example.bike_.data.ble.model.FtmsTrainerMode.ERG) {
                    ftmsErgOffsetWatts = (ftmsErgOffsetWatts + delta).coerceIn(-200, 200)
                } else {
                    ftmsTargetResistancePercent = (ftmsTargetResistancePercent + delta).coerceIn(0, 100)
                }
                applyFtmsForCurrentState(force = true)
                publishState()
            }
            else -> {
                // If system restarts service without explicit action, stop safely
                if (!isTracking) stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopStepUpdates()
        stopLocationUpdates()
        stopTicker()
        bleCollectorJob?.cancel()
        bleCollectorJob = null
        startDbJob?.cancel()
        startDbJob = null
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startWorkout(requestedType: String, continueWorkoutId: Long? = null, structuredWorkoutId: Long? = null) {
        if (isTracking) {
            updateNotification(currentMetrics())
            return
        }
        if (!hasLocationPermission()) {
            stopSelf()
            return
        }

        if (continueWorkoutId != null) {
            continueExistingWorkout(continueWorkoutId)
            return
        }

        resetInternalState()
        workoutType = requestedType
        workoutStartEpochMs = System.currentTimeMillis()
        isTracking = true
        userPaused = false
        speedPaused = false
        recomputePausedState()
        isStopping = false

        structuredWorkout = structuredWorkoutId?.let { (application as? com.example.bike_.BikeApplication)?.structuredWorkoutStore?.getById(it) }
        stateHolder.activeStructuredWorkoutId = structuredWorkoutId
        structuredWorkoutElapsedSec = 0
        structuredWorkoutStepIndex = 0
        structuredWorkoutBeepedStep = -1
        persistStructuredRuntime()

        lapStartEpochMs = workoutStartEpochMs
        resetLapAccumulators()
        val autoLapM = cyclingSettings.autoLapDistanceMeters.value
        autoLapNextDistanceMeters = if (autoLapM > 0) autoLapM.toDouble() else 0.0
        startDbJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val id = workoutRepository.startWorkout(workoutType = workoutType, startTimeMs = workoutStartEpochMs)
                currentWorkoutId = id
            } catch (_: Exception) {
                // If DB start fails, keep live tracking running; stop will try to finalize if id exists
            }
        }

        createNotificationChannel()
        startForegroundWithNotification(currentMetrics())
        acquireWakeLock()
        startStepUpdatesIfNeeded()
        publishState()
        startLocationUpdates()
        startTicker()
    }

    private fun continueExistingWorkout(workoutId: Long) {
        resetInternalState()
        // Mark tracking early so UI/notification reflect active session while restoring
        isTracking = true
        userPaused = false
        speedPaused = false
        recomputePausedState()
        isStopping = false
        currentWorkoutId = workoutId
        createNotificationChannel()
        startForegroundWithNotification(currentMetrics())
        acquireWakeLock()
        publishState()

        startDbJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val workout = workoutRepository.getWorkout(workoutId)
                if (workout == null || workout.isFinished) {
                    withContext(Dispatchers.Main) { abortStartWithoutSave() }
                    return@launch
                }
                val points = workoutRepository.getTrackPoints(workoutId)
                val summary = workoutRepository.computeSummaryFromPoints(workoutId)
                val attached = workoutRepository.attachToWorkout(workoutId)
                if (!attached) {
                    withContext(Dispatchers.Main) { abortStartWithoutSave() }
                    return@launch
                }
                val lastPoint = points.lastOrNull()
                withContext(Dispatchers.Main) {
                    if (!isTracking) return@withContext
                    workoutType = workout.workoutType
                    workoutStartEpochMs = workout.startTimeMs
                    currentWorkoutId = workoutId
                    val now = System.currentTimeMillis()
                    elapsedTimeMs = max(summary.elapsedTimeMs, (now - workout.startTimeMs).coerceAtLeast(0L))
                    movingTimeMs = summary.movingTimeMs.coerceAtMost(elapsedTimeMs)
                    distanceMeters = summary.distanceMeters
                    maxSpeedKmh = summary.maxSpeedKmh
                    elevationGainMeters = summary.elevationGainMeters
                    totalSteps = summary.totalSteps ?: points.lastOrNull()?.stepsTotal ?: 0
                    stepDetectorTotal = totalSteps
                    stepCounterBaseline = null
                    stepCadenceSpm = 0
                    stepCadenceWindow.clear()
                    lastStepAtElapsedMs = 0L
                    stepDistanceMeters = distanceMeters
                    // restore baseline from last known point
                    lastAcceptedLocation = lastPoint?.let { trackPointToLocation(it) }
                    lastAltitudeM = lastPoint?.altitudeMeters
                    lastGpsSpeedKmh = 0f
                    restoreStructuredRuntime(workoutId)
                    // rebuild live track for map (sampled)
                    liveTrackPoints.clear()
                    if (points.isNotEmpty()) {
                        val step = max(1, points.size / MAX_RESTORE_TRACK_POINTS)
                        points.forEachIndexed { idx, p ->
                            if (idx % step == 0 || idx == points.lastIndex) {
                                liveTrackPoints.add(TrackLatLng(p.latitude, p.longitude, p.altitudeMeters, p.timestampMs))
                            }
                        }
                    }
                    // continue laps from current totals
                    laps.clear()
                    lapStartElapsedMs = elapsedTimeMs
                    lapStartDistanceMeters = distanceMeters
                    lapStartEpochMs = lastPoint?.timestampMs ?: now
                    resetLapAccumulators()
                    val autoLapM = cyclingSettings.autoLapDistanceMeters.value
                    autoLapNextDistanceMeters = if (autoLapM > 0) {
                        (floor(distanceMeters / autoLapM.toDouble()) + 1.0) * autoLapM.toDouble()
                    } else 0.0
                    lastCompletedLap = null
                    lastCompletedLapAtMs = 0L
                    userPaused = false
                    speedPaused = false
                    recomputePausedState()
                    currentSpeedKmh = 0f
                    startStepUpdatesIfNeeded()
                    publishState()
                    updateNotification(currentMetrics())
                    startLocationUpdates()
                    startTicker()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { abortStartWithoutSave() }
            }
        }
    }

    private fun abortStartWithoutSave() {
        if (!isTracking) return
        isTracking = false
        stopStepUpdates()
        stopLocationUpdates()
        stopTicker()
        releaseWakeLock()
        currentWorkoutId = null
        clearStructuredRuntime()
        structuredWorkout = null
        stateHolder.activeStructuredWorkoutId = null
        stateHolder.reset()
        stopForegroundSafely()
        stopSelf()
    }

    private fun persistStructuredRuntime() {
        val workout = structuredWorkout ?: return
        val workoutId = currentWorkoutId ?: return
        structuredPrefs.edit()
            .putLong("workoutId", workoutId)
            .putLong("structuredId", workout.id)
            .putInt("elapsedSec", structuredWorkoutElapsedSec)
            .apply()
    }

    private fun restoreStructuredRuntime(workoutId: Long) {
        val savedWorkoutId = structuredPrefs.getLong("workoutId", -1L)
        val structuredId = structuredPrefs.getLong("structuredId", -1L)
        if (savedWorkoutId != workoutId || structuredId <= 0L) return
        val workout = (application as? com.example.bike_.BikeApplication)?.structuredWorkoutStore?.getById(structuredId) ?: return
        structuredWorkout = workout
        stateHolder.activeStructuredWorkoutId = structuredId
        structuredWorkoutElapsedSec = structuredPrefs.getInt("elapsedSec", 0).coerceAtLeast(0)
        structuredWorkoutStepIndex = 0
        structuredWorkoutBeepedStep = -1
    }

    private fun clearStructuredRuntime() {
        if (structuredPrefs.all.isNotEmpty()) structuredPrefs.edit().clear().apply()
    }

    private fun stopWorkout() {
        if (isStopping) return
        if (!isTracking && stateHolder.metrics.value == WorkoutMetrics() && currentWorkoutId == null) {
            stopForegroundSafely()
            stopSelf()
            return
        }
        isStopping = true
        isTracking = false
        stopStepUpdates()
        stopLocationUpdates()
        stopTicker()
        releaseWakeLock()

        val metricsAtStop = currentMetrics()
        val speedCapKmh = if (simulatorActive()) SpeedLimits.DEFAULT_KMH else maxPlausibleSpeedKmh()
        val finalMetrics = metricsAtStop.copy(
            isTracking = false,
            isAutoPaused = false,
            currentSpeedKmh = 0f,
            maxSpeedKmh = metricsAtStop.maxSpeedKmh.coerceIn(0f, speedCapKmh),
        )
        stateHolder.update(finalMetrics)

        serviceScope.launch(Dispatchers.IO) {
            try {
                startDbJob?.join()
                val id = currentWorkoutId
                if (id != null) {
                    workoutRepository.flushPendingPoints()
                    workoutRepository.finishWorkout(
                        endTimeMs = System.currentTimeMillis(),
                        elapsedTimeMs = finalMetrics.elapsedTimeMs,
                        movingTimeMs = finalMetrics.movingTimeMs,
                        distanceMeters = finalMetrics.distanceMeters,
                        avgSpeedKmh = finalMetrics.averageSpeedKmh,
                        maxSpeedKmh = finalMetrics.maxSpeedKmh,
                        elevationGainMeters = finalMetrics.elevationGainMeters,
                        totalSteps = if (isFootWorkout()) finalMetrics.steps else null,
                        caloriesKcal = finalMetrics.caloriesKcal,
                        caloriesSource = finalMetrics.caloriesSource,
                        avgPowerWatts = finalMetrics.avgPowerWatts,
                        maxPowerWatts = finalMetrics.maxPowerWatts,
                        normalizedPowerWatts = finalMetrics.normalizedPowerWatts,
                        intensityFactor = finalMetrics.intensityFactor,
                        trainingStressScore = finalMetrics.trainingStressScore,
                        hrZoneSeconds = finalMetrics.hrZoneSeconds.joinToString(","),
                        powerZoneSeconds = finalMetrics.powerZoneSeconds.joinToString(","),
                        laps = buildLapEntities(id, System.currentTimeMillis())
                    )
                    val app = application as? com.example.bike_.BikeApplication
                    if (app?.cyclingSettings?.healthConnectEnabled?.value == true) {
                        try {
                            app.healthConnectManager.exportWorkout(id)
                        } catch (_: Exception) {
                            // Health Connect must never prevent a workout from being finalized.
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                currentWorkoutId = null
                workoutStartEpochMs = 0L
                withContext(Dispatchers.Main) {
                    clearStructuredRuntime()
                    structuredWorkout = null
                    stateHolder.activeStructuredWorkoutId = null
                    stateHolder.reset()
                    stopForegroundSafely()
                    stopSelf()
                    isStopping = false
                }
            }
        }
    }

    private fun pauseWorkoutManual() {
        if (!isTracking) return
        // Manual pause: stop accumulating moving time and freeze motion metrics
        userPaused = true
        recomputePausedState()
        currentSpeedKmh = 0f
        updateLocationRequestModeIfNeeded()
        publishState()
        updateNotification(currentMetrics())
    }

    private fun resumeWorkoutManual() {
        if (!isTracking) return
        userPaused = false
        speedPaused = false
        recomputePausedState()
        updateLocationRequestModeIfNeeded()
        publishState()
        updateNotification(currentMetrics())
    }

    private fun resetInternalState() {
        isTracking = false
        userPaused = false
        speedPaused = false
        isAutoPaused = false
        elapsedTimeMs = 0L
        movingTimeMs = 0L
        distanceMeters = 0.0
        currentSpeedKmh = 0f
        maxSpeedKmh = 0f
        elevationGainMeters = 0.0
        lastAcceptedLocation = null
        lastAltitudeM = null
        lastGoodGpsElapsedMs = 0L
        lastSensorDistanceTickElapsedMs = 0L
        lastGpsSpeedKmh = 0f
        currentWorkoutId = null
        workoutStartEpochMs = 0L
        isStopping = false
        liveTrackPoints.clear()
        laps.clear()
        lapStartElapsedMs = 0L
        lapStartDistanceMeters = 0.0
        resetLapAccumulators()
        lastCompletedLap = null
        lastCompletedLapAtMs = 0L
        autoLapNextDistanceMeters = 0.0
        resetStepTracking()
        resetAnalyticsTracking()
        structuredWorkout = null
        stateHolder.activeStructuredWorkoutId = null
        structuredWorkoutElapsedSec = 0
        structuredWorkoutStepIndex = 0
        structuredWorkoutBeepedStep = -1
        clearStructuredRuntime()
    }

    private fun resetStepTracking() {
        stepCounterBaseline = null
        stepDetectorTotal = 0
        totalSteps = 0
        stepCadenceSpm = 0
        bleStepAccumulator = 0f
        lastStepAtElapsedMs = 0L
        stepDistanceMeters = 0.0
        lastStepDistanceTickElapsedMs = 0L
        stepCadenceWindow.clear()
    }

    private fun resetAnalyticsTracking() {
        powerSamplesWatts.clear()
        for (i in hrZoneSeconds.indices) hrZoneSeconds[i] = 0
        for (i in powerZoneSeconds.indices) powerZoneSeconds[i] = 0
        powerWindow3s.clear()
        smoothedPower3sWatts = null
        hrSum = 0L; hrCount = 0
        powerSum = 0L; powerCount = 0; maxPowerWattsLive = 0
    }

    private fun resetLapAccumulators() {
        lapStartEpochMs = if (workoutStartEpochMs > 0L) workoutStartEpochMs else System.currentTimeMillis()
        lapHrSum = 0L; lapHrCount = 0; lapHrMax = 0
        lapPowerSum = 0L; lapPowerCount = 0
        lapPowerSamples.clear()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        if (simulatorActive()) {
            stopLocationUpdates()
            return
        }
        stopLocationUpdates()

        val paused = isAutoPaused
        val interval = if (paused) LOCATION_INTERVAL_PAUSED_MS else LOCATION_INTERVAL_ACTIVE_MS
        val minInterval = if (paused) LOCATION_MIN_UPDATE_PAUSED_MS else LOCATION_MIN_UPDATE_ACTIVE_MS
        val minDist = if (paused) LOCATION_MIN_DISTANCE_PAUSED_M else LOCATION_MIN_DISTANCE_ACTIVE_M
        val priority = if (paused) Priority.PRIORITY_BALANCED_POWER_ACCURACY else Priority.PRIORITY_HIGH_ACCURACY
        val request = LocationRequest.Builder(priority, interval)
            .setMinUpdateIntervalMillis(minInterval)
            .setMinUpdateDistanceMeters(minDist)
            .setMaxUpdateDelayMillis(interval * 2)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onLocation(it) }
            }
        }
        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        locationUpdatesPausedMode = paused
    }

    private fun updateLocationRequestModeIfNeeded() {
        if (!isTracking) return
        val wantPaused = isAutoPaused
        if (locationUpdatesPausedMode != wantPaused || locationCallback == null) {
            startLocationUpdates()
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = serviceScope.launch(Dispatchers.Main) {
            while (isActive && isTracking) {
                delay(TICK_INTERVAL_MS)
                if (!isTracking) break
                elapsedTimeMs += TICK_INTERVAL_MS

                val nowElapsed = SystemClock.elapsedRealtime()
                val gpsFresh = isGpsFresh(nowElapsed)
                val sensorSpeed = currentSensorSpeedKmh()
                val foot = isFootWorkout()
                val hasStepSource = foot && (stepListener != null || totalSteps > 0 || lastStepAtElapsedMs > 0L)
                val footStepSpeed = if (foot) stepSpeedKmh(nowElapsed) else 0f
                val stepActive = if (foot) isStepActive(nowElapsed) else false
                if (foot && !userPaused && !isAutoPaused) accumulateRscStepsIfNeeded()

                val bleFallback = canUseSensorFallback(nowElapsed, sensorSpeed, gpsFresh)
                val stepFallback = foot && hasStepSource && !gpsFresh && !bleFallback

                // Collect 1Hz analytics samples only while actively recording (not auto-paused/manual paused)
                if (!isAutoPaused) {
                    collectAnalyticsSecond()
                    if (BuildConfig.ENABLE_TEST_TOOLS) applySimulatorTick()
                    tickStructuredWorkout()
                    persistStructuredRuntime()
                }

                if (bleFallback) {
                    val speed = sensorSpeed!!
                    // GPS signal lost or indoor trainer: use BLE speed sensor / smart trainer speed.
                    if (!userPaused) {
                        applyAutoPause(speed)
                    } else {
                        recomputePausedState()
                    }
                    if (!isAutoPaused) {
                        val dtSec = if (lastSensorDistanceTickElapsedMs > 0L) {
                            ((nowElapsed - lastSensorDistanceTickElapsedMs).coerceIn(200L, 3000L)) / 1000.0
                        } else {
                            TICK_INTERVAL_MS / 1000.0
                        }
                        distanceMeters += (speed / 3.6) * dtSec
                        currentSpeedKmh = speed
                        if (speed > maxSpeedKmh) maxSpeedKmh = speed
                        movingTimeMs += TICK_INTERVAL_MS
                    } else {
                        currentSpeedKmh = 0f
                    }
                    lastSensorDistanceTickElapsedMs = nowElapsed
                    lastStepDistanceTickElapsedMs = 0L
                } else if (stepFallback) {
                    val speed = footStepSpeed
                    if (!userPaused) {
                        applyAutoPause(speed)
                    } else {
                        recomputePausedState()
                    }
                    if (!isAutoPaused) {
                        val dtSec = if (lastStepDistanceTickElapsedMs > 0L) {
                            ((nowElapsed - lastStepDistanceTickElapsedMs).coerceIn(200L, 3000L)) / 1000.0
                        } else {
                            TICK_INTERVAL_MS / 1000.0
                        }
                        if (stepActive && speed > 0f) {
                            distanceMeters += (speed / 3.6) * dtSec
                            currentSpeedKmh = speed
                            if (speed > maxSpeedKmh) maxSpeedKmh = speed
                        } else {
                            currentSpeedKmh = 0f
                        }
                        movingTimeMs += TICK_INTERVAL_MS
                    } else {
                        currentSpeedKmh = 0f
                    }
                    lastStepDistanceTickElapsedMs = nowElapsed
                    lastSensorDistanceTickElapsedMs = 0L
                } else {
                    // GPS is primary while fresh (or no usable fallback).
                    lastSensorDistanceTickElapsedMs = 0L
                    lastStepDistanceTickElapsedMs = 0L
                    val pauseRefSpeed = when {
                        foot && hasStepSource && stepActive -> max(currentSpeedKmh, footStepSpeed)
                        else -> currentSpeedKmh
                    }
                    if (!userPaused) {
                        applyAutoPause(pauseRefSpeed)
                    } else {
                        recomputePausedState()
                    }
                    if (!isAutoPaused) {
                        movingTimeMs += TICK_INTERVAL_MS
                        if (foot && hasStepSource) {
                            // GPS ground speed is often reported as 0 while walking, so use
                            // cadence x stride for the displayed speed in foot workouts.
                            currentSpeedKmh = footStepSpeed
                            if (currentSpeedKmh > maxSpeedKmh) maxSpeedKmh = currentSpeedKmh
                        }
                    } else {
                        currentSpeedKmh = 0f
                    }
                }
                checkAutoLap()

                publishState()
                updateNotification(currentMetrics())
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun onLocation(location: Location) {
        if (!isTracking) return

        // 1) Filter obviously bad fixes (jumping / low-accuracy GPS)
        val accuracy = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE
        if (accuracy > MAX_ACCURACY_M) {
            // Poor GPS signal: do not trust this point for GPS metrics.
            // If signal stays lost, ticker falls back to BLE/step speed.
            if (isAutoPaused) currentSpeedKmh = 0f
            publishState()
            return
        }

        val rawSpeedMps = if (location.hasSpeed()) location.speed.coerceAtLeast(0f) else 0f
        val rawSpeedKmh = rawSpeedMps * 3.6f
        val sensorSpeed = currentSensorSpeedKmh()
        val foot = isFootWorkout()
        val hasStepSource = foot && (stepListener != null || totalSteps > 0 || lastStepAtElapsedMs > 0L)
        val stepSpeed = if (hasStepSource) stepSpeedKmh() else 0f
        val speedCapKmh = if (simulatorActive()) SpeedLimits.DEFAULT_KMH else maxPlausibleSpeedKmh()

        val previous = lastAcceptedLocation
        if (previous == null) {
            // First accepted fix: anchor position, initialize speed
            lastGoodGpsElapsedMs = SystemClock.elapsedRealtime()
            lastSensorDistanceTickElapsedMs = 0L
            lastAcceptedLocation = location
            if (location.hasAltitude()) lastAltitudeM = location.altitude
            val firstSpeed = rawSpeedKmh.coerceIn(0f, speedCapKmh)
            lastGpsSpeedKmh = firstSpeed
            val initRefSpeed = max(firstSpeed, stepSpeed)
            val initSpeed = if (sensorSpeed != null && sensorSpeed > initRefSpeed) sensorSpeed else initRefSpeed
            if (!userPaused) applyAutoPause(initSpeed) else recomputePausedState()
            currentSpeedKmh = if (isAutoPaused) 0f else initSpeed
            if (!isAutoPaused) {
                maxSpeedKmh = max(maxSpeedKmh, currentSpeedKmh)
            }
            updateOffRouteStatus(location)
            appendLiveTrackPoint(location)
            publishState()
            recordAcceptedLocation(location)
            return
        }

        val dtSec = ((location.time - previous.time).coerceAtLeast(0L)) / 1000.0
        if (dtSec <= 0.0) return

        val segmentDistanceM = previous.distanceTo(location).toDouble()
        val segmentSpeedMps = segmentDistanceM / dtSec

        // Reject implausible jumps even if reported accuracy is decent
        if (segmentSpeedMps > MAX_PLAUSIBLE_SPEED_MPS) {
            return
        }
        if (foot && !simulatorActive() && segmentSpeedMps * 3.6f > speedCapKmh) {
            return
        }

        // Determine speed: prefer sensor (trainer/BLE) when it reports higher than GPS-derived speed
        val gpsEffectiveSpeedKmh = (if (location.hasSpeed()) rawSpeedKmh else (segmentSpeedMps * 3.6).toFloat())
            .coerceIn(0f, speedCapKmh)
        lastGpsSpeedKmh = gpsEffectiveSpeedKmh
        val speedRefKmh = max(gpsEffectiveSpeedKmh, stepSpeed)
        val effectiveSpeedKmh = if (sensorSpeed != null && sensorSpeed > speedRefKmh) sensorSpeed else speedRefKmh
        if (!userPaused) applyAutoPause(effectiveSpeedKmh) else recomputePausedState()

        if (isAutoPaused) {
            // Paused: moving time is paused by ticker; avoid accumulating drift distance/elevation
            currentSpeedKmh = 0f
            lastGoodGpsElapsedMs = SystemClock.elapsedRealtime()
            lastSensorDistanceTickElapsedMs = 0L
            // Keep anchor updated to prevent a large jump when movement resumes
            lastAcceptedLocation = location
            if (location.hasAltitude()) {
                updateAltitudeBaseline(location)
            }
            updateOffRouteStatus(location)
            appendLiveTrackPoint(location)
            publishState()
            recordAcceptedLocation(location)
            return
        }

        // Accept movement point
        lastGoodGpsElapsedMs = SystemClock.elapsedRealtime()
        lastSensorDistanceTickElapsedMs = 0L
        if (segmentDistanceM >= MIN_SEGMENT_DISTANCE_M || gpsEffectiveSpeedKmh >= AUTO_RESUME_THRESHOLD_KMH) {
            distanceMeters += segmentDistanceM
            updateElevationGain(location)
        } else {
            // Very small GPS jitter; if speed comes from sensor (trainer), ticker integrates distance.
            if (location.hasAltitude()) updateAltitudeBaseline(location)
        }

        lastAcceptedLocation = location
        currentSpeedKmh = effectiveSpeedKmh.coerceAtLeast(0f)
        if (currentSpeedKmh > maxSpeedKmh) {
            maxSpeedKmh = currentSpeedKmh
        }

        updateOffRouteStatus(location)
        appendLiveTrackPoint(location)
        publishState()
        recordAcceptedLocation(location)
    }

    private fun updateOffRouteStatus(location: Location) {
        val route = try { routeStateHolder.route.value } catch (_: Exception) { null }
        if (route == null || route.points.isEmpty()) {
            offRouteMeters = null
            isOffRoute = false
            routeRemainingDistanceMeters = null
            routeRemainingElevationGainMeters = null
            routeProgressDistanceMeters = null
            routeBearingToReturnDeg = null
            return
        }
        val progress = RouteNavigation.progress(liveTrackPoints + com.example.bike_.domain.model.TrackLatLng(location.latitude, location.longitude), route)
        offRouteMeters = progress.distanceToRouteMeters
        isOffRoute = progress.isOffRoute
        routeRemainingDistanceMeters = progress.remainingDistanceMeters
        routeRemainingElevationGainMeters = progress.remainingElevationGainMeters
        routeProgressDistanceMeters = progress.progressDistanceMeters
        routeBearingToReturnDeg = progress.bearingToRouteDeg
    }

    private fun recomputePausedState() {
        isAutoPaused = userPaused || (autoPauseAllowed() && speedPaused)
    }

    private fun canUseSensorFallback(nowElapsedMs: Long, sensorSpeed: Float?, gpsFresh: Boolean): Boolean {
        if (sensorSpeed == null) return false
        val minSensorSpeed = if (autoPauseAllowed()) AUTO_RESUME_THRESHOLD_KMH else 0.2f
        if (sensorSpeed < minSensorSpeed) return false
        if (!gpsFresh) return true
        // Indoor trainer / weak GPS: sensor reports movement while GPS-derived movement is near zero
        return lastGpsSpeedKmh < AUTO_RESUME_THRESHOLD_KMH || (isAutoPaused && !userPaused)
    }

    private fun applyAutoPause(speedKmh: Float) {
        val prev = isAutoPaused
        if (autoPauseAllowed()) {
            speedPaused = when {
                speedPaused && speedKmh >= AUTO_RESUME_THRESHOLD_KMH -> false
                !speedPaused && speedKmh < AUTO_PAUSE_THRESHOLD_KMH -> true
                else -> speedPaused
            }
        } else {
            // Foot workouts or auto-pause disabled by user: keep only manual pause state
            speedPaused = false
        }
        recomputePausedState()
        if (prev != isAutoPaused) updateLocationRequestModeIfNeeded()
    }

    private fun updateElevationGain(current: Location) {
        if (!current.hasAltitude()) return
        val currentAlt = current.altitude
        val baseAlt = lastAltitudeM
        if (baseAlt == null) {
            lastAltitudeM = currentAlt
            return
        }

        val verticalAccOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && current.hasVerticalAccuracy()) {
            current.verticalAccuracyMeters <= MAX_VERTICAL_ACCURACY_M
        } else {
            true
        }
        if (!verticalAccOk) {
            // Don't trust this altitude sample for gain; keep previous baseline
            return
        }

        val delta = currentAlt - baseAlt
        when {
            delta >= MIN_ALTITUDE_GAIN_DELTA_M -> {
                elevationGainMeters += delta
                lastAltitudeM = currentAlt
            }
            delta <= -MIN_ALTITUDE_GAIN_DELTA_M -> {
                // Descending: move baseline down so future climbs count correctly
                lastAltitudeM = currentAlt
            }
            else -> {
                // Small noise: keep baseline to avoid drift accumulation
            }
        }
    }

    private fun updateAltitudeBaseline(location: Location) {
        if (!location.hasAltitude()) return
        val verticalAccOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
            location.verticalAccuracyMeters <= MAX_VERTICAL_ACCURACY_M
        } else true
        if (verticalAccOk) {
            lastAltitudeM = location.altitude
        }
    }


    private fun isGpsFresh(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        return lastGoodGpsElapsedMs > 0L && (nowElapsedMs - lastGoodGpsElapsedMs) <= GPS_LOST_TIMEOUT_MS
    }

    private fun currentSensorSpeedKmh(): Float? {
        val s = latestBleReadings.sensorSpeedKmh ?: return null
        val cap = if (simulatorActive()) SpeedLimits.DEFAULT_KMH else maxPlausibleSpeedKmh()
        return if (s.isFinite() && s >= 0f) s.coerceAtMost(cap) else null
    }

    private fun startStepUpdatesIfNeeded() {
        if (!isFootWorkout() || stepListener != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) return
        val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val counter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val detector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (counter == null && detector == null) return
        stepSensorManager = sm
        stepCounterSensor = counter
        stepDetectorSensor = detector
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!isTracking) return
                when (event.sensor.type) {
                    Sensor.TYPE_STEP_COUNTER -> {
                        val value = event.values.firstOrNull() ?: return
                        if (stepCounterBaseline == null) {
                            stepCounterBaseline = value - totalSteps.toFloat()
                        }
                        var steps = (value - (stepCounterBaseline ?: value)).toInt().coerceAtLeast(0)
                        if (steps < totalSteps) {
                            // counter reset/reboot edge case: keep accumulated steps
                            stepCounterBaseline = value - totalSteps.toFloat()
                            steps = totalSteps
                        }
                        if (steps > totalSteps) {
                            val delta = steps - totalSteps
                            totalSteps = steps
                            onStepsAdvanced(delta)
                        }
                    }
                    Sensor.TYPE_STEP_DETECTOR -> {
                        val delta = event.values.firstOrNull()?.toInt()?.coerceAtLeast(1) ?: 1
                        totalSteps += delta
                        stepDetectorTotal = totalSteps
                        onStepsAdvanced(delta)
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        stepListener = listener
        val sensor = counter ?: detector!!
        try {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        } catch (_: Exception) {
            stepListener = null
        }
    }

    private fun stopStepUpdates() {
        val sm = stepSensorManager
        val l = stepListener
        if (sm != null && l != null) {
            try { sm.unregisterListener(l) } catch (_: Exception) {}
        }
        stepListener = null
        stepSensorManager = null
        stepCounterSensor = null
        stepDetectorSensor = null
    }

    private fun onStepsAdvanced(delta: Int) {
        val now = SystemClock.elapsedRealtime()
        lastStepAtElapsedMs = now
        repeat(delta.coerceAtMost(200)) { stepCadenceWindow.addLast(now) }
        trimStepCadenceWindow(now)
        refreshStepCadence(now)
    }

    private fun trimStepCadenceWindow(now: Long) {
        while (stepCadenceWindow.isNotEmpty() && now - stepCadenceWindow.first() > STEP_CADENCE_WINDOW_MS) {
            stepCadenceWindow.removeFirst()
        }
    }

    private fun refreshStepCadence(now: Long = SystemClock.elapsedRealtime()) {
        trimStepCadenceWindow(now)
        val count = stepCadenceWindow.size
        stepCadenceSpm = if (count > 0) ((count * 60_000.0) / STEP_CADENCE_WINDOW_MS.toDouble()).roundToInt() else 0
    }

    /** A foot pod's RSC cadence is more reliable than the phone's accelerometer when connected. */
    private fun effectiveStepCadence(): Int =
        latestBleReadings.stepCadenceSpm?.takeIf { it > 0 }?.coerceIn(0, 300) ?: stepCadenceSpm

    /** When the phone has no step sensor, integrate RSC cadence into a step total. */
    private fun accumulateRscStepsIfNeeded() {
        val cadence = latestBleReadings.stepCadenceSpm?.takeIf { it > 0 } ?: return
        if (stepListener != null || totalSteps > 0) return
        bleStepAccumulator += (cadence / 60.0).toFloat() * (TICK_INTERVAL_MS / 1000.0).toFloat()
        val whole = bleStepAccumulator.toInt()
        if (whole > 0) {
            bleStepAccumulator -= whole
            totalSteps += whole
            onStepsAdvanced(whole)
        }
    }

    private fun isStepActive(now: Long = SystemClock.elapsedRealtime()): Boolean =
        lastStepAtElapsedMs > 0L && (now - lastStepAtElapsedMs) <= FOOT_STEP_ACTIVE_MS

    private fun stepSpeedKmh(now: Long = SystemClock.elapsedRealtime()): Float {
        if (!isFootWorkout()) return 0f
        refreshStepCadence(now)
        if (!isStepActive(now) || stepCadenceSpm <= 0) return 0f
        val mps = (stepCadenceSpm / 60.0) * strideMeters()
        return (mps * 3.6).toFloat().coerceIn(0f, maxPlausibleSpeedKmh())
    }

    private fun collectAnalyticsSecond() {
        val ble = latestBleReadings
        val hr = ble.heartRateBpm
        if (hr != null && hr > 0) {
            hrSum += hr
            hrCount++
            lapHrSum += hr
            lapHrCount++
            if (hr > lapHrMax) lapHrMax = hr
            val hz = TrainingAnalytics.hrZoneIndex(hr, cyclingSettings.athleteMaxHr.value, cyclingSettings.athleteRestingHr.value)
            if (hz in hrZoneSeconds.indices) hrZoneSeconds[hz]++
        }
        val p = ble.powerWatts
        if (p != null && p >= 0) {
            val pc = p.coerceAtLeast(0)
            powerSamplesWatts.add(pc)
            powerSum += pc
            powerCount++
            if (pc > maxPowerWattsLive) maxPowerWattsLive = pc
            lapPowerSum += pc
            lapPowerCount++
            lapPowerSamples.add(pc)
            powerWindow3s.addLast(pc)
            while (powerWindow3s.size > 3) powerWindow3s.removeFirst()
            smoothedPower3sWatts = if (powerWindow3s.isNotEmpty()) (powerWindow3s.sum().toFloat() / powerWindow3s.size.toFloat()).roundToInt() else null
            val pz = TrainingAnalytics.powerZoneIndex(pc, cyclingSettings.athleteFtpWatts.value)
            if (pz in powerZoneSeconds.indices) powerZoneSeconds[pz]++
        } else {
            powerWindow3s.clear()
            smoothedPower3sWatts = null
        }
    }

    private fun currentMetrics(): WorkoutMetrics {
        val avgSpeedKmh = if (movingTimeMs > 0L) {
            ((distanceMeters / (movingTimeMs / 1000.0)) * 3.6).toFloat()
        } else 0f
        val ble = latestBleReadings
        val currentLapTime = (elapsedTimeMs - lapStartElapsedMs).coerceAtLeast(0L)
        val currentLapDist = (distanceMeters - lapStartDistanceMeters).coerceAtLeast(0.0)
        val avgHr = if (hrCount > 0) (hrSum.toDouble() / hrCount.toDouble()).roundToInt() else null
        val avgPower = if (powerCount > 0) (powerSum.toDouble() / powerCount.toDouble()).roundToInt() else null
        val np = TrainingAnalytics.normalizedPower(powerSamplesWatts.toIntArray())
        val ftp = cyclingSettings.athleteFtpWatts.value
        val iff = TrainingAnalytics.intensityFactor(np, ftp)
        val tssPower = TrainingAnalytics.tss(movingTimeMs.coerceAtLeast(0L).div(1000L).toInt(), np, iff, ftp)
        val tssHr = TrainingAnalytics.hrTss(hrZoneSeconds)
        val tssFinal = tssPower ?: tssHr
        val durationSec = movingTimeMs.coerceAtLeast(0L).div(1000L).toInt()
        val caloriesPower = TrainingAnalytics.caloriesFromPower(avgPower, durationSec)
        val caloriesHr = TrainingAnalytics.caloriesFromHr(
            avgHr = avgHr,
            weightKg = cyclingSettings.athleteWeightKg.value,
            ageYears = cyclingSettings.athleteAgeYears.value,
            isMale = cyclingSettings.athleteIsMale.value,
            durationSec = durationSec
        )
        val (calories, caloriesSource) = when {
            caloriesPower != null -> caloriesPower to "POWER"
            caloriesHr != null -> caloriesHr to "HR"
            else -> 0 to null
        }
        val hrZoneIdx = ble.heartRateBpm?.let { TrainingAnalytics.hrZoneIndex(it, cyclingSettings.athleteMaxHr.value, cyclingSettings.athleteRestingHr.value) }
        val pwrZoneIdx = (smoothedPower3sWatts ?: ble.powerWatts)?.let { TrainingAnalytics.powerZoneIndex(it, ftp) }

        val ftms = sensorsManager.ftmsState.value
        return WorkoutMetrics(
            isTracking = isTracking,
            isAutoPaused = isAutoPaused && isTracking,
            elapsedTimeMs = elapsedTimeMs,
            movingTimeMs = movingTimeMs,
            distanceMeters = distanceMeters,
            currentSpeedKmh = if (isTracking && !isAutoPaused) currentSpeedKmh else 0f,
            averageSpeedKmh = avgSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            elevationGainMeters = elevationGainMeters,
            heartRateBpm = ble.heartRateBpm,
            cadenceRpm = ble.cadenceRpm,
            powerWatts = ble.powerWatts,
            sensorSpeedKmh = ble.sensorSpeedKmh,
            steps = totalSteps,
            stepCadenceSpm = effectiveStepCadence(),
            trackPoints = ArrayList(liveTrackPoints),
            laps = ArrayList(laps),
            currentLapTimeMs = currentLapTime,
            currentLapDistanceMeters = currentLapDist,
            lastCompletedLap = lastCompletedLap,
            lastCompletedLapAtMs = lastCompletedLapAtMs,
            offRouteMeters = offRouteMeters,
            isOffRoute = isOffRoute,
            routeRemainingDistanceMeters = routeRemainingDistanceMeters,
            routeRemainingElevationGainMeters = routeRemainingElevationGainMeters,
            routeProgressDistanceMeters = routeProgressDistanceMeters,
            routeBearingToReturnDeg = routeBearingToReturnDeg,
            smoothedPower3sWatts = smoothedPower3sWatts,
            avgPowerWatts = avgPower,
            maxPowerWatts = maxPowerWattsLive.takeIf { it > 0 },
            currentHrZone = hrZoneIdx?.plus(1),
            currentPowerZone = pwrZoneIdx?.plus(1),
            caloriesKcal = calories,
            caloriesSource = caloriesSource,
            normalizedPowerWatts = np,
            intensityFactor = iff,
            trainingStressScore = tssFinal,
            hrZoneSeconds = hrZoneSeconds.copyOf(),
            powerZoneSeconds = powerZoneSeconds.copyOf(),
            structuredWorkout = structuredWorkoutUiState(),
            ftmsConnected = ftms.connected,
            ftmsDeviceName = ftms.deviceName,
            ftmsModeErg = ftms.mode == com.example.bike_.data.ble.model.FtmsTrainerMode.ERG,
            ftmsControlGranted = ftms.controlGranted,
            ftmsTargetPowerWatts = ftms.targetPowerWatts ?: ftmsTargetPowerWatts,
            ftmsTargetResistancePercent = ftms.targetResistancePercent ?: ftmsTargetResistancePercent,
            ftmsLastError = ftms.lastError
        )
    }

    private fun appendLiveTrackPoint(location: Location) {
        val ts = if (location.time > 0L) location.time else System.currentTimeMillis()
        val alt = if (location.hasAltitude()) location.altitude else null
        liveTrackPoints.add(TrackLatLng(location.latitude, location.longitude, alt, ts))
        if (liveTrackPoints.size > MAX_LIVE_TRACK_POINTS) {
            val compact = ArrayList<TrackLatLng>((liveTrackPoints.size / 2) + 2)
            liveTrackPoints.forEachIndexed { index, point ->
                if (index % 2 == 0 || index == liveTrackPoints.lastIndex) compact.add(point)
            }
            liveTrackPoints.clear()
            liveTrackPoints.addAll(compact)
        }
    }

    private fun markLap(auto: Boolean = false) {
        if (!isTracking) return
        val nowMs = System.currentTimeMillis()
        val lapTime = (elapsedTimeMs - lapStartElapsedMs).coerceAtLeast(0L)
        val lapDist = (distanceMeters - lapStartDistanceMeters).coerceAtLeast(0.0)
        if (lapTime <= 0L && lapDist <= 0.01) return
        val lap = LapMetrics(
            index = laps.size + 1,
            startTimeMs = lapStartEpochMs,
            endTimeMs = nowMs,
            durationMs = lapTime,
            distanceMeters = lapDist,
            avgSpeedKmh = if (lapTime > 0L) ((lapDist / (lapTime / 1000.0)) * 3.6).toFloat() else 0f,
            avgHeartRateBpm = if (lapHrCount > 0) (lapHrSum.toDouble() / lapHrCount.toDouble()).roundToInt() else null,
            maxHeartRateBpm = if (lapHrCount > 0) lapHrMax else null,
            avgPowerWatts = if (lapPowerCount > 0) (lapPowerSum.toDouble() / lapPowerCount.toDouble()).roundToInt() else null,
            normalizedPowerWatts = TrainingAnalytics.normalizedPower(lapPowerSamples.toIntArray())
        )
        laps.add(lap)
        lastCompletedLap = lap
        lastCompletedLapAtMs = nowMs
        lapStartElapsedMs = elapsedTimeMs
        lapStartDistanceMeters = distanceMeters
        lapStartEpochMs = nowMs
        resetLapAccumulators()
        lapStartEpochMs = nowMs
        if (auto) {
            val autoLapM = cyclingSettings.autoLapDistanceMeters.value
            if (autoLapM > 0) autoLapNextDistanceMeters = lapStartDistanceMeters + autoLapM.toDouble()
        }
        publishState()
        updateNotification(currentMetrics())
    }

    private fun checkAutoLap() {
        val autoLapM = cyclingSettings.autoLapDistanceMeters.value
        if (autoLapM <= 0 || !isTracking) return
        if (autoLapNextDistanceMeters <= 0.0) {
            autoLapNextDistanceMeters = (floor(distanceMeters / autoLapM.toDouble()) + 1.0) * autoLapM.toDouble()
        }
        while (distanceMeters >= autoLapNextDistanceMeters && autoLapNextDistanceMeters > 0.0) {
            markLap(auto = true)
            autoLapNextDistanceMeters += autoLapM.toDouble()
        }
    }


    private var simTickCount = 0L
    private fun applySimulatorTick() {
        val sim = try { simulatorStateHolder.state.value } catch (_: Throwable) { return }
        if (!sim.enabled || !isTracking) return
        simTickCount++
        var power = sim.powerWatts
        var hr = sim.heartRateBpm
        var cad = sim.cadenceRpm
        var speed = sim.speedKmh
        if (sim.autoIntervals) {
            val phase = (simTickCount % 30L) / 30.0 * Math.PI * 2.0
            val wave = (kotlin.math.sin(phase) * 0.5 + 0.5).toFloat() // 0..1
            power = (150 + 200 * wave).toInt().coerceIn(0, 600)
            hr = (110 + 60 * wave).toInt().coerceIn(50, 200)
            cad = (70 + 30 * wave).toInt().coerceIn(0, 130)
            speed = (18f + 20f * wave).coerceIn(0f, 60f)
        }
        latestBleReadings = com.example.bike_.data.ble.model.BleSensorReadings(
            heartRateBpm = hr,
            cadenceRpm = cad.toFloat(),
            sensorSpeedKmh = speed,
            powerWatts = power
        )

        // GPS simulation
        if (sim.followRoute && sim.routePoints.isNotEmpty()) {
            simulatorStateHolder.advanceRoute(1)
            val s2 = simulatorStateHolder.state.value
            val pt = s2.routePoints.getOrNull(s2.routeIndex) ?: return
            val (lat, lon) = offsetLatLon(pt.latitude, pt.longitude, s2.routeOffsetMeters)
            val loc = android.location.Location("sim").apply {
                latitude = lat; longitude = lon
                this.speed = (speed / 3.6f)
                accuracy = 5f
                time = System.currentTimeMillis()
                pt.elevationMeters?.let { altitude = it }
                elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
            }
            onLocation(loc)
        } else if (!sim.followRoute) {
            // simple moving point when no route follow; keep last known or seed
            val base = lastAcceptedLocation
            if (base != null) {
                val metersPerSec = speed / 3.6f
                val dLat = (metersPerSec / 111320.0)
                val nl = android.location.Location("sim").apply {
                    latitude = base.latitude + dLat
                    longitude = base.longitude
                    this.speed = metersPerSec
                    accuracy = 5f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                }
                onLocation(nl)
            }
        }
    }

    private fun offsetLatLon(lat: Double, lon: Double, metersEast: Double): Pair<Double, Double> {
        if (metersEast == 0.0) return lat to lon
        val dLon = metersEast / (111320.0 * kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.0001))
        return lat to (lon + dLon)
    }
    private fun tickStructuredWorkout() {
        val workout = structuredWorkout ?: return
        if (isAutoPaused || !isTracking) return
        val expanded = com.example.bike_.domain.workout.StructuredWorkoutEngine.expandSteps(workout.steps)
        if (expanded.isEmpty()) return
        val totalDur = expanded.sumOf { it.durationSec.coerceAtLeast(1) }
        val prevIdx = currentStructuredStepIndex(expanded, structuredWorkoutElapsedSec).first
        structuredWorkoutElapsedSec = (structuredWorkoutElapsedSec + 1).coerceAtMost(totalDur)
        val (idx, stepElapsed, stepDur) = currentStructuredStepIndex(expanded, structuredWorkoutElapsedSec)
        if (idx != prevIdx) {
            markLap(auto = false) // auto-lap on interval transition (Stage 11 link)
            structuredWorkoutBeepedStep = -1
            applyFtmsForStructuredStep(expanded, idx, force = true)
        } else {
            applyFtmsForStructuredStep(expanded, idx, force = false)
        }
        structuredWorkoutStepIndex = idx
        val remaining = (stepDur - stepElapsed).coerceAtLeast(0)
        if (remaining in 1..3 && structuredWorkoutBeepedStep != idx) {
            playStepBeep()
            if (remaining == 1) structuredWorkoutBeepedStep = idx
        }
        if (structuredWorkoutElapsedSec >= totalDur && !isStopping) {
            // План тренировки закончился — завершаем запись и показываем уведомление.
            playStepBeep()
            notifyWorkoutComplete(currentMetrics())
            stopWorkout()
        }
    }

    private fun targetPowerForStep(step: com.example.bike_.domain.workout.WorkoutStep): Int? {
        if (step.targetType != com.example.bike_.domain.workout.TargetType.POWER_FTP) return null
        val ftp = cyclingSettings.athleteFtpWatts.value.coerceAtLeast(1)
        val avgPct = ((step.targetMin + step.targetMax) / 2f)
        return (ftp * avgPct / 100f).toInt().coerceIn(0, 2000)
    }

    private fun applyFtmsForStructuredStep(expanded: List<com.example.bike_.domain.workout.WorkoutStep>, idx: Int, force: Boolean) {
        val step = expanded.getOrNull(idx) ?: return
        if (ftmsMode != com.example.bike_.data.ble.model.FtmsTrainerMode.ERG) return
        val base = targetPowerForStep(step) ?: return
        val target = (base + ftmsErgOffsetWatts).coerceIn(0, 2000)
        if (force || ftmsLastAppliedStep != idx || ftmsLastAppliedPower != target) {
            sensorsManager.setFtmsTargetPower(target)
            ftmsLastAppliedStep = idx
            ftmsLastAppliedPower = target
            ftmsTargetPowerWatts = target
        }
    }

    private fun applyFtmsForCurrentState(force: Boolean) {
        val ftms = sensorsManager.ftmsState.value
        if (!ftms.connected) return
        if (ftmsMode == com.example.bike_.data.ble.model.FtmsTrainerMode.RESISTANCE) {
            sensorsManager.setFtmsTargetResistance(ftmsTargetResistancePercent)
            return
        }
        val workout = structuredWorkout ?: return
        val expanded = com.example.bike_.domain.workout.StructuredWorkoutEngine.expandSteps(workout.steps)
        if (expanded.isEmpty()) return
        val (idx, _, _) = currentStructuredStepIndex(expanded, structuredWorkoutElapsedSec)
        applyFtmsForStructuredStep(expanded, idx, force = force)
    }

    private fun currentStructuredStepIndex(expanded: List<com.example.bike_.domain.workout.WorkoutStep>, elapsedSec: Int): Triple<Int, Int, Int> {
        var acc = 0
        val clamped = elapsedSec.coerceAtLeast(0)
        for (i in expanded.indices) {
            val d = expanded[i].durationSec.coerceAtLeast(1)
            if (clamped < acc + d || i == expanded.lastIndex) {
                return Triple(i, (clamped - acc).coerceIn(0, d), d)
            }
            acc += d
        }
        val lastDur = expanded.lastOrNull()?.durationSec?.coerceAtLeast(1) ?: 0
        return Triple(expanded.lastIndex.coerceAtLeast(0), lastDur, lastDur)
    }

    private fun skipStructuredStep() {
        val workout = structuredWorkout ?: return
        val expanded = com.example.bike_.domain.workout.StructuredWorkoutEngine.expandSteps(workout.steps)
        if (expanded.isEmpty()) return
        val (idx, _, _) = currentStructuredStepIndex(expanded, structuredWorkoutElapsedSec)
        var acc = 0
        for (i in 0..idx) acc += expanded[i].durationSec.coerceAtLeast(1)
        structuredWorkoutElapsedSec = acc.coerceAtMost(expanded.sumOf { it.durationSec.coerceAtLeast(1) })
        markLap(auto = false)
        structuredWorkoutBeepedStep = -1
        publishState()
        updateNotification(currentMetrics())
    }

    private fun repeatStructuredStep() {
        val workout = structuredWorkout ?: return
        val expanded = com.example.bike_.domain.workout.StructuredWorkoutEngine.expandSteps(workout.steps)
        if (expanded.isEmpty()) return
        val (idx, _, _) = currentStructuredStepIndex(expanded, structuredWorkoutElapsedSec)
        var acc = 0
        for (i in 0 until idx) acc += expanded[i].durationSec.coerceAtLeast(1)
        structuredWorkoutElapsedSec = acc
        markLap(auto = false)
        structuredWorkoutBeepedStep = -1
        publishState()
        updateNotification(currentMetrics())
    }

    private fun playStepBeep() {
        try {
            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80)
            tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 180)
            // release after short delay
            serviceScope.launch(Dispatchers.Main) {
                delay(250)
                try { tone.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            try {
                android.os.VibrationEffect.createOneShot(120, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(android.os.VibrationEffect.createOneShot(120, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION") vib.vibrate(120)
                }
            } catch (_: Exception) {}
        }
    }

    private fun structuredWorkoutUiState(): com.example.bike_.domain.model.StructuredWorkoutUiState? {
        val workout = structuredWorkout ?: return null
        val ftp = cyclingSettings.athleteFtpWatts.value
        val maxHr = cyclingSettings.athleteMaxHr.value
        val restHr = cyclingSettings.athleteRestingHr.value
        val state = com.example.bike_.domain.workout.StructuredWorkoutEngine.stateAt(
            workout = workout,
            elapsedSecTotal = structuredWorkoutElapsedSec,
            ftpWatts = ftp,
            maxHr = maxHr,
            restingHr = restHr,
            currentPower = smoothedPower3sWatts ?: latestBleReadings.powerWatts,
            currentHr = latestBleReadings.heartRateBpm
        )
        val expanded = state.steps
        val uiSteps = expanded.mapIndexed { i, s ->
            val (minW, maxW, _, _) = com.example.bike_.domain.workout.StructuredWorkoutEngine.targets(s, ftp, maxHr, restHr)
            com.example.bike_.domain.model.StructuredWorkoutStepUi(
                index = i,
                type = s.type.name,
                durationSec = s.durationSec,
                targetType = s.targetType.name,
                targetMin = s.targetMin,
                targetMax = s.targetMax,
                targetMinWatts = minW,
                targetMaxWatts = maxW,
                targetCadence = s.targetCadence,
                label = s.label
            )
        }
        val nextIdx = state.currentStepIndex + 1
        val nextType = expanded.getOrNull(nextIdx)?.type?.name
        return com.example.bike_.domain.model.StructuredWorkoutUiState(
            workoutId = workout.id,
            title = workout.title,
            steps = uiSteps,
            currentStepIndex = state.currentStepIndex,
            stepElapsedSec = state.stepElapsedSec,
            stepRemainingSec = state.stepRemainingSec,
            totalElapsedSec = state.totalElapsedSec,
            totalDurationSec = state.totalDurationSec,
            isCompleted = state.isCompleted,
            currentTargetMinWatts = state.currentTargetMinWatts,
            currentTargetMaxWatts = state.currentTargetMaxWatts,
            targetCadence = state.targetCadence,
            nextStepType = nextType,
            nextStepLabel = state.nextStepLabel,
            nextStepTargetLabel = state.nextStepTargetLabel,
            compliance = state.compliance.name
        )
    }

    private fun buildLapEntities(workoutId: Long, endTimeMs: Long): List<LapEntity> {
        val out = ArrayList<LapEntity>(laps.size + 1)
        laps.forEach { lap ->
            out.add(
                LapEntity(
                    workoutId = workoutId,
                    lapNumber = lap.index,
                    startTimeMs = lap.startTimeMs,
                    endTimeMs = lap.endTimeMs,
                    durationSec = (lap.durationMs / 1000L).toInt().coerceAtLeast(0),
                    distanceMeters = lap.distanceMeters,
                    avgSpeedKmh = lap.avgSpeedKmh,
                    avgHeartRateBpm = lap.avgHeartRateBpm,
                    maxHeartRateBpm = lap.maxHeartRateBpm,
                    avgPowerWatts = lap.avgPowerWatts,
                    normalizedPowerWatts = lap.normalizedPowerWatts
                )
            )
        }
        val tailTime = (elapsedTimeMs - lapStartElapsedMs).coerceAtLeast(0L)
        val tailDist = (distanceMeters - lapStartDistanceMeters).coerceAtLeast(0.0)
        if (tailTime > 0L || tailDist > 0.5) {
            out.add(
                LapEntity(
                    workoutId = workoutId,
                    lapNumber = out.size + 1,
                    startTimeMs = lapStartEpochMs,
                    endTimeMs = endTimeMs,
                    durationSec = (tailTime / 1000L).toInt().coerceAtLeast(0),
                    distanceMeters = tailDist,
                    avgSpeedKmh = if (tailTime > 0L) ((tailDist / (tailTime / 1000.0)) * 3.6).toFloat() else 0f,
                    avgHeartRateBpm = if (lapHrCount > 0) (lapHrSum.toDouble() / lapHrCount.toDouble()).roundToInt() else null,
                    maxHeartRateBpm = if (lapHrCount > 0) lapHrMax else null,
                    avgPowerWatts = if (lapPowerCount > 0) (lapPowerSum.toDouble() / lapPowerCount.toDouble()).roundToInt() else null,
                    normalizedPowerWatts = TrainingAnalytics.normalizedPower(lapPowerSamples.toIntArray())
                )
            )
        }
        return out
    }

    private fun publishState() {
        stateHolder.update(currentMetrics())
    }

    private fun recordAcceptedLocation(location: Location) {
        if (!isTracking || isStopping) return
        val ble = latestBleReadings
        val speedKmh = if (isAutoPaused) 0f else currentSpeedKmh
        val timestampMs = if (location.time > 0L) location.time else System.currentTimeMillis()
        serviceScope.launch(Dispatchers.IO) {
            try {
                // If DB row not created yet, repository will no-op until start completes in our flow? we guard by id check
                // Wait briefly for start if needed is avoided; we only record when id is known
                if (currentWorkoutId == null) {
                    // try to wait for start job completion once
                    startDbJob?.join()
                }
                if (currentWorkoutId == null) return@launch
                workoutRepository.recordTrackPoint(
                    timestampMs = timestampMs,
                    location = location,
                    speedKmh = speedKmh,
                    heartRateBpm = ble.heartRateBpm,
                    cadenceRpm = ble.cadenceRpm,
                    powerWatts = ble.powerWatts,
                    isAutoPaused = isAutoPaused,
                    stepsTotal = if (isFootWorkout()) totalSteps else null,
                    stepCadenceSpm = if (isFootWorkout()) effectiveStepCadence() else null
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun trackPointToLocation(point: com.example.bike_.data.db.entity.TrackPointEntity): Location {
        return Location("recovery").apply {
            latitude = point.latitude
            longitude = point.longitude
            time = point.timestampMs
            if (point.altitudeMeters != null) altitude = point.altitudeMeters
            if (point.speedKmh != null) speed = (point.speedKmh / 3.6f)
            accuracy = 10f
        }
    }

    private fun buildNotification(metrics: WorkoutMetrics): Notification {
        val timeText = formatDuration(metrics.movingTimeMs)
        val distanceText = String.format(Locale.US, "%.2f", metrics.distanceMeters / 1000.0)
        val status = when {
            !metrics.isTracking -> getString(R.string.tracking_state_paused)
            metrics.isAutoPaused -> getString(R.string.tracking_state_auto_paused)
            else -> getString(R.string.tracking_state_tracking)
        }
        val offRouteSuffix = if (metrics.isOffRoute) {
            val m = (metrics.offRouteMeters ?: 0f).toInt().coerceAtLeast(0)
            getString(R.string.tracking_off_route_suffix, m)
        } else ""
        val contentText = getString(R.string.tracking_notification_time_distance, timeText, distanceText) +
            " · " + status + offRouteSuffix

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            pendingIntentFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun startForegroundWithNotification(metrics: WorkoutMetrics) {
        val notification = buildNotification(metrics)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            }
            else -> {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun updateNotification(metrics: WorkoutMetrics) {
        if (!isTracking) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(metrics))
    }

    private fun notifyWorkoutComplete(metrics: WorkoutMetrics) {
        val distanceText = String.format(Locale.US, "%.2f", metrics.distanceMeters / 1000.0)
        val timeText = formatDuration(metrics.elapsedTimeMs)
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val notification = NotificationCompat.Builder(this, COMPLETE_CHANNEL_ID)
            .setContentTitle(getString(R.string.workout_complete_title))
            .setContentText(getString(R.string.workout_complete_text, distanceText, timeText))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(PendingIntent.getActivity(this, 1, openIntent, pendingIntentFlags))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(android.app.Notification.DEFAULT_SOUND or android.app.Notification.DEFAULT_VIBRATE)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(COMPLETE_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.tracking_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            val completeChannel = NotificationChannel(
                COMPLETE_CHANNEL_ID,
                getString(R.string.workout_complete_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.workout_complete_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(completeChannel)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Bike_:WorkoutTrackingWakeLock").apply {
            setReferenceCounted(false)
            try {
                acquire(4 * 60 * 60 * 1000L) // safety timeout (4h); refreshed by lifecycle in practice
            } catch (_: Exception) {
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        } finally {
            wakeLock = null
        }
    }

    private fun stopForegroundSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    companion object {
        const val ACTION_START = "com.example.bike_.action.TRACKING_START"
        const val ACTION_STOP = "com.example.bike_.action.TRACKING_STOP"
        const val ACTION_PAUSE = "com.example.bike_.action.TRACKING_PAUSE"
        const val ACTION_RESUME = "com.example.bike_.action.TRACKING_RESUME"
        const val ACTION_LAP = "com.example.bike_.action.TRACKING_LAP"
        const val ACTION_SKIP_STEP = "com.example.bike_.action.TRACKING_SKIP_STEP"
        const val ACTION_REPEAT_STEP = "com.example.bike_.action.TRACKING_REPEAT_STEP"
        const val ACTION_FTMS_SET_MODE = "com.example.bike_.action.FTMS_SET_MODE"
        const val ACTION_FTMS_ADJUST = "com.example.bike_.action.FTMS_ADJUST"
        const val EXTRA_FTMS_MODE = "extra_ftms_mode"
        const val EXTRA_FTMS_DELTA = "extra_ftms_delta"
        const val EXTRA_WORKOUT_TYPE = "extra_workout_type"
        const val EXTRA_CONTINUE_WORKOUT_ID = "extra_continue_workout_id"
        const val EXTRA_STRUCTURED_WORKOUT_ID = "extra_structured_workout_id"

        const val WORKOUT_TYPE_CYCLING = "CYCLING"
        const val WORKOUT_TYPE_RUNNING = "RUNNING"
        const val WORKOUT_TYPE_WALKING = "WALKING"
        private const val DEFAULT_WORKOUT_TYPE = WORKOUT_TYPE_CYCLING

        private const val CHANNEL_ID = "workout_tracking"
        private const val NOTIFICATION_ID = 2001
        private const val COMPLETE_CHANNEL_ID = "workout_complete"
        private const val COMPLETE_NOTIFICATION_ID = 2002
        private const val TICK_INTERVAL_MS = 1000L

        // Location request (responsive for quick auto-pause/resume, still reasonable for battery)
        // Battery-optimized GPS: active tracking keeps good accuracy, paused/auto-paused reduces polling.
        private const val LOCATION_INTERVAL_ACTIVE_MS = 2000L
        private const val LOCATION_INTERVAL_PAUSED_MS = 10000L
        private const val LOCATION_MIN_UPDATE_ACTIVE_MS = 1500L
        private const val LOCATION_MIN_UPDATE_PAUSED_MS = 5000L
        private const val LOCATION_MIN_DISTANCE_ACTIVE_M = 2f
        private const val LOCATION_MIN_DISTANCE_PAUSED_M = 10f

        // Filtering / quality thresholds
        private const val MAX_ACCURACY_M = 30f
        private const val MAX_PLAUSIBLE_SPEED_MPS = 45.0 // ~162 km/h, rejects GPS jumps
        private const val MIN_SEGMENT_DISTANCE_M = 0.75
        private const val MAX_VERTICAL_ACCURACY_M = 25f
        private const val MIN_ALTITUDE_GAIN_DELTA_M = 1.0

        // Auto-pause thresholds (with hysteresis)
        private const val AUTO_PAUSE_THRESHOLD_KMH = 2.0f
        private const val AUTO_RESUME_THRESHOLD_KMH = 2.5f
        // Use BLE speed sensor / smart trainer speed when GPS signal is lost
        private const val GPS_LOST_TIMEOUT_MS = 5000L


        // Pedometer (walking/running)
        private const val STEP_CADENCE_WINDOW_MS = 10_000L
        private const val FOOT_STEP_ACTIVE_MS = 6_000L

        // Live UI track sampling cap
        private const val MAX_LIVE_TRACK_POINTS = 6000
        private const val MAX_RESTORE_TRACK_POINTS = 4000

        fun start(context: Context, workoutType: String = DEFAULT_WORKOUT_TYPE) {
            sendAction(context, ACTION_START, workoutType)
        }

        fun startStructured(context: Context, structuredWorkoutId: Long, workoutType: String = DEFAULT_WORKOUT_TYPE) {
            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_WORKOUT_TYPE, workoutType)
                putExtra(EXTRA_STRUCTURED_WORKOUT_ID, structuredWorkoutId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun continueWorkout(context: Context, workoutId: Long) {
            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONTINUE_WORKOUT_ID, workoutId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            sendAction(context, ACTION_STOP)
        }

        fun pause(context: Context) {
            sendAction(context, ACTION_PAUSE)
        }

        fun resume(context: Context) {
            sendAction(context, ACTION_RESUME)
        }

        fun lap(context: Context) {
            sendAction(context, ACTION_LAP)
        }

        fun skipStep(context: Context) {
            sendAction(context, ACTION_SKIP_STEP)
        }

        fun repeatStep(context: Context) {
            sendAction(context, ACTION_REPEAT_STEP)
        }

        fun setFtmsMode(context: Context, erg: Boolean) {
            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                action = ACTION_FTMS_SET_MODE
                putExtra(EXTRA_FTMS_MODE, if (erg) "ERG" else "RESISTANCE")
            }
            context.startService(intent)
        }

        fun adjustFtms(context: Context, delta: Int) {
            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                action = ACTION_FTMS_ADJUST
                putExtra(EXTRA_FTMS_DELTA, delta)
            }
            context.startService(intent)
        }

        private fun sendAction(context: Context, action: String, workoutType: String? = null) {
            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                this.action = action
                if (workoutType != null) putExtra(EXTRA_WORKOUT_TYPE, workoutType)
            }
            if (action == ACTION_START) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                // Service should already be running for pause/resume/stop; startService is fine
                context.startService(intent)
            }
        }
    }
}
