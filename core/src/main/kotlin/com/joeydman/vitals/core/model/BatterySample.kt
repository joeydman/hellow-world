package com.joeydman.vitals.core.model

/**
 * One raw battery reading as captured from BatteryManager / ACTION_BATTERY_CHANGED.
 * Raw values are stored unmodified so normalisation heuristics can be improved later
 * without losing history.
 */
data class BatterySample(
    val timestampMs: Long,
    val levelPercent: Int,
    /** BATTERY_PROPERTY_CHARGE_COUNTER, documented as µAh but some OEMs report mAh. */
    val chargeCounterRaw: Long?,
    /** BATTERY_PROPERTY_CURRENT_NOW, documented as µA (positive = charging); OEMs vary. */
    val currentNowRaw: Long?,
    val voltageMv: Int?,
    val temperatureDeciC: Int?,
    val status: ChargeStatus,
    val plugType: PlugType,
    /** BatteryManager.EXTRA_CYCLE_COUNT, API 34+. Null when unsupported. */
    val cycleCount: Int?,
)

enum class ChargeStatus { CHARGING, DISCHARGING, NOT_CHARGING, FULL, UNKNOWN }

enum class PlugType { NONE, AC, USB, WIRELESS, DOCK }
