package com.example.wifiheatmap.ui.survey

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifiheatmap.ui.floorplan.FloorPlanCanvas
import com.example.wifiheatmap.ui.floorplan.FloorPlanMarker
import com.example.wifiheatmap.viewmodel.FloorPlanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: FloorPlanViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var resetToken by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("← 뒤로") } },
                title = {
                    Column {
                        Text("신호 측정")
                        Text("Phase 4–5 · RSSI 중앙값", style = MaterialTheme.typography.labelSmall)
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                uiState.bitmap?.let { bitmap ->
                    FloorPlanCanvas(
                        bitmap = bitmap,
                        markers = uiState.measurements.map { measurement ->
                            FloorPlanMarker(
                                point = measurement.point,
                                color = signalColor(measurement.medianRssi),
                                label = "${measurement.medianRssi}",
                            )
                        } + listOfNotNull(
                            uiState.selectedPoint?.let { FloorPlanMarker(it, Color(0xFF111827), "+") },
                        ),
                        resetToken = resetToken,
                        onPointSelected = viewModel::selectPoint,
                    )
                }
                if (uiState.isMeasuring) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xEFFFFFFF))) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text("3초 동안 신호 측정 중…")
                        }
                    }
                }
            }
            uiState.surveyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("측정 결과 ${uiState.measurements.size}개", fontWeight = FontWeight.Bold)
                    Text(
                        uiState.measurements.lastOrNull()?.let {
                            "최근: ${it.ssid ?: "SSID 미확인"} / ${it.bssid ?: "BSSID 미확인"} / ${it.medianRssi} dBm (${it.samples.size}회)"
                        } ?: "평면도에서 측정 위치를 선택하세요.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.deleteNearestMeasurement() },
                            modifier = Modifier.weight(1f),
                            enabled = uiState.measurements.isNotEmpty() && !uiState.isMeasuring,
                        ) { Text("선택점 근처 삭제") }
                        Button(
                            onClick = { viewModel.measureSelectedPoint() },
                            modifier = Modifier.weight(1f),
                            enabled = uiState.selectedPoint != null && !uiState.isMeasuring,
                        ) { Text("이 위치 측정") }
                    }
                }
            }
            Button(
                onClick = onDone,
                enabled = uiState.measurements.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("측정 완료 · 결과 보기") }
        }
    }
}

private fun signalColor(rssi: Int): Color = when {
    rssi >= -55 -> Color(0xFF16A34A)
    rssi >= -67 -> Color(0xFFEAB308)
    rssi >= -75 -> Color(0xFFF97316)
    else -> Color(0xFFDC2626)
}
