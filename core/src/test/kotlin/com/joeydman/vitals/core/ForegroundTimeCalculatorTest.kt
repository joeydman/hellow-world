package com.joeydman.vitals.core

import com.joeydman.vitals.core.usage.ForegroundTimeCalculator
import com.joeydman.vitals.core.usage.ForegroundTimeCalculator.Event
import com.joeydman.vitals.core.usage.ForegroundTimeCalculator.Type
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundTimeCalculatorTest {
    @Test fun sumsSessionsAndClipsToWindow() {
        val events = listOf(
            Event("a", Type.RESUMED, 50), Event("a", Type.PAUSED, 150), // 50 inside window
            Event("b", Type.RESUMED, 200), Event("b", Type.PAUSED, 300),
            Event("a", Type.RESUMED, 400), Event("a", Type.PAUSED, 450),
        )
        val result = ForegroundTimeCalculator.compute(events, 100, 1000).associateBy { it.packageName }
        assertEquals(100L, result["a"]!!.foregroundMs)
        assertEquals(1, result["a"]!!.launches) // launch at 50 was before the window
        assertEquals(100L, result["b"]!!.foregroundMs)
    }

    @Test fun screenOffClosesOpenSessions() {
        val events = listOf(Event("a", Type.RESUMED, 0), Event("x", Type.SCREEN_OFF, 30))
        assertEquals(30L, ForegroundTimeCalculator.compute(events, 0, 1000).single().foregroundMs)
    }

    @Test fun openSessionRunsToWindowEnd() {
        val events = listOf(Event("a", Type.RESUMED, 900))
        assertEquals(100L, ForegroundTimeCalculator.compute(events, 0, 1000).single().foregroundMs)
    }
}
