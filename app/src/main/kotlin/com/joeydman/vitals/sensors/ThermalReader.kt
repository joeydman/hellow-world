package com.joeydman.vitals.sensors

import android.content.Context
import android.os.PowerManager
import com.joeydman.vitals.core.thermal.ThermalState

/**
 * Uses the public PowerManager thermal APIs. Raw thermal zones under /sys/class/thermal
 * are SELinux-blocked for apps on current Samsung firmware, so we do not rely on them.
 */
class ThermalReader(context: Context) {
    private val pm = context.getSystemService(PowerManager::class.java)

    fun status(): ThermalState = ThermalState.fromPlatform(pm.currentThermalStatus)

    /**
     * Forecast headroom 0 seconds ahead. The platform rate-limits this call to roughly
     * once per second, so UI polling must stay at or above that interval.
     */
    fun headroom(): Float? = pm.getThermalHeadroom(0).takeIf { !it.isNaN() }
}
