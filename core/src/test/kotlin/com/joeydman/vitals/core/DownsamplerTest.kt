package com.joeydman.vitals.core

import com.joeydman.vitals.core.model.MetricPoint
import com.joeydman.vitals.core.retention.Downsampler
import com.joeydman.vitals.core.thermal.ThermalHeadroom
import com.joeydman.vitals.core.thermal.ThermalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownsamplerTest {
    @Test fun hourlyBuckets() {
        val h = Downsampler.HOUR_MS
        val pts = listOf(MetricPoint(0, 1.0), MetricPoint(10, 3.0), MetricPoint(h + 5, 10.0))
        val agg = Downsampler.aggregate(pts, h)
        assertEquals(2, agg.size)
        assertEquals(2.0, agg[0].avg, 0.0)
        assertEquals(1.0, agg[0].min, 0.0)
        assertEquals(3.0, agg[0].max, 0.0)
        assertEquals(h, agg[1].bucketStartMs)
    }

    @Test fun thermalMapping() {
        assertEquals(ThermalState.SEVERE, ThermalState.fromPlatform(3))
        assertTrue(ThermalState.MODERATE.isThrottling)
        assertNull(ThermalHeadroom.toPercent(Float.NaN))
        assertEquals(50, ThermalHeadroom.toPercent(0.5f))
    }
}
