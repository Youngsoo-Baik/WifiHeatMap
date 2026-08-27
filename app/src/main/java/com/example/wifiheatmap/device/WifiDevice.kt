package com.example.wifiheatmap.device

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
)
