package com.example.bike_.data.ble.parser

import kotlin.math.roundToInt

data class IndoorBikeData(
    val speedKmh: Float? = null,
    val cadenceRpm: Float? = null,
    val powerWatts: Int? = null
)

object FtmsParser {
    // Indoor Bike Data 0x2AD2 flags (FTMS spec)
    private const val FLAG_MORE_DATA = 1 shl 0 // 0 = instantaneous speed present
    private const val FLAG_AVG_SPEED = 1 shl 1
    private const val FLAG_INSTANT_CADENCE = 1 shl 2
    private const val FLAG_AVG_CADENCE = 1 shl 3
    private const val FLAG_TOTAL_DISTANCE = 1 shl 4
    private const val FLAG_RESISTANCE_LEVEL = 1 shl 5
    private const val FLAG_INSTANT_POWER = 1 shl 6
    private const val FLAG_AVG_POWER = 1 shl 7
    private const val FLAG_EXPENDED_ENERGY = 1 shl 8
    private const val FLAG_HEART_RATE = 1 shl 9
    private const val FLAG_METABOLIC_EQUIV = 1 shl 10
    private const val FLAG_ELAPSED_TIME = 1 shl 11
    private const val FLAG_REMAINING_TIME = 1 shl 12

    fun parseIndoorBikeData(value: ByteArray): IndoorBikeData? {
        if (value.size < 2) return null
        var i = 0
        val flags = readUInt16Le(value, i) ?: return null
        i += 2

        var speedKmh: Float? = null
        if (flags and FLAG_MORE_DATA == 0) {
            val raw = readUInt16Le(value, i) ?: return IndoorBikeData(speedKmh, null, null)
            i += 2
            // uint16, 0.01 km/h
            speedKmh = raw / 100f
        }

        if (flags and FLAG_AVG_SPEED != 0) {
            if (i + 2 > value.size) return IndoorBikeData(speedKmh, null, null)
            i += 2
        }
        var cadenceRpm: Float? = null
        if (flags and FLAG_INSTANT_CADENCE != 0) {
            val raw = readUInt16Le(value, i) ?: return IndoorBikeData(speedKmh, cadenceRpm, null)
            i += 2
            // uint16, 0.5 rpm
            cadenceRpm = raw / 2f
        }
        if (flags and FLAG_AVG_CADENCE != 0) {
            if (i + 2 > value.size) return IndoorBikeData(speedKmh, cadenceRpm, null)
            i += 2
        }
        if (flags and FLAG_TOTAL_DISTANCE != 0) {
            if (i + 3 > value.size) return IndoorBikeData(speedKmh, cadenceRpm, null)
            i += 3 // uint24
        }
        if (flags and FLAG_RESISTANCE_LEVEL != 0) {
            if (i + 2 > value.size) return IndoorBikeData(speedKmh, cadenceRpm, null)
            i += 2 // sint16
        }
        var powerWatts: Int? = null
        if (flags and FLAG_INSTANT_POWER != 0) {
            val raw = readSInt16Le(value, i) ?: return IndoorBikeData(speedKmh, cadenceRpm, powerWatts)
            i += 2
            powerWatts = raw
        }
        if (flags and FLAG_AVG_POWER != 0) {
            if (i + 2 > value.size) return IndoorBikeData(speedKmh, cadenceRpm, powerWatts)
            i += 2
        }
        if (flags and FLAG_EXPENDED_ENERGY != 0) {
            if (i + 5 > value.size) return IndoorBikeData(speedKmh, cadenceRpm, powerWatts)
            i += 5
        }
        if (flags and FLAG_HEART_RATE != 0) {
            if (i + 1 > value.size) return IndoorBikeData(speedKmh, cadenceRpm, powerWatts)
            i += 1
        }
        if (flags and FLAG_METABOLIC_EQUIV != 0) {
            if (i + 1 > value.size) return IndoorBikeData(speedKmh, cadenceRpm, powerWatts)
            i += 1
        }
        if (flags and FLAG_ELAPSED_TIME != 0) {
            if (i + 2 > value.size) return IndoorBikeData(speedKmh, cadenceRpm, powerWatts)
            i += 2
        }
        if (flags and FLAG_REMAINING_TIME != 0) {
            if (i + 2 > value.size) return IndoorBikeData(speedKmh, cadenceRpm, powerWatts)
            i += 2
        }
        return IndoorBikeData(speedKmh, cadenceRpm, powerWatts)
    }

    fun buildRequestControl(): ByteArray = byteArrayOf(0x00)
    fun buildReset(): ByteArray = byteArrayOf(0x01)
    fun buildStartOrResume(): ByteArray = byteArrayOf(0x07)
    fun buildStopPause(): ByteArray = byteArrayOf(0x08, 0x02) // stop, pause
    fun buildSetTargetPower(watts: Int): ByteArray {
        val w = watts.coerceIn(0, 4000)
        return byteArrayOf(
            0x05,
            (w and 0xFF).toByte(),
            ((w shr 8) and 0xFF).toByte()
        )
    }
    fun buildSetTargetResistanceLevel(percent: Int): ByteArray {
        // FTMS: unitless, resolution 0.1. Many trainers accept 0.1% steps; clamp 0-100%.
        val v = (percent.coerceIn(0, 100) * 10).coerceIn(0, 2000)
        return byteArrayOf(
            0x04,
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte()
        )
    }

    data class ControlPointResponse(val requestOpCode: Int, val resultCode: Int, val raw: ByteArray)

    fun parseControlPointResponse(value: ByteArray): ControlPointResponse? {
        if (value.size < 3) return null
        val op = value[0].toInt() and 0xFF
        if (op != 0x80) return null
        val req = value[1].toInt() and 0xFF
        val res = value[2].toInt() and 0xFF
        return ControlPointResponse(req, res, value)
    }

    fun isSuccess(response: ControlPointResponse): Boolean = response.resultCode == 0x01

    private fun readUInt16Le(bytes: ByteArray, offset: Int): Int? {
        if (offset + 2 > bytes.size) return null
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }
    private fun readSInt16Le(bytes: ByteArray, offset: Int): Int? {
        val u = readUInt16Le(bytes, offset) ?: return null
        var s = u
        if (s >= 0x8000) s -= 0x10000
        return s
    }
}
