package com.example.bike_

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.example.bike_.data.backup.BackupManager
import com.example.bike_.data.crash.CrashReporting
import com.example.bike_.data.map.AppMapConfig
import com.example.bike_.data.sensors.UnifiedSensorsManager
import com.example.bike_.data.db.BikeDatabase
import com.example.bike_.data.healthconnect.HealthConnectManager
import com.example.bike_.data.repository.WorkoutRepository
import com.example.bike_.data.route.RouteRepository
import com.example.bike_.data.settings.CyclingSettings
import com.example.bike_.data.tracking.RouteStateHolder
import com.example.bike_.data.tracking.SimulatorStateHolder
import com.example.bike_.data.tracking.WorkoutStateHolder
import com.example.bike_.data.watch.WatchSyncManager
import com.example.bike_.data.workout.StructuredWorkoutStore

class BikeApplication : Application() {

    lateinit var workoutStateHolder: WorkoutStateHolder
        private set
    lateinit var routeStateHolder: RouteStateHolder
        private set
    lateinit var simulatorStateHolder: SimulatorStateHolder
        private set
    lateinit var cyclingSettings: CyclingSettings
        private set
    lateinit var sensorsManager: UnifiedSensorsManager
        private set
    lateinit var database: BikeDatabase
        private set
    lateinit var workoutRepository: WorkoutRepository
        private set
    lateinit var structuredWorkoutStore: StructuredWorkoutStore
        private set
    lateinit var routeRepository: RouteRepository
        private set
    lateinit var backupManager: BackupManager
        private set
    lateinit var healthConnectManager: HealthConnectManager
        private set
    lateinit var watchSyncManager: WatchSyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Crashlytics is enabled in release builds only; debug crashes stay in logcat.
        CrashReporting.initialize(!BuildConfig.DEBUG, BuildConfig.VERSION_NAME)

        // osmdroid: configure early with correct internal-storage paths, valid User-Agent and cache limits.
        try {
            AppMapConfig.ensureConfigured(this)
            if (BuildConfig.DEBUG) Log.i("BikeApplication", "osmdroid configured: base=${AppMapConfig.baseDir(this).absolutePath} tiles=${AppMapConfig.tilesDir(this).absolutePath}")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("BikeApplication", "osmdroid config failed: ${e.message}", e)
        }

        workoutStateHolder = WorkoutStateHolder()
        routeStateHolder = RouteStateHolder()
        simulatorStateHolder = SimulatorStateHolder()
        cyclingSettings = CyclingSettings(this)
        sensorsManager = UnifiedSensorsManager(this, cyclingSettings)

        database = Room.databaseBuilder(
            applicationContext,
            BikeDatabase::class.java,
            "bike_workouts.db"
        ).addMigrations(BikeDatabase.MIGRATION_5_6)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

        workoutRepository = WorkoutRepository(applicationContext, database)
        healthConnectManager = HealthConnectManager(applicationContext, workoutRepository, cyclingSettings)
        watchSyncManager = WatchSyncManager(applicationContext, workoutRepository)
        backupManager = BackupManager(applicationContext, database, cyclingSettings)
        structuredWorkoutStore = StructuredWorkoutStore(applicationContext)
        routeRepository = RouteRepository(database)
    }

    companion object {
        lateinit var instance: BikeApplication
            private set
    }
}
