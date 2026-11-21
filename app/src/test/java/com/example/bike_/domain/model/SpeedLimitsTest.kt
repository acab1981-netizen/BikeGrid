package com.example.bike_.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedLimitsTest {

    @Test
    fun limits_byWorkoutType() {
        assertEquals(12f, SpeedLimits.forWorkoutType("WALKING"))
        assertEquals(30f, SpeedLimits.forWorkoutType("RUNNING"))
        assertEquals(120f, SpeedLimits.forWorkoutType("CYCLING"))
        assertEquals(120f, SpeedLimits.forWorkoutType(null))
        assertEquals(120f, SpeedLimits.forWorkoutType("UNKNOWN"))
    }

    @Test
    fun clamp_singleGpsSpike_cannotBecomeWorkoutMax() {
        // Regression: one noisy GPS fix used to be stored as an impossible max speed.
        assertEquals(12f, SpeedLimits.clamp(85f, "WALKING"))
        assertEquals(30f, SpeedLimits.clamp(85f, "RUNNING"))
        assertEquals(85f, SpeedLimits.clamp(85f, "CYCLING"))
    }

    @Test
    fun clamp_negativeAndNormalValues() {
        assertEquals(0f, SpeedLimits.clamp(-5f, "WALKING"))
        assertEquals(10.5f, SpeedLimits.clamp(10.5f, "WALKING"))
        assertEquals(0f, SpeedLimits.clamp(0f, null))
    }
}
