package com.example.bike_.domain.analytics

import kotlin.math.pow
import kotlin.math.roundToInt

object TrainingAnalytics {

    data class HrZoneRange(val zone: Int, val name: String, val minBpm: Int, val maxBpm: Int?, val colorArgb: Long)
    data class PowerZoneRange(val zone: Int, val name: String, val minWatts: Int, val maxWatts: Int?, val colorArgb: Long)
    data class ZoneTime(val zone: Int, val name: String, val seconds: Int, val percent: Float, val colorArgb: Long)

    val HR_ZONE_COLOR_ARGBS = listOf(0xFF9E9E9EL, 0xFF2196F3L, 0xFF4CAF50L, 0xFFFFEB3BL, 0xFFF44336L)
    val HR_ZONE_NAMES = listOf("Recovery", "Endurance", "Aerobic", "Threshold", "Anaerobic")

    val POWER_ZONE_COLOR_ARGBS = listOf(0xFF9E9E9EL, 0xFF2196F3L, 0xFF4CAF50L, 0xFFFFEB3BL, 0xFFFF9800L, 0xFFF44336L, 0xFFB71C1CL)
    val POWER_ZONE_NAMES = listOf(
        "Active Recovery", "Endurance", "Tempo", "Lactate Threshold", "VO2 Max", "Anaerobic", "Neuromuscular"
    )

    fun hrZones(maxHr: Int, restingHr: Int): List<HrZoneRange> {
        val maxH = maxHr.coerceIn(100, 240)
        val restH = restingHr.coerceIn(30, maxH - 1)
        val reserve = (maxH - restH).coerceAtLeast(1)
        fun b(p: Double) = (restH + reserve * p).roundToInt()
        val z1=b(0.60); val z2=b(0.70); val z3=b(0.80); val z4=b(0.90)
        return listOf(
            HrZoneRange(1, HR_ZONE_NAMES[0], 0, z1 - 1, HR_ZONE_COLOR_ARGBS[0]),
            HrZoneRange(2, HR_ZONE_NAMES[1], z1, z2 - 1, HR_ZONE_COLOR_ARGBS[1]),
            HrZoneRange(3, HR_ZONE_NAMES[2], z2, z3 - 1, HR_ZONE_COLOR_ARGBS[2]),
            HrZoneRange(4, HR_ZONE_NAMES[3], z3, z4 - 1, HR_ZONE_COLOR_ARGBS[3]),
            HrZoneRange(5, HR_ZONE_NAMES[4], z4, null, HR_ZONE_COLOR_ARGBS[4]),
        )
    }

    fun hrZoneIndex(hrBpm: Int, maxHr: Int, restingHr: Int): Int {
        if (hrBpm <= 0) return -1
        val maxH = maxHr.coerceIn(100, 240)
        val restH = restingHr.coerceIn(30, maxH - 1)
        val reserve = (maxH - restH).coerceAtLeast(1)
        val pct = (hrBpm - restH).toDouble() / reserve.toDouble()
        return when { pct < 0.60 -> 0; pct < 0.70 -> 1; pct < 0.80 -> 2; pct < 0.90 -> 3; else -> 4 }
    }

    fun powerZones(ftp: Int): List<PowerZoneRange> {
        val f = ftp.coerceIn(50, 600)
        fun p(x: Double) = (f * x).roundToInt()
        return listOf(
            PowerZoneRange(1, POWER_ZONE_NAMES[0], 0, p(0.55)-1, POWER_ZONE_COLOR_ARGBS[0]),
            PowerZoneRange(2, POWER_ZONE_NAMES[1], p(0.55), p(0.75)-1, POWER_ZONE_COLOR_ARGBS[1]),
            PowerZoneRange(3, POWER_ZONE_NAMES[2], p(0.75), p(0.90)-1, POWER_ZONE_COLOR_ARGBS[2]),
            PowerZoneRange(4, POWER_ZONE_NAMES[3], p(0.90), p(1.05)-1, POWER_ZONE_COLOR_ARGBS[3]),
            PowerZoneRange(5, POWER_ZONE_NAMES[4], p(1.05), p(1.20)-1, POWER_ZONE_COLOR_ARGBS[4]),
            PowerZoneRange(6, POWER_ZONE_NAMES[5], p(1.20), p(1.50)-1, POWER_ZONE_COLOR_ARGBS[5]),
            PowerZoneRange(7, POWER_ZONE_NAMES[6], p(1.50), null, POWER_ZONE_COLOR_ARGBS[6]),
        )
    }

