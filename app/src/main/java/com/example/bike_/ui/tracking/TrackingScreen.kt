package com.example.bike_.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bike_.data.export.ExportFormat
import com.example.bike_.domain.model.WorkoutMetrics
import com.example.bike_.service.WorkoutTrackingService
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.example.bike_.R

@Composable
fun TrackingScreen(
    modifier: Modifier = Modifier,
    viewModel: TrackingViewModel = viewModel()
) {
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val wheelCircumferenceMm by viewModel.wheelCircumferenceMm.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is TrackingViewModel.ExportUiState.Ready -> {
                try {
                    context.startActivity(state.shareIntent)
                } catch (_: Exception) {
                }
                viewModel.consumeExportState()
            }
            is TrackingViewModel.ExportUiState.Error -> {
                android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_LONG).show()
                viewModel.consumeExportState()
            }
            else -> Unit
        }
    }

    TrackingContent(
        metrics = metrics,
        wheelCircumferenceMm = wheelCircumferenceMm,
        exportState = exportState,
        onWheelCircumferenceChange = viewModel::setWheelCircumferenceMm,
        onStart = viewModel::startWorkout,
        onStop = viewModel::stopWorkout,
        onPause = viewModel::pauseWorkout,
        onResume = viewModel::resumeWorkout,
        onExportLast = viewModel::exportLastFinishedWorkout,
        modifier = modifier
    )
}

