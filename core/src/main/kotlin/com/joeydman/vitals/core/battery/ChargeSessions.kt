package com.joeydman.vitals.core.battery

import com.joeydman.vitals.core.model.BatterySample
import com.joeydman.vitals.core.model.ChargeStatus
import com.joeydman.vitals.core.model.PlugType

/** One continuous period of being plugged in, summarised from raw battery samples. */
data class ChargeSession(
    val startMs: Long,
    val endMs: Long,
    val ongoing: Boolean,
    val plugType: PlugType,
    val startLevel: Int,
    val endLevel: Int,
    /** Charge put into the battery according to the fuel gauge, null if the counter is unusable. */
    val addedMah: Double?,
    val avgCurrentMa: Double?,
    val peakCurrentMa: Double?,
    /** 90th percentile of battery-side power, so one spike does not decide the speed. */
    val sustainedPowerW: Double?,
    val avgTempC: Double?,
    val peakTempC: Double?,
    /** From plug-in to first reading at or above 80%. Null if it started at 80%+ or never got there. */
    val timeTo80Ms: Long?,
    /** Time spent plugged in at 100%, which is the main avoidable source of wear. */
    val heldAtFullMs: Long,
    val sampleCount: Int,
    val speed: ChargeSpeed,
) {
    val durationMs: Long get() = endMs - startMs
    val levelGained: Int get() = endLevel - startLevel
    val ratePercentPerHour: Double?
        get() = if (durationMs <= 0 || levelGained <= 0) null else levelGained / (durationMs / 3_600_000.0)
    val wasHot: Boolean get() = (peakTempC ?: 0.0) >= HOT_CHARGE_C

    companion object {
        /** Lithium-ion wear accelerates noticeably when charged above about 40 °C. */
        const val HOT_CHARGE_C = 40.0
    }
}

/**
 * Speed buckets for power measured at the battery. These read lower than the adapter
 * rating (a 25 W "Super fast" charger lands around 15 to 22 W here), so the thresholds
 * are deliberately conservative. Tune against real S25 sessions.
 */
enum class ChargeSpeed(val label: String) {
    WIRELESS("Wireless"),
    SLOW("Slow"),
    NORMAL("Normal"),
    FAST("Fast"),
    SUPER_FAST("Super fast"),
    UNKNOWN("Unknown"),
}

object ChargeSessionAnalyzer {

    /** Samples further apart than this start a new session (sampling is 1 min while plugged). */
    const val DEFAULT_MAX_GAP_MS = 20 * 60 * 1000L

    fun detect(
        samples: List<BatterySample>,
        nowMs: Long,
        maxGapMs: Long = DEFAULT_MAX_GAP_MS,
    ): List<ChargeSession> {
        val sorted = samples.sortedBy { it.timestampMs }
        if (sorted.isEmpty()) return emptyList()

        // Learn current units from the whole window: more readings, more reliable inference.
        val unit = UnitNormalizer.detectCurrentUnit(sorted.mapNotNull { it.currentNowRaw })
        val inverted = UnitNormalizer.detectSignInverted(
            sorted.filter { it.status == ChargeStatus.CHARGING }.mapNotNull { it.currentNowRaw },
        )

        val groups = mutableListOf<List<BatterySample>>()
        var current = mutableListOf<BatterySample>()
        for (s in sorted) {
            val plugged = s.plugType != PlugType.NONE
            val contiguous = current.isNotEmpty() && s.timestampMs - current.last().timestampMs <= maxGapMs
            when {
                plugged && (current.isEmpty() || contiguous) -> current.add(s)
                else -> {
                    if (current.size >= 2) groups.add(current)
                    current = if (plugged) mutableListOf(s) else mutableListOf()
                }
            }
        }
        if (current.size >= 2) groups.add(current)

        val last = sorted.last()
        return groups.map { g ->
            val ongoing = g.last() === last && nowMs - last.timestampMs <= maxGapMs
            summarise(g, ongoing, unit, inverted)
        }
    }

