package com.example.wifiheatmap.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifiheatmap.floorplan.NormalizedPoint
import com.example.wifiheatmap.tracking.FloorPlanPose
import com.example.wifiheatmap.tracking.FloorPlanTrackingCoordinates
import com.example.wifiheatmap.tracking.PdrTrackingProvider
import com.example.wifiheatmap.tracking.TrackingPoint
import com.example.wifiheatmap.tracking.TrackingStatus
import com.example.wifiheatmap.data.model.WifiSnapshot
import com.example.wifiheatmap.survey.SurveyMeasurement
import com.example.wifiheatmap.wifi.WifiRepository
import com.example.wifiheatmap.wifi.WifiScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlin.math.hypot

data class AutoSurveyUiState(
    val startPoint: NormalizedPoint? = null,
    val directionPoint: NormalizedPoint? = null,
    val path: List<NormalizedPoint> = emptyList(),
    val isTracking: Boolean = false,
    val providerSupported: Boolean = true,
    val stepCount: Int = 0,
    val distanceMeters: Double = 0.0,
    val averageConfidence: Double = 0.0,
    val observationCount: Int = 0,
    val detectedBssidCount: Int = 0,
    val isCollectingWifi: Boolean = false,
    val errorMessage: String? = null,
)

class AutoSurveyViewModel(application: Application) : AndroidViewModel(application) {
    private val provider = PdrTrackingProvider(application)
    private val wifiRepository = WifiRepository(WifiScanner(application))
    private val mutableUiState = MutableStateFlow(AutoSurveyUiState(providerSupported = provider.isSupported))
    private var pose: FloorPlanPose? = null
    private var bitmapWidth: Int = 0
    private var bitmapHeight: Int = 0
    private var metersPerPixel: Double = 0.0
    private var observationJob: Job? = null
    private val samples = mutableListOf<AutoSurveySample>()
    val uiState: StateFlow<AutoSurveyUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            provider.points.collect(::updatePath)
        }
        viewModelScope.launch {
            provider.status.collect { status ->
                mutableUiState.value = mutableUiState.value.copy(isTracking = status == TrackingStatus.TRACKING)
            }
        }
    }

    fun selectPosePoint(point: NormalizedPoint) {
        if (mutableUiState.value.isTracking) return
        val state = mutableUiState.value
        mutableUiState.value = when {
            state.startPoint == null -> state.copy(startPoint = point, directionPoint = null, path = listOf(point), errorMessage = null)
            state.directionPoint == null -> runCatching {
                FloorPlanTrackingCoordinates.headingBetween(state.startPoint, point)
            }.fold(
                onSuccess = { state.copy(directionPoint = point, errorMessage = null) },
                onFailure = { state.copy(errorMessage = it.message) },
            )
            else -> state.copy(startPoint = point, directionPoint = null, path = listOf(point), errorMessage = null)
        }
    }

    fun resetPose() {
        if (mutableUiState.value.isTracking) return
        pose = null
        mutableUiState.value = mutableUiState.value.copy(
            startPoint = null,
            directionPoint = null,
            path = emptyList(),
            errorMessage = null,
        )
    }

    fun start(bitmapWidth: Int, bitmapHeight: Int, metersPerPixel: Double?) {
        val state = mutableUiState.value
        val startPoint = state.startPoint
        val directionPoint = state.directionPoint
        if (!provider.isSupported) {
            mutableUiState.value = state.copy(errorMessage = "이 기기는 Step Detector 또는 Rotation Vector 센서를 지원하지 않습니다.")
            return
        }
        if (startPoint == null || directionPoint == null) {
            mutableUiState.value = state.copy(errorMessage = "시작 위치와 바라보는 방향을 순서대로 선택하세요.")
            return
        }
        if (metersPerPixel == null || metersPerPixel <= 0.0) {
            mutableUiState.value = state.copy(errorMessage = "자동 이동 경로를 평면도에 표시하려면 거리 보정이 필요합니다.")
            return
        }
        val floorHeading = FloorPlanTrackingCoordinates.headingBetween(startPoint, directionPoint)
        pose = FloorPlanPose(startPoint, floorHeading)
        this.bitmapWidth = bitmapWidth
        this.bitmapHeight = bitmapHeight
        this.metersPerPixel = metersPerPixel
        val started = provider.start(floorHeading)
        if (started) startObservationCollector()
        mutableUiState.value = mutableUiState.value.copy(
            errorMessage = if (started) null else "PDR 센서를 시작하지 못했습니다.",
        )
    }

    fun stop() {
        observationJob?.cancel()
        observationJob = null
        provider.stop()
        mutableUiState.value = mutableUiState.value.copy(isCollectingWifi = false)
    }

    fun stopAndBuildMeasurements(): List<SurveyMeasurement> {
        stop()
        return samples.mapIndexedNotNull { index, sample ->
            val connected = sample.snapshot.connectedWifi
            val representativeRssi = connected?.rssi
                ?: sample.snapshot.nearbyAccessPoints.maxByOrNull { it.rssi }?.rssi
                ?: return@mapIndexedNotNull null
            SurveyMeasurement(
                id = sample.snapshot.capturedAtMillis + index,
                point = sample.point,
                ssid = connected?.ssid,
                bssid = connected?.bssid,
                medianRssi = representativeRssi,
                frequencyMhz = connected?.frequencyMhz,
                samples = listOf(representativeRssi),
                nearbyAccessPoints = sample.snapshot.nearbyAccessPoints,
                measuredAtMillis = sample.snapshot.capturedAtMillis,
            )
        }
    }

    private fun startObservationCollector() {
        observationJob?.cancel()
        samples.clear()
        mutableUiState.value = mutableUiState.value.copy(
            observationCount = 0,
            detectedBssidCount = 0,
            isCollectingWifi = true,
        )
        observationJob = viewModelScope.launch {
            var lastActiveScanMillis = 0L
            while (isActive && provider.status.value == TrackingStatus.TRACKING) {
                val now = System.currentTimeMillis()
                val requestActiveScan = now - lastActiveScanMillis >= ACTIVE_SCAN_INTERVAL_MILLIS
                if (requestActiveScan) lastActiveScanMillis = now
                runCatching { wifiRepository.loadSnapshot(requestActiveScan) }
                    .onSuccess(::appendSample)
                    .onFailure { error ->
                        mutableUiState.value = mutableUiState.value.copy(
                            errorMessage = error.message ?: "Wi-Fi 자동 수집에 실패했습니다.",
                        )
                    }
                delay(OBSERVATION_INTERVAL_MILLIS)
            }
        }
    }

    private fun appendSample(snapshot: WifiSnapshot) {
        val point = mutableUiState.value.path.lastOrNull() ?: mutableUiState.value.startPoint ?: return
        samples += AutoSurveySample(point, snapshot)
        val bssids = samples.flatMap { sample ->
            listOfNotNull(sample.snapshot.connectedWifi?.bssid) +
                sample.snapshot.nearbyAccessPoints.map { it.bssid }
        }.map { it.lowercase() }.toSet()
        mutableUiState.value = mutableUiState.value.copy(
            observationCount = samples.sumOf { sample ->
                sample.snapshot.nearbyAccessPoints.size + if (sample.snapshot.connectedWifi?.rssi != null) 1 else 0
            },
            detectedBssidCount = bssids.size,
        )
    }

    private fun updatePath(points: List<TrackingPoint>) {
        val currentPose = pose ?: return
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || metersPerPixel <= 0.0) return
        val path = points.map { point ->
            FloorPlanTrackingCoordinates.map(currentPose, point, bitmapWidth, bitmapHeight, metersPerPixel)
        }
        val distance = points.zipWithNext().sumOf { (first, second) ->
            hypot(second.xMeters - first.xMeters, second.yMeters - first.yMeters)
        }
        mutableUiState.value = mutableUiState.value.copy(
            path = path,
            stepCount = (points.size - 1).coerceAtLeast(0),
            distanceMeters = distance,
            averageConfidence = points.map { it.trackingConfidence }.average().takeUnless { it.isNaN() } ?: 0.0,
        )
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private data class AutoSurveySample(
        val point: NormalizedPoint,
        val snapshot: WifiSnapshot,
    )

    private companion object {
        const val OBSERVATION_INTERVAL_MILLIS = 2_000L
        const val ACTIVE_SCAN_INTERVAL_MILLIS = 20_000L
    }
}
