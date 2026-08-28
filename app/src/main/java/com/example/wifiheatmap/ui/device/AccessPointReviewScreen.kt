package com.example.wifiheatmap.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifiheatmap.ui.floorplan.FloorPlanCanvas
import com.example.wifiheatmap.ui.floorplan.FloorPlanMarker
import com.example.wifiheatmap.viewmodel.FloorPlanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessPointReviewScreen(
    onBackHome: () -> Unit,
    onDone: () -> Unit,
    viewModel: FloorPlanViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val estimatedDevices = uiState.devices.filter { it.automaticallyEstimated == true }
    var selectedDeviceId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(estimatedDevices) {
        if (estimatedDevices.none { it.id == selectedDeviceId }) {
            selectedDeviceId = estimatedDevices.firstOrNull { it.userConfirmed != true }?.id
                ?: estimatedDevices.firstOrNull()?.id
        }
    }
    val selectedDevice = estimatedDevices.firstOrNull { it.id == selectedDeviceId }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBackHome) { Text("← 홈") } },
                title = {
                    Column {
                        Text("추정 AP 검토")
                        Text("필요한 위치만 수정하세요", style = MaterialTheme.typography.labelSmall)
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                estimatedDevices.forEach { device ->
                    OutlinedButton(
                        onClick = { selectedDeviceId = device.id },
                        enabled = device.id != selectedDeviceId,
                    ) {
                        Text((if (device.userConfirmed == true) "✓ " else "") + device.name)
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .background(Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                uiState.bitmap?.let { bitmap ->
                    FloorPlanCanvas(
                        bitmap = bitmap,
                        markers = estimatedDevices.map { device ->
                            FloorPlanMarker(
                                point = device.point,
                                color = if (device.id == selectedDeviceId) Color(0xFFF59E0B) else Color(0xFF2563EB),
                                label = device.name,
                            )
                        },
                        walls = uiState.walls,
                        resetToken = 0,
                        onPointSelected = { point ->
                            selectedDeviceId?.let { viewModel.updateEstimatedDevicePosition(it, point) }
                        },
                    )
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (selectedDevice == null) {
                        Text("검토할 자동 추정 AP가 없습니다.")
                    } else {
                        Text(selectedDevice.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "BSSID ${selectedDevice.mappedBssids.size}개 · 위치 신뢰도 " +
                                selectedDevice.positionConfidence?.let { "%.0f%%".format(it * 100) }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text("위치가 다르면 평면도에서 실제 AP 위치를 탭한 뒤 확인하세요.", style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { selectedDeviceId?.let(viewModel::confirmEstimatedDevice) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (selectedDevice.userConfirmed == true) "확인 완료" else "이 AP 위치 확인") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::confirmAllEstimatedDevices,
                    modifier = Modifier.weight(1f),
                    enabled = estimatedDevices.isNotEmpty(),
                ) { Text("모두 확인") }
                Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text("저장하고 홈으로") }
            }
        }
    }
}
