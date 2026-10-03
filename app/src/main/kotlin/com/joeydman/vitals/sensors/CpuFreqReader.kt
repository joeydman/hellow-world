package com.joeydman.vitals.sensors

import java.io.File

/**
 * Per-core current frequency from sysfs. Device-wide CPU utilisation (/proc/stat) has been
 * blocked for apps since Android 8, so frequency is the best public CPU-load signal.
 * Returns null when SELinux blocks these files, and the UI hides the CPU card.
 */
class CpuFreqReader {
    data class CpuFreq(val perCoreKhz: List<Long?>, val maxPerCoreKhz: List<Long?>) {
        val averageCurrentKhz: Long? get() = perCoreKhz.filterNotNull().takeIf { it.isNotEmpty() }?.average()?.toLong()
        val maxCurrentKhz: Long? get() = perCoreKhz.filterNotNull().maxOrNull()
    }

    fun read(): CpuFreq? {
        val cores = (0 until Runtime.getRuntime().availableProcessors())
        val current = cores.map { readLong("/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq") }
        if (current.all { it == null }) return null
        val max = cores.map { readLong("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq") }
        return CpuFreq(current, max)
    }

    private fun readLong(path: String): Long? =
        runCatching { File(path).readText().trim().toLong() }.getOrNull()
}
