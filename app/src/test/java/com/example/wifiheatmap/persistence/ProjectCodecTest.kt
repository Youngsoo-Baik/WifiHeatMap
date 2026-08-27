package com.example.wifiheatmap.persistence

import com.example.wifiheatmap.device.WifiDevice
import com.example.wifiheatmap.device.WifiDeviceType
import com.example.wifiheatmap.floorplan.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectCodecTest {
    @Test
    fun roundTripsProjectJson() {
        val project = SavedProject(
            calibration = null,
            measurements = emptyList(),
            devices = listOf(WifiDevice(1, "Router", WifiDeviceType.ROUTER, NormalizedPoint(0.2f, 0.3f), setOf("aa"))),
            walls = emptyList(),
        )
        val decoded = ProjectCodec.decode(ProjectCodec.encode(project))
        assertEquals("Router", decoded.devices.single().name)
        assertEquals(0.2f, decoded.devices.single().point.x)
    }
}
