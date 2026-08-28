package com.example.wifiheatmap.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PdrTrackingProvider(context: Context) : IndoorTrackingProvider, SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val estimator = PdrEstimator()
    private val mutableStatus = MutableStateFlow(TrackingStatus.IDLE)
    private val mutablePoints = MutableStateFlow<List<TrackingPoint>>(emptyList())
    private var currentDeviceHeadingDegrees: Double? = null

    override val providerType: TrackingProviderType = TrackingProviderType.PDR
    override val status: StateFlow<TrackingStatus> = mutableStatus.asStateFlow()
    override val points: StateFlow<List<TrackingPoint>> = mutablePoints.asStateFlow()
    override val isSupported: Boolean = stepSensor != null && rotationSensor != null

    override fun start(floorHeadingDegrees: Double): Boolean {
        stop()
        if (!isSupported) {
            mutableStatus.value = TrackingStatus.UNAVAILABLE
            return false
        }
        estimator.start(floorHeadingDegrees, currentDeviceHeadingDegrees)
        mutablePoints.value = listOf(estimator.origin(System.currentTimeMillis()))
        val rotationRegistered = sensorManager.registerListener(
            this,
            rotationSensor,
            SensorManager.SENSOR_DELAY_GAME,
        )
        val stepRegistered = sensorManager.registerListener(
            this,
            stepSensor,
            SensorManager.SENSOR_DELAY_NORMAL,
        )
        if (!rotationRegistered || !stepRegistered) {
            stop()
            mutableStatus.value = TrackingStatus.UNAVAILABLE
            return false
        }
        mutableStatus.value = TrackingStatus.TRACKING
        return true
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        if (mutableStatus.value != TrackingStatus.UNAVAILABLE) {
            mutableStatus.value = TrackingStatus.IDLE
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> currentDeviceHeadingDegrees = rotationHeadingDegrees(event.values)
            Sensor.TYPE_STEP_DETECTOR -> {
                if (mutableStatus.value != TrackingStatus.TRACKING) return
                val detectedSteps = event.values.firstOrNull()?.toInt()?.coerceAtLeast(1) ?: 1
                repeat(detectedSteps) {
                    val point = estimator.onStep(
                        deviceHeadingDegrees = currentDeviceHeadingDegrees,
                        timestampMillis = System.currentTimeMillis(),
                        confidence = if (currentDeviceHeadingDegrees == null) 0.45 else 0.75,
                    )
                    mutablePoints.value = mutablePoints.value + point
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun rotationHeadingDegrees(rotationVector: FloatArray): Double {
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
        SensorManager.getOrientation(rotationMatrix, orientation)
        return FloorPlanTrackingCoordinates.normalizeDegrees(Math.toDegrees(orientation[0].toDouble()))
    }
}
