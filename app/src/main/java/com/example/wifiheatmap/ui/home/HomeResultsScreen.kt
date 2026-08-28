package com.example.wifiheatmap.ui.home

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
import androidx.compose.material3.Slider
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
import com.example.wifiheatmap.coverage.CoverageRadiusEstimator
import com.example.wifiheatmap.coverage.ResultView
import com.example.wifiheatmap.coverage.SignalAggregation
import com.example.wifiheatmap.coverage.SignalSourceMode
import com.example.wifiheatmap.data.model.WifiBand
import com.example.wifiheatmap.device.WifiDevice
import com.example.wifiheatmap.heatmap.HeatmapSample
import com.example.wifiheatmap.heatmap.HybridHeatmap
import com.example.wifiheatmap.heatmap.IdwHeatmap
import com.example.wifiheatmap.mesh.MeshAnalyzer
import com.example.wifiheatmap.ui.floorplan.FloorPlanCanvas
import com.example.wifiheatmap.ui.floorplan.FloorPlanCoverageRing
import com.example.wifiheatmap.ui.floorplan.FloorPlanMarker
import com.example.wifiheatmap.viewmodel.FloorPlanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeResultsScreen(
    onStartAutoSurvey: () -> Unit,
    onStartManualSurvey: () -> Unit,
    onReviewAccessPoints: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: FloorPlanViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDevice = uiState.devices.firstOrNull { it.id == uiState.selectedHeatmapDeviceId }
        ?: uiState.devices.firstOrNull()
    val effectiveSourceMode = when (uiState.resultView) {
        ResultView.COVERAGE -> SignalSourceMode.DEVICE
        ResultView.MESH -> SignalSourceMode.MESH_BEST
        else -> uiState.signalSourceMode
    }
    val signals = remember(
        uiState.measurements, uiState.devices, effectiveSourceMode,
        uiState.selectedHeatmapDeviceId, uiState.selectedBand,
    ) {
        SignalAggregation.aggregate(
            uiState.measurements,
            uiState.devices,
            effectiveSourceMode,
            selectedDevice?.id,
            uiState.selectedBand,
        )
    }
    val idwHeatmap = remember(signals) {
        signals.takeIf { it.isNotEmpty() }?.let {
            IdwHeatmap.generate(
                it.map { signal -> HeatmapSample(signal.point, signal.rssi.toDouble()) },
                columns = 100,
                rows = 100,
            )
        }
    }
    val hybridHeatmap = remember(
        selectedDevice, uiState.measurements, uiState.walls, uiState.calibration, uiState.bitmap,
    ) {
        val device = selectedDevice
        val bitmap = uiState.bitmap
        val calibration = uiState.calibration
        if (device != null && bitmap != null && calibration != null) {
            val measurements = uiState.measurements.filter { measurement ->
                measurement.bssid?.lowercase() in device.mappedBssids
            }
            HybridHeatmap.generate(
                transmitter = device.point,
                measurements = measurements,
                walls = uiState.walls,
                bitmapWidth = bitmap.width,
                bitmapHeight = bitmap.height,
                metersPerPixel = calibration.metersPerPixel,
                columns = 100,
                rows = 100,
            )
        } else null
    }
    val displayedHeatmap = when {
        uiState.resultView == ResultView.COVERAGE -> null
        uiState.resultView == ResultView.HEATMAP &&
            uiState.useWallAwareHeatmap &&
            effectiveSourceMode == SignalSourceMode.DEVICE -> hybridHeatmap ?: idwHeatmap
        else -> idwHeatmap
    }
    val coverage = remember(selectedDevice, signals, uiState.bitmap, uiState.calibration, uiState.selectedBand) {
        val bitmap = uiState.bitmap
        val calibration = uiState.calibration
        if (selectedDevice != null && bitmap != null && calibration != null) {
            CoverageRadiusEstimator.estimate(
                selectedDevice,
                signals,
                bitmap.width,
                bitmap.height,
                calibration.metersPerPixel,
                uiState.selectedBand,
            )
        } else null
    }
    val rings = remember(coverage, selectedDevice, uiState.calibration) {
        if (coverage == null || selectedDevice == null) emptyList() else {
            val metersPerPixel = uiState.calibration?.metersPerPixel ?: return@remember emptyList()
            listOfNotNull(
                coverage.usableRadiusM?.let {
                    FloorPlanCoverageRing(selectedDevice.point, (it / metersPerPixel).toFloat(), Color(0xFF2563EB))
                },
                coverage.goodRadiusM?.let {
                    FloorPlanCoverageRing(selectedDevice.point, (it / metersPerPixel).toFloat(), Color(0xFFEAB308))
                },
                coverage.strongRadiusM?.let {
                    FloorPlanCoverageRing(selectedDevice.point, (it / metersPerPixel).toFloat(), Color(0xFF16A34A))
                },
            )
        }
    }
    val meshAnalyses = remember(uiState.measurements, uiState.devices) {
        uiState.measurements.map { MeshAnalyzer.analyze(it, uiState.devices) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.projectName.ifBlank { "우리집 Wi-Fi" })
                        Text(
                            if (uiState.isProjectLoading) "프로젝트 불러오는 중…" else "기본 결과 화면",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onOpenDiagnostics) { Text("진단") }
                    TextButton(onClick = onOpenSettings) { Text("설정") }
                },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ProjectOverviewCard(
                calibrationReady = uiState.calibration != null,
                wallCount = uiState.walls.size,
                deviceCount = uiState.devices.size,
                reviewCount = uiState.devices.count { it.automaticallyEstimated == true && it.userConfirmed != true },
                measurementCount = uiState.measurements.size,
                projectMessage = uiState.projectMessage,
                isSaving = uiState.isProjectSaving,
                onStartAutoSurvey = onStartAutoSurvey,
                onStartManualSurvey = onStartManualSurvey,
                onReviewAccessPoints = onReviewAccessPoints,
                onOpenSettings = onOpenSettings,
            )
            SelectorRow(ResultView.entries, uiState.resultView, { it.displayName }, viewModel::selectResultView)
            SelectorRow(
                listOf(null) + WifiBand.entries.filter { it != WifiBand.UNKNOWN },
                uiState.selectedBand,
                { it?.displayName ?: "All" },
                viewModel::selectBand,
            )
            if (uiState.resultView != ResultView.COVERAGE && uiState.resultView != ResultView.MESH) {
                SelectorRow(
                    SignalSourceMode.entries,
                    uiState.signalSourceMode,
                    { it.displayName },
                    viewModel::selectSignalSourceMode,
                )
            }
            if (effectiveSourceMode == SignalSourceMode.DEVICE && uiState.devices.isNotEmpty()) {
                SelectorRow<WifiDevice?>(uiState.devices, selectedDevice, { it?.name ?: "미선택" }) { device ->
                    viewModel.selectHeatmapDevice(device?.id)
                }
            }
            if (uiState.resultView == ResultView.HEATMAP && effectiveSourceMode == SignalSourceMode.DEVICE) {
                ToggleButton("벽 반영 Hybrid", uiState.useWallAwareHeatmap, viewModel::toggleWallAwareHeatmap)
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                uiState.bitmap?.let { bitmap ->
                    FloorPlanCanvas(
                        bitmap = bitmap,
                        markers = buildList {
                            if (uiState.showDevices) {
                                addAll(uiState.devices.map { device ->
                                    val confidence = device.positionConfidence?.let { " %.0f%%".format(it * 100) }.orEmpty()
                                    val color = if (device.automaticallyEstimated == true && device.userConfirmed != true) {
                                        Color(0xFFF59E0B)
                                    } else {
                                        Color(0xFF2563EB)
                                    }
                                    FloorPlanMarker(device.point, color, device.name + confidence)
                                })
                            }
                            if (uiState.showMeasurements) {
                                addAll(signals.map { FloorPlanMarker(it.point, Color.White, "${it.rssi}") })
                            }
                        },
                        heatmap = displayedHeatmap,
                        coverageRings = rings.takeIf { uiState.resultView == ResultView.COVERAGE } ?: emptyList(),
                        walls = uiState.walls.takeIf { uiState.showWalls } ?: emptyList(),
                        deadZoneOnly = uiState.resultView == ResultView.WEAK_ZONE,
                        deadZoneThreshold = uiState.deadZoneThreshold,
                        resetToken = 0,
                        onPointSelected = viewModel::selectPoint,
                    )
                }
                if (signals.isEmpty()) {
                    Text(
                        if (uiState.measurements.isEmpty()) "측정을 시작하면 이곳에 결과가 표시됩니다."
                        else "선택 조건에 해당하는 측정 데이터가 없습니다.",
                    )
                }
            }
            when (uiState.resultView) {
                ResultView.COVERAGE -> CoverageSummary(coverage)
                ResultView.MESH -> MeshSummary(meshAnalyses.size, meshAnalyses.count { it.isRoamingCandidate })
                else -> ResultSummary(signals.map { it.rssi }, uiState.deadZoneThreshold)
            }
            if (uiState.resultView == ResultView.WEAK_ZONE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("음영 기준 ${uiState.deadZoneThreshold} dBm", modifier = Modifier.weight(1f))
                    Slider(
                        value = uiState.deadZoneThreshold.toFloat(),
                        onValueChange = { viewModel.setDeadZoneThreshold(it.toInt()) },
                        valueRange = -85f..-60f,
                        steps = 24,
                        modifier = Modifier.weight(2f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToggleButton("측정점", uiState.showMeasurements, viewModel::toggleMeasurements)
                ToggleButton("AP", uiState.showDevices, viewModel::toggleDevices)
                ToggleButton("벽", uiState.showWalls, viewModel::toggleWalls)
            }
        }
    }
}

