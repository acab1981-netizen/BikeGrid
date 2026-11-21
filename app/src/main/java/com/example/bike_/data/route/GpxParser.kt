package com.example.bike_.data.route

import com.example.bike_.domain.model.LoadedRoute
import com.example.bike_.domain.model.RoutePoint
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

object GpxParser {
    class InvalidGpxException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun parse(inputStream: InputStream): LoadedRoute {
        try {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(inputStream.reader(Charsets.UTF_8))

        val points = ArrayList<RoutePoint>()
        var routeName: String? = null

        var currentLat: Double? = null
        var currentLon: Double? = null
        var currentEle: Double? = null
        var inPoint = false
        var inEle = false
        var inName = false
        var trackOrRouteDepth = 0
        val eleBuffer = StringBuilder()
        val nameBuffer = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfterLast(':')?.lowercase()
            when (eventType) {
                XmlPullParser.START_TAG -> when (tag) {
                    "trkpt", "rtept", "wpt" -> {
                        inPoint = true
                        currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        currentEle = null
                    }
                    "ele" -> if (inPoint) {
                        inEle = true
                        eleBuffer.setLength(0)
                    }
                    "name" -> {
                        if (!inPoint && (trackOrRouteDepth > 0 || routeName == null)) {
                            inName = true
                            nameBuffer.setLength(0)
                        }
                    }
                    "trk", "rte" -> trackOrRouteDepth++
                }
                XmlPullParser.TEXT -> {
                    if (inEle) eleBuffer.append(parser.text.orEmpty())
                    if (inName) nameBuffer.append(parser.text.orEmpty())
                }
                XmlPullParser.END_TAG -> when (tag) {
                    "ele" -> if (inEle) {
                        inEle = false
                        val v = eleBuffer.toString().trim().toDoubleOrNull()
                        if (v != null) currentEle = v
                    }
                    "name" -> if (inName) {
                        inName = false
                        val n = nameBuffer.toString().trim()
                        if (routeName == null && n.isNotEmpty()) routeName = n
                    }
                    "trkpt", "rtept", "wpt" -> {
                        val lat = currentLat
                        val lon = currentLon
                        if (inPoint && lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                            points.add(RoutePoint(lat, lon, currentEle))
                        }
                        inPoint = false
                        inEle = false
                        currentLat = null
                        currentLon = null
                        currentEle = null
                    }
                    "trk", "rte" -> if (trackOrRouteDepth > 0) trackOrRouteDepth--
                }
            }
            eventType = parser.next()
        }

        if (points.isEmpty()) throw InvalidGpxException("GPX file has no route points")
        val cum = com.example.bike_.domain.util.RouteNavigation.cumulativeDistances(points)
        return LoadedRoute(
            name = routeName,
            points = points,
            distanceMeters = cum.lastOrNull() ?: 0.0,
            elevationGainMeters = com.example.bike_.domain.util.RouteNavigation.computeElevationGainMeters(points),
            cumulativeDistancesMeters = cum
        )
        } catch (e: InvalidGpxException) {
            throw e
        } catch (e: Exception) {
            throw InvalidGpxException("Invalid GPX file format", e)
        }
    }
}
