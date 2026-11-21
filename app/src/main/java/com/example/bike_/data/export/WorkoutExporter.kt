package com.example.bike_.data.export

import com.example.bike_.data.db.entity.LapEntity
import com.example.bike_.data.db.entity.TrackPointEntity
import com.example.bike_.data.db.entity.WorkoutEntity
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    GPX("gpx", "application/gpx+xml", "GPX"),
    TCX("tcx", "application/vnd.garmin.tcx+xml", "TCX"),
    FIT("fit", "application/vnd.ant.fit", "FIT")
}

class WorkoutExporter {

    fun export(workout: WorkoutEntity, points: List<TrackPointEntity>, laps: List<LapEntity> = emptyList(), format: ExportFormat): ByteArray {
        return when (format) {
            ExportFormat.GPX -> exportToGpx(workout, points).toByteArray(Charsets.UTF_8)
            ExportFormat.TCX -> exportToTcx(workout, points).toByteArray(Charsets.UTF_8)
            ExportFormat.FIT -> exportToFit(workout, points, laps)
        }
    }

    fun exportToGpx(workout: WorkoutEntity, points: List<TrackPointEntity>): String {
        val sb = StringBuilder()
        val name = buildWorkoutName(workout)
        val desc = buildDescription(workout)

        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append(
            """<gpx version="1.1" creator="Bike_""""
        ).append(" xmlns=\"http://www.topografix.com/GPX/1/1\"")
            .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
            .append(" xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\"")
            .append(" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd\">")
            .append('\n')

        sb.append("  <metadata>\n")
        sb.append("    <name>").append(escapeXml(name)).append("</name>\n")
        sb.append("    <desc>").append(escapeXml(desc)).append("</desc>\n")
        sb.append("    <time>").append(formatIso8601(workout.startTimeMs)).append("</time>\n")
        sb.append("  </metadata>\n")

        sb.append("  <trk>\n")
        sb.append("    <name>").append(escapeXml(name)).append("</name>\n")
        sb.append("    <type>").append(escapeXml(mapWorkoutTypeGpx(workout.workoutType))).append("</type>\n")
        sb.append("    <trkseg>\n")

        for (p in points) {
            val hasGps = isValidGps(p.latitude, p.longitude)
            if (hasGps) {
                sb.append("      <trkpt lat=\"").append(formatCoord(p.latitude))
                    .append("\" lon=\"").append(formatCoord(p.longitude)).append("\">\n")
            } else {
                // GPX 1.1 requires lat/lon on trkpt; for indoor/no-GPS points omit coordinates by skipping trkpt.
                continue
            }
            p.altitudeMeters?.let {
                sb.append("        <ele>").append(formatNumber(it)).append("</ele>\n")
            }
            sb.append("        <time>").append(formatIso8601(p.timestampMs)).append("</time>\n")

            val speedMs = p.speedKmh?.let { it / 3.6f }
            val cad = p.cadenceRpm?.roundToInt() ?: p.stepCadenceSpm
            val hasExt = p.heartRateBpm != null || cad != null || p.powerWatts != null
            if (hasExt) {
                sb.append("        <extensions>\n")
                // Power at TrackPointExtension sibling level is commonly used; also include gpxtpx extensions.
                p.powerWatts?.let { sb.append("          <power>").append(it).append("</power>\n") }
                sb.append("          <gpxtpx:TrackPointExtension>\n")
                speedMs?.let {
                    sb.append("            <gpxtpx:speed>").append(formatNumber(it.toDouble())).append("</gpxtpx:speed>\n")
                }
                p.heartRateBpm?.let {
                    sb.append("            <gpxtpx:hr>").append(it).append("</gpxtpx:hr>\n")
                }
                cad?.let {
                    sb.append("            <gpxtpx:cad>").append(it.coerceAtLeast(0)).append("</gpxtpx:cad>\n")
                }
                p.powerWatts?.let {
                    sb.append("            <gpxtpx:watts>").append(it).append("</gpxtpx:watts>\n")
                }
                sb.append("          </gpxtpx:TrackPointExtension>\n")
                sb.append("        </extensions>\n")
            }

            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    fun exportToTcx(workout: WorkoutEntity, points: List<TrackPointEntity>): String {
        val sb = StringBuilder()
        val sport = mapWorkoutTypeTcx(workout.workoutType)
        val startIso = formatIso8601(workout.startTimeMs)
        val endMs = workout.endTimeMs ?: (if (points.isNotEmpty()) points.last().timestampMs else workout.startTimeMs)
        val totalTimeSec = max(0.0, (if (workout.elapsedTimeMs > 0) workout.elapsedTimeMs else (endMs - workout.startTimeMs)).toDouble() / 1000.0)
        val distanceM = if (workout.distanceMeters > 0.0) workout.distanceMeters else computeTotalDistance(points)
        val maxSpeedMs = (workout.maxSpeedKmh / 3.6f).toDouble().coerceAtLeast(0.0)

        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("<TrainingCenterDatabase xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\"")
            .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
            .append(" xmlns:ns3=\"http://www.garmin.com/xmlschemas/ActivityExtension/v2\"")
            .append(" xsi:schemaLocation=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2 http://www.garmin.com/xmlschemas/TrainingCenterDatabasev2.xsd\">")
            .append('\n')
        sb.append("  <Activities>\n")
        sb.append("    <Activity Sport=\"").append(sport).append("\">\n")
        sb.append("      <Id>").append(startIso).append("</Id>\n")
        sb.append("      <Lap StartTime=\"").append(startIso).append("\">\n")
        sb.append("        <TotalTimeSeconds>").append(formatNumber(totalTimeSec)).append("</TotalTimeSeconds>\n")
        sb.append("        <DistanceMeters>").append(formatNumber(distanceM)).append("</DistanceMeters>\n")
        sb.append("        <MaximumSpeed>").append(formatNumber(maxSpeedMs)).append("</MaximumSpeed>\n")
        sb.append("        <Calories>0</Calories>\n")
        workout.avgHeartRateBpm?.let {
            sb.append("        <AverageHeartRateBpm><Value>").append(it).append("</Value></AverageHeartRateBpm>\n")
        }
        workout.maxHeartRateBpm?.let {
            sb.append("        <MaximumHeartRateBpm><Value>").append(it).append("</Value></MaximumHeartRateBpm>\n")
        }
        sb.append("        <Intensity>Active</Intensity>\n")
        sb.append("        <TriggerMethod>Manual</TriggerMethod>\n")
        sb.append("        <Track>\n")

        var cumDist = 0.0
        var prev: TrackPointEntity? = null
        for (p in points) {
            if (prev != null) cumDist += haversineMeters(prev.latitude, prev.longitude, p.latitude, p.longitude)
            prev = p
            sb.append("          <Trackpoint>\n")
            sb.append("            <Time>").append(formatIso8601(p.timestampMs)).append("</Time>\n")
            sb.append("            <Position><LatitudeDegrees>").append(formatCoord(p.latitude))
                .append("</LatitudeDegrees><LongitudeDegrees>").append(formatCoord(p.longitude))
                .append("</LongitudeDegrees></Position>\n")
            p.altitudeMeters?.let {
                sb.append("            <AltitudeMeters>").append(formatNumber(it)).append("</AltitudeMeters>\n")
            }
            sb.append("            <DistanceMeters>").append(formatNumber(cumDist)).append("</DistanceMeters>\n")
            p.heartRateBpm?.let {
                sb.append("            <HeartRateBpm><Value>").append(it).append("</Value></HeartRateBpm>\n")
            }
            val pointCad = p.cadenceRpm?.roundToInt() ?: p.stepCadenceSpm
            pointCad?.let {
                sb.append("            <Cadence>").append(it.coerceAtLeast(0)).append("</Cadence>\n")
            }
            val speedMs = p.speedKmh?.let { it / 3.6f }
            val hasExt = speedMs != null || p.powerWatts != null
            if (hasExt) {
                sb.append("            <Extensions><ns3:TPX>\n")
                speedMs?.let { sb.append("              <ns3:Speed>").append(formatNumber(it.toDouble())).append("</ns3:Speed>\n") }
                p.powerWatts?.let { sb.append("              <ns3:Watts>").append(it).append("</ns3:Watts>\n") }
                sb.append("            </ns3:TPX></Extensions>\n")
            }
            sb.append("          </Trackpoint>\n")
        }

        sb.append("        </Track>\n")
        sb.append("      </Lap>\n")
        sb.append("      <Creator xsi:type=\"Device_t\"><Name>Bike_</Name><UnitId>0</UnitId><ProductID>0</ProductID></Creator>\n")
        sb.append("    </Activity>\n")
        sb.append("  </Activities>\n")
        sb.append("</TrainingCenterDatabase>\n")
        return sb.toString()
    }

    /**
     * Minimal FIT activity encoder (file_id, event start/stop, records, lap(s), session, activity).
     * Produces a valid FIT container suitable for import to Strava/Garmin-compatible tools.
     */
    fun exportToFit(workout: WorkoutEntity, points: List<TrackPointEntity>, laps: List<LapEntity> = emptyList()): ByteArray {
        val data = ByteArrayOutputStream()
        val startFit = toFitTimestamp(workout.startTimeMs)
        val endMs = workout.endTimeMs ?: (if (points.isNotEmpty()) points.last().timestampMs else workout.startTimeMs + workout.elapsedTimeMs)
        val endFit = toFitTimestamp(endMs)
        val totalElapsed = max(1.0, (if (workout.elapsedTimeMs > 0) workout.elapsedTimeMs else (endMs - workout.startTimeMs)).toDouble() / 1000.0)
        val totalTimer = max(1.0, (if (workout.movingTimeMs > 0) workout.movingTimeMs else workout.elapsedTimeMs).toDouble() / 1000.0)
        val distance = if (workout.distanceMeters > 0.0) workout.distanceMeters else computeTotalDistance(points)
        val sport = mapWorkoutTypeFitSport(workout.workoutType)

        // file_id (mesg 0): type, manufacturer, product, serial, time_created
        data.write(defMsg(0, 0, listOf(F(0,1,0), F(1,2,132), F(2,2,132), F(3,4,140), F(4,4,134))))
        data.write(dataMsg(0, byteArrayOf(4) + le16(1) + le16(0) + le32(1) + le32(startFit)))
        // event start (mesg 21)
        data.write(defMsg(1, 21, listOf(F(253,4,134), F(0,1,0), F(1,1,0))))
        data.write(dataMsg(1, le32(startFit) + byteArrayOf(0, 0)))
        // record (mesg 20)
        data.write(defMsg(2, 20, listOf(
            F(253,4,134), // timestamp
            F(0,4,133),   // position_lat (sint32)
            F(1,4,133),   // position_long (sint32)
            F(2,2,132),   // altitude (uint16, scale 5, offset 500)
            F(5,4,134),   // distance (uint32, scale 100)
            F(6,2,132),   // speed (uint16, scale 1000)
            F(3,1,2),     // heart_rate
            F(4,1,2),     // cadence
            F(7,2,132)    // power
        )))
        var cumDist = 0.0
        var prev: TrackPointEntity? = null
        for (p in points) {
            val hasGps = isValidGps(p.latitude, p.longitude)
            if (prev != null && hasGps && isValidGps(prev.latitude, prev.longitude)) {
                cumDist += haversineMeters(prev.latitude, prev.longitude, p.latitude, p.longitude)
            }
            prev = p
            val ts = toFitTimestamp(p.timestampMs)
            val lat = if (hasGps) toSemicircles(p.latitude) else 0x7FFFFFFF
            val lon = if (hasGps) toSemicircles(p.longitude) else 0x7FFFFFFF
            val alt = encodeAltitude(p.altitudeMeters)
            val dist = ((cumDist) * 100.0).roundToLong().coerceIn(0, 0xFFFFFFFFL).toInt()
            val speed = (((p.speedKmh ?: 0f) / 3.6f) * 1000.0).roundToInt().coerceIn(0, 65535)
            val hr = (p.heartRateBpm ?: 0xFF).coerceIn(0, 255)
            val cad = (p.cadenceRpm?.roundToInt() ?: p.stepCadenceSpm ?: 0xFF).coerceIn(0, 255)
            val pwr = (p.powerWatts ?: 0xFFFF).coerceIn(0, 65535)
            val payload = le32(ts) + le32(lat) + le32(lon) + le16(alt) + le32(dist) + le16(speed) +
                byteArrayOf(hr.toByte(), cad.toByte()) + le16(pwr)
            data.write(dataMsg(2, payload))
        }
        // event stop
        data.write(dataMsg(1, le32(endFit) + byteArrayOf(0, 1)))
        // lap (mesg 19)
        val maxSpeedEnc = ((workout.maxSpeedKmh / 3.6f) * 1000.0).roundToInt().coerceIn(0, 65535)
        data.write(defMsg(3, 19, listOf(
            F(254,2,132), // message_index
            F(253,4,134), // timestamp
            F(2,4,134),   // start_time
            F(7,4,134),   // total_elapsed_time (ms, scale 1000)
            F(8,4,134),   // total_timer_time
            F(9,4,134),   // total_distance (cm, scale 100)
            F(13,2,132),  // max_speed
            F(11,1,2),    // avg_heart_rate
            F(16,1,2),    // max_heart_rate
            F(17,1,2),    // avg_cadence
            F(19,2,132),  // avg_power
            F(20,2,132),  // max_power / not used as NP but keeps slot? better normalized_power not in minimal; keep avg/max
            F(25,1,0),    // sport
            F(0,1,0)      // event
        )))
        val lapsToWrite = if (laps.isNotEmpty()) laps else listOf(
            LapEntity(
                workoutId = workout.id,
                lapNumber = 1,
                startTimeMs = workout.startTimeMs,
                endTimeMs = endMs,
                durationSec = totalElapsed.roundToInt(),
                distanceMeters = distance,
                avgSpeedKmh = if (totalTimer > 0) ((distance / totalTimer) * 3.6).toFloat() else 0f,
                avgHeartRateBpm = workout.avgHeartRateBpm,
                maxHeartRateBpm = workout.maxHeartRateBpm,
                avgPowerWatts = workout.avgPowerWatts,
                normalizedPowerWatts = workout.normalizedPowerWatts
            )
        )
        lapsToWrite.forEachIndexed { idx, lap ->
            val lapEnd = toFitTimestamp(lap.endTimeMs)
            val lapStart = toFitTimestamp(lap.startTimeMs)
            val lapElapsed = lap.durationSec.coerceAtLeast(1) * 1000
            val lapTimer = lapElapsed
            val lapDistEnc = (lap.distanceMeters * 100.0).roundToLong().coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            val lapMaxSpeed = (((lap.avgSpeedKmh) / 3.6f) * 1000.0).roundToInt().coerceIn(0, 65535)
            val lapAvgHr = (lap.avgHeartRateBpm ?: 0xFF).coerceIn(0, 255)
            val lapMaxHr = (lap.maxHeartRateBpm ?: 0xFF).coerceIn(0, 255)
            val lapAvgCad = 0xFF
            val lapAvgPwr = (lap.avgPowerWatts ?: 0xFFFF).coerceIn(0, 65535)
            val lapNp = (lap.normalizedPowerWatts ?: 0xFFFF).coerceIn(0, 65535)
            val lapPayload = le16(idx) + le32(lapEnd) + le32(lapStart) + le32(lapElapsed) +
                le32(lapTimer) + le32(lapDistEnc) + le16(lapMaxSpeed) +
                byteArrayOf(lapAvgHr.toByte(), lapMaxHr.toByte(), lapAvgCad.toByte()) +
                le16(lapAvgPwr) + le16(lapNp) + byteArrayOf(sport.toByte(), 9)
            data.write(dataMsg(3, lapPayload))
        }
        // session (mesg 18)
        data.write(defMsg(4, 18, listOf(
            F(254,2,132), F(253,4,134), F(2,4,134), F(7,4,134), F(8,4,134), F(9,4,134), F(5,1,0), F(6,1,0), F(0,1,0)
        )))
        val sessPayload = le16(0) + le32(endFit) + le32(startFit) + le32((totalElapsed * 1000).roundToLong().toInt()) +
            le32((totalTimer * 1000).roundToLong().toInt()) + le32((distance * 100.0).roundToLong().toInt()) +
            byteArrayOf(sport.toByte(), mapWorkoutTypeFitSubSport(workout.workoutType).toByte(), 9)
        data.write(dataMsg(4, sessPayload))
        // activity (mesg 34)
        data.write(defMsg(5, 34, listOf(F(253,4,134), F(0,4,134), F(1,2,132), F(2,1,0), F(3,1,0))))
        val actPayload = le32(endFit) + le32((totalTimer * 1000).roundToLong().toInt()) + le16(1) + byteArrayOf(0, 0)
        data.write(dataMsg(5, actPayload))

        val dataBytes = data.toByteArray()
        val header = ByteArray(14)
        val hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        hb.put(14)           // header size
        hb.put(0x20)         // protocol version 2.0
        hb.putShort(2140.toShort()) // profile version
        hb.putInt(dataBytes.size)
        hb.put('.'.code.toByte()); hb.put('F'.code.toByte()); hb.put('I'.code.toByte()); hb.put('T'.code.toByte())
        val headerCrc = crc16(header, 0, 12)
        hb.putShort(headerCrc.toShort())

        val out = ByteArrayOutputStream(header.size + dataBytes.size + 2)
        out.write(header)
        out.write(dataBytes)
        val fileCrc = crc16(header, 0, header.size, 0)
        val fileCrc2 = crc16(dataBytes, 0, dataBytes.size, fileCrc)
        out.write(byteArrayOf((fileCrc2 and 0xFF).toByte(), ((fileCrc2 shr 8) and 0xFF).toByte()))
        return out.toByteArray()
    }

    private data class F(val fieldNum: Int, val size: Int, val baseType: Int)

    private fun defMsg(local: Int, globalMsg: Int, fields: List<F>): ByteArray {
        val buf = ByteArrayOutputStream()
        buf.write(0x40 or (local and 0x0F)) // definition header
        buf.write(0x00) // reserved
        buf.write(0x00) // architecture little-endian
        buf.write(globalMsg and 0xFF); buf.write((globalMsg shr 8) and 0xFF)
        buf.write(fields.size)
        for (f in fields) {
            buf.write(f.fieldNum); buf.write(f.size); buf.write(f.baseType)
        }
        return buf.toByteArray()
    }

    private fun dataMsg(local: Int, payload: ByteArray): ByteArray {
        return byteArrayOf((local and 0x0F).toByte()) + payload
    }

    private fun le16(v: Int): ByteArray = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    private fun le32(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
    )

    private fun toFitTimestamp(ms: Long): Int = ((ms / 1000L) - 631065600L).coerceAtLeast(0L).toInt()
    private fun toSemicircles(deg: Double): Int = (deg * (2147483648.0 / 180.0)).roundToLong().toInt()
    private fun encodeAltitude(alt: Double?): Int {
        if (alt == null) return 0xFFFF
        return (((alt + 500.0) * 5.0).roundToInt()).coerceIn(0, 0xFFFE)
    }

    private fun mapWorkoutTypeFitSport(type: String): Int = when (type.uppercase(Locale.US)) {
        "RUNNING", "RUN" -> 1
        "WALKING", "WALK" -> 11
        else -> 2 // cycling
    }
    private fun mapWorkoutTypeFitSubSport(type: String): Int = when (type.uppercase(Locale.US)) {
        "RUNNING", "RUN" -> 0
        "WALKING", "WALK" -> 0
        else -> 0
    }

    // CRC-16 for FIT
    private val crcTable = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400
    )
    private fun crc16(data: ByteArray, offset: Int, len: Int, start: Int = 0): Int {
        var crc = start
        for (i in offset until (offset + len)) {
            val b = data[i].toInt() and 0xFF
            var tmp = crcTable[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor crcTable[b and 0xF]
            tmp = crcTable[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor crcTable[(b shr 4) and 0xF]
        }
        return crc and 0xFFFF
    }

    private fun buildWorkoutName(workout: WorkoutEntity): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        df.timeZone = TimeZone.getDefault()
        return "Workout ${df.format(Date(workout.startTimeMs))}"
    }

    private fun buildDescription(workout: WorkoutEntity): String {
        val distKm = workout.distanceMeters / 1000.0
        val base = String.format(
            Locale.US,
            "Distance %.2f km, moving %d s, elapsed %d s, elev %.1f m",
            distKm,
            workout.movingTimeMs / 1000,
            workout.elapsedTimeMs / 1000,
            workout.elevationGainMeters
        )
        val steps = workout.totalSteps
        return if (steps != null && steps > 0) "$base, steps $steps" else base
    }

    private fun mapWorkoutTypeGpx(type: String): String = when (type.uppercase(Locale.US)) {
        "RUNNING", "RUN" -> "running"
        "WALKING", "WALK" -> "walking"
        else -> "cycling"
    }

    private fun mapWorkoutTypeTcx(type: String): String = when (type.uppercase(Locale.US)) {
        "RUNNING", "RUN" -> "Running"
        "WALKING", "WALK" -> "Walking"
        else -> "Biking"
    }

    private fun isValidGps(lat: Double, lon: Double): Boolean {
        if (lat == 0.0 && lon == 0.0) return false
        return lat in -90.0..90.0 && lon in -180.0..180.0
    }

    private fun computeTotalDistance(points: List<TrackPointEntity>): Double {
        if (points.size < 2) return 0.0
        var d = 0.0
        for (i in 1 until points.size) {
            d += haversineMeters(points[i - 1].latitude, points[i - 1].longitude, points[i].latitude, points[i].longitude)
        }
        return d
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return r * c
    }

    companion object {
        fun buildFileName(workout: WorkoutEntity, extension: String): String {
            val df = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            df.timeZone = TimeZone.getDefault()
            val ts = df.format(Date(workout.startTimeMs))
            val ext = extension.trim().trimStart('.').lowercase(Locale.US)
            return "workout_${workout.id}_$ts.$ext"
        }

        fun formatIso8601(timeMs: Long): String {
            val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            df.timeZone = TimeZone.getTimeZone("UTC")
            return df.format(Date(timeMs))
        }

        fun formatCoord(value: Double): String = String.format(Locale.US, "%.7f", value)
        fun formatNumber(value: Double): String = String.format(Locale.US, "%.2f", value)

        fun escapeXml(text: String): String {
            return buildString(text.length) {
                for (ch in text) {
                    when (ch) {
                        '&' -> append("&amp;")
                        '<' -> append("&lt;")
                        '>' -> append("&gt;")
                        '"' -> append("&quot;")
                        '\'' -> append("&apos;")
                        else -> append(ch)
                    }
                }
            }
        }
    }
}
