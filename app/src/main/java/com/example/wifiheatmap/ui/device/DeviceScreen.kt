package com.example.wifiheatmap.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifiheatmap.data.model.WifiBand
import com.example.wifiheatmap.device.WifiDeviceType
import com.example.wifiheatmap.ui.floorplan.FloorPlanCanvas
import com.example.wifiheatmap.ui.floorplan.FloorPlanMarker
import com.example.wifiheatmap.viewmodel.FloorPlanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: FloorPlanViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var bssids by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(WifiDeviceType.ROUTER) }
    LaunchedEffect(Unit) { viewModel.scanDeviceCandidates() }
    val candidates = remember(uiState.measurements, uiState.deviceCandidates) {
        (uiState.measurements.flatMap { measurement ->
            listOfNotNull(
                measurement.bssid?.let { Triple(it, measurement.ssid, measurement.frequencyMhz) },
            ) + measurement.nearbyAccessPoints.map { Triple(it.bssid, it.ssid, it.frequencyMhz) }
        } + uiState.deviceCandidates.map { Triple(it.bssid, it.ssid, it.frequencyMhz) })
            .distinctBy { it.first.lowercase() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("← 뒤로") } },
                title = { Column { Text("네트워크 장비"); Text("Phase 7 · 장비/BSSID 매핑", style = MaterialTheme.typography.labelSmall) } },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                uiState.bitmap?.let { bitmap ->
                    FloorPlanCanvas(
                        bitmap = bitmap,
                        markers = uiState.devices.map { device ->
                            FloorPlanMarker(device.point, deviceColor(device.type), device.name)
                        } + listOfNotNull(uiState.selectedPoint?.let { FloorPlanMarker(it, Color.Black, "+") }),
                        resetToken = 0,
                        onPointSelected = viewModel::selectPoint,
                    )
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WifiDeviceType.entries.forEach { candidate ->
                            OutlinedButton(
                                onClick = { type = candidate },
                                enabled = type != candidate,
                                modifier = Modifier.weight(1f),
                            ) { Text(candidate.displayName, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("장비 이름") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("감지된 BSSID 후보 · 탭하여 추가", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = viewModel::scanDeviceCandidates, enabled = !uiState.isScanningDeviceCandidates) {
                            Text(if (uiState.isScanningDeviceCandidates) "검색 중…" else "다시 검색")
                        }
                    }
                    uiState.deviceCandidateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(candidates) { (bssid, ssid, frequency) ->
                            val currentBssids = bssids.split(',').map { it.trim().lowercase() }
                            val isSelected = bssid.lowercase() in currentBssids
                            BssidCandidateChip(
                                ssid = ssid,
                                bssid = bssid,
                                frequencyMhz = frequency,
                                isSelected = isSelected,
                                onClick = {
                                    val current = bssids.split(',').map { it.trim() }.filter { it.isNotBlank() }
                                    if (bssid !in current) {
                                        bssids = (current + bssid).joinToString(", ")
                                        if (name.isBlank()) {
                                            name = ssid ?: "AP-${bssid.takeLast(5).replace(":", "")}"
                                        }
                                    }
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = bssids,
                        onValueChange = { bssids = it },
                        label = { Text("BSSID (쉼표로 복수 입력)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = viewModel::deleteNearestDevice,
                            modifier = Modifier.weight(1f),
                            enabled = uiState.devices.isNotEmpty(),
                        ) { Text("근처 장비 삭제") }
                        Button(
                            onClick = {
                                viewModel.addDevice(name, type, bssids)
                                name = ""
                                bssids = ""
                            },
                            modifier = Modifier.weight(1f),
                            enabled = uiState.selectedPoint != null && name.isNotBlank(),
                        ) { Text("장비 추가") }
                    }
                }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("적용하고 홈으로") }
        }
    }
}

@Composable
private fun BssidCandidateChip(
    ssid: String?,
    bssid: String,
    frequencyMhz: Int?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color(0xFFF1F5F9),
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = ssid ?: "숨김 네트워크",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = bssid,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            frequencyMhz?.let {
                val band = WifiBand.fromFrequency(it)
                Text(
                    text = "${band.displayName} · $it MHz",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

private fun deviceColor(type: WifiDeviceType): Color = when (type) {
    WifiDeviceType.ROUTER -> Color(0xFF2563EB)
    WifiDeviceType.MESH_MAIN -> Color(0xFF7C3AED)
    WifiDeviceType.MESH_NODE -> Color(0xFF9333EA)
    WifiDeviceType.EXTENDER -> Color(0xFF0891B2)
}
