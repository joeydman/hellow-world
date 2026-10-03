package com.joeydman.vitals.sensors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.joeydman.vitals.core.model.BatterySample
import com.joeydman.vitals.core.model.ChargeStatus
import com.joeydman.vitals.core.model.PlugType

class BatteryReader(private val context: Context) {
    private val bm = context.getSystemService(BatteryManager::class.java)

    /** Reads the sticky ACTION_BATTERY_CHANGED broadcast; no receiver stays registered. */
    fun read(nowMs: Long = System.currentTimeMillis()): BatterySample? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return null

        return BatterySample(
            timestampMs = nowMs,
            levelPercent = level * 100 / scale,
            chargeCounterRaw = bm.intProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.toLong(),
            currentNowRaw = bm.intProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.toLong(),
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 },
            temperatureDeciC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
            status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.CHARGING
                BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeStatus.DISCHARGING
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeStatus.NOT_CHARGING
                BatteryManager.BATTERY_STATUS_FULL -> ChargeStatus.FULL
                else -> ChargeStatus.UNKNOWN
            },
            plugType = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
                BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
                BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
                PLUGGED_DOCK -> PlugType.DOCK
                else -> PlugType.NONE
            },
            cycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 }
            } else {
                null
            },
        )
    }

    private companion object {
        /** BatteryManager.BATTERY_PLUGGED_DOCK, inlined because the constant is API 33+. */
        const val PLUGGED_DOCK = 8
    }

    /** BatteryManager returns Int.MIN_VALUE (or 0 on some kernels) when a property is unsupported. */
    private fun BatteryManager.intProperty(id: Int): Int? =
        getIntProperty(id).takeIf { it != Int.MIN_VALUE && it != 0 }
}
