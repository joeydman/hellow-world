package com.joeydman.vitals.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.joeydman.vitals.core.battery.DeviceProfiles
import com.joeydman.vitals.data.MetricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(repo: MetricsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileMah = remember { DeviceProfiles.designCapacityFor(Build.MODEL) }
    var capacityText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        capacityText = (repo.designCapacityMah() ?: profileMah)?.toString().orEmpty()
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { repo.exportBatteryCsv(it) }
                }.isSuccess
            }
            status = if (ok) "Exported." else "Export failed."
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Text("Design capacity", style = MaterialTheme.typography.titleMedium)
        Text(
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}" +
                (profileMah?.let { " · built-in profile $it mAh" } ?: " · no built-in profile"),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = capacityText,
            onValueChange = { capacityText = it.filter(Char::isDigit).take(5) },
            label = { Text("mAh") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            scope.launch {
                val mah = capacityText.toIntOrNull()?.takeIf { it in 500..20_000 }
                repo.setDesignCapacity(if (mah == profileMah) null else mah)
                status = "Saved."
            }
        }) { Text("Save") }

        HorizontalDivider()
        Text("Keep monitoring reliable", style = MaterialTheme.typography.titleMedium)
        Text(
            "One UI can pause background apps. Open App info > Battery and choose Unrestricted so Vitals can " +
                "sample every 15 minutes. Also check Settings > Battery > Background usage limits and make sure " +
                "Vitals is not in Sleeping or Deep sleeping apps.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
            )
        }) { Text("Open App info") }
        OutlinedButton(onClick = { context.startActivity(repo.usage.settingsIntent()) }) {
            Text(if (repo.usage.hasPermission()) "Usage access: granted" else "Grant usage access")
        }

        HorizontalDivider()
        Text("Your data", style = MaterialTheme.typography.titleMedium)
        Text(
            "Vitals has no internet permission and excludes its data from cloud backup. " +
                "Everything stays on this phone unless you export it.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = { exportLauncher.launch("vitals-battery.csv") }) { Text("Export battery history (CSV)") }

        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
