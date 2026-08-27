package com.example.wifiheatmap.mesh

import com.example.wifiheatmap.data.model.NearbyAccessPoint
import com.example.wifiheatmap.device.WifiDevice
import com.example.wifiheatmap.device.WifiDeviceType
import com.example.wifiheatmap.floorplan.NormalizedPoint
import com.example.wifiheatmap.survey.SurveyMeasurement
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshAnalyzerTest {
    @Test
    fun recommendsStrongerMappedAccessPoint() {
        val connected = WifiDevice(1, "Main", WifiDeviceType.MESH_MAIN, NormalizedPoint(0f, 0f), setOf("aa"))
        val stronger = WifiDevice(2, "Node", WifiDeviceType.MESH_NODE, NormalizedPoint(1f, 1f), setOf("bb"))
        val measurement = SurveyMeasurement(
            1, NormalizedPoint(0.5f, 0.5f), "mesh", "aa", -75, 5180, listOf(-75),
            listOf(NearbyAccessPoint("mesh", "bb", -55, 5180, 36, 0, null)), 1,
        )
        assertTrue(MeshAnalyzer.analyze(measurement, listOf(connected, stronger)).isRoamingCandidate)
    }
}
