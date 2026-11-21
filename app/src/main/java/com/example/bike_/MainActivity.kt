package com.example.bike_

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bike_.data.route.RouteFileParser
import com.example.bike_.ui.bikecomputer.BikeComputerScreen
import com.example.bike_.ui.history.HistoryScreen
import com.example.bike_.ui.history.WorkoutDetailScreen
import com.example.bike_.ui.offlinemaps.OfflineMapsScreen
import com.example.bike_.ui.permissions.PermissionsOnboardingScreen
import com.example.bike_.ui.permissions.allRequiredPermissionsGranted
import com.example.bike_.ui.profile.AthleteProfileScreen
import com.example.bike_.ui.routes.RoutesScreen
import com.example.bike_.ui.sensors.SensorsScreen
import com.example.bike_.ui.stats.FitnessStatsScreen
import com.example.bike_.ui.workouts.StructuredWorkoutsScreen
import com.example.bike_.ui.workouts.WorkoutBuilderScreen
import com.example.bike_.ui.theme.Bike_Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingRouteIntent(intent)
        setContent {
            val settings = (application as BikeApplication).cyclingSettings
            val uiThemeKey by settings.uiTheme.collectAsStateWithLifecycle()
            val uiAccentKey by settings.uiAccent.collectAsStateWithLifecycle()
            Bike_Theme(uiThemeKey = uiThemeKey, accentKey = uiAccentKey, dynamicColor = false) {
                var onboardingComplete by rememberSaveable {
                    mutableStateOf(allRequiredPermissionsGranted(this))
                }

                if (onboardingComplete) {
                    MainApp()
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        PermissionsOnboardingScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            onFinished = { onboardingComplete = true }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingRouteIntent(intent)
    }

    private fun handleIncomingRouteIntent(intent: Intent?) {
        if (intent == null) return
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        } ?: return

        lifecycleScope.launch {
            try {
                val route = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        RouteFileParser.parse(input)
                    }
                }
                if (route == null || route.points.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.route_import_failed),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    (application as BikeApplication).routeStateHolder.setRoute(route)
                    val name = route.name ?: getString(R.string.route_import_default_name)
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.route_import_loaded, name, route.points.size),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.route_import_error, e.message ?: getString(R.string.unknown_error)),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

private enum class RootTab(val route: String, val labelRes: Int) {
    Sensors("sensors", R.string.tab_sensors),
    Computer("computer", R.string.tab_computer),
    History("history", R.string.tab_history),
    Stats("stats", R.string.tab_stats)
}

@Composable
private fun MainApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as BikeApplication
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val metrics by app.workoutStateHolder.metrics.collectAsStateWithLifecycle()
    var unfinishedWorkout by androidx.compose.runtime.remember { mutableStateOf<com.example.bike_.data.db.entity.WorkoutEntity?>(null) }
    var unfinishedPointsCount by androidx.compose.runtime.remember { mutableStateOf(0) }
    var unfinishedDistanceMeters by androidx.compose.runtime.remember { mutableStateOf(0.0) }

    androidx.compose.runtime.LaunchedEffect(metrics.isTracking) {
        if (metrics.isTracking) return@LaunchedEffect
        val unfinished = withContext(Dispatchers.IO) { app.workoutRepository.getUnfinishedWorkout() }
        if (unfinished != null) {
            val count = withContext(Dispatchers.IO) { app.workoutRepository.getTrackPoints(unfinished.id).size }
            val dist = withContext(Dispatchers.IO) { app.workoutRepository.computeSummaryFromPoints(unfinished.id).distanceMeters }
            if (count > 0) {
                unfinishedWorkout = unfinished
                unfinishedPointsCount = count
                unfinishedDistanceMeters = dist
            } else {
                withContext(Dispatchers.IO) { app.workoutRepository.finalizeRecoveredWorkout(unfinished.id) }
            }
        }
    }

    val navController = rememberNavController()
    val tabs = listOf(RootTab.Sensors, RootTab.Computer, RootTab.History)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = tabs.any { it.route == currentRoute }

    val unfinished = unfinishedWorkout
    if (unfinished != null && !metrics.isTracking) {
        val dateFmt = androidx.compose.runtime.remember { java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()) }
        val dateStr = dateFmt.format(java.util.Date(unfinished.startTimeMs))
        val typeStr = when (unfinished.workoutType.uppercase(java.util.Locale.US)) {
            com.example.bike_.service.WorkoutTrackingService.WORKOUT_TYPE_RUNNING -> stringResource(R.string.bc_running)
            com.example.bike_.service.WorkoutTrackingService.WORKOUT_TYPE_WALKING -> stringResource(R.string.bc_walking)
            else -> stringResource(R.string.bc_cycling)
        }
        val distStr = String.format(java.util.Locale.US, "%.2f km", unfinishedDistanceMeters / 1000.0)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.bc_unfinished_title)) },
            text = { Text(stringResource(R.string.bc_unfinished_text, typeStr, dateStr, distStr, unfinishedPointsCount)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val id = unfinished.id
                    unfinishedWorkout = null
                    scope.launch { withContext(Dispatchers.IO) { try { app.workoutRepository.finalizeRecoveredWorkout(id) } catch (_: Exception) {} } }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val id = unfinished.id
                    unfinishedWorkout = null
                    com.example.bike_.service.WorkoutTrackingService.continueWorkout(context, id)
                }) { Text(stringResource(R.string.continue_label)) }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        val label = stringResource(tab.labelRes)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        RootTab.Sensors -> Icons.Filled.Bluetooth
                                        RootTab.Computer -> Icons.Filled.Speed
                                        RootTab.History -> Icons.Filled.History
                                        RootTab.Stats -> Icons.AutoMirrored.Filled.ShowChart
                                    },
                                    contentDescription = label
                                )
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = RootTab.Sensors.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(RootTab.Sensors.route) { SensorsScreen() }
            composable(RootTab.Computer.route) {
                BikeComputerScreen(
                    onOpenOfflineMaps = { navController.navigate("offline_maps") },
                    onOpenProfile = { navController.navigate("profile") },
                    onOpenWorkouts = { navController.navigate("workouts") },
                    onOpenRoutes = { navController.navigate("routes") }
                )
            }
            composable(RootTab.History.route) {
                HistoryScreen(onWorkoutClick = { id -> navController.navigate("workout/$id") })
            }
            composable(RootTab.Stats.route) {
                FitnessStatsScreen()
            }
            composable(
                route = "workout/{workoutId}",
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
            ) { backStackEntry ->
                val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: -1L
                WorkoutDetailScreen(
                    workoutId = workoutId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("offline_maps") {
                OfflineMapsScreen(onBack = { navController.popBackStack() })
            }
            composable("profile") {
                AthleteProfileScreen(onBack = { navController.popBackStack() })
            }
            composable("routes") {
                RoutesScreen(
                    onBack = { navController.popBackStack() },
                    onUseRoute = { navController.navigate(RootTab.Computer.route) { launchSingleTop = true } }
                )
            }
            composable("workouts") {
                StructuredWorkoutsScreen(
                    onBack = { navController.popBackStack() },
                    onCreate = { navController.navigate("workout_builder") },
                    onStartWorkout = { navController.navigate(RootTab.Computer.route) { launchSingleTop = true } }
                )
            }
            composable("workout_builder") {
                WorkoutBuilderScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
        }
    }
}
