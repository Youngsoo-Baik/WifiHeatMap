package com.example.wifiheatmap.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifiheatmap.viewmodel.FloorPlanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackHome: () -> Unit,
    onOpenFloorPlan: () -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenWalls: () -> Unit,
    onOpenDevices: () -> Unit,
    viewModel: FloorPlanViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showNewProjectConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBackHome) { Text("← 홈") } },
                title = { Text("설정 및 프로젝트") },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingCard("평면도", uiState.sourceName, onOpenFloorPlan)
            SettingCard(
                "거리 보정",
                if (uiState.calibration == null) "설정 필요 · Coverage 정확도에 사용" else "완료 · %.5f m/px".format(uiState.calibration?.metersPerPixel),
                onOpenCalibration,
            )
            SettingCard("벽", "${uiState.walls.size}개 · 자동 검출 및 수정", onOpenWalls)
            SettingCard("Wi-Fi 장비", "${uiState.devices.size}개 · 위치와 BSSID", onOpenDevices)

            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("프로젝트", style = MaterialTheme.typography.titleMedium)
                    Text(
                        uiState.projectMessage ?: if (uiState.hasSavedProject) "저장된 프로젝트 있음" else "아직 저장되지 않음",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::saveProject,
                            enabled = !uiState.isProjectSaving,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (uiState.isProjectSaving) "저장 중…" else "지금 저장") }
                        OutlinedButton(
                            onClick = viewModel::loadProject,
                            enabled = !uiState.isProjectLoading,
                            modifier = Modifier.weight(1f),
                        ) { Text("다시 불러오기") }
                    }
                    OutlinedButton(
                        onClick = { showNewProjectConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("새 프로젝트") }
                }
            }
        }
    }

    if (showNewProjectConfirmation) {
        AlertDialog(
            onDismissRequest = { showNewProjectConfirmation = false },
            title = { Text("새 프로젝트를 시작할까요?") },
            text = { Text("현재 기기에 저장된 프로젝트와 측정 결과가 삭제됩니다.") },
            confirmButton = {
                Button(onClick = {
                    showNewProjectConfirmation = false
                    viewModel.newProject()
                    onBackHome()
                }) { Text("새로 시작") }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectConfirmation = false }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun SettingCard(title: String, description: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
