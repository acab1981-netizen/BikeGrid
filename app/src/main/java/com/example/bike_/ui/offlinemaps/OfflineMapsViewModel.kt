package com.example.bike_.ui.offlinemaps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bike_.BikeApplication
import com.example.bike_.data.map.AppMapConfig
import com.example.bike_.data.map.MapTileSources
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

data class OfflineDownloadProgress(
    val active: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val failed: Int = 0,
    val completed: Boolean = false,
    val cancelled: Boolean = false,
    val message: String? = null
)

class OfflineMapsViewModel(private val app: BikeApplication) : ViewModel() {
    val settings = app.cyclingSettings
    val mapTileSource = settings.mapTileSource
    val mapUseNetwork = settings.mapUseNetwork

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _download = MutableStateFlow(OfflineDownloadProgress())
    val download: StateFlow<OfflineDownloadProgress> = _download.asStateFlow()

    private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
    val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()

    @Volatile private var activeCacheManager: CacheManager? = null

    fun refreshCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) { AppMapConfig.calculateCacheSizeBytes(app) }
            _cacheSizeBytes.value = size
        }
    }

    fun setTileSource(key: String) { settings.setMapTileSource(key) }
    fun setUseNetwork(enabled: Boolean) { settings.setMapUseNetwork(enabled) }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { AppMapConfig.clearCache(app) }
            refreshCacheSize()
        }
    }

    fun fetchCurrentLocation(context: Context) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        try {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            fused.lastLocation.addOnSuccessListener { loc: Location? ->
                if (loc != null) _currentLocation.value = GeoPoint(loc.latitude, loc.longitude)
            }
            val cts = CancellationTokenSource()
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) _currentLocation.value = GeoPoint(loc.latitude, loc.longitude)
                }
        } catch (_: Exception) { }
    }

    fun cancelDownload() {
        try { activeCacheManager?.cancelAllJobs() } catch (_: Exception) { }
        _download.value = _download.value.copy(active = false, cancelled = true, message = app.getString(com.example.bike_.R.string.offline_maps_download_cancelled))
        viewModelScope.launch { refreshCacheSize() }
    }

    fun startDownload(mapView: MapView, box: BoundingBox, zoomMin: Int, zoomMax: Int) {
        val current = _download.value
        if (current.active) return
        viewModelScope.launch {
            val srcKey = mapTileSource.value
            val maxZ = MapTileSources.maxZoomForDownload(srcKey, zoomMax)
            val minZ = zoomMin.coerceAtMost(maxZ)
            if (maxZ < 0 || minZ > maxZ) {
                _download.value = OfflineDownloadProgress(message = app.getString(com.example.bike_.R.string.offline_maps_invalid_zoom))
                return@launch
            }
            val possible = try {
                val mgr = CacheManager(mapView)
                mgr.possibleTilesInArea(box, minZ, maxZ)
            } catch (_: Exception) { -1 }
            if (possible > 120000) {
                _download.value = OfflineDownloadProgress(message = app.getString(com.example.bike_.R.string.offline_maps_area_too_large, possible))
                return@launch
            }
            _download.value = OfflineDownloadProgress(active = true, total = max(0, possible), message = app.getString(com.example.bike_.R.string.offline_maps_preparing))
            try {
                val mgr = CacheManager(mapView)
                activeCacheManager = mgr
                mgr.downloadAreaAsync(app, box, minZ, maxZ, object : CacheManager.CacheManagerCallback {
                    override fun onTaskComplete() {
                        viewModelScope.launch {
                            _download.value = _download.value.copy(active = false, completed = true, cancelled = false, message = app.getString(com.example.bike_.R.string.offline_maps_complete))
                            refreshCacheSize()
                        }
                    }
                    override fun onTaskFailed(errors: Int) {
                        viewModelScope.launch {
                            _download.value = _download.value.copy(active = false, completed = true, failed = _download.value.failed + errors, message = app.getString(com.example.bike_.R.string.offline_maps_complete_errors, errors))
                            refreshCacheSize()
                        }
                    }
                    override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                        val st = _download.value
                        _download.value = st.copy(
                            active = true,
                            processed = progress,
                            total = if (st.total > 0) st.total else progress,
                            message = app.getString(com.example.bike_.R.string.offline_maps_zoom_progress, currentZoomLevel, zoomMin, zoomMax, progress)
                        )
                    }
                    override fun downloadStarted() {
                        _download.value = _download.value.copy(active = true, message = app.getString(com.example.bike_.R.string.offline_maps_downloading_msg))
                    }
                    override fun setPossibleTilesInArea(total: Int) {
                        _download.value = _download.value.copy(total = total)
                    }
                })
            } catch (e: Exception) {
                _download.value = OfflineDownloadProgress(active = false, message = app.getString(com.example.bike_.R.string.offline_maps_error, e.message ?: app.getString(com.example.bike_.R.string.unknown_error)))
            }
        }
    }

    fun clearDownloadMessage() {
        if (!_download.value.active) _download.value = OfflineDownloadProgress()
    }

    companion object {
        fun bboxAround(center: GeoPoint, radiusMeters: Double): BoundingBox {
            val lat = center.latitude
            val lon = center.longitude
            val latDelta = radiusMeters / 111320.0
            val lonDen = 111320.0 * max(0.2, abs(cos(Math.toRadians(lat))))
            val lonDelta = radiusMeters / lonDen
            return BoundingBox(
                (lat + latDelta).coerceAtMost(85.0),
                (lon + lonDelta).coerceAtMost(180.0),
                (lat - latDelta).coerceAtLeast(-85.0),
                (lon - lonDelta).coerceAtLeast(-180.0)
            )
        }

        fun factory(app: BikeApplication): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OfflineMapsViewModel(app) as T
        }
    }
}
