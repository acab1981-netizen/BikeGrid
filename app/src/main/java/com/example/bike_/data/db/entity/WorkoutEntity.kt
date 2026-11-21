package com.example.bike_.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutType: String = "CYCLING",
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val elapsedTimeMs: Long = 0L,
    val movingTimeMs: Long = 0L,
    val distanceMeters: Double = 0.0,
    val avgSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val avgHeartRateBpm: Int? = null,
    val maxHeartRateBpm: Int? = null,
    val avgCadenceRpm: Float? = null,
    val maxCadenceRpm: Float? = null,
    val avgPowerWatts: Int? = null,
    val maxPowerWatts: Int? = null,
    val elevationGainMeters: Double = 0.0,
    val totalSteps: Int? = null,
    val avgStepCadenceSpm: Int? = null,
    val maxStepCadenceSpm: Int? = null,
    val isFinished: Boolean = false,
    // Advanced analytics (Stage 9)
    val caloriesKcal: Int? = null,
    val caloriesSource: String? = null, // POWER / HR
    val normalizedPowerWatts: Int? = null,
    val intensityFactor: Float? = null,
    val trainingStressScore: Float? = null,
    val hrZoneSeconds: String? = null,    // CSV 5 values
    val powerZoneSeconds: String? = null  // CSV 7 values
)
