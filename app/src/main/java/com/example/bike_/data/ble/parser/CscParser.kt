package com.example.bike_.data.ble.parser

import kotlin.math.abs

data class CscMeasurement(
    val cadenceRpm: Float? = null,
    val speedKmh: Float? = null
)

/**
 * Stateful parser for Cycling Speed and Cadence Measurement (0x2A5B).
 * Maintains previous cumulative values/timestamps per device to compute rates.
 */
class CscParser {
    private var lastWheelRevs: Long? = null
    private var lastWheelEventTime: Int? = null
    private var lastCrankRevs: Int? = null
    private var lastCrankEventTime: Int? = null

    fun reset() {
        lastWheelRevs = null
        lastWheelEventTime = null
        lastCrankRevs = null
        lastCrankEventTime = null
    }

    fun parse(data: ByteArray, wheelCircumferenceMm: Float): CscMeasurement? {
        if (data.isEmpty()) return null
        val flags = data[0].toInt() and 0xFF
        val wheelPresent = (flags and 0x01) != 0
        val crankPresent = (flags and 0x02) != 0
        var index = 1

        var speedKmh: Float? = null
        var cadenceRpm: Float? = null

        if (wheelPresent) {
            if (data.size < index + 6) return null
            val cumWheelRevs = uInt32Le(data, index)
            index += 4
            val wheelEventTime = uInt16Le(data, index) // 1/1024 s
            index += 2

            val prevRevs = lastWheelRevs
            val prevTime = lastWheelEventTime
            if (prevRevs != null && prevTime != null) {
                val revDelta = rolloverDelta32(prevRevs, cumWheelRevs)
                val timeDeltaTicks = rolloverDelta16(prevTime, wheelEventTime)
                if (timeDeltaTicks > 0 && revDelta >= 0) {
                    val timeSec = timeDeltaTicks / 1024.0
                    if (timeSec > 0.0) {
                        val distanceM = revDelta * (wheelCircumferenceMm / 1000.0)
                        val speed = (distanceM / timeSec) * 3.6
                        if (speed.isFinite() && speed in 0.0..120.0) {
                            speedKmh = speed.toFloat()
                        }
                    }
                }
            }
            lastWheelRevs = cumWheelRevs
            lastWheelEventTime = wheelEventTime
        }

        if (crankPresent) {
            if (data.size < index + 4) return null
            val cumCrankRevs = uInt16Le(data, index)
            index += 2
            val crankEventTime = uInt16Le(data, index) // 1/1024 s
            index += 2

            val prevRevs = lastCrankRevs
            val prevTime = lastCrankEventTime
            if (prevRevs != null && prevTime != null) {
                val revDelta = rolloverDelta16(prevRevs, cumCrankRevs)
                val timeDeltaTicks = rolloverDelta16(prevTime, crankEventTime)
                if (timeDeltaTicks > 0 && revDelta >= 0) {
                    val timeSec = timeDeltaTicks / 1024.0
                    if (timeSec > 0.0) {
                        val cadence = (revDelta / timeSec) * 60.0
                        if (cadence.isFinite() && cadence in 0.0..300.0) {
                            cadenceRpm = cadence.toFloat()
                        }
                    }
                }
            }
            lastCrankRevs = cumCrankRevs
            lastCrankEventTime = crankEventTime
        }

        return if (speedKmh != null || cadenceRpm != null) {
            CscMeasurement(cadenceRpm = cadenceRpm, speedKmh = speedKmh)
        } else {
            // First sample initializes state; no rate yet
            CscMeasurement()
        }
    }

    private fun rolloverDelta16(prev: Int, curr: Int): Int {
        val p = prev and 0xFFFF
        val c = curr and 0xFFFF
        return if (c >= p) c - p else (0x10000 - p) + c
    }

    private fun rolloverDelta32(prev: Long, curr: Long): Long {
        val p = prev and 0xFFFFFFFFL
        val c = curr and 0xFFFFFFFFL
        return if (c >= p) c - p else (0x100000000L - p) + c
    }
}
