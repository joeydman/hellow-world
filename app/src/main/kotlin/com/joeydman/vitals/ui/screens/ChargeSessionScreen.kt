package com.joeydman.vitals.ui.screens

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
import com.joeydman.vitals.core.battery.ChargeSession
import com.joeydman.vitals.core.battery.UnitNormalizer
import com.joeydman.vitals.core.model.ChargeStatus
import com.joeydman.vitals.data.MetricsRepository

@Composable
fun ChargeSessionScreen(repo: MetricsRepository, startMs: Long) {
    val session by remember(startMs) { repo.chargeSession(startMs) }.collectAsState(initial = null)
    val s = session ?: return
    // Keyed on endMs so an ongoing session's charts keep extending.
    val raw by remember(startMs, s.endMs) { repo.batterySamplesBetween(s.startMs, s.endMs) }
        .collectAsState(initial = emptyList())
    val samples = raw.map { it.toModel() }
    val unit = UnitNormalizer.detectCurrentUnit(samples.mapNotNull { it.currentNowRaw })
    val inverted = UnitNormalizer.detectSignInverted(
        samples.filter { it.status == ChargeStatus.CHARGING }.mapNotNull { it.currentNowRaw },
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(formatSessionStart(s.startMs), style = MaterialTheme.typography.headlineSmall)
        Text(
            listOf(s.plugType.name.lowercase().replaceFirstChar { it.uppercase() }, s.speed.label)
                .joinToString(" · ") + if (s.ongoing) " · charging now" else "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MetricCard(
            title = "Level",
            value = "${s.startLevel}% → ${s.endLevel}%",
            subtitle = summaryLine(s),
        ) { Sparkline(samples.map { it.levelPercent.toDouble() }) }

        MetricCard(
            title = "Charging current",
            value = s.avgCurrentMa?.let { "%.0f mA avg".format(it) } ?: "--",
            subtitle = listOfNotNull(
                s.peakCurrentMa?.let { "Peak %.0f mA".format(it) },
                s.sustainedPowerW?.let { "Sustained %.1f W at the battery".format(it) },
            ).joinToString(" · "),
        ) {
            Sparkline(samples.mapNotNull { it.currentNowRaw?.let { r -> UnitNormalizer.currentToMa(r, unit, inverted) } })
        }

        MetricCard(
            title = "Battery temperature",
            value = s.peakTempC?.let { "%.1f°C peak".format(it) } ?: "--",
            subtitle = s.avgTempC?.let {
                "Average %.1f°C".format(it) + if (s.wasHot) " · above 40°C speeds up wear" else ""
            },
        ) { Sparkline(samples.mapNotNull { it.temperatureDeciC?.div(10.0) }) }

        if (raw.isEmpty()) {
            Text(
                "Detailed readings are kept for 30 days. The summary above is kept permanently.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun summaryLine(s: ChargeSession): String = listOfNotNull(
    "+${s.levelGained}% in ${formatDuration(s.durationMs)}",
    s.ratePercentPerHour?.let { "%.0f%%/h".format(it) },
    s.addedMah?.let { "%.0f mAh added".format(it) },
    s.timeTo80Ms?.let { "80% after ${formatDuration(it)}" },
    s.heldAtFullMs.takeIf { it >= 60_000 }?.let { "${formatDuration(it)} held at 100%" },
).joinToString(" · ")
