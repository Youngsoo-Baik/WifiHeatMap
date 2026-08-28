package com.example.wifiheatmap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationJoinerTest {
    @Test
    fun joinsNearestTrackingPointAndDownweightsStaleScan() {
        val tracking = listOf(
            TrackingPoint(1_000, 0.0, 0.0, 0.0, 1.0),
            TrackingPoint(3_000, 1.0, 0.0, 90.0, 0.8),
        )
        val fresh = WifiObservation(2_900, "Home", "aa", -50, 5180, 36, 1_000, false)
        val stale = fresh.copy(bssid = "bb", scanAgeMillis = 29_000)

        val joined = ObservationJoiner.join(tracking, listOf(fresh, stale))

        assertEquals(3_000, joined.first().trackingPoint.timestampMillis)
        assertEquals(100, joined.first().temporalGapMillis)
        assertTrue(joined.first().weight > joined.last().weight)
    }

    @Test
    fun excludesObservationOutsideTemporalWindow() {
        val joined = ObservationJoiner.join(
            trackingPoints = listOf(TrackingPoint(1_000, 0.0, 0.0, 0.0, 1.0)),
            observations = listOf(WifiObservation(10_000, null, "aa", -70, 2412, 1, null, false)),
        )
        assertTrue(joined.isEmpty())
    }
}
