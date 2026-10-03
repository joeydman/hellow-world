package com.joeydman.vitals.core

import com.joeydman.vitals.core.battery.CurrentUnit
import com.joeydman.vitals.core.battery.UnitNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitNormalizerTest {
    @Test fun chargeCounterInMicroAmpHours() {
        // 2,000,000 µAh at 50% -> 2000 mAh now, 4000 mAh full.
        assertEquals(2000.0, UnitNormalizer.chargeCounterToMah(2_000_000, 50)!!, 0.01)
    }

    @Test fun chargeCounterInMilliAmpHours() {
        assertEquals(2000.0, UnitNormalizer.chargeCounterToMah(2000, 50)!!, 0.01)
    }

    @Test fun implausibleChargeCounterRejected() {
        assertNull(UnitNormalizer.chargeCounterToMah(5, 50))
        assertNull(UnitNormalizer.chargeCounterToMah(2_000_000, 0))
    }

    @Test fun detectsCurrentUnit() {
        assertEquals(CurrentUnit.MICRO_AMPS, UnitNormalizer.detectCurrentUnit(listOf(-450_000, -380_000, 1_200_000)))
        assertEquals(CurrentUnit.MILLI_AMPS, UnitNormalizer.detectCurrentUnit(listOf(-450, -380, 1200)))
    }

    @Test fun detectsInvertedSign() {
        assertTrue(UnitNormalizer.detectSignInverted(listOf(-1500, -1400, -1300, 200)))
        assertFalse(UnitNormalizer.detectSignInverted(listOf(1500, 1400, 1300)))
        assertFalse(UnitNormalizer.detectSignInverted(listOf(-1500))) // too few to judge
    }
}
