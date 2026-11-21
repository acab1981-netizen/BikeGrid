package com.example.bike_.domain.workout

import com.example.bike_.domain.analytics.TrainingAnalytics
import kotlin.math.roundToInt

data class WorkoutPlayerState(
    val workoutId: Long = 0,
    val title: String = "",
    val steps: List<WorkoutStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val stepElapsedSec: Int = 0,
    val stepRemainingSec: Int = 0,
    val totalElapsedSec: Int = 0,
    val totalDurationSec: Int = 0,
    val isCompleted: Boolean = false,
    val currentTargetMinWatts: Int? = null,
    val currentTargetMaxWatts: Int? = null,
    val currentTargetMinHr: Int? = null,
    val currentTargetMaxHr: Int? = null,
    val targetCadence: Int? = null,
    val currentStepType: StepType = StepType.WORK,
    val nextStepLabel: String? = null,
    val nextStepTargetLabel: String? = null,
    val compliance: TargetCompliance = TargetCompliance.NONE
)

enum class TargetCompliance { NONE, BELOW, IN_RANGE, ABOVE }

object StructuredWorkoutEngine {
    fun expandSteps(steps: List<WorkoutStep>): List<WorkoutStep> {
        val out = ArrayList<WorkoutStep>()
        for (s in steps) {
            val rep = s.repeat.coerceAtLeast(1)
            repeat(rep) { out.add(s.copy(repeat = 1)) }
        }
        return out
    }

    fun stateAt(
        workout: StructuredWorkout,
        elapsedSecTotal: Int,
        ftpWatts: Int,
        maxHr: Int,
        restingHr: Int,
        currentPower: Int?,
        currentHr: Int?
    ): WorkoutPlayerState {
        val expanded = expandSteps(workout.steps)
        val totalDur = expanded.sumOf { it.durationSec.coerceAtLeast(1) }
        val clamped = elapsedSecTotal.coerceAtLeast(0)
        var acc = 0
        var idx = expanded.lastIndex.coerceAtLeast(0)
        var stepElapsed = 0
        for (i in expanded.indices) {
            val d = expanded[i].durationSec.coerceAtLeast(1)
            if (clamped < acc + d || i == expanded.lastIndex) {
                idx = i
                stepElapsed = (clamped - acc).coerceIn(0, d)
                break
            }
            acc += d
        }
        val cur = expanded.getOrNull(idx)
        val next = expanded.getOrNull(idx + 1)
        val stepDur = cur?.durationSec?.coerceAtLeast(1) ?: 0
        val remaining = (stepDur - stepElapsed).coerceAtLeast(0)
        val (minW, maxW, minHr, maxHrT) = targets(cur, ftpWatts, maxHr, restingHr)
        val compliance = compliance(cur?.targetType, currentPower, currentHr, minW, maxW, minHr, maxHrT)
        return WorkoutPlayerState(
            workoutId = workout.id,
            title = workout.title,
            steps = expanded,
            currentStepIndex = idx,
            stepElapsedSec = stepElapsed,
            stepRemainingSec = remaining,
            totalElapsedSec = clamped.coerceAtMost(totalDur),
            totalDurationSec = totalDur,
            isCompleted = clamped >= totalDur && totalDur > 0,
            currentTargetMinWatts = minW,
            currentTargetMaxWatts = maxW,
            currentTargetMinHr = minHr,
            currentTargetMaxHr = maxHrT,
            targetCadence = cur?.targetCadence,
            currentStepType = cur?.type ?: StepType.WORK,
            nextStepLabel = next?.let { stepLabel(it) },
            nextStepTargetLabel = next?.let { targetLabel(it, ftpWatts) },
            compliance = compliance
        )
    }

