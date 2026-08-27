package com.example.wifiheatmap.data.model

enum class WifiBand(val displayName: String) {
    BAND_2_4_GHZ("2.4 GHz"),
    BAND_5_GHZ("5 GHz"),
    BAND_6_GHZ("6 GHz"),
    UNKNOWN("Unknown");

    companion object {
        fun fromFrequency(frequencyMhz: Int): WifiBand = when (frequencyMhz) {
            in 2400..2500 -> BAND_2_4_GHZ
            in 4900..5899 -> BAND_5_GHZ
            in 5925..7125 -> BAND_6_GHZ
            else -> UNKNOWN
        }
    }
}

data class ConnectedWifi(
    val ssid: String?,
    val bssid: String?,
    val rssi: Int?,
    val frequencyMhz: Int?,
    val linkSpeedMbps: Int?,
    val rxLinkSpeedMbps: Int?,
    val txLinkSpeedMbps: Int?,
)

data class NearbyAccessPoint(
    val ssid: String?,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int?,
    val channelWidth: Int,
    val ageMillis: Long?,
) {
    val band: WifiBand = WifiBand.fromFrequency(frequencyMhz)
}

data class WifiSnapshot(
    val connectedWifi: ConnectedWifi?,
    val nearbyAccessPoints: List<NearbyAccessPoint>,
    val activeScanRequested: Boolean,
    val scanResultsUpdated: Boolean?,
    val capturedAtMillis: Long,
)
