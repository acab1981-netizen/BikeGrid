package com.example.bike_.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bike_.BikeApplication
import com.example.bike_.domain.analytics.FitnessAnalytics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

class FitnessStatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as BikeApplication).workoutRepository

    val series: StateFlow<List<FitnessAnalytics.DailyPoint>> = repo.observeWorkouts()
        .map { workouts ->
            val finished = workouts.filter { it.isFinished }
            val inputs = finished.map { it.startTimeMs to (it.trainingStressScore ?: 0f) }
            FitnessAnalytics.buildSeries(inputs, ZoneId.systemDefault(), LocalDate.now())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val finishedCount: StateFlow<Int> = repo.observeWorkouts()
        .map { list -> list.count { it.isFinished } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
