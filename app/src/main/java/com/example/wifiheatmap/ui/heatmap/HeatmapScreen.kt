package com.example.wifiheatmap.ui.heatmap

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
import com.example.wifiheatmap.coverage.SignalAggregation
import com.example.wifiheatmap.coverage.ResultView
import com.example.wifiheatmap.coverage.SignalSourceMode
import com.example.wifiheatmap.data.model.WifiBand
import com.example.wifiheatmap.heatmap.HeatmapSample
import com.example.wifiheatmap.heatmap.IdwHeatmap
import com.example.wifiheatmap.ui.floorplan.FloorPlanCanvas
import com.example.wifiheatmap.ui.floorplan.FloorPlanCoverageRing
import com.example.wifiheatmap.ui.floorplan.FloorPlanMarker
import com.example.wifiheatmap.viewmodel.FloorPlanViewModel
import com.example.wifiheatmap.device.WifiDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapScreen(onBack: () -> Unit, onNext: () -> Unit, viewModel: FloorPlanViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDevice = uiState.devices.firstOrNull { it.id == uiState.selectedHeatmapDeviceId }
        ?: uiState.devices.firstOrNull()
    val signals = remember(
        uiState.measurements, uiState.devices, uiState.signalSourceMode,
        uiState.selectedHeatmapDeviceId, uiState.selectedBand,
    ) {
        SignalAggregation.aggregate(
            uiState.measurements,
            uiState.devices,
            uiState.signalSourceMode,
            selectedDevice?.id,
            uiState.selectedBand,
        )
    }
    val heatmap = remember(signals) {
        signals.takeIf { it.isNotEmpty() }?.let {
            IdwHeatmap.generate(it.map { signal -> HeatmapSample(signal.point, signal.rssi.toDouble()) }, columns = 100, rows = 100)
        }
    }
    val coverage = remember(selectedDevice, signals, uiState.bitmap, uiState.calibration, uiState.selectedBand) {
        val bitmap = uiState.bitmap
        val calibration = uiState.calibration
        if (selectedDevice != null && bitmap != null && calibration != null) {
            CoverageRadiusEstimator.estimate(
                selectedDevice, signals, bitmap.width, bitmap.height, calibration.metersPerPixel, uiState.selectedBand,
            )
        } else null
    }
    val rings = remember(coverage, selectedDevice, uiState.calibration) {
        if (coverage == null || selectedDevice == null) emptyList() else {
            val metersPerPixel = uiState.calibration?.metersPerPixel ?: return@remember emptyList()
            listOfNotNull(
                coverage.usableRadiusM?.let { FloorPlanCoverageRing(selectedDevice.point, (it / metersPerPixel).toFloat(), Color(0xFF2563EB)) },
                coverage.goodRadiusM?.let { FloorPlanCoverageRing(selectedDevice.point, (it / metersPerPixel).toFloat(), Color(0xFFEAB308)) },
                coverage.strongRadiusM?.let { FloorPlanCoverageRing(selectedDevice.point, (it / metersPerPixel).toFloat(), Color(0xFF16A34A)) },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("← 뒤로") } },
                title = { Column { Text("측정 결과"); Text("예상 Coverage · 실측 Heatmap", style = MaterialTheme.typography.labelSmall) } },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            SelectorRow(ResultView.entries, uiState.resultView, { it.displayName }, viewModel::selectResultView)
            SelectorRow(listOf(null) + WifiBand.entries.filter { it != WifiBand.UNKNOWN }, uiState.selectedBand, {
                it?.displayName ?: "All"
            }, viewModel::selectBand)
            SelectorRow(SignalSourceMode.entries, uiState.signalSourceMode, { it.displayName }, viewModel::selectSignalSourceMode)
            if (uiState.signalSourceMode == SignalSourceMode.DEVICE && uiState.devices.isNotEmpty()) {
                SelectorRow<WifiDevice?>(uiState.devices, selectedDevice, {
                    it?.name ?: "미선택"
                }) { device -> viewModel.selectHeatmapDevice(device?.id) }
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                uiState.bitmap?.let { bitmap ->
                    FloorPlanCanvas(
                        bitmap = bitmap,
                        markers = buildList {
                            if (uiState.showDevices) addAll(uiState.devices.map { FloorPlanMarker(it.point, Color(0xFF2563EB), it.name) })
                            if (uiState.showMeasurements) addAll(signals.map { FloorPlanMarker(it.point, Color.White, "${it.rssi}") })
                        },
                        heatmap = heatmap.takeIf { uiState.resultView != ResultView.COVERAGE },
                        coverageRings = rings.takeIf { uiState.resultView == ResultView.COVERAGE } ?: emptyList(),
                        walls = uiState.walls.takeIf { uiState.showWalls } ?: emptyList(),
                        deadZoneOnly = uiState.resultView == ResultView.WEAK_ZONE,
                        deadZoneThreshold = uiState.deadZoneThreshold,
                        resetToken = 0,
                        onPointSelected = viewModel::selectPoint,
                    )
                }
                if (signals.isEmpty()) Text("선택 조건에 해당하는 측정 데이터가 없습니다.")
            }
            if (uiState.resultView == ResultView.COVERAGE) {
                CoverageSummary(coverage)
            } else {
                ResultSummary(signals.map { it.rssi }, uiState.deadZoneThreshold)
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
                Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text("상세") }
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
            } ?: "장비·축척·측정값이 필요합니다.",
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
            if (values.isEmpty()) "통계 없음" else "${values.size}점 · 평균 %.1f dBm · 최소 ${values.min()} / 최대 ${values.max()} · 음영 ${weakPercent}%".format(values.average()),
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun Double?.formatMeters(): String = this?.let { "%.1fm".format(it) } ?: "-"
