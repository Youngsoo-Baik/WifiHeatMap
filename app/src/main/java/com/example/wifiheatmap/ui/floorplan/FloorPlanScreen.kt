package com.example.wifiheatmap.ui.floorplan

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifiheatmap.floorplan.FloorPlanCoordinates
import com.example.wifiheatmap.floorplan.NormalizedPoint
import com.example.wifiheatmap.heatmap.HeatmapGrid
import com.example.wifiheatmap.heatmap.HeatmapConfidence
import com.example.wifiheatmap.wall.WallSegment
import com.example.wifiheatmap.viewmodel.FloorPlanViewModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorPlanScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: FloorPlanViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var resetToken by remember { mutableIntStateOf(0) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.loadImage(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← 뒤로") }
                },
                title = {
                    Column {
                        Text("평면도 설정")
                        Text(
                            "Phase 2 · Zoom / Pan / 좌표 선택",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { imagePicker.launch(arrayOf("image/png", "image/jpeg")) },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoading,
                ) {
                    Text("평면도 선택")
                }
                OutlinedButton(
                    onClick = { resetToken++ },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Zoom/Pan 초기화")
                }
            }

            OutlinedTextField(
                value = uiState.projectName,
                onValueChange = viewModel::setProjectName,
                label = { Text("프로젝트 이름") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = { TextButton(onClick = viewModel::newProject) { Text("새 프로젝트") } },
            )

            Text(
                text = uiState.sourceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            uiState.errorMessage?.let { message ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))) {
                    Text(
                        message,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                uiState.bitmap?.let { bitmap ->
                    FloorPlanCanvas(
                        bitmap = bitmap,
                        markers = listOfNotNull(
                            uiState.selectedPoint?.let { FloorPlanMarker(it, Color(0xFF2563EB)) },
                        ),
                        resetToken = resetToken,
                        onPointSelected = viewModel::selectPoint,
                    )
                }
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xCCFFFFFF), RoundedCornerShape(12.dp))
                            .padding(20.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }

            CoordinateCard(uiState.selectedPoint)
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("적용하고 홈으로") }
        }
    }
}

