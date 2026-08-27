package com.example.wifiheatmap.coverage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import com.example.wifiheatmap.device.WifiDevice
import com.example.wifiheatmap.device.WifiDeviceType
import com.example.wifiheatmap.floorplan.NormalizedPoint
import org.junit.Test

class CoverageRadiusEstimatorTest {
    @Test
    fun interpolatesThresholdRadius() {
        val radius = CoverageRadiusEstimator.radiusAtThreshold(
            listOf(2.0 to -50, 6.0 to -70),
            threshold = -60,
        )
        assertEquals(4.0, radius ?: 0.0, 0.001)
    }

    @Test
    fun returnsNullWhenEverySampleIsWeak() {
        assertNull(CoverageRadiusEstimator.radiusAtThreshold(listOf(1.0 to -75, 2.0 to -80), -67))
    }

    @Test
    fun assignsConfidenceFromMeasurementCount() {
        val device = WifiDevice(1, "Router", WifiDeviceType.ROUTER, NormalizedPoint(0f, 0f), setOf("aa"))
        val signals = List(8) { index ->
            AggregatedSignal(NormalizedPoint((index + 1) / 10f, 0f), -45 - index, "aa", 5180)
        }
        val coverage = CoverageRadiusEstimator.estimate(device, signals, 100, 100, 0.1, null)
        assertEquals(CoverageConfidence.HIGH, coverage.confidence)
    }
}
