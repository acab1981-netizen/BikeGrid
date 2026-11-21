package com.example.bike_.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.bike_.BikeApplication
import kotlinx.coroutines.flow.StateFlow

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = (application as BikeApplication).cyclingSettings
    val weightKg: StateFlow<Float> = settings.athleteWeightKg
    val maxHr: StateFlow<Int> = settings.athleteMaxHr
    val restingHr: StateFlow<Int> = settings.athleteRestingHr
    val ftpWatts: StateFlow<Int> = settings.athleteFtpWatts
    val ageYears: StateFlow<Int> = settings.athleteAgeYears
    val isMale: StateFlow<Boolean> = settings.athleteIsMale
    val healthConnectEnabled: StateFlow<Boolean> = settings.healthConnectEnabled

    fun setHealthConnectEnabled(enabled: Boolean) = settings.setHealthConnectEnabled(enabled)

    fun setWeightKg(v: Float) = settings.setAthleteWeightKg(v)
    fun setMaxHr(v: Int) = settings.setAthleteMaxHr(v)
    fun setRestingHr(v: Int) = settings.setAthleteRestingHr(v)
    fun setFtpWatts(v: Int) = settings.setAthleteFtpWatts(v)
    fun setAgeYears(v: Int) = settings.setAthleteAgeYears(v)
    fun setIsMale(v: Boolean) = settings.setAthleteIsMale(v)
}