    fun powerZoneIndex(powerWatts: Int, ftp: Int): Int {
        if (powerWatts < 0) return -1
        val pct = powerWatts / ftp.coerceIn(50, 600).toDouble()
        return when { pct < 0.55 -> 0; pct < 0.75 -> 1; pct < 0.90 -> 2; pct < 1.05 -> 3; pct < 1.20 -> 4; pct < 1.50 -> 5; else -> 6 }
    }

    fun normalizedPower(samples: IntArray): Int? {
        if (samples.size < 30) return null
        val w = 30
        var sum = 0L
        for (i in 0 until w) sum += samples[i].coerceAtLeast(0)
        val rolling = ArrayList<Double>(samples.size - w + 1)
        rolling.add(sum.toDouble() / w)
        for (i in w until samples.size) {
            sum += samples[i].coerceAtLeast(0) - samples[i - w].coerceAtLeast(0)
            rolling.add(sum.toDouble() / w)
        }
        var fourth = 0.0
        for (v in rolling) { val v2 = v*v; fourth += v2*v2 }
        val avg = fourth / rolling.size
        return if (avg.isFinite() && avg > 0) avg.pow(0.25).roundToInt() else null
    }

    fun intensityFactor(npWatts: Int?, ftp: Int): Float? {
        val np = npWatts ?: return null
        if (np <= 0 || ftp <= 0) return null
        return np.toFloat() / ftp.toFloat()
    }

    fun tss(durationSec: Int, npWatts: Int?, ifFactor: Float?, ftp: Int): Float? {
        val np = npWatts ?: return null
        val iff = ifFactor ?: return null
        if (durationSec <= 0 || np <= 0 || ftp <= 0) return null
        val v = (durationSec.toDouble() * np.toDouble() * iff.toDouble()) / (ftp.toDouble() * 3600.0) * 100.0
        return if (v.isFinite() && v >= 0) v.toFloat() else null
    }

    fun hrTss(hrZoneSeconds: IntArray): Float? {
        if (hrZoneSeconds.size < 5) return null
        val weights = doubleArrayOf(20.0, 40.0, 60.0, 80.0, 100.0)
        var points = 0.0; var sum = 0
        for (i in 0 until 5) { val sec = hrZoneSeconds[i].coerceAtLeast(0); sum += sec; points += (sec / 3600.0) * weights[i] }
        return if (sum > 0) points.toFloat() else null
    }

    fun caloriesFromPower(avgPowerWatts: Int?, durationSec: Int): Int? {
        val p = avgPowerWatts ?: return null
        if (p <= 0 || durationSec <= 0) return null
        return ((p.toDouble() * durationSec.toDouble()) / 1000.0).roundToInt().coerceAtLeast(0)
    }

    fun caloriesFromHr(avgHr: Int?, weightKg: Float, ageYears: Int = 30, isMale: Boolean = true, durationSec: Int): Int? {
        val hr = avgHr ?: return null
        if (hr <= 0 || durationSec <= 0) return null
        val w = weightKg.coerceIn(20f, 250f).toDouble()
        val age = ageYears.coerceIn(10, 100).toDouble()
        val hrD = hr.toDouble()
        val min = durationSec / 60.0
        val calPerMin = if (isMale) (-55.0969 + 0.6309*hrD + 0.1988*w + 0.2017*age) / 4.184
        else (-20.4022 + 0.4472*hrD - 0.1263*w + 0.074*age) / 4.184
        val total = calPerMin * min
        return if (total.isFinite() && total > 0) total.roundToInt() else 0
    }

    fun zoneTimes(secondsByZone: IntArray, names: List<String>, colors: List<Long>): List<ZoneTime> {
        val total = secondsByZone.sum().coerceAtLeast(1)
        return secondsByZone.indices.map { i ->
            val sec = secondsByZone[i].coerceAtLeast(0)
            ZoneTime(i+1, names.getOrElse(i){"Z"}, sec, sec*100f/total.toFloat(), colors.getOrElse(i){0xFF808080L})
        }
    }

    fun formatMmSs(totalSec: Int): String {
        val s = totalSec.coerceAtLeast(0)
        return "%02d:%02d".format(s/60, s%60)
    }

    fun encodeZoneSeconds(arr: IntArray): String = arr.joinToString(",")
    fun decodeZoneSeconds(raw: String?, size: Int): IntArray {
        val out = IntArray(size)
        if (raw.isNullOrBlank()) return out
        val parts = raw.split(',')
        for (i in 0 until minOf(size, parts.size)) {
            out[i] = parts[i].trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
        }
        return out
    }
}
