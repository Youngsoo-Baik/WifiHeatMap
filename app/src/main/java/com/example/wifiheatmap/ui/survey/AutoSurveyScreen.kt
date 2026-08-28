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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wifiheatmap.ui.floorplan.FloorPlanCanvas
import com.example.wifiheatmap.ui.floorplan.FloorPlanMarker
import com.example.wifiheatmap.viewmodel.AutoSurveyViewModel
import com.example.wifiheatmap.viewmodel.FloorPlanViewModel
import com.example.wifiheatmap.wifi.hasWifiPermissions
import com.example.wifiheatmap.wifi.requiredWifiPermissions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoSurveyScreen(
    onBackHome: () -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenManualSurvey: () -> Unit,
    floorPlanViewModel: FloorPlanViewModel,
    trackingViewModel: AutoSurveyViewModel = viewModel(),
) {
    val floorPlanState by floorPlanViewModel.uiState.collectAsStateWithLifecycle()
    val trackingState by trackingViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var wifiPermissionsGranted by remember { mutableStateOf(hasWifiPermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        wifiPermissionsGranted = hasWifiPermissions(context)
    }

    DisposableEffect(Unit) {
        onDispose { trackingViewModel.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    TextButton(onClick = {
                        trackingViewModel.stop()
                        onBackHome()
                    }) { Text("← 홈") }
                },
                title = {
                    Column {
                        Text("자동 이동 측정")
                        Text("PDR 이동 경로 · Wi-Fi 자동 관측", style = MaterialTheme.typography.labelSmall)
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (trackingState.isTracking) "스마트폰을 들고 집 안을 천천히 걸어 주세요."
                else "평면도에서 시작 위치와 바라보는 방향 지점을 순서대로 탭하세요.",
                style = MaterialTheme.typography.bodySmall,
            )
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                floorPlanState.bitmap?.let { bitmap ->
                    FloorPlanCanvas(
                        bitmap = bitmap,
                        markers = listOfNotNull(
                            trackingState.startPoint?.let { FloorPlanMarker(it, Color(0xFF2563EB), "시작") },
                            trackingState.directionPoint?.let { FloorPlanMarker(it, Color(0xFF7C3AED), "방향") },
                            trackingState.path.lastOrNull()?.takeIf { trackingState.isTracking }?.let {
                                FloorPlanMarker(it, Color(0xFF0891B2), "현재")
                            },
                        ),
                        trackingPath = trackingState.path,
                        walls = floorPlanState.walls,
                        resetToken = 0,
                        onPointSelected = trackingViewModel::selectPosePoint,
                    )
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        if (trackingState.isTracking) {
                            "걸음 ${trackingState.stepCount} · 이동 %.1fm · 신뢰도 %.0f%%\nWi-Fi 관측 ${trackingState.observationCount} · BSSID ${trackingState.detectedBssidCount}개".format(
                                trackingState.distanceMeters,
                                trackingState.averageConfidence * 100,
                            )
                        } else {
                            "PDR 센서 ${if (trackingState.providerSupported) "사용 가능" else "미지원"} · 축척 ${if (floorPlanState.calibration != null) "완료" else "필요"}"
                        },
                    )
                    trackingState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!wifiPermissionsGranted) {
                        Text("Wi-Fi 자동 수집을 위해 위치/근처 기기 권한이 필요합니다.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (trackingState.isTracking) {
                Button(
                    onClick = {
                        val measurements = trackingViewModel.stopAndBuildMeasurements()
                        floorPlanViewModel.addAutomaticMeasurements(measurements)
                        floorPlanViewModel.saveProject()
                        onBackHome()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("경로 기록 종료 · 홈으로") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = trackingViewModel::resetPose, modifier = Modifier.weight(1f)) {
                        Text("위치 다시 선택")
                    }
                    Button(
                        onClick = {
                            if (!wifiPermissionsGranted) {
                                permissionLauncher.launch(requiredWifiPermissions())
                                return@Button
                            }
                            val bitmap = floorPlanState.bitmap ?: return@Button
                            trackingViewModel.start(
                                bitmap.width,
                                bitmap.height,
                                floorPlanState.calibration?.metersPerPixel,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("자동 측정 시작") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenCalibration, modifier = Modifier.weight(1f)) { Text("거리 보정") }
                    OutlinedButton(onClick = onOpenManualSurvey, modifier = Modifier.weight(1f)) { Text("수동 측정") }
                }
            }
        }
    }
}
