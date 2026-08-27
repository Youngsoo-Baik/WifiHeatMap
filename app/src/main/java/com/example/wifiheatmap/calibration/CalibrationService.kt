package com.example.wifiheatmap.calibration

import com.example.wifiheatmap.floorplan.NormalizedPoint
import kotlin.math.hypot

data class CalibrationData(
    val firstPoint: NormalizedPoint,
    val secondPoint: NormalizedPoint,
    val actualDistanceMeters: Double,
    val pixelDistance: Double,
    val metersPerPixel: Double,
)

object CalibrationService {
    fun calculate(
        firstPoint: NormalizedPoint,
        secondPoint: NormalizedPoint,
        bitmapWidth: Int,
        bitmapHeight: Int,
        actualDistanceMeters: Double,
    ): CalibrationData {
        require(bitmapWidth > 0 && bitmapHeight > 0) { "평면도 크기가 올바르지 않습니다." }
        require(actualDistanceMeters > 0.0) { "실제 거리는 0보다 커야 합니다." }

        val pixelDistance = hypot(
            (secondPoint.x - firstPoint.x).toDouble() * bitmapWidth,
            (secondPoint.y - firstPoint.y).toDouble() * bitmapHeight,
        )
        require(pixelDistance > 0.0) { "서로 다른 두 지점을 선택하세요." }

        return CalibrationData(
            firstPoint = firstPoint,
            secondPoint = secondPoint,
            actualDistanceMeters = actualDistanceMeters,
            pixelDistance = pixelDistance,
            metersPerPixel = actualDistanceMeters / pixelDistance,
        )
    }
}
