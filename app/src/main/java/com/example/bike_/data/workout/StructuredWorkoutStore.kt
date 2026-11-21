package com.example.bike_.data.workout

import android.content.Context
import com.example.bike_.domain.workout.StepType
import com.example.bike_.domain.workout.StructuredWorkout
import com.example.bike_.domain.workout.StructuredWorkoutEngine
import com.example.bike_.domain.workout.TargetType
import com.example.bike_.domain.workout.WorkoutStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class StructuredWorkoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("structured_workouts", Context.MODE_PRIVATE)
    private val _workouts = MutableStateFlow<List<StructuredWorkout>>(emptyList())
    val workouts: StateFlow<List<StructuredWorkout>> = _workouts.asStateFlow()

    init {
        val loaded = load()
        _workouts.value = loaded
    }

    fun getById(id: Long): StructuredWorkout? = _workouts.value.firstOrNull { it.id == id }

    fun save(workout: StructuredWorkout, ftpWatts: Int = 200): StructuredWorkout {
        val list = _workouts.value.toMutableList()
        val id = if (workout.id <= 0L) (System.currentTimeMillis() + (0..999).random()) else workout.id
        val withId = workout.copy(id = id, estimatedTss = workout.estimatedTss ?: StructuredWorkoutEngine.estimateTss(workout, ftpWatts))
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) list[idx] = withId else list.add(0, withId)
        persist(list)
        return withId
    }

    fun delete(id: Long) {
        persist(_workouts.value.filterNot { it.id == id })
    }

    private fun persist(list: List<StructuredWorkout>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_WORKOUTS, arr.toString()).apply()
        _workouts.value = list
    }

    private fun load(): List<StructuredWorkout> {
        val raw = prefs.getString(KEY_WORKOUTS, null)
        if (raw.isNullOrBlank()) {
            val builtins = builtinWorkouts()
            prefs.edit().putString(KEY_WORKOUTS, JSONArray().apply { builtins.forEach { put(it.toJson()) } }.toString()).apply()
            return builtins
        }
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<StructuredWorkout>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(o.toWorkout())
            }
            if (out.isEmpty()) {
                val builtins = builtinWorkouts()
                prefs.edit().putString(KEY_WORKOUTS, JSONArray().apply { builtins.forEach { put(it.toJson()) } }.toString()).apply()
                builtins
            } else out
        } catch (_: Exception) {
            val builtins = builtinWorkouts()
            prefs.edit().putString(KEY_WORKOUTS, JSONArray().apply { builtins.forEach { put(it.toJson()) } }.toString()).apply()
            builtins
        }
    }

    private fun builtinWorkouts(): List<StructuredWorkout> = listOf(
        StructuredWorkout(
            id = 1L, title = "Recovery Spin 45 min", description = "Easy Z1-Z2 recovery ride", source = "BUILTIN",
            estimatedTss = 28,
            steps = listOf(
                WorkoutStep(StepType.WARMUP, 10 * 60, TargetType.POWER_FTP, 45, 55),
                WorkoutStep(StepType.WORK, 30 * 60, TargetType.POWER_FTP, 55, 65),
                WorkoutStep(StepType.COOLDOWN, 5 * 60, TargetType.POWER_FTP, 45, 55)
            )
        ),
        StructuredWorkout(
            id = 2L, title = "Endurance Z2 90 min", description = "Long aerobic endurance", source = "BUILTIN",
            estimatedTss = 62,
            steps = listOf(
                WorkoutStep(StepType.WARMUP, 15 * 60, TargetType.POWER_FTP, 55, 65),
                WorkoutStep(StepType.WORK, 65 * 60, TargetType.POWER_FTP, 65, 75),
                WorkoutStep(StepType.COOLDOWN, 10 * 60, TargetType.POWER_FTP, 50, 60)
            )
        ),
        StructuredWorkout(
            id = 3L, title = "Sweet Spot 3x10 min", description = "88-94% FTP with recovery", source = "BUILTIN",
            estimatedTss = 74,
            steps = listOf(
                WorkoutStep(StepType.WARMUP, 10 * 60, TargetType.POWER_FTP, 55, 70),
                WorkoutStep(StepType.WORK, 10 * 60, TargetType.POWER_FTP, 88, 94, repeat = 1),
                WorkoutStep(StepType.RECOVERY, 5 * 60, TargetType.POWER_FTP, 50, 60),
                WorkoutStep(StepType.WORK, 10 * 60, TargetType.POWER_FTP, 88, 94),
                WorkoutStep(StepType.RECOVERY, 5 * 60, TargetType.POWER_FTP, 50, 60),
                WorkoutStep(StepType.WORK, 10 * 60, TargetType.POWER_FTP, 88, 94),
                WorkoutStep(StepType.COOLDOWN, 10 * 60, TargetType.POWER_FTP, 45, 55)
            )
        ),
        StructuredWorkout(
            id = 4L, title = "FTP 2x20 min", description = "Threshold intervals 95-105% FTP", source = "BUILTIN",
            estimatedTss = 88,
            steps = listOf(
                WorkoutStep(StepType.WARMUP, 15 * 60, TargetType.POWER_FTP, 55, 70),
                WorkoutStep(StepType.WORK, 20 * 60, TargetType.POWER_FTP, 95, 105),
                WorkoutStep(StepType.RECOVERY, 5 * 60, TargetType.POWER_FTP, 45, 55),
                WorkoutStep(StepType.WORK, 20 * 60, TargetType.POWER_FTP, 95, 105),
                WorkoutStep(StepType.COOLDOWN, 10 * 60, TargetType.POWER_FTP, 45, 55)
            )
        ),
        StructuredWorkout(
            id = 5L, title = "VO2 Max 5x3 min", description = "Hard 110-120% FTP intervals", source = "BUILTIN",
            estimatedTss = 79,
            steps = listOf(
                WorkoutStep(StepType.WARMUP, 15 * 60, TargetType.POWER_FTP, 55, 70),
                WorkoutStep(StepType.WORK, 3 * 60, TargetType.POWER_FTP, 110, 120),
                WorkoutStep(StepType.RECOVERY, 3 * 60, TargetType.POWER_FTP, 45, 55),
                WorkoutStep(StepType.WORK, 3 * 60, TargetType.POWER_FTP, 110, 120),
                WorkoutStep(StepType.RECOVERY, 3 * 60, TargetType.POWER_FTP, 45, 55),
                WorkoutStep(StepType.WORK, 3 * 60, TargetType.POWER_FTP, 110, 120),
                WorkoutStep(StepType.RECOVERY, 3 * 60, TargetType.POWER_FTP, 45, 55),
                WorkoutStep(StepType.WORK, 3 * 60, TargetType.POWER_FTP, 110, 120),
                WorkoutStep(StepType.RECOVERY, 3 * 60, TargetType.POWER_FTP, 45, 55),
                WorkoutStep(StepType.WORK, 3 * 60, TargetType.POWER_FTP, 110, 120),
                WorkoutStep(StepType.COOLDOWN, 10 * 60, TargetType.POWER_FTP, 45, 55)
            )
        )
    )

    companion object { private const val KEY_WORKOUTS = "workouts_json" }
}

