package com.joeydman.vitals.core.battery

import kotlin.math.abs

/**
 * Android documents CHARGE_COUNTER in µAh and CURRENT_NOW in µA, but several OEM
 * kernels (including some Samsung builds) report mAh / mA, and some invert the sign.
 * These helpers infer the unit from plausible physical ranges for a phone battery.
 */
object UnitNormalizer {

    /** Phones ship with roughly 1,500 to 7,000 mAh. Wider band tolerates estimation noise. */
    private val PLAUSIBLE_FULL_MAH = 800.0..12_000.0

    /**
     * Returns the charge counter in mAh, or null when the value is implausible.
     * Uses the implied full capacity (counter / level) to choose between µAh and mAh.
     */
    fun chargeCounterToMah(raw: Long, levelPercent: Int): Double? {
        if (raw <= 0 || levelPercent !in 1..100) return null
        val asMicro = raw / 1000.0
        val impliedFullMicro = asMicro * 100.0 / levelPercent
        if (impliedFullMicro in PLAUSIBLE_FULL_MAH) return asMicro
        val impliedFullMilli = raw * 100.0 / levelPercent
        if (impliedFullMilli in PLAUSIBLE_FULL_MAH) return raw.toDouble()
        return null
    }

    /**
     * Learns the CURRENT_NOW unit from a set of raw readings. A phone draws
     * tens to thousands of mA, so a median magnitude above 20,000 can only be µA.
     */
    fun detectCurrentUnit(rawReadings: List<Long>): CurrentUnit {
        val nonZero = rawReadings.filter { it != 0L }.map { abs(it) }.sorted()
        if (nonZero.isEmpty()) return CurrentUnit.MICRO_AMPS
        val median = nonZero[nonZero.size / 2]
        return if (median > 20_000) CurrentUnit.MICRO_AMPS else CurrentUnit.MILLI_AMPS
    }

    /**
     * Learns whether the sign is inverted by looking at readings taken while the
     * device reported it was charging. Per the docs, charging should be positive.
     */
    fun detectSignInverted(chargingReadings: List<Long>): Boolean {
        val nonZero = chargingReadings.filter { it != 0L }
        if (nonZero.size < 3) return false
        val negative = nonZero.count { it < 0 }
        return negative > nonZero.size / 2
    }

    fun currentToMa(raw: Long, unit: CurrentUnit, signInverted: Boolean): Double {
        val ma = when (unit) {
            CurrentUnit.MICRO_AMPS -> raw / 1000.0
            CurrentUnit.MILLI_AMPS -> raw.toDouble()
        }
        return if (signInverted) -ma else ma
    }
}

enum class CurrentUnit { MICRO_AMPS, MILLI_AMPS }
