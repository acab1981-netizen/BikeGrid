package com.example.bike_.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bike_.data.db.entity.LapEntity

@Dao
interface LapDao {
    @Insert
    suspend fun insertAll(laps: List<LapEntity>)

    @Query("SELECT * FROM laps WHERE workoutId = :workoutId ORDER BY lapNumber ASC")
    suspend fun getByWorkoutId(workoutId: Long): List<LapEntity>

    @Query("DELETE FROM laps WHERE workoutId = :workoutId")
    suspend fun deleteByWorkoutId(workoutId: Long)

    @Query("SELECT * FROM laps ORDER BY id ASC")
    suspend fun getAll(): List<LapEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(laps: List<LapEntity>)

    @Query("DELETE FROM laps")
    suspend fun deleteAll()
}
