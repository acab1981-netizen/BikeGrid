package com.example.bike_.ui.tracking

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bike_.BikeApplication
import com.example.bike_.BuildConfig
import com.example.bike_.data.export.ExportFormat
import com.example.bike_.data.repository.WorkoutRepository
import com.example.bike_.data.settings.CyclingSettings
import com.example.bike_.data.tracking.RouteStateHolder
import com.example.bike_.data.tracking.WorkoutStateHolder
import com.example.bike_.domain.model.LoadedRoute
import com.example.bike_.domain.model.WorkoutMetrics
import com.example.bike_.domain.workout.StructuredWorkout
import com.example.bike_.service.WorkoutTrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TrackingViewModel(application: Application) : AndroidViewModel(application) {

    private val app: BikeApplication = application as BikeApplication
    private val workoutStateHolder: WorkoutStateHolder = app.workoutStateHolder
    private val routeStateHolder: RouteStateHolder = app.routeStateHolder
    private val cyclingSettings: CyclingSettings = app.cyclingSettings
    private val workoutRepository: WorkoutRepository = app.workoutRepository
    private val structuredWorkoutStore = app.structuredWorkoutStore
    private val routeRepository = app.routeRepository
    private val simulatorStateHolder = app.simulatorStateHolder

    val metrics: StateFlow<WorkoutMetrics> = workoutStateHolder.metrics
    val simulatorState = simulatorStateHolder.state
    val structuredWorkouts: StateFlow<List<StructuredWorkout>> = structuredWorkoutStore.workouts
    val loadedRoute: StateFlow<LoadedRoute?> = routeStateHolder.route
    val savedRoutes = routeRepository.observeRoutes()
    val wheelCircumferenceMm: StateFlow<Float> = cyclingSettings.wheelCircumferenceMm
    val dashboardFields: StateFlow<List<String>> = cyclingSettings.dashboardFields
    val uiTheme: StateFlow<String> = cyclingSettings.uiTheme
    val uiAccent: StateFlow<String> = cyclingSettings.uiAccent
    val uiDefaultView: StateFlow<String> = cyclingSettings.uiDefaultView
    val uiCompactCards: StateFlow<Boolean> = cyclingSettings.uiCompactCards
    val uiShowMapControls: StateFlow<Boolean> = cyclingSettings.uiShowMapControls
    val autoPauseEnabled: StateFlow<Boolean> = cyclingSettings.autoPauseEnabled
    val autoLapDistanceMeters: StateFlow<Int> = cyclingSettings.autoLapDistanceMeters
    val walkingStrideCm: StateFlow<Float> = cyclingSettings.walkingStrideCm
    val runningStrideCm: StateFlow<Float> = cyclingSettings.runningStrideCm
    val mapTileSource: StateFlow<String> = cyclingSettings.mapTileSource
    val mapUseNetwork: StateFlow<Boolean> = cyclingSettings.mapUseNetwork

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    fun startWorkout(workoutType: String = WorkoutTrackingService.WORKOUT_TYPE_CYCLING) {
        WorkoutTrackingService.start(getApplication(), workoutType)
    }

    fun startStructuredWorkout(workoutId: Long, workoutType: String = WorkoutTrackingService.WORKOUT_TYPE_CYCLING) {
        WorkoutTrackingService.startStructured(getApplication(), workoutId, workoutType)
    }

    fun skipStructuredStep() {
        WorkoutTrackingService.skipStep(getApplication())
    }

    fun repeatStructuredStep() {
        WorkoutTrackingService.repeatStep(getApplication())
    }

    fun importZwo(input: java.io.InputStream, sourceName: String?): StructuredWorkout? {
        val parsed = try { com.example.bike_.data.workout.ZwoParser.parse(input, sourceName) ?: return null }
        catch (_: Exception) { return null }
        return structuredWorkoutStore.save(parsed, cyclingSettings.athleteFtpWatts.value)
    }

    fun saveStructuredWorkout(workout: StructuredWorkout): StructuredWorkout {
        return structuredWorkoutStore.save(workout, cyclingSettings.athleteFtpWatts.value)
    }

    fun deleteStructuredWorkout(id: Long) {
        structuredWorkoutStore.delete(id)
    }

    fun stopWorkout() {
        WorkoutTrackingService.stop(getApplication())
    }

    fun pauseWorkout() {
        WorkoutTrackingService.pause(getApplication())
    }

    fun resumeWorkout() {
        WorkoutTrackingService.resume(getApplication())
    }

    fun lapWorkout() {
        WorkoutTrackingService.lap(getApplication())
    }

    fun setFtmsMode(erg: Boolean) {
        WorkoutTrackingService.setFtmsMode(getApplication(), erg)
    }

    fun adjustFtms(delta: Int) {
        WorkoutTrackingService.adjustFtms(getApplication(), delta)
    }

    fun setSimulatorEnabled(v: Boolean) { if (BuildConfig.ENABLE_TEST_TOOLS) simulatorStateHolder.setEnabled(v) }
    fun setSimPower(v: Int) { if (BuildConfig.ENABLE_TEST_TOOLS) simulatorStateHolder.setPower(v) }
    fun setSimHr(v: Int) { if (BuildConfig.ENABLE_TEST_TOOLS) simulatorStateHolder.setHr(v) }
    fun setSimCadence(v: Int) { if (BuildConfig.ENABLE_TEST_TOOLS) simulatorStateHolder.setCadence(v) }
    fun setSimSpeed(v: Float) { if (BuildConfig.ENABLE_TEST_TOOLS) simulatorStateHolder.setSpeed(v) }
    fun setSimAutoIntervals(v: Boolean) { if (BuildConfig.ENABLE_TEST_TOOLS) simulatorStateHolder.setAutoIntervals(v) }
    fun setSimFollowRoute(v: Boolean) {
        if (!BuildConfig.ENABLE_TEST_TOOLS) return
        val route = routeStateHolder.route.value
        simulatorStateHolder.setFollowRoute(v, route?.points ?: emptyList())
    }
    fun simOffsetRoute100m() { if (BuildConfig.ENABLE_TEST_TOOLS) simulatorStateHolder.offsetRoute(100.0) }
    fun simResetRouteOffset() { if (BuildConfig.ENABLE_TEST_TOOLS) simulatorStateHolder.resetRouteOffset() }

    fun generateTestHistory30Days() {
        if (!BuildConfig.ENABLE_TEST_TOOLS) return
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val dayMs = 24L * 3600L * 1000L
            for (i in 29 downTo 0) {
                val start = now - i * dayMs - 3600_000L
                val end = start + 3600_000L
                val tss = when { i % 7 == 0 -> 20f; i % 3 == 0 -> 90f; else -> 55f + (i % 5) * 5f }
                val avgP = (150 + (i % 6) * 10)
                val np = avgP
                val iff = avgP / cyclingSettings.athleteFtpWatts.value.toFloat()
                val entity = com.example.bike_.data.db.entity.WorkoutEntity(
                    startTimeMs = start, endTimeMs = end, elapsedTimeMs = 3600_000L, movingTimeMs = 3600_000L,
                    distanceMeters = 30000.0, avgSpeedKmh = 30f, maxSpeedKmh = 45f,
                    avgHeartRateBpm = 140, maxHeartRateBpm = 165, avgPowerWatts = avgP, maxPowerWatts = avgP + 80,
                    workoutType = "CYCLING", isFinished = true,
                    normalizedPowerWatts = np, intensityFactor = iff, trainingStressScore = tss,
                    caloriesKcal = 700, caloriesSource = "POWER"
                )
                try { workoutRepository.insertWorkout(entity) } catch (_: Exception) {}
            }
        }
    }

    fun setWheelCircumferenceMm(value: Float) {
        cyclingSettings.setWheelCircumferenceMm(value)
    }

    fun setDashboardFields(fields: List<String>) {
        cyclingSettings.setDashboardFields(fields)
    }

    fun setUiTheme(theme: String) {
        cyclingSettings.setUiTheme(theme)
    }

    fun setUiAccent(accent: String) {
        cyclingSettings.setUiAccent(accent)
    }

    fun setUiDefaultView(view: String) {
        cyclingSettings.setUiDefaultView(view)
    }

    fun setUiCompactCards(enabled: Boolean) {
        cyclingSettings.setUiCompactCards(enabled)
    }

    fun setUiShowMapControls(enabled: Boolean) {
        cyclingSettings.setUiShowMapControls(enabled)
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        cyclingSettings.setAutoPauseEnabled(enabled)
    }

    fun setAutoLapDistanceMeters(meters: Int) {
        cyclingSettings.setAutoLapDistanceMeters(meters)
    }

    fun setWalkingStrideCm(cm: Float) {
        cyclingSettings.setWalkingStrideCm(cm)
    }

    fun setRunningStrideCm(cm: Float) {
        cyclingSettings.setRunningStrideCm(cm)
    }

    fun setLoadedRoute(route: LoadedRoute?) {
        routeStateHolder.setRoute(route)
    }

    fun clearLoadedRoute() {
        routeStateHolder.clear()
    }

    fun importRoute(input: java.io.InputStream, fileName: String?): LoadedRoute? {
        val parsed = try { com.example.bike_.data.route.RouteFileParser.parse(input) } catch (_: Exception) { return null }
        if (parsed.points.isEmpty()) return null
        return kotlinx.coroutines.runBlocking(Dispatchers.IO) { routeRepository.saveImportedRoute(parsed, fileName) }
    }

    fun selectSavedRoute(route: LoadedRoute) {
        routeStateHolder.setRoute(route)
    }

    fun deleteSavedRoute(id: Long) {
        viewModelScope.launch { routeRepository.deleteRoute(id) }
    }

    fun exportLastFinishedWorkout(format: ExportFormat = ExportFormat.GPX) {
        viewModelScope.launch {
            _exportState.value = ExportUiState.InProgress
            try {
                val last = workoutRepository.getLastFinishedWorkout()
                if (last == null) {
                    _exportState.value = ExportUiState.Error(getApplication<android.app.Application>().getString(com.example.bike_.R.string.export_no_workout))
                    return@launch
                }
                when (val result = workoutRepository.exportWorkoutAndShare(last.id, format)) {
                    is WorkoutRepository.ExportResult.Success -> {
                        _exportState.value = ExportUiState.Ready(result.path, result.shareIntent)
                    }
                    is WorkoutRepository.ExportResult.Error -> {
                        _exportState.value = ExportUiState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _exportState.value = ExportUiState.Error(e.message ?: getApplication<android.app.Application>().getString(com.example.bike_.R.string.export_error))
            }
        }
    }

    fun consumeExportState() {
        _exportState.value = ExportUiState.Idle
    }

    sealed interface ExportUiState {
        data object Idle : ExportUiState
        data object InProgress : ExportUiState
        data class Ready(val path: String, val shareIntent: Intent) : ExportUiState
        data class Error(val message: String) : ExportUiState
    }
}
