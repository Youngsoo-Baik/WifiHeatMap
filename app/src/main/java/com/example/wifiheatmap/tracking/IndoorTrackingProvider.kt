package com.example.wifiheatmap.tracking

import kotlinx.coroutines.flow.StateFlow

enum class TrackingStatus { IDLE, TRACKING, UNAVAILABLE }

interface IndoorTrackingProvider {
    val providerType: TrackingProviderType
    val status: StateFlow<TrackingStatus>
    val points: StateFlow<List<TrackingPoint>>
    val isSupported: Boolean

    fun start(floorHeadingDegrees: Double): Boolean
    fun stop()
}