@Composable
private fun ProjectOverviewCard(
    calibrationReady: Boolean,
    wallCount: Int,
    deviceCount: Int,
    reviewCount: Int,
    measurementCount: Int,
    projectMessage: String?,
    isSaving: Boolean,
    onStartAutoSurvey: () -> Unit,
    onStartManualSurvey: () -> Unit,
    onReviewAccessPoints: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "축척 ${if (calibrationReady) "완료" else "필요"} · 벽 ${wallCount} · AP ${deviceCount} · 측정 ${measurementCount}" +
                    if (reviewCount > 0) " · AP 확인 필요 ${reviewCount}" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            projectMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            Button(onClick = onStartAutoSurvey, modifier = Modifier.fillMaxWidth()) {
                Text(if (measurementCount == 0) "자동 측정 시작" else "자동 측정 계속")
            }
            if (reviewCount > 0) {
                OutlinedButton(onClick = onReviewAccessPoints, modifier = Modifier.fillMaxWidth()) {
                    Text("추정 AP ${reviewCount}개 확인")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onStartManualSurvey, modifier = Modifier.weight(1f)) {
                    Text("수동 위치 측정")
                }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                    Text(if (isSaving) "저장 중…" else "설정 및 프로젝트")
                }
            }
        }
    }
}

@Composable
private fun <T> SelectorRow(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            OutlinedButton(onClick = { onSelect(item) }, enabled = item != selected) { Text(label(item)) }
        }
    }
}

