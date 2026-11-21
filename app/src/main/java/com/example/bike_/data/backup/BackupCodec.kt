package com.example.bike_.data.backup

import com.example.bike_.data.db.entity.LapEntity
import com.example.bike_.data.db.entity.SavedRouteEntity
import com.example.bike_.data.db.entity.TrackPointEntity
import com.example.bike_.data.db.entity.WorkoutEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure (Android-independent) serializer for the full app backup file.
 * Kept separate from [BackupManager] so the file format can be unit-tested.
 */
object BackupCodec {
    const val FORMAT = "bike_backup"
    const val VERSION = 1

    data class BackupData(
        val workouts: List<WorkoutEntity> = emptyList(),
        val points: List<TrackPointEntity> = emptyList(),
        val laps: List<LapEntity> = emptyList(),
        val routes: List<SavedRouteEntity> = emptyList(),
        val settings: JSONObject = JSONObject()
    )

    class InvalidBackupException(message: String) : IllegalArgumentException(message)

    fun encode(data: BackupData): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("exportedAtMs", System.currentTimeMillis())
        root.put("workouts", toArray(data.workouts) { workoutToJson(it) })
        root.put("points", toArray(data.points) { pointToJson(it) })
        root.put("laps", toArray(data.laps) { lapToJson(it) })
        root.put("routes", toArray(data.routes) { routeToJson(it) })
        root.put("settings", data.settings)
        return root.toString()
    }

    fun decode(json: String): BackupData {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw InvalidBackupException("Malformed JSON: ${e.message}")
        }
        if (root.optString("format") != FORMAT) {
            throw InvalidBackupException("Not a Bike backup file")
        }
        val version = root.optInt("version", -1)
        if (version < 1 || version > VERSION) {
            throw InvalidBackupException("Unsupported backup version: $version")
        }
        // Parse everything before any data is written, so a corrupt file cannot leave a half-restored DB.
        val workouts = fromArray(root.optJSONArray("workouts")) { jsonToWorkout(it) }
        val points = fromArray(root.optJSONArray("points")) { jsonToPoint(it) }
        val laps = fromArray(root.optJSONArray("laps")) { jsonToLap(it) }
        val routes = fromArray(root.optJSONArray("routes")) { jsonToRoute(it) }
        val settings = root.optJSONObject("settings") ?: JSONObject()
        return BackupData(workouts, points, laps, routes, settings)
    }

    private fun <T> toArray(items: List<T>, serialize: (T) -> JSONObject): JSONArray {
        val arr = JSONArray()
        items.forEach { arr.put(serialize(it)) }
        return arr
    }

    private fun <T> fromArray(arr: JSONArray?, deserialize: (JSONObject) -> T): List<T> {
        if (arr == null) return emptyList()
        val out = ArrayList<T>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: throw InvalidBackupException("Invalid record at index $i")
            out.add(deserialize(obj))
        }
        return out
    }

    // ---- JSONObject helpers ----

    private fun JSONObject.optField(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)
    private fun JSONObject.doubleOrNull(key: String): Double? = if (isNull(key)) null else optDouble(key)
    private fun JSONObject.floatOrNull(key: String): Float? = doubleOrNull(key)?.toFloat()
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key)

    // ---- WorkoutEntity ----

    private fun workoutToJson(w: WorkoutEntity) = JSONObject().apply {
        put("id", w.id)
        put("workoutType", w.workoutType)
        put("startTimeMs", w.startTimeMs)
        optField("endTimeMs", w.endTimeMs)
        put("elapsedTimeMs", w.elapsedTimeMs)
        put("movingTimeMs", w.movingTimeMs)
        put("distanceMeters", w.distanceMeters)
        put("avgSpeedKmh", w.avgSpeedKmh.toDouble())
        put("maxSpeedKmh", w.maxSpeedKmh.toDouble())
        optField("avgHeartRateBpm", w.avgHeartRateBpm)
        optField("maxHeartRateBpm", w.maxHeartRateBpm)
        optField("avgCadenceRpm", w.avgCadenceRpm?.toDouble())
        optField("maxCadenceRpm", w.maxCadenceRpm?.toDouble())
        optField("avgPowerWatts", w.avgPowerWatts)
        optField("maxPowerWatts", w.maxPowerWatts)
        put("elevationGainMeters", w.elevationGainMeters)
        optField("totalSteps", w.totalSteps)
        optField("avgStepCadenceSpm", w.avgStepCadenceSpm)
        optField("maxStepCadenceSpm", w.maxStepCadenceSpm)
        put("isFinished", w.isFinished)
        optField("caloriesKcal", w.caloriesKcal)
        optField("caloriesSource", w.caloriesSource)
        optField("normalizedPowerWatts", w.normalizedPowerWatts)
        optField("intensityFactor", w.intensityFactor?.toDouble())
        optField("trainingStressScore", w.trainingStressScore?.toDouble())
        optField("hrZoneSeconds", w.hrZoneSeconds)
        optField("powerZoneSeconds", w.powerZoneSeconds)
    }

    private fun jsonToWorkout(o: JSONObject) = WorkoutEntity(
        id = o.optLong("id"),
        workoutType = o.optString("workoutType", "CYCLING"),
        startTimeMs = o.optLong("startTimeMs"),
        endTimeMs = o.longOrNull("endTimeMs"),
        elapsedTimeMs = o.optLong("elapsedTimeMs"),
        movingTimeMs = o.optLong("movingTimeMs"),
        distanceMeters = o.optDouble("distanceMeters"),
        avgSpeedKmh = o.optDouble("avgSpeedKmh").toFloat(),
        maxSpeedKmh = o.optDouble("maxSpeedKmh").toFloat(),
        avgHeartRateBpm = o.intOrNull("avgHeartRateBpm"),
        maxHeartRateBpm = o.intOrNull("maxHeartRateBpm"),
        avgCadenceRpm = o.floatOrNull("avgCadenceRpm"),
        maxCadenceRpm = o.floatOrNull("maxCadenceRpm"),
        avgPowerWatts = o.intOrNull("avgPowerWatts"),
        maxPowerWatts = o.intOrNull("maxPowerWatts"),
        elevationGainMeters = o.optDouble("elevationGainMeters"),
        totalSteps = o.intOrNull("totalSteps"),
        avgStepCadenceSpm = o.intOrNull("avgStepCadenceSpm"),
        maxStepCadenceSpm = o.intOrNull("maxStepCadenceSpm"),
        isFinished = o.optBoolean("isFinished"),
        caloriesKcal = o.intOrNull("caloriesKcal"),
        caloriesSource = o.stringOrNull("caloriesSource"),
        normalizedPowerWatts = o.intOrNull("normalizedPowerWatts"),
        intensityFactor = o.floatOrNull("intensityFactor"),
        trainingStressScore = o.floatOrNull("trainingStressScore"),
        hrZoneSeconds = o.stringOrNull("hrZoneSeconds"),
        powerZoneSeconds = o.stringOrNull("powerZoneSeconds")
    )

    // ---- TrackPointEntity ----

    private fun pointToJson(p: TrackPointEntity) = JSONObject().apply {
        put("id", p.id)
        put("workoutId", p.workoutId)
        put("timestampMs", p.timestampMs)
        put("latitude", p.latitude)
        put("longitude", p.longitude)
        optField("altitudeMeters", p.altitudeMeters)
        optField("speedKmh", p.speedKmh?.toDouble())
        optField("heartRateBpm", p.heartRateBpm)
        optField("cadenceRpm", p.cadenceRpm?.toDouble())
        optField("powerWatts", p.powerWatts)
        optField("stepsTotal", p.stepsTotal)
        optField("stepCadenceSpm", p.stepCadenceSpm)
    }

    private fun jsonToPoint(o: JSONObject) = TrackPointEntity(
        id = o.optLong("id"),
        workoutId = o.optLong("workoutId"),
        timestampMs = o.optLong("timestampMs"),
        latitude = o.optDouble("latitude"),
        longitude = o.optDouble("longitude"),
        altitudeMeters = o.doubleOrNull("altitudeMeters"),
        speedKmh = o.floatOrNull("speedKmh"),
        heartRateBpm = o.intOrNull("heartRateBpm"),
        cadenceRpm = o.floatOrNull("cadenceRpm"),
        powerWatts = o.intOrNull("powerWatts"),
        stepsTotal = o.intOrNull("stepsTotal"),
        stepCadenceSpm = o.intOrNull("stepCadenceSpm")
    )

    // ---- LapEntity ----

    private fun lapToJson(l: LapEntity) = JSONObject().apply {
        put("id", l.id)
        put("workoutId", l.workoutId)
        put("lapNumber", l.lapNumber)
        put("startTimeMs", l.startTimeMs)
        put("endTimeMs", l.endTimeMs)
        put("durationSec", l.durationSec)
        put("distanceMeters", l.distanceMeters)
        put("avgSpeedKmh", l.avgSpeedKmh.toDouble())
        optField("avgHeartRateBpm", l.avgHeartRateBpm)
        optField("maxHeartRateBpm", l.maxHeartRateBpm)
        optField("avgPowerWatts", l.avgPowerWatts)
        optField("normalizedPowerWatts", l.normalizedPowerWatts)
    }

    private fun jsonToLap(o: JSONObject) = LapEntity(
        id = o.optLong("id"),
        workoutId = o.optLong("workoutId"),
        lapNumber = o.optInt("lapNumber"),
        startTimeMs = o.optLong("startTimeMs"),
        endTimeMs = o.optLong("endTimeMs"),
        durationSec = o.optInt("durationSec"),
        distanceMeters = o.optDouble("distanceMeters"),
        avgSpeedKmh = o.optDouble("avgSpeedKmh").toFloat(),
        avgHeartRateBpm = o.intOrNull("avgHeartRateBpm"),
        maxHeartRateBpm = o.intOrNull("maxHeartRateBpm"),
        avgPowerWatts = o.intOrNull("avgPowerWatts"),
        normalizedPowerWatts = o.intOrNull("normalizedPowerWatts")
    )

    // ---- SavedRouteEntity ----

    private fun routeToJson(r: SavedRouteEntity) = JSONObject().apply {
        put("id", r.id)
        put("name", r.name)
        optField("fileName", r.fileName)
        put("createdAtMs", r.createdAtMs)
        put("distanceMeters", r.distanceMeters)
        put("elevationGainMeters", r.elevationGainMeters)
        put("pointsJson", r.pointsJson)
    }

    private fun jsonToRoute(o: JSONObject) = SavedRouteEntity(
        id = o.optLong("id"),
        name = o.optString("name"),
        fileName = o.stringOrNull("fileName"),
        createdAtMs = o.optLong("createdAtMs"),
        distanceMeters = o.optDouble("distanceMeters"),
        elevationGainMeters = o.optDouble("elevationGainMeters"),
        pointsJson = o.optString("pointsJson")
    )
}
