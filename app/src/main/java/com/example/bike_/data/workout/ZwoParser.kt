package com.example.bike_.data.workout

import com.example.bike_.domain.workout.StepType
import com.example.bike_.domain.workout.StructuredWorkout
import com.example.bike_.domain.workout.TargetType
import com.example.bike_.domain.workout.WorkoutStep
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import kotlin.math.roundToInt

object ZwoParser {
    class InvalidZwoException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun parse(input: InputStream, sourceName: String? = null): StructuredWorkout? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(input, "UTF-8")
            var event = parser.eventType
            var title: String? = null
            var description = ""
            val steps = ArrayList<WorkoutStep>()
            var inWorkout = false
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name.lowercase()) {
                        "name" -> if (title == null) title = parser.nextTextOrEmpty().trim()
                        "description" -> if (description.isBlank()) description = parser.nextTextOrEmpty().trim()
                        "workout" -> inWorkout = true
                        "warmup" -> if (inWorkout) steps.add(parseRamp(parser, StepType.WARMUP))
                        "cooldown" -> if (inWorkout) steps.add(parseRamp(parser, StepType.COOLDOWN))
                        "steadystate" -> if (inWorkout) steps.add(parseSteady(parser))
                        "intervalst" -> if (inWorkout) steps.addAll(parseIntervals(parser))
                        "freeride" -> if (inWorkout) steps.add(parseFreeRide(parser))
                    }
                } else if (event == XmlPullParser.END_TAG && parser.name.equals("workout", true)) {
                    inWorkout = false
                }
                event = parser.next()
            }
            if (steps.isEmpty()) throw InvalidZwoException("ZWO workout has no steps")
            StructuredWorkout(
                id = 0L,
                title = title?.takeIf { it.isNotBlank() } ?: sourceName?.substringBeforeLast('.') ?: "ZWO Workout",
                description = description,
                source = "ZWO",
                steps = steps
            )
        } catch (e: InvalidZwoException) {
            throw e
        } catch (e: Exception) {
            throw InvalidZwoException("Invalid ZWO file format", e)
        }
    }

    private fun parseRamp(p: XmlPullParser, type: StepType): WorkoutStep {
        val dur = p.attrInt("Duration")
        val p1 = p.attrFloat("PowerLow").toPct()
        val p2 = p.attrFloat("PowerHigh").toPct()
        val cadence = p.attrIntOrNull("Cadence")
        val min = minOf(p1, p2)
        val max = maxOf(p1, p2)
        return WorkoutStep(type, dur.coerceAtLeast(1), TargetType.POWER_FTP, min, max, cadence)
    }

    private fun parseSteady(p: XmlPullParser): WorkoutStep {
        val dur = p.attrInt("Duration")
        val power = p.attrFloat("Power").toPct()
        val cadence = p.attrIntOrNull("Cadence")
        return WorkoutStep(StepType.WORK, dur.coerceAtLeast(1), TargetType.POWER_FTP, (power - 3).coerceAtLeast(30), (power + 3).coerceAtLeast(30), cadence)
    }

    private fun parseIntervals(p: XmlPullParser): List<WorkoutStep> {
        val repeat = p.attrInt("Repeat").coerceAtLeast(1)
        val onDur = p.attrInt("OnDuration")
        val offDur = p.attrInt("OffDuration")
        val onP = p.attrFloat("OnPower").toPct()
        val offP = p.attrFloat("OffPower").toPct()
        val onCad = p.attrIntOrNull("Cadence") ?: p.attrIntOrNull("CadenceResting") // best effort
        val offCad = p.attrIntOrNull("CadenceResting")
        val out = ArrayList<WorkoutStep>(repeat * 2)
        repeat(repeat) {
            out.add(WorkoutStep(StepType.WORK, onDur.coerceAtLeast(1), TargetType.POWER_FTP, (onP - 3).coerceAtLeast(30), (onP + 3).coerceAtLeast(30), onCad))
            out.add(WorkoutStep(StepType.RECOVERY, offDur.coerceAtLeast(1), TargetType.POWER_FTP, (offP - 5).coerceAtLeast(30), (offP + 5).coerceAtLeast(30), offCad))
        }
        return out
    }

    private fun parseFreeRide(p: XmlPullParser): WorkoutStep {
        val dur = p.attrInt("Duration").coerceAtLeast(1)
        return WorkoutStep(StepType.WORK, dur, TargetType.POWER_FTP, 45, 65, p.attrIntOrNull("Cadence"), label = "Free")
    }

    private fun XmlPullParser.attr(name: String): String? {
        for (i in 0 until attributeCount) if (getAttributeName(i).equals(name, true)) return getAttributeValue(i)
        return null
    }
    private fun XmlPullParser.attrInt(name: String): Int = attr(name)?.toFloatOrNull()?.roundToInt() ?: 0
    private fun XmlPullParser.attrIntOrNull(name: String): Int? = attr(name)?.toFloatOrNull()?.roundToInt()
    private fun XmlPullParser.attrFloat(name: String): Float = attr(name)?.toFloatOrNull() ?: 0f
    private fun Float.toPct(): Int = if (this <= 3f) (this * 100f).roundToInt() else this.roundToInt()
    private fun XmlPullParser.nextTextOrEmpty(): String = try { nextText() ?: "" } catch (_: Exception) { "" }
}
