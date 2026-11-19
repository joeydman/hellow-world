package com.joeydman.vitals.core

import com.joeydman.vitals.core.battery.ChargeHabits
import com.joeydman.vitals.core.battery.ChargeSessionAnalyzer
import com.joeydman.vitals.core.battery.ChargeSpeed
import com.joeydman.vitals.core.model.BatterySample
import com.joeydman.vitals.core.model.ChargeStatus
import com.joeydman.vitals.core.model.PlugType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeSessionAnalyzerTest {
    private val min = 60_000L
    private val fullMah = 4000.0

    private fun s(
        t: Long,
        level: Int,
        plug: PlugType = PlugType.AC,
        currentUa: Long = 3_000_000,
        mv: Int = 4_200,
        tempDeci: Int = 330,
    ) = BatterySample(
        timestampMs = t,
        levelPercent = level,
        chargeCounterRaw = (fullMah * level / 100.0 * 1000).toLong(),
        currentNowRaw = if (plug == PlugType.NONE) -400_000 else currentUa,
        voltageMv = mv,
        temperatureDeciC = tempDeci,
        status = when {
            plug == PlugType.NONE -> ChargeStatus.DISCHARGING
            level >= 100 -> ChargeStatus.FULL
            else -> ChargeStatus.CHARGING
        },
        plugType = plug,
        cycleCount = null,
    )

    @Test fun summarisesAFastWiredCharge() {
        val samples = listOf(s(0, 30, PlugType.NONE)) +
            (0..10).map { s(min + it * 5 * min, 30 + it * 5, tempDeci = 330 + it * 10) } + // 30% -> 80% in 50 min
            listOf(s(70 * min, 79, PlugType.NONE))
        val session = ChargeSessionAnalyzer.detect(samples, nowMs = 70 * min).single()

        assertEquals(30, session.startLevel)
        assertEquals(80, session.endLevel)
        assertEquals(50 * min, session.durationMs)
        assertEquals(50 * min, session.timeTo80Ms)
        assertEquals(2000.0, session.addedMah!!, 1.0)
        assertEquals(3000.0, session.avgCurrentMa!!, 0.1)
        assertEquals(12.6, session.sustainedPowerW!!, 0.01) // 3 A x 4.2 V
        assertEquals(ChargeSpeed.FAST, session.speed)
        assertEquals(43.0, session.peakTempC!!, 0.01)
        assertTrue(session.wasHot)
        assertFalse(session.ongoing)
        assertEquals(60.0, session.ratePercentPerHour!!, 0.01)
    }

    @Test fun ongoingWhenLatestSampleIsPluggedAndRecent() {
        val samples = (0..3).map { s(it * min, 50 + it) }
        val session = ChargeSessionAnalyzer.detect(samples, nowMs = 4 * min).single()
        assertTrue(session.ongoing)
        assertFalse(ChargeSessionAnalyzer.detect(samples, nowMs = 60 * min).single().ongoing)
    }

    @Test fun unplugAndLongGapsSplitSessions() {
        val samples = listOf(s(0, 40), s(min, 41), s(2 * min, 41, PlugType.NONE), s(3 * min, 41), s(4 * min, 42)) +
            listOf(s(60 * min, 50), s(61 * min, 51)) // 56-minute gap: a new session
        assertEquals(3, ChargeSessionAnalyzer.detect(samples, nowMs = 100 * min).size)
    }

    @Test fun measuresTimeHeldAtFull() {
        val samples = listOf(s(0, 98), s(min, 100)) + (1..6).map { s(min + it * 10 * min, 100) }
        val session = ChargeSessionAnalyzer.detect(samples, nowMs = 1000 * min).single()
        assertEquals(60 * min, session.heldAtFullMs)
        assertNull(session.timeTo80Ms) // started above 80%
    }

    @Test fun wirelessAndSpeedBuckets() {
        val wireless = (0..2).map { s(it * min, 50 + it, PlugType.WIRELESS) }
        assertEquals(ChargeSpeed.WIRELESS, ChargeSessionAnalyzer.detect(wireless, 0).single().speed)
        assertEquals(ChargeSpeed.SUPER_FAST, ChargeSessionAnalyzer.classify(PlugType.AC, 18.0))
        assertEquals(ChargeSpeed.NORMAL, ChargeSessionAnalyzer.classify(PlugType.USB, 5.0))
        assertEquals(ChargeSpeed.SLOW, ChargeSessionAnalyzer.classify(PlugType.USB, 2.5))
        assertEquals(ChargeSpeed.UNKNOWN, ChargeSessionAnalyzer.classify(PlugType.AC, null))
    }

    @Test fun habitsIgnoreOngoingSession() {
        val samples = listOf(s(0, 15), s(15 * min, 100), s(16 * min, 100, PlugType.NONE)) +
            listOf(s(100 * min, 50), s(101 * min, 51))
        val sessions = ChargeSessionAnalyzer.detect(samples, nowMs = 101 * min)
        val habits = ChargeHabits.summarise(sessions)!!
        assertEquals(1, habits.sessions)
        assertEquals(1.0, habits.fullChargeShare, 0.0)
        assertEquals(1.0, habits.deepDischargeShare, 0.0)
    }
}
