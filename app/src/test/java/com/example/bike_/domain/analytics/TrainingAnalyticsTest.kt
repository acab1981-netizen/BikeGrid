package com.example.bike_.domain.analytics

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class TrainingAnalyticsTest {

    @Test
    fun hrZones_karvonen_reference() {
        val zones = TrainingAnalytics.hrZones(maxHr = 190, restingHr = 50)
        assertEquals(5, zones.size)
        // reserve 140: 60%=134,70%=148,80%=162,90%=176
        assertEquals(1, zones[0].zone); assertEquals(0, zones[0].minBpm); assertEquals(133, zones[0].maxBpm)
        assertEquals(2, zones[1].zone); assertEquals(134, zones[1].minBpm); assertEquals(147, zones[1].maxBpm)
        assertEquals(3, zones[2].zone); assertEquals(148, zones[2].minBpm); assertEquals(161, zones[2].maxBpm)
        assertEquals(4, zones[3].zone); assertEquals(162, zones[3].minBpm); assertEquals(175, zones[3].maxBpm)
        assertEquals(5, zones[4].zone); assertEquals(176, zones[4].minBpm); assertNull(zones[4].maxBpm)
    }

    @Test
    fun hrZoneIndex_matchesKarvonen() {
        val maxHr = 190; val rest = 50
        assertEquals(0, TrainingAnalytics.hrZoneIndex(120, maxHr, rest)) // below 60%
        assertEquals(1, TrainingAnalytics.hrZoneIndex(140, maxHr, rest)) // 60-70
        assertEquals(2, TrainingAnalytics.hrZoneIndex(155, maxHr, rest)) // 70-80
        assertEquals(3, TrainingAnalytics.hrZoneIndex(170, maxHr, rest)) // 80-90
        assertEquals(4, TrainingAnalytics.hrZoneIndex(180, maxHr, rest)) // >=90
    }

    @Test
    fun powerZones_coggan_reference() {
        val zones = TrainingAnalytics.powerZones(ftp = 200)
        assertEquals(7, zones.size)
        assertEquals(0, zones[0].minWatts); assertEquals(109, zones[0].maxWatts)   // <55%
        assertEquals(110, zones[1].minWatts); assertEquals(149, zones[1].maxWatts) // 55-75
        assertEquals(150, zones[2].minWatts); assertEquals(179, zones[2].maxWatts) // 75-90
        assertEquals(180, zones[3].minWatts); assertEquals(209, zones[3].maxWatts) // 90-105
        assertEquals(210, zones[4].minWatts); assertEquals(239, zones[4].maxWatts) // 105-120
        assertEquals(240, zones[5].minWatts); assertEquals(299, zones[5].maxWatts) // 120-150
        assertEquals(300, zones[6].minWatts); assertNull(zones[6].maxWatts)       // >150
    }

    @Test
    fun powerZoneIndex_matchesCoggan() {
        val ftp = 200
        assertEquals(0, TrainingAnalytics.powerZoneIndex(100, ftp))
        assertEquals(1, TrainingAnalytics.powerZoneIndex(120, ftp))
        assertEquals(2, TrainingAnalytics.powerZoneIndex(160, ftp))
        assertEquals(3, TrainingAnalytics.powerZoneIndex(200, ftp))
        assertEquals(4, TrainingAnalytics.powerZoneIndex(230, ftp))
        assertEquals(5, TrainingAnalytics.powerZoneIndex(260, ftp))
        assertEquals(6, TrainingAnalytics.powerZoneIndex(320, ftp))
    }

    @Test
    fun npIfTss_constantPowerReference() {
        // 1 hour at 200W, FTP 200 => NP=200, IF=1.0, TSS=100
        val samples = IntArray(3600) { 200 }
        val np = TrainingAnalytics.normalizedPower(samples)
        assertNotNull(np)
        assertEquals(200, np!!)
        val iff = TrainingAnalytics.intensityFactor(np, 200)
        assertNotNull(iff)
        assertEquals(1.0f, iff!!, 0.001f)
        val tss = TrainingAnalytics.tss(3600, np, iff, 200)
        assertNotNull(tss)
        assertEquals(100f, tss!!, 0.5f)
    }

    @Test
    fun np_handlesShortSamples() {
        assertNull(TrainingAnalytics.normalizedPower(IntArray(29) { 200 }))
        assertNotNull(TrainingAnalytics.normalizedPower(IntArray(30) { 200 }))
    }
}
