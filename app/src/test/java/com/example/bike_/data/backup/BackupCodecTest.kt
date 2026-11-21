package com.example.bike_.data.backup

import com.example.bike_.data.db.entity.LapEntity
import com.example.bike_.data.db.entity.SavedRouteEntity
import com.example.bike_.data.db.entity.TrackPointEntity
import com.example.bike_.data.db.entity.WorkoutEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class BackupCodecTest {

    private fun sampleData() = BackupCodec.BackupData(
        workouts = listOf(
            WorkoutEntity(
                id = 42L,
                workoutType = "RUNNING",
                startTimeMs = 1_700_000_000_000L,
                endTimeMs = 1_700_000_600_000L,
                elapsedTimeMs = 600_000L,
                movingTimeMs = 540_000L,
                distanceMeters = 3000.0,
                avgSpeedKmh = 18.5f,
                maxSpeedKmh = 29.4f,
                avgHeartRateBpm = 155,
                maxPowerWatts = 410,
                isFinished = true,
                caloriesKcal = 520,
                caloriesSource = "POWER",
                intensityFactor = 0.88f,
                trainingStressScore = 77f,
                hrZoneSeconds = "10,20,30,40,50",
                powerZoneSeconds = "1,2,3,4,5,6,7"
            )
        ),
        points = listOf(
            TrackPointEntity(
                id = 7L,
                workoutId = 42L,
                timestampMs = 1_700_000_001_000L,
                latitude = 55.7558,
                longitude = 37.6173,
                altitudeMeters = 150.5,
                speedKmh = 19.2f,
                heartRateBpm = 160,
                cadenceRpm = 175f,
                powerWatts = 300,
                stepsTotal = 1200,
                stepCadenceSpm = 170
            )
        ),
        laps = listOf(
            LapEntity(
                id = 3L,
                workoutId = 42L,
                lapNumber = 1,
                startTimeMs = 1_700_000_000_000L,
                endTimeMs = 1_700_000_300_000L,
                durationSec = 300,
                distanceMeters = 1500.0,
                avgSpeedKmh = 18f,
                avgHeartRateBpm = 150,
                normalizedPowerWatts = 320
            )
        ),
        routes = listOf(
            SavedRouteEntity(
                id = 5L,
                name = "Park loop",
                fileName = "park.gpx",
                createdAtMs = 1_699_000_000_000L,
                distanceMeters = 10_000.0,
                elevationGainMeters = 120.0,
                pointsJson = "[{\"lat\":1.0,\"lon\":2.0}]"
            )
        ),
        settings = JSONObject().apply {
            put("athlete_weight_kg", JSONObject().put("t", "f").put("v", 68.5))
            put("athlete_max_hr", JSONObject().put("t", "i").put("v", 192))
        }
    )

    @Test
    fun roundTrip_preservesAllEntitiesAndSettings() {
        val original = sampleData()
        val decoded = BackupCodec.decode(BackupCodec.encode(original))

        assertEquals(1, decoded.workouts.size)
        val w = decoded.workouts[0]
        assertEquals(42L, w.id)
        assertEquals("RUNNING", w.workoutType)
        assertEquals(1_700_000_600_000L, w.endTimeMs)
        assertEquals(18.5f, w.avgSpeedKmh, 0.001f)
        assertEquals(155, w.avgHeartRateBpm)
        assertEquals(410, w.maxPowerWatts)
        assertTrue(w.isFinished)
        assertEquals(520, w.caloriesKcal)
        assertEquals("POWER", w.caloriesSource)
        assertEquals(0.88f, w.intensityFactor ?: -1f, 0.001f)
        assertEquals("1,2,3,4,5,6,7", w.powerZoneSeconds)

        assertEquals(1, decoded.points.size)
        val p = decoded.points[0]
        assertEquals(42L, p.workoutId)
        assertEquals(55.7558, p.latitude, 0.00001)
        assertEquals(19.2f, p.speedKmh ?: -1f, 0.001f)
        assertEquals(160, p.heartRateBpm)
        assertEquals(1200, p.stepsTotal)

        assertEquals(1, decoded.laps.size)
        assertEquals("Park loop", decoded.routes[0].name)
        assertEquals("park.gpx", decoded.routes[0].fileName)

        assertEquals("f", decoded.settings.getJSONObject("athlete_weight_kg").getString("t"))
        assertEquals(192, decoded.settings.getJSONObject("athlete_max_hr").getInt("v"))
    }

    @Test
    fun roundTrip_preservesNullsAsNulls() {
        val data = BackupCodec.BackupData(
            workouts = listOf(WorkoutEntity(startTimeMs = 1L, avgHeartRateBpm = null, caloriesSource = null)),
            points = listOf(TrackPointEntity(workoutId = 1L, timestampMs = 2L, latitude = 0.0, longitude = 0.0, speedKmh = null))
        )
        val w = BackupCodec.decode(BackupCodec.encode(data)).workouts[0]
        val p = BackupCodec.decode(BackupCodec.encode(data)).points[0]
        assertNull(w.endTimeMs)
        assertNull(w.avgHeartRateBpm)
        assertNull(w.caloriesSource)
        assertNull(p.speedKmh)
        assertNull(p.altitudeMeters)
    }

    @Test
    fun decode_rejectsForeignAndFutureFormats() {
        assertThrows(BackupCodec.InvalidBackupException::class.java) {
            BackupCodec.decode("""{"format":"something_else","version":1}""")
        }
        assertThrows(BackupCodec.InvalidBackupException::class.java) {
            BackupCodec.decode("""{"format":"bike_backup","version":99}""")
        }
        assertThrows(BackupCodec.InvalidBackupException::class.java) {
            BackupCodec.decode("not json at all")
        }
    }

    @Test
    fun decode_missingArraysAreTreatedAsEmpty() {
        val decoded = BackupCodec.decode("""{"format":"bike_backup","version":1}""")
        assertTrue(decoded.workouts.isEmpty())
        assertTrue(decoded.points.isEmpty())
        assertTrue(decoded.settings.length() == 0)
    }
}
