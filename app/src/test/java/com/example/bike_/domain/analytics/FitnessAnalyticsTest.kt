package com.example.bike_.domain.analytics

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FitnessAnalyticsTest {

    @Test
    fun ctlAtlTsb_14daysWithRest() {
        val zone = ZoneId.of("UTC")
        val start = LocalDate.of(2026, 1, 1)
        // 14 days: hard workouts on days 1,3,5,8,10,12 (TSS 80), rest others
        val workouts = mutableListOf<Pair<Long, Float?>>()
        for (i in 0 until 14) {
            val day = start.plusDays(i.toLong())
            val ms = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val tss = if (i in setOf(0,2,4,7,9,11)) 80f else 0f
            workouts.add(ms to tss)
        }
        val series = FitnessAnalytics.buildSeries(workouts, zone, today = start.plusDays(13))
        assertEquals(14, series.size)
        // Day 1: ctl = 0 + (80-0)/42 = 1.9048, atl = 0 + (80-0)/7 = 11.4286, tsb = 0-0 = 0
        val d1 = series[0]
        assertEquals(80f, d1.dailyTss, 0.001f)
        assertEquals(1.9048f, d1.ctl, 0.01f)
        assertEquals(11.4286f, d1.atl, 0.01f)
        assertEquals(0f, d1.tsb, 0.001f)
        // Day 2 rest: tsb = ctlPrev-atlPrev (day1)
        val d2 = series[1]
        assertEquals(0f, d2.dailyTss, 0.001f)
        assertEquals(d1.ctl - d1.atl, d2.tsb, 0.01f)
        assertEquals(-9.5238f, d2.tsb, 0.05f)
        // After 14 days with rest, CTL/ATL should be positive
        val last = series.last()
        assertTrue("CTL should be positive", last.ctl > 0f)
        assertTrue("ATL should be positive", last.atl > 0f)
        // TSB = ctlPrev - atlPrev
        val prev = series[series.size - 2]
        assertEquals(prev.ctl - prev.atl, last.tsb, 0.01f)
    }

    @Test
    fun tsbStatus_ranges() {
        assertEquals(FitnessAnalytics.TsbStatus.FRESHNESS_DROP, FitnessAnalytics.tsbStatus(20f))
        assertEquals(FitnessAnalytics.TsbStatus.PEAK, FitnessAnalytics.tsbStatus(10f))
        assertEquals(FitnessAnalytics.TsbStatus.MAINTENANCE, FitnessAnalytics.tsbStatus(0f))
        assertEquals(FitnessAnalytics.TsbStatus.OPTIMAL, FitnessAnalytics.tsbStatus(-20f))
        assertEquals(FitnessAnalytics.TsbStatus.HIGH_RISK, FitnessAnalytics.tsbStatus(-40f))
    }
}
