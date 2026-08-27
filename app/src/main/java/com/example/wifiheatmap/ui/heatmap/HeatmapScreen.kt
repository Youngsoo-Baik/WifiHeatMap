package com.example.wifiheatmap.ui.heatmap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.wifiheatmap.heatmap.HeatmapSample
import com.example.wifiheatmap.heatmap.IdwHeatmap
import com.example.wifiheatmap.ui.floorplan.FloorPlanCanvas
import com.example.wifiheatmap.ui.floorplan.FloorPlanMarker
import com.example.wifiheatmap.viewmodel.FloorPlanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: FloorPlanViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDevice = uiState.devices.firstOrNull { it.id == uiState.selectedHeatmapDeviceId }
    val filteredMeasurements = remember(uiState.measurements, selectedDevice) {
        if (selectedDevice == null) uiState.measurements else uiState.measurements.filter {
            it.bssid?.lowercase() in selectedDevice.bssids
        }
    }
    val heatmap = remember(filteredMeasurements) {
        filteredMeasurements.takeIf { it.isNotEmpty() }?.let { measurements ->
            IdwHeatmap.generate(measurements.map { HeatmapSample(it.point, it.medianRssi.toDouble()) })
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("← 뒤로") } },
                title = { Column { Text("Wi-Fi 히트맵"); Text("Phase 6 · IDW", style = MaterialTheme.typography.labelSmall) } },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (uiState.devices.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedHeatmapFilter("전체", selectedDevice == null) { viewModel.selectHeatmapDevice(null) }
                    uiState.devices.take(3).forEach { device ->
                        OutlinedHeatmapFilter(device.name, selectedDevice?.id == device.id) {
                            viewModel.selectHeatmapDevice(device.id)
                        }
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                uiState.bitmap?.let { bitmap ->
                    FloorPlanCanvas(
                        bitmap = bitmap,
                        markers = filteredMeasurements.map { FloorPlanMarker(it.point, Color.White, "${it.medianRssi}") },
                        heatmap = heatmap,
                        resetToken = 0,
                        onPointSelected = viewModel::selectPoint,
                    )
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    LegendItem(Color(0xFF16A34A), "강함 ≥ -55")
                    LegendItem(Color(0xFFEAB308), "양호")
                    LegendItem(Color(0xFFDC2626), "음영 < -75")
                    LegendItem(Color.Gray, "미측정")
                }
            }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("다음 · 장비 설정") }
        }
    }
}

@Composable
private fun OutlinedHeatmapFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, enabled = !selected) { Text(label) }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
