package com.example.wifiheatmap.heatmap

import com.example.wifiheatmap.floorplan.NormalizedPoint
import kotlin.math.hypot
import kotlin.math.pow

data class HeatmapSample(val point: NormalizedPoint, val value: Double)

data class HeatmapCell(
    val value: Double,
    val isTrusted: Boolean,
)

data class HeatmapGrid(
    val columns: Int,
    val rows: Int,
    val cells: List<HeatmapCell>,
) {
    operator fun get(column: Int, row: Int): HeatmapCell = cells[row * columns + column]
}

object IdwHeatmap {
    fun generate(
        samples: List<HeatmapSample>,
        columns: Int = 36,
        rows: Int = 36,
        power: Double = 2.0,
        trustRadius: Double = 0.25,
    ): HeatmapGrid {
        require(samples.isNotEmpty()) { "히트맵 샘플이 없습니다." }
        val cells = buildList(columns * rows) {
            repeat(rows) { row ->
                repeat(columns) { column ->
                    val x = (column + 0.5) / columns
                    val y = (row + 0.5) / rows
                    val distances = samples.map { sample ->
                        sample to hypot(sample.point.x.toDouble() - x, sample.point.y.toDouble() - y)
                    }
                    val exact = distances.firstOrNull { it.second < 1e-9 }
                    val value = exact?.first?.value ?: run {
                        var weightedSum = 0.0
                        var weightSum = 0.0
                        distances.forEach { (sample, distance) ->
                            val weight = 1.0 / distance.pow(power)
                            weightedSum += sample.value * weight
                            weightSum += weight
                        }
                        weightedSum / weightSum
                    }
                    add(HeatmapCell(value, distances.minOf { it.second } <= trustRadius))
                }
            }
        }
        return HeatmapGrid(columns, rows, cells)
    }
}
