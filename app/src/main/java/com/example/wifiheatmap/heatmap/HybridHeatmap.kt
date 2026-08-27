package com.example.wifiheatmap.heatmap

import com.example.wifiheatmap.floorplan.NormalizedPoint
import com.example.wifiheatmap.survey.SurveyMeasurement
import com.example.wifiheatmap.wall.WallSegment
import kotlin.math.hypot
import kotlin.math.log10

object WallGeometry {
    fun intersects(from: NormalizedPoint, to: NormalizedPoint, wall: WallSegment): Boolean {
        if (wall.isOpening) return false
        fun orientation(a: NormalizedPoint, b: NormalizedPoint, c: NormalizedPoint): Float =
            (b.y - a.y) * (c.x - b.x) - (b.x - a.x) * (c.y - b.y)
        val first = orientation(from, to, wall.start)
        val second = orientation(from, to, wall.end)
        val third = orientation(wall.start, wall.end, from)
        val fourth = orientation(wall.start, wall.end, to)
        return first * second <= 0f && third * fourth <= 0f
    }

    fun countIntersections(from: NormalizedPoint, to: NormalizedPoint, walls: List<WallSegment>): Int =
        walls.count { intersects(from, to, it) }
}

object HybridHeatmap {
    fun generate(
        transmitter: NormalizedPoint,
        measurements: List<SurveyMeasurement>,
        walls: List<WallSegment>,
        bitmapWidth: Int,
        bitmapHeight: Int,
        metersPerPixel: Double,
        columns: Int = 36,
        rows: Int = 36,
    ): HeatmapGrid {
        val residualSamples = measurements.map { measurement ->
            HeatmapSample(
                measurement.point,
                measurement.medianRssi - predict(
                    transmitter, measurement.point, walls, bitmapWidth, bitmapHeight,
                    metersPerPixel, measurement.frequencyMhz,
                ),
            )
        }
        val residualGrid = residualSamples.takeIf { it.isNotEmpty() }?.let {
            IdwHeatmap.generate(it, columns, rows, trustRadius = 0.3)
        }
        val cells = buildList(columns * rows) {
            repeat(rows) { row ->
                repeat(columns) { column ->
                    val point = NormalizedPoint(
                        ((column + 0.5) / columns).toFloat(),
                        ((row + 0.5) / rows).toFloat(),
                    )
                    val predicted = predict(
                        transmitter, point, walls, bitmapWidth, bitmapHeight,
                        metersPerPixel, measurements.firstOrNull()?.frequencyMhz,
                    )
                    val residual = residualGrid?.get(column, row)
                    add(HeatmapCell(predicted + (residual?.value ?: 0.0), residual?.isTrusted ?: false))
                }
            }
        }
        return HeatmapGrid(columns, rows, cells)
    }

    private fun predict(
        transmitter: NormalizedPoint,
        point: NormalizedPoint,
        walls: List<WallSegment>,
        bitmapWidth: Int,
        bitmapHeight: Int,
        metersPerPixel: Double,
        frequencyMhz: Int?,
    ): Double {
        val pixels = hypot(
            (point.x - transmitter.x).toDouble() * bitmapWidth,
            (point.y - transmitter.y).toDouble() * bitmapHeight,
        )
        val distanceMeters = (pixels * metersPerPixel).coerceAtLeast(1.0)
        val pathLossExponent = if ((frequencyMhz ?: 0) >= 4900) 2.4 else 2.0
        val wallLoss = if ((frequencyMhz ?: 0) >= 4900) 6.0 else 4.0
        return -35.0 - 10.0 * pathLossExponent * log10(distanceMeters) -
            wallLoss * WallGeometry.countIntersections(transmitter, point, walls)
    }
}
