package com.example.wifiheatmap.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiBandTest {
    @Test
    fun mapsCommonFrequenciesToBands() {
        assertEquals(WifiBand.BAND_2_4_GHZ, WifiBand.fromFrequency(2412))
        assertEquals(WifiBand.BAND_5_GHZ, WifiBand.fromFrequency(5180))
        assertEquals(WifiBand.BAND_6_GHZ, WifiBand.fromFrequency(5955))
        assertEquals(WifiBand.UNKNOWN, WifiBand.fromFrequency(0))
    }
}
