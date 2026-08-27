package com.example.wifiheatmap.ui.heatmap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifiheatmap.heatmap.HybridHeatmap
import com.example.wifiheatmap.ui.floorplan.FloorPlanCanvas
import com.example.wifiheatmap.ui.floorplan.FloorPlanMarker
import com.example.wifiheatmap.viewmodel.FloorPlanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HybridHeatmapScreen(onBack: () -> Unit, onNext: () -> Unit, viewModel: FloorPlanViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val device = uiState.devices.firstOrNull { it.id == uiState.selectedHeatmapDeviceId } ?: uiState.devices.firstOrNull()
    val measurements = remember(uiState.measurements, device) {
        if (device == null || device.mappedBssids.isEmpty()) uiState.measurements else uiState.measurements.filter {
            it.bssid?.lowercase() in device.mappedBssids
        }
    }
    val heatmap = remember(device, measurements, uiState.walls, uiState.calibration, uiState.bitmap) {
        val bitmap = uiState.bitmap
        val calibration = uiState.calibration
        if (device != null && bitmap != null && calibration != null) {
            HybridHeatmap.generate(
                device.point, measurements, uiState.walls, bitmap.width, bitmap.height, calibration.metersPerPixel,
            )
        } else null
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("← 뒤로") } },
                title = { Column { Text("하이브리드 히트맵"); Text("Phase 9 · 전파모델 + 잔차 IDW", style = MaterialTheme.typography.labelSmall) } },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("장비: ${device?.name ?: "미설정"} · 벽 ${uiState.walls.count { !it.isOpening }}개 반영")
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                uiState.bitmap?.let { bitmap ->
                    FloorPlanCanvas(
                        bitmap = bitmap,
                        markers = listOfNotNull(device?.let { FloorPlanMarker(it.point, Color(0xFF2563EB), it.name) }),
                        heatmap = heatmap,
                        walls = uiState.walls,
                        resetToken = 0,
                        onPointSelected = viewModel::selectPoint,
                    )
                }
                if (heatmap == null) Text("축척과 장비를 먼저 설정하세요.")
            }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("다음 · Mesh 분석") }
        }
    }
}
