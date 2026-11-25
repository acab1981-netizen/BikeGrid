package com.example.bike_.ui.bikecomputer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.BugReport
import com.example.bike_.BuildConfig
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bike_.BikeApplication
import com.example.bike_.R
import com.example.bike_.data.db.entity.WorkoutEntity
import com.example.bike_.data.map.AppMapConfig
import com.example.bike_.data.map.MapTileSources
import com.example.bike_.data.route.RouteFileParser
import com.example.bike_.data.settings.CyclingSettings
import com.example.bike_.domain.model.LoadedRoute
import com.example.bike_.domain.model.TrackLatLng
import com.example.bike_.domain.analytics.TrainingAnalytics
import com.example.bike_.domain.model.WorkoutMetrics
import com.example.bike_.service.WorkoutTrackingService
import com.example.bike_.ui.theme.BikeUiPalette
import com.example.bike_.ui.theme.LocalBikeUiPalette
import com.example.bike_.ui.theme.bikeAccentColor
import com.example.bike_.ui.tracking.TrackingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BikeComputerScreen(
    modifier: Modifier = Modifier,
    onOpenOfflineMaps: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenWorkouts: () -> Unit = {},
    onOpenRoutes: () -> Unit = {},
    viewModel: TrackingViewModel = viewModel()
) {
    val palette = LocalBikeUiPalette.current
    val context = LocalContext.current
    val app = context.applicationContext as BikeApplication
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val dashboardFields by viewModel.dashboardFields.collectAsStateWithLifecycle()
    val loadedRoute by viewModel.loadedRoute.collectAsStateWithLifecycle()
    val uiTheme by viewModel.uiTheme.collectAsStateWithLifecycle()
    val uiAccent by viewModel.uiAccent.collectAsStateWithLifecycle()
    val uiDefaultView by viewModel.uiDefaultView.collectAsStateWithLifecycle()
    val uiCompactCards by viewModel.uiCompactCards.collectAsStateWithLifecycle()
    val uiShowMapControls by viewModel.uiShowMapControls.collectAsStateWithLifecycle()
    val autoPauseEnabled by viewModel.autoPauseEnabled.collectAsStateWithLifecycle()
    val autoLapDistanceMeters by viewModel.autoLapDistanceMeters.collectAsStateWithLifecycle()
    val walkingStrideCm by viewModel.walkingStrideCm.collectAsStateWithLifecycle()
    val runningStrideCm by viewModel.runningStrideCm.collectAsStateWithLifecycle()
    val mapTileSource by viewModel.mapTileSource.collectAsStateWithLifecycle()
    val mapUseNetwork by viewModel.mapUseNetwork.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var selectedTab by rememberSaveable(uiDefaultView) {
        mutableIntStateOf(if (uiDefaultView == CyclingSettings.UI_VIEW_MAP) 1 else 0)
    }
    var showStopConfirm by rememberSaveable { mutableStateOf(false) }
    var showSimulatorDialog by rememberSaveable { mutableStateOf(false) }
    val simState by viewModel.simulatorState.collectAsState()
    var pendingStartType by rememberSaveable { mutableStateOf<String?>(null) }
    var showBatteryDialog by rememberSaveable { mutableStateOf(false) }
    // Crash recovery is handled globally in MainActivity.
    var showCustomizeDialog by rememberSaveable { mutableStateOf(false) }
    var importingRoute by rememberSaveable { mutableStateOf(false) }
    var selectedWorkoutType by rememberSaveable {
        mutableStateOf(WorkoutTrackingService.WORKOUT_TYPE_CYCLING)
    }
    val isFootWorkout = selectedWorkoutType == WorkoutTrackingService.WORKOUT_TYPE_RUNNING ||
        selectedWorkoutType == WorkoutTrackingService.WORKOUT_TYPE_WALKING
    val visibleDashboardFields = remember(dashboardFields, isFootWorkout) {
        dashboardFieldsForType(dashboardFields, isFootWorkout)
    }

    val gpxPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importingRoute = true
        scope.launch {
            try {
                val route = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        try { RouteFileParser.parse(input) }
                        catch (e: Exception) { android.widget.Toast.makeText(context, e.message ?: context.getString(R.string.route_import_failed), android.widget.Toast.LENGTH_LONG).show(); null }
                    }
                }
                if (route == null || route.points.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.route_import_failed), Toast.LENGTH_LONG).show()
                } else {
                    viewModel.setLoadedRoute(route)
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.route_import_loaded,
                            route.name ?: context.getString(R.string.route_import_default_name),
                            route.points.size
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    selectedTab = 1
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.route_import_error, e.message ?: context.getString(R.string.unknown_error)),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                importingRoute = false
            }
        }
    }

    fun proceedStart(type: String) {
        viewModel.startWorkout(type)
    }

    fun tryStart(type: String) {
        if (metrics.isTracking) return
        if (shouldPromptBatteryOptimization(context, app)) {
            pendingStartType = type
            showBatteryDialog = true
        } else {
            proceedStart(type)
        }
    }

    KeepScreenOnEffect(enabled = metrics.isTracking)

    // Recovery handled in MainActivity.

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.screenBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = palette.cardBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ViewToggleChip(
                    selected = selectedTab == 0,
                    icon = Icons.Filled.Speed,
                    label = stringResource(R.string.bc_metrics),
                    modifier = Modifier.weight(1f),
                    palette = palette,
                    onClick = { selectedTab = 0 }
                )
                Spacer(Modifier.width(4.dp))
                ViewToggleChip(
                    selected = selectedTab == 1,
                    icon = Icons.Filled.Map,
                    label = stringResource(R.string.bc_map),
                    modifier = Modifier.weight(1f),
                    palette = palette,
                    onClick = { selectedTab = 1 }
                )
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onOpenWorkouts, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Route, contentDescription = stringResource(R.string.sw_library), tint = palette.accent, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onOpenProfile, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.profile_title), tint = palette.accent, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { showCustomizeDialog = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.bc_settings_view), tint = palette.accent, modifier = Modifier.size(20.dp))
                        }
                        if (BuildConfig.ENABLE_TEST_TOOLS) {
                            IconButton(onClick = { showSimulatorDialog = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.BugReport, contentDescription = "Simulator", tint = if (simState.enabled) palette.accent else palette.textSecondary, modifier = Modifier.size(20.dp))
                            }
                        }
                        if (selectedTab == 1) {
                            IconButton(onClick = onOpenRoutes, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Route, contentDescription = stringResource(R.string.bc_routes), tint = palette.accent, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onOpenOfflineMaps, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.bc_offline_maps), tint = palette.accent, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (selectedTab == 0) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    metrics.structuredWorkout?.let { sw ->
                        StructuredWorkoutPlayerCard(
                            state = sw,
                            currentPower = metrics.smoothedPower3sWatts ?: metrics.powerWatts,
                            palette = palette,
                            onSkip = { viewModel.skipStructuredStep() },
                            onRepeat = { viewModel.repeatStructuredStep() },
                            onPauseResume = { if (metrics.isAutoPaused) viewModel.resumeWorkout() else viewModel.pauseWorkout() },
                            isPaused = metrics.isAutoPaused
                        )
                    }
                    if (metrics.ftmsConnected && !isFootWorkout) {
                        SmartTrainerPanel(
                            metrics = metrics,
                            palette = palette,
                            onModeErg = { viewModel.setFtmsMode(true) },
                            onModeResistance = { viewModel.setFtmsMode(false) },
                            onMinus = { viewModel.adjustFtms(if (metrics.ftmsModeErg) -5 else -5) },
                            onPlus = { viewModel.adjustFtms(if (metrics.ftmsModeErg) 5 else 5) }
                        )
                    }
                    MetricsDashboard(
                        metrics = metrics,
                        selectedFields = visibleDashboardFields,
                        compact = uiCompactCards,
                        palette = palette,
                        onCustomize = { showCustomizeDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                LiveMapCard(
                    trackPoints = metrics.trackPoints,
                    route = loadedRoute,
                    metrics = metrics,
                    importingRoute = importingRoute,
                    showControls = uiShowMapControls,
                    tileSourceKey = mapTileSource,
                    useNetwork = mapUseNetwork,
                    palette = palette,
                    onOpenRoutes = onOpenRoutes,
                    onClearRoute = { viewModel.clearLoadedRoute() },
                    onOpenOfflineMaps = onOpenOfflineMaps
                )
            }
        }

        ControlPanel(
            metrics = metrics,
            palette = palette,
            selectedType = selectedWorkoutType,
            onSelectType = { selectedWorkoutType = it },
            onStart = { type -> tryStart(type) },
            onPause = { viewModel.pauseWorkout() },
            onResume = { viewModel.resumeWorkout() },
            onLap = { viewModel.lapWorkout() },
            onStopRequest = { showStopConfirm = true }
        )
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            containerColor = palette.cardBg,
            title = { Text(stringResource(R.string.bc_stop_workout_title), color = palette.textPrimary) },
            text = { Text(stringResource(R.string.bc_stop_workout_text), color = palette.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    viewModel.stopWorkout()
                    selectedTab = 0
                }) { Text(stringResource(R.string.bc_finish), color = palette.accent) }
            },
            dismissButton = { TextButton(onClick = { showStopConfirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false; pendingStartType = null },
            containerColor = palette.cardBg,
            title = { Text(stringResource(R.string.bc_battery_title), color = palette.textPrimary) },
            text = { Text(stringResource(R.string.bc_battery_text), color = palette.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    val type = pendingStartType ?: WorkoutTrackingService.WORKOUT_TYPE_CYCLING
                    app.cyclingSettings.setBatteryOptimizationPromptShown()
                    requestIgnoreBatteryOptimizations(context)
                    showBatteryDialog = false
                    pendingStartType = null
                    proceedStart(type)
                }) { Text(stringResource(R.string.bc_disable_optimization), color = palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val type = pendingStartType ?: WorkoutTrackingService.WORKOUT_TYPE_CYCLING
                    app.cyclingSettings.setBatteryOptimizationPromptShown()
                    showBatteryDialog = false
                    pendingStartType = null
                    proceedStart(type)
                }) { Text(stringResource(R.string.bc_continue_without_changes)) }
            }
        )
    }

    if (showCustomizeDialog) {
        CustomizeScreenDialog(
            currentFields = dashboardFields,
            currentTheme = uiTheme,
            currentAccent = uiAccent,
            currentDefaultView = uiDefaultView,
            compactCards = uiCompactCards,
            showMapControls = uiShowMapControls,
            autoPauseEnabled = autoPauseEnabled,
            autoLapDistanceMeters = autoLapDistanceMeters,
            walkingStrideCm = walkingStrideCm,
            runningStrideCm = runningStrideCm,
            palette = palette,
            onDismiss = { showCustomizeDialog = false },
            onSave = { fields, theme, accent, defaultView, compact, mapControls, autoPause, autoLapM, walkStride, runStride ->
                viewModel.setDashboardFields(fields)
                viewModel.setUiTheme(theme)
                viewModel.setUiAccent(accent)
                viewModel.setUiDefaultView(defaultView)
                viewModel.setUiCompactCards(compact)
                viewModel.setUiShowMapControls(mapControls)
                viewModel.setAutoPauseEnabled(autoPause)
                viewModel.setAutoLapDistanceMeters(autoLapM)
                viewModel.setWalkingStrideCm(walkStride)
                viewModel.setRunningStrideCm(runStride)
                selectedTab = if (defaultView == CyclingSettings.UI_VIEW_MAP) 1 else 0
                showCustomizeDialog = false
            }
        )
    }

    if (BuildConfig.ENABLE_TEST_TOOLS && showSimulatorDialog) {
        SimulatorDialog(
            sim = simState,
            hasRoute = loadedRoute != null,
            palette = palette,
            onDismiss = { showSimulatorDialog = false },
            onEnabled = viewModel::setSimulatorEnabled,
            onPower = viewModel::setSimPower,
            onHr = viewModel::setSimHr,
            onCadence = viewModel::setSimCadence,
            onSpeed = viewModel::setSimSpeed,
            onAutoIntervals = viewModel::setSimAutoIntervals,
            onFollowRoute = viewModel::setSimFollowRoute,
            onOffset100m = viewModel::simOffsetRoute100m,
            onResetOffset = viewModel::simResetRouteOffset,
            onGenerateHistory = viewModel::generateTestHistory30Days
        )
    }

    // Unfinished-workout recovery dialog is shown globally in MainActivity.
}

