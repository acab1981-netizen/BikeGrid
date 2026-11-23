package com.example.bike_.ui.history

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bike_.data.db.entity.LapEntity
import com.example.bike_.data.db.entity.TrackPointEntity
import com.example.bike_.data.db.entity.WorkoutEntity
import com.example.bike_.data.export.ExportFormat
import com.example.bike_.ui.theme.LocalBikeUiPalette
import com.example.bike_.BikeApplication
import com.example.bike_.data.map.AppMapConfig
import com.example.bike_.data.map.MapTileSources
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.res.stringResource
import com.example.bike_.R
import com.example.bike_.domain.analytics.TrainingAnalytics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    workoutId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutDetailViewModel = viewModel()
) {
    val workout by viewModel.observeWorkout(workoutId).collectAsStateWithLifecycle(initialValue = null)
    val points by viewModel.trackPoints.collectAsStateWithLifecycle()
    val laps by viewModel.laps.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(workoutId) {
        viewModel.loadTrackPoints(workoutId)
        viewModel.loadLaps(workoutId)
    }

    LaunchedEffect(exportState) {
        when (val s = exportState) {
            is WorkoutDetailViewModel.ExportState.Ready -> {
                try { context.startActivity(s.shareIntent) } catch (_: Exception) {}
                Toast.makeText(context, context.getString(R.string.detail_saved, s.path), Toast.LENGTH_LONG).show()
                viewModel.consumeExport()
            }
            is WorkoutDetailViewModel.ExportState.Error -> {
                Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                viewModel.consumeExport()
            }
            else -> Unit
        }
    }

    val palette = LocalBikeUiPalette.current
    Scaffold(
        containerColor = palette.screenBg,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title), color = palette.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = palette.textPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.cardBg,
                    titleContentColor = palette.textPrimary,
                    navigationIconContentColor = palette.textPrimary
                )
            )
        }
    ) { inner ->
        val w = workout
        if (w == null) {
            Box(Modifier.fillMaxSize().padding(inner).background(palette.screenBg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            return@Scaffold
        }
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(palette.screenBg)
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeaderCard(w)
            RouteMap(points = points)
            ZoneDistributionCard(w)
            GarminMetricHighlights(w, points)
            SummaryTable(w)
            LoadAnalyticsSection(w)
            TimeInZonesSection(w)
            LapsSection(laps = laps)
            ChartsSection(points = points)
            ExportSection(
                exporting = exportState is WorkoutDetailViewModel.ExportState.InProgress,
                onExport = { fmt -> viewModel.export(workoutId, fmt) }
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HeaderCard(w: WorkoutEntity) {
    val palette = LocalBikeUiPalette.current
    val df = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())
    val accent = when (w.workoutType.uppercase(Locale.US)) {
        "RUNNING" -> Color(0xFFEF6C00)
        "WALKING" -> Color(0xFF2E7D32)
        else -> palette.accent
    }
    Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.22f), palette.cardBg)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(typeLabel(w.workoutType), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                        Text(df.format(Date(w.startTimeMs)), style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                    }
                    Box(
                        Modifier
                            .background(accent.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.detail_title), color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroStat(Modifier.weight(1f), stringResource(R.string.detail_distance), String.format(Locale.US, "%.2f", w.distanceMeters / 1000.0), stringResource(R.string.unit_km), accent, palette)
                    HeroStat(Modifier.weight(1f), stringResource(R.string.detail_total_time), formatDuration(w.elapsedTimeMs), "", accent, palette)
                    HeroStat(Modifier.weight(1f), stringResource(R.string.detail_avg_speed), String.format(Locale.US, "%.1f", w.avgSpeedKmh), stringResource(R.string.unit_kmh), accent, palette)
                }
            }
        }
    }
}