    private fun summarise(
        g: List<BatterySample>,
        ongoing: Boolean,
        unit: CurrentUnit,
        inverted: Boolean,
    ): ChargeSession {
        val first = g.first()
        val last = g.last()
        val plug = g.groupingBy { it.plugType }.eachCount().maxByOrNull { it.value }!!.key

        val currents = g.mapNotNull { s -> s.currentNowRaw?.let { UnitNormalizer.currentToMa(it, unit, inverted) } }
            .filter { it > 0 }
        val powers = g.mapNotNull { s ->
            val ma = s.currentNowRaw?.let { UnitNormalizer.currentToMa(it, unit, inverted) } ?: return@mapNotNull null
            val mv = s.voltageMv ?: return@mapNotNull null
            if (ma <= 0) null else ma * mv / 1_000_000.0
        }
        val temps = g.mapNotNull { it.temperatureDeciC?.div(10.0) }

        val c0 = first.chargeCounterRaw?.let { UnitNormalizer.chargeCounterToMah(it, first.levelPercent) }
        val c1 = last.chargeCounterRaw?.let { UnitNormalizer.chargeCounterToMah(it, last.levelPercent) }
        val added = if (c0 != null && c1 != null && c1 > c0) c1 - c0 else null

        val timeTo80 = if (first.levelPercent >= 80) {
            null
        } else {
            g.firstOrNull { it.levelPercent >= 80 }?.let { it.timestampMs - first.timestampMs }
        }

        val heldAtFull = g.zipWithNext().sumOf { (a, b) ->
            if (a.levelPercent >= 100 && b.levelPercent >= 100) b.timestampMs - a.timestampMs else 0L
        }

        val sustained = percentile(powers, 0.9)
        return ChargeSession(
            startMs = first.timestampMs,
            endMs = last.timestampMs,
            ongoing = ongoing,
            plugType = plug,
            startLevel = first.levelPercent,
            endLevel = last.levelPercent,
            addedMah = added,
            avgCurrentMa = currents.takeIf { it.isNotEmpty() }?.average(),
            peakCurrentMa = currents.maxOrNull(),
            sustainedPowerW = sustained,
            avgTempC = temps.takeIf { it.isNotEmpty() }?.average(),
            peakTempC = temps.maxOrNull(),
            timeTo80Ms = timeTo80,
            heldAtFullMs = heldAtFull,
            sampleCount = g.size,
            speed = classify(plug, sustained),
        )
    }

    fun classify(plug: PlugType, sustainedPowerW: Double?): ChargeSpeed = when {
        plug == PlugType.WIRELESS -> ChargeSpeed.WIRELESS
        sustainedPowerW == null -> ChargeSpeed.UNKNOWN
        sustainedPowerW >= 15.0 -> ChargeSpeed.SUPER_FAST
        sustainedPowerW >= 9.0 -> ChargeSpeed.FAST
        sustainedPowerW >= 4.5 -> ChargeSpeed.NORMAL
        else -> ChargeSpeed.SLOW
    }

    internal fun percentile(values: List<Double>, p: Double): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[(p * (sorted.size - 1)).toInt()]
    }
}

/** Battery-care view across many sessions: the habits that drive long-term wear. */
object ChargeHabits {

    data class Summary(
        val sessions: Int,
        val avgStartLevel: Double,
        val avgEndLevel: Double,
        /** Share of sessions ending at 95% or more. */
        val fullChargeShare: Double,
        /** Share of sessions starting at 20% or less. */
        val deepDischargeShare: Double,
        /** Share of sessions peaking at 40 °C or more. */
        val hotShare: Double,
        val avgHeldAtFullMs: Long,
    )

    fun summarise(sessions: List<ChargeSession>): Summary? {
        val done = sessions.filter { !it.ongoing }
        if (done.isEmpty()) return null
        val n = done.size.toDouble()
        return Summary(
            sessions = done.size,
            avgStartLevel = done.map { it.startLevel }.average(),
            avgEndLevel = done.map { it.endLevel }.average(),
            fullChargeShare = done.count { it.endLevel >= 95 } / n,
            deepDischargeShare = done.count { it.startLevel <= 20 } / n,
            hotShare = done.count { it.wasHot } / n,
            avgHeldAtFullMs = done.map { it.heldAtFullMs }.average().toLong(),
        )
    }
}
