package com.joeydman.vitals.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joeydman.vitals.core.battery.CapacityEstimator
import com.joeydman.vitals.core.battery.DegradationTrend
import com.joeydman.vitals.core.battery.DeviceProfiles
import com.joeydman.vitals.core.battery.UnitNormalizer
import com.joeydman.vitals.core.model.ChargeStatus
import com.joeydman.vitals.core.retention.Downsampler
import com.joeydman.vitals.data.MetricsRepository

@Composable
fun BatteryScreen(repo: MetricsRepository) {
    val since = remember { System.currentTimeMillis() - 2 * Downsampler.DAY_MS }
    val samples by remember { repo.batteryHistory(since) }.collectAsState(initial = emptyList())
    val capacity by remember { repo.capacityHistory() }.collectAsState(initial = emptyList())
    val health by remember { repo.healthFlow() }.collectAsState(initial = null)
    val design by remember { repo.designCapacityFlow }.collectAsState(initial = null)

    val models = samples.map { it.toModel() }
    val currentRaw = models.mapNotNull { it.currentNowRaw }
    val unit = UnitNormalizer.detectCurrentUnit(currentRaw)
    val inverted = UnitNormalizer.detectSignInverted(
        models.filter { it.status == ChargeStatus.CHARGING }.mapNotNull { it.currentNowRaw },
    )
    val designMah = health?.designCapacityMah ?: design ?: remember { DeviceProfiles.designCapacityFor(Build.MODEL) }
    val trend = designMah?.let { d ->
        DegradationTrend.compute(
            capacity.map { CapacityEstimator.Estimate(it.dayStartMs, it.capacityMah, CapacityEstimator.Method.FUEL_GAUGE) },
            d,
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Battery health", style = MaterialTheme.typography.headlineSmall)

        val h = health
        MetricCard(
            title = "Estimated health",
            value = h?.let { "%.0f%%".format(it.healthPercent) } ?: "Learning…",
            subtitle = when {
                h != null -> "%.0f of %d mAh design capacity · %s".format(
                    h.estimatedCapacityMah, h.designCapacityMah, h.grade.name.lowercase(),
                )
                designMah == null -> "Set your design capacity in Settings to see health."
                else -> "Needs a few charge cycles of data. Keep the app installed and charge as normal."
            },
        ) {
            Sparkline(capacity.map { it.capacityMah })
        }

        MetricCard(
            title = "Degradation trend",
            value = trend?.let { "%.2f%% / month".format(it.percentPerMonth) } ?: "Needs 2+ weeks",
            subtitle = "Linear fit over daily capacity estimates",
        )

        MetricCard(
            title = "Charge cycles",
            value = models.lastOrNull()?.cycleCount?.toString() ?: "Not reported",
            subtitle = "Reported by Android 14+ (BatteryManager.EXTRA_CYCLE_COUNT)",
        )

        MetricCard(
            title = "Level, last 48 h",
            value = models.lastOrNull()?.let { "${it.levelPercent}%" } ?: "--",
            subtitle = if (currentRaw.isNotEmpty()) {
                "Current sensor: ${unit.name.lowercase()}${if (inverted) ", sign inverted" else ""}"
            } else {
                null
            },
        ) {
            Sparkline(models.map { it.levelPercent.toDouble() })
        }

        Text(
            "Estimates combine the fuel gauge's charge counter with measured charge between plug-in and unplug. " +
                "They improve over the first one to two weeks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
