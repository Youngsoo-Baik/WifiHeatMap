package com.example.wifiheatmap.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifiheatmap.analysis.AccessPointAnalyzer
import com.example.wifiheatmap.calibration.CalibrationData
import com.example.wifiheatmap.calibration.CalibrationService
import com.example.wifiheatmap.floorplan.FloorPlanRepository
import com.example.wifiheatmap.floorplan.NormalizedPoint
import com.example.wifiheatmap.device.WifiDevice
import com.example.wifiheatmap.device.WifiDeviceType
import com.example.wifiheatmap.device.WifiRadio
import com.example.wifiheatmap.data.model.WifiBand
import com.example.wifiheatmap.data.model.NearbyAccessPoint
import com.example.wifiheatmap.coverage.ResultView
import com.example.wifiheatmap.coverage.SignalSourceMode
import com.example.wifiheatmap.survey.RssiStatistics
import com.example.wifiheatmap.survey.SurveyMeasurement
import com.example.wifiheatmap.wifi.WifiRepository
import com.example.wifiheatmap.wifi.WifiScanner
import com.example.wifiheatmap.wall.WallDetector
import com.example.wifiheatmap.wall.WallSegment
import com.example.wifiheatmap.persistence.ProjectStore
import com.example.wifiheatmap.persistence.SavedProject
import com.example.wifiheatmap.persistence.ProjectSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FloorPlanUiState(
    val bitmap: Bitmap? = null,
    val sourceName: String = "기본 평면도",
    val projectName: String = "우리집",
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
    val wallMoveId: Long? = null,
    val projectMessage: String? = null,
    val resultView: ResultView = ResultView.COVERAGE,
    val selectedBand: WifiBand? = null,
    val signalSourceMode: SignalSourceMode = SignalSourceMode.DEVICE,
    val deadZoneThreshold: Int = -70,
    val showMeasurements: Boolean = true,
    val showDevices: Boolean = true,
    val showWalls: Boolean = true,
    val deviceCandidates: List<NearbyAccessPoint> = emptyList(),
    val isScanningDeviceCandidates: Boolean = false,
    val deviceCandidateError: String? = null,
    val useWallAwareHeatmap: Boolean = true,
    val isProjectLoading: Boolean = false,
    val isProjectSaving: Boolean = false,
    val hasSavedProject: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class FloorPlanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FloorPlanRepository(application)
    private val wifiRepository = WifiRepository(WifiScanner(application))
    private val projectStore = ProjectStore(application)
    private val mutableUiState = MutableStateFlow(
        FloorPlanUiState(bitmap = repository.loadDefault()),
    )
    val uiState: StateFlow<FloorPlanUiState> = mutableUiState.asStateFlow()

    init {
        restoreProject(showMissingMessage = false)
    }

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { repository.load(uri) }
                .onSuccess { bitmap ->
                    mutableUiState.value = mutableUiState.value.copy(
                        bitmap = bitmap,
                        sourceName = uri.lastPathSegment?.substringAfterLast('/') ?: "선택한 평면도",
                        selectedPoint = null,
                        calibration = null,
                        calibrationFirstPoint = null,
                        calibrationSecondPoint = null,
                        isLoading = false,
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

    fun setProjectName(name: String) {
        mutableUiState.value = mutableUiState.value.copy(projectName = name)
    }

    fun newProject() {
        mutableUiState.value = FloorPlanUiState(bitmap = repository.loadDefault())
        viewModelScope.launch(Dispatchers.IO) { projectStore.clear() }
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
                        devices = enrichDeviceRadios(mutableUiState.value.devices, measurement),
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

    fun addAutomaticMeasurements(newMeasurements: List<SurveyMeasurement>) {
        if (newMeasurements.isEmpty()) {
            mutableUiState.value = mutableUiState.value.copy(
                surveyError = "자동 측정에서 저장 가능한 Wi-Fi 관측을 수집하지 못했습니다.",
            )
            return
        }
        val existingIds = mutableUiState.value.measurements.map { it.id }.toSet()
        val uniqueMeasurements = newMeasurements.filterNot { it.id in existingIds }
        val enrichedDevices = uniqueMeasurements.fold(mutableUiState.value.devices) { devices, measurement ->
            enrichDeviceRadios(devices, measurement)
        }
        val allMeasurements = mutableUiState.value.measurements + uniqueMeasurements
        val analyzedDevices = mergeAutomaticDevices(enrichedDevices, allMeasurements)
        mutableUiState.value = mutableUiState.value.copy(
            measurements = allMeasurements,
            devices = analyzedDevices,
            surveyError = null,
            projectMessage = "자동 측정 ${uniqueMeasurements.size}개 위치와 AP 후보 ${analyzedDevices.count { it.automaticallyEstimated == true }}개를 추가했습니다.",
        )
    }

    private fun mergeAutomaticDevices(
        currentDevices: List<WifiDevice>,
        measurements: List<SurveyMeasurement>,
    ): List<WifiDevice> {
        val manualDevices = currentDevices.filter { it.automaticallyEstimated != true }
        val manualBssids = manualDevices.flatMap { it.mappedBssids }.toSet()
        val previousAutomatic = currentDevices.filter { it.automaticallyEstimated == true }
        val candidates = AccessPointAnalyzer.analyze(measurements).filter { candidate ->
            candidate.radios.none { it.bssid.lowercase() in manualBssids }
        }
        val automaticDevices = candidates.map { candidate ->
            val candidateBssids = candidate.radios.map { it.bssid.lowercase() }.toSet()
            val previous = previousAutomatic.firstOrNull { device ->
                device.mappedBssids.intersect(candidateBssids).isNotEmpty()
            }
            WifiDevice(
                id = previous?.id ?: (candidate.id.hashCode().toLong() and Long.MAX_VALUE),
                name = previous?.name ?: candidate.name,
                type = previous?.type ?: WifiDeviceType.ROUTER,
                point = previous?.point?.takeIf { previous.userConfirmed == true } ?: candidate.estimatedPoint,
                bssids = candidateBssids,
                radios = candidate.radios,
                positionConfidence = candidate.positionConfidence,
                clusterConfidence = candidate.clusterConfidence,
                automaticallyEstimated = true,
                userConfirmed = previous?.userConfirmed ?: false,
            )
        }
        return manualDevices + automaticDevices
    }

    fun addDevice(name: String, type: WifiDeviceType, bssidText: String) {
        val point = mutableUiState.value.selectedPoint ?: return
        val normalizedBssids = bssidText.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        val observedRadios = mutableUiState.value.measurements.flatMap { measurement ->
            val connected = measurement.bssid?.lowercase()?.takeIf { it in normalizedBssids }?.let {
                WifiRadio(it, measurement.ssid, WifiBand.fromFrequency(measurement.frequencyMhz ?: 0), measurement.frequencyMhz)
            }
            listOfNotNull(connected) + measurement.nearbyAccessPoints.mapNotNull { accessPoint ->
                accessPoint.bssid.lowercase().takeIf { it in normalizedBssids }?.let {
                    WifiRadio(it, accessPoint.ssid, accessPoint.band, accessPoint.frequencyMhz)
                }
            }
        } + mutableUiState.value.deviceCandidates.mapNotNull { accessPoint ->
            accessPoint.bssid.lowercase().takeIf { it in normalizedBssids }?.let {
                WifiRadio(it, accessPoint.ssid, accessPoint.band, accessPoint.frequencyMhz)
            }
        }
        val device = WifiDevice(
            id = System.currentTimeMillis(),
            name = name.trim(),
            type = type,
            point = point,
            bssids = normalizedBssids,
            radios = observedRadios.distinctBy { it.bssid },
        )
        mutableUiState.value = mutableUiState.value.copy(devices = mutableUiState.value.devices + device)
    }

    fun scanDeviceCandidates() {
        if (mutableUiState.value.isScanningDeviceCandidates) return
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                isScanningDeviceCandidates = true,
                deviceCandidateError = null,
            )
            runCatching { wifiRepository.loadSnapshot(requestActiveScan = true) }
                .onSuccess { snapshot ->
                    mutableUiState.value = mutableUiState.value.copy(
                        deviceCandidates = snapshot.nearbyAccessPoints,
                        isScanningDeviceCandidates = false,
                    )
                }
                .onFailure { error ->
                    mutableUiState.value = mutableUiState.value.copy(
                        isScanningDeviceCandidates = false,
                        deviceCandidateError = error.message ?: "BSSID 후보 검색에 실패했습니다.",
                    )
                }
        }
    }

    private fun enrichDeviceRadios(
        devices: List<WifiDevice>,
        measurement: SurveyMeasurement,
    ): List<WifiDevice> = devices.map { device ->
        val observed = buildList {
            measurement.bssid?.lowercase()?.takeIf { it in device.mappedBssids }?.let {
                add(WifiRadio(it, measurement.ssid, WifiBand.fromFrequency(measurement.frequencyMhz ?: 0), measurement.frequencyMhz))
            }
            measurement.nearbyAccessPoints.forEach { accessPoint ->
                accessPoint.bssid.lowercase().takeIf { it in device.mappedBssids }?.let {
                    add(WifiRadio(it, accessPoint.ssid, accessPoint.band, accessPoint.frequencyMhz))
                }
            }
        }
        device.copy(radios = (device.radios + observed).distinctBy { it.bssid })
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

    fun updateEstimatedDevicePosition(deviceId: Long, point: NormalizedPoint) {
        mutableUiState.value = mutableUiState.value.copy(
            devices = mutableUiState.value.devices.map { device ->
                if (device.id == deviceId) device.copy(point = point) else device
            },
        )
    }

    fun confirmEstimatedDevice(deviceId: Long) {
        mutableUiState.value = mutableUiState.value.copy(
            devices = mutableUiState.value.devices.map { device ->
                if (device.id == deviceId) device.copy(userConfirmed = true) else device
            },
            projectMessage = "AP 위치를 확인했습니다.",
        )
    }

    fun confirmAllEstimatedDevices() {
        mutableUiState.value = mutableUiState.value.copy(
            devices = mutableUiState.value.devices.map { device ->
                if (device.automaticallyEstimated == true) device.copy(userConfirmed = true) else device
            },
            projectMessage = "추정 AP를 모두 확인했습니다.",
        )
    }

    fun selectResultView(resultView: ResultView) {
        mutableUiState.value = mutableUiState.value.copy(resultView = resultView)
    }

    fun selectBand(band: WifiBand?) {
        mutableUiState.value = mutableUiState.value.copy(selectedBand = band)
    }

    fun selectSignalSourceMode(mode: SignalSourceMode) {
        mutableUiState.value = mutableUiState.value.copy(signalSourceMode = mode)
    }

    fun setDeadZoneThreshold(threshold: Int) {
        mutableUiState.value = mutableUiState.value.copy(deadZoneThreshold = threshold.coerceIn(-85, -60))
    }

    fun toggleMeasurements() {
        mutableUiState.value = mutableUiState.value.copy(showMeasurements = !mutableUiState.value.showMeasurements)
    }

    fun toggleDevices() {
        mutableUiState.value = mutableUiState.value.copy(showDevices = !mutableUiState.value.showDevices)
    }

    fun toggleWalls() {
        mutableUiState.value = mutableUiState.value.copy(showWalls = !mutableUiState.value.showWalls)
    }

    fun toggleWallAwareHeatmap() {
        mutableUiState.value = mutableUiState.value.copy(
            useWallAwareHeatmap = !mutableUiState.value.useWallAwareHeatmap,
        )
    }

    fun selectWallPoint(point: NormalizedPoint) {
        val state = mutableUiState.value
        state.wallMoveId?.let { wallId ->
            mutableUiState.value = state.copy(
                selectedPoint = point,
                wallMoveId = null,
                walls = state.walls.map { wall ->
                    if (wall.id != wallId) wall else {
                        val startDistance = squaredDistance(wall.start, point)
                        val endDistance = squaredDistance(wall.end, point)
                        if (startDistance <= endDistance) wall.copy(start = point, automatic = false)
                        else wall.copy(end = point, automatic = false)
                    }
                },
            )
            return
        }
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

    fun selectNearestWallForMove() {
        val point = mutableUiState.value.selectedPoint ?: return
        val nearest = mutableUiState.value.walls.minByOrNull {
            minOf(squaredDistance(it.start, point), squaredDistance(it.end, point))
        } ?: return
        mutableUiState.value = mutableUiState.value.copy(wallMoveId = nearest.id, wallStartPoint = null)
    }

    private fun squaredDistance(first: NormalizedPoint, second: NormalizedPoint): Float {
        val dx = first.x - second.x
        val dy = first.y - second.y
        return dx * dx + dy * dy
    }

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

    fun saveProject() {
        val state = mutableUiState.value
        if (state.isProjectSaving) return
        mutableUiState.value = state.copy(isProjectSaving = true, projectMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val bitmap = state.bitmap ?: error("평면도가 없습니다.")
                projectStore.save(
                    SavedProject(
                        calibration = state.calibration,
                        measurements = state.measurements,
                        devices = state.devices,
                        walls = state.walls,
                        name = state.projectName.ifBlank { "우리집" },
                        floorPlanSourceName = state.sourceName,
                        settings = ProjectSettings(
                            deadZoneThreshold = state.deadZoneThreshold,
                            selectedBand = state.selectedBand,
                            signalSourceMode = state.signalSourceMode,
                            resultView = state.resultView,
                            useWallAwareHeatmap = state.useWallAwareHeatmap,
                        ),
                    ),
                    bitmap,
                )
            }.onSuccess {
                mutableUiState.value = mutableUiState.value.copy(
                    isProjectSaving = false,
                    hasSavedProject = true,
                    projectMessage = "자동 저장했습니다.",
                )
            }.onFailure { error ->
                mutableUiState.value = mutableUiState.value.copy(
                    isProjectSaving = false,
                    projectMessage = error.message ?: "저장에 실패했습니다.",
                )
            }
        }
    }

    fun loadProject() {
        restoreProject(showMissingMessage = true)
    }

    private fun restoreProject(showMissingMessage: Boolean) {
        if (mutableUiState.value.isProjectLoading) return
        if (!projectStore.exists()) {
            mutableUiState.value = mutableUiState.value.copy(
                hasSavedProject = false,
                projectMessage = if (showMissingMessage) "저장된 프로젝트가 없습니다." else null,
            )
            return
        }
        mutableUiState.value = mutableUiState.value.copy(isProjectLoading = true, projectMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { projectStore.load() to projectStore.loadFloorPlan() }.onSuccess { (project, floorPlan) ->
                val settings = project.settings ?: ProjectSettings()
                mutableUiState.value = mutableUiState.value.copy(
                    bitmap = floorPlan ?: repository.loadDefault(),
                    projectName = project.name?.ifBlank { "우리집" } ?: "우리집",
                    sourceName = project.floorPlanSourceName?.ifBlank { "저장된 평면도" } ?: "저장된 평면도",
                    calibration = project.calibration,
                    measurements = project.measurements,
                    devices = project.devices,
                    walls = project.walls,
                    deadZoneThreshold = settings.deadZoneThreshold.takeIf { it in -85..-60 } ?: -70,
                    selectedBand = settings.selectedBand,
                    signalSourceMode = settings.signalSourceMode,
                    resultView = settings.resultView,
                    useWallAwareHeatmap = settings.useWallAwareHeatmap ?: true,
                    isProjectLoading = false,
                    hasSavedProject = true,
                    projectMessage = "저장된 프로젝트를 불러왔습니다.",
                )
            }.onFailure { error ->
                mutableUiState.value = mutableUiState.value.copy(
                    isProjectLoading = false,
                    projectMessage = error.message ?: "불러오기에 실패했습니다.",
                )
            }
        }
    }
}