    fun targets(step: WorkoutStep?, ftpWatts: Int, maxHr: Int, restingHr: Int): List<Int?> {
        if (step == null) return listOf(null, null, null, null)
        return when (step.targetType) {
            TargetType.POWER_FTP -> {
                val ftp = ftpWatts.coerceAtLeast(1)
                val minW = ((step.targetMin.coerceAtLeast(0) / 100f) * ftp).roundToInt()
                val maxW = ((step.targetMax.coerceAtLeast(step.targetMin) / 100f) * ftp).roundToInt()
                listOf(minW, maxW, null, null)
            }
            TargetType.HR_ZONE -> {
                val zMin = step.targetMin.coerceIn(1, 5)
                val zMax = step.targetMax.coerceIn(zMin, 5)
                listOf(null, null, hrZoneLow(zMin, maxHr, restingHr), hrZoneHigh(zMax, maxHr, restingHr))
            }
        }
    }

    fun hrZoneLow(zone: Int, maxHr: Int, restingHr: Int): Int {
        // Approx inverse of Karvonen 5-zone used in TrainingAnalytics; simple bounds.
        val hrr = (maxHr - restingHr).coerceAtLeast(1).toFloat()
        val pct = when (zone) { 1 -> 0.50f; 2 -> 0.60f; 3 -> 0.70f; 4 -> 0.80f; else -> 0.90f }
        return (restingHr + hrr * pct).roundToInt()
    }
    fun hrZoneHigh(zone: Int, maxHr: Int, restingHr: Int): Int {
        val hrr = (maxHr - restingHr).coerceAtLeast(1).toFloat()
        val pct = when (zone) { 1 -> 0.60f; 2 -> 0.70f; 3 -> 0.80f; 4 -> 0.90f; else -> 1.00f }
        return (restingHr + hrr * pct).roundToInt().coerceAtMost(maxHr)
    }

    private fun compliance(targetType: TargetType?, power: Int?, hr: Int?, minW: Int?, maxW: Int?, minHr: Int?, maxHr: Int?): TargetCompliance {
        if (targetType == null) return TargetCompliance.NONE
        return when (targetType) {
            TargetType.POWER_FTP -> {
                val p = power ?: return TargetCompliance.NONE
                val lo = minW ?: return TargetCompliance.NONE
                val hi = maxW ?: return TargetCompliance.NONE
                when { p < lo -> TargetCompliance.BELOW; p > hi -> TargetCompliance.ABOVE; else -> TargetCompliance.IN_RANGE }
            }
            TargetType.HR_ZONE -> {
                val h = hr ?: return TargetCompliance.NONE
                val lo = minHr ?: return TargetCompliance.NONE
                val hi = maxHr ?: return TargetCompliance.NONE
                when { h < lo -> TargetCompliance.BELOW; h > hi -> TargetCompliance.ABOVE; else -> TargetCompliance.IN_RANGE }
            }
        }
    }

    fun stepLabel(s: WorkoutStep): String = when (s.type) {
        StepType.WARMUP -> "Warmup"
        StepType.WORK -> "Work"
        StepType.RECOVERY -> "Recovery"
        StepType.COOLDOWN -> "Cooldown"
    }

    fun targetLabel(s: WorkoutStep, ftpWatts: Int): String = when (s.targetType) {
        TargetType.POWER_FTP -> {
            val minW = ((s.targetMin / 100f) * ftpWatts).roundToInt()
            val maxW = ((s.targetMax / 100f) * ftpWatts).roundToInt()
            "$minW-$maxW W"
        }
        TargetType.HR_ZONE -> "Z${s.targetMin}-Z${s.targetMax}"
    }

    fun estimateTss(workout: StructuredWorkout, ftpWatts: Int): Int {
        val ftp = ftpWatts.coerceAtLeast(1).toFloat()
        val expanded = expandSteps(workout.steps)
        var tss = 0f
        for (s in expanded) {
            val midPct = ((s.targetMin + s.targetMax).coerceAtLeast(0) / 2f) / 100f
            val intensity = midPct
            val sec = s.durationSec.coerceAtLeast(0).toFloat()
            tss += (sec * intensity * intensity) / 36f // (sec*NP*IF)/(FTP*3600)*100 with NP~IF*FTP
        }
        return tss.roundToInt()
    }
}
