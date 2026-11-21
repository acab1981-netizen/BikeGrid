package com.example.bike_.domain.util

import com.example.bike_.domain.model.LoadedRoute
import com.example.bike_.domain.model.RoutePoint
import com.example.bike_.domain.model.RouteProgress
import com.example.bike_.domain.model.TrackLatLng
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object RouteNavigation {
    const val OFF_ROUTE_THRESHOLD_METERS = 40.0

    data class Projection(
        val distanceMeters: Double,
        val point: RoutePoint,
        val segmentIndex: Int,
        val segmentFraction: Double
    )

    fun progress(trackPoints: List<TrackLatLng>, route: LoadedRoute?): RouteProgress {
        val r = route ?: return RouteProgress()
        if (r.points.isEmpty()) return RouteProgress()
        val last = trackPoints.lastOrNull() ?: return RouteProgress(
            remainingDistanceMeters = r.distanceMeters,
            remainingElevationGainMeters = r.elevationGainMeters
        )
        val proj = projectToRoute(last.latitude, last.longitude, r.points) ?: return RouteProgress()
        val cum = routeCumulativeDistances(r)
        val segStart = cum.getOrElse(proj.segmentIndex) { 0.0 }
        val segEnd = cum.getOrElse(proj.segmentIndex + 1) { segStart }
        val progressM = segStart + (segEnd - segStart) * proj.segmentFraction.coerceIn(0.0, 1.0)
        val remaining = (r.distanceMeters - progressM).coerceAtLeast(0.0)
        val remainingGain = remainingElevationGain(r.points, cum, proj, progressM)
        val bearing = bearingDeg(last.latitude, last.longitude, proj.point.latitude, proj.point.longitude)
        val dist = proj.distanceMeters.toFloat()
        return RouteProgress(
            distanceToRouteMeters = dist,
            isOffRoute = dist > OFF_ROUTE_THRESHOLD_METERS.toFloat(),
            nearestPoint = proj.point,
            projectedPoint = proj.point,
            progressDistanceMeters = progressM,
            remainingDistanceMeters = remaining,
            remainingElevationGainMeters = remainingGain,
            bearingToRouteDeg = bearing
        )
    }

    fun distanceToRouteMeters(lat: Double, lon: Double, route: List<RoutePoint>): Double {
        return projectToRoute(lat, lon, route)?.distanceMeters ?: Double.POSITIVE_INFINITY
    }

    fun projectToRoute(lat: Double, lon: Double, route: List<RoutePoint>): Projection? {
        if (route.isEmpty()) return null
        if (route.size == 1) {
            val p = route[0]
            return Projection(haversineMeters(lat, lon, p.latitude, p.longitude), p, 0, 0.0)
        }
        var best: Projection? = null
        for (i in 0 until route.size - 1) {
            val a = route[i]
            val b = route[i + 1]
            val p = projectPointToSegment(lat, lon, a, b)
            if (best == null || p.distanceMeters < best.distanceMeters) {
                best = Projection(p.distanceMeters, p.point, i, p.fraction)
                if (best.distanceMeters <= 0.5) return best
            }
        }
        return best
    }

    fun computeDistanceMeters(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0
        var d = 0.0
        for (i in 1 until points.size) {
            d += haversineMeters(points[i - 1].latitude, points[i - 1].longitude, points[i].latitude, points[i].longitude)
        }
        return d
    }

    fun computeElevationGainMeters(points: List<RoutePoint>): Double {
        var gain = 0.0
        var prev: Double? = null
        for (p in points) {
            val e = p.elevationMeters ?: continue
            val last = prev
            if (last != null) {
                val delta = e - last
                if (delta > 0.5) gain += delta
            }
            prev = e
        }
        return gain
    }

    fun routeCumulativeDistances(route: LoadedRoute): List<Double> {
        if (route.cumulativeDistancesMeters.size == route.points.size && route.points.isNotEmpty()) return route.cumulativeDistancesMeters
        return cumulativeDistances(route.points)
    }

    fun cumulativeDistances(points: List<RoutePoint>): List<Double> {
        if (points.isEmpty()) return emptyList()
        val out = ArrayList<Double>(points.size)
        var acc = 0.0
        out.add(0.0)
        for (i in 1 until points.size) {
            acc += haversineMeters(points[i - 1].latitude, points[i - 1].longitude, points[i].latitude, points[i].longitude)
            out.add(acc)
        }
        return out
    }

    private data class SegmentProjection(val distanceMeters: Double, val point: RoutePoint, val fraction: Double)

    private fun projectPointToSegment(
        pLat: Double, pLon: Double,
        a: RoutePoint, b: RoutePoint
    ): SegmentProjection {
        val refLat = Math.toRadians((pLat + a.latitude + b.latitude) / 3.0)
        val mPerDegLat = 111320.0
        val mPerDegLon = 111320.0 * cos(refLat)
        val ax = 0.0; val ay = 0.0
        val bx = (b.longitude - a.longitude) * mPerDegLon
        val by = (b.latitude - a.latitude) * mPerDegLat
        val px = (pLon - a.longitude) * mPerDegLon
        val py = (pLat - a.latitude) * mPerDegLat
        val ab2 = bx * bx + by * by
        val t = if (ab2 <= 1e-9) 0.0 else (((px * bx + py * by) / ab2).coerceIn(0.0, 1.0))
        val cx = ax + t * bx
        val cy = ay + t * by
        val dx = px - cx
        val dy = py - cy
        val dist = sqrt(dx * dx + dy * dy)
        val lat = a.latitude + (b.latitude - a.latitude) * t
        val lon = a.longitude + (b.longitude - a.longitude) * t
        val ele = when {
            a.elevationMeters != null && b.elevationMeters != null -> a.elevationMeters + (b.elevationMeters - a.elevationMeters) * t
            else -> a.elevationMeters ?: b.elevationMeters
        }
        return SegmentProjection(dist, RoutePoint(lat, lon, ele), t)
    }

    private fun remainingElevationGain(points: List<RoutePoint>, cum: List<Double>, proj: Projection, progressM: Double): Double {
        if (points.isEmpty()) return 0.0
        var gain = 0.0
        var prevEle = proj.point.elevationMeters
        val startIdx = (proj.segmentIndex + 1).coerceAtMost(points.lastIndex)
        for (i in startIdx..points.lastIndex) {
            val e = points[i].elevationMeters
            val p = prevEle
            if (e != null && p != null) {
                val delta = e - p
                if (delta > 0.5) gain += delta
            }
            if (e != null) prevEle = e
        }
        return gain
    }

    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng + 360.0) % 360.0).toFloat()
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return r * c
    }
}
