package com.joeydman.vitals.core.thermal

/** Mirrors PowerManager.THERMAL_STATUS_* (API 29+). Values match the platform ints. */
enum class ThermalState(val platformValue: Int, val label: String) {
    NONE(0, "Normal"),
    LIGHT(1, "Light"),
    MODERATE(2, "Moderate"),
    SEVERE(3, "Severe"),
    CRITICAL(4, "Critical"),
    EMERGENCY(5, "Emergency"),
    SHUTDOWN(6, "Shutdown");

    val isThrottling: Boolean get() = platformValue >= MODERATE.platformValue

    companion object {
        fun fromPlatform(value: Int): ThermalState = entries.firstOrNull { it.platformValue == value } ?: NONE
    }
}

/**
 * PowerManager.getThermalHeadroom returns 0.0 (cool) to 1.0 (severe throttling), NaN
 * when unsupported, and must not be polled more than about once per second.
 */
object ThermalHeadroom {
    fun toPercent(headroom: Float): Int? =
        if (headroom.isNaN() || headroom < 0f) null else (headroom.coerceAtMost(1.5f) * 100).toInt()
}
