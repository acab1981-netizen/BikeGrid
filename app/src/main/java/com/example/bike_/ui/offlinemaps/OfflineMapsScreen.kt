package com.example.bike_.ui.offlinemaps

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bike_.BikeApplication
import com.example.bike_.data.map.AppMapConfig
import com.example.bike_.data.map.MapTileSources
import com.example.bike_.ui.theme.LocalBikeUiPalette
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.example.bike_.R

private enum class SelectMode { VIEW_RECT, RADIUS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as BikeApplication
    val vm: OfflineMapsViewModel = viewModel(factory = OfflineMapsViewModel.factory(app))
    val palette = LocalBikeUiPalette.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val tileSourceKey by vm.mapTileSource.collectAsStateWithLifecycle()
    val useNetwork by vm.mapUseNetwork.collectAsStateWithLifecycle()
    val cacheSize by vm.cacheSizeBytes.collectAsStateWithLifecycle()
    val download by vm.download.collectAsStateWithLifecycle()
    val currentLoc by vm.currentLocation.collectAsStateWithLifecycle()

    var mode by rememberSaveable { mutableStateOf(SelectMode.VIEW_RECT) }
    var radiusKm by rememberSaveable { mutableDoubleStateOf(5.0) }
    var radiusCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var zoomMin by rememberSaveable { mutableStateOf(10) }
    var zoomMax by rememberSaveable { mutableStateOf(16) }
    var showLayers by rememberSaveable { mutableStateOf(false) }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true || granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) vm.fetchCurrentLocation(context)
    }

    LaunchedEffect(Unit) {
        AppMapConfig.ensureConfigured(context)
        vm.refreshCacheSize()
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) vm.fetchCurrentLocation(context)
    }

    LaunchedEffect(currentLoc) {
        if (radiusCenter == null && currentLoc != null) radiusCenter = currentLoc
    }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(12.0)
            setUseDataConnection(true)
        }
    }
    val selectionPolygon = remember {
        Polygon().apply {
            outlinePaint.strokeWidth = 5f
            outlinePaint.color = Color.parseColor("#1976D2")
            outlinePaint.style = Paint.Style.STROKE
            fillPaint.color = Color.parseColor("#331976D2")
            outlinePaint.isAntiAlias = true
        }
    }
    val tapReceiver = remember {
        object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (mode == SelectMode.RADIUS && p != null) {
                    radiusCenter = p
                    return true
                }
                return false
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }
    }
    val tapOverlay = remember { MapEventsOverlay(tapReceiver) }

    fun updateSelectionOverlay() {
        try {
            val pts: List<GeoPoint> = when (mode) {
                SelectMode.VIEW_RECT -> {
                    val b = mapView.boundingBox
                    listOf(
                        GeoPoint(b.latNorth, b.lonWest),
                        GeoPoint(b.latNorth, b.lonEast),
                        GeoPoint(b.latSouth, b.lonEast),
                        GeoPoint(b.latSouth, b.lonWest)
                    )
                }
                SelectMode.RADIUS -> {
                    val c = radiusCenter
                    if (c == null) emptyList() else Polygon.pointsAsCircle(c, radiusKm * 1000.0)
                }
            }
            selectionPolygon.points = pts
            mapView.invalidate()
        } catch (_: Exception) { }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val obs = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { mapView.onResume() }
            override fun onPause(owner: LifecycleOwner) { mapView.onPause() }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        mapView.onResume()
        if (!mapView.overlays.contains(tapOverlay)) mapView.overlays.add(tapOverlay)
        if (!mapView.overlays.contains(selectionPolygon)) mapView.overlays.add(selectionPolygon)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            try { mapView.overlays.remove(tapOverlay) } catch (_: Exception) {}
            try { mapView.overlays.remove(selectionPolygon) } catch (_: Exception) {}
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(tileSourceKey, useNetwork) {
        try { mapView.setTileSource(MapTileSources.source(tileSourceKey)) } catch (_: Exception) { }
        mapView.setUseDataConnection(useNetwork)
        mapView.invalidate()
    }
    LaunchedEffect(mode, radiusKm, radiusCenter) { updateSelectionOverlay() }

    Scaffold(
        containerColor = palette.screenBg,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.offline_maps_title), color = palette.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = palette.textPrimary) }
                },
                actions = {
                    IconButton(onClick = { showLayers = !showLayers }) { Icon(Icons.Filled.Layers, contentDescription = stringResource(R.string.offline_maps_layers), tint = palette.accent) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.cardBg)
            )
        }
    ) { inner ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showLayers) {
                Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.offline_maps_layer), fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            MapTileSources.allKeys.take(2).forEach { k ->
                                FilterChip(selected = tileSourceKey == k, onClick = { vm.setTileSource(k) }, label = { Text(MapTileSources.label(k), maxLines = 1) }, colors = chipColors(palette))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            MapTileSources.allKeys.drop(2).forEach { k ->
                                FilterChip(selected = tileSourceKey == k, onClick = { vm.setTileSource(k) }, label = { Text(MapTileSources.label(k), maxLines = 1) }, colors = chipColors(palette))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.offline_maps_use_network), color = palette.textPrimary)
                                Text(stringResource(R.string.offline_maps_use_network_hint), color = palette.textSecondary, fontSize = 12.sp)
                            }
                            Switch(checked = useNetwork, onCheckedChange = { vm.setUseNetwork(it) }, colors = SwitchDefaults.colors(checkedTrackColor = palette.accent.copy(alpha = 0.5f), checkedThumbColor = palette.accent))
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.offline_maps_area), fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mode == SelectMode.VIEW_RECT, onClick = { mode = SelectMode.VIEW_RECT }, label = { Text(stringResource(R.string.offline_maps_rect)) }, colors = chipColors(palette))
                        FilterChip(selected = mode == SelectMode.RADIUS, onClick = { mode = SelectMode.RADIUS }, label = { Text(stringResource(R.string.offline_maps_radius)) }, colors = chipColors(palette))
                    }
                    if (mode == SelectMode.RADIUS) {
                        Text(stringResource(R.string.offline_maps_radius_value, radiusKm), color = palette.textSecondary, fontSize = 13.sp)
                        Slider(value = radiusKm.toFloat(), onValueChange = { radiusKm = it.toDouble().coerceIn(0.5, 50.0) }, valueRange = 0.5f..50f)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = {
                                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (fine || coarse) {
                                    vm.fetchCurrentLocation(context)
                                    currentLoc?.let { radiusCenter = it; mapView.controller.animateTo(it) }
                                } else {
                                    locationPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                }
                            }) {
                                Icon(Icons.Filled.MyLocation, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.offline_maps_my_location))
                            }
                            Text(
                                radiusCenter?.let { String.format(Locale.US, "%.4f, %.4f", it.latitude, it.longitude) } ?: stringResource(R.string.offline_maps_no_point),
                                color = palette.textSecondary, fontSize = 12.sp
                            )
                        }
                    } else {
                        Text(stringResource(R.string.offline_maps_rect_hint), color = palette.textSecondary, fontSize = 13.sp)
                    }
                    Text(stringResource(R.string.offline_maps_zoom, zoomMin, zoomMax), color = palette.textSecondary, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.offline_maps_min_zoom, zoomMin), color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.width(64.dp))
                        Slider(value = zoomMin.toFloat(), onValueChange = { zoomMin = it.roundToInt().coerceIn(0, zoomMax) }, valueRange = 0f..17f, modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.offline_maps_max_zoom, zoomMax), color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.width(64.dp))
                        Slider(value = zoomMax.toFloat(), onValueChange = { zoomMax = it.roundToInt().coerceIn(zoomMin, 17) }, valueRange = 0f..17f, modifier = Modifier.weight(1f))
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(320.dp)) {
                Box(Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, palette.border, RoundedCornerShape(10.dp))) {
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize(), update = { updateSelectionOverlay() })
                    OutlinedButton(
                        onClick = { updateSelectionOverlay() },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text(stringResource(R.string.offline_maps_refresh_frame), fontSize = 12.sp) }
                }
            }

            if (download.active || download.message != null || download.completed || download.cancelled) {
                Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.offline_maps_downloading), fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        if (download.total > 0) {
                            val frac = (download.processed.toFloat() / download.total.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.offline_maps_progress, download.processed, download.total), color = palette.textSecondary, fontSize = 13.sp)
                        } else if (download.active) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        download.message?.let { Text(it, color = if (it.startsWith("Error") || it.contains("Area too large") || it.contains("Invalid") || it.contains("errors")) palette.danger else palette.textSecondary, fontSize = 13.sp) }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (!download.active) {
                    Button(
                        onClick = {
                            updateSelectionOverlay()
                            val box = when (mode) {
                                SelectMode.VIEW_RECT -> try { mapView.boundingBox } catch (_: Exception) { null }
                                SelectMode.RADIUS -> {
                                    val c = radiusCenter ?: currentLoc
                                    if (c == null) null else OfflineMapsViewModel.bboxAround(c, radiusKm * 1000.0)
                                }
                            }
                            if (box != null) {
                                vm.startDownload(mapView, box, zoomMin, zoomMax)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = androidx.compose.ui.graphics.Color.White)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.offline_maps_download, zoomMin, zoomMax))
                    }
                } else {
                    Button(
                        onClick = { vm.cancelDownload() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.danger, contentColor = androidx.compose.ui.graphics.Color.White)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.cancel))
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.offline_maps_cache), fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Text(stringResource(R.string.offline_maps_used, formatMb(cacheSize)), color = palette.textSecondary)
                    Text(stringResource(R.string.offline_maps_storage), color = palette.textSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.refreshCacheSize() }) { Text(stringResource(R.string.offline_maps_refresh)) }
                        OutlinedButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.offline_maps_clear_cache))
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.offline_maps_tip),
                color = palette.textSecondary,
                fontSize = 12.sp
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = palette.cardBg,
            title = { Text(stringResource(R.string.offline_maps_clear_title), color = palette.textPrimary) },
            text = { Text(stringResource(R.string.offline_maps_clear_text), color = palette.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    vm.clearCache()
                }) { Text(stringResource(R.string.offline_maps_clear_confirm), color = palette.danger) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun chipColors(palette: com.example.bike_.ui.theme.BikeUiPalette) = FilterChipDefaults.filterChipColors(
    containerColor = palette.mutedBg,
    labelColor = palette.textSecondary,
    selectedContainerColor = palette.accent.copy(alpha = 0.16f),
    selectedLabelColor = palette.accent
)

private fun formatMb(bytes: Long): String = String.format(Locale.US, "%.1f", bytes.toDouble() / (1024.0 * 1024.0))
