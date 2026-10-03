package com.joeydman.vitals.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joeydman.vitals.core.battery.UnitNormalizer
import com.joeydman.vitals.core.model.BatterySample
import com.joeydman.vitals.core.thermal.ThermalHeadroom
import com.joeydman.vitals.core.thermal.ThermalState
import com.joeydman.vitals.data.MetricsRepository
import com.joeydman.vitals.sensors.CpuFreqReader
import com.joeydman.vitals.sensors.MemoryReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class Live(
    val battery: BatterySample?,
    val memory: MemoryReader.Memory,
    val thermal: ThermalState,
    val headroom: Float?,
    val cpu: CpuFreqReader.CpuFreq?,
)

/** Live view. Polls every 2 s while visible (thermal headroom is rate-limited to 1 Hz). */
@Composable
fun OverviewScreen(repo: MetricsRepository) {
    val live by produceState<Live?>(initialValue = null) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                Live(repo.battery.read(), repo.memory.read(), repo.thermal.status(), repo.thermal.headroom(), repo.cpu.read())
            }
            delay(2_000)
        }
    }
    val l = live ?: return

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Right now", style = MaterialTheme.typography.headlineSmall)

        val b = l.battery
        val currentMa = b?.currentNowRaw?.let {
            // Single reading: magnitude heuristic only. History screens use the learned unit.
            UnitNormalizer.currentToMa(it, UnitNormalizer.detectCurrentUnit(listOf(it)), signInverted = false)
        }
        MetricCard(
            title = "Battery",
            value = b?.let { "${it.levelPercent}%" } ?: "--",
            subtitle = listOfNotNull(
                b?.status?.name?.lowercase()?.replaceFirstChar { it.uppercase() },
                currentMa?.let { "%+.0f mA".format(it) },
                b?.voltageMv?.let { "%.2f V".format(it / 1000.0) },
            ).joinToString(" · "),
        )

        MetricCard(
            title = "Temperature",
            value = b?.temperatureDeciC?.let { "%.1f°C".format(it / 10.0) } ?: "--",
            subtitle = buildString {
                append("Thermal status: ${l.thermal.label}")
                ThermalHeadroom.toPercent(l.headroom ?: Float.NaN)?.let { append(" · Headroom used $it%") }
                if (l.thermal.isThrottling) append(" · Throttling likely")
            },
        )

        MetricCard(
            title = "Memory",
            value = "%.1f GB free".format(l.memory.availBytes / 1_073_741_824.0),
            subtitle = "of %.1f GB%s".format(
                l.memory.totalBytes / 1_073_741_824.0,
                if (l.memory.lowMemory) " · System is low on memory" else "",
            ),
        ) {
            LinearProgressIndicator(
                progress = { l.memory.usedFraction },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        val cpu = l.cpu
        if (cpu != null) {
            MetricCard(
                title = "CPU frequency",
                value = cpu.averageCurrentKhz?.let { "%.2f GHz avg".format(it / 1_000_000.0) } ?: "--",
                subtitle = cpu.perCoreKhz.mapIndexed { i, f -> "C$i ${f?.div(1000) ?: "-"}" }.joinToString("  ") + " MHz",
            )
        }
    }
}
