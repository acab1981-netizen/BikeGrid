package com.example.bike_.domain.model

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null
)

data class LoadedRoute(
    val id: Long = 0L,
    val name: String? = null,
    val points: List<RoutePoint> = emptyList(),
    val distanceMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val cumulativeDistancesMeters: List<Double> = emptyList()
) {
    val isEmpty: Boolean get() = points.isEmpty()
}

data class RouteProgress(
    val distanceToRouteMeters: Float? = null,
    val isOffRoute: Boolean = false,
    val nearestPoint: RoutePoint? = null,
    val projectedPoint: RoutePoint? = null,
    val progressDistanceMeters: Double = 0.0,
    val remainingDistanceMeters: Double = 0.0,
    val remainingElevationGainMeters: Double = 0.0,
    val bearingToRouteDeg: Float? = null
)
