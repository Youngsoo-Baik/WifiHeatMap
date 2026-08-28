package com.example.wifiheatmap.persistence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.wifiheatmap.calibration.CalibrationData
import com.example.wifiheatmap.device.WifiDevice
import com.example.wifiheatmap.survey.SurveyMeasurement
import com.example.wifiheatmap.wall.WallSegment
import com.example.wifiheatmap.coverage.ResultView
import com.example.wifiheatmap.coverage.SignalSourceMode
import com.example.wifiheatmap.data.model.WifiBand
import com.google.gson.Gson

data class SavedProject(
    val calibration: CalibrationData?,
    val measurements: List<SurveyMeasurement>,
    val devices: List<WifiDevice>,
    val walls: List<WallSegment>,
    val name: String? = "우리집",
    val floorPlanSourceName: String? = "기본 평면도",
    val settings: ProjectSettings? = ProjectSettings(),
)

data class ProjectSettings(
    val deadZoneThreshold: Int = -70,
    val selectedBand: WifiBand? = null,
    val signalSourceMode: SignalSourceMode = SignalSourceMode.DEVICE,
    val resultView: ResultView = ResultView.COVERAGE,
    val useWallAwareHeatmap: Boolean? = true,
)

object ProjectCodec {
    private val gson = Gson()
    fun encode(project: SavedProject): String = gson.toJson(project)
    fun decode(json: String): SavedProject = gson.fromJson(json, SavedProject::class.java)
}

class ProjectStore(context: Context) {
    private val directory = context.filesDir.resolve("wifi_heatmap_project")
    private val file = directory.resolve("project.json")
    private val legacyFile = context.filesDir.resolve("wifi_heatmap_project.json")
    private val floorPlanFile = directory.resolve("floorplan.png")

    fun save(project: SavedProject, floorPlan: Bitmap) {
        directory.mkdirs()
        val temporaryJson = directory.resolve("project.json.tmp")
        temporaryJson.writeText(ProjectCodec.encode(project))
        floorPlanFile.outputStream().use { floorPlan.compress(Bitmap.CompressFormat.PNG, 100, it) }
        temporaryJson.copyTo(file, overwrite = true)
        temporaryJson.delete()
        legacyFile.delete()
    }

    fun load(): SavedProject = ProjectCodec.decode((file.takeIf { it.exists() } ?: legacyFile).readText())
    fun loadFloorPlan(): Bitmap? = floorPlanFile.takeIf { it.exists() }?.let {
        BitmapFactory.decodeFile(it.absolutePath)
    }
    fun exists(): Boolean = file.exists() || legacyFile.exists()
    fun clear() {
        directory.deleteRecursively()
        legacyFile.delete()
    }
}
