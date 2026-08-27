package com.example.wifiheatmap.ui.mesh

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifiheatmap.mesh.MeshAnalyzer
import com.example.wifiheatmap.viewmodel.FloorPlanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshAnalysisScreen(onBack: () -> Unit, viewModel: FloorPlanViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val analyses = uiState.measurements.map { MeshAnalyzer.analyze(it, uiState.devices) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("← 뒤로") } },
                title = { Column { Text("Mesh 분석"); Text("Phase 10 · 연결 AP vs 최적 AP", style = MaterialTheme.typography.labelSmall) } },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("로밍 권장 위치 ${analyses.count { it.isRoamingCandidate }}곳", style = MaterialTheme.typography.titleMedium)
                    Text("다른 매핑 AP가 12 dB 이상 강하면 후보로 표시합니다.")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::saveProject, modifier = Modifier.weight(1f)) { Text("결과 저장") }
                OutlinedButton(onClick = viewModel::loadProject, modifier = Modifier.weight(1f)) { Text("결과 불러오기") }
            }
            uiState.projectMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(analyses, key = { it.measurementId }) { analysis ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (analysis.isRoamingCandidate) Color(0xFFFFF1F2) else Color.White,
                        ),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(if (analysis.isRoamingCandidate) "로밍 후보" else "현재 연결 적정")
                            Text("연결: ${analysis.connectedDevice?.name ?: "미매핑"} ${analysis.connectedRssi} dBm")
                            Text("최적: ${analysis.bestDevice?.name ?: "미확인"} ${analysis.bestRssi?.let { "$it dBm" } ?: "-"}")
                        }
                    }
                }
            }
        }
    }
}
