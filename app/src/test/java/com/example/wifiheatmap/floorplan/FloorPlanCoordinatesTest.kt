package com.example.wifiheatmap.floorplan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloorPlanCoordinatesTest {
    @Test
    fun normalizesPointInsideRenderedImage() {
        val point = FloorPlanCoordinates.normalize(
            pointX = 300f,
            pointY = 250f,
            imageLeft = 100f,
            imageTop = 50f,
            imageWidth = 400f,
            imageHeight = 400f,
        )

        assertEquals(0.5f, point?.x)
        assertEquals(0.5f, point?.y)
    }

    @Test
    fun rejectsPointOutsideRenderedImage() {
        assertNull(
            FloorPlanCoordinates.normalize(
                pointX = 99f,
                pointY = 250f,
                imageLeft = 100f,
                imageTop = 50f,
                imageWidth = 400f,
                imageHeight = 400f,
            ),
        )
    }
}
