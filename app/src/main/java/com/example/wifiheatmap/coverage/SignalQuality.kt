package com.example.wifiheatmap.coverage

enum class SignalQuality(val displayName: String) {
    EXCELLENT("Excellent"),
    VERY_GOOD("Very Good"),
    GOOD("Good"),
    FAIR("Fair"),
    WEAK("Weak"),
    VERY_WEAK("Very Weak"),
}

object SignalQualityClassifier {
    fun classify(rssi: Int): SignalQuality = when {
        rssi >= -50 -> SignalQuality.EXCELLENT
        rssi >= -60 -> SignalQuality.VERY_GOOD
        rssi >= -67 -> SignalQuality.GOOD
        rssi >= -70 -> SignalQuality.FAIR
        rssi >= -80 -> SignalQuality.WEAK
        else -> SignalQuality.VERY_WEAK
    }

    fun isDeadZone(rssi: Int, threshold: Int = -70): Boolean = rssi < threshold
}
