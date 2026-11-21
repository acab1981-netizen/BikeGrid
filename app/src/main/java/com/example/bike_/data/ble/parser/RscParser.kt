package com.example.bike_.data.ble.parser

/**
 * Parser for the Running Speed and Cadence Measurement characteristic (0x2A53, service 0x1814).
 *
 * Layout (RSC spec):
 *   flags       uint8   bit0 stride length present, bit1 total distance present, bit2 running=1
 *   speed       uint16  1/256 m/s
 *   cadence     uint8   steps/min
 *   strideLen   uint16  0.1 m   (optional)
 *   totalDist   uint32  1 m     (optional)
 */
data class RscMeasurement(
    val speedKmh: Float,
    val cadenceSpm: Int,
    val strideLengthM: Float? = null,
    val totalDistanceM: Long? = null,
    val isRunning: Boolean = false
)

object RscParser {
    private const val FLAG_STRIDE_LENGTH = 0x01
    private const val FLAG_TOTAL_DISTANCE = 0x02
    private const val FLAG_RUNNING = 0x04

    /** Beyond ~108 km/h a foot pod reading is noise, not a runner. */
    private const val MAX_PLAUSIBLE_SPEED_MPS = 30.0

    fun parse(data: ByteArray): RscMeasurement? {
        // flags + mandatory speed + mandatory cadence
        if (data.size < 4) return null

        val flags = data[0].toInt() and 0xFF
        val speedMps = uInt16Le(data, 1) / 256.0
        val cadence = data[3].toInt() and 0xFF

        if (!speedMps.isFinite() || speedMps < 0.0 || speedMps > MAX_PLAUSIBLE_SPEED_MPS) return null
        if (cadence !in 0..255) return null

        var offset = 4
        val strideLengthM = if (flags and FLAG_STRIDE_LENGTH != 0 && offset + 2 <= data.size) {
            val value = uInt16Le(data, offset) / 10.0
            offset += 2
            value.toFloat()
        } else {
            null
        }
        val totalDistanceM = if (flags and FLAG_TOTAL_DISTANCE != 0 && offset + 4 <= data.size) {
            val value = uInt32Le(data, offset)
            offset += 4
            value
        } else {
            null
        }

        return RscMeasurement(
            speedKmh = (speedMps * 3.6).toFloat(),
            cadenceSpm = cadence,
            strideLengthM = strideLengthM,
            totalDistanceM = totalDistanceM,
            isRunning = flags and FLAG_RUNNING != 0
        )
    }
}
