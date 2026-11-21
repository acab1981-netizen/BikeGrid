package com.example.bike_.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.bike_.R
import com.example.bike_.ui.theme.LocalBikeUiPalette

@Composable
fun PermissionsOnboardingScreen(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    val steps = remember { buildPermissionSteps() }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
                permanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (steps.isEmpty()) {
        onFinished()
        return
    }

    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshTick++
        val anyDeniedPermanently = activity != null && results.any { (perm, granted) ->
            !granted && !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        }
        permanentlyDenied = anyDeniedPermanently
    }

    @Suppress("UNUSED_VARIABLE")
    val _tick = refreshTick

    val isStepGranted = isStepGranted(context, step)
    val isBgGranted = isBackgroundLocationGranted(context)
    val allForegroundGranted = allRequiredPermissionsGranted(context)

    val palette = LocalBikeUiPalette.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.screenBg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.perm_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary
        )
        Text(
            text = stringResource(R.string.perm_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary
        )

        LinearProgressIndicator(
            progress = { (stepIndex + 1).toFloat() / steps.size.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = palette.accent,
            trackColor = palette.mutedBg
        )
        Text(
            text = stringResource(R.string.perm_step_of, stepIndex + 1, steps.size),
            style = MaterialTheme.typography.labelLarge,
            color = palette.textSecondary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.cardBg),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stepTitle(step),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
                Text(
                    text = stepRationale(step),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
                Text(
                    text = if (isStepGranted) stringResource(R.string.perm_status_granted) else stringResource(R.string.perm_status_denied),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isStepGranted) palette.success else palette.danger
                )
                if (permanentlyDenied && !isStepGranted) {
                    Text(
                        text = stringResource(R.string.perm_denied_forever),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.danger
                    )
                }
            }
        }

        if (step == PermissionStep.BackgroundLocation) {
            BackgroundLocationHint(isGranted = isBgGranted)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    when {
                        isStepGranted -> Unit
                        step == PermissionStep.BackgroundLocation -> {
                            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                                launcher.launch(step.permissions.toTypedArray())
                            } else {
                                openAppSettings(context)
                            }
                        }
                        step.permissions.isEmpty() -> Unit
                        else -> launcher.launch(step.permissions.toTypedArray())
                    }
                },
                enabled = !isStepGranted && step.permissions.isNotEmpty(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White)
            ) {
                Text(
                    when {
                        isStepGranted -> stringResource(R.string.perm_status_granted)
                        step == PermissionStep.BackgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> stringResource(R.string.perm_open_settings)
                        else -> stringResource(R.string.ok)
                    }
                )
            }

            OutlinedButton(onClick = { openAppSettings(context) }) {
                Text(stringResource(R.string.perm_open_settings))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { if (stepIndex > 0) stepIndex-- },
                enabled = stepIndex > 0
            ) { Text(stringResource(R.string.back)) }

            val isLast = stepIndex == steps.lastIndex
            val canProceed = when (step) {
                PermissionStep.BackgroundLocation, PermissionStep.ActivityRecognition -> true
                else -> isStepGranted
            }

            Button(
                onClick = {
                    when {
                        !isLast -> stepIndex++
                        allForegroundGranted -> onFinished()
                        else -> {
                            val firstIncomplete = steps.indexOfFirst { !isStepGranted(context, it) && it != PermissionStep.BackgroundLocation }
                            if (firstIncomplete >= 0) stepIndex = firstIncomplete else onFinished()
                        }
                    }
                },
                enabled = canProceed,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White)
            ) {
                Text(
                    when {
                        !isLast -> stringResource(R.string.next)
                        allForegroundGranted -> stringResource(R.string.done)
                        else -> stringResource(R.string.continue_label)
                    }
                )
            }
        }

        Text(
            text = stepHint(step),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary
        )

        if (allForegroundGranted && !isBgGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Text(
                text = stringResource(R.string.perm_bg_not_granted_warning),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun BackgroundLocationHint(isGranted: Boolean) {
    val palette = LocalBikeUiPalette.current
    val hint = when {
        isGranted -> stringResource(R.string.perm_bg_hint_granted)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            stringResource(R.string.perm_bg_hint_r)
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ->
            stringResource(R.string.perm_bg_hint_q)
        else -> stringResource(R.string.perm_bg_hint_pre_q)
    }
    Text(
        text = hint,
        style = MaterialTheme.typography.bodySmall,
        color = palette.textSecondary
    )
}

sealed class PermissionStep(
    val permissions: List<String>
) {
    data object LocationForeground : PermissionStep(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    data object Bluetooth : PermissionStep(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else emptyList()
    )

    data object Notifications : PermissionStep(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else emptyList()
    )

    data object ActivityRecognition : PermissionStep(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.ACTIVITY_RECOGNITION)
        } else emptyList()
    )

    data object BackgroundLocation : PermissionStep(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else emptyList()
    )
}

@Composable
private fun stepTitle(step: PermissionStep): String = when (step) {
    PermissionStep.LocationForeground -> stringResource(R.string.perm_location_title)
    PermissionStep.Bluetooth -> stringResource(R.string.perm_bt_title)
    PermissionStep.Notifications -> stringResource(R.string.perm_notifications_title)
    PermissionStep.ActivityRecognition -> stringResource(R.string.perm_activity_title)
    PermissionStep.BackgroundLocation -> stringResource(R.string.perm_bg_location_title)
}

@Composable
private fun stepRationale(step: PermissionStep): String = when (step) {
    PermissionStep.LocationForeground -> stringResource(R.string.perm_location_rationale)
    PermissionStep.Bluetooth -> stringResource(R.string.perm_bt_rationale)
    PermissionStep.Notifications -> stringResource(R.string.perm_notifications_rationale)
    PermissionStep.ActivityRecognition -> stringResource(R.string.perm_activity_rationale)
    PermissionStep.BackgroundLocation -> stringResource(R.string.perm_bg_location_rationale)
}

@Composable
private fun stepHint(step: PermissionStep): String = when (step) {
    PermissionStep.LocationForeground -> stringResource(R.string.perm_hint_location)
    PermissionStep.Bluetooth -> stringResource(R.string.perm_hint_bt)
    PermissionStep.Notifications -> stringResource(R.string.perm_hint_notifications)
    PermissionStep.ActivityRecognition -> stringResource(R.string.perm_hint_activity)
    PermissionStep.BackgroundLocation -> stringResource(R.string.perm_hint_bg_location)
}

fun buildPermissionSteps(): List<PermissionStep> = buildList {
    add(PermissionStep.LocationForeground)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(PermissionStep.Bluetooth)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(PermissionStep.Notifications)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(PermissionStep.ActivityRecognition)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(PermissionStep.BackgroundLocation)
    }
}

fun isStepGranted(context: Context, step: PermissionStep): Boolean = when (step) {
    PermissionStep.LocationForeground -> isForegroundLocationGranted(context)
    PermissionStep.Bluetooth -> isBluetoothGranted(context)
    PermissionStep.Notifications -> isNotificationsGranted(context)
    PermissionStep.ActivityRecognition -> isActivityRecognitionGranted(context)
    PermissionStep.BackgroundLocation -> isBackgroundLocationGranted(context)
}

fun isForegroundLocationGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun isBluetoothGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val scan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        val connect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        scan && connect
    } else {
        true
    }

fun isNotificationsGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

fun isActivityRecognitionGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    } else true

fun isBackgroundLocationGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    } else true

/** "Required" to start normal tracking flow from onboarding (background/activity recognition are recommended, handled separately). */
fun allRequiredPermissionsGranted(context: Context): Boolean =
    isForegroundLocationGranted(context) && isBluetoothGranted(context) && isNotificationsGranted(context)

fun permissionsOnboardingComplete(context: Context): Boolean = allRequiredPermissionsGranted(context)

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
