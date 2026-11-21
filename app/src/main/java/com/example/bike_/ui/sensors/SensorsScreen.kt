package com.example.bike_.ui.sensors

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bike_.R
import com.example.bike_.data.ble.model.BleConnectionState
import com.example.bike_.data.ble.model.BleConnectionStatus
import com.example.bike_.data.ble.model.BleSensorType
import com.example.bike_.data.ble.model.DiscoveredBleDevice
import com.example.bike_.data.settings.KnownSensorPref
import com.example.bike_.ui.theme.LocalBikeUiPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SensorsScreen(
    modifier: Modifier = Modifier,
    viewModel: SensorsViewModel = viewModel()
) {
    val palette = LocalBikeUiPalette.current
    val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val knownSensors by viewModel.knownSensors.collectAsStateWithLifecycle()
    val wheelCircumferenceMm by viewModel.wheelCircumferenceMm.collectAsStateWithLifecycle()
    val bleUnfilteredScan by viewModel.bleUnfilteredScanEnabled.collectAsStateWithLifecycle()
    var scanning by rememberSaveable { mutableStateOf(false) }
    var showWheelDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.tryAutoConnectKnown()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopScan()
        }
    }

    val connectedIds = connections
        .filter { it.state == BleConnectionState.CONNECTED || it.state == BleConnectionState.CONNECTING }
        .map { it.stableId }
        .toSet()
    val filteredResults = scanResults.filter { it.stableId !in connectedIds }
    val rememberedNotActive = knownSensors.filter { it.id !in connectedIds }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(palette.screenBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                stringResource(R.string.sensors_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            Text(
                stringResource(R.string.sensors_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (scanning) {
                            viewModel.stopScan(); scanning = false
                        } else {
                            viewModel.startScan(); scanning = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White)
                ) {
                    Icon(
                        if (scanning) Icons.Filled.Bluetooth else Icons.AutoMirrored.Filled.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (scanning) stringResource(R.string.sensors_stop_search) else stringResource(R.string.sensors_search), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = { showWheelDialog = true },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.sensors_wheel_setup), tint = palette.accent, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                stringResource(R.string.sensors_wheel_label, wheelCircumferenceMm),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.sensors_unfiltered_scan),
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.sensors_unfiltered_scan_hint),
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = bleUnfilteredScan,
                    onCheckedChange = viewModel::setBleUnfilteredScanEnabled
                )
            }
        }

        item { SectionTitle(stringResource(R.string.sensors_connected), palette) }
        if (connections.isEmpty()) {
            item { EmptyHint(stringResource(R.string.sensors_connected_empty), palette) }
        } else {
            items(connections, key = { it.stableId + ":" + it.state.name }) { status ->
                ConnectedDeviceCard(
                    status = status,
                    palette = palette,
                    onDisconnect = { viewModel.disconnect(status) }
                )
            }
        }

        item { SectionTitle(stringResource(R.string.sensors_saved), palette) }
        if (knownSensors.isEmpty()) {
            item { EmptyHint(stringResource(R.string.sensors_saved_empty), palette) }
        } else {
            items(rememberedNotActive, key = { "known-" + it.id }) { known ->
                KnownDeviceCard(
                    known = known,
                    palette = palette,
                    onReconnect = { viewModel.reconnectKnown(known.id) },
                    onToggleAuto = { enabled -> viewModel.setAutoReconnect(known.id, enabled) },
                    onForget = { viewModel.forgetKnown(known.id) }
                )
            }
            if (rememberedNotActive.isEmpty() && knownSensors.isNotEmpty()) {
                item { EmptyHint(stringResource(R.string.sensors_saved_all_active), palette) }
            }
        }

        item { SectionTitle(stringResource(R.string.sensors_found), palette) }
        if (filteredResults.isEmpty()) {
            item { EmptyHint(if (scanning) stringResource(R.string.sensors_searching_ble) else stringResource(R.string.sensors_found_empty), palette) }
        } else {
            items(filteredResults, key = { it.stableId }) { device ->
                DiscoveredDeviceCard(device = device, palette = palette, onConnect = { viewModel.connect(device) })
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showWheelDialog) {
        WheelCircumferenceDialog(
            currentMm = wheelCircumferenceMm,
            palette = palette,
            onDismiss = { showWheelDialog = false },
            onSave = { mm ->
                viewModel.setWheelCircumference(mm)
                showWheelDialog = false
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String, palette: com.example.bike_.ui.theme.BikeUiPalette) {
    Spacer(Modifier.height(2.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
}

@Composable
private fun WheelCircumferenceDialog(
    currentMm: Float,
    palette: com.example.bike_.ui.theme.BikeUiPalette,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit
) {
    var text by rememberSaveable(currentMm) { mutableStateOf(String.format(Locale.US, "%.0f", currentMm)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = { Text(stringResource(R.string.sensors_wheel_title), color = palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.sensors_wheel_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.') },
                    label = { Text(stringResource(R.string.sensors_mm)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { text.toFloatOrNull()?.let(onSave) }) { Text(stringResource(R.string.save), color = palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ConnectedDeviceCard(status: BleConnectionStatus, palette: com.example.bike_.ui.theme.BikeUiPalette, onDisconnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(10.dp).clip(CircleShape).background(
                    when (status.state) {
                        BleConnectionState.CONNECTED -> palette.success
                        BleConnectionState.CONNECTING -> palette.accent
                        else -> palette.textSecondary
                    }
                )
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status.name ?: stringResource(R.string.sensors_unknown_name), fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Spacer(Modifier.width(6.dp))
                    BleBadge(palette)
                }
                Text(status.address, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                val stateText = stateLabel(status.state)
                val detectingText = stringResource(R.string.sensors_detecting)
                val typeNames = mutableListOf<String>()
                for (sensorType in status.sensorTypes) {
                    typeNames.add(typeLabel(sensorType))
                }
                val typesText = if (typeNames.isEmpty()) detectingText else typeNames.joinToString(", ")
                Text(
                    "$stateText · $typesText",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
                status.batteryPercent?.let { pct ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.BatteryFull, contentDescription = null, modifier = Modifier.size(16.dp), tint = palette.textSecondary)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.sensors_battery, pct), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                    }
                }
                status.lastError?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = palette.danger)
                }
            }
            IconButton(onClick = onDisconnect) {
                Icon(Icons.Filled.LinkOff, contentDescription = stringResource(R.string.sensors_disconnect), tint = palette.textSecondary)
            }
        }
    }
}

@Composable
private fun KnownDeviceCard(
    known: KnownSensorPref,
    palette: com.example.bike_.ui.theme.BikeUiPalette,
    onReconnect: () -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onForget: () -> Unit
) {
    val df = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Memory, contentDescription = null, tint = palette.accent)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(known.name ?: stringResource(R.string.sensors_saved_sensor), fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Spacer(Modifier.width(6.dp))
                        BleBadge(palette)
                    }
                    Text(known.address.ifBlank { known.id }, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                    val types = known.sensorTypes.mapNotNull { runCatching { BleSensorType.valueOf(it) }.getOrNull() }
                    if (types.isNotEmpty()) {
                        val labels = mutableListOf<String>()
                        for (t in types) {
                            labels.add(typeLabel(t))
                        }
                        Text(labels.joinToString(), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                    }
                    if (known.lastConnectedMs > 0L) {
                        Text(stringResource(R.string.sensors_last_connected, df.format(Date(known.lastConnectedMs))), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onReconnect,
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.sensors_connect), style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = { onToggleAuto(!known.autoConnect) },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text(
                        if (known.autoConnect) stringResource(R.string.sensors_auto_on) else stringResource(R.string.sensors_auto_off),
                        color = if (known.autoConnect) palette.accent else palette.textSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                IconButton(onClick = onForget, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.sensors_forget), tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DiscoveredDeviceCard(device: DiscoveredBleDevice, palette: com.example.bike_.ui.theme.BikeUiPalette, onConnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = palette.accent
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(device.name ?: stringResource(R.string.sensors_ble_device), fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Spacer(Modifier.width(6.dp))
                    BleBadge(palette)
                }
                Text(device.address, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                Text(stringResource(R.string.sensors_rssi, device.rssi), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
            OutlinedButton(
                onClick = onConnect,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.sensors_connect), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun BleBadge(palette: com.example.bike_.ui.theme.BikeUiPalette) {
    Box(
        modifier = Modifier
            .background(palette.mutedBg, RoundedCornerShape(6.dp))
            .border(1.dp, palette.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text("BLE", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
    }
}

@Composable
private fun EmptyHint(text: String, palette: com.example.bike_.ui.theme.BikeUiPalette) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
}

@Composable
private fun stateLabel(state: BleConnectionState): String = when (state) {
    BleConnectionState.CONNECTED -> stringResource(R.string.sensors_state_connected)
    BleConnectionState.CONNECTING -> stringResource(R.string.sensors_state_connecting)
    BleConnectionState.DISCONNECTING -> stringResource(R.string.sensors_state_disconnecting)
    BleConnectionState.DISCONNECTED -> stringResource(R.string.sensors_state_disconnected)
}

@Composable
private fun typeLabel(type: BleSensorType): String = when (type) {
    BleSensorType.HEART_RATE -> stringResource(R.string.sensors_type_hr)
    BleSensorType.CYCLING_SPEED_AND_CADENCE -> stringResource(R.string.sensors_type_csc)
    BleSensorType.CYCLING_POWER -> stringResource(R.string.sensors_type_power)
    BleSensorType.RUNNING_SPEED_AND_CADENCE -> stringResource(R.string.sensors_type_rsc)
    BleSensorType.FITNESS_MACHINE -> stringResource(R.string.sensors_type_ftms)
}
