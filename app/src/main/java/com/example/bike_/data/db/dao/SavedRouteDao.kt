package com.example.bike_.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bike_.data.db.entity.SavedRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedRouteDao {
    @Query("SELECT * FROM saved_routes ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<SavedRouteEntity>>

    @Query("SELECT * FROM saved_routes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SavedRouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SavedRouteEntity): Long

    @Query("DELETE FROM saved_routes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM saved_routes ORDER BY id ASC")
    suspend fun getAll(): List<SavedRouteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(routes: List<SavedRouteEntity>)

    @Query("DELETE FROM saved_routes")
    suspend fun deleteAll()
}
