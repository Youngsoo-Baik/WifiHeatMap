package com.example.wifiheatmap.heatmap

import com.example.wifiheatmap.floorplan.NormalizedPoint
import com.example.wifiheatmap.wall.WallSegment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallGeometryTest {
    private val wall = WallSegment(1, NormalizedPoint(0.5f, 0f), NormalizedPoint(0.5f, 1f))

    @Test
    fun detectsIntersectionAndIgnoresOpening() {
        assertTrue(WallGeometry.intersects(NormalizedPoint(0f, 0.5f), NormalizedPoint(1f, 0.5f), wall))
        assertFalse(WallGeometry.intersects(NormalizedPoint(0f, 0.5f), NormalizedPoint(1f, 0.5f), wall.copy(isOpening = true)))
    }
}
