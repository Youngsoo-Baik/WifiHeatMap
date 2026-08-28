package com.example.wifiheatmap.tracking

import kotlin.math.cos
import kotlin.math.sin

class PdrEstimator(private val stepLengthMeters: Double = 0.72) {
    private var floorHeadingDegrees = 0.0
    private var initialDeviceHeadingDegrees: Double? = null
    private var xMeters = 0.0
    private var yMeters = 0.0

    fun start(floorHeadingDegrees: Double, deviceHeadingDegrees: Double?) {
        this.floorHeadingDegrees = FloorPlanTrackingCoordinates.normalizeDegrees(floorHeadingDegrees)
        initialDeviceHeadingDegrees = deviceHeadingDegrees
        xMeters = 0.0
        yMeters = 0.0
    }

    fun onStep(
        deviceHeadingDegrees: Double?,
        timestampMillis: Long,
        confidence: Double = 0.75,
    ): TrackingPoint {
        if (initialDeviceHeadingDegrees == null && deviceHeadingDegrees != null) {
            initialDeviceHeadingDegrees = deviceHeadingDegrees
        }
        val relativeHeading = if (deviceHeadingDegrees != null && initialDeviceHeadingDegrees != null) {
            shortestDelta(initialDeviceHeadingDegrees!!, deviceHeadingDegrees)
        } else 0.0
        val mappedHeading = FloorPlanTrackingCoordinates.normalizeDegrees(floorHeadingDegrees + relativeHeading)
        val radians = Math.toRadians(mappedHeading)
        xMeters += sin(radians) * stepLengthMeters
        yMeters -= cos(radians) * stepLengthMeters
        return TrackingPoint(
            timestampMillis = timestampMillis,
            xMeters = xMeters,
            yMeters = yMeters,
            headingDegrees = mappedHeading,
            trackingConfidence = confidence.coerceIn(0.0, 1.0),
        )
    }

    fun origin(timestampMillis: Long): TrackingPoint = TrackingPoint(
        timestampMillis = timestampMillis,
        xMeters = 0.0,
        yMeters = 0.0,
        headingDegrees = floorHeadingDegrees,
        trackingConfidence = 1.0,
    )

    private fun shortestDelta(fromDegrees: Double, toDegrees: Double): Double =
        (toDegrees - fromDegrees + 540.0) % 360.0 - 180.0
}