@Composable
private fun ViewToggleChip(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    palette: BikeUiPalette,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(40.dp),
        label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1, fontSize = 13.sp, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = palette.mutedBg,
            labelColor = palette.textSecondary,
            iconColor = palette.textSecondary,
            selectedContainerColor = palette.accent.copy(alpha = 0.16f),
            selectedLabelColor = palette.accent,
            selectedLeadingIconColor = palette.accent
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = palette.border,
            selectedBorderColor = palette.accent.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun KeepScreenOnEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
private fun MetricsDashboard(
    metrics: WorkoutMetrics,
    selectedFields: List<String>,
    compact: Boolean,
    palette: BikeUiPalette,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val compactHeight = configuration.screenHeightDp < 520
    val effectiveCompact = compact || compactHeight
    val spacing = if (effectiveCompact) 4.dp else 6.dp
    val speedFont = when {
        compactHeight && isLandscape -> 30.sp
        effectiveCompact -> 38.sp
        else -> 52.sp
    }
    val speedLine = when {
        compactHeight && isLandscape -> 34.sp
        effectiveCompact -> 44.sp
        else -> 60.sp
    }
    val tileValueFont = when {
        compactHeight && isLandscape -> 19.sp
        effectiveCompact -> 21.sp
        else -> 24.sp
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = palette.cardBg),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                    .padding(vertical = if (effectiveCompact) 5.dp else 10.dp, horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.bc_speed), color = palette.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp)
                Text(
                    String.format(Locale.US, "%.1f", metrics.currentSpeedKmh),
                    fontSize = speedFont,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary,
                    lineHeight = speedLine
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.bc_kmh), color = palette.textSecondary, fontSize = 12.sp)
                    val status = if (!metrics.isTracking) stringResource(R.string.bc_ready) else if (metrics.isAutoPaused) stringResource(R.string.bc_auto_paused) else stringResource(R.string.bc_recording)
                    val statusColor = when {
                        !metrics.isTracking -> palette.textSecondary
                        metrics.isAutoPaused -> Color(0xFFB26A00)
                        else -> palette.accent
                    }
                    Text("•", color = palette.textSecondary, fontSize = 12.sp)
                    Text(status, color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        val tiles = selectedFields.mapNotNull { key -> metricTileFor(key, metrics) }
        if (tiles.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.bc_no_fields), color = palette.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onCustomize) { Text(stringResource(R.string.bc_configure_fields)) }
                }
            }
        } else {
            // Обычные строки вместо LazyVerticalGrid: вся вкладка прокручивается целиком,
            // поэтому все показатели доступны даже при активной тренировке на станке.
            val tileColumns = if (configuration.screenWidthDp >= 600) 4 else 3
            for (rowTiles in tiles.chunked(tileColumns)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    for (tile in rowTiles) {
                        val targetBg = tile.zoneColor?.copy(alpha = 0.16f) ?: palette.cardBg
                        val targetBorder = tile.zoneColor?.copy(alpha = 0.65f) ?: palette.border
                        val bg by animateColorAsState(targetBg, label = "tileBg_${tile.label}")
                        val border by animateColorAsState(targetBorder, label = "tileBorder_${tile.label}")
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = if (effectiveCompact) 66.dp else 78.dp),
                            colors = CardDefaults.cardColors(containerColor = bg),
                            shape = RoundedCornerShape(11.dp)
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, border, RoundedCornerShape(11.dp))
                                    .padding(horizontal = 10.dp, vertical = if (effectiveCompact) 6.dp else 9.dp)
                            ) {
                                Text(tile.label, fontSize = 12.sp, color = palette.textSecondary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(tile.value, fontSize = tileValueFont, fontWeight = FontWeight.Bold, color = tile.zoneColor ?: palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = tileValueFont)
                                    if (tile.unit.isNotBlank()) {
                                        Text(tile.unit, fontSize = 10.sp, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 1.dp))
                                    }
                                }
                                if (tile.zoneLabel != null) {
                                    Text(tile.zoneLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = tile.zoneColor ?: palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (metrics.laps.isNotEmpty()) {
            Text(
                stringResource(R.string.bc_laps_summary, metrics.laps.size, formatDuration(metrics.laps.last().durationMs), metrics.laps.last().distanceMeters / 1000.0),
                color = palette.textSecondary,
                fontSize = 13.sp
            )
        }

        LapToast(metrics = metrics, palette = palette)
    }
}

