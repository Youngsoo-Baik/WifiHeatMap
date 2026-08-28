package com.example.wifiheatmap.persistence

import com.example.wifiheatmap.device.WifiDevice
import com.example.wifiheatmap.device.WifiDeviceType
import com.example.wifiheatmap.floorplan.NormalizedPoint
import com.example.wifiheatmap.coverage.ResultView
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectCodecTest {
    @Test
    fun roundTripsProjectJson() {
        val project = SavedProject(
            calibration = null,
            measurements = emptyList(),
            devices = listOf(
                WifiDevice(
                    1,
                    "Router",
                    WifiDeviceType.ROUTER,
                    NormalizedPoint(0.2f, 0.3f),
                    setOf("aa"),
                    positionConfidence = 0.8,
                    clusterConfidence = 0.7,
                    automaticallyEstimated = true,
                    userConfirmed = false,
                ),
            ),
            walls = emptyList(),
            settings = ProjectSettings(
                resultView = ResultView.MESH,
                useWallAwareHeatmap = false,
            ),
        )
        val decoded = ProjectCodec.decode(ProjectCodec.encode(project))
        assertEquals("Router", decoded.devices.single().name)
        assertEquals(0.2f, decoded.devices.single().point.x)
        assertEquals(0.8, decoded.devices.single().positionConfidence)
        assertEquals(true, decoded.devices.single().automaticallyEstimated)
        assertEquals(ResultView.MESH, decoded.settings?.resultView)
        assertEquals(false, decoded.settings?.useWallAwareHeatmap)
    }

    @Test
    fun loadsLegacyDeviceWithoutAutomaticEstimationFields() {
        val decoded = ProjectCodec.decode(
            """{"calibration":null,"measurements":[],"devices":[{"id":1,"name":"Legacy","type":"ROUTER","point":{"x":0.2,"y":0.3},"bssids":["aa"],"radios":[]}],"walls":[]}""",
        )

        assertEquals(null, decoded.devices.single().positionConfidence)
        assertEquals(null, decoded.devices.single().automaticallyEstimated)
    }
}
