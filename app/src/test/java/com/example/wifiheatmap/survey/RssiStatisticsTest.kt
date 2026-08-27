package com.example.wifiheatmap.survey

import org.junit.Assert.assertEquals
import org.junit.Test

class RssiStatisticsTest {
    @Test
    fun calculatesOddAndEvenMedian() {
        assertEquals(-60, RssiStatistics.median(listOf(-80, -60, -50)))
        assertEquals(-65, RssiStatistics.median(listOf(-80, -70, -60, -50)))
    }
}
