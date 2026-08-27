package com.example.wifiheatmap.coverage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalQualityClassifierTest {
    @Test
    fun classifiesRssiAndCustomDeadZone() {
        assertEquals(SignalQuality.EXCELLENT, SignalQualityClassifier.classify(-45))
        assertEquals(SignalQuality.GOOD, SignalQualityClassifier.classify(-65))
        assertEquals(SignalQuality.VERY_WEAK, SignalQualityClassifier.classify(-85))
        assertTrue(SignalQualityClassifier.isDeadZone(-72, -70))
        assertFalse(SignalQualityClassifier.isDeadZone(-69, -70))
    }
}
