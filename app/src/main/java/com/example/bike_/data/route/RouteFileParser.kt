package com.example.bike_.data.route

import com.example.bike_.domain.model.LoadedRoute
import com.example.bike_.domain.model.RoutePoint
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Parses route files for map overlay. Supports GPX (XML) and FIT (binary, record positions).
 * Not a full FIT decoder; extracts lat/lon from record messages (global msg 20, fields 0/1).
 */
object RouteFileParser {

    class InvalidRouteFileException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun parse(inputStream: InputStream): LoadedRoute {
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) throw InvalidRouteFileException("File is empty")

        if (isFit(bytes)) {
            return try {
                parseFit(bytes).also { if (it.points.isEmpty()) throw InvalidRouteFileException("FIT file has no route points") }
            } catch (e: InvalidRouteFileException) { throw e }
              catch (_: Exception) {
                try { GpxParser.parse(ByteArrayInputStream(bytes)) }
                catch (e: GpxParser.InvalidGpxException) { throw InvalidRouteFileException(e.message ?: "Invalid route file", e) }
                catch (e: Exception) { throw InvalidRouteFileException("Invalid FIT/GPX file format", e) }
              }
        }

        return try {
            GpxParser.parse(ByteArrayInputStream(bytes))
        } catch (e: GpxParser.InvalidGpxException) {
            throw InvalidRouteFileException(e.message ?: "Invalid GPX file format", e)
        } catch (e: Exception) {
            throw InvalidRouteFileException("Invalid route file format", e)
        }
    }

    private fun isFit(bytes: ByteArray): Boolean {
        if (bytes.size < 14) return false
        val headerSize = bytes[0].toInt() and 0xFF
        if (headerSize != 12 && headerSize != 14) return false
        return bytes[8] == '.'.code.toByte() && bytes[9] == 'F'.code.toByte() &&
            bytes[10] == 'I'.code.toByte() && bytes[11] == 'T'.code.toByte()
    }

    private data class FieldDef(val num: Int, val size: Int, val baseType: Int)
    private data class MsgDef(
        val globalMsgNum: Int,
        val archLittle: Boolean,
        val fields: List<FieldDef>,
        val devFields: List<FieldDef>
    )

    private fun parseFit(bytes: ByteArray): LoadedRoute {
        if (bytes.size < 14) return LoadedRoute(points = emptyList())
        val headerSize = bytes[0].toInt() and 0xFF
        val dataSize = le32(bytes, 4)
        val dataEnd = minOf(bytes.size, headerSize + dataSize).coerceAtLeast(headerSize)
        val defs = HashMap<Int, MsgDef>()
        val points = ArrayList<RoutePoint>(1024)

        var i = headerSize
        var lastTimestamp: Long? = null

        while (i < dataEnd) {
            val header = bytes[i].toInt() and 0xFF
            i++

            // Compressed timestamp header
            if ((header and 0x80) != 0) {
                val localMesg = (header shr 5) and 0x03
                val timeOffset = header and 0x1F
                val def = defs[localMesg] ?: break
                if (lastTimestamp != null) {
                    val base = lastTimestamp and 0x1F.inv()
                    var ts = base + timeOffset
                    if (ts <= lastTimestamp) ts += 0x20
                    lastTimestamp = ts
                }
                val rec = readDataMessage(bytes, i, def)
                i = rec.nextIndex
                extractRecordPoint(def, rec.values, lastTimestamp)?.let { points.add(it) }
                // update timestamp if present in record
                rec.values[253]?.let { v ->
                    if (v is Long) lastTimestamp = v else if (v is Int) lastTimestamp = v.toLong() and 0xFFFFFFFFL
                }
                continue
            }

            val isDefinition = (header and 0x40) != 0
            val hasDev = (header and 0x20) != 0
            val localMesg = header and 0x0F

            if (isDefinition) {
                if (i + 5 > dataEnd) break
                i++ // reserved
                val arch = bytes[i].toInt() and 0xFF
                i++
                val archLittle = arch == 0
                val globalMsgNum = readU16(bytes, i, archLittle)
                i += 2
                val numFields = bytes[i].toInt() and 0xFF
                i++
                val fields = ArrayList<FieldDef>(numFields)
                for (f in 0 until numFields) {
                    if (i + 3 > bytes.size) break
                    val fn = bytes[i].toInt() and 0xFF
                    val sz = bytes[i + 1].toInt() and 0xFF
                    val bt = bytes[i + 2].toInt() and 0xFF
                    fields.add(FieldDef(fn, sz, bt))
                    i += 3
                }
                val devFields = ArrayList<FieldDef>()
                if (hasDev) {
                    if (i < bytes.size) {
                        val nDev = bytes[i].toInt() and 0xFF
                        i++
                        for (d in 0 until nDev) {
                            if (i + 3 > bytes.size) break
                            val fn = bytes[i].toInt() and 0xFF
                            val sz = bytes[i + 1].toInt() and 0xFF
                            val bt = bytes[i + 2].toInt() and 0xFF
                            devFields.add(FieldDef(fn, sz, bt))
                            i += 3
                        }
                    }
                }
                defs[localMesg] = MsgDef(globalMsgNum, archLittle, fields, devFields)
            } else {
                val def = defs[localMesg] ?: break
                val rec = readDataMessage(bytes, i, def)
                i = rec.nextIndex
                rec.values[253]?.let { v ->
                    lastTimestamp = when (v) {
                        is Long -> v
                        is Int -> v.toLong() and 0xFFFFFFFFL
                        else -> lastTimestamp
                    }
                }
                extractRecordPoint(def, rec.values, lastTimestamp)?.let { points.add(it) }
            }
        }

        val cum = com.example.bike_.domain.util.RouteNavigation.cumulativeDistances(points)
        return LoadedRoute(
            name = "FIT route",
            points = points,
            distanceMeters = cum.lastOrNull() ?: 0.0,
            elevationGainMeters = com.example.bike_.domain.util.RouteNavigation.computeElevationGainMeters(points),
            cumulativeDistancesMeters = cum
        )
    }

    private data class DataRead(val values: Map<Int, Any?>, val nextIndex: Int)

    private fun readDataMessage(bytes: ByteArray, start: Int, def: MsgDef): DataRead {
        var i = start
        val map = HashMap<Int, Any?>(def.fields.size)
        for (f in def.fields) {
            if (i + f.size > bytes.size) { i = bytes.size; break }
            val v = decodeField(bytes, i, f, def.archLittle)
            map[f.num] = v
            i += f.size
        }
        for (f in def.devFields) {
            if (i + f.size > bytes.size) { i = bytes.size; break }
            i += f.size // ignore developer fields
        }
        return DataRead(map, i)
    }

    private fun decodeField(bytes: ByteArray, off: Int, f: FieldDef, little: Boolean): Any? {
        val sz = f.size
        if (sz <= 0) return null
        // Treat single-byte/standard ints; ignore arrays/strings for our needs
        return when (f.baseType and 0x1F) {
            0x00 -> bytes[off].toInt() and 0xFF // enum/uint8
            0x01 -> bytes[off].toInt() // sint8
            0x02 -> if (sz == 1) bytes[off].toInt() and 0xFF else readUInt(bytes, off, sz, little) // uint8
            0x83 -> if (sz >= 2) readS16(bytes, off, little) else null // sint16
            0x84 -> if (sz >= 2) readU16(bytes, off, little) else null // uint16
            0x85 -> if (sz >= 4) readS32(bytes, off, little) else null // sint32
            0x86 -> if (sz >= 4) readU32(bytes, off, little) else null // uint32
            0x07 -> null // string
            0x0D -> if (sz == 1) bytes[off].toInt() and 0xFF else null // byte
            else -> when (sz) {
                1 -> bytes[off].toInt() and 0xFF
                2 -> readU16(bytes, off, little)
                4 -> readU32(bytes, off, little)
                else -> null
            }
        }
    }

    private fun extractRecordPoint(def: MsgDef, values: Map<Int, Any?>, ts: Long?): RoutePoint? {
        // Record message global number is 20
        if (def.globalMsgNum != 20) return null
        val latRaw = toInt(values[0]) ?: return null
        val lonRaw = toInt(values[1]) ?: return null
        // FIT invalid/sentinel
        if (latRaw == 0x7FFFFFFF || lonRaw == 0x7FFFFFFF) return null
        val lat = semicirclesToDeg(latRaw)
        val lon = semicirclesToDeg(lonRaw)
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        if (abs(lat) < 1e-9 && abs(lon) < 1e-9) return null
        val alt = decodeAltitude(values[2])
        return RoutePoint(lat, lon, alt)
    }

    private fun toInt(v: Any?): Int? = when (v) {
        is Int -> v
        is Long -> v.toInt()
        is Short -> v.toInt()
        is Byte -> v.toInt()
        else -> null
    }

    private fun semicirclesToDeg(semi: Int): Double = semi.toDouble() * (180.0 / 2147483648.0)

    private fun decodeAltitude(v: Any?): Double? {
        val raw = toInt(v) ?: return null
        // Our exporter stores altitude as uint16 with scale 5 offset 500; invalid 0xFFFF
        if (raw == 0xFFFF || raw == 0xFFFE) return null
        // Heuristic: if looks like encoded uint16 altitude, decode; else treat as meters
        return if (raw in 0..65534) (raw / 5.0) - 500.0 else raw.toDouble()
    }

    private fun readU16(bytes: ByteArray, off: Int, little: Boolean): Int {
        return if (little) (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8)
        else ((bytes[off].toInt() and 0xFF) shl 8) or (bytes[off + 1].toInt() and 0xFF)
    }

    private fun readS16(bytes: ByteArray, off: Int, little: Boolean): Int {
        val u = readU16(bytes, off, little)
        return if (u >= 0x8000) u - 0x10000 else u
    }

    private fun readU32(bytes: ByteArray, off: Int, little: Boolean): Long {
        return if (little) {
            (bytes[off].toLong() and 0xFF) or
                ((bytes[off + 1].toLong() and 0xFF) shl 8) or
                ((bytes[off + 2].toLong() and 0xFF) shl 16) or
                ((bytes[off + 3].toLong() and 0xFF) shl 24)
        } else {
            ((bytes[off].toLong() and 0xFF) shl 24) or
                ((bytes[off + 1].toLong() and 0xFF) shl 16) or
                ((bytes[off + 2].toLong() and 0xFF) shl 8) or
                (bytes[off + 3].toLong() and 0xFF)
        }
    }

    private fun readS32(bytes: ByteArray, off: Int, little: Boolean): Int {
        val u = readU32(bytes, off, little)
        val v = u.toInt()
        return v
    }

    private fun readUInt(bytes: ByteArray, off: Int, size: Int, little: Boolean): Long {
        var v = 0L
        if (little) {
            for (k in size - 1 downTo 0) v = (v shl 8) or (bytes[off + k].toLong() and 0xFF)
        } else {
            for (k in 0 until size) v = (v shl 8) or (bytes[off + k].toLong() and 0xFF)
        }
        return v
    }

    private fun le32(bytes: ByteArray, off: Int): Int {
        return (bytes[off].toInt() and 0xFF) or
            ((bytes[off + 1].toInt() and 0xFF) shl 8) or
            ((bytes[off + 2].toInt() and 0xFF) shl 16) or
            ((bytes[off + 3].toInt() and 0xFF) shl 24)
    }
}
