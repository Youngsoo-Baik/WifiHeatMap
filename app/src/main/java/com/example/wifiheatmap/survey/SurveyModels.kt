package com.example.wifiheatmap.survey

import com.example.wifiheatmap.data.model.NearbyAccessPoint
import com.example.wifiheatmap.floorplan.NormalizedPoint

data class SurveyMeasurement(
    val id: Long,
    val point: NormalizedPoint,
    val ssid: String?,
    val bssid: String?,
    val medianRssi: Int,
    val frequencyMhz: Int?,
    val samples: List<Int>,
    val nearbyAccessPoints: List<NearbyAccessPoint>,
    val measuredAtMillis: Long,
)

object RssiStatistics {
    fun median(samples: List<Int>): Int {
        require(samples.isNotEmpty()) { "RSSI 샘플이 없습니다." }
        val sorted = samples.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            ((sorted[middle - 1] + sorted[middle]) / 2.0).toInt()
        }
    }
}