@Composable
private fun ToggleButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(if (enabled) "✓ $label" else label) }
}

@Composable
private fun CoverageSummary(coverage: com.example.wifiheatmap.coverage.DeviceCoverage?) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Text(
            coverage?.let {
                "예상 범위 · Strong ${it.strongRadiusM.formatMeters()} / Good ${it.goodRadiusM.formatMeters()} / Usable ${it.usableRadiusM.formatMeters()} · ${it.measurementCount}점 · 신뢰도 ${it.confidence.displayName}"
            } ?: "기본 화면은 사용할 수 있습니다. 정확한 Coverage에는 축척·AP·측정값이 필요합니다.",
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ResultSummary(values: List<Int>, threshold: Int) {
    val weakPercent = if (values.isEmpty()) 0 else values.count { it < threshold } * 100 / values.size
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Text(
            if (values.isEmpty()) "통계 없음"
            else "${values.size}점 · 평균 %.1f dBm · 최소 ${values.min()} / 최대 ${values.max()} · 음영 ${weakPercent}%".format(values.average()),
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MeshSummary(measurementCount: Int, roamingCandidateCount: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Text(
            "Mesh 분석 · 측정 ${measurementCount}점 · 로밍 확인 필요 ${roamingCandidateCount}곳",
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun Double?.formatMeters(): String = this?.let { "%.1fm".format(it) } ?: "-"
