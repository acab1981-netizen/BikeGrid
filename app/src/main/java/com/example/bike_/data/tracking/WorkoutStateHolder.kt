package com.example.bike_.data.tracking

import com.example.bike_.domain.model.WorkoutMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class WorkoutStateHolder {

    private val _metrics = MutableStateFlow(WorkoutMetrics())
    val metrics: StateFlow<WorkoutMetrics> = _metrics.asStateFlow()

    @Volatile
    var activeStructuredWorkoutId: Long? = null

    fun update(metrics: WorkoutMetrics) {
        _metrics.value = metrics
    }

    fun update(transform: (WorkoutMetrics) -> WorkoutMetrics) {
        _metrics.update(transform)
    }

    fun reset() {
        _metrics.value = WorkoutMetrics()
    }
}
