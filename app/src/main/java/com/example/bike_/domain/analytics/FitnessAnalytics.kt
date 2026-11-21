package com.example.bike_.domain.analytics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

object FitnessAnalytics {

    data class DailyPoint(
        val date: LocalDate,
        val dailyTss: Float,
        val ctl: Float, // Fitness
        val atl: Float, // Fatigue
        val tsb: Float  // Form
    )

    enum class TsbStatus {
        FRESHNESS_DROP,
        PEAK,
        MAINTENANCE,
        OPTIMAL,
        HIGH_RISK
    }

    fun tsbStatus(tsb: Float): TsbStatus = when {
        tsb > 15f -> TsbStatus.FRESHNESS_DROP
        tsb >= 5f -> TsbStatus.PEAK
        tsb >= -10f -> TsbStatus.MAINTENANCE
        tsb >= -30f -> TsbStatus.OPTIMAL
        else -> TsbStatus.HIGH_RISK
    }

    /**
     * Build daily CTL/ATL/TSB series from finished workouts.
     * @param workouts pairs of (startTimeMs, tss). TSS null treated as 0.
     * @param zoneId local zone for day bucketing.
     * @param today inclusive end day.
     */
    fun buildSeries(
        workouts: List<Pair<Long, Float?>>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zoneId)
    ): List<DailyPoint> {
        if (workouts.isEmpty()) return emptyList()

        val tssByDay = HashMap<LocalDate, Float>()
        for ((startMs, tss) in workouts) {
            val day = Instant.ofEpochMilli(startMs).atZone(zoneId).toLocalDate()
            val v = (tss ?: 0f).coerceAtLeast(0f)
            tssByDay[day] = (tssByDay[day] ?: 0f) + v
        }

        val firstDay = tssByDay.keys.minOrNull() ?: today
        val startDay = if (firstDay.isAfter(today)) today else firstDay

        val out = ArrayList<DailyPoint>()
        var ctlPrev = 0f
        var atlPrev = 0f
        var day = startDay
        while (!day.isAfter(today)) {
            val dailyTss = tssByDay[day] ?: 0f
            val ctlToday = ctlPrev + (dailyTss - ctlPrev) * (1f / 42f)
            val atlToday = atlPrev + (dailyTss - atlPrev) * (1f / 7f)
            val tsbToday = ctlPrev - atlPrev // readiness on morning of current day, from previous day's loads
            out.add(DailyPoint(date = day, dailyTss = dailyTss, ctl = ctlToday, atl = atlToday, tsb = tsbToday))
            ctlPrev = ctlToday
            atlPrev = atlToday
            day = day.plusDays(1)
        }
        return out
    }

    fun currentOrLast(series: List<DailyPoint>): DailyPoint? = series.lastOrNull()

    fun IntSafe(v: Float): Int = v.roundToInt()
}
