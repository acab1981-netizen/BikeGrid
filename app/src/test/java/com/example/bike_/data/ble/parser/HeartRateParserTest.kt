package com.example.bike_.data.ble.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateParserTest {
    private val parser = HeartRateParser()

    @Test
    fun parsesUInt8Bpm() {
        assertEquals(74, parser.parse(byteArrayOf(0x00, 0x4A)))
    }

    @Test
    fun parsesUInt16Bpm() {
        assertEquals(300, parser.parse(byteArrayOf(0x01, 0x2C, 0x01)))
    }

    @Test
    fun rejectsEmptyTruncatedAndOutOfRange() {
        assertNull(parser.parse(byteArrayOf()))
        assertNull(parser.parse(byteArrayOf(0x00)))
        assertNull(parser.parse(byteArrayOf(0x01, 0x2C)))
        assertNull(parser.parse(byteArrayOf(0x01, 0xFF.toByte(), 0xFF.toByte()))) // 65535 bpm
    }
}
