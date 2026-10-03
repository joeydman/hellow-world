package com.joeydman.vitals.sensors

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.joeydman.vitals.core.usage.ForegroundTimeCalculator.Event
import com.joeydman.vitals.core.usage.ForegroundTimeCalculator.Type

class UsageReader(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)

    fun hasPermission(): Boolean =
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun events(startMs: Long, endMs: Long): List<Event> {
        val result = mutableListOf<Event>()
        val events = usm.queryEvents(startMs, endMs) ?: return result
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val type = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> Type.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> Type.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> Type.SCREEN_OFF
                UsageEvents.Event.DEVICE_SHUTDOWN -> Type.DEVICE_SHUTDOWN
                else -> null
            } ?: continue
            result += Event(e.packageName, type, e.timeStamp)
        }
        return result
    }

    fun label(packageName: String): String = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        pm.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)
}
