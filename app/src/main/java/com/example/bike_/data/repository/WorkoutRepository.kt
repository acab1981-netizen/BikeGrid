package com.example.bike_.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.bike_.data.db.BikeDatabase
import com.example.bike_.data.db.entity.LapEntity
import com.example.bike_.data.db.entity.TrackPointEntity
import com.example.bike_.data.db.entity.WorkoutEntity
import com.example.bike_.data.export.ExportFormat
import com.example.bike_.data.export.WorkoutExporter
import com.example.bike_.data.settings.CyclingSettings
import com.example.bike_.domain.analytics.TrainingAnalytics
import com.example.bike_.domain.model.SpeedLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Handles workout persistence in real time with batched track-point writes and export/sharing helpers.
 */
class WorkoutRepository(
    private val context: Context,
    private val db: BikeDatabase
) {
    private val workoutDao = db.workoutDao()
    private val trackPointDao = db.trackPointDao()
    private val lapDao = db.lapDao()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    @Volatile
    private var activeWorkoutId: Long? = null
    @Volatile
    private var workoutStartTimeMs: Long = 0L

    // Aggregates for summary
    @Volatile private var hrSum: Long = 0L
    @Volatile private var hrCount: Int = 0
    @Volatile private var hrMax: Int = 0
    @Volatile private var cadenceSum: Double = 0.0
    @Volatile private var cadenceCount: Int = 0
    @Volatile private var cadenceMax: Float = 0f
    @Volatile private var powerSum: Long = 0L
    @Volatile private var powerCount: Int = 0
    @Volatile private var powerMax: Int = 0
    @Volatile private var stepCadenceSum: Long = 0L
    @Volatile private var stepCadenceCount: Int = 0
    @Volatile private var stepCadenceMax: Int = 0
    @Volatile private var maxStepsTotal: Int = 0

    private val pendingPoints = ArrayList<TrackPointEntity>(POINT_BATCH_SIZE)
    private var lastPointTimestampMs: Long = 0L
    private var lastRecordedLat: Double? = null
    private var lastRecordedLon: Double? = null

    fun observeWorkouts(): Flow<List<WorkoutEntity>> = workoutDao.observeAll()

    suspend fun startWorkout(workoutType: String = "CYCLING", startTimeMs: Long = System.currentTimeMillis()): Long {
        return writeMutex.withLock {
            flushPendingPointsLocked()
            resetAggregatesLocked()
            val id = workoutDao.insert(
                WorkoutEntity(
                    workoutType = workoutType,
                    startTimeMs = startTimeMs,
                    isFinished = false
                )
            )
            activeWorkoutId = id
            workoutStartTimeMs = startTimeMs
            lastPointTimestampMs = 0L
            lastRecordedLat = null
            lastRecordedLon = null
            id
        }
    }

    /**
     * Attach to an existing unfinished workout (crash recovery continue).
     * Rebuilds in-memory sensor aggregates from stored points.
     */
    suspend fun attachToWorkout(workoutId: Long): Boolean {
        return writeMutex.withLock {
            flushPendingPointsLocked()
            resetAggregatesLocked()
            val workout = workoutDao.getById(workoutId) ?: return@withLock false
            if (workout.isFinished) return@withLock false
            val points = trackPointDao.getByWorkoutId(workoutId)
            points.forEach { p ->
                updateAggregatesLocked(p.heartRateBpm, p.cadenceRpm, p.powerWatts, p.stepCadenceSpm, p.stepsTotal)
            }
            activeWorkoutId = workoutId
            workoutStartTimeMs = workout.startTimeMs
            val last = points.lastOrNull()
            lastPointTimestampMs = last?.timestampMs ?: 0L
            lastRecordedLat = last?.latitude
            lastRecordedLon = last?.longitude
            true
        }
    }

    /**
     * Queue a track point for batched insert. Points are flushed when batch size is reached or on stop/flush.
     * To save battery/storage, intermediate points are written only when enough time/distance has passed.
     */
    suspend fun recordTrackPoint(
        timestampMs: Long,
        location: Location,
        speedKmh: Float,
        heartRateBpm: Int?,
        cadenceRpm: Float?,
        powerWatts: Int?,
        force: Boolean = false,
        isAutoPaused: Boolean = false,
        stepsTotal: Int? = null,
        stepCadenceSpm: Int? = null
    ) {
        val workoutId = activeWorkoutId ?: return
        val pointTs = if (timestampMs > 0L) timestampMs else System.currentTimeMillis()

        // Throttle writes: keep sensor aggregates in memory, but persist points less frequently to save battery.
        if (!force) {
            val dt = pointTs - lastPointTimestampMs
            val movedM = distanceFromLastRecorded(location.latitude, location.longitude)
            val minInterval = if (isAutoPaused) MIN_POINT_INTERVAL_PAUSED_MS else MIN_POINT_INTERVAL_MS
            val shouldWrite = (movedM >= MIN_POINT_DISTANCE_M) || (dt >= minInterval)
            if (!shouldWrite) {
                // Still update aggregates opportunistically from live sensor values
                writeMutex.withLock {
                    if (activeWorkoutId == workoutId) updateAggregatesLocked(heartRateBpm, cadenceRpm, powerWatts, stepCadenceSpm, stepsTotal)
                }
                return
            }
        }

        val altitude = if (location.hasAltitude()) location.altitude else null
        val point = TrackPointEntity(
            workoutId = workoutId,
            timestampMs = pointTs,
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = altitude,
            speedKmh = speedKmh,
            heartRateBpm = heartRateBpm,
            cadenceRpm = cadenceRpm,
            powerWatts = powerWatts,
            stepsTotal = stepsTotal,
            stepCadenceSpm = stepCadenceSpm
        )

        writeMutex.withLock {
            val currentId = activeWorkoutId ?: return
            if (currentId != workoutId) return
            pendingPoints.add(point)
            lastPointTimestampMs = pointTs
            lastRecordedLat = location.latitude
            lastRecordedLon = location.longitude
            updateAggregatesLocked(heartRateBpm, cadenceRpm, powerWatts, stepCadenceSpm, stepsTotal)
            if (force || pendingPoints.size >= POINT_BATCH_SIZE) {
                flushPendingPointsLocked()
            }
        }
    }

    suspend fun flushPendingPoints() {
        writeMutex.withLock { flushPendingPointsLocked() }
    }

    suspend fun finishWorkout(
        endTimeMs: Long,
        elapsedTimeMs: Long,
        movingTimeMs: Long,
        distanceMeters: Double,
        avgSpeedKmh: Float,
        maxSpeedKmh: Float,
        elevationGainMeters: Double,
        totalSteps: Int? = null,
        caloriesKcal: Int? = null,
        caloriesSource: String? = null,
        avgPowerWatts: Int? = null,
        maxPowerWatts: Int? = null,
        normalizedPowerWatts: Int? = null,
        intensityFactor: Float? = null,
        trainingStressScore: Float? = null,
        hrZoneSeconds: String? = null,
        powerZoneSeconds: String? = null,
        laps: List<LapEntity> = emptyList()
    ): Long? {
        return writeMutex.withLock {
            val workoutId = activeWorkoutId ?: return@withLock null
            flushPendingPointsLocked()

            val existing = workoutDao.getById(workoutId) ?: return@withLock null
            val avgHr = if (hrCount > 0) (hrSum.toDouble() / hrCount.toDouble()).roundToInt() else null
            val avgCad = if (cadenceCount > 0) (cadenceSum / cadenceCount.toDouble()).toFloat() else null
            val avgPower = if (powerCount > 0) (powerSum.toDouble() / powerCount.toDouble()).roundToInt() else null
            val avgStepCad = if (stepCadenceCount > 0) (stepCadenceSum.toDouble() / stepCadenceCount.toDouble()).roundToInt() else null
            val resolvedSteps = totalSteps ?: maxStepsTotal.takeIf { it > 0 }

            val updated = existing.copy(
                endTimeMs = endTimeMs,
                elapsedTimeMs = elapsedTimeMs,
                movingTimeMs = movingTimeMs,
                distanceMeters = distanceMeters,
                avgSpeedKmh = avgSpeedKmh,
                maxSpeedKmh = maxSpeedKmh,
                avgHeartRateBpm = avgHr,
                maxHeartRateBpm = if (hrCount > 0) hrMax else null,
                avgCadenceRpm = avgCad,
                maxCadenceRpm = if (cadenceCount > 0) cadenceMax else null,
                avgPowerWatts = avgPowerWatts ?: avgPower,
                maxPowerWatts = maxPowerWatts ?: if (powerCount > 0) powerMax else null,
                elevationGainMeters = elevationGainMeters,
                totalSteps = resolvedSteps,
                avgStepCadenceSpm = avgStepCad,
                maxStepCadenceSpm = if (stepCadenceCount > 0) stepCadenceMax else null,
                isFinished = true,
                caloriesKcal = caloriesKcal,
                caloriesSource = caloriesSource,
                normalizedPowerWatts = normalizedPowerWatts,
                intensityFactor = intensityFactor,
                trainingStressScore = trainingStressScore,
                hrZoneSeconds = hrZoneSeconds,
                powerZoneSeconds = powerZoneSeconds
            )
            workoutDao.update(updated)
            try { lapDao.deleteByWorkoutId(workoutId) } catch (_: Exception) {}
            if (laps.isNotEmpty()) try { lapDao.insertAll(laps.map { it.copy(workoutId = workoutId) }) } catch (_: Exception) {}
            activeWorkoutId = null
            workoutStartTimeMs = 0L
            resetAggregatesLocked()
            workoutId
        }
    }

    suspend fun discardActiveWorkout() {
        writeMutex.withLock {
            val id = activeWorkoutId ?: return@withLock
            pendingPoints.clear()
            trackPointDao.deleteByWorkoutId(id)
            workoutDao.deleteById(id)
            activeWorkoutId = null
            workoutStartTimeMs = 0L
            resetAggregatesLocked()
        }
    }

    suspend fun insertWorkout(entity: WorkoutEntity): Long = workoutDao.insert(entity)

    suspend fun getWorkout(workoutId: Long): WorkoutEntity? = workoutDao.getById(workoutId)
    suspend fun getAllWorkouts(): List<WorkoutEntity> = workoutDao.getAll()
    suspend fun getTrackPoints(workoutId: Long): List<TrackPointEntity> = trackPointDao.getByWorkoutId(workoutId)
    suspend fun getLaps(workoutId: Long): List<LapEntity> = lapDao.getByWorkoutId(workoutId)
    suspend fun getLastFinishedWorkout(): WorkoutEntity? = workoutDao.getLastFinished()
    suspend fun getUnfinishedWorkout(): WorkoutEntity? = workoutDao.getUnfinished()
    fun observeWorkout(workoutId: Long): Flow<WorkoutEntity?> = workoutDao.observeById(workoutId)

    suspend fun deleteWorkout(workoutId: Long): Boolean {
        return writeMutex.withLock {
            val existing = workoutDao.getById(workoutId) ?: return@withLock false
            // If deleting the currently active workout, clear in-memory state.
            if (activeWorkoutId == workoutId) {
                pendingPoints.clear()
                activeWorkoutId = null
                workoutStartTimeMs = 0L
                resetAggregatesLocked()
            } else {
                flushPendingPointsLocked()
            }
            try { trackPointDao.deleteByWorkoutId(workoutId) } catch (_: Exception) { }
            try { lapDao.deleteByWorkoutId(workoutId) } catch (_: Exception) { }
            workoutDao.deleteById(workoutId)
            true
        }
    }

    data class RecoveredSummary(
        val elapsedTimeMs: Long,
        val movingTimeMs: Long,
        val distanceMeters: Double,
        val avgSpeedKmh: Float,
        val maxSpeedKmh: Float,
        val elevationGainMeters: Double,
        val avgHeartRateBpm: Int?,
        val maxHeartRateBpm: Int?,
        val avgCadenceRpm: Float?,
        val maxCadenceRpm: Float?,
        val avgPowerWatts: Int?,
        val maxPowerWatts: Int?,
        val totalSteps: Int? = null,
        val avgStepCadenceSpm: Int? = null,
        val maxStepCadenceSpm: Int? = null,
        val endTimeMs: Long = 0L,
        val caloriesKcal: Int? = null,
        val caloriesSource: String? = null,
        val normalizedPowerWatts: Int? = null,
        val intensityFactor: Float? = null,
        val trainingStressScore: Float? = null,
        val hrZoneSeconds: String? = null,
        val powerZoneSeconds: String? = null
    )

    suspend fun computeSummaryFromPoints(workoutId: Long): RecoveredSummary {
        val workout = workoutDao.getById(workoutId) ?: return RecoveredSummary(
            0L, 0L, 0.0, 0f, 0f, 0.0, null, null, null, null, null, null, null, null, null, System.currentTimeMillis()
        )
        return computeSummaryFromPointsInternal(workoutId, workout)
    }

    /** Finalize an unfinished workout (crash recovery "save") using data computed from stored points. */
    suspend fun finalizeRecoveredWorkout(workoutId: Long): Boolean {
        return writeMutex.withLock {
            flushPendingPointsLocked()
            val existing = workoutDao.getById(workoutId) ?: return@withLock false
            if (existing.isFinished) return@withLock true
            val summary = computeSummaryFromPointsInternal(workoutId, existing)
            workoutDao.update(
                existing.copy(
                    endTimeMs = summary.endTimeMs,
                    elapsedTimeMs = summary.elapsedTimeMs,
                    movingTimeMs = summary.movingTimeMs,
                    distanceMeters = summary.distanceMeters,
                    avgSpeedKmh = summary.avgSpeedKmh,
                    maxSpeedKmh = summary.maxSpeedKmh,
                    avgHeartRateBpm = summary.avgHeartRateBpm,
                    maxHeartRateBpm = summary.maxHeartRateBpm,
                    avgCadenceRpm = summary.avgCadenceRpm,
                    maxCadenceRpm = summary.maxCadenceRpm,
                    avgPowerWatts = summary.avgPowerWatts,
                    maxPowerWatts = summary.maxPowerWatts,
                    elevationGainMeters = summary.elevationGainMeters,
                    totalSteps = summary.totalSteps ?: existing.totalSteps,
                    avgStepCadenceSpm = summary.avgStepCadenceSpm ?: existing.avgStepCadenceSpm,
                    maxStepCadenceSpm = summary.maxStepCadenceSpm ?: existing.maxStepCadenceSpm,
                    isFinished = true,
                    caloriesKcal = summary.caloriesKcal ?: existing.caloriesKcal,
                    caloriesSource = summary.caloriesSource ?: existing.caloriesSource,
                    normalizedPowerWatts = summary.normalizedPowerWatts ?: existing.normalizedPowerWatts,
                    intensityFactor = summary.intensityFactor ?: existing.intensityFactor,
                    trainingStressScore = summary.trainingStressScore ?: existing.trainingStressScore,
                    hrZoneSeconds = summary.hrZoneSeconds ?: existing.hrZoneSeconds,
                    powerZoneSeconds = summary.powerZoneSeconds ?: existing.powerZoneSeconds
                )
            )
            if (activeWorkoutId == workoutId) {
                activeWorkoutId = null
                workoutStartTimeMs = 0L
                resetAggregatesLocked()
            }
            true
        }
    }

    private suspend fun computeSummaryFromPointsInternal(workoutId: Long, workout: WorkoutEntity): RecoveredSummary {
        val points = trackPointDao.getByWorkoutId(workoutId)
        if (points.isEmpty()) {
            val now = System.currentTimeMillis()
            val end = workout.endTimeMs ?: now
            val elapsed = if (workout.elapsedTimeMs > 0L) workout.elapsedTimeMs else (end - workout.startTimeMs).coerceAtLeast(0L)
            return RecoveredSummary(
                elapsedTimeMs = elapsed,
                movingTimeMs = if (workout.movingTimeMs > 0L) workout.movingTimeMs else 0L,
                distanceMeters = workout.distanceMeters,
                avgSpeedKmh = workout.avgSpeedKmh,
                maxSpeedKmh = SpeedLimits.clamp(workout.maxSpeedKmh, workout.workoutType),
                elevationGainMeters = workout.elevationGainMeters,
                avgHeartRateBpm = workout.avgHeartRateBpm,
                maxHeartRateBpm = workout.maxHeartRateBpm,
                avgCadenceRpm = workout.avgCadenceRpm,
                maxCadenceRpm = workout.maxCadenceRpm,
                avgPowerWatts = workout.avgPowerWatts,
                maxPowerWatts = workout.maxPowerWatts,
                totalSteps = workout.totalSteps,
                avgStepCadenceSpm = workout.avgStepCadenceSpm,
                maxStepCadenceSpm = workout.maxStepCadenceSpm,
                endTimeMs = end
            )
        }
        var distance = 0.0
        var movingMs = 0L
        var elevGain = 0.0
        var maxSpeed = 0f
        var lastAlt: Double? = null
        var hrSumL = 0L; var hrCnt = 0; var hrMx = 0
        var cadSumD = 0.0; var cadCnt = 0; var cadMx = 0f
        var pwrSumL = 0L; var pwrCnt = 0; var pwrMx = 0
        var stepCadSumL = 0L; var stepCadCnt = 0; var stepCadMx = 0
        var maxSteps = 0
        var prev: TrackPointEntity? = null
        for (p in points) {
            val sp = SpeedLimits.clamp(p.speedKmh ?: 0f, workout.workoutType)
            if (sp > maxSpeed) maxSpeed = sp
            p.heartRateBpm?.takeIf { it > 0 }?.let { hrSumL += it; hrCnt++; if (it > hrMx) hrMx = it }
            p.cadenceRpm?.takeIf { it >= 0f }?.let { cadSumD += it.toDouble(); cadCnt++; if (it > cadMx) cadMx = it }
            p.powerWatts?.takeIf { it >= 0 }?.let { pwrSumL += it; pwrCnt++; if (it > pwrMx) pwrMx = it }
            p.stepCadenceSpm?.takeIf { it > 0 }?.let { stepCadSumL += it; stepCadCnt++; if (it > stepCadMx) stepCadMx = it }
            p.stepsTotal?.takeIf { it > maxSteps }?.let { maxSteps = it }
            if (prev != null) {
                val dt = (p.timestampMs - prev.timestampMs).coerceAtLeast(0L)
                val seg = haversineMeters(prev.latitude, prev.longitude, p.latitude, p.longitude)
                val segSpeedKmh = if (dt > 0) ((seg / (dt / 1000.0)) * 3.6).toFloat() else sp
                val effective = max(sp, segSpeedKmh)
                if (effective >= AUTO_RESUME_KMH_FOR_SUMMARY && seg < MAX_SEGMENT_M_FOR_SUMMARY) {
                    movingMs += dt; distance += seg
                } else if (seg in 2.0..MAX_SEGMENT_M_FOR_SUMMARY) {
                    distance += seg; movingMs += dt
                }
            }
            val alt = p.altitudeMeters
            if (alt != null) {
                val la = lastAlt
                if (la == null) lastAlt = alt else {
                    val dAlt = alt - la
                    if (dAlt in 1.0..50.0) { elevGain += dAlt; lastAlt = alt }
                    else if (dAlt <= -1.0) lastAlt = alt
                }
            }
            prev = p
        }
        val end = points.last().timestampMs.coerceAtLeast(workout.startTimeMs)
        val elapsed = (end - workout.startTimeMs).coerceAtLeast(0L)
        val moving = movingMs.coerceIn(0L, elapsed)
        val avgSpeed = if (moving > 0L) ((distance / (moving / 1000.0)) * 3.6).toFloat() else 0f
        // Recompute Stage 9 analytics from stored points if missing (crash recovery / legacy rows).
        val prefs = context.getSharedPreferences(CyclingSettings.PREFS_NAME, Context.MODE_PRIVATE)
        val weightKg = prefs.getFloat(CyclingSettings.KEY_ATHLETE_WEIGHT_KG, CyclingSettings.DEFAULT_WEIGHT_KG)
        val maxHr = prefs.getInt(CyclingSettings.KEY_ATHLETE_MAX_HR, CyclingSettings.DEFAULT_MAX_HR)
        val restingHr = prefs.getInt(CyclingSettings.KEY_ATHLETE_RESTING_HR, CyclingSettings.DEFAULT_RESTING_HR)
        val ftp = prefs.getInt(CyclingSettings.KEY_ATHLETE_FTP_WATTS, CyclingSettings.DEFAULT_FTP_WATTS)
        val age = prefs.getInt(CyclingSettings.KEY_ATHLETE_AGE_YEARS, CyclingSettings.DEFAULT_AGE_YEARS)
        val isMale = prefs.getBoolean(CyclingSettings.KEY_ATHLETE_IS_MALE, CyclingSettings.DEFAULT_IS_MALE)

        val hrZonesArr = TrainingAnalytics.decodeZoneSeconds(workout.hrZoneSeconds, 5)
        val pwrZonesArr = TrainingAnalytics.decodeZoneSeconds(workout.powerZoneSeconds, 7)
        val hasHrZones = hrZonesArr.any { it > 0 }
        val hasPwrZones = pwrZonesArr.any { it > 0 }
        if ((!hasHrZones || !hasPwrZones) && points.size >= 2) {
            var prevP: TrackPointEntity? = null
            for (p in points) {
                val dtSec = if (prevP != null) ((p.timestampMs - prevP.timestampMs).coerceAtLeast(0L) / 1000L).toInt().coerceAtMost(30) else 0
                prevP = p
                if (dtSec <= 0) continue
                val hr = p.heartRateBpm
                if (!hasHrZones && hr != null && hr > 0) {
                    val idx = TrainingAnalytics.hrZoneIndex(hr, maxHr, restingHr)
                    if (idx in hrZonesArr.indices) hrZonesArr[idx] += dtSec
                }
                val pw = p.powerWatts
                if (!hasPwrZones && pw != null && pw >= 0) {
                    val idx = TrainingAnalytics.powerZoneIndex(pw, ftp)
                    if (idx in pwrZonesArr.indices) pwrZonesArr[idx] += dtSec
                }
            }
        }

        val avgPower = if (pwrCnt > 0) (pwrSumL.toDouble() / pwrCnt).roundToInt() else null
        val avgHr = if (hrCnt > 0) (hrSumL.toDouble() / hrCnt).roundToInt() else null
        val powerSamples = if (workout.normalizedPowerWatts == null) points.mapNotNull { it.powerWatts?.coerceAtLeast(0) }.toIntArray() else IntArray(0)
        val np = workout.normalizedPowerWatts ?: TrainingAnalytics.normalizedPower(powerSamples)
        val iff = workout.intensityFactor ?: TrainingAnalytics.intensityFactor(np, ftp)
        val movingSec = (moving / 1000L).toInt()
        val tssPower = workout.trainingStressScore ?: TrainingAnalytics.tss(movingSec, np, iff, ftp)
        val hrTssVal = if (hrZonesArr.any { it > 0 }) TrainingAnalytics.hrTss(hrZonesArr) else null
        val tssFinal = tssPower ?: hrTssVal ?: workout.trainingStressScore
        val caloriesPower = TrainingAnalytics.caloriesFromPower(avgPower, movingSec)
        val caloriesHr = TrainingAnalytics.caloriesFromHr(avgHr, weightKg, age, isMale, movingSec)
        val (calKcal, calSrc) = when {
            workout.caloriesKcal != null && workout.caloriesSource != null -> workout.caloriesKcal to workout.caloriesSource
            caloriesPower != null -> caloriesPower to "POWER"
            caloriesHr != null -> caloriesHr to "HR"
            else -> null to null
        }

        return RecoveredSummary(
            elapsedTimeMs = elapsed,
            movingTimeMs = moving,
            distanceMeters = distance,
            avgSpeedKmh = avgSpeed,
            maxSpeedKmh = maxSpeed,
            elevationGainMeters = elevGain,
            avgHeartRateBpm = avgHr,
            maxHeartRateBpm = if (hrCnt > 0) hrMx else null,
            avgCadenceRpm = if (cadCnt > 0) (cadSumD / cadCnt).toFloat() else null,
            maxCadenceRpm = if (cadCnt > 0) cadMx else null,
            avgPowerWatts = avgPower,
            maxPowerWatts = if (pwrCnt > 0) pwrMx else null,
            totalSteps = if (maxSteps > 0) maxSteps else null,
            avgStepCadenceSpm = if (stepCadCnt > 0) (stepCadSumL.toDouble() / stepCadCnt).roundToInt() else null,
            maxStepCadenceSpm = if (stepCadCnt > 0) stepCadMx else null,
            endTimeMs = end,
            caloriesKcal = calKcal,
            caloriesSource = calSrc,
            normalizedPowerWatts = np,
            intensityFactor = iff,
            trainingStressScore = tssFinal,
            hrZoneSeconds = if (hrZonesArr.any { it > 0 }) TrainingAnalytics.encodeZoneSeconds(hrZonesArr) else workout.hrZoneSeconds,
            powerZoneSeconds = if (pwrZonesArr.any { it > 0 }) TrainingAnalytics.encodeZoneSeconds(pwrZonesArr) else workout.powerZoneSeconds
        )
    }

    /**
     * Generate workout file, save to Downloads/Bike_, and return a share Intent (FileProvider) if possible.
     */
    suspend fun exportWorkoutAndShare(workoutId: Long, format: ExportFormat): ExportResult {
        val workout = workoutDao.getById(workoutId) ?: return ExportResult.Error(context.getString(com.example.bike_.R.string.export_no_workout))
        val points = trackPointDao.getByWorkoutId(workoutId)
        val laps = lapDao.getByWorkoutId(workoutId)
        val exporter = WorkoutExporter()
        val bytes = exporter.export(workout, points, laps, format)
        val fileName = WorkoutExporter.buildFileName(workout, format.extension)

        val saved = saveToDownloads(fileName, bytes, format.mimeType)
            ?: return ExportResult.Error(context.getString(com.example.bike_.R.string.error_cannot_save_downloads))

        val shareIntent = buildShareIntent(saved.uri, format.mimeType)
        return ExportResult.Success(saved.displayPath, shareIntent)
    }

    suspend fun exportWorkoutToGpxAndShare(workoutId: Long): ExportResult =
        exportWorkoutAndShare(workoutId, ExportFormat.GPX)

    fun launchFlush() {
        ioScope.launch { flushPendingPoints() }
    }

    private suspend fun flushPendingPointsLocked() {
        if (pendingPoints.isEmpty()) return
        val batch = ArrayList(pendingPoints)
        pendingPoints.clear()
        trackPointDao.insertAll(batch)
    }

    private fun updateAggregatesLocked(hr: Int?, cadence: Float?, power: Int?, stepCadenceSpm: Int? = null, stepsTotal: Int? = null) {
        if (hr != null && hr > 0) {
            hrSum += hr
            hrCount += 1
            if (hr > hrMax) hrMax = hr
        }
        if (cadence != null && cadence >= 0f) {
            cadenceSum += cadence.toDouble()
            cadenceCount += 1
            if (cadence > cadenceMax) cadenceMax = cadence
        }
        if (power != null && power >= 0) {
            powerSum += power
            powerCount += 1
            if (power > powerMax) powerMax = power
        }
        if (stepCadenceSpm != null && stepCadenceSpm > 0) {
            stepCadenceSum += stepCadenceSpm
            stepCadenceCount += 1
            if (stepCadenceSpm > stepCadenceMax) stepCadenceMax = stepCadenceSpm
        }
        if (stepsTotal != null && stepsTotal > maxStepsTotal) {
            maxStepsTotal = stepsTotal
        }
    }

    private fun resetAggregatesLocked() {
        hrSum = 0L; hrCount = 0; hrMax = 0
        cadenceSum = 0.0; cadenceCount = 0; cadenceMax = 0f
        powerSum = 0L; powerCount = 0; powerMax = 0
        stepCadenceSum = 0L; stepCadenceCount = 0; stepCadenceMax = 0
        maxStepsTotal = 0
        pendingPoints.clear()
        lastRecordedLat = null
        lastRecordedLon = null
    }

    private fun distanceFromLastRecorded(lat: Double, lon: Double): Double {
        val la = lastRecordedLat ?: return Double.MAX_VALUE
        val lo = lastRecordedLon ?: return Double.MAX_VALUE
        return haversineMeters(la, lo, lat, lon)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt((1 - a).coerceAtLeast(0.0)))
        return r * c
    }

    private data class SavedFile(val uri: android.net.Uri, val displayPath: String)

    private fun saveToDownloads(fileName: String, bytes: ByteArray, mimeType: String): SavedFile? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Bike_")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: run {
                    resolver.delete(uri, null, null)
                    return null
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                SavedFile(uri, "Downloads/Bike_/$fileName")
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Bike_")
                if (!dir.exists()) dir.mkdirs()
                val outFile = File(dir, fileName)
                FileOutputStream(outFile).use { it.write(bytes) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
                SavedFile(uri, outFile.absolutePath)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildShareIntent(uri: android.net.Uri, mimeType: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(com.example.bike_.R.string.detail_export_share)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    sealed class ExportResult {
        data class Success(val path: String, val shareIntent: Intent) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    companion object {
        // Battery-optimized batching: write fewer points, flush in larger chunks.
        private const val POINT_BATCH_SIZE = 40
        private const val MIN_POINT_INTERVAL_MS = 5000L
        private const val MIN_POINT_INTERVAL_PAUSED_MS = 20000L
        private const val MIN_POINT_DISTANCE_M = 10.0
        private const val AUTO_RESUME_KMH_FOR_SUMMARY = 2.5f
        private const val MAX_SEGMENT_M_FOR_SUMMARY = 200.0
    }
}
