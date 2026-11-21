package com.example.bike_.data.ble.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CscParserTest {
    private val parser = CscParser()
    private val circumferenceMm = 2000f // 2 m

    @Test
    fun computesSpeedFromConsecutiveWheelSamples() {
        // First sample only initializes state.
        assertNull(parser.parse(wheel(revs = 100, eventTime = 0), circumferenceMm)!!.speedKmh)
        // 10 revolutions * 2 m = 20 m in 2048/1024 = 2 s => 10 m/s => 36 km/h.
        val result = parser.parse(wheel(revs = 110, eventTime = 2048), circumferenceMm)!!
        assertEquals(36f, result.speedKmh ?: -1f, 0.01f)
    }

    @Test
    fun rejectsImplausibleSpeedSpike() {
        parser.parse(wheel(revs = 100, eventTime = 0), circumferenceMm)
        // 100000 revolutions in a single sensor tick would be a spike.
        assertNull(parser.parse(wheel(revs = 100100, eventTime = 2049), circumferenceMm)!!.speedKmh)
    }

    @Test
    fun computesCadenceAndHonorsReset() {
        parser.parse(crank(revs = 10, eventTime = 0), circumferenceMm)
        // 5 revolutions in 2 s => 150 rpm.
        val result = parser.parse(crank(revs = 15, eventTime = 2048), circumferenceMm)!!
        assertEquals(150f, result.cadenceRpm ?: -1f, 0.01f)

        parser.reset()
        assertNull(parser.parse(crank(revs = 100, eventTime = 5000), circumferenceMm)!!.cadenceRpm)
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun wheel(revs: Int, eventTime: Int): ByteArray = bytes(
        0x01,
        revs, revs shr 8, revs shr 16, revs shr 24,
        eventTime, eventTime shr 8
    )

    private fun crank(revs: Int, eventTime: Int): ByteArray = bytes(
        0x02,
        revs, revs shr 8,
        eventTime, eventTime shr 8
    )
}