private fun StructuredWorkout.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("description", description)
    put("estimatedTss", estimatedTss ?: JSONObject.NULL)
    put("source", source)
    put("steps", JSONArray().apply {
        steps.forEach { s ->
            put(JSONObject().apply {
                put("type", s.type.name)
                put("durationSec", s.durationSec)
                put("targetType", s.targetType.name)
                put("targetMin", s.targetMin)
                put("targetMax", s.targetMax)
                put("targetCadence", s.targetCadence ?: JSONObject.NULL)
                put("repeat", s.repeat)
                put("label", s.label ?: JSONObject.NULL)
            })
        }
    })
}

private fun JSONObject.toWorkout(): StructuredWorkout {
    val stepsArr = optJSONArray("steps") ?: JSONArray()
    val steps = ArrayList<WorkoutStep>(stepsArr.length())
    for (i in 0 until stepsArr.length()) {
        val o = stepsArr.optJSONObject(i) ?: continue
        steps.add(
            WorkoutStep(
                type = runCatching { StepType.valueOf(o.optString("type", "WORK")) }.getOrDefault(StepType.WORK),
                durationSec = o.optInt("durationSec", 60).coerceAtLeast(1),
                targetType = runCatching { TargetType.valueOf(o.optString("targetType", "POWER_FTP")) }.getOrDefault(TargetType.POWER_FTP),
                targetMin = o.optInt("targetMin", 50),
                targetMax = o.optInt("targetMax", 60),
                targetCadence = if (o.isNull("targetCadence")) null else o.optInt("targetCadence"),
                repeat = o.optInt("repeat", 1).coerceAtLeast(1),
                label = if (o.isNull("label")) null else o.optString("label")
            )
        )
    }
    return StructuredWorkout(
        id = optLong("id", 0L),
        title = optString("title", "Workout"),
        description = optString("description", ""),
        estimatedTss = if (isNull("estimatedTss")) null else optInt("estimatedTss"),
        source = optString("source", "USER"),
        steps = steps
    )
}
