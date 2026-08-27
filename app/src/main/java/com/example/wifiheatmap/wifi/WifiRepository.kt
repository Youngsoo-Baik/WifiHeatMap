package com.example.wifiheatmap.wifi

import com.example.wifiheatmap.data.model.WifiSnapshot

class WifiRepository(private val scanner: WifiScanner) {
    suspend fun loadSnapshot(requestActiveScan: Boolean): WifiSnapshot =
        scanner.readSnapshot(requestActiveScan)

    suspend fun collectConnectedRssiSamples(): Pair<List<Int>, WifiSnapshot> =
        scanner.collectConnectedRssiSamples()
}
