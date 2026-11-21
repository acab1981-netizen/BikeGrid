package com.example.bike_.domain.model

/**
 * Plausible upper speed limits by workout type.
 *
 * GPS and BLE sensors can occasionally report a single impossible spike. Keeping the limit in one
 * place makes sure live tracking, saved workouts and recovered summaries apply the same cap.
 */
object SpeedLimits {
    const val WALKING_KMH = 12f
    const val RUNNING_KMH = 30f
    const val DEFAULT_KMH = 120f

    fun forWorkoutType(workoutType: String?): Float = when (workoutType) {
        "WALKING" -> WALKING_KMH
        "RUNNING" -> RUNNING_KMH
        else -> DEFAULT_KMH
    }

    /** Keeps one noisy GPS/sensor reading from becoming the workout max speed. */
    fun clamp(speedKmh: Float, workoutType: String?): Float =
        speedKmh.coerceIn(0f, forWorkoutType(workoutType))
}
