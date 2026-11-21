package com.example.bike_.data.ble.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RscParserTest {

    @Test
    fun parsesWalkingSpeedAndCadence() {
        // flags=0 (walking, no optional fields), speed=2560 -> 10 m/s -> 36 km/h, cadence=170
        val m = RscParser.parse(byteArrayOf(0x00, 0x00, 0x0A, 0xAA.toByte()))!!
        assertEquals(36f, m.speedKmh, 0.001f)
        assertEquals(170, m.cadenceSpm)
        assertFalse(m.isRunning)
        assertNull(m.strideLengthM)
        assertNull(m.totalDistanceM)
    }

    @Test
    fun parsesRunningWithStrideLength() {
        // flags=0x05 (running + stride), stride=100 -> 10.0 m, cadence=180
        val m = RscParser.parse(
            byteArrayOf(0x05, 0x00, 0x0A, 0xB4.toByte(), 0x64, 0x00)
        )!!
        assertTrue(m.isRunning)
        assertEquals(180, m.cadenceSpm)
        assertEquals(10f, m.strideLengthM ?: -1f, 0.001f)
        assertNull(m.totalDistanceM)
    }

    @Test
    fun parsesTotalDistance() {
        // flags=0x02 (total distance present), distance=12345 m -> 0x3039 LE
        val m = RscParser.parse(
            byteArrayOf(0x02, 0x00, 0x08, 0x9C.toByte(), 0x39, 0x30, 0x00, 0x00)
        )!!
        assertEquals(12345L, m.totalDistanceM)
        assertNull(m.strideLengthM)
    }

    @Test
    fun parsesAllFieldsTogether() {
        val m = RscParser.parse(
            byteArrayOf(0x07, 0x00, 0x0A, 0xB4.toByte(), 0x64, 0x00, 0x39, 0x30, 0x00, 0x00)
        )!!
        assertTrue(m.isRunning)
        assertEquals(36f, m.speedKmh, 0.001f)
        assertEquals(180, m.cadenceSpm)
        assertEquals(10f, m.strideLengthM ?: -1f, 0.001f)
        assertEquals(12345L, m.totalDistanceM)
    }

    @Test
    fun rejectsTruncatedAndNoise() {
        assertNull(RscParser.parse(byteArrayOf()))
        assertNull(RscParser.parse(byteArrayOf(0x00, 0x00, 0x0A)))
        // raw 65535 -> 255.99 m/s: garbage, not a runner
        assertNull(RscParser.parse(byteArrayOf(0x00, 0xFF.toByte(), 0xFF.toByte(), 0x64)))
    }

    @Test
    fun truncatedOptionalFieldsKeepMandatoryValues() {
        // Flags announce stride but the frame ends after the mandatory part.
        val m = RscParser.parse(byteArrayOf(0x01, 0x00, 0x0A, 0xAA.toByte()))!!
        assertEquals(36f, m.speedKmh, 0.001f)
        assertEquals(170, m.cadenceSpm)
        assertNull(m.strideLengthM)
    }
}
