package com.example.wifiheatmap.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifiheatmap.calibration.CalibrationData
import com.example.wifiheatmap.calibration.CalibrationService
import com.example.wifiheatmap.floorplan.FloorPlanRepository
import com.example.wifiheatmap.floorplan.NormalizedPoint
import com.example.wifiheatmap.device.WifiDevice
import com.example.wifiheatmap.device.WifiDeviceType
import com.example.wifiheatmap.survey.RssiStatistics
import com.example.wifiheatmap.survey.SurveyMeasurement
import com.example.wifiheatmap.wifi.WifiRepository
import com.example.wifiheatmap.wifi.WifiScanner
import com.example.wifiheatmap.wall.WallDetector
import com.example.wifiheatmap.wall.WallSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FloorPlanUiState(
    val bitmap: Bitmap? = null,
    val sourceName: String = "기본 평면도",
    val selectedPoint: NormalizedPoint? = null,
    val calibrationFirstPoint: NormalizedPoint? = null,
    val calibrationSecondPoint: NormalizedPoint? = null,
    val calibration: CalibrationData? = null,
    val calibrationError: String? = null,
    val measurements: List<SurveyMeasurement> = emptyList(),
    val isMeasuring: Boolean = false,
    val surveyError: String? = null,
    val devices: List<WifiDevice> = emptyList(),
    val selectedHeatmapDeviceId: Long? = null,
    val walls: List<WallSegment> = emptyList(),
    val wallStartPoint: NormalizedPoint? = null,
    val isDetectingWalls: Boolean = false,
    val wallError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class FloorPlanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FloorPlanRepository(application)
    private val wifiRepository = WifiRepository(WifiScanner(application))
    private val mutableUiState = MutableStateFlow(
        FloorPlanUiState(bitmap = repository.loadDefault()),
    )
    val uiState: StateFlow<FloorPlanUiState> = mutableUiState.asStateFlow()

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { repository.load(uri) }
                .onSuccess { bitmap ->
                    mutableUiState.value = FloorPlanUiState(
                        bitmap = bitmap,
                        sourceName = uri.lastPathSegment?.substringAfterLast('/') ?: "선택한 평면도",
                    )
                }
                .onFailure { throwable ->
                    mutableUiState.value = mutableUiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "평면도 이미지를 불러오지 못했습니다.",
                    )
                }
        }
    }

    fun selectPoint(point: NormalizedPoint) {
        mutableUiState.value = mutableUiState.value.copy(selectedPoint = point)
    }

    fun selectCalibrationPoint(point: NormalizedPoint) {
        val state = mutableUiState.value
        mutableUiState.value = when {
            state.calibrationFirstPoint == null -> state.copy(
                calibrationFirstPoint = point,
                calibrationSecondPoint = null,
                calibration = null,
                calibrationError = null,
            )
            state.calibrationSecondPoint == null -> state.copy(
                calibrationSecondPoint = point,
                calibration = null,
                calibrationError = null,
            )
            else -> state.copy(
                calibrationFirstPoint = point,
                calibrationSecondPoint = null,
                calibration = null,
                calibrationError = null,
            )
        }
    }

    fun resetCalibrationPoints() {
        mutableUiState.value = mutableUiState.value.copy(
            calibrationFirstPoint = null,
            calibrationSecondPoint = null,
            calibration = null,
            calibrationError = null,
        )
    }

    fun calibrate(actualDistanceMeters: Double?) {
        val state = mutableUiState.value
        val bitmap = state.bitmap
        val firstPoint = state.calibrationFirstPoint
        val secondPoint = state.calibrationSecondPoint
        if (bitmap == null || firstPoint == null || secondPoint == null || actualDistanceMeters == null) {
            mutableUiState.value = state.copy(calibrationError = "두 지점과 실제 거리를 모두 입력하세요.")
            return
        }
        runCatching {
            CalibrationService.calculate(
                firstPoint = firstPoint,
                secondPoint = secondPoint,
                bitmapWidth = bitmap.width,
                bitmapHeight = bitmap.height,
                actualDistanceMeters = actualDistanceMeters,
            )
        }.onSuccess { calibration ->
            mutableUiState.value = state.copy(calibration = calibration, calibrationError = null)
        }.onFailure { error ->
            mutableUiState.value = state.copy(calibrationError = error.message)
        }
    }

    fun measureSelectedPoint() {
        val point = mutableUiState.value.selectedPoint ?: return
        if (mutableUiState.value.isMeasuring) return
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isMeasuring = true, surveyError = null)
            runCatching { wifiRepository.collectConnectedRssiSamples() }
                .onSuccess { (samples, snapshot) ->
                    if (samples.isEmpty()) {
                        mutableUiState.value = mutableUiState.value.copy(
                            isMeasuring = false,
                            surveyError = "연결된 Wi-Fi RSSI를 읽지 못했습니다. 위치 권한과 Wi-Fi 연결을 확인하세요.",
                        )
                        return@onSuccess
                    }
                    val connected = snapshot.connectedWifi
                    val measurement = SurveyMeasurement(
                        id = System.currentTimeMillis(),
                        point = point,
                        ssid = connected?.ssid,
                        bssid = connected?.bssid,
                        medianRssi = RssiStatistics.median(samples),
                        frequencyMhz = connected?.frequencyMhz,
                        samples = samples,
                        nearbyAccessPoints = snapshot.nearbyAccessPoints,
                        measuredAtMillis = snapshot.capturedAtMillis,
                    )
                    mutableUiState.value = mutableUiState.value.copy(
                        measurements = mutableUiState.value.measurements + measurement,
                        isMeasuring = false,
                    )
                }
                .onFailure { error ->
                    mutableUiState.value = mutableUiState.value.copy(
                        isMeasuring = false,
                        surveyError = error.message ?: "신호 측정에 실패했습니다.",
                    )
                }
        }
    }

    fun deleteNearestMeasurement() {
        val point = mutableUiState.value.selectedPoint ?: return
        val nearest = mutableUiState.value.measurements.minByOrNull { measurement ->
            val dx = measurement.point.x - point.x
            val dy = measurement.point.y - point.y
            dx * dx + dy * dy
        } ?: return
        mutableUiState.value = mutableUiState.value.copy(
            measurements = mutableUiState.value.measurements.filterNot { it.id == nearest.id },
        )
    }

    fun addDevice(name: String, type: WifiDeviceType, bssidText: String) {
        val point = mutableUiState.value.selectedPoint ?: return
        val device = WifiDevice(
            id = System.currentTimeMillis(),
            name = name.trim(),
            type = type,
            point = point,
            bssids = bssidText.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet(),
        )
        mutableUiState.value = mutableUiState.value.copy(devices = mutableUiState.value.devices + device)
    }

    fun deleteNearestDevice() {
        val point = mutableUiState.value.selectedPoint ?: return
        val nearest = mutableUiState.value.devices.minByOrNull {
            val dx = it.point.x - point.x
            val dy = it.point.y - point.y
            dx * dx + dy * dy
        } ?: return
        mutableUiState.value = mutableUiState.value.copy(
            devices = mutableUiState.value.devices.filterNot { it.id == nearest.id },
            selectedHeatmapDeviceId = mutableUiState.value.selectedHeatmapDeviceId.takeUnless { it == nearest.id },
        )
    }

    fun selectHeatmapDevice(deviceId: Long?) {
        mutableUiState.value = mutableUiState.value.copy(selectedHeatmapDeviceId = deviceId)
    }

    fun selectWallPoint(point: NormalizedPoint) {
        val state = mutableUiState.value
        val start = state.wallStartPoint
        if (start == null) {
            mutableUiState.value = state.copy(selectedPoint = point, wallStartPoint = point)
        } else {
            mutableUiState.value = state.copy(
                selectedPoint = point,
                wallStartPoint = null,
                walls = state.walls + WallSegment(System.nanoTime(), start, point),
            )
        }
    }

    fun detectWalls() {
        val bitmap = mutableUiState.value.bitmap ?: return
        if (mutableUiState.value.isDetectingWalls) return
        viewModelScope.launch(Dispatchers.Default) {
            mutableUiState.value = mutableUiState.value.copy(isDetectingWalls = true, wallError = null)
            runCatching { WallDetector.detect(bitmap) }
                .onSuccess { walls -> mutableUiState.value = mutableUiState.value.copy(walls = walls, isDetectingWalls = false) }
                .onFailure { error -> mutableUiState.value = mutableUiState.value.copy(isDetectingWalls = false, wallError = error.message) }
        }
    }

    fun deleteNearestWall() = updateNearestWall { null }

    fun toggleNearestWallOpening() = updateNearestWall { it.copy(isOpening = !it.isOpening) }

    private fun updateNearestWall(transform: (WallSegment) -> WallSegment?) {
        val point = mutableUiState.value.selectedPoint ?: return
        val nearest = mutableUiState.value.walls.minByOrNull { wall ->
            val dx = (wall.start.x + wall.end.x) / 2f - point.x
            val dy = (wall.start.y + wall.end.y) / 2f - point.y
            dx * dx + dy * dy
        } ?: return
        mutableUiState.value = mutableUiState.value.copy(
            walls = mutableUiState.value.walls.mapNotNull { if (it.id == nearest.id) transform(it) else it },
        )
    }
}
