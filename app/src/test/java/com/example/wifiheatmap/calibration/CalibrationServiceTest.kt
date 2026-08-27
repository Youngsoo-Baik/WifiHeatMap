package com.example.wifiheatmap.calibration

import com.example.wifiheatmap.floorplan.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationServiceTest {
    @Test
    fun calculatesPixelDistanceAndScale() {
        val result = CalibrationService.calculate(
            firstPoint = NormalizedPoint(0.1f, 0.2f),
            secondPoint = NormalizedPoint(0.4f, 0.6f),
            bitmapWidth = 1000,
            bitmapHeight = 500,
            actualDistanceMeters = 10.0,
        )

        assertEquals(360.555, result.pixelDistance, 0.001)
        assertEquals(0.027735, result.metersPerPixel, 0.000001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSamePoint() {
        CalibrationService.calculate(
            firstPoint = NormalizedPoint(0.5f, 0.5f),
            secondPoint = NormalizedPoint(0.5f, 0.5f),
            bitmapWidth = 100,
            bitmapHeight = 100,
            actualDistanceMeters = 1.0,
        )
    }
}
