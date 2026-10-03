package com.joeydman.vitals.core.usage

/**
 * Computes per-app foreground time from UsageEvents. UsageStatsManager.queryUsageStats
 * buckets are coarse and known to double count around bucket edges, so we derive
 * durations from the raw resume/pause event stream instead.
 */
object ForegroundTimeCalculator {

    enum class Type { RESUMED, PAUSED, SCREEN_OFF, DEVICE_SHUTDOWN }

    data class Event(val packageName: String, val type: Type, val timestampMs: Long)

    data class AppUsage(val packageName: String, val foregroundMs: Long, val launches: Int)

    fun compute(events: List<Event>, windowStartMs: Long, windowEndMs: Long): List<AppUsage> {
        val totals = HashMap<String, Long>()
        val launches = HashMap<String, Int>()
        val openSince = HashMap<String, Long>()

        fun close(pkg: String, at: Long) {
            val start = openSince.remove(pkg) ?: return
            val s = maxOf(start, windowStartMs)
            val e = minOf(at, windowEndMs)
            if (e > s) totals[pkg] = (totals[pkg] ?: 0L) + (e - s)
        }

        for (ev in events.sortedBy { it.timestampMs }) {
            when (ev.type) {
                Type.RESUMED -> {
                    if (ev.packageName !in openSince) {
                        openSince[ev.packageName] = ev.timestampMs
                        if (ev.timestampMs in windowStartMs until windowEndMs) {
                            launches[ev.packageName] = (launches[ev.packageName] ?: 0) + 1
                        }
                    }
                }
                Type.PAUSED -> close(ev.packageName, ev.timestampMs)
                // Screen off / shutdown ends every open session even if no pause was logged.
                Type.SCREEN_OFF, Type.DEVICE_SHUTDOWN -> openSince.keys.toList().forEach { close(it, ev.timestampMs) }
            }
        }
        // Apps still open at the end of the window count up to the window end.
        openSince.keys.toList().forEach { close(it, windowEndMs) }

        return (totals.keys + launches.keys).distinct()
            .map { AppUsage(it, totals[it] ?: 0L, launches[it] ?: 0) }
            .filter { it.foregroundMs > 0 || it.launches > 0 }
            .sortedByDescending { it.foregroundMs }
    }
}
