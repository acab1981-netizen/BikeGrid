package com.example.bike_.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.bike_.data.db.dao.LapDao
import com.example.bike_.data.db.dao.SavedRouteDao
import com.example.bike_.data.db.dao.TrackPointDao
import com.example.bike_.data.db.dao.WorkoutDao
import com.example.bike_.data.db.entity.LapEntity
import com.example.bike_.data.db.entity.SavedRouteEntity
import com.example.bike_.data.db.entity.TrackPointEntity
import com.example.bike_.data.db.entity.WorkoutEntity

@Database(
    entities = [WorkoutEntity::class, TrackPointEntity::class, LapEntity::class, SavedRouteEntity::class],
    version = 6,
    exportSchema = false
)
abstract class BikeDatabase : RoomDatabase() {
    companion object {
        // Old builds could store a single GPS/sensor spike as the workout max and point speed.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE workouts SET maxSpeedKmh = MIN(maxSpeedKmh, 12.0) " +
                        "WHERE workoutType = 'WALKING' AND maxSpeedKmh > 12.0"
                )
                db.execSQL(
                    "UPDATE workouts SET maxSpeedKmh = MIN(maxSpeedKmh, 30.0) " +
                        "WHERE workoutType = 'RUNNING' AND maxSpeedKmh > 30.0"
                )
                db.execSQL(
                    "UPDATE track_points SET speedKmh = MIN(speedKmh, 12.0) " +
                        "WHERE workoutId IN (SELECT id FROM workouts WHERE workoutType = 'WALKING') AND speedKmh > 12.0"
                )
                db.execSQL(
                    "UPDATE track_points SET speedKmh = MIN(speedKmh, 30.0) " +
                        "WHERE workoutId IN (SELECT id FROM workouts WHERE workoutType = 'RUNNING') AND speedKmh > 30.0"
                )
            }
        }
    }

    abstract fun workoutDao(): WorkoutDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun lapDao(): LapDao
    abstract fun savedRouteDao(): SavedRouteDao
}