private data class MetricTile(
    val label: String,
    val value: String,
    val unit: String,
    val zoneLabel: String? = null,
    val zoneColor: Color? = null
)

@Composable
private fun metricTileFor(key: String, m: WorkoutMetrics): MetricTile? {
    return when (key) {
        "distance" -> MetricTile(stringResource(R.string.metric_distance), String.format(Locale.US, "%.2f", m.distanceMeters / 1000.0), stringResource(R.string.unit_km))
        "time" -> MetricTile(stringResource(R.string.metric_time), formatDuration(m.elapsedTimeMs), "")
        "moving_time" -> MetricTile(stringResource(R.string.metric_moving_time), formatDuration(m.movingTimeMs), "")
        "avg_speed" -> MetricTile(stringResource(R.string.metric_avg_speed), String.format(Locale.US, "%.1f", m.averageSpeedKmh), stringResource(R.string.unit_kmh))
        "max_speed" -> MetricTile(stringResource(R.string.metric_max_speed), String.format(Locale.US, "%.1f", m.maxSpeedKmh), stringResource(R.string.unit_kmh))
        "heart_rate" -> {
            val idx = (m.currentHrZone ?: 0) - 1
            val zone = idx.takeIf { it in TrainingAnalytics.HR_ZONE_NAMES.indices }
            val zoneLabel = zone?.let { "Z${it + 1} ${stringResource(hrZoneNameRes(it))}" }
            val zoneColor = zone?.let { Color(TrainingAnalytics.HR_ZONE_COLOR_ARGBS[it].toInt()) }
            MetricTile(stringResource(R.string.metric_heart_rate), m.heartRateBpm?.toString() ?: "--", stringResource(R.string.unit_bpm), zoneLabel, zoneColor)
        }
        "cadence" -> MetricTile(stringResource(R.string.metric_cadence), m.cadenceRpm?.let { String.format(Locale.US, "%.0f", it) } ?: "--", stringResource(R.string.unit_rpm))
        "power" -> {
            val idx = (m.currentPowerZone ?: 0) - 1
            val zone = idx.takeIf { it in TrainingAnalytics.POWER_ZONE_NAMES.indices }
            val zoneLabel = zone?.let { "Z${it + 1} ${stringResource(powerZoneNameRes(it))}" }
            val zoneColor = zone?.let { Color(TrainingAnalytics.POWER_ZONE_COLOR_ARGBS[it].toInt()) }
            val displayPower = m.smoothedPower3sWatts ?: m.powerWatts
            MetricTile(stringResource(R.string.metric_power), displayPower?.toString() ?: "--", stringResource(R.string.unit_w), zoneLabel, zoneColor)
        }
        "elevation" -> MetricTile(stringResource(R.string.metric_elevation), String.format(Locale.US, "%.0f", m.elevationGainMeters), stringResource(R.string.unit_m))
        "steps" -> MetricTile(stringResource(R.string.metric_steps), m.steps.toString(), stringResource(R.string.unit_steps))
        "step_cadence" -> MetricTile(stringResource(R.string.metric_step_cadence), m.stepCadenceSpm.toString(), stringResource(R.string.unit_spm))
        "pace" -> MetricTile(stringResource(R.string.metric_pace), formatPace(m.averageSpeedKmh), stringResource(R.string.unit_min_per_km))
        else -> null
    }
}

