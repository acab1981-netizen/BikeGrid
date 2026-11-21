package com.example.bike_.domain.workout

data class StructuredWorkout(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val estimatedTss: Int? = null,
    val source: String = "BUILTIN", // BUILTIN, USER, ZWO
    val steps: List<WorkoutStep>
) {
    val totalDurationSec: Int get() = steps.sumOf { it.durationSec.coerceAtLeast(0) }
}

data class WorkoutStep(
    val type: StepType,
    val durationSec: Int,
    val targetType: TargetType = TargetType.POWER_FTP,
    val targetMin: Int = 0,   // %FTP or HR zone min
    val targetMax: Int = 0,   // %FTP or HR zone max
    val targetCadence: Int? = null,
    val repeat: Int = 1,
    val label: String? = null
)

enum class StepType { WARMUP, WORK, RECOVERY, COOLDOWN }
enum class TargetType { POWER_FTP, HR_ZONE }
