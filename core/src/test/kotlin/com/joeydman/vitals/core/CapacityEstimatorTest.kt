package com.joeydman.vitals.core

import com.joeydman.vitals.core.battery.BatteryHealth
import com.joeydman.vitals.core.battery.CapacityEstimator
import com.joeydman.vitals.core.battery.DegradationTrend
import com.joeydman.vitals.core.battery.DeviceProfiles
import com.joeydman.vitals.core.model.BatterySample
import com.joeydman.vitals.core.model.ChargeStatus
import com.joeydman.vitals.core.model.PlugType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CapacityEstimatorTest {
    private val min = 60_000L

    private fun sample(t: Long, level: Int, fullMah: Double, status: ChargeStatus) = BatterySample(
        timestampMs = t, levelPercent = level,
        chargeCounterRaw = (fullMah * level / 100.0 * 1000).toLong(),
        currentNowRaw = null, voltageMv = null, temperatureDeciC = null,
        status = status, plugType = PlugType.AC, cycleCount = null,
    )

    @Test fun fuelGaugeEstimateRecoversFullCapacity() {
        val est = CapacityEstimator().fuelGaugeEstimates(listOf(sample(0, 60, 3800.0, ChargeStatus.DISCHARGING)))
        assertEquals(3800.0, est.single().capacityMah, 1.0)
    }

    @Test fun chargeDeltaNeedsEnoughCharge() {
        val e = CapacityEstimator()
        val small = listOf(sample(0, 40, 3800.0, ChargeStatus.CHARGING), sample(15 * min, 50, 3800.0, ChargeStatus.CHARGING))
        assertEquals(0, e.chargeDeltaEstimates(small).size)
        val big = (0..6).map { sample(it * 15 * min, 20 + it * 10, 3800.0, ChargeStatus.CHARGING) }
        assertEquals(3800.0, e.chargeDeltaEstimates(big).single().capacityMah, 1.0)
    }

    @Test fun gapSplitsChargingRuns() {
        val s = listOf(
            sample(0, 20, 3800.0, ChargeStatus.CHARGING),
            sample(15 * min, 30, 3800.0, ChargeStatus.CHARGING),
            sample(5 * 60 * min, 40, 3800.0, ChargeStatus.CHARGING),
            sample(5 * 60 * min + 15 * min, 50, 3800.0, ChargeStatus.CHARGING),
        )
        assertEquals(2, CapacityEstimator().chargingRuns(s).size)
    }

    @Test fun healthAgainstS25DesignCapacity() {
        val design = DeviceProfiles.designCapacityFor("SM-S931B")!!
        val health = BatteryHealth(3600.0, design)
        assertEquals(90.0, health.healthPercent, 0.01)
        assertEquals(BatteryHealth.Grade.EXCELLENT, health.grade)
        assertNull(DeviceProfiles.designCapacityFor("Pixel 9"))
    }

    @Test fun degradationTrendSlope() {
        val month = 30L * 24 * 60 * 60 * 1000
        val estimates = (0..3).map {
            CapacityEstimator.Estimate(it * month, 4000.0 - it * 20.0, CapacityEstimator.Method.FUEL_GAUGE)
        }
        val trend = DegradationTrend.compute(estimates, 4000)
        assertNotNull(trend)
        assertEquals(-20.0, trend!!.mahPerMonth, 0.01)
        assertEquals(-0.5, trend.percentPerMonth, 0.001)
    }
}
