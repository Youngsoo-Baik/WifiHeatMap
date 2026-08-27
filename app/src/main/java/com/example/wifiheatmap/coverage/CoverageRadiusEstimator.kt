package com.example.wifiheatmap.coverage

import com.example.wifiheatmap.data.model.WifiBand
import com.example.wifiheatmap.device.WifiDevice
import kotlin.math.hypot

enum class CoverageConfidence(val displayName: String) {
    HIGH("높음"), MEDIUM("보통"), LOW("낮음"), INSUFFICIENT("데이터 부족"),
}

data class DeviceCoverage(
    val deviceId: Long,
    val band: WifiBand?,
    val strongRadiusM: Double?,
    val goodRadiusM: Double?,
    val usableRadiusM: Double?,
    val confidence: CoverageConfidence,
    val measurementCount: Int,
    val averageRssi: Double?,
    val weakestRssi: Int?,
    val strongestRssi: Int?,
)

object CoverageRadiusEstimator {
    fun estimate(
        device: WifiDevice,
        signals: List<AggregatedSignal>,
        bitmapWidth: Int,
        bitmapHeight: Int,
        metersPerPixel: Double,
        band: WifiBand?,
    ): DeviceCoverage {
        val samples = signals.map { signal ->
            val pixelDistance = hypot(
                (signal.point.x - device.point.x).toDouble() * bitmapWidth,
                (signal.point.y - device.point.y).toDouble() * bitmapHeight,
            )
            pixelDistance * metersPerPixel to signal.rssi
        }.sortedBy { it.first }
        return DeviceCoverage(
            deviceId = device.id,
            band = band,
            strongRadiusM = radiusAtThreshold(samples, -50),
            goodRadiusM = radiusAtThreshold(samples, -60),
            usableRadiusM = radiusAtThreshold(samples, -67),
            confidence = when {
                samples.size >= 8 -> CoverageConfidence.HIGH
                samples.size >= 4 -> CoverageConfidence.MEDIUM
                samples.size >= 2 -> CoverageConfidence.LOW
                else -> CoverageConfidence.INSUFFICIENT
            },
            measurementCount = samples.size,
            averageRssi = samples.map { it.second }.average().takeUnless { it.isNaN() },
            weakestRssi = samples.minOfOrNull { it.second },
            strongestRssi = samples.maxOfOrNull { it.second },
        )
    }

    fun radiusAtThreshold(samples: List<Pair<Double, Int>>, threshold: Int): Double? {
        if (samples.isEmpty()) return null
        val sorted = samples.sortedBy { it.first }
        sorted.zipWithNext().forEach { (near, far) ->
            val nearDelta = near.second - threshold
            val farDelta = far.second - threshold
            if (nearDelta == 0) return near.first
            if (nearDelta * farDelta <= 0 && near.second != far.second) {
                val ratio = (threshold - near.second).toDouble() / (far.second - near.second)
                return near.first + ratio * (far.first - near.first)
            }
        }
        return when {
            sorted.all { it.second >= threshold } -> sorted.last().first
            sorted.all { it.second < threshold } -> null
            else -> sorted.minByOrNull { kotlin.math.abs(it.second - threshold) }?.first
        }
    }
}
