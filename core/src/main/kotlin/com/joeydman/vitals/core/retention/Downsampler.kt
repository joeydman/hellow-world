package com.joeydman.vitals.core.retention

import com.joeydman.vitals.core.model.AggregatedPoint
import com.joeydman.vitals.core.model.MetricPoint

/**
 * Retention policy: keep raw samples for a short window, then roll them into
 * hourly then daily min/avg/max buckets so years of history stay small on disk.
 */
object Downsampler {

    const val HOUR_MS = 60 * 60 * 1000L
    const val DAY_MS = 24 * HOUR_MS

    fun aggregate(points: List<MetricPoint>, bucketMs: Long): List<AggregatedPoint> {
        require(bucketMs > 0)
        return points
            .groupBy { Math.floorDiv(it.timestampMs, bucketMs) * bucketMs }
            .toSortedMap()
            .map { (start, pts) ->
                val values = pts.map { it.value }
                AggregatedPoint(start, values.min(), values.average(), values.max(), values.size)
            }
    }

    data class Policy(
        val rawRetentionMs: Long = 7 * DAY_MS,
        val hourlyRetentionMs: Long = 180 * DAY_MS,
        // Daily aggregates are kept forever: that is the degradation history.
    )
}
