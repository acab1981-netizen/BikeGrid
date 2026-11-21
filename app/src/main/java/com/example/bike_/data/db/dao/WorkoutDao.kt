package com.example.bike_.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.bike_.data.db.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Update
    suspend fun update(workout: WorkoutEntity)

    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts ORDER BY startTimeMs DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE isFinished = 1 ORDER BY endTimeMs DESC LIMIT 1")
    suspend fun getLastFinished(): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE isFinished = 0 ORDER BY startTimeMs DESC LIMIT 1")
    suspend fun getUnfinished(): WorkoutEntity?

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM workouts ORDER BY id ASC")
    suspend fun getAll(): List<WorkoutEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(workouts: List<WorkoutEntity>)

    @Query("DELETE FROM workouts")
    suspend fun deleteAll()
}
