package com.example.wifiheatmap.coverage

import com.example.wifiheatmap.data.model.WifiBand
import com.example.wifiheatmap.device.WifiDevice
import com.example.wifiheatmap.floorplan.NormalizedPoint
import com.example.wifiheatmap.survey.SurveyMeasurement

enum class SignalSourceMode(val displayName: String) {
    DEVICE("장비별"),
    MESH_BEST("전체 Mesh"),
    CONNECTED("실제 연결"),
}

enum class ResultView(val displayName: String) {
    COVERAGE("Coverage ○"), HEATMAP("Heatmap"), WEAK_ZONE("Weak Zone"),
}

data class AggregatedSignal(
    val point: NormalizedPoint,
    val rssi: Int,
    val bssid: String?,
    val frequencyMhz: Int?,
)

object SignalAggregation {
    fun aggregate(
        measurements: List<SurveyMeasurement>,
        devices: List<WifiDevice>,
        mode: SignalSourceMode,
        selectedDeviceId: Long?,
        band: WifiBand?,
    ): List<AggregatedSignal> = measurements.mapNotNull { measurement ->
        val candidates = when (mode) {
            SignalSourceMode.CONNECTED -> listOfNotNull(
                measurement.bssid?.let {
                    AggregatedSignal(measurement.point, measurement.medianRssi, it, measurement.frequencyMhz)
                },
            )
            SignalSourceMode.DEVICE -> {
                val device = devices.firstOrNull { it.id == selectedDeviceId } ?: return@mapNotNull null
                candidatesFor(measurement, device.mappedBssids)
            }
            SignalSourceMode.MESH_BEST -> {
                val mappedBssids = devices.flatMap { it.mappedBssids }.toSet()
                candidatesFor(measurement, mappedBssids)
            }
        }.filter { signal ->
            band == null || WifiBand.fromFrequency(signal.frequencyMhz ?: 0) == band
        }
        candidates.maxByOrNull { it.rssi }
    }

    private fun candidatesFor(
        measurement: SurveyMeasurement,
        mappedBssids: Set<String>,
    ): List<AggregatedSignal> {
        val connected = measurement.bssid?.lowercase()?.takeIf { it in mappedBssids }?.let {
            AggregatedSignal(measurement.point, measurement.medianRssi, it, measurement.frequencyMhz)
        }
        val nearby = measurement.nearbyAccessPoints.mapNotNull { accessPoint ->
            accessPoint.bssid.lowercase().takeIf { it in mappedBssids }?.let {
                AggregatedSignal(measurement.point, accessPoint.rssi, it, accessPoint.frequencyMhz)
            }
        }
        return listOfNotNull(connected) + nearby
    }
}
