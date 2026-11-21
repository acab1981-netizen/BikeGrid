package com.example.bike_.domain.model

data class TrackLatLng(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val timestampMs: Long = 0L
)

data class LapMetrics(
    val index: Int,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val durationMs: Long,
    val distanceMeters: Double,
    val avgSpeedKmh: Float = 0f,
    val avgHeartRateBpm: Int? = null,
    val maxHeartRateBpm: Int? = null,
    val avgPowerWatts: Int? = null,
    val normalizedPowerWatts: Int? = null
)

data class StructuredWorkoutStepUi(
    val index: Int,
    val type: String,
    val durationSec: Int,
    val targetType: String,
    val targetMin: Int,
    val targetMax: Int,
    val targetMinWatts: Int? = null,
    val targetMaxWatts: Int? = null,
    val targetCadence: Int? = null,
    val label: String? = null
)

data class StructuredWorkoutUiState(
    val workoutId: Long = 0L,
    val title: String = "",
    val steps: List<StructuredWorkoutStepUi> = emptyList(),
    val currentStepIndex: Int = 0,
    val stepElapsedSec: Int = 0,
    val stepRemainingSec: Int = 0,
    val totalElapsedSec: Int = 0,
    val totalDurationSec: Int = 0,
    val isCompleted: Boolean = false,
    val currentTargetMinWatts: Int? = null,
    val currentTargetMaxWatts: Int? = null,
    val targetCadence: Int? = null,
    val nextStepType: String? = null,
    val nextStepLabel: String? = null,
    val nextStepTargetLabel: String? = null,
    val compliance: String = "NONE" // NONE/BELOW/IN_RANGE/ABOVE
)

data class WorkoutMetrics(
    val isTracking: Boolean = false,
    val isAutoPaused: Boolean = false,
    val elapsedTimeMs: Long = 0L,
    val movingTimeMs: Long = 0L,
    val distanceMeters: Double = 0.0,
    val currentSpeedKmh: Float = 0f,
    val averageSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val elevationGainMeters: Double = 0.0,
    // BLE sensor data (merged into workout state)
    val heartRateBpm: Int? = null,
    val cadenceRpm: Float? = null,
    val powerWatts: Int? = null,
    val sensorSpeedKmh: Float? = null,
    // Walking/running pedometer
    val steps: Int = 0,
    val stepCadenceSpm: Int = 0,
    // Live route/laps for UI
    val trackPoints: List<TrackLatLng> = emptyList(),
    val laps: List<LapMetrics> = emptyList(),
    val currentLapTimeMs: Long = 0L,
    val currentLapDistanceMeters: Double = 0.0,
    val lastCompletedLap: LapMetrics? = null,
    val lastCompletedLapAtMs: Long = 0L,
    // Route navigation
    val offRouteMeters: Float? = null,
    val isOffRoute: Boolean = false,
    val routeRemainingDistanceMeters: Double? = null,
    val routeRemainingElevationGainMeters: Double? = null,
    val routeProgressDistanceMeters: Double? = null,
    val routeBearingToReturnDeg: Float? = null,
    // Advanced analytics (live)
    val smoothedPower3sWatts: Int? = null,
    val currentHrZone: Int? = null,
    val currentPowerZone: Int? = null,
    val caloriesKcal: Int = 0,
    val caloriesSource: String? = null, // POWER / HR
    val normalizedPowerWatts: Int? = null,
    val intensityFactor: Float? = null,
    val trainingStressScore: Float? = null,
    val hrZoneSeconds: IntArray = IntArray(5),
    val powerZoneSeconds: IntArray = IntArray(7),
    // Structured workout player
    val structuredWorkout: StructuredWorkoutUiState? = null,
    // FTMS Smart Trainer
    val ftmsConnected: Boolean = false,
    val ftmsDeviceName: String? = null,
    val ftmsModeErg: Boolean = true,
    val ftmsControlGranted: Boolean = false,
    val ftmsTargetPowerWatts: Int? = null,
    val ftmsTargetResistancePercent: Int? = null,
    val ftmsLastError: String? = null
) {
    val distanceKm: Double get() = distanceMeters / 1000.0
    /** Current pace in minutes per km, or null if not moving. */
    val paceMinPerKm: Float?
        get() = if (currentSpeedKmh > 0.3f) 60f / currentSpeedKmh else null
}
