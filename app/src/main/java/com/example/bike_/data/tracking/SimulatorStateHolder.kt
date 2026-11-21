package com.example.bike_.data.tracking

import com.example.bike_.domain.model.RoutePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SimulatorState(
    val enabled: Boolean = false,
    val powerWatts: Int = 150,
    val heartRateBpm: Int = 130,
    val cadenceRpm: Int = 85,
    val speedKmh: Float = 25f,
    val autoIntervals: Boolean = false,
    val followRoute: Boolean = false,
    val routePoints: List<RoutePoint> = emptyList(),
    val routeIndex: Int = 0,
    val routeOffsetMeters: Double = 0.0
)

class SimulatorStateHolder {
    private val _state = MutableStateFlow(SimulatorState())
    val state: StateFlow<SimulatorState> = _state.asStateFlow()

    fun setEnabled(v: Boolean) { _state.value = _state.value.copy(enabled = v) }
    fun setPower(v: Int) { _state.value = _state.value.copy(powerWatts = v.coerceIn(0, 600)) }
    fun setHr(v: Int) { _state.value = _state.value.copy(heartRateBpm = v.coerceIn(50, 220)) }
    fun setCadence(v: Int) { _state.value = _state.value.copy(cadenceRpm = v.coerceIn(0, 130)) }
    fun setSpeed(v: Float) { _state.value = _state.value.copy(speedKmh = v.coerceIn(0f, 60f)) }
    fun setAutoIntervals(v: Boolean) { _state.value = _state.value.copy(autoIntervals = v) }
    fun setFollowRoute(v: Boolean, points: List<RoutePoint> = _state.value.routePoints) {
        _state.value = _state.value.copy(followRoute = v, routePoints = points, routeIndex = 0)
    }
    fun setRoutePoints(points: List<RoutePoint>) { _state.value = _state.value.copy(routePoints = points, routeIndex = 0) }
    fun advanceRoute(step: Int = 1) {
        val s = _state.value
        if (s.routePoints.isEmpty()) return
        _state.value = s.copy(routeIndex = (s.routeIndex + step).coerceIn(0, s.routePoints.lastIndex))
    }
    fun offsetRoute(meters: Double) { _state.value = _state.value.copy(routeOffsetMeters = _state.value.routeOffsetMeters + meters) }
    fun resetRouteOffset() { _state.value = _state.value.copy(routeOffsetMeters = 0.0) }
    fun update(transform: (SimulatorState) -> SimulatorState) { _state.value = transform(_state.value) }
}
