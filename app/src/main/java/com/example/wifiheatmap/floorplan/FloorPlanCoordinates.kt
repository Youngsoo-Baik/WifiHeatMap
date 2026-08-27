package com.example.wifiheatmap.floorplan

data class NormalizedPoint(val x: Float, val y: Float)

object FloorPlanCoordinates {
    fun normalize(
        pointX: Float,
        pointY: Float,
        imageLeft: Float,
        imageTop: Float,
        imageWidth: Float,
        imageHeight: Float,
    ): NormalizedPoint? {
        if (imageWidth <= 0f || imageHeight <= 0f) return null
        val normalizedX = (pointX - imageLeft) / imageWidth
        val normalizedY = (pointY - imageTop) / imageHeight
        if (normalizedX !in 0f..1f || normalizedY !in 0f..1f) return null
        return NormalizedPoint(normalizedX, normalizedY)
    }
}
