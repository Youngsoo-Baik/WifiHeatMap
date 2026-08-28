package com.example.wifiheatmap.tracking

import com.example.wifiheatmap.floorplan.NormalizedPoint
import kotlin.math.atan2

enum class TrackingProviderType { PDR, ARCORE, SYNTHETIC }

data class FloorPlanPose(
    val startPoint: NormalizedPoint,
    val headingDegrees: Double,
)

data class TrackingPoint(
    val timestampMillis: Long,
    val xMeters: Double,
    val yMeters: Double,
    val headingDegrees: Double?,
    val trackingConfidence: Double,
)

object FloorPlanTrackingCoordinates {
    fun map(
        pose: FloorPlanPose,
        trackingPoint: TrackingPoint,
        bitmapWidth: Int,
        bitmapHeight: Int,
        metersPerPixel: Double,
    ): NormalizedPoint {
        require(bitmapWidth > 0 && bitmapHeight > 0 && metersPerPixel > 0.0)
        return NormalizedPoint(
            x = (pose.startPoint.x + trackingPoint.xMeters / (bitmapWidth * metersPerPixel)).toFloat().coerceIn(0f, 1f),
            y = (pose.startPoint.y + trackingPoint.yMeters / (bitmapHeight * metersPerPixel)).toFloat().coerceIn(0f, 1f),
        )
    }

    fun headingBetween(start: NormalizedPoint, directionPoint: NormalizedPoint): Double {
        val deltaX = (directionPoint.x - start.x).toDouble()
        val deltaY = (directionPoint.y - start.y).toDouble()
        require(deltaX != 0.0 || deltaY != 0.0) { "시작 위치와 다른 방향 지점을 선택하세요." }
        return normalizeDegrees(Math.toDegrees(atan2(deltaX, -deltaY)))
    }

    fun normalizeDegrees(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0
}
