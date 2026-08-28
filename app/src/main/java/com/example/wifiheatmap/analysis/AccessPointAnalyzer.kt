package com.example.wifiheatmap.analysis

import com.example.wifiheatmap.data.model.WifiBand
import com.example.wifiheatmap.device.WifiRadio
import com.example.wifiheatmap.floorplan.NormalizedPoint
import com.example.wifiheatmap.survey.SurveyMeasurement
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

data class PhysicalAccessPointCandidate(
    val id: String,
    val name: String,
    val estimatedPoint: NormalizedPoint,
    val clusterConfidence: Double,
    val positionConfidence: Double,
    val radios: List<WifiRadio>,
    val observationCount: Int,
)

object AccessPointAnalyzer {
    fun analyze(measurements: List<SurveyMeasurement>): List<PhysicalAccessPointCandidate> {
        if (measurements.isEmpty()) return emptyList()
        val homeSsids = measurements.mapNotNull { normalizeSsid(it.ssid) }.toSet()
        val connectedBssids = measurements.mapNotNull { normalizeBssid(it.bssid) }.toSet()
        val fingerprints = buildFingerprints(measurements).filter { fingerprint ->
            fingerprint.bssid in connectedBssids || normalizeSsid(fingerprint.ssid) in homeSsids
        }
        if (fingerprints.isEmpty()) return emptyList()

        return cluster(fingerprints).mapIndexed { index, members ->
            val pairScores = members.indices.flatMap { first ->
                (first + 1 until members.size).map { second -> similarity(members[first], members[second]) }
            }
            val clusterConfidence = if (pairScores.isNotEmpty()) {
                pairScores.average().coerceIn(0.0, 1.0)
            } else {
                (0.45 + members.single().observations.size.coerceAtMost(10) * 0.025).coerceAtMost(0.7)
            }
            val observations = members.flatMap { it.observations }
            val estimatedPoint = weightedCentroid(observations)
            val radios = members.map { fingerprint ->
                WifiRadio(
                    bssid = fingerprint.bssid,
                    ssid = fingerprint.ssid,
                    band = fingerprint.band,
                    frequencyMhz = fingerprint.frequencyMhz,
                )
            }
            PhysicalAccessPointCandidate(
                id = members.map { it.bssid }.sorted().joinToString("+"),
                name = "추정 AP ${index + 1}",
                estimatedPoint = estimatedPoint,
                clusterConfidence = clusterConfidence,
                positionConfidence = positionConfidence(observations, estimatedPoint, clusterConfidence),
                radios = radios,
                observationCount = observations.size,
            )
        }.sortedByDescending { it.positionConfidence }
            .mapIndexed { index, candidate -> candidate.copy(name = "추정 AP ${index + 1}") }
    }

    private fun buildFingerprints(measurements: List<SurveyMeasurement>): List<RadioFingerprint> {
        val observationsByBssid = linkedMapOf<String, MutableList<RadioObservation>>()
        val metadataByBssid = linkedMapOf<String, RadioMetadata>()
        measurements.forEach { measurement ->
            val observationsAtPoint = buildList {
                normalizeBssid(measurement.bssid)?.let { bssid ->
                    add(RawRadioObservation(bssid, measurement.ssid, measurement.medianRssi, measurement.frequencyMhz ?: 0))
                }
                measurement.nearbyAccessPoints.forEach { accessPoint ->
                    normalizeBssid(accessPoint.bssid)?.let { bssid ->
                        add(RawRadioObservation(bssid, accessPoint.ssid, accessPoint.rssi, accessPoint.frequencyMhz))
                    }
                }
            }.groupBy { it.bssid }.mapValues { (_, values) -> values.maxBy { it.rssi } }

            observationsAtPoint.values.forEach { observation ->
                observationsByBssid.getOrPut(observation.bssid) { mutableListOf() } += RadioObservation(
                    measurementId = measurement.id,
                    point = measurement.point,
                    rssi = observation.rssi,
                )
                val previous = metadataByBssid[observation.bssid]
                metadataByBssid[observation.bssid] = RadioMetadata(
                    ssid = normalizeSsid(observation.ssid) ?: previous?.ssid,
                    frequencyMhz = observation.frequencyMhz.takeIf { it > 0 } ?: previous?.frequencyMhz ?: 0,
                )
            }
        }
        return observationsByBssid.map { (bssid, observations) ->
            val metadata = metadataByBssid.getValue(bssid)
            RadioFingerprint(
                bssid = bssid,
                ssid = metadata.ssid,
                frequencyMhz = metadata.frequencyMhz,
                band = WifiBand.fromFrequency(metadata.frequencyMhz),
                observations = observations,
            )
        }
    }

    private fun cluster(fingerprints: List<RadioFingerprint>): List<List<RadioFingerprint>> {
        val parents = IntArray(fingerprints.size) { it }
        fun find(index: Int): Int {
            var current = index
            while (parents[current] != current) {
                parents[current] = parents[parents[current]]
                current = parents[current]
            }
            return current
        }
        fun union(first: Int, second: Int) {
            val firstRoot = find(first)
            val secondRoot = find(second)
            if (firstRoot != secondRoot) parents[secondRoot] = firstRoot
        }
        fingerprints.indices.forEach { first ->
            (first + 1 until fingerprints.size).forEach { second ->
                if (similarity(fingerprints[first], fingerprints[second]) >= CLUSTER_THRESHOLD) union(first, second)
            }
        }
        return fingerprints.indices.groupBy(::find).values.map { indices -> indices.map(fingerprints::get) }
    }

