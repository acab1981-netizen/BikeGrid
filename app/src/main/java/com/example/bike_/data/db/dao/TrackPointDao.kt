package com.example.bike_.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bike_.data.db.entity.TrackPointEntity

@Dao
interface TrackPointDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE workoutId = :workoutId ORDER BY timestampMs ASC")
    suspend fun getByWorkoutId(workoutId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE workoutId = :workoutId ORDER BY timestampMs DESC LIMIT 1")
    suspend fun getLastByWorkoutId(workoutId: Long): TrackPointEntity?

    @Query("SELECT COUNT(*) FROM track_points WHERE workoutId = :workoutId")
    suspend fun countByWorkoutId(workoutId: Long): Int

    @Query("DELETE FROM track_points WHERE workoutId = :workoutId")
    suspend fun deleteByWorkoutId(workoutId: Long)

    @Query("SELECT * FROM track_points ORDER BY id ASC")
    suspend fun getAll(): List<TrackPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(points: List<TrackPointEntity>)

    @Query("DELETE FROM track_points")
    suspend fun deleteAll()
}
