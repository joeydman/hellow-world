package com.joeydman.vitals.core.battery

/**
 * Health is reported against design capacity. Android does not expose design capacity
 * publicly, so it comes from [DeviceProfiles] or a user override in settings.
 */
data class BatteryHealth(
    val estimatedCapacityMah: Double,
    val designCapacityMah: Int,
) {
    val healthPercent: Double get() = (estimatedCapacityMah / designCapacityMah * 100.0).coerceIn(0.0, 120.0)

    val grade: Grade
        get() = when {
            healthPercent >= 90 -> Grade.EXCELLENT
            healthPercent >= 80 -> Grade.GOOD
            healthPercent >= 70 -> Grade.FAIR
            else -> Grade.POOR
        }

    enum class Grade { EXCELLENT, GOOD, FAIR, POOR }
}

/**
 * Least-squares trend of capacity over time, used for "you are losing X% per month".
 * Needs a spread of data: returns null until at least [minSpanDays] are covered.
 */
object DegradationTrend {

    data class Trend(val mahPerMonth: Double, val percentPerMonth: Double)

    private const val MONTH_MS = 30.0 * 24 * 60 * 60 * 1000

    fun compute(
        estimates: List<CapacityEstimator.Estimate>,
        designCapacityMah: Int,
        minSpanDays: Int = 14,
    ): Trend? {
        if (estimates.size < 3) return null
        val t0 = estimates.minOf { it.timestampMs }
        val span = estimates.maxOf { it.timestampMs } - t0
        if (span < minSpanDays * 24L * 60 * 60 * 1000) return null
        val xs = estimates.map { (it.timestampMs - t0) / MONTH_MS }
        val ys = estimates.map { it.capacityMah }
        val xMean = xs.average()
        val yMean = ys.average()
        val num = xs.indices.sumOf { (xs[it] - xMean) * (ys[it] - yMean) }
        val den = xs.sumOf { (it - xMean) * (it - xMean) }
        if (den == 0.0) return null
        val slope = num / den
        return Trend(mahPerMonth = slope, percentPerMonth = slope / designCapacityMah * 100.0)
    }
}

/** Known design capacities. Users can override in settings for unlisted models. */
object DeviceProfiles {
    private val designCapacityMah = mapOf(
        // Galaxy S25 family. Model prefixes cover regional variants (e.g. SM-S931B, SM-S931U).
        "SM-S931" to 4000, // S25
        "SM-S936" to 4900, // S25+
        "SM-S938" to 5000, // S25 Ultra
    )

    fun designCapacityFor(model: String): Int? =
        designCapacityMah.entries.firstOrNull { model.startsWith(it.key) }?.value
}
