package com.joeydman.vitals.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.joeydman.vitals.container
import com.joeydman.vitals.ui.MainActivity

/** What a widget instance shows. Chosen in [WidgetConfigActivity], changeable by long-press > Settings. */
enum class WidgetLayout(val title: String) {
    OVERVIEW("Overview"),
    BATTERY("Battery"),
    THERMAL("Temperature"),
    MEMORY("Memory"),
}

data class WidgetSnapshot(
    val levelPercent: Int?,
    val charging: Boolean,
    val tempC: Double?,
    val thermalLabel: String,
    val healthPercent: Int?,
    val ramUsedFraction: Float,
    val ramAvailGb: Double,
)

class VitalsWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        provideContent {
            val layout = currentState<Preferences>()[LAYOUT_KEY]
                ?.let { runCatching { WidgetLayout.valueOf(it) }.getOrNull() }
                ?: WidgetLayout.OVERVIEW
            GlanceTheme {
                WidgetBody(snapshot, layout)
            }
        }
    }

    private suspend fun loadSnapshot(context: Context): WidgetSnapshot {
        val repo = context.container.repository
        val battery = repo.battery.read()
        val mem = repo.memory.read()
        return WidgetSnapshot(
            levelPercent = battery?.levelPercent,
            charging = battery?.status == com.joeydman.vitals.core.model.ChargeStatus.CHARGING,
            tempC = battery?.temperatureDeciC?.div(10.0),
            thermalLabel = repo.thermal.status().label,
            healthPercent = repo.latestHealth()?.healthPercent?.toInt(),
            ramUsedFraction = mem.usedFraction,
            ramAvailGb = mem.availBytes / 1_073_741_824.0,
        )
    }

    companion object {
        val LAYOUT_KEY = stringPreferencesKey("widget_layout")
        private val SMALL = DpSize(110.dp, 40.dp)
        private val MEDIUM = DpSize(250.dp, 110.dp)

        @Composable
        private fun WidgetBody(s: WidgetSnapshot, layout: WidgetLayout) {
            val compact = LocalSize.current.height < MEDIUM.height
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground)
                    .cornerRadius(20.dp)
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (layout) {
                    WidgetLayout.OVERVIEW -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Metric(batteryText(s), "Battery")
                            Spacer(GlanceModifier.width(16.dp))
                            Metric(s.tempC?.let { "%.1f°".format(it) } ?: "--", s.thermalLabel)
                            if (!compact) {
                                Spacer(GlanceModifier.width(16.dp))
                                Metric("${(s.ramUsedFraction * 100).toInt()}%", "RAM used")
                            }
                        }
                    }
                    WidgetLayout.BATTERY -> {
                        Metric(batteryText(s), s.healthPercent?.let { "Health $it%" } ?: "Health: learning")
                        if (!compact) Bar((s.levelPercent ?: 0) / 100f)
                    }
                    WidgetLayout.THERMAL -> Metric(s.tempC?.let { "%.1f°C".format(it) } ?: "--", "Thermal: ${s.thermalLabel}")
                    WidgetLayout.MEMORY -> {
                        Metric("%.1f GB free".format(s.ramAvailGb), "${(s.ramUsedFraction * 100).toInt()}% used")
                        if (!compact) Bar(s.ramUsedFraction)
                    }
                }
            }
        }

        private fun batteryText(s: WidgetSnapshot) =
            (s.levelPercent?.let { "$it%" } ?: "--") + if (s.charging) " ⚡" else ""

        @Composable
        private fun Metric(value: String, label: String) {
            Column {
                Text(
                    value,
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
                )
                Text(label, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
            }
        }

        @Composable
        private fun Bar(progress: Float) {
            Spacer(GlanceModifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth(),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
        }
    }
}

class VitalsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VitalsWidget()
}
