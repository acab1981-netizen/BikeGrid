package com.example.bike_.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "laps",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workoutId"]), Index(value = ["lapNumber"])]
)
data class LapEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutId: Long,
    val lapNumber: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationSec: Int,
    val distanceMeters: Double,
    val avgSpeedKmh: Float,
    val avgHeartRateBpm: Int? = null,
    val maxHeartRateBpm: Int? = null,
    val avgPowerWatts: Int? = null,
    val normalizedPowerWatts: Int? = null
)