    private fun similarity(first: RadioFingerprint, second: RadioFingerprint): Double {
        val sameSsid = normalizeSsid(first.ssid) != null && normalizeSsid(first.ssid) == normalizeSsid(second.ssid)
        val ssidScore = if (sameSsid) 0.25 else 0.0
        val ouiScore = if (oui(first.bssid) == oui(second.bssid)) 0.08 else 0.0
        val prefixScore = if (hardwarePrefix(first.bssid) == hardwarePrefix(second.bssid)) 0.15 else 0.0
        val correlationScore = ((spatialCorrelation(first, second) + 1.0) / 2.0).coerceIn(0.0, 1.0) * 0.32
        val peakScore = (1.0 - distance(peakPoint(first), peakPoint(second)) / MAX_PEAK_DISTANCE)
            .coerceIn(0.0, 1.0) * 0.20
        return ssidScore + ouiScore + prefixScore + correlationScore + peakScore
    }

    private fun spatialCorrelation(first: RadioFingerprint, second: RadioFingerprint): Double {
        val firstByMeasurement = first.observations.associate { it.measurementId to it.rssi.toDouble() }
        val secondByMeasurement = second.observations.associate { it.measurementId to it.rssi.toDouble() }
        val measurementIds = (firstByMeasurement.keys + secondByMeasurement.keys).distinct()
        if (measurementIds.size < 3) return 0.0
        val firstValues = measurementIds.map { firstByMeasurement[it] ?: MISSING_RSSI }
        val secondValues = measurementIds.map { secondByMeasurement[it] ?: MISSING_RSSI }
        val firstMean = firstValues.average()
        val secondMean = secondValues.average()
        var numerator = 0.0
        var firstVariance = 0.0
        var secondVariance = 0.0
        firstValues.indices.forEach { index ->
            val firstDelta = firstValues[index] - firstMean
            val secondDelta = secondValues[index] - secondMean
            numerator += firstDelta * secondDelta
            firstVariance += firstDelta * firstDelta
            secondVariance += secondDelta * secondDelta
        }
        val denominator = sqrt(firstVariance * secondVariance)
        return if (denominator == 0.0) 0.0 else (numerator / denominator).coerceIn(-1.0, 1.0)
    }

    private fun weightedCentroid(observations: List<RadioObservation>): NormalizedPoint {
        val sampleCount = ceil(observations.size * 0.4).toInt().coerceAtLeast(3).coerceAtMost(observations.size)
        val weighted = observations.sortedByDescending { it.rssi }.take(sampleCount).map { observation ->
            observation to 10.0.pow((observation.rssi + 100.0) / 20.0)
        }
        val totalWeight = weighted.sumOf { it.second }.takeIf { it > 0.0 } ?: 1.0
        return NormalizedPoint(
            x = (weighted.sumOf { it.first.point.x * it.second } / totalWeight).toFloat().coerceIn(0f, 1f),
            y = (weighted.sumOf { it.first.point.y * it.second } / totalWeight).toFloat().coerceIn(0f, 1f),
        )
    }

    private fun positionConfidence(
        observations: List<RadioObservation>,
        estimatedPoint: NormalizedPoint,
        clusterConfidence: Double,
    ): Double {
        val observationScore = (observations.size / 12.0).coerceIn(0.0, 1.0)
        val distinctCells = observations.map { (it.point.x * 10).toInt() to (it.point.y * 10).toInt() }.distinct().size
        val spatialScore = (distinctCells / 6.0).coerceIn(0.0, 1.0)
        val meanDistance = observations.map { distance(it.point, estimatedPoint) }.average()
        val compactnessScore = (1.0 - meanDistance / 0.45).coerceIn(0.0, 1.0)
        return (observationScore + spatialScore + compactnessScore + clusterConfidence) / 4.0
    }

    private fun peakPoint(fingerprint: RadioFingerprint): NormalizedPoint = fingerprint.observations.maxBy { it.rssi }.point
    private fun distance(first: NormalizedPoint, second: NormalizedPoint): Double =
        hypot((first.x - second.x).toDouble(), (first.y - second.y).toDouble())
    private fun normalizeBssid(value: String?): String? = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    private fun normalizeSsid(value: String?): String? = value?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() }
    private fun oui(bssid: String): String = bssid.split(':').take(3).joinToString(":")
    private fun hardwarePrefix(bssid: String): String = bssid.split(':').take(5).joinToString(":")

    private data class RadioFingerprint(
        val bssid: String,
        val ssid: String?,
        val frequencyMhz: Int,
        val band: WifiBand,
        val observations: List<RadioObservation>,
    )

    private data class RadioObservation(val measurementId: Long, val point: NormalizedPoint, val rssi: Int)
    private data class RawRadioObservation(val bssid: String, val ssid: String?, val rssi: Int, val frequencyMhz: Int)
    private data class RadioMetadata(val ssid: String?, val frequencyMhz: Int)

    private const val CLUSTER_THRESHOLD = 0.62
    private const val MAX_PEAK_DISTANCE = 0.25
    private const val MISSING_RSSI = -100.0
}
