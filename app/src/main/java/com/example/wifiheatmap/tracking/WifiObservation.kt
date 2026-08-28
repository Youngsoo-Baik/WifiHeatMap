package com.example.wifiheatmap.tracking

import kotlin.math.abs

data class WifiObservation(
    val timestampMillis: Long,
    val ssid: String?,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int?,
    val scanAgeMillis: Long?,
    val connected: Boolean,
)

data class PositionedWifiObservation(
    val trackingPoint: TrackingPoint,
    val observation: WifiObservation,
    val temporalGapMillis: Long,
    val weight: Double,
)

object ObservationJoiner {
    fun join(
        trackingPoints: List<TrackingPoint>,
        observations: List<WifiObservation>,
        maxTemporalGapMillis: Long = 2_500L,
        staleAfterMillis: Long = 30_000L,
    ): List<PositionedWifiObservation> = observations.mapNotNull { observation ->
        val nearest = trackingPoints.minByOrNull { abs(it.timestampMillis - observation.timestampMillis) }
            ?: return@mapNotNull null
        val gap = abs(nearest.timestampMillis - observation.timestampMillis)
        if (gap > maxTemporalGapMillis) return@mapNotNull null
        val temporalWeight = 1.0 - gap.toDouble() / maxTemporalGapMillis
        val freshnessWeight = observation.scanAgeMillis?.let { age ->
            (1.0 - age.toDouble() / staleAfterMillis).coerceIn(0.1, 1.0)
        } ?: 0.7
        PositionedWifiObservation(
            trackingPoint = nearest,
            observation = observation,
            temporalGapMillis = gap,
            weight = (nearest.trackingConfidence * temporalWeight * freshnessWeight).coerceIn(0.0, 1.0),
        )
    }
}
