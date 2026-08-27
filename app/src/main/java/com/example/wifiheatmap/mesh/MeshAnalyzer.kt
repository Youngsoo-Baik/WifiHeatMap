package com.example.wifiheatmap.mesh

import com.example.wifiheatmap.device.WifiDevice
import com.example.wifiheatmap.survey.SurveyMeasurement

data class MeshAnalysis(
    val measurementId: Long,
    val connectedDevice: WifiDevice?,
    val bestDevice: WifiDevice?,
    val connectedRssi: Int,
    val bestRssi: Int?,
    val isRoamingCandidate: Boolean,
)

object MeshAnalyzer {
    fun analyze(
        measurement: SurveyMeasurement,
        devices: List<WifiDevice>,
        roamingThresholdDb: Int = 12,
    ): MeshAnalysis {
        val connectedDevice = devices.firstOrNull { device ->
            measurement.bssid?.lowercase() in device.bssids
        }
        val mappedResults = measurement.nearbyAccessPoints.mapNotNull { accessPoint ->
            devices.firstOrNull { accessPoint.bssid.lowercase() in it.bssids }?.let { it to accessPoint.rssi }
        }
        val best = mappedResults.maxByOrNull { it.second }
        val shouldRoam = best != null && connectedDevice != null && best.first.id != connectedDevice.id &&
            best.second - measurement.medianRssi >= roamingThresholdDb
        return MeshAnalysis(
            measurementId = measurement.id,
            connectedDevice = connectedDevice,
            bestDevice = best?.first,
            connectedRssi = measurement.medianRssi,
            bestRssi = best?.second,
            isRoamingCandidate = shouldRoam,
        )
    }
}
