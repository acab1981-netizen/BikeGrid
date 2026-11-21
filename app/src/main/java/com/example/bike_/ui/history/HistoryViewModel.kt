package com.example.bike_.ui.history

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bike_.BikeApplication
import com.example.bike_.data.backup.BackupManager
import com.example.bike_.data.db.entity.LapEntity
import com.example.bike_.data.db.entity.TrackPointEntity
import com.example.bike_.data.db.entity.WorkoutEntity
import com.example.bike_.data.export.ExportFormat
import com.example.bike_.data.repository.WorkoutRepository
import com.example.bike_.data.watch.WatchSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: WorkoutRepository = (application as BikeApplication).workoutRepository
    private val backupManager: BackupManager = (application as BikeApplication).backupManager
    private val watchSyncManager: WatchSyncManager = (application as BikeApplication).watchSyncManager

    val workouts: StateFlow<List<WorkoutEntity>> = repo.observeWorkouts()
        .map { list -> list.filter { it.isFinished } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    private val _restoreState = MutableStateFlow<RestoreUiState>(RestoreUiState.Idle)
    val restoreState: StateFlow<RestoreUiState> = _restoreState.asStateFlow()

    private val _watchState = MutableStateFlow<WatchUiState>(WatchUiState.Idle)
    val watchState: StateFlow<WatchUiState> = _watchState.asStateFlow()

    fun observeWorkout(id: Long): Flow<WorkoutEntity?> = repo.observeWorkout(id)

    suspend fun getTrackPoints(id: Long): List<TrackPointEntity> = repo.getTrackPoints(id)

    fun deleteWorkout(id: Long) {
        viewModelScope.launch {
            try {
                repo.deleteWorkout(id)
            } catch (_: Exception) {
            }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            _backupState.value = BackupUiState.InProgress
            try {
                when (val result = backupManager.createBackup()) {
                    is BackupManager.BackupResult.Success ->
                        _backupState.value = BackupUiState.Success(result.path, result.workoutCount)
                    is BackupManager.BackupResult.Error ->
                        _backupState.value = BackupUiState.Error(result.message)
                }
            } catch (e: Exception) {
                _backupState.value = BackupUiState.Error(e.message ?: getApplication<Application>().getString(com.example.bike_.R.string.backup_error_save))
            }
        }
    }

    fun restoreFromUri(uri: Uri) {
        viewModelScope.launch {
            _restoreState.value = RestoreUiState.InProgress
            try {
                val json = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
                if (json == null) {
                    _restoreState.value = RestoreUiState.Error(getApplication<Application>().getString(com.example.bike_.R.string.backup_restore_invalid))
                    return@launch
                }
                when (val result = backupManager.restore(json)) {
                    is BackupManager.RestoreResult.Success ->
                        _restoreState.value = RestoreUiState.Success(result.workoutCount)
                    is BackupManager.RestoreResult.Error ->
                        _restoreState.value = RestoreUiState.Error(result.message)
                }
            } catch (e: Exception) {
                _restoreState.value = RestoreUiState.Error(e.message ?: getApplication<Application>().getString(com.example.bike_.R.string.backup_restore_error))
            }
        }
    }

    fun sendToWatch() {
        viewModelScope.launch {
            _watchState.value = WatchUiState.InProgress
            try {
                when (val result = watchSyncManager.sendLastWorkout()) {
                    is WatchSyncManager.SendResult.Success -> _watchState.value = WatchUiState.Sent
                    WatchSyncManager.SendResult.NoWorkout -> _watchState.value = WatchUiState.Error(
                        getApplication<Application>().getString(com.example.bike_.R.string.watch_no_workout)
                    )
                    WatchSyncManager.SendResult.PermissionsNeeded -> _watchState.value = WatchUiState.Error(
                        getApplication<Application>().getString(com.example.bike_.R.string.watch_permission_needed)
                    )
                    is WatchSyncManager.SendResult.Error -> _watchState.value = WatchUiState.Error(
                        getApplication<Application>().getString(com.example.bike_.R.string.watch_error_fmt, result.message)
                    )
                }
            } catch (e: Exception) {
                _watchState.value = WatchUiState.Error(
                    getApplication<Application>().getString(
                        com.example.bike_.R.string.watch_error_fmt,
                        e.message ?: getApplication<Application>().getString(com.example.bike_.R.string.unknown_error)
                    )
                )
            }
        }
    }

    fun watchPermissionDenied() {
        _watchState.value = WatchUiState.Error(
            getApplication<Application>().getString(com.example.bike_.R.string.watch_permission_needed)
        )
    }

    fun consumeBackup() { _backupState.value = BackupUiState.Idle }
    fun consumeRestore() { _restoreState.value = RestoreUiState.Idle }

    sealed interface BackupUiState {
        data object Idle : BackupUiState
        data object InProgress : BackupUiState
        data class Success(val path: String, val workoutCount: Int) : BackupUiState
        data class Error(val message: String) : BackupUiState
    }

    sealed interface RestoreUiState {
        data object Idle : RestoreUiState
        data object InProgress : RestoreUiState
        data class Success(val workoutCount: Int) : RestoreUiState
        data class Error(val message: String) : RestoreUiState
    }

    sealed interface WatchUiState {
        data object Idle : WatchUiState
        data object InProgress : WatchUiState
        data object Sent : WatchUiState
        data class Error(val message: String) : WatchUiState
    }
}

class WorkoutDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: WorkoutRepository = (application as BikeApplication).workoutRepository

    private val _trackPoints = MutableStateFlow<List<TrackPointEntity>>(emptyList())
    val trackPoints: StateFlow<List<TrackPointEntity>> = _trackPoints.asStateFlow()

    private val _laps = MutableStateFlow<List<LapEntity>>(emptyList())
    val laps: StateFlow<List<LapEntity>> = _laps.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun observeWorkout(id: Long): Flow<WorkoutEntity?> = repo.observeWorkout(id)

    fun loadTrackPoints(id: Long) {
        viewModelScope.launch {
            try {
                _trackPoints.value = repo.getTrackPoints(id)
            } catch (_: Exception) {
                _trackPoints.value = emptyList()
            }
        }
    }

    fun loadLaps(id: Long) {
        viewModelScope.launch {
            try {
                _laps.value = repo.getLaps(id)
            } catch (_: Exception) {
                _laps.value = emptyList()
            }
        }
    }

    fun export(workoutId: Long, format: ExportFormat) {
        viewModelScope.launch {
            _exportState.value = ExportState.InProgress
            try {
                when (val result = repo.exportWorkoutAndShare(workoutId, format)) {
                    is WorkoutRepository.ExportResult.Success -> _exportState.value = ExportState.Ready(result.path, result.shareIntent)
                    is WorkoutRepository.ExportResult.Error -> _exportState.value = ExportState.Error(result.message)
                }
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: getApplication<android.app.Application>().getString(com.example.bike_.R.string.export_error))
            }
        }
    }

    fun consumeExport() { _exportState.value = ExportState.Idle }

    sealed interface ExportState {
        data object Idle : ExportState
        data object InProgress : ExportState
        data class Ready(val path: String, val shareIntent: Intent) : ExportState
        data class Error(val message: String) : ExportState
    }
}
