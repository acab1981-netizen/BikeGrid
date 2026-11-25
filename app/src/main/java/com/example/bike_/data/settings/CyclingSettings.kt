package com.example.bike_.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class CyclingSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _wheelCircumferenceMm = MutableStateFlow(
        prefs.getFloat(KEY_WHEEL_CIRCUMFERENCE_MM, DEFAULT_WHEEL_CIRCUMFERENCE_MM)
    )
    val wheelCircumferenceMm: StateFlow<Float> = _wheelCircumferenceMm.asStateFlow()

    private val _dashboardFields = MutableStateFlow(loadDashboardFields())
    val dashboardFields: StateFlow<List<String>> = _dashboardFields.asStateFlow()

    private val _uiTheme = MutableStateFlow(prefs.getString(KEY_UI_THEME, UI_THEME_SOFT) ?: UI_THEME_SOFT)
    val uiTheme: StateFlow<String> = _uiTheme.asStateFlow()

    private val _uiAccent = MutableStateFlow(prefs.getString(KEY_UI_ACCENT, UI_ACCENT_ORANGE) ?: UI_ACCENT_ORANGE)
    val uiAccent: StateFlow<String> = _uiAccent.asStateFlow()

    private val _uiDefaultView = MutableStateFlow(prefs.getString(KEY_UI_DEFAULT_VIEW, UI_VIEW_METRICS) ?: UI_VIEW_METRICS)
    val uiDefaultView: StateFlow<String> = _uiDefaultView.asStateFlow()

    private val _uiCompactCards = MutableStateFlow(prefs.getBoolean(KEY_UI_COMPACT_CARDS, false))
    val uiCompactCards: StateFlow<Boolean> = _uiCompactCards.asStateFlow()

    private val _uiShowMapControls = MutableStateFlow(prefs.getBoolean(KEY_UI_SHOW_MAP_CONTROLS, true))
    val uiShowMapControls: StateFlow<Boolean> = _uiShowMapControls.asStateFlow()

    private val _autoPauseEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_PAUSE_ENABLED, true))
    val autoPauseEnabled: StateFlow<Boolean> = _autoPauseEnabled.asStateFlow()

    private val _autoLapDistanceMeters = MutableStateFlow(prefs.getInt(KEY_AUTO_LAP_DISTANCE_M, DEFAULT_AUTO_LAP_DISTANCE_M))
    val autoLapDistanceMeters: StateFlow<Int> = _autoLapDistanceMeters.asStateFlow()

    private val _walkingStrideCm = MutableStateFlow(prefs.getFloat(KEY_WALKING_STRIDE_CM, DEFAULT_WALKING_STRIDE_CM))
    val walkingStrideCm: StateFlow<Float> = _walkingStrideCm.asStateFlow()

    private val _runningStrideCm = MutableStateFlow(prefs.getFloat(KEY_RUNNING_STRIDE_CM, DEFAULT_RUNNING_STRIDE_CM))
    val runningStrideCm: StateFlow<Float> = _runningStrideCm.asStateFlow()

    private val _athleteWeightKg = MutableStateFlow(prefs.getFloat(KEY_ATHLETE_WEIGHT_KG, DEFAULT_WEIGHT_KG))
    val athleteWeightKg: StateFlow<Float> = _athleteWeightKg.asStateFlow()
    private val _athleteMaxHr = MutableStateFlow(prefs.getInt(KEY_ATHLETE_MAX_HR, DEFAULT_MAX_HR))
    val athleteMaxHr: StateFlow<Int> = _athleteMaxHr.asStateFlow()
    private val _athleteRestingHr = MutableStateFlow(prefs.getInt(KEY_ATHLETE_RESTING_HR, DEFAULT_RESTING_HR))
    val athleteRestingHr: StateFlow<Int> = _athleteRestingHr.asStateFlow()
    private val _athleteFtpWatts = MutableStateFlow(prefs.getInt(KEY_ATHLETE_FTP_WATTS, DEFAULT_FTP_WATTS))
    val athleteFtpWatts: StateFlow<Int> = _athleteFtpWatts.asStateFlow()
    private val _athleteAgeYears = MutableStateFlow(prefs.getInt(KEY_ATHLETE_AGE_YEARS, DEFAULT_AGE_YEARS))
    val athleteAgeYears: StateFlow<Int> = _athleteAgeYears.asStateFlow()
    private val _athleteIsMale = MutableStateFlow(prefs.getBoolean(KEY_ATHLETE_IS_MALE, DEFAULT_IS_MALE))
    val athleteIsMale: StateFlow<Boolean> = _athleteIsMale.asStateFlow()

    private val _knownSensors = MutableStateFlow(loadKnownSensors())
    val knownSensors: StateFlow<List<KnownSensorPref>> = _knownSensors.asStateFlow()

    private val _mapTileSource = MutableStateFlow(run {
        val saved = prefs.getString(KEY_MAP_TILE_SOURCE, null)
        when (saved) {
            null -> DEFAULT_MAP_TILE_SOURCE
            // Migrate away from OSM standard tile endpoint that is commonly blocked by policy for apps
            "MAPNIK" -> DEFAULT_MAP_TILE_SOURCE.also {
                prefs.edit().putString(KEY_MAP_TILE_SOURCE, it).apply()
            }
            else -> saved
        }
    })
    val mapTileSource: StateFlow<String> = _mapTileSource.asStateFlow()

    private val _mapUseNetwork = MutableStateFlow(prefs.getBoolean(KEY_MAP_USE_NETWORK, true))
    val mapUseNetwork: StateFlow<Boolean> = _mapUseNetwork.asStateFlow()

    private val _healthConnectEnabled = MutableStateFlow(prefs.getBoolean(KEY_HEALTH_CONNECT_ENABLED, false))
    val healthConnectEnabled: StateFlow<Boolean> = _healthConnectEnabled.asStateFlow()

    private val _bleUnfilteredScanEnabled = MutableStateFlow(prefs.getBoolean(KEY_BLE_UNFILTERED_SCAN_ENABLED, false))
    val bleUnfilteredScanEnabled: StateFlow<Boolean> = _bleUnfilteredScanEnabled.asStateFlow()

    fun setWheelCircumferenceMm(mm: Float) {
        val clamped = mm.coerceIn(MIN_WHEEL_CIRCUMFERENCE_MM, MAX_WHEEL_CIRCUMFERENCE_MM)
        prefs.edit().putFloat(KEY_WHEEL_CIRCUMFERENCE_MM, clamped).apply()
        _wheelCircumferenceMm.value = clamped
    }

    fun isBatteryOptimizationPromptShown(): Boolean =
        prefs.getBoolean(KEY_BATTERY_OPT_PROMPT_SHOWN, false)

    fun setBatteryOptimizationPromptShown() {
        prefs.edit().putBoolean(KEY_BATTERY_OPT_PROMPT_SHOWN, true).apply()
    }

    fun setDashboardFields(fields: List<String>) {
        val normalized = if (fields.isEmpty()) DEFAULT_DASHBOARD_FIELDS else fields.distinct()
        prefs.edit().putString(KEY_DASHBOARD_FIELDS, normalized.joinToString(",")).apply()
        _dashboardFields.value = normalized
    }

    fun setUiTheme(theme: String) {
        val v = if (theme in UI_THEMES) theme else UI_THEME_SOFT
        prefs.edit().putString(KEY_UI_THEME, v).apply()
        _uiTheme.value = v
    }

    fun setUiAccent(accent: String) {
        val v = if (accent in UI_ACCENTS) accent else UI_ACCENT_ORANGE
        prefs.edit().putString(KEY_UI_ACCENT, v).apply()
        _uiAccent.value = v
    }

    fun setUiDefaultView(view: String) {
        val v = if (view in UI_VIEWS) view else UI_VIEW_METRICS
        prefs.edit().putString(KEY_UI_DEFAULT_VIEW, v).apply()
        _uiDefaultView.value = v
    }

    fun setUiCompactCards(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UI_COMPACT_CARDS, enabled).apply()
        _uiCompactCards.value = enabled
    }

    fun setUiShowMapControls(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UI_SHOW_MAP_CONTROLS, enabled).apply()
        _uiShowMapControls.value = enabled
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PAUSE_ENABLED, enabled).apply()
        _autoPauseEnabled.value = enabled
    }

    fun setAutoLapDistanceMeters(meters: Int) {
        val v = if (meters in AUTO_LAP_OPTIONS_M) meters else 0
        prefs.edit().putInt(KEY_AUTO_LAP_DISTANCE_M, v).apply()
        _autoLapDistanceMeters.value = v
    }

    fun setWalkingStrideCm(cm: Float) {
        val clamped = cm.coerceIn(MIN_STRIDE_CM, MAX_STRIDE_CM)
        prefs.edit().putFloat(KEY_WALKING_STRIDE_CM, clamped).apply()
        _walkingStrideCm.value = clamped
    }

    fun setRunningStrideCm(cm: Float) {
        val clamped = cm.coerceIn(MIN_STRIDE_CM, MAX_STRIDE_CM)
        prefs.edit().putFloat(KEY_RUNNING_STRIDE_CM, clamped).apply()
        _runningStrideCm.value = clamped
    }

    fun setAthleteWeightKg(kg: Float) {
        val v = kg.coerceIn(30f, 200f)
        prefs.edit().putFloat(KEY_ATHLETE_WEIGHT_KG, v).apply()
        _athleteWeightKg.value = v
    }

    fun setAthleteMaxHr(bpm: Int) {
        val v = bpm.coerceIn(120, 230)
        prefs.edit().putInt(KEY_ATHLETE_MAX_HR, v).apply()
        _athleteMaxHr.value = v
    }

    fun setAthleteRestingHr(bpm: Int) {
        val v = bpm.coerceIn(30, 120).coerceAtMost(_athleteMaxHr.value - 1)
        prefs.edit().putInt(KEY_ATHLETE_RESTING_HR, v).apply()
        _athleteRestingHr.value = v
    }

    fun setAthleteFtpWatts(watts: Int) {
        val v = watts.coerceIn(50, 600)
        prefs.edit().putInt(KEY_ATHLETE_FTP_WATTS, v).apply()
        _athleteFtpWatts.value = v
    }

    fun setAthleteAgeYears(years: Int) {
        val v = years.coerceIn(10, 100)
        prefs.edit().putInt(KEY_ATHLETE_AGE_YEARS, v).apply()
        _athleteAgeYears.value = v
    }

    fun setAthleteIsMale(male: Boolean) {
        prefs.edit().putBoolean(KEY_ATHLETE_IS_MALE, male).apply()
        _athleteIsMale.value = male
    }

    fun setMapTileSource(sourceKey: String) {
        val v = if (sourceKey.isBlank()) DEFAULT_MAP_TILE_SOURCE else sourceKey
        prefs.edit().putString(KEY_MAP_TILE_SOURCE, v).apply()
        _mapTileSource.value = v
    }

    fun setMapUseNetwork(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MAP_USE_NETWORK, enabled).apply()
        _mapUseNetwork.value = enabled
    }

    fun setHealthConnectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HEALTH_CONNECT_ENABLED, enabled).apply()
        _healthConnectEnabled.value = enabled
    }

    fun setBleUnfilteredScanEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLE_UNFILTERED_SCAN_ENABLED, enabled).apply()
        _bleUnfilteredScanEnabled.value = enabled
    }

    fun upsertKnownSensor(sensor: KnownSensorPref) {
        val list = _knownSensors.value.toMutableList()
        val idx = list.indexOfFirst { it.id == sensor.id }
        if (idx >= 0) list[idx] = sensor else list.add(sensor)
        saveKnownSensors(list)
    }

    fun removeKnownSensor(id: String) {
        val list = _knownSensors.value.filterNot { it.id == id }
        saveKnownSensors(list)
    }

    fun setKnownSensorAutoConnect(id: String, enabled: Boolean) {
        val list = _knownSensors.value.map { if (it.id == id) it.copy(autoConnect = enabled) else it }
        saveKnownSensors(list)
    }

    fun getKnownSensor(id: String): KnownSensorPref? = _knownSensors.value.firstOrNull { it.id == id }

    private fun saveKnownSensors(list: List<KnownSensorPref>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("transport", "BLE")
                put("address", s.address.ifBlank { s.id })
                put("name", s.name ?: JSONObject.NULL)
                put("sensorTypes", JSONArray(s.sensorTypes))
                put("autoConnect", s.autoConnect)
                put("lastConnectedMs", s.lastConnectedMs)
            })
        }
        prefs.edit().putString(KEY_KNOWN_SENSORS, arr.toString()).apply()
        _knownSensors.value = list
    }

    private fun loadKnownSensors(): List<KnownSensorPref> {
        val raw = prefs.getString(KEY_KNOWN_SENSORS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<KnownSensorPref>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: o.optString("address").takeIf { it.isNotBlank() } ?: continue
                val address = o.optString("address", id).ifBlank { id }
                val typesArr = o.optJSONArray("sensorTypes") ?: JSONArray()
                val types = ArrayList<String>(typesArr.length())
                for (j in 0 until typesArr.length()) types.add(typesArr.optString(j))
                out.add(
                    KnownSensorPref(
                        id = id,
                        address = address,
                        name = if (o.isNull("name")) null else o.optString("name").takeIf { it.isNotBlank() },
                        sensorTypes = types,
                        autoConnect = o.optBoolean("autoConnect", true),
                        lastConnectedMs = o.optLong("lastConnectedMs", 0L)
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadDashboardFields(): List<String> {
        val raw = prefs.getString(KEY_DASHBOARD_FIELDS, null)
        if (raw.isNullOrBlank()) return DEFAULT_DASHBOARD_FIELDS
        val parsed = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() && it in ALL_DASHBOARD_FIELDS }
        val base = if (parsed.isEmpty()) DEFAULT_DASHBOARD_FIELDS else parsed
        // Одноразовая миграция: добавляем новые велосипедные поля в уже сохранённый набор.
        if (!prefs.getBoolean(KEY_DASHBOARD_FIELDS_V2, false)) {
            val merged = base + listOf("avg_power", "grade").filter { it !in base }
            prefs.edit()
                .putString(KEY_DASHBOARD_FIELDS, merged.joinToString(","))
                .putBoolean(KEY_DASHBOARD_FIELDS_V2, true)
                .apply()
            return merged
        }
        return base
    }

    /** Re-reads persisted values after a settings restore so the UI updates without an app restart. */
    fun reload() {
        _wheelCircumferenceMm.value = prefs.getFloat(KEY_WHEEL_CIRCUMFERENCE_MM, DEFAULT_WHEEL_CIRCUMFERENCE_MM)
        _dashboardFields.value = loadDashboardFields()
        _uiTheme.value = prefs.getString(KEY_UI_THEME, UI_THEME_SOFT) ?: UI_THEME_SOFT
        _uiAccent.value = prefs.getString(KEY_UI_ACCENT, UI_ACCENT_ORANGE) ?: UI_ACCENT_ORANGE
        _uiDefaultView.value = prefs.getString(KEY_UI_DEFAULT_VIEW, UI_VIEW_METRICS) ?: UI_VIEW_METRICS
        _uiCompactCards.value = prefs.getBoolean(KEY_UI_COMPACT_CARDS, false)
        _uiShowMapControls.value = prefs.getBoolean(KEY_UI_SHOW_MAP_CONTROLS, true)
        _autoPauseEnabled.value = prefs.getBoolean(KEY_AUTO_PAUSE_ENABLED, true)
        _autoLapDistanceMeters.value = prefs.getInt(KEY_AUTO_LAP_DISTANCE_M, DEFAULT_AUTO_LAP_DISTANCE_M)
        _walkingStrideCm.value = prefs.getFloat(KEY_WALKING_STRIDE_CM, DEFAULT_WALKING_STRIDE_CM)
        _runningStrideCm.value = prefs.getFloat(KEY_RUNNING_STRIDE_CM, DEFAULT_RUNNING_STRIDE_CM)
        _athleteWeightKg.value = prefs.getFloat(KEY_ATHLETE_WEIGHT_KG, DEFAULT_WEIGHT_KG)
        _athleteMaxHr.value = prefs.getInt(KEY_ATHLETE_MAX_HR, DEFAULT_MAX_HR)
        _athleteRestingHr.value = prefs.getInt(KEY_ATHLETE_RESTING_HR, DEFAULT_RESTING_HR)
        _athleteFtpWatts.value = prefs.getInt(KEY_ATHLETE_FTP_WATTS, DEFAULT_FTP_WATTS)
        _athleteAgeYears.value = prefs.getInt(KEY_ATHLETE_AGE_YEARS, DEFAULT_AGE_YEARS)
        _athleteIsMale.value = prefs.getBoolean(KEY_ATHLETE_IS_MALE, DEFAULT_IS_MALE)
        _knownSensors.value = loadKnownSensors()
        _mapTileSource.value = prefs.getString(KEY_MAP_TILE_SOURCE, null) ?: DEFAULT_MAP_TILE_SOURCE
        _mapUseNetwork.value = prefs.getBoolean(KEY_MAP_USE_NETWORK, true)
        _healthConnectEnabled.value = prefs.getBoolean(KEY_HEALTH_CONNECT_ENABLED, false)
        _bleUnfilteredScanEnabled.value = prefs.getBoolean(KEY_BLE_UNFILTERED_SCAN_ENABLED, false)
    }

    companion object {
        const val DEFAULT_WHEEL_CIRCUMFERENCE_MM = 2105f
        const val MIN_WHEEL_CIRCUMFERENCE_MM = 1000f
        const val MAX_WHEEL_CIRCUMFERENCE_MM = 3000f
        const val DEFAULT_WALKING_STRIDE_CM = 75f
        const val DEFAULT_RUNNING_STRIDE_CM = 100f
        const val MIN_STRIDE_CM = 30f
        const val MAX_STRIDE_CM = 200f
        const val DEFAULT_WEIGHT_KG = 75f
        const val DEFAULT_MAX_HR = 190
        const val DEFAULT_RESTING_HR = 60
        const val DEFAULT_FTP_WATTS = 200
        const val DEFAULT_AGE_YEARS = 30
        const val DEFAULT_IS_MALE = true
        const val DEFAULT_AUTO_LAP_DISTANCE_M = 0
        val AUTO_LAP_OPTIONS_M = listOf(0, 1000, 5000, 10000)
        const val PREFS_NAME = "cycling_settings"
        const val KEY_ATHLETE_WEIGHT_KG = "athlete_weight_kg"
        const val KEY_ATHLETE_MAX_HR = "athlete_max_hr"
        const val KEY_ATHLETE_RESTING_HR = "athlete_resting_hr"
        const val KEY_ATHLETE_FTP_WATTS = "athlete_ftp_watts"
        const val KEY_ATHLETE_AGE_YEARS = "athlete_age_years"
        const val KEY_ATHLETE_IS_MALE = "athlete_is_male"
        private const val KEY_WHEEL_CIRCUMFERENCE_MM = "wheel_circumference_mm"
        private const val KEY_BATTERY_OPT_PROMPT_SHOWN = "battery_opt_prompt_shown"
        private const val KEY_DASHBOARD_FIELDS = "dashboard_fields"
        private const val KEY_DASHBOARD_FIELDS_V2 = "dashboard_fields_v2"
        private const val KEY_UI_THEME = "ui_theme"
        private const val KEY_UI_ACCENT = "ui_accent"
        private const val KEY_UI_DEFAULT_VIEW = "ui_default_view"
        private const val KEY_UI_COMPACT_CARDS = "ui_compact_cards"
        private const val KEY_UI_SHOW_MAP_CONTROLS = "ui_show_map_controls"
        private const val KEY_AUTO_PAUSE_ENABLED = "auto_pause_enabled"
        private const val KEY_AUTO_LAP_DISTANCE_M = "auto_lap_distance_m"
        private const val KEY_WALKING_STRIDE_CM = "walking_stride_cm"
        private const val KEY_RUNNING_STRIDE_CM = "running_stride_cm"
        private const val KEY_KNOWN_SENSORS = "known_sensors_json"
        private const val KEY_MAP_TILE_SOURCE = "map_tile_source"
        private const val KEY_MAP_USE_NETWORK = "map_use_network"
        private const val KEY_HEALTH_CONNECT_ENABLED = "health_connect_enabled"
        private const val KEY_BLE_UNFILTERED_SCAN_ENABLED = "ble_unfiltered_scan_enabled"
        // Avoid OSM standard tiles as hard default because many devices/apps get blocked without a
        // fully compliant usage setup; CyclOSM is cycling-focused and usually works better out of box.
        const val DEFAULT_MAP_TILE_SOURCE = "CYCLOSM"

        const val UI_THEME_SOFT = "SOFT"
        const val UI_THEME_WARM = "WARM"
        const val UI_THEME_COOL = "COOL"
        const val UI_THEME_DARK = "DARK"
        val UI_THEMES = listOf(UI_THEME_SOFT, UI_THEME_WARM, UI_THEME_COOL, UI_THEME_DARK)

        const val UI_ACCENT_ORANGE = "ORANGE"
        const val UI_ACCENT_BLUE = "BLUE"
        const val UI_ACCENT_GREEN = "GREEN"
        const val UI_ACCENT_TEAL = "TEAL"
        const val UI_ACCENT_PURPLE = "PURPLE"
        val UI_ACCENTS = listOf(UI_ACCENT_ORANGE, UI_ACCENT_BLUE, UI_ACCENT_GREEN, UI_ACCENT_TEAL, UI_ACCENT_PURPLE)

        const val UI_VIEW_METRICS = "METRICS"
        const val UI_VIEW_MAP = "MAP"
        val UI_VIEWS = listOf(UI_VIEW_METRICS, UI_VIEW_MAP)

        val ALL_DASHBOARD_FIELDS = listOf(
            "distance", "time", "moving_time", "avg_speed", "max_speed",
            "heart_rate", "cadence", "power", "avg_power", "grade",
            "elevation", "lap", "steps", "step_cadence", "pace"
        )
        val DEFAULT_DASHBOARD_FIELDS = listOf(
            "distance", "time", "heart_rate", "cadence", "power", "avg_power", "grade", "avg_speed"
        )

        fun dashboardFieldLabel(key: String): String = when (key) {
            "distance" -> "Distance"
            "time" -> "Time"
            "moving_time" -> "Moving time"
            "avg_speed" -> "Avg speed"
            "max_speed" -> "Max speed"
            "heart_rate" -> "Heart rate"
            "cadence" -> "Cadence"
            "power" -> "Power"
            "avg_power" -> "Avg power"
            "grade" -> "Grade"
            "elevation" -> "Elevation gain"
            "lap" -> "Current lap"
            "steps" -> "Steps"
            "step_cadence" -> "Step cadence"
            "pace" -> "Pace"
            else -> key
        }

        fun uiThemeLabel(key: String): String = when (key) {
            UI_THEME_SOFT -> "Soft"
            UI_THEME_WARM -> "Warm"
            UI_THEME_COOL -> "Cool"
            UI_THEME_DARK -> "Dark"
            else -> key
        }

        fun uiAccentLabel(key: String): String = when (key) {
            UI_ACCENT_ORANGE -> "Orange"
            UI_ACCENT_BLUE -> "Blue"
            UI_ACCENT_GREEN -> "Green"
            UI_ACCENT_TEAL -> "Teal"
            UI_ACCENT_PURPLE -> "Purple"
            else -> key
        }
    }
}

data class KnownSensorPref(
    val id: String,
    val address: String = "",
    val name: String? = null,
    val sensorTypes: List<String> = emptyList(),
    val autoConnect: Boolean = true,
    val lastConnectedMs: Long = 0L
)
