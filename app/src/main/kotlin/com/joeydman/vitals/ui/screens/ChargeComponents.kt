package com.joeydman.vitals.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.joeydman.vitals.core.battery.ChargeHabits
import com.joeydman.vitals.core.battery.ChargeSession
import com.joeydman.vitals.core.battery.ChargeSpeed
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val sessionTimeFormat = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")

fun formatSessionStart(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(sessionTimeFormat)

@Composable
fun ChargeSessionRow(session: ChargeSession, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text("${session.startLevel}% → ${session.endLevel}%" + if (session.ongoing) "  (charging)" else "")
        },
        supportingContent = {
            Text(
                listOfNotNull(
                    formatSessionStart(session.startMs),
                    formatDuration(session.durationMs),
                    session.speed.takeIf { it != ChargeSpeed.UNKNOWN }?.label,
                ).joinToString(" · "),
            )
        },
        trailingContent = {
            session.peakTempC?.let {
                Text(
                    "%.0f°C".format(it),
                    color = if (session.wasHot) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** Turns habit shares into a short, specific battery-care note. */
fun habitsAdvice(h: ChargeHabits.Summary): String {
    val notes = mutableListOf<String>()
    if (h.fullChargeShare > 0.5) notes += "Most charges end near 100%. Turning on Protect battery (80% limit) slows wear."
    if (h.avgHeldAtFullMs > 60 * 60_000L) notes += "The phone sits at 100% for over an hour on average, often overnight."
    if (h.hotShare > 0.25) notes += "Over a quarter of charges hit 40°C. Avoid gaming or a case-on wireless charge while charging."
    if (h.deepDischargeShare > 0.3) notes += "You often start below 20%. Topping up earlier is gentler on the cells."
    return notes.joinToString("\n").ifEmpty { "Charging habits look battery-friendly." }
}
