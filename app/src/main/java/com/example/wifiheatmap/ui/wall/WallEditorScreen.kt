package com.example.wifiheatmap.ui.wall

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
fun WallEditorScreen(onBack: () -> Unit, onNext: () -> Unit, viewModel: FloorPlanViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("← 뒤로") } },
                title = { Column { Text("벽 편집"); Text("Phase 8 · Canny/HoughLinesP", style = MaterialTheme.typography.labelSmall) } },
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
                        markers = listOfNotNull(
                            uiState.wallStartPoint?.let { FloorPlanMarker(it, Color(0xFF7C3AED), "시작") },
                            uiState.selectedPoint?.let { FloorPlanMarker(it, Color.Black) },
                        ),
                        walls = uiState.walls,
                        resetToken = 0,
                        onPointSelected = viewModel::selectWallPoint,
                    )
                }
                if (uiState.isDetectingWalls) CircularProgressIndicator()
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("벽 ${uiState.walls.size}개 · 두 지점을 탭하면 수동 벽 추가")
                    uiState.wallError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = viewModel::detectWalls, modifier = Modifier.weight(1f)) { Text("자동 감지") }
                        OutlinedButton(onClick = viewModel::deleteNearestWall, modifier = Modifier.weight(1f)) { Text("근처 삭제") }
                        OutlinedButton(onClick = viewModel::toggleNearestWallOpening, modifier = Modifier.weight(1f)) { Text("개구부 전환") }
                    }
                }
            }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("다음 · 벽 반영 히트맵") }
        }
    }
}
