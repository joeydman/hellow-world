package com.joeydman.vitals.sensors

import android.app.ActivityManager
import android.content.Context

/**
 * System-wide RAM only. Since Android 10, apps cannot read other apps' memory use,
 * so per-app RAM is intentionally out of scope (see docs/SCOPE.md).
 */
class MemoryReader(context: Context) {
    private val am = context.getSystemService(ActivityManager::class.java)

    data class Memory(val availBytes: Long, val totalBytes: Long, val lowMemory: Boolean, val thresholdBytes: Long) {
        val usedFraction: Float get() = if (totalBytes == 0L) 0f else 1f - availBytes.toFloat() / totalBytes
    }

    fun read(): Memory {
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return Memory(info.availMem, info.totalMem, info.lowMemory, info.threshold)
    }
}
