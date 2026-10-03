package com.joeydman.vitals.core.model

/** Generic time-series point used for RAM, thermal headroom, CPU frequency etc. */
data class MetricPoint(val timestampMs: Long, val value: Double)

data class AggregatedPoint(
    val bucketStartMs: Long,
    val min: Double,
    val avg: Double,
    val max: Double,
    val count: Int,
)
