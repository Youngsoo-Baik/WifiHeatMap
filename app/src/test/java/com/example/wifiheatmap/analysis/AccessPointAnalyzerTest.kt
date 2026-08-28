package com.example.wifiheatmap.analysis

import com.example.wifiheatmap.data.model.NearbyAccessPoint
import com.example.wifiheatmap.floorplan.NormalizedPoint
import com.example.wifiheatmap.survey.SurveyMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessPointAnalyzerTest {
    @Test
    fun groupsCorrelatedDualBandBssidsAndSeparatesDistantNode() {
        val measurements = listOf(
            measurement(1, 0.10f, 0.10f, -42, -44, -88),
            measurement(2, 0.20f, 0.15f, -50, -51, -82),
            measurement(3, 0.45f, 0.45f, -67, -66, -60),
            measurement(4, 0.80f, 0.80f, -85, -83, -41),
        )

        val candidates = AccessPointAnalyzer.analyze(measurements)

        assertEquals(2, candidates.size)
        assertTrue(candidates.any { candidate ->
            candidate.radios.map { it.bssid }.toSet() == setOf(FIRST_24, FIRST_5)
        })
        assertTrue(candidates.any { candidate -> candidate.radios.singleOrNull()?.bssid == SECOND_5 })
    }

    @Test
    fun excludesUnrelatedNeighborSsid() {
        val base = measurement(1, 0.2f, 0.2f, -45, -47, -80)
        val measurement = base.copy(
            nearbyAccessPoints = base.nearbyAccessPoints +
                NearbyAccessPoint("Neighbor", "90:90:90:90:90:90", -35, 2412, 1, 0, 0),
        )

        val candidates = AccessPointAnalyzer.analyze(listOf(measurement))

        assertTrue(candidates.flatMap { it.radios }.none { it.ssid == "Neighbor" })
    }

    @Test
    fun estimatesPositionNearStrongestObservations() {
        val measurements = listOf(
            measurement(1, 0.10f, 0.10f, -38, -40, -90),
            measurement(2, 0.15f, 0.12f, -42, -44, -88),
            measurement(3, 0.70f, 0.70f, -82, -84, -55),
        )

        val firstNode = AccessPointAnalyzer.analyze(measurements)
            .first { it.radios.any { radio -> radio.bssid == FIRST_24 } }

        assertTrue(firstNode.estimatedPoint.x < 0.3f)
        assertTrue(firstNode.estimatedPoint.y < 0.3f)
        assertTrue(firstNode.positionConfidence in 0.0..1.0)
    }

    private fun measurement(
        id: Long,
        x: Float,
        y: Float,
        first24Rssi: Int,
        first5Rssi: Int,
        second5Rssi: Int,
    ) = SurveyMeasurement(
        id = id,
        point = NormalizedPoint(x, y),
        ssid = "Home",
        bssid = FIRST_24,
        medianRssi = first24Rssi,
        frequencyMhz = 2412,
        samples = listOf(first24Rssi),
        nearbyAccessPoints = listOf(
            NearbyAccessPoint("Home", FIRST_24, first24Rssi, 2412, 1, 0, 0),
            NearbyAccessPoint("Home", FIRST_5, first5Rssi, 5180, 36, 0, 0),
            NearbyAccessPoint("Home", SECOND_5, second5Rssi, 5500, 100, 0, 0),
        ),
        measuredAtMillis = id,
    )

    private companion object {
        const val FIRST_24 = "10:20:30:40:50:01"
        const val FIRST_5 = "10:20:30:40:50:02"
        const val SECOND_5 = "10:20:30:99:99:01"
    }
}