@Composable
private fun HeroStat(modifier: Modifier, label: String, value: String, unit: String, accent: Color, palette: com.example.bike_.ui.theme.BikeUiPalette) {
    Column(modifier.background(palette.screenBg.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, color = palette.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, color = palette.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1)
            if (unit.isNotBlank()) Text(unit, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun RouteMap(points: List<TrackPointEntity>) {
    val palette = LocalBikeUiPalette.current
    Text(stringResource(R.string.detail_route), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
    if (points.isEmpty()) {
        Text(stringResource(R.string.detail_no_gps_points), color = palette.textSecondary)
        return
    }
    val context = LocalContext.current
    val app = context.applicationContext as BikeApplication
    val tileSourceKey by app.cyclingSettings.mapTileSource.collectAsStateWithLifecycle()
    val useNetwork by app.cyclingSettings.mapUseNetwork.collectAsStateWithLifecycle()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val mapView = remember {
        AppMapConfig.ensureConfigured(context)
        MapView(context).apply {
            setTileSource(MapTileSources.source(tileSourceKey))
            setMultiTouchControls(true)
            setUseDataConnection(useNetwork)
            isTilesScaledToDpi = true
            setBackgroundColor(android.graphics.Color.parseColor(if (palette.isDark) "#1B2027" else "#DDE3E8"))
        }
    }
    val polyline = remember {
        Polyline().apply {
            outlinePaint.strokeWidth = 8f
            outlinePaint.color = palette.accent.toArgb()
            outlinePaint.isAntiAlias = true
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) { mapView.onResume() }
            override fun onPause(owner: androidx.lifecycle.LifecycleOwner) { mapView.onPause() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        if (!mapView.overlays.contains(polyline)) mapView.overlays.add(polyline)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.overlays.remove(polyline)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(tileSourceKey, useNetwork) {
        try {
            AppMapConfig.ensureConfigured(context)
            mapView.setTileSource(MapTileSources.source(tileSourceKey))
            mapView.setUseDataConnection(useNetwork)
            mapView.invalidate()
        } catch (_: Exception) { }
    }

    val geoPoints = remember(points) { points.map { GeoPoint(it.latitude, it.longitude) } }
    LaunchedEffect(geoPoints) {
        polyline.setPoints(geoPoints)
        if (geoPoints.size >= 2) {
            try {
                val box = BoundingBox.fromGeoPoints(geoPoints)
                mapView.post { try { mapView.zoomToBoundingBox(box, false, 48) } catch (_: Exception) {} }
            } catch (_: Exception) {
                mapView.controller.setCenter(geoPoints.last())
                mapView.controller.setZoom(15.0)
            }
        } else if (geoPoints.size == 1) {
            mapView.controller.setCenter(geoPoints.first())
            mapView.controller.setZoom(16.0)
        }
        mapView.invalidate()
    }

    Card(modifier = Modifier.fillMaxWidth().height(260.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = palette.cardBg)) {
        Box(Modifier.fillMaxSize().border(1.dp, palette.border, RoundedCornerShape(12.dp))) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SummaryTable(w: WorkoutEntity) {
    val palette = LocalBikeUiPalette.current
    Text(stringResource(R.string.detail_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
    val stats = buildList {
        add(Triple(stringResource(R.string.detail_moving_time), formatDuration(w.movingTimeMs), ""))
        add(Triple(stringResource(R.string.detail_max_speed), String.format(Locale.US, "%.1f", w.maxSpeedKmh), stringResource(R.string.unit_kmh)))
        add(Triple(stringResource(R.string.detail_elevation_gain), String.format(Locale.US, "%.0f", w.elevationGainMeters), stringResource(R.string.unit_m)))
        w.avgHeartRateBpm?.let { add(Triple(stringResource(R.string.detail_hr_avg_max), "$it / ${w.maxHeartRateBpm ?: "-"}", stringResource(R.string.unit_bpm))) }
        w.avgCadenceRpm?.let { add(Triple(stringResource(R.string.detail_cadence_avg_max), String.format(Locale.US, "%.0f / %.0f", it, w.maxCadenceRpm ?: 0f), stringResource(R.string.unit_rpm))) }
        w.avgPowerWatts?.let { add(Triple(stringResource(R.string.detail_power_avg_max), "$it / ${w.maxPowerWatts ?: "-"}", stringResource(R.string.unit_w))) }
        w.totalSteps?.takeIf { it > 0 }?.let { add(Triple(stringResource(R.string.detail_steps), "$it", stringResource(R.string.unit_steps))) }
        w.avgStepCadenceSpm?.let { add(Triple(stringResource(R.string.detail_avg_step_cadence), "$it", stringResource(R.string.unit_spm))) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { (label, value, unit) ->
                    Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Text(label, color = palette.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(value, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                                if (unit.isNotBlank()) Text(unit, color = palette.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 1.dp))
                            }
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LoadAnalyticsSection(w: WorkoutEntity) {
    val palette = LocalBikeUiPalette.current
    val hasAny = w.normalizedPowerWatts != null || w.intensityFactor != null || w.trainingStressScore != null || w.caloriesKcal != null
    if (!hasAny) return
    Text(stringResource(R.string.detail_load_analytics), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
    val recovery = when {
        (w.trainingStressScore ?: 0f) > 300f -> stringResource(R.string.detail_recovery_heavy)
        (w.trainingStressScore ?: 0f) > 150f -> stringResource(R.string.detail_recovery_medium)
        else -> stringResource(R.string.detail_recovery_light)
    }
    val kcalLabel = when (w.caloriesSource) {
        "POWER" -> stringResource(R.string.analytics_kcal_power)
        "HR" -> stringResource(R.string.analytics_kcal_hr)
        else -> ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalyticsTile(
                modifier = Modifier.weight(1f),
                title = if (w.normalizedPowerWatts != null) stringResource(R.string.analytics_np) else stringResource(R.string.analytics_tss),
                value = if (w.normalizedPowerWatts != null) "${w.normalizedPowerWatts} ${stringResource(R.string.unit_w)}" else String.format(Locale.US, "%.0f", w.trainingStressScore ?: 0f),
                subtitle = if (w.normalizedPowerWatts != null) "NP" else recovery
            )
            AnalyticsTile(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.analytics_if),
                value = w.intensityFactor?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
                subtitle = ""
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tssTitle = if (w.caloriesSource == "HR" && w.normalizedPowerWatts == null) stringResource(R.string.analytics_hrtss) else stringResource(R.string.analytics_tss)
            AnalyticsTile(
                modifier = Modifier.weight(1f),
                title = tssTitle,
                value = w.trainingStressScore?.let { String.format(Locale.US, "%.0f", it) } ?: "--",
                subtitle = recovery
            )
            AnalyticsTile(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.analytics_kcal),
                value = w.caloriesKcal?.let { "$it kcal" } ?: "--",
                subtitle = kcalLabel
            )
        }
    }
}

@Composable
private fun AnalyticsTile(modifier: Modifier = Modifier, title: String, value: String, subtitle: String) {
    val palette = LocalBikeUiPalette.current
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp)) {
            Text(title, color = palette.textSecondary, fontSize = 12.sp)
            Text(value, color = palette.textPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) Text(subtitle, color = palette.textSecondary, fontSize = 11.sp, maxLines = 2)
        }
    }
}

@Composable
private fun TimeInZonesSection(w: WorkoutEntity) {
    val palette = LocalBikeUiPalette.current
    val hr = TrainingAnalytics.decodeZoneSeconds(w.hrZoneSeconds, 5)
    val pwr = TrainingAnalytics.decodeZoneSeconds(w.powerZoneSeconds, 7)
    val hasHr = hr.any { it > 0 }
    val hasPwr = pwr.any { it > 0 }
    if (!hasHr && !hasPwr) return

    var tab by rememberSaveable(w.id) { mutableStateOf(if (hasPwr) 0 else 1) }
    Text(stringResource(R.string.detail_time_in_zones), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
    Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    enabled = hasPwr,
                    label = { Text(stringResource(R.string.detail_power_zones_tab)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = palette.accent.copy(alpha = 0.18f), selectedLabelColor = palette.accent)
                )
                FilterChip(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    enabled = hasHr,
                    label = { Text(stringResource(R.string.detail_hr_zones_tab)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = palette.accent.copy(alpha = 0.18f), selectedLabelColor = palette.accent)
                )
            }
            if (tab == 0) {
                if (hasPwr) ZoneBars(seconds = pwr, names = powerZoneDisplayNames(), colors = TrainingAnalytics.POWER_ZONE_COLOR_ARGBS.map { androidx.compose.ui.graphics.Color(it.toInt()) })
                else Text(stringResource(R.string.detail_no_power_data), color = palette.textSecondary)
            } else {
                if (hasHr) ZoneBars(seconds = hr, names = hrZoneDisplayNames(), colors = TrainingAnalytics.HR_ZONE_COLOR_ARGBS.map { androidx.compose.ui.graphics.Color(it.toInt()) })
                else Text(stringResource(R.string.detail_no_hr_zone_data), color = palette.textSecondary)
            }
        }
    }
}

@Composable
private fun hrZoneDisplayNames(): List<String> = listOf(
    stringResource(R.string.zone_hr_z1), stringResource(R.string.zone_hr_z2), stringResource(R.string.zone_hr_z3),
    stringResource(R.string.zone_hr_z4), stringResource(R.string.zone_hr_z5)
)

@Composable
private fun powerZoneDisplayNames(): List<String> = listOf(
    stringResource(R.string.zone_pwr_z1), stringResource(R.string.zone_pwr_z2), stringResource(R.string.zone_pwr_z3),
    stringResource(R.string.zone_pwr_z4), stringResource(R.string.zone_pwr_z5), stringResource(R.string.zone_pwr_z6), stringResource(R.string.zone_pwr_z7)
)

@Composable
private fun ZoneBars(seconds: IntArray, names: List<String>, colors: List<Color>) {
    val palette = LocalBikeUiPalette.current
    val total = seconds.sum().coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in seconds.indices) {
            val sec = seconds[i].coerceAtLeast(0)
            val pct = sec * 100f / total.toFloat()
            val color = colors.getOrElse(i) { palette.accent }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Z${i + 1} ${names.getOrElse(i) { "" }}", color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("${TrainingAnalytics.formatMmSs(sec)} · ${String.format(Locale.US, "%.0f%%", pct)}", color = palette.textSecondary, fontSize = 12.sp)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).background(palette.mutedBg, RoundedCornerShape(999.dp))) {
                    Box(Modifier.fillMaxWidth((pct / 100f).coerceIn(0f, 1f)).height(10.dp).background(color, RoundedCornerShape(999.dp)))
                }
            }
        }
    }
}



@Composable
private fun ZoneDistributionCard(w: WorkoutEntity) {
    val palette = LocalBikeUiPalette.current
    val hr = TrainingAnalytics.decodeZoneSeconds(w.hrZoneSeconds, 5)
    val pwr = TrainingAnalytics.decodeZoneSeconds(w.powerZoneSeconds, 7)
    val hasHr = hr.any { it > 0 }
    val hasPwr = pwr.any { it > 0 }
    if (!hasHr && !hasPwr) return
    Text(stringResource(R.string.detail_time_in_zones), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
    Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (hasPwr) ZoneStrip(stringResource(R.string.detail_power_zones_tab), pwr, TrainingAnalytics.POWER_ZONE_COLOR_ARGBS.map { Color(it.toInt()) }, palette)
            if (hasHr) ZoneStrip(stringResource(R.string.detail_hr_zones_tab), hr, TrainingAnalytics.HR_ZONE_COLOR_ARGBS.map { Color(it.toInt()) }, palette)
        }
    }
}

@Composable
private fun ZoneStrip(title: String, seconds: IntArray, colors: List<Color>, palette: com.example.bike_.ui.theme.BikeUiPalette) {
    val total = seconds.sum().coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(TrainingAnalytics.formatMmSs(total), color = palette.textSecondary, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(999.dp)).background(palette.mutedBg)) {
            seconds.forEachIndexed { i, sec ->
                if (sec > 0) Box(Modifier.weight(sec.toFloat()).fillMaxSize().background(colors.getOrElse(i) { palette.accent }))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            seconds.forEachIndexed { i, sec ->
                if (sec > 0) {
                    val pct = sec * 100 / total
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.height(8.dp).width(8.dp).background(colors.getOrElse(i) { palette.accent }, RoundedCornerShape(2.dp)))
                        Text("Z${i+1} $pct%", color = palette.textSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GarminMetricHighlights(w: WorkoutEntity, points: List<TrackPointEntity>) {
    val palette = LocalBikeUiPalette.current
    val series = remember(points) { buildDistanceSeries(points) }
    val speed = series.mapNotNull { it.speedKmh?.let { v -> it.distanceKm to v } }
    val hr = series.mapNotNull { it.heartRateBpm?.let { v -> it.distanceKm to v.toFloat() } }
    val pwr = series.mapNotNull { it.powerWatts?.let { v -> it.distanceKm to v.toFloat() } }
    if (speed.isEmpty() && hr.isEmpty() && pwr.isEmpty()) return
    Text(stringResource(R.string.detail_charts_by_distance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (speed.isNotEmpty()) HighlightMetricCard(stringResource(R.string.chart_speed), String.format(Locale.US, "%.1f", w.avgSpeedKmh), stringResource(R.string.unit_kmh), speed, Color(0xFF2E7DD7), Icons.Filled.Speed, palette)
        if (hr.isNotEmpty()) HighlightMetricCard(stringResource(R.string.chart_hr), (w.avgHeartRateBpm ?: hr.map{it.second}.average().toInt()).toString(), stringResource(R.string.unit_bpm), hr, Color(0xFFE53935), Icons.Filled.Favorite, palette)
        if (pwr.isNotEmpty()) HighlightMetricCard(stringResource(R.string.chart_power), (w.avgPowerWatts ?: pwr.map{it.second}.average().toInt()).toString(), stringResource(R.string.unit_w), pwr, Color(0xFFF9A825), Icons.Filled.Bolt, palette)
    }
}

@Composable
private fun HighlightMetricCard(title: String, value: String, unit: String, data: List<Pair<Float, Float>>, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, palette: com.example.bike_.ui.theme.BikeUiPalette) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().border(1.dp, color.copy(alpha=0.24f), RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.background(color.copy(alpha=0.14f), RoundedCornerShape(12.dp)).padding(8.dp)) { Icon(icon, null, tint=color) }
            Column(Modifier.width(92.dp)) {
                Text(title, color = palette.textSecondary, fontSize = 11.sp, maxLines = 1)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(value, color = palette.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text(unit, color = palette.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom=3.dp))
                }
            }
            Sparkline(data = data, color = color, modifier = Modifier.weight(1f).height(52.dp))
        }
    }
}

@Composable
private fun Sparkline(data: List<Pair<Float, Float>>, color: Color, modifier: Modifier = Modifier) {
    val ys = data.map { it.second }
    val minY = ys.minOrNull() ?: 0f
    val maxY = ys.maxOrNull() ?: 1f
    val range = (maxY - minY).takeIf { it > 1e-6f } ?: 1f
    Canvas(modifier) {
        if (data.size < 2) return@Canvas
        val path = Path()
        data.forEachIndexed { i, p ->
            val x = size.width * i / (data.size - 1).toFloat()
            val y = size.height - ((p.second - minY) / range) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color.copy(alpha=0.22f), Color.Transparent)))
        drawPath(path, color = color, style = Stroke(width = 3f))
    }
}
@Composable
private fun LapsSection(laps: List<LapEntity>) {
    if (laps.isEmpty()) return
    val palette = LocalBikeUiPalette.current
    Text(stringResource(R.string.detail_laps), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
    Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LapHeaderCell(stringResource(R.string.detail_lap_no), 36.dp)
                LapHeaderCell(stringResource(R.string.detail_lap_time), 72.dp)
                LapHeaderCell(stringResource(R.string.detail_lap_distance), 72.dp)
                LapHeaderCell(stringResource(R.string.detail_lap_avg_speed), 82.dp)
                LapHeaderCell(stringResource(R.string.detail_lap_avg_hr), 72.dp)
                LapHeaderCell(stringResource(R.string.detail_lap_avg_power), 82.dp)
            }
            laps.forEach { lap ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LapCell(lap.lapNumber.toString(), 36.dp, palette.textPrimary, FontWeight.SemiBold)
                    LapCell(formatDuration(lap.durationSec * 1000L), 72.dp)
                    LapCell(String.format(Locale.US, "%.2f %s", lap.distanceMeters / 1000.0, stringResource(R.string.unit_km)), 72.dp)
                    LapCell(String.format(Locale.US, "%.1f %s", lap.avgSpeedKmh, stringResource(R.string.unit_kmh)), 82.dp)
                    LapCell(lap.avgHeartRateBpm?.let { "$it ${stringResource(R.string.unit_bpm)}" } ?: "--", 72.dp)
                    LapCell(lap.avgPowerWatts?.let { "$it ${stringResource(R.string.unit_w)}" } ?: "--", 82.dp)
                }
            }
        }
    }
}

