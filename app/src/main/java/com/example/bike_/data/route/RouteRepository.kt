package com.example.bike_.data.route

import com.example.bike_.data.db.BikeDatabase
import com.example.bike_.data.db.entity.SavedRouteEntity
import com.example.bike_.domain.model.LoadedRoute
import com.example.bike_.domain.model.RoutePoint
import com.example.bike_.domain.util.RouteNavigation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class RouteRepository(db: BikeDatabase) {
    private val dao = db.savedRouteDao()

    fun observeRoutes(): Flow<List<LoadedRoute>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getRoute(id: Long): LoadedRoute? = dao.getById(id)?.toDomain()

    suspend fun saveImportedRoute(route: LoadedRoute, fileName: String?): LoadedRoute {
        val points = route.points
        val cum = RouteNavigation.cumulativeDistances(points)
        val distance = if (route.distanceMeters > 0.0) route.distanceMeters else cum.lastOrNull() ?: 0.0
        val gain = if (route.elevationGainMeters > 0.0) route.elevationGainMeters else RouteNavigation.computeElevationGainMeters(points)
        val name = route.name?.takeIf { it.isNotBlank() } ?: fileName?.substringBeforeLast('.') ?: "Route"
        val entity = SavedRouteEntity(
            name = name,
            fileName = fileName,
            createdAtMs = System.currentTimeMillis(),
            distanceMeters = distance,
            elevationGainMeters = gain,
            pointsJson = pointsToJson(points)
        )
        val id = dao.insert(entity)
        return route.copy(id = id, name = name, distanceMeters = distance, elevationGainMeters = gain, cumulativeDistancesMeters = cum)
    }

    suspend fun deleteRoute(id: Long) {
        dao.deleteById(id)
    }
}

private fun SavedRouteEntity.toDomain(): LoadedRoute {
    val points = pointsFromJson(pointsJson)
    val cum = RouteNavigation.cumulativeDistances(points)
    return LoadedRoute(
        id = id,
        name = name,
        points = points,
        distanceMeters = if (distanceMeters > 0.0) distanceMeters else cum.lastOrNull() ?: 0.0,
        elevationGainMeters = elevationGainMeters,
        cumulativeDistancesMeters = cum
    )
}

private fun pointsToJson(points: List<RoutePoint>): String {
    val arr = JSONArray()
    points.forEach { p ->
        arr.put(JSONObject().apply {
            put("lat", p.latitude)
            put("lon", p.longitude)
            if (p.elevationMeters != null) put("ele", p.elevationMeters) else put("ele", JSONObject.NULL)
        })
    }
    return arr.toString()
}

private fun pointsFromJson(json: String): List<RoutePoint> {
    return try {
        val arr = JSONArray(json)
        val out = ArrayList<RoutePoint>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val lat = o.optDouble("lat", Double.NaN)
            val lon = o.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) continue
            val ele = if (o.isNull("ele")) null else o.optDouble("ele").takeIf { it.isFinite() }
            out.add(RoutePoint(lat, lon, ele))
        }
        out
    } catch (_: Exception) {
        emptyList()
    }
}
