package com.example.bike_.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fileName: String? = null,
    val createdAtMs: Long,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val pointsJson: String
)