@Composable
private fun TrackingContent(
    metrics: WorkoutMetrics,
    wheelCircumferenceMm: Float,
    exportState: TrackingViewModel.ExportUiState,
    onWheelCircumferenceChange: (Float) -> Unit,
    onStart: (String) -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExportLast: (ExportFormat) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedWorkoutType by rememberSaveable { mutableStateOf(WorkoutTrackingService.WORKOUT_TYPE_CYCLING) }
    var selectedExportFormat by rememberSaveable { mutableStateOf(ExportFormat.GPX.name) }
    val statusText = when {
        !metrics.isTracking -> stringResource(R.string.tracking_status_stopped)
        metrics.isAutoPaused -> stringResource(R.string.tracking_status_auto_paused)
        else -> stringResource(R.string.tracking_status_recording)
    }
    val exportFormat = runCatching { ExportFormat.valueOf(selectedExportFormat) }.getOrDefault(ExportFormat.GPX)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.tracking_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.tracking_status, statusText),
            style = MaterialTheme.typography.titleMedium,
            color = if (metrics.isTracking && !metrics.isAutoPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )

        MetricsGrid(metrics = metrics)

        WheelCircumferenceCard(
            wheelCircumferenceMm = wheelCircumferenceMm,
            onApply = onWheelCircumferenceChange
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (!metrics.isTracking) {
            Text(
                text = stringResource(R.string.tracking_workout_type),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WorkoutTypeChip(stringResource(R.string.workout_type_cycling), WorkoutTrackingService.WORKOUT_TYPE_CYCLING, selectedWorkoutType) { selectedWorkoutType = it }
                WorkoutTypeChip(stringResource(R.string.workout_type_running), WorkoutTrackingService.WORKOUT_TYPE_RUNNING, selectedWorkoutType) { selectedWorkoutType = it }
                WorkoutTypeChip(stringResource(R.string.workout_type_walking), WorkoutTrackingService.WORKOUT_TYPE_WALKING, selectedWorkoutType) { selectedWorkoutType = it }
            }
            Button(
                onClick = { onStart(selectedWorkoutType) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.tracking_start))
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tracking_export_last),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExportFormat.values().forEach { fmt ->
                    FilterChip(
                        selected = exportFormat == fmt,
                        onClick = { selectedExportFormat = fmt.name },
                        label = { Text(fmt.label) }
                    )
                }
            }
            OutlinedButton(
                onClick = { onExportLast(exportFormat) },
                enabled = exportState !is TrackingViewModel.ExportUiState.InProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (exportState is TrackingViewModel.ExportUiState.InProgress) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.tracking_exporting_format, exportFormat.label))
                } else {
                    Text(stringResource(R.string.tracking_export_share_format, exportFormat.label))
                }
            }
            Text(
                text = stringResource(R.string.tracking_export_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { if (metrics.isAutoPaused) onResume() else onPause() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (metrics.isAutoPaused) stringResource(R.string.tracking_resume) else stringResource(R.string.tracking_pause))
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.tracking_stop))
                }
            }
            Text(
                text = stringResource(R.string.tracking_auto_pause_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorkoutTypeChip(
    label: String,
    type: String,
    selectedType: String,
    onSelect: (String) -> Unit
) {
    FilterChip(
        selected = selectedType == type,
        onClick = { onSelect(type) },
        label = { Text(label) }
    )
}

@Composable
private fun MetricsGrid(metrics: WorkoutMetrics) {
    val cards = listOf(
        stringResource(R.string.tracking_metric_moving_time) to formatDuration(metrics.movingTimeMs),
        stringResource(R.string.tracking_metric_elapsed_time) to formatDuration(metrics.elapsedTimeMs),
        stringResource(R.string.tracking_metric_distance) to String.format(Locale.US, "%.2f %s", metrics.distanceMeters / 1000.0, stringResource(R.string.unit_km)),
        stringResource(R.string.tracking_metric_speed_gps) to String.format(Locale.US, "%.1f %s", metrics.currentSpeedKmh, stringResource(R.string.unit_kmh)),
        stringResource(R.string.tracking_metric_avg_speed) to String.format(Locale.US, "%.1f %s", metrics.averageSpeedKmh, stringResource(R.string.unit_kmh)),
        stringResource(R.string.tracking_metric_max_speed) to String.format(Locale.US, "%.1f %s", metrics.maxSpeedKmh, stringResource(R.string.unit_kmh)),
        stringResource(R.string.tracking_metric_elevation) to String.format(Locale.US, "%.0f %s", metrics.elevationGainMeters, stringResource(R.string.unit_m)),
        stringResource(R.string.tracking_metric_hr) to (metrics.heartRateBpm?.let { "$it ${stringResource(R.string.unit_bpm)}" } ?: "--"),
        stringResource(R.string.tracking_metric_cadence) to (metrics.cadenceRpm?.let { String.format(Locale.US, "%.0f %s", it, stringResource(R.string.unit_rpm)) } ?: "--"),
        stringResource(R.string.tracking_metric_power) to (metrics.powerWatts?.let { "$it ${stringResource(R.string.unit_w)}" } ?: "--"),
        stringResource(R.string.tracking_metric_speed_sensor) to (metrics.sensorSpeedKmh?.let { String.format(Locale.US, "%.1f %s", it, stringResource(R.string.unit_kmh)) } ?: "--"),
        *(if (metrics.stepCadenceSpm > 0 || metrics.steps > 0) listOf(
            stringResource(R.string.metric_step_cadence) to "${metrics.stepCadenceSpm} ${stringResource(R.string.unit_spm)}",
            stringResource(R.string.tracking_metric_steps) to metrics.steps.toString()
        ) else emptyList()).toTypedArray()
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { (label, value) ->
                    MetricCard(
                        label = label,
                        value = value,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun WheelCircumferenceCard(
    wheelCircumferenceMm: Float,
    onApply: (Float) -> Unit
) {
    var text by rememberSaveable(wheelCircumferenceMm) {
        mutableStateOf(String.format(Locale.US, "%.0f", wheelCircumferenceMm))
    }
    LaunchedEffect(wheelCircumferenceMm) {
        val parsed = text.toFloatOrNull()
        if (parsed == null || kotlin.math.abs(parsed - wheelCircumferenceMm) > 0.5f) {
            text = String.format(Locale.US, "%.0f", wheelCircumferenceMm)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.tracking_sensor_settings),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.tracking_sensor_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.replace(',', '.') },
                    label = { Text(stringResource(R.string.tracking_circumference_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val value = text.toFloatOrNull()
                        if (value != null) onApply(value)
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
            Text(
                text = stringResource(R.string.tracking_current_value, wheelCircumferenceMm),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}
