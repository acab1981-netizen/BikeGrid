package com.example.bike_.data.ble.parser

class HeartRateParser {
    fun parse(data: ByteArray): Int? {
        if (data.isEmpty()) return null
        val flags = data[0].toInt() and 0xFF
        val hrFormatUInt16 = (flags and 0x01) != 0
        var index = 1
        val bpm = if (hrFormatUInt16) {
            if (data.size < index + 2) return null
            val value = uInt16Le(data, index)
            index += 2
            value
        } else {
            if (data.size < index + 1) return null
            val value = data[index].toInt() and 0xFF
            index += 1
            value
        }
        return bpm.takeIf { it in 0..300 }
    }
}
