package com.example.bike_.data.ble.parser

internal fun uInt16Le(data: ByteArray, offset: Int): Int {
    val b0 = data[offset].toInt() and 0xFF
    val b1 = data[offset + 1].toInt() and 0xFF
    return b0 or (b1 shl 8)
}

internal fun sInt16Le(data: ByteArray, offset: Int): Int {
    val v = uInt16Le(data, offset)
    return if (v and 0x8000 != 0) v - 0x10000 else v
}

internal fun uInt32Le(data: ByteArray, offset: Int): Long {
    val b0 = data[offset].toLong() and 0xFF
    val b1 = data[offset + 1].toLong() and 0xFF
    val b2 = data[offset + 2].toLong() and 0xFF
    val b3 = data[offset + 3].toLong() and 0xFF
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}
