package com.example.wifiheatmap.device

import com.example.wifiheatmap.data.model.WifiBand
import com.example.wifiheatmap.floorplan.NormalizedPoint

enum class WifiDeviceType(val displayName: String) {
    ROUTER("공유기"),
    MESH_MAIN("메시 메인"),
    MESH_NODE("메시 노드"),
    EXTENDER("증폭기"),
}

data class WifiDevice(
    val id: Long,
    val name: String,
    val type: WifiDeviceType,
    val point: NormalizedPoint,
    val bssids: Set<String>,
    val radios: List<WifiRadio> = emptyList(),
    val positionConfidence: Double? = null,
    val clusterConfidence: Double? = null,
    val automaticallyEstimated: Boolean? = null,
    val userConfirmed: Boolean? = null,
) {
    val mappedBssids: Set<String>
        get() = bssids + radios.map { it.bssid.lowercase() }
}

data class WifiRadio(
    val bssid: String,
    val ssid: String?,
    val band: WifiBand,
    val frequencyMhz: Int?,
)
