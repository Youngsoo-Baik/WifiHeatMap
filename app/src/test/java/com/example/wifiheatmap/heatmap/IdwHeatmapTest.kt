package com.example.wifiheatmap.heatmap

import com.example.wifiheatmap.floorplan.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IdwHeatmapTest {
    @Test
    fun interpolatesCenterBetweenTwoSamples() {
        val grid = IdwHeatmap.generate(
            samples = listOf(
                HeatmapSample(NormalizedPoint(0f, 0.5f), -40.0),
                HeatmapSample(NormalizedPoint(1f, 0.5f), -80.0),
            ),
            columns = 1,
            rows = 1,
            trustRadius = 0.1,
        )
        assertEquals(-60.0, grid[0, 0].value, 0.001)
        assertFalse(grid[0, 0].isTrusted)
    }
}
