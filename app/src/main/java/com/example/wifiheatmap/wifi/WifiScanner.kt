package com.example.wifiheatmap.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import com.example.wifiheatmap.data.model.ConnectedWifi
import com.example.wifiheatmap.data.model.NearbyAccessPoint
import com.example.wifiheatmap.data.model.WifiSnapshot
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.max

class WifiScanner(context: Context) {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)

    @SuppressLint("MissingPermission")
    suspend fun readSnapshot(requestActiveScan: Boolean): WifiSnapshot {
        val scanUpdated = if (requestActiveScan) awaitActiveScan() else null
        return WifiSnapshot(
            connectedWifi = readConnectedWifi(),
            nearbyAccessPoints = readNearbyAccessPoints(),
            activeScanRequested = requestActiveScan,
            scanResultsUpdated = scanUpdated,
            capturedAtMillis = System.currentTimeMillis(),
        )
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun readConnectedWifi(): ConnectedWifi? {
        val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val network = connectivityManager.activeNetwork ?: return null
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
            capabilities.transportInfo as? WifiInfo
        } else {
            wifiManager.connectionInfo
        } ?: return null

        val validBssid = wifiInfo.bssid?.takeUnless { it == REDACTED_BSSID }
        val validSsid = wifiInfo.ssid
            ?.removeSurrounding("\"")
            ?.takeUnless { it == WifiManager.UNKNOWN_SSID }
        return ConnectedWifi(
            ssid = validSsid,
            bssid = validBssid,
            rssi = wifiInfo.rssi.takeIf { it in -126..-1 },
            frequencyMhz = wifiInfo.frequency.takeIf { it > 0 },
            linkSpeedMbps = wifiInfo.linkSpeed.takeIf { it >= 0 },
            rxLinkSpeedMbps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wifiInfo.rxLinkSpeedMbps.takeIf { it >= 0 }
            } else null,
            txLinkSpeedMbps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wifiInfo.txLinkSpeedMbps.takeIf { it >= 0 }
            } else null,
        )
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun readNearbyAccessPoints(): List<NearbyAccessPoint> {
        val nowMicros = SystemClock.elapsedRealtimeNanos() / 1_000L
        return wifiManager.scanResults
            .asSequence()
            .filter { !it.BSSID.isNullOrBlank() }
            .map { result ->
                NearbyAccessPoint(
                    ssid = result.SSID.takeIf { it.isNotBlank() },
                    bssid = result.BSSID,
                    rssi = result.level,
                    frequencyMhz = result.frequency,
                    channel = frequencyToChannel(result.frequency),
                    channelWidth = result.channelWidth,
                    ageMillis = result.timestamp
                        .takeIf { it > 0L }
                        ?.let { max(0L, (nowMicros - it) / 1_000L) },
                )
            }
            .sortedByDescending { it.rssi }
            .toList()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun awaitActiveScan(): Boolean? = withTimeoutOrNull(SCAN_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            var registered = false
            lateinit var receiver: BroadcastReceiver

            fun unregisterReceiver() {
                if (!registered) return
                registered = false
                runCatching { appContext.unregisterReceiver(receiver) }
            }

            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                    val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    unregisterReceiver()
                    if (continuation.isActive) continuation.resume(updated)
                }
            }

            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }
            registered = true
            continuation.invokeOnCancellation { unregisterReceiver() }

            val started = runCatching { wifiManager.startScan() }.getOrDefault(false)
            if (!started) {
                unregisterReceiver()
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    private fun frequencyToChannel(frequencyMhz: Int): Int? = when (frequencyMhz) {
        2484 -> 14
        in 2412..2472 -> (frequencyMhz - 2407) / 5
        in 5000..5895 -> (frequencyMhz - 5000) / 5
        in 5955..7115 -> (frequencyMhz - 5950) / 5
        else -> null
    }

    private companion object {
        const val REDACTED_BSSID = "02:00:00:00:00:00"
        const val SCAN_TIMEOUT_MILLIS = 5_000L
    }
}
