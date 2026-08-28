package com.example.wifiheatmap.tracking

import com.example.wifiheatmap.floorplan.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class PdrEstimatorTest {
    @Test
    fun mapsDeviceHeadingChangesToFloorPlanHeading() {
        val estimator = PdrEstimator(stepLengthMeters = 1.0)
        estimator.start(floorHeadingDegrees = 0.0, deviceHeadingDegrees = 30.0)

        val north = estimator.onStep(deviceHeadingDegrees = 30.0, timestampMillis = 1)
        val east = estimator.onStep(deviceHeadingDegrees = 120.0, timestampMillis = 2)

        assertEquals(0.0, north.xMeters, 0.001)
        assertEquals(-1.0, north.yMeters, 0.001)
        assertEquals(1.0, east.xMeters, 0.001)
        assertEquals(-1.0, east.yMeters, 0.001)
    }

    @Test
    fun mapsMetersToNormalizedFloorPlanCoordinates() {
        val point = FloorPlanTrackingCoordinates.map(
            pose = FloorPlanPose(NormalizedPoint(0.5f, 0.5f), 0.0),
            trackingPoint = TrackingPoint(1, 2.0, -1.0, 0.0, 1.0),
            bitmapWidth = 1000,
            bitmapHeight = 500,
            metersPerPixel = 0.01,
        )
        assertEquals(0.7f, point.x, 0.001f)
        assertEquals(0.3f, point.y, 0.001f)
    }

    @Test
    fun derivesFloorHeadingFromTwoPoints() {
        assertEquals(
            90.0,
            FloorPlanTrackingCoordinates.headingBetween(NormalizedPoint(0.5f, 0.5f), NormalizedPoint(0.8f, 0.5f)),
            0.001,
        )
    }
}
