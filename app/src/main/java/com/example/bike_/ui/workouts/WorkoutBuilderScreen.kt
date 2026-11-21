package com.example.bike_.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bike_.R
import com.example.bike_.domain.workout.StepType
import com.example.bike_.domain.workout.StructuredWorkout
import com.example.bike_.domain.workout.TargetType
import com.example.bike_.domain.workout.WorkoutStep
import com.example.bike_.ui.theme.LocalBikeUiPalette
import com.example.bike_.ui.tracking.TrackingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutBuilderScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TrackingViewModel = viewModel()
) {
    val palette = LocalBikeUiPalette.current
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val steps = remember {
        mutableStateListOf(
            WorkoutStep(StepType.WARMUP, 600, TargetType.POWER_FTP, 50, 60),
            WorkoutStep(StepType.WORK, 300, TargetType.POWER_FTP, 90, 100),
            WorkoutStep(StepType.RECOVERY, 180, TargetType.POWER_FTP, 50, 60),
            WorkoutStep(StepType.COOLDOWN, 300, TargetType.POWER_FTP, 45, 55)
        )
    }

    Scaffold(
        containerColor = palette.screenBg,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sw_builder_title), color = palette.textPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = palette.textPrimary) } },
                actions = {
                    IconButton(onClick = { steps.add(WorkoutStep(StepType.WORK, 300, TargetType.POWER_FTP, 88, 94)) }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.sw_add_step), tint = palette.accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.cardBg)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.sw_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item {
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(stringResource(R.string.sw_description)) }, modifier = Modifier.fillMaxWidth())
            }
            itemsIndexed(steps) { index, step ->
                StepEditorCard(
                    step = step,
                    onChange = { steps[index] = it },
                    onDelete = { if (steps.size > 1) steps.removeAt(index) }
                )
            }
            item {
                Button(
                    onClick = {
                        val workout = StructuredWorkout(
                            title = title.ifBlank { "Custom Workout" },
                            description = desc,
                            source = "USER",
                            steps = steps.toList()
                        )
                        viewModel.saveStructuredWorkout(workout)
                        onSaved()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.sw_save_workout)) }
            }
        }
    }
}

@Composable
private fun StepEditorCard(step: WorkoutStep, onChange: (WorkoutStep) -> Unit, onDelete: () -> Unit) {
    val palette = LocalBikeUiPalette.current
    Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(StepType.WARMUP, StepType.WORK, StepType.RECOVERY, StepType.COOLDOWN).forEach { t ->
                    FilterChip(selected = step.type == t, onClick = { onChange(step.copy(type = t)) }, label = { Text(stepTypeLabel(t)) })
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.sw_delete), tint = palette.danger) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NumField(stringResource(R.string.sw_duration_min), (step.durationSec / 60).coerceAtLeast(1), Modifier.weight(1f)) { v -> onChange(step.copy(durationSec = (v.coerceAtLeast(1)) * 60)) }
                NumField(stringResource(R.string.sw_repeat), step.repeat, Modifier.weight(1f)) { v -> onChange(step.copy(repeat = v.coerceAtLeast(1))) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NumField(stringResource(R.string.sw_target_min_pct), step.targetMin, Modifier.weight(1f)) { v -> onChange(step.copy(targetMin = v.coerceIn(30, 200))) }
                NumField(stringResource(R.string.sw_target_max_pct), step.targetMax, Modifier.weight(1f)) { v -> onChange(step.copy(targetMax = v.coerceIn(step.targetMin, 250))) }
            }
        }
    }
}

@Composable
private fun NumField(label: String, value: Int, modifier: Modifier = Modifier, onValue: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { s -> val d = s.filter { it.isDigit() }; if (d.isBlank()) onValue(0) else d.toIntOrNull()?.let(onValue) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun stepTypeLabel(type: StepType): String = when (type) {
    StepType.WARMUP -> stringResource(R.string.sw_step_warmup)
    StepType.WORK -> stringResource(R.string.sw_step_work)
    StepType.RECOVERY -> stringResource(R.string.sw_step_recovery)
    StepType.COOLDOWN -> stringResource(R.string.sw_step_cooldown)
}