@Composable
private fun StructuredWorkoutPlayerCard(
    state: com.example.bike_.domain.model.StructuredWorkoutUiState,
    currentPower: Int?,
    palette: BikeUiPalette,
    onSkip: () -> Unit,
    onRepeat: () -> Unit,
    onPauseResume: () -> Unit,
    isPaused: Boolean
) {
    val step = state.steps.getOrNull(state.currentStepIndex)
    val targetText = when {
        state.currentTargetMinWatts != null && state.currentTargetMaxWatts != null ->
            stringResource(R.string.sw_target_fmt, state.currentTargetMinWatts, state.currentTargetMaxWatts)
        step?.targetType == "HR_ZONE" -> stringResource(R.string.sw_hr_target_fmt, step.targetMin, step.targetMax)
        else -> "--"
    }
    val complianceColor = when (state.compliance) {
        "IN_RANGE" -> Color(0xFF2E7D32)
        "BELOW" -> Color(0xFF1976D2)
        "ABOVE" -> Color(0xFFC62828)
        else -> palette.textSecondary
    }
    val complianceText = when (state.compliance) {
        "IN_RANGE" -> stringResource(R.string.sw_player_in_target)
        "BELOW" -> {
            val delta = ((state.currentTargetMinWatts ?: 0) - (currentPower ?: 0)).coerceAtLeast(0)
            stringResource(R.string.sw_player_increase, delta)
        }
        "ABOVE" -> {
            val delta = ((currentPower ?: 0) - (state.currentTargetMaxWatts ?: 0)).coerceAtLeast(0)
            stringResource(R.string.sw_player_decrease, delta)
        }
        else -> targetText
    }
    val nextStepName = when (state.nextStepType) {
        "WARMUP" -> stringResource(R.string.sw_step_warmup)
        "WORK" -> stringResource(R.string.sw_step_work)
        "RECOVERY" -> stringResource(R.string.sw_step_recovery)
        "COOLDOWN" -> stringResource(R.string.sw_step_cooldown)
        else -> state.nextStepLabel
    }

    Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(state.title, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(formatDuration(state.stepRemainingSec * 1000L), color = palette.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(targetText, color = palette.textSecondary, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(complianceText, color = complianceColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    val next = if (!nextStepName.isNullOrBlank()) "$nextStepName ${state.nextStepTargetLabel ?: ""}".trim() else null
                    if (next != null) Text(stringResource(R.string.sw_player_next, next), color = palette.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Mini profile chart
            val steps = state.steps
            if (steps.isNotEmpty()) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    val n = steps.size
                    val gap = 2f
                    val barW = ((size.width - gap * (n - 1).coerceAtLeast(0)) / n.toFloat()).coerceAtLeast(2f)
                    val maxPct = steps.maxOf { maxOf(it.targetMax, it.targetMin, 100) }.coerceAtLeast(100).toFloat()
                    steps.forEachIndexed { i, s ->
                        val hFrac = (s.targetMax.coerceAtLeast(30) / maxPct).coerceIn(0.12f, 1f)
                        val x = i * (barW + gap)
                        val h = size.height * hFrac
                        val zoneIdx = com.example.bike_.domain.analytics.TrainingAnalytics.powerZoneIndex(((s.targetMin + s.targetMax) / 2 * 2), 200) ?: 0
                        val color = Color(com.example.bike_.domain.analytics.TrainingAnalytics.POWER_ZONE_COLOR_ARGBS.getOrElse(zoneIdx) { palette.accent.value.toLong() })
                        val alpha = if (i < state.currentStepIndex) 0.28f else if (i == state.currentStepIndex) 0.95f else 0.65f
                        drawRect(color = color.copy(alpha = alpha), topLeft = androidx.compose.ui.geometry.Offset(x, size.height - h), size = androidx.compose.ui.geometry.Size(barW, h))
                        if (i == state.currentStepIndex) {
                            drawRect(color = Color.White.copy(alpha = 0.9f), topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(barW, size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                        }
                    }
                    val progress = if (state.totalDurationSec > 0) (state.totalElapsedSec.toFloat() / state.totalDurationSec.toFloat()).coerceIn(0f, 1f) else 0f
                    val px = size.width * progress
                    drawLine(Color.White, androidx.compose.ui.geometry.Offset(px, 0f), androidx.compose.ui.geometry.Offset(px, size.height), strokeWidth = 2f)
                }
            }

            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onPauseResume,
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text(if (isPaused) stringResource(R.string.bc_resume) else stringResource(R.string.bc_pause), fontSize = 11.sp, maxLines = 1) }
                    OutlinedButton(
                        onClick = onRepeat,
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text(stringResource(R.string.sw_repeat_step), fontSize = 11.sp, maxLines = 1) }
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text(stringResource(R.string.sw_skip), fontSize = 11.sp, maxLines = 1) }
                }
            }
        }
    }
}


