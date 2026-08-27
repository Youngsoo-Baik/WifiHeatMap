package com.example.wifiheatmap.wall

import android.graphics.Bitmap
import com.example.wifiheatmap.floorplan.NormalizedPoint
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class WallSegment(
    val id: Long,
    val start: NormalizedPoint,
    val end: NormalizedPoint,
    val isOpening: Boolean = false,
    val automatic: Boolean = false,
)

object WallDetector {
    fun detect(bitmap: Bitmap): List<WallSegment> {
        check(OpenCVLoader.initLocal()) { "OpenCV 초기화에 실패했습니다." }
        val source = Mat()
        val gray = Mat()
        val edges = Mat()
        val lines = Mat()
        return try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
            Imgproc.Canny(gray, edges, 60.0, 160.0)
            Imgproc.HoughLinesP(
                edges, lines, 1.0, Math.PI / 180.0, 55,
                minOf(bitmap.width, bitmap.height) * 0.035, 12.0,
            )
            buildList {
                repeat(minOf(lines.rows(), 160)) { index ->
                    val line = lines.get(index, 0) ?: return@repeat
                    add(
                        WallSegment(
                            id = System.nanoTime() + index,
                            start = NormalizedPoint(
                                (line[0] / bitmap.width).toFloat().coerceIn(0f, 1f),
                                (line[1] / bitmap.height).toFloat().coerceIn(0f, 1f),
                            ),
                            end = NormalizedPoint(
                                (line[2] / bitmap.width).toFloat().coerceIn(0f, 1f),
                                (line[3] / bitmap.height).toFloat().coerceIn(0f, 1f),
                            ),
                            automatic = true,
                        ),
                    )
                }
            }
        } finally {
            source.release(); gray.release(); edges.release(); lines.release()
        }
    }
}
