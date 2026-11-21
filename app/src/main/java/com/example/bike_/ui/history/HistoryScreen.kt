package com.example.bike_.ui.history

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bike_.R
import com.example.bike_.data.db.entity.WorkoutEntity
import com.example.bike_.ui.theme.LocalBikeUiPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onWorkoutClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel()
) {
    val palette = LocalBikeUiPalette.current
    val context = LocalContext.current
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val restoreState by viewModel.restoreState.collectAsStateWithLifecycle()
    var workoutToDelete by remember { mutableStateOf<WorkoutEntity?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) pendingRestoreUri = uri
    }

    LaunchedEffect(backupState) {
        when (val s = backupState) {
            is HistoryViewModel.BackupUiState.Success -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.backup_created, s.path, s.workoutCount),
                    Toast.LENGTH_LONG
                ).show()
                viewModel.consumeBackup()
            }
            is HistoryViewModel.BackupUiState.Error -> {
                Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                viewModel.consumeBackup()
            }
            else -> Unit
        }
    }

    LaunchedEffect(restoreState) {
        when (val s = restoreState) {
            is HistoryViewModel.RestoreUiState.Success -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.backup_restored, s.workoutCount),
                    Toast.LENGTH_LONG
                ).show()
                viewModel.consumeRestore()
            }
            is HistoryViewModel.RestoreUiState.Error -> {
                Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                viewModel.consumeRestore()
            }
            else -> Unit
        }
    }

    Column(modifier = modifier.fillMaxSize().background(palette.screenBg).padding(16.dp)) {
        Text(stringResource(R.string.history_title), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Spacer(Modifier.height(8.dp))

        BackupCard(
            isBusy = backupState is HistoryViewModel.BackupUiState.InProgress ||
                restoreState is HistoryViewModel.RestoreUiState.InProgress,
            onCreate = { viewModel.createBackup() },
            onRestore = { restorePicker.launch(arrayOf("application/json", "*/*")) }
        )
        Spacer(Modifier.height(12.dp))

        if (workouts.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.history_empty_title),
                fontSize = 16.sp,
                color = palette.textPrimary
            )
            Text(
                stringResource(R.string.history_empty_subtitle),
                fontSize = 13.sp,
                color = palette.textSecondary
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(workouts, key = { it.id }) { workout ->
                    WorkoutListItem(
                        workout = workout,
                        onClick = { onWorkoutClick(workout.id) },
                        onDelete = { workoutToDelete = workout }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    val pendingDelete = workoutToDelete
    if (pendingDelete != null) {
        val df = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { workoutToDelete = null },
            containerColor = palette.cardBg,
            title = { Text(stringResource(R.string.history_delete_title), color = palette.textPrimary) },
            text = {
                Text(
                    stringResource(
                        R.string.history_delete_text,
                        workoutTypeLabel(pendingDelete.workoutType),
                        df.format(Date(pendingDelete.startTimeMs))
                    ),
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = pendingDelete.id
                    workoutToDelete = null
                    viewModel.deleteWorkout(id)
                }) { Text(stringResource(R.string.delete), color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { workoutToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    val uriToRestore = pendingRestoreUri
    if (uriToRestore != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            containerColor = palette.cardBg,
            title = { Text(stringResource(R.string.backup_restore_confirm_title), color = palette.textPrimary) },
            text = { Text(stringResource(R.string.backup_restore_confirm_text), color = palette.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    viewModel.restoreFromUri(uriToRestore)
                }) { Text(stringResource(R.string.backup_restore), color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun BackupCard(
    isBusy: Boolean,
    onCreate: () -> Unit,
    onRestore: () -> Unit
) {
    val palette = LocalBikeUiPalette.current
    Card(
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(stringResource(R.string.backup_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = palette.textPrimary)
            Text(stringResource(R.string.backup_subtitle), fontSize = 12.sp, color = palette.textSecondary)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCreate,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.backup_create), fontSize = 12.sp) }
                Button(
                    onClick = onRestore,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.backup_restore), fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun WorkoutListItem(
    workout: WorkoutEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalBikeUiPalette.current
    val df = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val dateStr = df.format(Date(workout.startTimeMs))
    val distKm = workout.distanceMeters / 1000.0
    val timeStr = formatDuration(workout.elapsedTimeMs)
    val accent = when (workout.workoutType.uppercase(Locale.US)) {
        "RUNNING" -> androidx.compose.ui.graphics.Color(0xFFEF6C00)
        "WALKING" -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
        else -> palette.accent
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(accent.copy(alpha = 0.10f), palette.cardBg)), RoundedCornerShape(16.dp))
                .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(workoutTypeLabel(workout.workoutType), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = palette.textPrimary)
                    Text(dateStr, fontSize = 12.sp, color = palette.textSecondary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_delete_cd), tint = palette.textSecondary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(Modifier.weight(1f), stringResource(R.string.history_distance), String.format(Locale.US, "%.2f", distKm), stringResource(R.string.unit_km), palette)
                StatChip(Modifier.weight(1f), stringResource(R.string.history_time), timeStr, "", palette)
                StatChip(Modifier.weight(1f), stringResource(R.string.history_avg_speed), String.format(Locale.US, "%.1f", workout.avgSpeedKmh), stringResource(R.string.unit_kmh), palette)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                workout.avgHeartRateBpm?.let { StatChip(Modifier.weight(1f), stringResource(R.string.history_avg_hr), "$it", stringResource(R.string.unit_bpm), palette) }
                workout.avgPowerWatts?.let { StatChip(Modifier.weight(1f), stringResource(R.string.history_avg_power), "$it", stringResource(R.string.unit_w), palette) }
                workout.trainingStressScore?.let { StatChip(Modifier.weight(1f), "TSS", String.format(Locale.US, "%.0f", it), "", palette) }
                    ?: StatChip(Modifier.weight(1f), stringResource(R.string.history_elevation), String.format(Locale.US, "%.0f", workout.elevationGainMeters), stringResource(R.string.unit_m), palette)
            }
        }
    }
}

@Composable
private fun StatChip(modifier: Modifier, label: String, value: String, unit: String, palette: com.example.bike_.ui.theme.BikeUiPalette) {
    Column(modifier.background(palette.screenBg.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 7.dp)) {
        Text(label, fontSize = 10.sp, color = palette.textSecondary, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, maxLines = 1)
            if (unit.isNotBlank()) Text(unit, fontSize = 9.sp, color = palette.textSecondary, modifier = Modifier.padding(bottom = 1.dp))
        }
    }
}

@Composable
private fun workoutTypeLabel(type: String): String = when (type.uppercase(Locale.US)) {
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
