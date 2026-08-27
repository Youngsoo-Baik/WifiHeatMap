package com.example.wifiheatmap.persistence

import android.content.Context
import com.example.wifiheatmap.calibration.CalibrationData
import com.example.wifiheatmap.device.WifiDevice
import com.example.wifiheatmap.survey.SurveyMeasurement
import com.example.wifiheatmap.wall.WallSegment
import com.google.gson.Gson

data class SavedProject(
    val calibration: CalibrationData?,
    val measurements: List<SurveyMeasurement>,
    val devices: List<WifiDevice>,
    val walls: List<WallSegment>,
)

object ProjectCodec {
    private val gson = Gson()
    fun encode(project: SavedProject): String = gson.toJson(project)
    fun decode(json: String): SavedProject = gson.fromJson(json, SavedProject::class.java)
}

class ProjectStore(context: Context) {
    private val file = context.filesDir.resolve("wifi_heatmap_project.json")
    fun save(project: SavedProject) = file.writeText(ProjectCodec.encode(project))
    fun load(): SavedProject = ProjectCodec.decode(file.readText())
    fun exists(): Boolean = file.exists()
}