@Composable
private fun LapHeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    val palette = LocalBikeUiPalette.current
    Text(text, modifier = Modifier.widthIn(min = width), color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun LapCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    color: Color = LocalBikeUiPalette.current.textPrimary,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Text(text, modifier = Modifier.widthIn(min = width), color = color, fontSize = 13.sp, fontWeight = fontWeight)
}

@Composable
private fun ChartsSection(points: List<TrackPointEntity>) {
    val palette = LocalBikeUiPalette.current
    val series = remember(points) { buildDistanceSeries(points) }
    if (series.isEmpty()) {
        Text(stringResource(R.string.detail_no_chart_data), color = palette.textSecondary)
        return
    }
    Text(stringResource(R.string.detail_charts_by_distance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
    val speedData = series.mapNotNull { s -> s.speedKmh?.let { s.distanceKm to it } }
    val hrData = series.mapNotNull { s -> s.heartRateBpm?.let { s.distanceKm to it.toFloat() } }
    val altData = series.mapNotNull { s -> s.altitudeM?.let { s.distanceKm to it.toFloat() } }
    val pwrData = series.mapNotNull { s -> s.powerWatts?.let { s.distanceKm to it.toFloat() } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (speedData.isNotEmpty()) LineChartCard(stringResource(R.string.chart_speed), speedData, Color(0xFF2E7DD7), Icons.Filled.Speed, stringResource(R.string.unit_kmh))
        if (hrData.isNotEmpty()) LineChartCard(stringResource(R.string.chart_hr), hrData, Color(0xFFE53935), Icons.Filled.Favorite, stringResource(R.string.unit_bpm))
        if (pwrData.isNotEmpty()) LineChartCard(stringResource(R.string.chart_power), pwrData, Color(0xFFF9A825), Icons.Filled.Bolt, stringResource(R.string.unit_w))
        if (altData.isNotEmpty()) LineChartCard(stringResource(R.string.chart_altitude), altData, Color(0xFF43A047), Icons.Filled.Landscape, stringResource(R.string.unit_m))
        if (speedData.isEmpty() && hrData.isEmpty() && altData.isEmpty() && pwrData.isEmpty()) {
            Text(stringResource(R.string.detail_no_series_data), color = palette.textSecondary)
        }
    }
}

private data class DistSample(val distanceKm: Float, val speedKmh: Float?, val heartRateBpm: Int?, val altitudeM: Double?, val powerWatts: Int?)

private fun buildDistanceSeries(points: List<TrackPointEntity>): List<DistSample> {
    if (points.isEmpty()) return emptyList()
    val maxPoints = 300
    val step = max(1, points.size / maxPoints)
    val out = ArrayList<DistSample>(points.size / step + 1)
    var cum = 0.0
    var prev: TrackPointEntity? = null
    for ((i, p) in points.withIndex()) {
        if (prev != null) cum += haversineMeters(prev.latitude, prev.longitude, p.latitude, p.longitude)
        prev = p
        if (i % step == 0 || i == points.lastIndex) {
            out.add(DistSample((cum / 1000.0).toFloat(), p.speedKmh, p.heartRateBpm, p.altitudeMeters, p.powerWatts))
        }
    }
    return out
}

@Composable
private fun LineChartCard(title: String, data: List<Pair<Float, Float>>, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, unit: String) {
    val palette = LocalBikeUiPalette.current
    if (data.size < 2) return
    val xs = data.map { it.first }
    val ys = data.map { it.second }
    val minX = xs.minOrNull() ?: 0f
    val maxX = xs.maxOrNull() ?: 1f
    val minY = ys.minOrNull() ?: 0f
    val maxY = ys.maxOrNull() ?: 1f
    val avgY = ys.average().toFloat()
    val yPad = ((maxY - minY).takeIf { it > 0f } ?: 1f) * 0.12f
    val yMin = (minY - yPad).coerceAtLeast(0f)
    val yMax = maxY + yPad

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(16.dp)).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(10.dp)).padding(6.dp)) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.height(18.dp).width(18.dp))
                    }
                    Column {
                        Text(title, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Text(String.format(Locale.US, "%.1f km", maxX), fontSize = 11.sp, color = palette.textSecondary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniMetric("avg", String.format(Locale.US, "%.0f", avgY), unit, color, palette)
                    MiniMetric("max", String.format(Locale.US, "%.0f", maxY - yPad), unit, color, palette)
                }
            }
            Spacer(Modifier.height(8.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp).background(palette.screenBg.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(horizontal = 6.dp, vertical = 8.dp)) {
                val w = size.width
                val h = size.height
                val xRange = (maxX - minX).takeIf { it > 1e-6f } ?: 1f
                val yRange = (yMax - yMin).takeIf { it > 1e-6f } ?: 1f
                fun px(p: Pair<Float, Float>): Offset {
                    val x = ((p.first - minX) / xRange) * w
                    val y = h - ((p.second - yMin) / yRange) * h
                    return Offset(x, y.coerceIn(0f, h))
                }
                for (i in 0..3) {
                    val gy = h * i / 3f
                    drawLine(palette.border.copy(alpha = 0.45f), Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                }
                val path = Path().apply {
                    val first = px(data.first())
                    moveTo(first.x, first.y)
                    for (i in 1 until data.size) lineTo(px(data[i]).x, px(data[i]).y)
                }
                val fill = Path().apply {
                    val first = px(data.first())
                    moveTo(first.x, h)
                    lineTo(first.x, first.y)
                    for (i in 1 until data.size) { val pt = px(data[i]); lineTo(pt.x, pt.y) }
                    val last = px(data.last())
                    lineTo(last.x, h)
                    close()
                }
                drawPath(fill, brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f))))
                drawPath(path, color = color, style = Stroke(width = 4f))
            }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, unit: String, color: Color, palette: com.example.bike_.ui.theme.BikeUiPalette) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label.uppercase(Locale.US), fontSize = 9.sp, color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
            Text(unit, fontSize = 9.sp, color = palette.textSecondary, modifier = Modifier.padding(bottom = 1.dp))
        }
    }
}

@Composable
private fun ExportSection(
    exporting: Boolean,
    onExport: (ExportFormat) -> Unit
) {
    val palette = LocalBikeUiPalette.current
    Text(stringResource(R.string.detail_export_share), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onExport(ExportFormat.FIT) },
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.detail_download_fit))
                }
                OutlinedButton(
                    onClick = { onExport(ExportFormat.GPX) },
                    enabled = !exporting,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = palette.accent)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.detail_download_gpx), color = palette.accent)
                }
            }
            if (exporting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp, color = palette.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.detail_exporting), color = palette.textSecondary, fontSize = 12.sp)
                }
            }
            Text(
                stringResource(R.string.detail_export_hint),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun typeLabel(type: String): String = when (type.uppercase(Locale.US)) {
    "RUNNING" -> stringResource(R.string.workout_type_running)
    "WALKING" -> stringResource(R.string.workout_type_walking)
    else -> stringResource(R.string.workout_type_cycling)
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
    val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    return r * c
}
