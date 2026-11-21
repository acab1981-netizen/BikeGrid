package com.example.bike_.data.healthconnect

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import com.example.bike_.data.crash.CrashReporting
import com.example.bike_.data.db.entity.TrackPointEntity
import com.example.bike_.data.db.entity.WorkoutEntity
import com.example.bike_.data.repository.WorkoutRepository
import com.example.bike_.data.settings.CyclingSettings
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Health Connect integration.
 *
 * Export writes finished workouts to Health Connect. Import reads exercise sessions from other
 * apps and creates summary workouts in the local history. Import is intentionally summary-only:
 * no fake GPS track points are created from Health Connect samples.
 */
class HealthConnectManager(
    context: Context,
    private val repository: WorkoutRepository,
    private val settings: CyclingSettings
) {
    private val appContext = context.applicationContext
    private val device = Device(Device.TYPE_PHONE, Build.MANUFACTURER, Build.MODEL)

    val permissionRequestContract = PermissionController.createRequestPermissionResultContract()

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(appContext) == HealthConnectClient.SDK_AVAILABLE

    fun areWritePermissionsGranted(grantedPermissions: Set<String>): Boolean =
        grantedPermissions.containsAll(WRITE_PERMISSIONS)

    fun areReadPermissionsGranted(grantedPermissions: Set<String>): Boolean =
        grantedPermissions.containsAll(READ_PERMISSIONS)

    suspend fun hasWritePermissions(): Boolean {
        if (!isAvailable()) return false
        return try {
            val client = HealthConnectClient.getOrCreate(appContext)
            areWritePermissionsGranted(client.permissionController.getGrantedPermissions())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read Health Connect permissions: ${e.message}")
            false
        }
    }

    suspend fun hasReadPermissions(): Boolean {
        if (!isAvailable()) return false
        return try {
            val client = HealthConnectClient.getOrCreate(appContext)
            areReadPermissionsGranted(client.permissionController.getGrantedPermissions())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read Health Connect read permissions: ${e.message}")
            false
        }
    }

    suspend fun exportWorkout(workoutId: Long): ExportResult {
        if (!settings.healthConnectEnabled.value) return ExportResult.Disabled
        if (!isAvailable()) return ExportResult.Unavailable
        if (!hasWritePermissions()) return ExportResult.PermissionsMissing

        val workout = repository.getWorkout(workoutId) ?: return ExportResult.NoWorkout
        val points = repository.getTrackPoints(workoutId)
        val records = buildRecords(workout, points)
        if (records.isEmpty()) return ExportResult.NoData

        return try {
            val client = HealthConnectClient.getOrCreate(appContext)
            client.insertRecords(records)
            ExportResult.Success(records.size)
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect export failed for workout $workoutId: ${e.message}", e)
            CrashReporting.record(e, "Health Connect export workout $workoutId")
            ExportResult.Error(e.message ?: "unknown error")
        }
    }

    suspend fun importWorkouts(): ImportResult {
        if (!isAvailable()) return ImportResult.Unavailable
        if (!hasReadPermissions()) return ImportResult.PermissionsMissing

        return try {
            val client = HealthConnectClient.getOrCreate(appContext)
            val now = Instant.now()
            val from = now.minus(Duration.ofDays(IMPORT_HISTORY_DAYS))
            val sessions = readAllExerciseSessions(client, from, now)
            val existing = repository.getAllWorkouts()
            var imported = 0
            var skipped = 0

            for (session in sessions) {
                val type = localWorkoutType(session.exerciseType)
                if (type == null || session.metadata.clientRecordId?.startsWith(OWN_RECORD_PREFIX) == true) {
                    skipped++
                    continue
                }
                val durationMs = Duration.between(session.startTime, session.endTime).toMillis()
                if (durationMs <= 0L || isDuplicate(existing, type, session.startTime.toEpochMilli())) {
                    skipped++
                    continue
                }

                val filter = TimeRangeFilter.between(session.startTime, session.endTime)
                val aggregate = client.aggregate(
                    AggregateRequest(
                        metrics = IMPORT_METRICS,
                        timeRangeFilter = filter,
                        dataOriginFilter = emptySet()
                    )
                )
                val distanceMeters = aggregate[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
                val steps = aggregate[StepsRecord.COUNT_TOTAL]?.toInt()?.takeIf { it > 0 }
                val avgHr = aggregate[HeartRateRecord.BPM_AVG]?.toInt()?.takeIf { it in 1..300 }
                val maxHr = aggregate[HeartRateRecord.BPM_MAX]?.toInt()?.takeIf { it in 1..300 }
                val calories = aggregate[TotalCaloriesBurnedRecord.ENERGY_TOTAL]
                    ?.inKilocalories
                    ?.roundToInt()
                    ?.takeIf { it > 0 }
                val avgSpeed = if (distanceMeters > 0.0 && durationMs > 0L) {
                    ((distanceMeters / 1000.0) / (durationMs / 3_600_000.0)).toFloat().coerceAtMost(300f)
                } else {
                    0f
                }

                repository.insertWorkout(
                    WorkoutEntity(
                        workoutType = type,
                        startTimeMs = session.startTime.toEpochMilli(),
                        endTimeMs = session.endTime.toEpochMilli(),
                        elapsedTimeMs = durationMs,
                        movingTimeMs = durationMs,
                        distanceMeters = if (distanceMeters.isFinite()) distanceMeters else 0.0,
                        avgSpeedKmh = avgSpeed,
                        maxSpeedKmh = 0f,
                        avgHeartRateBpm = avgHr,
                        maxHeartRateBpm = maxHr,
                        elevationGainMeters = 0.0,
                        totalSteps = if (type != WORKOUT_TYPE_CYCLING) steps else null,
                        isFinished = true,
                        caloriesKcal = calories,
                        caloriesSource = if (calories != null) "HEALTH_CONNECT" else null
                    )
                )
                imported++
            }
            ImportResult.Success(imported = imported, skipped = skipped)
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect import failed: ${e.message}", e)
            CrashReporting.record(e, "Health Connect import")
            ImportResult.Error(e.message ?: "unknown error")
        }
    }

    private suspend fun readAllExerciseSessions(
        client: HealthConnectClient,
        from: Instant,
        to: Instant
    ): List<ExerciseSessionRecord> {
        val all = ArrayList<ExerciseSessionRecord>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageToken = pageToken
                )
            )
            all += response.records
            pageToken = response.pageToken
        } while (!pageToken.isNullOrBlank())
        return all
    }

    /** Pure record builder; kept separate from the client call so it stays easy to reason about. */
    fun buildRecords(workout: WorkoutEntity, points: List<TrackPointEntity>): List<Record> {
        val startMs = workout.startTimeMs.takeIf { it > 0L }
            ?: points.minOfOrNull { it.timestampMs }
            ?: return emptyList()
        val endMs = maxOf(
            workout.endTimeMs ?: 0L,
            points.maxOfOrNull { it.timestampMs } ?: 0L,
            startMs + workout.elapsedTimeMs
        )
        if (endMs <= startMs) return emptyList()

        val startTime = instant(startMs)
        val endTime = instant(endMs)
        val startOffset = offset(startMs)
        val endOffset = offset(endMs)
        val records = ArrayList<Record>(5)

        records += ExerciseSessionRecord(
            startTime = startTime,
            startZoneOffset = startOffset,
            endTime = endTime,
            endZoneOffset = endOffset,
            metadata = metadata(workout.id, "exercise"),
            exerciseType = exportExerciseType(workout.workoutType),
            title = workout.workoutType
        )

        val steps = workout.totalSteps?.takeIf { it > 0 }
            ?: points.lastOrNull { (it.stepsTotal ?: 0) > 0 }?.stepsTotal
            ?: 0
        if (isFootWorkout(workout.workoutType) && steps > 0) {
            records += StepsRecord(
                startTime = startTime,
                startZoneOffset = startOffset,
                endTime = endTime,
                endZoneOffset = endOffset,
                count = steps.toLong(),
                metadata = metadata(workout.id, "steps")
            )
        }

        val hrSamples = points.asSequence()
            .filter { it.timestampMs in startMs..endMs }
            .mapNotNull { point ->
                val bpm = point.heartRateBpm ?: return@mapNotNull null
                if (bpm in 1..300) HeartRateRecord.Sample(instant(point.timestampMs), bpm.toLong()) else null
            }
            .sortedBy { it.time }
            .toList()
        if (hrSamples.isNotEmpty()) {
            val firstHr = hrSamples.first().time
            val lastHr = hrSamples.last().time.let { if (it == firstHr) it.plusMillis(1_000L) else it }
            records += HeartRateRecord(
                startTime = firstHr,
                startZoneOffset = offset(firstHr.toEpochMilli()),
                endTime = lastHr,
                endZoneOffset = offset(lastHr.toEpochMilli()),
                samples = hrSamples,
                metadata = metadata(workout.id, "heart_rate")
            )
        }

        if (workout.distanceMeters > 0.0 && workout.distanceMeters.isFinite()) {
            records += DistanceRecord(
                startTime = startTime,
                startZoneOffset = startOffset,
                endTime = endTime,
                endZoneOffset = endOffset,
                distance = Length.meters(workout.distanceMeters),
                metadata = metadata(workout.id, "distance")
            )
        }

        val calories = workout.caloriesKcal?.takeIf { it > 0 }
        if (calories != null) {
            records += TotalCaloriesBurnedRecord(
                startTime = startTime,
                startZoneOffset = startOffset,
                endTime = endTime,
                endZoneOffset = endOffset,
                energy = Energy.kilocalories(calories.toDouble()),
                metadata = metadata(workout.id, "calories")
            )
        }

        return records
    }

    private fun metadata(workoutId: Long, suffix: String): Metadata =
        Metadata.autoRecorded(device, "${OWN_RECORD_PREFIX}${workoutId}_${suffix}", 0L)

    private fun instant(epochMs: Long): Instant = Instant.ofEpochMilli(epochMs)

    private fun offset(epochMs: Long): ZoneOffset =
        ZoneOffset.systemDefault().rules.getOffset(instant(epochMs))

    private fun isDuplicate(existing: List<WorkoutEntity>, type: String, startMs: Long): Boolean =
        existing.any { it.workoutType == type && abs(it.startTimeMs - startMs) <= DUPLICATE_WINDOW_MS }

    private fun isFootWorkout(type: String): Boolean =
        type == WORKOUT_TYPE_RUNNING || type == WORKOUT_TYPE_WALKING

    private fun exportExerciseType(type: String): Int = when (type.uppercase()) {
        WORKOUT_TYPE_CYCLING -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        WORKOUT_TYPE_RUNNING -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        WORKOUT_TYPE_WALKING -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    }

    private fun localWorkoutType(exerciseType: Int): String? = when (exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> WORKOUT_TYPE_CYCLING

        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> WORKOUT_TYPE_RUNNING

        ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> WORKOUT_TYPE_WALKING

        else -> null
    }

    sealed class ExportResult {
        data class Success(val recordCount: Int) : ExportResult()
        data object Disabled : ExportResult()
        data object Unavailable : ExportResult()
        data object PermissionsMissing : ExportResult()
        data object NoWorkout : ExportResult()
        data object NoData : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    sealed class ImportResult {
        data class Success(val imported: Int, val skipped: Int) : ImportResult()
        data object Unavailable : ImportResult()
        data object PermissionsMissing : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    companion object {
        private const val TAG = "HealthConnectManager"
        private const val OWN_RECORD_PREFIX = "bike_"
        private const val IMPORT_HISTORY_DAYS = 730L
        private const val DUPLICATE_WINDOW_MS = 120_000L

        private const val WORKOUT_TYPE_CYCLING = "CYCLING"
        private const val WORKOUT_TYPE_RUNNING = "RUNNING"
        private const val WORKOUT_TYPE_WALKING = "WALKING"

        private val IMPORT_METRICS: Set<AggregateMetric<*>> = setOf(
            DistanceRecord.DISTANCE_TOTAL,
            StepsRecord.COUNT_TOTAL,
            HeartRateRecord.BPM_AVG,
            HeartRateRecord.BPM_MAX,
            TotalCaloriesBurnedRecord.ENERGY_TOTAL
        )

        val WRITE_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
        )

        val READ_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        )
    }
}
