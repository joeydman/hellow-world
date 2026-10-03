package com.joeydman.vitals.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.joeydman.vitals.data.MetricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@Composable
fun AppsScreen(repo: MetricsRepository) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(repo.usage.hasPermission()) }

    // Re-check on return from the Usage Access settings page and refresh today's totals.
    LifecycleResumeEffect(Unit) {
        granted = repo.usage.hasPermission()
        val scope = MainScope()
        scope.launch(Dispatchers.IO) { repo.refreshUsageToday() }
        onPauseOrDispose { scope.cancel() }
    }

    if (!granted) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Usage access needed", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Android only shares per-app screen time with apps you allow in Settings > Usage data access. " +
                    "Vitals reads it on the device and never sends it anywhere: the app has no internet permission.",
            )
            Button(onClick = { context.startActivity(repo.usage.settingsIntent()) }) { Text("Open settings") }
        }
        return
    }

    val usage by remember { repo.usageToday() }.collectAsState(initial = emptyList())
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "Today · ${formatDuration(usage.sumOf { it.foregroundMs })} screen time",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(usage, key = { it.packageName }) { row ->
            ListItem(
                headlineContent = { Text(remember(row.packageName) { repo.usage.label(row.packageName) }) },
                supportingContent = { Text("${row.launches} opens") },
                trailingContent = { Text(formatDuration(row.foregroundMs)) },
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "Per-app battery drain is not available to third-party apps on Android without root, " +
                        "so Vitals shows screen time and opens instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