@Composable
internal fun FloorPlanCanvas(
    bitmap: Bitmap,
    markers: List<FloorPlanMarker>,
    heatmap: HeatmapGrid? = null,
    walls: List<WallSegment> = emptyList(),
    coverageRings: List<FloorPlanCoverageRing> = emptyList(),
    deadZoneOnly: Boolean = false,
    deadZoneThreshold: Int = -70,
    trackingPath: List<NormalizedPoint> = emptyList(),
    resetToken: Int,
    onPointSelected: (NormalizedPoint) -> Unit,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var userScale by remember(bitmap, resetToken) { mutableFloatStateOf(1f) }
    var panOffset by remember(bitmap, resetToken) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    fun renderedImageBounds(scale: Float = userScale, offset: Offset = panOffset): ImageBounds {
        if (canvasSize.width == 0 || canvasSize.height == 0) return ImageBounds.Zero
        val baseScale = min(
            canvasSize.width.toFloat() / bitmap.width,
            canvasSize.height.toFloat() / bitmap.height,
        )
        val width = bitmap.width * baseScale * scale
        val height = bitmap.height * baseScale * scale
        return ImageBounds(
            left = canvasSize.width / 2f - width / 2f + offset.x,
            top = canvasSize.height / 2f - height / 2f + offset.y,
            width = width,
            height = height,
        )
    }

    fun clampOffset(candidate: Offset, scale: Float): Offset {
        val bounds = renderedImageBounds(scale = scale, offset = Offset.Zero)
        val maxX = max(0f, (bounds.width - canvasSize.width) / 2f)
        val maxY = max(0f, (bounds.height - canvasSize.height) / 2f)
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(bitmap, resetToken) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val previousScale = userScale
                    val nextScale = (previousScale * zoom).coerceIn(1f, 5f)
                    val zoomRatio = nextScale / previousScale
                    val canvasCenter = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                    val currentContentCenter = canvasCenter + panOffset
                    val nextContentCenter = centroid + (currentContentCenter - centroid) * zoomRatio + pan
                    userScale = nextScale
                    panOffset = clampOffset(nextContentCenter - canvasCenter, nextScale)
                }
            }
            .pointerInput(bitmap, resetToken) {
                detectTapGestures { tapPosition ->
                    val bounds = renderedImageBounds()
                    FloorPlanCoordinates.normalize(
                        pointX = tapPosition.x,
                        pointY = tapPosition.y,
                        imageLeft = bounds.left,
                        imageTop = bounds.top,
                        imageWidth = bounds.width,
                        imageHeight = bounds.height,
                    )?.let(onPointSelected)
                }
            },
    ) {
        val bounds = renderedImageBounds()
        if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas
        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()),
            dstSize = IntSize(bounds.width.roundToInt(), bounds.height.roundToInt()),
        )
        drawRect(
            color = Color(0xFF64748B),
            topLeft = Offset(bounds.left, bounds.top),
            size = Size(bounds.width, bounds.height),
            style = Stroke(width = 1.dp.toPx()),
        )
        heatmap?.let { grid ->
            val cellWidth = bounds.width / grid.columns
            val cellHeight = bounds.height / grid.rows
            repeat(grid.rows) { row ->
                repeat(grid.columns) { column ->
                    val cell = grid[column, row]
                    drawRect(
                        color = heatmapColor(cell.value, cell.confidence, deadZoneOnly, deadZoneThreshold),
                        topLeft = Offset(bounds.left + column * cellWidth, bounds.top + row * cellHeight),
                        size = Size(cellWidth + 1f, cellHeight + 1f),
                    )
                }
            }
        }
        coverageRings.forEach { ring ->
            val center = Offset(
                bounds.left + bounds.width * ring.point.x,
                bounds.top + bounds.height * ring.point.y,
            )
            val displayedRadius = ring.radiusPixels * bounds.width / bitmap.width
            drawCircle(
                color = ring.color.copy(alpha = 0.18f),
                radius = displayedRadius,
                center = center,
            )
            drawCircle(
                color = ring.color.copy(alpha = 0.8f),
                radius = displayedRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        trackingPath.zipWithNext().forEach { (startPoint, endPoint) ->
            drawLine(
                color = Color(0xFF0891B2),
                start = Offset(
                    bounds.left + bounds.width * startPoint.x,
                    bounds.top + bounds.height * startPoint.y,
                ),
                end = Offset(
                    bounds.left + bounds.width * endPoint.x,
                    bounds.top + bounds.height * endPoint.y,
                ),
                strokeWidth = 5.dp.toPx(),
            )
        }
        walls.forEach { wall ->
            drawLine(
                color = if (wall.isOpening) Color(0xFF22C55E) else Color(0xFF7C3AED),
                start = Offset(bounds.left + bounds.width * wall.start.x, bounds.top + bounds.height * wall.start.y),
                end = Offset(bounds.left + bounds.width * wall.end.x, bounds.top + bounds.height * wall.end.y),
                strokeWidth = if (wall.isOpening) 3.dp.toPx() else 5.dp.toPx(),
            )
        }
        markers.forEach { floorPlanMarker ->
            val point = floorPlanMarker.point
            val marker = Offset(
                x = bounds.left + bounds.width * point.x,
                y = bounds.top + bounds.height * point.y,
            )
            drawCircle(Color.White, radius = 11.dp.toPx(), center = marker)
            drawCircle(floorPlanMarker.color, radius = 7.dp.toPx(), center = marker)
            drawCircle(
                color = floorPlanMarker.color,
                radius = 14.dp.toPx(),
                center = marker,
                style = Stroke(width = 2.dp.toPx()),
            )
            floorPlanMarker.label?.let { label ->
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        label,
                        marker.x + 12.dp.toPx(),
                        marker.y - 12.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 16.dp.toPx()
                            isFakeBoldText = true
                        },
                    )
                }
            }
        }
    }
}

private fun heatmapColor(
    rssi: Double,
    confidence: HeatmapConfidence,
    deadZoneOnly: Boolean,
    deadZoneThreshold: Int,
): Color {
    if (confidence == HeatmapConfidence.UNMEASURED) return Color(0x665B6472)
    if (deadZoneOnly) {
        return if (rssi < deadZoneThreshold) Color(0x99DC2626) else Color(0x2216A34A)
    }
    val color = when {
        rssi >= -55 -> Color(0xFF16A34A)
        rssi >= -67 -> Color(0xFFEAB308)
        rssi >= -75 -> Color(0xFFF97316)
        else -> Color(0xFFDC2626)
    }
    val alpha = when (confidence) {
        HeatmapConfidence.HIGH -> 0.58f
        HeatmapConfidence.MEDIUM -> 0.46f
        HeatmapConfidence.LOW -> 0.30f
        HeatmapConfidence.UNMEASURED -> 0f
    }
    return color.copy(alpha = alpha)
}

internal data class FloorPlanMarker(
    val point: NormalizedPoint,
    val color: Color,
    val label: String? = null,
)

internal data class FloorPlanCoverageRing(
    val point: NormalizedPoint,
    val radiusPixels: Float,
    val color: Color,
)

@Composable
private fun CoordinateCard(point: NormalizedPoint?) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("선택 좌표", fontWeight = FontWeight.Bold)
            Text(
                text = point?.let { "x = %.4f   y = %.4f".format(it.x, it.y) }
                    ?: "평면도에서 위치를 탭하세요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "한 손가락으로 이동하고 두 손가락으로 확대/축소할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class ImageBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    companion object {
        val Zero = ImageBounds(0f, 0f, 0f, 0f)
    }
}
