package com.example.bike_.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bike_.BikeApplication
import com.example.bike_.BuildConfig
import com.example.bike_.R
import com.example.bike_.data.healthconnect.HealthConnectManager
import com.example.bike_.domain.analytics.TrainingAnalytics
import com.example.bike_.ui.theme.LocalBikeUiPalette
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthleteProfileScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ProfileViewModel = viewModel()) {
    val palette = LocalBikeUiPalette.current
    val weight by viewModel.weightKg.collectAsStateWithLifecycle()
    val maxHr by viewModel.maxHr.collectAsStateWithLifecycle()
    val restingHr by viewModel.restingHr.collectAsStateWithLifecycle()
    val ftp by viewModel.ftpWatts.collectAsStateWithLifecycle()
    val age by viewModel.ageYears.collectAsStateWithLifecycle()
    val isMale by viewModel.isMale.collectAsStateWithLifecycle()
    val healthConnectEnabled by viewModel.healthConnectEnabled.collectAsStateWithLifecycle()

    val appContext = LocalContext.current.applicationContext
    val healthConnectManager = remember { (appContext as BikeApplication).healthConnectManager }
    var hcAvailable by remember { mutableStateOf(false) }
    var hcGranted by remember { mutableStateOf(false) }
    var hcReadGranted by remember { mutableStateOf(false) }
    var hcChecked by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<HealthConnectManager.ImportResult?>(null) }
    val hcScope = rememberCoroutineScope()
    LaunchedEffect(healthConnectManager) {
        hcAvailable = healthConnectManager.isAvailable()
        hcGranted = if (hcAvailable) healthConnectManager.hasWritePermissions() else false
        hcReadGranted = if (hcAvailable) healthConnectManager.hasReadPermissions() else false
        hcChecked = true
    }
    val hcPermissionLauncher = rememberLauncherForActivityResult(healthConnectManager.permissionRequestContract) { granted ->
        val ok = healthConnectManager.areWritePermissionsGranted(granted)
        hcGranted = ok
        viewModel.setHealthConnectEnabled(ok)
    }
    val hcReadPermissionLauncher = rememberLauncherForActivityResult(healthConnectManager.permissionRequestContract) { granted ->
        val ok = healthConnectManager.areReadPermissionsGranted(granted)
        hcReadGranted = ok
        if (ok) {
            hcScope.launch { importResult = healthConnectManager.importWorkouts() }
        } else {
            importResult = HealthConnectManager.ImportResult.PermissionsMissing
        }
    }
    fun requestHealthImport() {
        if (!hcAvailable) return
        importResult = null
        if (hcReadGranted) {
            hcScope.launch { importResult = healthConnectManager.importWorkouts() }
        } else {
            hcReadPermissionLauncher.launch(HealthConnectManager.READ_PERMISSIONS)
        }
    }

    Scaffold(containerColor = palette.screenBg, topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.profile_title), color = palette.textPrimary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = palette.textPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.cardBg)
        )
    }) { inner ->
        Column(
            modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.profile_athlete_data), fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    ProfileNumberField(stringResource(R.string.profile_weight), formatWeight(weight), "kg", { it.toFloatOrNull()?.let(viewModel::setWeightKg) }, allowDecimal = true)
                    ProfileNumberField(stringResource(R.string.profile_max_hr), maxHr.toString(), "bpm", onChange = { it.toIntOrNull()?.let(viewModel::setMaxHr) })
                    ProfileNumberField(stringResource(R.string.profile_resting_hr), restingHr.toString(), "bpm", onChange = { it.toIntOrNull()?.let(viewModel::setRestingHr) })
                    ProfileNumberField(stringResource(R.string.profile_ftp), ftp.toString(), "W", onChange = { it.toIntOrNull()?.let(viewModel::setFtpWatts) })
                    ProfileNumberField(stringResource(R.string.profile_age), age.toString(), stringResource(R.string.profile_years), onChange = { it.toIntOrNull()?.let(viewModel::setAgeYears) })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.profile_sex), color = palette.textSecondary, modifier = Modifier.weight(1f))
                        FilterChip(
                            selected = isMale,
                            onClick = { viewModel.setIsMale(true) },
                            label = { Text(stringResource(R.string.profile_male)) }
                        )
                        FilterChip(
                            selected = !isMale,
                            onClick = { viewModel.setIsMale(false) },
                            label = { Text(stringResource(R.string.profile_female)) }
                        )
                    }
                    Text(stringResource(R.string.profile_defaults_hint), fontSize = 12.sp, color = palette.textSecondary)
                }
            }

            val hrZones = TrainingAnalytics.hrZones(maxHr, restingHr)
            Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.profile_hr_zones), fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    hrZones.forEach { z ->
                        val range = if (z.maxBpm == null) ">= ${z.minBpm} bpm" else "${z.minBpm}-${z.maxBpm} bpm"
                        ZoneRow(Color(z.colorArgb.toInt()), "Z${z.zone} ${z.name}", range)
                    }
                }
            }

            val pwrZones = TrainingAnalytics.powerZones(ftp)
            Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.profile_power_zones), fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    pwrZones.forEach { z ->
                        val range = if (z.maxWatts == null) ">= ${z.minWatts} W" else "${z.minWatts}-${z.maxWatts} W"
                        ZoneRow(Color(z.colorArgb.toInt()), "Z${z.zone} ${z.name}", range)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.profile_health_connect), fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.profile_health_connect_enable), color = palette.textPrimary, fontSize = 14.sp)
                            val status = when {
                                !hcChecked -> stringResource(R.string.health_status_checking)
                                !hcAvailable -> stringResource(R.string.health_status_unavailable)
                                hcGranted -> stringResource(R.string.health_status_ready)
                                else -> stringResource(R.string.health_status_permissions_needed)
                            }
                            Text(status, color = palette.textSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = healthConnectEnabled,
                            enabled = hcAvailable,
                            onCheckedChange = { checked ->
                                when {
                                    !checked -> viewModel.setHealthConnectEnabled(false)
                                    hcGranted -> viewModel.setHealthConnectEnabled(true)
                                    hcAvailable -> hcPermissionLauncher.launch(HealthConnectManager.WRITE_PERMISSIONS)
                                }
                            }
                        )
                    }
                    Text(stringResource(R.string.profile_health_connect_hint), color = palette.textSecondary, fontSize = 12.sp)
                    if (hcAvailable && !hcGranted) {
                        Button(onClick = { hcPermissionLauncher.launch(HealthConnectManager.WRITE_PERMISSIONS) }) {
                            Text(stringResource(R.string.health_grant_permissions))
                        }
                    }
                    if (hcAvailable) {
                        OutlinedButton(onClick = ::requestHealthImport, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.health_import))
                        }
                    }
                    importResult?.let { result ->
                        Text(
                            text = when (result) {
                                is HealthConnectManager.ImportResult.Success -> stringResource(
                                    R.string.health_imported_fmt,
                                    result.imported,
                                    result.skipped
                                )
                                HealthConnectManager.ImportResult.Unavailable -> stringResource(R.string.health_status_unavailable)
                                HealthConnectManager.ImportResult.PermissionsMissing -> stringResource(R.string.health_import_permissions_needed)
                                is HealthConnectManager.ImportResult.Error -> stringResource(
                                    R.string.health_import_error_fmt,
                                    result.message
                                )
                            },
                            color = palette.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.profile_build_info), fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Text(
                        "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.BUILD_TYPE}",
                        color = palette.textPrimary,
                        fontSize = 13.sp
                    )
                    Text(BuildConfig.APPLICATION_ID, color = palette.textSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ProfileNumberField(
    label: String,
    value: String,
    suffix: String,
    onChange: (String) -> Unit,
    allowDecimal: Boolean = false
) {
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Keep in sync with stored settings, but never overwrite text while the user is typing:
    // otherwise clamping in the settings jumps the value to min/max on every keystroke.
    LaunchedEffect(value, focused) {
        if (!focused && text != value) text = value
    }

    fun commit() {
        val normalized = if (allowDecimal) text.replace(',', '.') else text
        val valid = if (allowDecimal) normalized.toFloatOrNull() != null else normalized.toIntOrNull() != null
        if (valid) onChange(normalized) else text = value
    }

    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = if (allowDecimal) {
                val filtered = v.replace(',', '.').filter { it.isDigit() || it == '.' }
                val firstDot = filtered.indexOf('.')
                if (firstDot >= 0) filtered.filterIndexed { index, c -> c != '.' || index == firstDot } else filtered
            } else {
                v.filter { it.isDigit() }
            }
        },
        label = { Text("$label ($suffix)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { commit(); focusManager.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                val wasFocused = focused
                focused = state.isFocused
                if (wasFocused && !state.isFocused) commit()
            }
    )
}

private fun formatWeight(kg: Float): String =
    if (kg % 1f == 0f) kg.toInt().toString() else String.format(Locale.US, "%.1f", kg)

@Composable
private fun ZoneRow(color: Color, label: String, value: String) {
    val palette = LocalBikeUiPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f), color = palette.textPrimary, fontSize = 13.sp)
        Text(value, color = palette.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
