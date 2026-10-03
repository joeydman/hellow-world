package com.joeydman.vitals.core.battery

import com.joeydman.vitals.core.model.BatterySample
import com.joeydman.vitals.core.model.ChargeStatus

/**
 * Estimates the battery's current full-charge capacity (mAh) without root.
 *
 * Two independent methods, combined by median to reject outliers:
 *  1. Fuel gauge: charge_counter / level. Cheap, available on every sample, but
 *     reflects the gauge's own (possibly stale) learned capacity.
 *  2. Charge delta: during one continuous charging run, Δcharge / Δlevel. Closer to
 *     what AccuBattery-style apps measure; needs at least [minDeltaPercent] of charge.
 */
class CapacityEstimator(
    private val minDeltaPercent: Int = 20,
    private val minFuelGaugeLevel: Int = 15,
    /** Gap between samples that splits one charging run into two. */
    private val maxGapMs: Long = 45 * 60 * 1000L,
) {

    data class Estimate(val timestampMs: Long, val capacityMah: Double, val method: Method)

    enum class Method { FUEL_GAUGE, CHARGE_DELTA }

    fun fuelGaugeEstimates(samples: List<BatterySample>): List<Estimate> =
        samples.mapNotNull { s ->
            val raw = s.chargeCounterRaw ?: return@mapNotNull null
            if (s.levelPercent < minFuelGaugeLevel) return@mapNotNull null
            val mah = UnitNormalizer.chargeCounterToMah(raw, s.levelPercent) ?: return@mapNotNull null
            Estimate(s.timestampMs, mah * 100.0 / s.levelPercent, Method.FUEL_GAUGE)
        }

    fun chargeDeltaEstimates(samples: List<BatterySample>): List<Estimate> =
        chargingRuns(samples).mapNotNull { run ->
            val first = run.first()
            val last = run.last()
            val dLevel = last.levelPercent - first.levelPercent
            if (dLevel < minDeltaPercent) return@mapNotNull null
            val c0 = first.chargeCounterRaw?.let { UnitNormalizer.chargeCounterToMah(it, first.levelPercent) }
            val c1 = last.chargeCounterRaw?.let { UnitNormalizer.chargeCounterToMah(it, last.levelPercent) }
            if (c0 == null || c1 == null || c1 <= c0) return@mapNotNull null
            Estimate(last.timestampMs, (c1 - c0) * 100.0 / dLevel, Method.CHARGE_DELTA)
        }

    /** Median of all estimates, or null if there is not enough data. */
    fun bestEstimateMah(samples: List<BatterySample>): Double? {
        val all = (fuelGaugeEstimates(samples) + chargeDeltaEstimates(samples)).map { it.capacityMah }
        return median(all)
    }

    internal fun chargingRuns(samples: List<BatterySample>): List<List<BatterySample>> {
        val runs = mutableListOf<List<BatterySample>>()
        var current = mutableListOf<BatterySample>()
        for (s in samples.sortedBy { it.timestampMs }) {
            val charging = s.status == ChargeStatus.CHARGING
            val contiguous = current.isNotEmpty() && s.timestampMs - current.last().timestampMs <= maxGapMs
            if (charging && (current.isEmpty() || contiguous)) {
                current.add(s)
            } else {
                if (current.size >= 2) runs.add(current)
                current = if (charging) mutableListOf(s) else mutableListOf()
            }
        }
        if (current.size >= 2) runs.add(current)
        return runs
    }

    companion object {
        fun median(values: List<Double>): Double? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }
}
