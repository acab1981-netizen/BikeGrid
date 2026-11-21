package com.example.bike_.data.ble.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FtmsParserTest {

    @Test
    fun parsesSpeedOnly() {
        // flags = 0 (instantaneous speed present), 2500 -> 25.00 km/h
        val result = FtmsParser.parseIndoorBikeData(byteArrayOf(0x00, 0x00, 0xC4.toByte(), 0x09))
        assertEquals(25f, result?.speedKmh ?: -1f, 0.001f)
        assertNull(result?.cadenceRpm)
        assertNull(result?.powerWatts)
    }

    @Test
    fun parsesCadenceAndPowerTogether() {
        // flags = instant cadence (1 shl 2) | instant power (1 shl 6) = 0x44
        val data = byteArrayOf(
            0x44, 0x00,           // flags
            0xB8.toByte(), 0x0B,  // speed 3000 -> 30.00 km/h
            0xB4.toByte(), 0x00,  // cadence 180 -> 90 rpm
            0xFA.toByte(), 0x00   // power 250 W
        )
        val result = FtmsParser.parseIndoorBikeData(data)!!
        assertEquals(30f, result.speedKmh ?: -1f, 0.001f)
        assertEquals(90f, result.cadenceRpm ?: -1f, 0.001f)
        assertEquals(250, result.powerWatts)
    }

    @Test
    fun malformedData_neverThrowsAndKeepsParsedFields() {
        assertNull(FtmsParser.parseIndoorBikeData(byteArrayOf()))
        val truncated = FtmsParser.parseIndoorBikeData(byteArrayOf(0x00, 0x00))
        assertNull(truncated?.speedKmh)
    }

    @Test
    fun controlPoint_buildersAndResponses() {
        val cmd = FtmsParser.buildSetTargetPower(200)
        assertEquals(0x05, cmd[0].toInt() and 0xFF)
        assertEquals(0xC8, cmd[1].toInt() and 0xFF)
        assertEquals(0x00, cmd[2].toInt() and 0xFF)

        val clamped = FtmsParser.buildSetTargetPower(5000)
        assertEquals(0xA0, clamped[1].toInt() and 0xFF) // 4000 W max
        assertEquals(0x0F, clamped[2].toInt() and 0xFF)

        val ok = FtmsParser.parseControlPointResponse(byteArrayOf(0x80.toByte(), 0x05, 0x01))!!
        assertEquals(0x05, ok.requestOpCode)
        assertTrue(FtmsParser.isSuccess(ok))
        val fail = FtmsParser.parseControlPointResponse(byteArrayOf(0x80.toByte(), 0x05, 0x02))!!
        assertFalse(FtmsParser.isSuccess(fail))
        assertNull(FtmsParser.parseControlPointResponse(byteArrayOf(0x05, 0x00, 0x01)))
    }
}
