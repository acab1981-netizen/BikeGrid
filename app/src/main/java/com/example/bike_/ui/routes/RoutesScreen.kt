package com.example.bike_.ui.routes

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Route
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bike_.BikeApplication
import com.example.bike_.R
import com.example.bike_.data.route.RouteFileParser
import com.example.bike_.domain.model.LoadedRoute
import com.example.bike_.ui.theme.LocalBikeUiPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    onBack: () -> Unit,
    onUseRoute: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as BikeApplication
    val palette = LocalBikeUiPalette.current
    val scope = rememberCoroutineScope()
    val routes by app.routeRepository.observeRoutes().collectAsStateWithLifecycle(initialValue = emptyList())
    var importing by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            try {
                val name = uri.lastPathSegment
                val parsed = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input -> try { RouteFileParser.parse(input) } catch (e: Exception) { android.widget.Toast.makeText(context, e.message ?: context.getString(R.string.route_import_failed), android.widget.Toast.LENGTH_LONG).show(); null } }
                }
                if (parsed == null || parsed.points.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.route_import_failed), Toast.LENGTH_LONG).show()
                } else {
                    val saved = withContext(Dispatchers.IO) { app.routeRepository.saveImportedRoute(parsed, name) }
                    app.routeStateHolder.setRoute(saved)
                    Toast.makeText(context, context.getString(R.string.route_import_loaded, saved.name ?: context.getString(R.string.route_import_default_name), saved.points.size), Toast.LENGTH_SHORT).show()
                    onUseRoute()
                }
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.route_import_error, e.message ?: ""), Toast.LENGTH_LONG).show()
            } finally {
                importing = false
            }
        }
    }

    Scaffold(
        containerColor = palette.screenBg,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routes_title), color = palette.textPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = palette.textPrimary) } },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/gpx+xml","application/vnd.ant.fit","application/octet-stream","application/xml","text/xml","*/*")) }, enabled = !importing) {
                        Icon(Icons.Filled.FileOpen, contentDescription = stringResource(R.string.routes_import), tint = palette.accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.cardBg)
            )
        }
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(routes, key = { it.id }) { route ->
                RouteCard(
                    route = route,
                    onUse = {
                        app.routeStateHolder.setRoute(route)
                        onUseRoute()
                    },
                    onDelete = { scope.launch(Dispatchers.IO) { app.routeRepository.deleteRoute(route.id) } }
                )
            }
            if (routes.isEmpty()) {
                item { Text(stringResource(R.string.routes_empty), color = palette.textSecondary) }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun RouteCard(route: LoadedRoute, onUse: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalBikeUiPalette.current
    Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Route, contentDescription = null, tint = palette.accent)
                Text(route.name ?: stringResource(R.string.route_import_default_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            }
            Text(
                String.format(Locale.US, "%.1f km  ?  +%.0f m  ?  %d pts", route.distanceMeters / 1000.0, route.elevationGainMeters, route.points.size),
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onUse) { Text(stringResource(R.string.routes_use)) }
                OutlinedButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = null); Spacer(Modifier.padding(2.dp)); Text(stringResource(R.string.sw_delete)) }
            }
        }
    }
}
