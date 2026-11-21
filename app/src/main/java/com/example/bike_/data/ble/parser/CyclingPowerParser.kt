package com.example.bike_.data.ble.parser

class CyclingPowerParser {
    fun parse(data: ByteArray): Int? {
        // Flags (uint16) + Instantaneous Power (sint16) are mandatory
        if (data.size < 4) return null
        val power = sInt16Le(data, 2)
        return power.takeIf { it in 0..4000 }
    }
}
