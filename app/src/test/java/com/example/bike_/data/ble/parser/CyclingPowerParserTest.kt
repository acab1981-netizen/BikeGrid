package com.example.bike_.data.ble.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CyclingPowerParserTest {
    private val parser = CyclingPowerParser()

    @Test
    fun parsesValidPower() {
        // flags uint16 = 0, instantaneous power sint16 = 200 W
        assertEquals(200, parser.parse(byteArrayOf(0x00, 0x00, 0xC8.toByte(), 0x00)))
        assertEquals(0, parser.parse(byteArrayOf(0x00, 0x00, 0x00, 0x00)))
    }

    @Test
    fun rejectsNegativeOutOfRangeAndTruncated() {
        assertNull(parser.parse(byteArrayOf(0x00, 0x00)))                                 // too short
        assertNull(parser.parse(byteArrayOf(0x00, 0x00, 0xFB.toByte(), 0xFF.toByte())))     // -5 W
        assertNull(parser.parse(byteArrayOf(0x00, 0x00, 0xA1.toByte(), 0x0F)))              // 4001 W
    }
}