@Composable
private fun SmartTrainerPanel(
    metrics: WorkoutMetrics,
    palette: BikeUiPalette,
    onModeErg: () -> Unit,
    onModeResistance: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    val statusColor = when {
        !metrics.ftmsConnected -> palette.textSecondary
        metrics.ftmsLastError != null -> Color(0xFFE53935)
        metrics.ftmsControlGranted -> Color(0xFF2E7D32)
        else -> Color(0xFFB26A00)
    }
    val statusText = when {
        !metrics.ftmsConnected -> stringResource(R.string.ftms_status_disconnected)
        metrics.ftmsLastError != null -> stringResource(R.string.ftms_status_error)
        metrics.ftmsControlGranted -> stringResource(R.string.ftms_status_control)
        else -> stringResource(R.string.ftms_status_connected)
    }
    Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ftms_panel_title), color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (!metrics.ftmsDeviceName.isNullOrBlank()) {
                        Text(metrics.ftmsDeviceName!!, color = palette.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(statusText, color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = metrics.ftmsModeErg,
                        onClick = onModeErg,
                        label = { Text(stringResource(R.string.ftms_mode_erg), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                        colors = chipColors(palette),
                        modifier = Modifier.weight(1f).height(30.dp)
                    )
                    FilterChip(
                        selected = !metrics.ftmsModeErg,
                        onClick = onModeResistance,
                        label = { Text(stringResource(R.string.ftms_mode_resistance), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                        colors = chipColors(palette),
                        modifier = Modifier.weight(1f).height(30.dp)
                    )
                }
            }
            val targetLabel = if (metrics.ftmsModeErg) {
                stringResource(R.string.ftms_target_power, metrics.ftmsTargetPowerWatts ?: 0)
            } else {
                stringResource(R.string.ftms_target_resistance, metrics.ftmsTargetResistancePercent ?: 0)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(targetLabel, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onMinus, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(30.dp)) {
                        Text(if (metrics.ftmsModeErg) stringResource(R.string.ftms_power_minus) else stringResource(R.string.ftms_res_minus), fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = onPlus, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(30.dp)) {
                        Text(if (metrics.ftmsModeErg) stringResource(R.string.ftms_power_plus) else stringResource(R.string.ftms_res_plus), fontSize = 11.sp)
                    }
                }
            }
            if (!metrics.ftmsLastError.isNullOrBlank()) {
                Text(metrics.ftmsLastError!!, color = Color(0xFFE53935), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
@Composable
private fun autoLapLabel(meters: Int): String = when (meters) {
    0 -> stringResource(R.string.bc_auto_lap_off)
    1000 -> stringResource(R.string.bc_auto_lap_1km)
    5000 -> stringResource(R.string.bc_auto_lap_5km)
    10000 -> stringResource(R.string.bc_auto_lap_10km)
    else -> "${meters / 1000} km"
}

private fun hrZoneNameRes(index: Int): Int = when (index) {
    0 -> R.string.zone_hr_z1
    1 -> R.string.zone_hr_z2
    2 -> R.string.zone_hr_z3
    3 -> R.string.zone_hr_z4
    else -> R.string.zone_hr_z5
}


@Composable
private fun SimulatorDialog(
    sim: com.example.bike_.data.tracking.SimulatorState,
    hasRoute: Boolean,
    palette: BikeUiPalette,
    onDismiss: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onPower: (Int) -> Unit,
    onHr: (Int) -> Unit,
    onCadence: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onAutoIntervals: (Boolean) -> Unit,
    onFollowRoute: (Boolean) -> Unit,
    onOffset100m: () -> Unit,
    onResetOffset: () -> Unit,
    onGenerateHistory: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = { Text("Simulator / Debug", color = palette.textPrimary) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable simulator", color = palette.textPrimary)
                    Switch(checked = sim.enabled, onCheckedChange = onEnabled)
                }
                Text("Power: ${sim.powerWatts} W", color = palette.textSecondary, fontSize = 12.sp)
                Slider(value = sim.powerWatts.toFloat(), onValueChange = { onPower(it.toInt()) }, valueRange = 0f..600f)
                Text("HR: ${sim.heartRateBpm} bpm", color = palette.textSecondary, fontSize = 12.sp)
                Slider(value = sim.heartRateBpm.toFloat(), onValueChange = { onHr(it.toInt()) }, valueRange = 50f..200f)
                Text("Cadence: ${sim.cadenceRpm} rpm", color = palette.textSecondary, fontSize = 12.sp)
                Slider(value = sim.cadenceRpm.toFloat(), onValueChange = { onCadence(it.toInt()) }, valueRange = 0f..130f)
                Text("Speed: ${"%.1f".format(sim.speedKmh)} km/h", color = palette.textSecondary, fontSize = 12.sp)
                Slider(value = sim.speedKmh, onValueChange = onSpeed, valueRange = 0f..60f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-intervals (15s sine)", color = palette.textPrimary, fontSize = 13.sp)
                    Switch(checked = sim.autoIntervals, onCheckedChange = onAutoIntervals)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onFollowRoute(!sim.followRoute) }, enabled = hasRoute || sim.followRoute, modifier = Modifier.weight(1f)) {
                        Text(if (sim.followRoute) "Stop route sim" else "Move along GPX", fontSize = 11.sp, maxLines = 1)
                    }
                    OutlinedButton(onClick = onOffset100m, modifier = Modifier.weight(1f)) { Text("+100m offset", fontSize = 11.sp, maxLines = 1) }
                }
                OutlinedButton(onClick = onResetOffset, modifier = Modifier.fillMaxWidth()) { Text("Reset route offset", fontSize = 11.sp) }
                HorizontalDivider()
                OutlinedButton(onClick = onGenerateHistory, modifier = Modifier.fillMaxWidth()) { Text("Fill 30 days history", fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = palette.accent) } }
    )
}
private fun powerZoneNameRes(index: Int): Int = when (index) {
    0 -> R.string.zone_pwr_z1
    1 -> R.string.zone_pwr_z2
    2 -> R.string.zone_pwr_z3
    3 -> R.string.zone_pwr_z4
    4 -> R.string.zone_pwr_z5
    5 -> R.string.zone_pwr_z6
    else -> R.string.zone_pwr_z7
}

@Composable
private fun LapToast(metrics: WorkoutMetrics, palette: BikeUiPalette) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lap = metrics.lastCompletedLap
    val withinToastWindow = (now - metrics.lastCompletedLapAtMs) < 3000L
    LaunchedEffect(lap?.index, metrics.lastCompletedLapAtMs) {
        if (metrics.lastCompletedLapAtMs > 0L) {
            while (System.currentTimeMillis() - metrics.lastCompletedLapAtMs < 3000L) {
                now = System.currentTimeMillis()
                delay(250)
            }
            now = System.currentTimeMillis()
        }
    }
    if (lap != null && withinToastWindow) {
        Card(
            colors = CardDefaults.cardColors(containerColor = palette.accent.copy(alpha = 0.16f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        R.string.bc_lap_toast,
                        lap.index,
                        formatDuration(lap.durationMs),
                        lap.avgPowerWatts ?: 0,
                        lap.avgHeartRateBpm ?: 0
                    ),
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CustomizeScreenDialog(
    currentFields: List<String>,
    currentTheme: String,
    currentAccent: String,
    currentDefaultView: String,
    compactCards: Boolean,
    showMapControls: Boolean,
    autoPauseEnabled: Boolean,
    autoLapDistanceMeters: Int,
    walkingStrideCm: Float,
    runningStrideCm: Float,
    palette: BikeUiPalette,
    onDismiss: () -> Unit,
    onSave: (List<String>, String, String, String, Boolean, Boolean, Boolean, Int, Float, Float) -> Unit
) {
    var selectedFields by remember(currentFields) { mutableStateOf(currentFields.toSet()) }
    var selectedTheme by remember(currentTheme) { mutableStateOf(currentTheme) }
    var selectedAccent by remember(currentAccent) { mutableStateOf(currentAccent) }
    var selectedDefaultView by remember(currentDefaultView) { mutableStateOf(currentDefaultView) }
    var compact by remember(compactCards) { mutableStateOf(compactCards) }
    var mapControls by remember(showMapControls) { mutableStateOf(showMapControls) }
    var autoPause by remember(autoPauseEnabled) { mutableStateOf(autoPauseEnabled) }
    var autoLap by remember(autoLapDistanceMeters) { mutableIntStateOf(autoLapDistanceMeters) }
    var walkingStrideText by remember(walkingStrideCm) { mutableStateOf(walkingStrideCm.toInt().toString()) }
    var runningStrideText by remember(runningStrideCm) { mutableStateOf(runningStrideCm.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.96f),
        title = { Text(stringResource(R.string.bc_view_customize_title), color = palette.textPrimary, fontSize = 18.sp) },
        text = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = screenHeight * 0.72f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.bc_theme), fontWeight = FontWeight.SemiBold, color = palette.textPrimary, fontSize = 13.sp)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CyclingSettings.UI_THEMES.forEach { key ->
                            FilterChip(
                                selected = selectedTheme == key,
                                onClick = { selectedTheme = key },
                                label = { Text(bikeUiThemeLabel(key), fontSize = 12.sp, maxLines = 1) },
                                colors = chipColors(palette),
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }

                    Text(stringResource(R.string.bc_accent), fontWeight = FontWeight.SemiBold, color = palette.textPrimary, fontSize = 13.sp)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CyclingSettings.UI_ACCENTS.forEach { key ->
                            val c = bikeAccentColor(key)
                            FilterChip(
                                selected = selectedAccent == key,
                                onClick = { selectedAccent = key },
                                label = { Text(bikeUiAccentLabel(key), maxLines = 1, fontSize = 12.sp) },
                                leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(c)) },
                                colors = chipColors(palette),
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }

                    Text(stringResource(R.string.bc_panels), fontWeight = FontWeight.SemiBold, color = palette.textPrimary, fontSize = 13.sp)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.bc_start_on_map), color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Switch(
                            checked = selectedDefaultView == CyclingSettings.UI_VIEW_MAP,
                            onCheckedChange = { selectedDefaultView = if (it) CyclingSettings.UI_VIEW_MAP else CyclingSettings.UI_VIEW_METRICS },
                            modifier = Modifier.scale(0.75f),
                            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent.copy(alpha = 0.5f), checkedThumbColor = palette.accent)
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.bc_compact_cards), color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Switch(
                            checked = compact,
                            onCheckedChange = { compact = it },
                            modifier = Modifier.scale(0.75f),
                            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent.copy(alpha = 0.5f), checkedThumbColor = palette.accent)
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.bc_show_map_controls), color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Switch(
                            checked = mapControls,
                            onCheckedChange = { mapControls = it },
                            modifier = Modifier.scale(0.75f),
                            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent.copy(alpha = 0.5f), checkedThumbColor = palette.accent)
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(stringResource(R.string.bc_auto_pause_enabled), color = palette.textPrimary, fontSize = 13.sp)
                            Text(
                                stringResource(R.string.bc_auto_pause_enabled_hint),
                                fontSize = 11.sp,
                                color = palette.textSecondary,
                                lineHeight = 14.sp
                            )
                        }
                        Switch(
                            checked = autoPause,
                            onCheckedChange = { autoPause = it },
                            modifier = Modifier.scale(0.75f),
                            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent.copy(alpha = 0.5f), checkedThumbColor = palette.accent)
                        )
                    }

                    Text(stringResource(R.string.bc_auto_lap), fontWeight = FontWeight.SemiBold, color = palette.textPrimary, fontSize = 13.sp)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CyclingSettings.AUTO_LAP_OPTIONS_M.forEach { m ->
                            FilterChip(
                                selected = autoLap == m,
                                onClick = { autoLap = m },
                                label = { Text(autoLapLabel(m), fontSize = 12.sp, maxLines = 1) },
                                colors = chipColors(palette),
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }

                    Text(stringResource(R.string.bc_metric_fields), fontWeight = FontWeight.SemiBold, color = palette.textPrimary, fontSize = 13.sp)
                    Text(stringResource(R.string.bc_metric_fields_hint), fontSize = 11.sp, color = palette.textSecondary, lineHeight = 14.sp)
                    CyclingSettings.ALL_DASHBOARD_FIELDS.forEach { key ->
                        val checked = key in selectedFields
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { on ->
                                    selectedFields = if (on) selectedFields + key else selectedFields - key
                                },
                                modifier = Modifier.scale(0.8f),
                                colors = CheckboxDefaults.colors(checkedColor = palette.accent)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(bikeDashboardFieldLabel(key), color = palette.textPrimary, fontSize = 13.sp)
                        }
                    }

                    Text(stringResource(R.string.bc_stride_walking), fontWeight = FontWeight.SemiBold, color = palette.textPrimary, fontSize = 13.sp)
                    OutlinedTextField(
                        value = walkingStrideText,
                        onValueChange = { walkingStrideText = it.filter { c -> c.isDigit() }.take(3) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )
                    Text(stringResource(R.string.bc_stride_running), fontWeight = FontWeight.SemiBold, color = palette.textPrimary, fontSize = 13.sp)
                    OutlinedTextField(
                        value = runningStrideText,
                        onValueChange = { runningStrideText = it.filter { c -> c.isDigit() }.take(3) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )
                    Text(stringResource(R.string.bc_stride_hint), fontSize = 11.sp, color = palette.textSecondary, lineHeight = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ordered = CyclingSettings.ALL_DASHBOARD_FIELDS.filter { it in selectedFields }
                val fields = if (ordered.isEmpty()) CyclingSettings.DEFAULT_DASHBOARD_FIELDS else ordered
                val walkStride = walkingStrideText.toFloatOrNull() ?: walkingStrideCm
                val runStride = runningStrideText.toFloatOrNull() ?: runningStrideCm
                onSave(fields, selectedTheme, selectedAccent, selectedDefaultView, compact, mapControls, autoPause, autoLap, walkStride, runStride)
            }) { Text(stringResource(R.string.save), color = palette.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun chipColors(palette: BikeUiPalette) = FilterChipDefaults.filterChipColors(
    containerColor = palette.mutedBg,
    labelColor = palette.textSecondary,
    selectedContainerColor = palette.accent.copy(alpha = 0.16f),
    selectedLabelColor = palette.accent,
    selectedLeadingIconColor = palette.accent
)

@Composable
private fun ControlPanel(
    metrics: WorkoutMetrics,
    palette: BikeUiPalette,
    selectedType: String,
    onSelectType: (String) -> Unit,
    onStart: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onLap: () -> Unit,
    onStopRequest: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                if (!metrics.isTracking) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TypeChip(stringResource(R.string.bc_cycling), WorkoutTrackingService.WORKOUT_TYPE_CYCLING, selectedType, palette) { onSelectType(it) }
                        TypeChip(stringResource(R.string.bc_running), WorkoutTrackingService.WORKOUT_TYPE_RUNNING, selectedType, palette) { onSelectType(it) }
                        TypeChip(stringResource(R.string.bc_walking), WorkoutTrackingService.WORKOUT_TYPE_WALKING, selectedType, palette) { onSelectType(it) }
                    }
                    Button(
                        onClick = { onStart(selectedType) },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White)
                    ) { Text(stringResource(R.string.bc_start), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { if (metrics.isAutoPaused) onResume() else onPause() },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text(if (metrics.isAutoPaused) stringResource(R.string.bc_resume) else stringResource(R.string.bc_pause), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = onLap,
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text(stringResource(R.string.bc_lap), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                    Button(
                        onClick = onStopRequest,
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.danger, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) { Text(stringResource(R.string.bc_stop), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun TypeChip(
    label: String,
    type: String,
    selected: String,
    palette: BikeUiPalette,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    FilterChip(
        selected = selected == type,
        onClick = { onSelect(type) },
        modifier = modifier.height(30.dp),
        label = { Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = palette.mutedBg,
            selectedContainerColor = palette.accent.copy(alpha = 0.16f),
            selectedLabelColor = palette.accent,
            labelColor = palette.textSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = (selected == type),
            selectedBorderColor = palette.accent.copy(alpha = 0.6f),
            borderColor = palette.border
        )
    )
}

@Composable
private fun LiveMapCard(
    trackPoints: List<TrackLatLng>,
    route: LoadedRoute?,
    metrics: WorkoutMetrics,
    importingRoute: Boolean,
    showControls: Boolean,
    tileSourceKey: String,
    useNetwork: Boolean,
    palette: BikeUiPalette,
    onOpenRoutes: () -> Unit,
    onClearRoute: () -> Unit,
    onOpenOfflineMaps: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        AppMapConfig.ensureConfigured(context)
        MapView(context).apply {
            setTileSource(MapTileSources.source(tileSourceKey))
            setMultiTouchControls(true)
            setUseDataConnection(useNetwork)
            isTilesScaledToDpi = true
            controller.setZoom(15.0)
            setBackgroundColor(android.graphics.Color.parseColor(if (palette.isDark) "#1B2027" else "#DDE3E8"))
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        }
    }
    val routePolyline = remember {
        Polyline().apply {
            outlinePaint.strokeWidth = 10f
            outlinePaint.color = android.graphics.Color.parseColor("#7E57C2")
            outlinePaint.isAntiAlias = true
            outlinePaint.alpha = 170
        }
    }
    val trackPolyline = remember {
        Polyline().apply {
            outlinePaint.strokeWidth = 12f
            outlinePaint.isAntiAlias = true
        }
    }
    val positionMarker = remember {
        org.osmdroid.views.overlay.Marker(mapView).apply {
            setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
            isFlat = true
        }
    }
    val routeArrowMarkers = remember { mutableListOf<org.osmdroid.views.overlay.Marker>() }

    LaunchedEffect(palette.accent) {
        trackPolyline.outlinePaint.color = palette.accent.toArgb()
        mapView.invalidate()
    }

    LaunchedEffect(tileSourceKey, useNetwork) {
        try {
            AppMapConfig.ensureConfigured(context)
            mapView.setTileSource(MapTileSources.source(tileSourceKey))
            mapView.setUseDataConnection(useNetwork)
            mapView.invalidate()
        } catch (_: Exception) { }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { mapView.onResume() }
            override fun onPause(owner: LifecycleOwner) { mapView.onPause() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
        }
    }

    LaunchedEffect(route?.name, route?.points?.size, route?.distanceMeters) {
        try {
            routeArrowMarkers.forEach { mapView.overlays.remove(it) }
            routeArrowMarkers.clear()
            if (route == null || route.points.isEmpty()) {
                if (mapView.overlays.contains(routePolyline)) mapView.overlays.remove(routePolyline)
            } else {
                val pts = route.points
                routePolyline.setPoints(pts.map { GeoPoint(it.latitude, it.longitude) })
                if (!mapView.overlays.contains(routePolyline)) mapView.overlays.add(0, routePolyline)
                val cum = if (route.cumulativeDistancesMeters.size == pts.size) route.cumulativeDistancesMeters else com.example.bike_.domain.util.RouteNavigation.cumulativeDistances(pts)
                val total = cum.lastOrNull() ?: 0.0
                var nextArrowAt = 250.0
                for (i in 1 until pts.size) {
                    val d = cum.getOrElse(i) { 0.0 }
                    if (d >= nextArrowAt && d < total - 80.0) {
                        val a = pts[i - 1]; val b = pts[i]
                        val bearing = com.example.bike_.domain.util.RouteNavigation.bearingDeg(a.latitude, a.longitude, b.latitude, b.longitude)
                        val m = org.osmdroid.views.overlay.Marker(mapView).apply {
                            position = GeoPoint(b.latitude, b.longitude)
                            setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                            isFlat = true
                            rotation = -bearing
                            icon = directionMarkerDrawable(context, android.graphics.Color.parseColor("#7E57C2")).apply { alpha = 190 }
                            setOnMarkerClickListener { _, _ -> true }
                        }
                        routeArrowMarkers.add(m)
                        mapView.overlays.add(m)
                        nextArrowAt += 350.0
                    }
                }
                val bb = BoundingBox.fromGeoPointsSafe(routePolyline.actualPoints)
                if (bb != null && trackPoints.isEmpty()) {
                    mapView.post { try { mapView.zoomToBoundingBox(bb, true, 48) } catch (_: Exception) {} }
                }
            }
            mapView.invalidate()
        } catch (_: Exception) { }
    }

    val headingDeg = remember(trackPoints) {
        if (trackPoints.size < 2) null
        else {
            val a = trackPoints[trackPoints.lastIndex - 1]
            val b = trackPoints.last()
            com.example.bike_.domain.util.RouteNavigation.bearingDeg(a.latitude, a.longitude, b.latitude, b.longitude)
        }
    }

    LaunchedEffect(trackPoints.size, trackPoints.lastOrNull()?.latitude, trackPoints.lastOrNull()?.longitude, headingDeg, metrics.isTracking) {
        try {
            if (trackPoints.isEmpty()) {
                if (mapView.overlays.contains(trackPolyline)) mapView.overlays.remove(trackPolyline)
                if (mapView.overlays.contains(positionMarker)) mapView.overlays.remove(positionMarker)
            } else {
                trackPolyline.setPoints(trackPoints.map { GeoPoint(it.latitude, it.longitude) })
                if (!mapView.overlays.contains(trackPolyline)) mapView.overlays.add(trackPolyline)
                val last = trackPoints.last()
                val gp = GeoPoint(last.latitude, last.longitude)
                positionMarker.position = gp
                positionMarker.rotation = -(headingDeg ?: 0f)
                positionMarker.icon = directionMarkerDrawable(context, palette.accent.toArgb())
                if (!mapView.overlays.contains(positionMarker)) mapView.overlays.add(positionMarker)
                if (metrics.isTracking && headingDeg != null) mapView.mapOrientation = -headingDeg
                if (trackPoints.size <= 2 && route == null) mapView.controller.setCenter(gp) else mapView.controller.animateTo(gp)
            }
            mapView.invalidate()
        } catch (_: Exception) { }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, palette.border, RoundedCornerShape(16.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

                if (metrics.isOffRoute && metrics.offRouteMeters != null) {
                    val dir = metrics.routeBearingToReturnDeg?.let { bearingToCardinal(it) } ?: ""
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                            .background(Color(0xDDB3261E), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.bc_off_route_banner, metrics.offRouteMeters.toInt()), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (dir.isNotBlank()) {
                            Text(stringResource(R.string.bc_return_to_route, metrics.offRouteMeters.toInt(), dir), color = Color.White.copy(alpha = 0.95f), fontSize = 11.sp)
                        }
                    }
                }

                if (trackPoints.isEmpty() && route == null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(palette.cardBg.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (metrics.isTracking) stringResource(R.string.bc_waiting_gps) else stringResource(R.string.bc_map_placeholder),
                            color = palette.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                if (importingRoute) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .background(palette.cardBg.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.bc_importing_route), color = palette.textSecondary, fontSize = 12.sp)
                    }
                }
            }

            if (showControls) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onOpenRoutes,
                            modifier = Modifier.weight(1f).heightIn(min = 34.dp),
                            enabled = !importingRoute,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.Route, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(stringResource(R.string.bc_routes), maxLines = 1, fontSize = 11.sp, overflow = TextOverflow.Ellipsis, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = onOpenOfflineMaps,
                            modifier = Modifier.weight(1f).heightIn(min = 34.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(stringResource(R.string.bc_offline_maps), maxLines = 1, fontSize = 11.sp, overflow = TextOverflow.Ellipsis, softWrap = false)
                        }
                    }
                    if (route != null) {
                        RouteElevationMiniProfile(route = route, progressMeters = metrics.routeProgressDistanceMeters, remainingMeters = metrics.routeRemainingDistanceMeters, remainingGainMeters = metrics.routeRemainingElevationGainMeters, palette = palette)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(route.name ?: stringResource(R.string.bc_route), color = palette.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(String.format(Locale.US, "%.1f km • +%.0f m", route.distanceMeters / 1000.0, route.elevationGainMeters), color = palette.textSecondary, fontSize = 12.sp)
                            }
                            TextButton(onClick = onClearRoute) {
                                Icon(Icons.Filled.Close, contentDescription = null, tint = palette.danger, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.bc_remove_route), color = palette.danger)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteElevationMiniProfile(
    route: LoadedRoute,
    progressMeters: Double?,
    remainingMeters: Double?,
    remainingGainMeters: Double?,
    palette: BikeUiPalette
) {
    val points = route.points
    if (points.isEmpty()) return
    val cum = if (route.cumulativeDistancesMeters.size == points.size) route.cumulativeDistancesMeters else com.example.bike_.domain.util.RouteNavigation.cumulativeDistances(points)
    val total = route.distanceMeters.takeIf { it > 0.0 } ?: (cum.lastOrNull() ?: 0.0)
    val progress = (progressMeters ?: 0.0).coerceIn(0.0, total.coerceAtLeast(0.0))
    val remDist = (remainingMeters ?: (total - progress).coerceAtLeast(0.0)) / 1000.0
    val remGain = (remainingGainMeters ?: 0.0).coerceAtLeast(0.0)
    Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(10.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.bc_route_elevation_profile), color = palette.textSecondary, fontSize = 11.sp)
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(52.dp)) {
            val elev = points.map { it.elevationMeters }
            val valid = elev.filterNotNull()
            val minE = (valid.minOrNull() ?: 0.0)
            val maxE = (valid.maxOrNull() ?: (minE + 1.0))
            val span = (maxE - minE).coerceAtLeast(1.0)
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { i, p ->
                val x = if (total > 0.0) ((cum.getOrElse(i) { 0.0 } / total).toFloat() * size.width) else (i.toFloat() / (points.lastIndex.coerceAtLeast(1)) * size.width)
                val e = p.elevationMeters ?: minE
                val y = size.height - (((e - minE) / span).toFloat() * size.height)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = Color(0xFF7E57C2), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            val fill = androidx.compose.ui.graphics.Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, color = Color(0x337E57C2))
            val px = if (total > 0.0) ((progress / total).toFloat() * size.width) else 0f
            val curIdx = cum.indexOfFirst { it >= progress }.let { if (it < 0) points.lastIndex else it }.coerceIn(0, points.lastIndex)
            val curEle = points[curIdx].elevationMeters ?: (valid.minOrNull() ?: 0.0)
            val py = size.height - (((curEle - minE) / span).toFloat() * size.height)
            drawLine(Color.White, androidx.compose.ui.geometry.Offset(px, 0f), androidx.compose.ui.geometry.Offset(px, size.height), strokeWidth = 2f)
            drawCircle(Color(0xFFFF7043), radius = 5f, center = androidx.compose.ui.geometry.Offset(px, py.coerceIn(0f, size.height)))
        }
        Text(String.format(Locale.US, stringResource(R.string.bc_route_remaining), String.format(Locale.US, "%.1f", remDist), remGain.toInt()), color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun directionMarkerDrawable(context: Context, color: Int): android.graphics.drawable.Drawable {
    val size = (28 * context.resources.displayMetrics.density).toInt().coerceAtLeast(24)
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL; this.color = color }
    val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; this.color = android.graphics.Color.WHITE; strokeWidth = size * 0.08f }
    val path = android.graphics.Path().apply {
        moveTo(size * 0.5f, size * 0.08f)
        lineTo(size * 0.82f, size * 0.88f)
        lineTo(size * 0.5f, size * 0.72f)
        lineTo(size * 0.18f, size * 0.88f)
        close()
    }
    c.drawPath(path, p)
    c.drawPath(path, stroke)
    return android.graphics.drawable.BitmapDrawable(context.resources, bmp)
}

private fun bearingToCardinal(bearing: Float): String {
    val dirs = listOf("N","NE","E","SE","S","SW","W","NW")
    val idx = (((bearing % 360f) + 360f) % 360f / 45f).toInt() % 8
    return dirs[idx]
}

private fun shouldPromptBatteryOptimization(context: Context, app: BikeApplication): Boolean {
    if (app.cyclingSettings.isBatteryOptimizationPromptShown()) return false
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) { }
    }
}

/** Показатели зависят от выбранного вида спорта: велосипедные не смешиваются с беговыми/ходьбой. */
private fun dashboardFieldsForType(fields: List<String>, isFootWorkout: Boolean): List<String> =
    fields.filter { key ->
        when (key) {
            // Велосипедные метрики
            "cadence", "power" -> !isFootWorkout
            // Метрики шагов — только для бега/ходьбы; темп показываем во всех режимах
            "steps", "step_cadence" -> isFootWorkout
            else -> true
        }
    }

@Composable
private fun workoutTypeLabel(type: String): String = when (type.uppercase(Locale.US)) {
    WorkoutTrackingService.WORKOUT_TYPE_RUNNING -> stringResource(R.string.bc_running)
    WorkoutTrackingService.WORKOUT_TYPE_WALKING -> stringResource(R.string.bc_walking)
    else -> stringResource(R.string.bc_cycling)
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

private fun formatPace(paceMinPerKm: Float?): String {
    if (paceMinPerKm == null || !paceMinPerKm.isFinite() || paceMinPerKm <= 0f || paceMinPerKm > 99f) return "--:--"
    val totalSec = (paceMinPerKm * 60f).toInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

@Composable
private fun bikeUiThemeLabel(key: String): String = when (key) {
    CyclingSettings.UI_THEME_SOFT -> stringResource(R.string.theme_soft)
    CyclingSettings.UI_THEME_WARM -> stringResource(R.string.theme_warm)
    CyclingSettings.UI_THEME_COOL -> stringResource(R.string.theme_cool)
    CyclingSettings.UI_THEME_DARK -> stringResource(R.string.theme_dark)
    else -> key
}

@Composable
private fun bikeUiAccentLabel(key: String): String = when (key) {
    CyclingSettings.UI_ACCENT_ORANGE -> stringResource(R.string.accent_orange)
    CyclingSettings.UI_ACCENT_BLUE -> stringResource(R.string.accent_blue)
    CyclingSettings.UI_ACCENT_GREEN -> stringResource(R.string.accent_green)
    CyclingSettings.UI_ACCENT_TEAL -> stringResource(R.string.accent_teal)
    CyclingSettings.UI_ACCENT_PURPLE -> stringResource(R.string.accent_purple)
    else -> key
}

@Composable
private fun bikeDashboardFieldLabel(key: String): String = when (key) {
    "distance" -> stringResource(R.string.metric_distance)
    "time" -> stringResource(R.string.metric_time)
    "moving_time" -> stringResource(R.string.metric_moving_time)
    "avg_speed" -> stringResource(R.string.metric_avg_speed)
    "max_speed" -> stringResource(R.string.metric_max_speed)
    "heart_rate" -> stringResource(R.string.metric_heart_rate)
    "cadence" -> stringResource(R.string.metric_cadence)
    "power" -> stringResource(R.string.metric_power)
    "elevation" -> stringResource(R.string.metric_elevation)
    "lap" -> stringResource(R.string.metric_lap)
    "steps" -> stringResource(R.string.metric_steps)
    "step_cadence" -> stringResource(R.string.metric_step_cadence)
    "pace" -> stringResource(R.string.metric_pace)
    else -> key
}
