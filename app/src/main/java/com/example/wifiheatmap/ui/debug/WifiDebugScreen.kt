package com.example.wifiheatmap.ui.debug

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wifiheatmap.data.model.ConnectedWifi
import com.example.wifiheatmap.data.model.NearbyAccessPoint
import com.example.wifiheatmap.viewmodel.WifiDebugViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiDebugScreen(
    onOpenFloorPlan: () -> Unit,
    viewModel: WifiDebugViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var permissionsGranted by remember { mutableStateOf(hasWifiPermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsGranted = hasWifiPermissions(context)
        if (permissionsGranted) viewModel.refresh(requestActiveScan = true)
    }

    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted && uiState.snapshot == null) {
            viewModel.refresh(requestActiveScan = false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Wi-Fi Debug")
                        Text(
                            text = "Phase 1 · 실제 기기 데이터 검증",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!permissionsGranted) {
                item {
                    PermissionCard {
                        permissionLauncher.launch(requiredWifiPermissions())
                    }
                }
            } else {
                item {
                    RefreshCard(
                        isLoading = uiState.isLoading,
                        scanResultsUpdated = uiState.snapshot?.scanResultsUpdated,
                        onRefresh = { viewModel.refresh(requestActiveScan = true) },
                    )
                }
                item {
                    Button(onClick = onOpenFloorPlan, modifier = Modifier.fillMaxWidth()) {
                        Text("Phase 2 · 평면도 열기")
                    }
                }
                uiState.errorMessage?.let { error ->
                    item { ErrorCard(error) }
                }
                item {
                    ConnectedWifiCard(uiState.snapshot?.connectedWifi)
                }
                item {
                    val accessPointCount = uiState.snapshot?.nearbyAccessPoints?.size ?: 0
                    Text(
                        text = "주변 AP ${accessPointCount}개",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(
                    items = uiState.snapshot?.nearbyAccessPoints.orEmpty(),
                    key = { "${it.bssid}-${it.frequencyMhz}" },
                ) { accessPoint ->
                    AccessPointCard(accessPoint)
                }
                if (uiState.isLoading && uiState.snapshot == null) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Wi-Fi 정보 권한 필요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "SSID, BSSID와 주변 AP ScanResult는 위치 및 근처 기기 권한이 필요합니다. " +
                    "기기의 위치 서비스도 켜져 있어야 합니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                Text("권한 허용")
            }
        }
    }
}

@Composable
private fun RefreshCard(isLoading: Boolean, scanResultsUpdated: Boolean?, onRefresh: () -> Unit) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Wi-Fi Snapshot", fontWeight = FontWeight.Bold)
                Text(
                    text = when (scanResultsUpdated) {
                        true -> "Active Scan 결과가 갱신됨"
                        false -> "캐시된 ScanResult 사용"
                        null -> "현재 OS ScanResult 표시"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onRefresh, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("새로고침")
                }
            }
        }
    }
}

@Composable
private fun ConnectedWifiCard(wifi: ConnectedWifi?) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("현재 연결 AP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (wifi == null) {
                Text("Wi-Fi 연결 정보를 찾지 못했습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LabelValue("SSID", wifi.ssid ?: "권한으로 숨김")
                LabelValue("BSSID", wifi.bssid ?: "권한으로 숨김")
                LabelValue("RSSI", wifi.rssi?.let { "$it dBm" } ?: "알 수 없음")
                LabelValue("Frequency", wifi.frequencyMhz?.let { "$it MHz" } ?: "알 수 없음")
                LabelValue("Link Speed", wifi.linkSpeedMbps?.let { "$it Mbps" } ?: "알 수 없음")
                LabelValue("Rx / Tx", "${wifi.rxLinkSpeedMbps ?: "-"} / ${wifi.txLinkSpeedMbps ?: "-"} Mbps")
            }
        }
    }
}

@Composable
private fun AccessPointCard(accessPoint: NearbyAccessPoint) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(rssiColor(accessPoint.rssi), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text("${accessPoint.rssi}", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(accessPoint.ssid ?: "숨김 네트워크", fontWeight = FontWeight.Bold)
                Text(accessPoint.bssid, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${accessPoint.band.displayName} · ${accessPoint.frequencyMhz} MHz" +
                        (accessPoint.channel?.let { " · CH $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatFreshness(accessPoint.ageMillis),
                style = MaterialTheme.typography.labelSmall,
                color = if ((accessPoint.ageMillis ?: Long.MAX_VALUE) > 30_000L) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(0.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(0.65f), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun requiredWifiPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}.toTypedArray()

private fun hasWifiPermissions(context: Context): Boolean = requiredWifiPermissions().all { permission ->
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun formatFreshness(ageMillis: Long?): String = when {
    ageMillis == null -> "age -"
    ageMillis < 1_000L -> "방금"
    ageMillis < 60_000L -> "${ageMillis / 1_000L}초 전"
    else -> "${ageMillis / 60_000L}분 전"
}

private fun rssiColor(rssi: Int): Color = when {
    rssi >= -50 -> Color(0xFF16A34A)
    rssi >= -60 -> Color(0xFF65A30D)
    rssi >= -67 -> Color(0xFFD97706)
    rssi >= -70 -> Color(0xFFEA580C)
    else -> Color(0xFFDC2626)
}
