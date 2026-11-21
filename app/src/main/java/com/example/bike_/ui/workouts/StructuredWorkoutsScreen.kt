package com.example.bike_.ui.workouts

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bike_.R
import com.example.bike_.domain.workout.StructuredWorkout
import com.example.bike_.ui.theme.LocalBikeUiPalette
import com.example.bike_.ui.tracking.TrackingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructuredWorkoutsScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onStartWorkout: () -> Unit,
    viewModel: TrackingViewModel = viewModel()
) {
    val workouts by viewModel.structuredWorkouts.collectAsStateWithLifecycle()
    val palette = LocalBikeUiPalette.current
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val name = uri.lastPathSegment
            val imported = context.contentResolver.openInputStream(uri)?.use { input ->
                viewModel.importZwo(input, name)
            }
            if (imported != null) {
                Toast.makeText(context, context.getString(R.string.sw_import_ok), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.sw_import_failed), Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.sw_import_failed), Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = palette.screenBg,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sw_library), color = palette.textPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = palette.textPrimary) } },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/octet-stream", "text/xml", "application/xml", "*/*")) }) {
                        Icon(Icons.Filled.FileOpen, contentDescription = stringResource(R.string.sw_import_zwo), tint = palette.accent)
                    }
                    IconButton(onClick = onCreate) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.sw_create), tint = palette.accent) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.cardBg)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(workouts, key = { it.id }) { workout ->
                WorkoutCard(
                    workout = workout,
                    onStart = {
                        viewModel.startStructuredWorkout(workout.id)
                        onStartWorkout()
                    },
                    onDelete = { viewModel.deleteStructuredWorkout(workout.id) }
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun WorkoutCard(workout: StructuredWorkout, onStart: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalBikeUiPalette.current
    val mins = (workout.totalDurationSec / 60).coerceAtLeast(1)
    Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(workout.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            if (workout.description.isNotBlank()) Text(workout.description, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
            Text("${stringResource(R.string.sw_minutes_short, mins)} ? ${stringResource(R.string.sw_est_tss, workout.estimatedTss ?: 0)}", color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onStart) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 2.dp))
                    Text(stringResource(R.string.sw_start))
                }
                if (workout.source != "BUILTIN") {
                    OutlinedButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 2.dp))
                        Text(stringResource(R.string.sw_delete))
                    }
                }
            }
        }
    }
}
