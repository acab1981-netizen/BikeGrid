package com.example.bike_.data.export

import com.example.bike_.data.db.entity.LapEntity
import com.example.bike_.data.db.entity.TrackPointEntity
import com.example.bike_.data.db.entity.WorkoutEntity
import org.junit.Assert.*
import org.junit.Test

class WorkoutExporterTest {

    @Test
    fun gpx_containsExtensionsAndBasicTags() {
        val workout = WorkoutEntity(
            id = 1, startTimeMs = 1700000000000L, endTimeMs = 1700003600000L,
            distanceMeters = 10000.0, avgSpeedKmh = 25f, maxSpeedKmh = 40f, workoutType = "CYCLING"
        )
        val points = listOf(
            TrackPointEntity(workoutId=1, timestampMs=1700000000000L, latitude=55.0, longitude=37.0, altitudeMeters=150.0, speedKmh=25f, heartRateBpm=140, cadenceRpm=80f, powerWatts=200),
            TrackPointEntity(workoutId=1, timestampMs=1700000001000L, latitude=55.001, longitude=37.001, altitudeMeters=151.0, speedKmh=26f, heartRateBpm=142, cadenceRpm=82f, powerWatts=210),
        )
        val gpx = WorkoutExporter().exportToGpx(workout, points)
        assertTrue(gpx.contains("<gpx"))
        assertTrue(gpx.contains("<trkpt"))
        assertTrue(gpx.contains("<ele>"))
        assertTrue(gpx.contains("<time>"))
        assertTrue(gpx.contains("gpxtpx:hr"))
        assertTrue(gpx.contains("gpxtpx:cad"))
        assertTrue(gpx.contains("gpxtpx:watts") || gpx.contains("<power>"))
        assertTrue(gpx.contains(">140<"))
        assertTrue(gpx.contains(">80<"))
        assertTrue(gpx.contains(">200<"))
    }

    @Test
    fun fit_export_hasHeaderAndData() {
        val workout = WorkoutEntity(id = 1, startTimeMs = 1700000000000L, endTimeMs = 1700003600000L, distanceMeters = 10000.0, avgSpeedKmh = 25f, maxSpeedKmh = 40f, workoutType = "CYCLING")
        val points = listOf(
            TrackPointEntity(workoutId=1, timestampMs=1700000000000L, latitude=55.0, longitude=37.0, heartRateBpm=140, cadenceRpm=80f, powerWatts=200),
        )
        val bytes = WorkoutExporter().exportToFit(workout, points, emptyList())
        assertTrue(bytes.size > 14)
        // FIT header signature .FIT at 8
        assertEquals('.'.code.toByte(), bytes[8])
        assertEquals('F'.code.toByte(), bytes[9])
        assertEquals('I'.code.toByte(), bytes[10])
        assertEquals('T'.code.toByte(), bytes[11])
    }
}
