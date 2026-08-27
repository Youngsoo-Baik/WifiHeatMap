package com.example.wifiheatmap.coverage

import com.example.wifiheatmap.data.model.NearbyAccessPoint
import com.example.wifiheatmap.data.model.WifiBand
import com.example.wifiheatmap.device.WifiDevice
import com.example.wifiheatmap.device.WifiDeviceType
import com.example.wifiheatmap.floorplan.NormalizedPoint
import com.example.wifiheatmap.survey.SurveyMeasurement
import org.junit.Assert.assertEquals
import org.junit.Test

class SignalAggregationTest {
    @Test
    fun selectsStrongestMappedBssidForDeviceAndBand() {
        val device = WifiDevice(1, "Node", WifiDeviceType.MESH_NODE, NormalizedPoint(0f, 0f), setOf("aa", "bb"))
        val measurement = SurveyMeasurement(
            1, NormalizedPoint(0.5f, 0.5f), "Home", "aa", -70, 2412, listOf(-70),
            listOf(
                NearbyAccessPoint("Home", "aa", -68, 2412, 1, 0, null),
                NearbyAccessPoint("Home", "bb", -52, 5180, 36, 0, null),
            ), 1,
        )
        val signals = SignalAggregation.aggregate(
            listOf(measurement), listOf(device), SignalSourceMode.DEVICE, 1, WifiBand.BAND_5_GHZ,
        )
        assertEquals(-52, signals.single().rssi)
    }
}
