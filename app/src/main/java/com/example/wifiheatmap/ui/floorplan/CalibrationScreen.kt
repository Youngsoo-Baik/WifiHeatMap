package com.example.wifiheatmap.ui.floorplan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifiheatmap.viewmodel.FloorPlanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    onBack: () -> Unit,
    onOpenSurvey: () -> Unit,
    viewModel: FloorPlanViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var actualDistance by remember { mutableStateOf("") }
    var resetToken by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("← 뒤로") } },
                title = {
                    Column {
                        Text("거리 보정")
                        Text(
                            "Phase 3 · Pixel → Meter",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "평면도에서 실제 거리를 아는 두 지점을 차례로 선택하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                        markers = listOfNotNull(
                            uiState.calibrationFirstPoint?.let { FloorPlanMarker(it, Color(0xFF2563EB), "A") },
                            uiState.calibrationSecondPoint?.let { FloorPlanMarker(it, Color(0xFFDC2626), "B") },
                        ),
                        resetToken = resetToken,
                        onPointSelected = viewModel::selectCalibrationPoint,
                    )
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("기준 거리", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = actualDistance,
                        onValueChange = { actualDistance = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("실제 거리 (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    uiState.calibrationError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    uiState.calibration?.let { calibration ->
                        Text("픽셀 거리: %.1f px".format(calibration.pixelDistance))
                        Text("축척: %.5f m/px".format(calibration.metersPerPixel))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.resetCalibrationPoints()
                                resetToken++
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("다시 선택") }
                        Button(
                            onClick = { viewModel.calibrate(actualDistance.toDoubleOrNull()) },
                            modifier = Modifier.weight(1f),
                        ) { Text("축척 계산") }
                    }
                }
            }
            Button(
                onClick = onOpenSurvey,
                enabled = uiState.calibration != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("다음 · 벽 자동 인식") }
        }
    }
}
