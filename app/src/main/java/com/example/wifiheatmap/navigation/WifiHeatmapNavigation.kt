package com.example.wifiheatmap.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.wifiheatmap.ui.debug.WifiDebugScreen
import com.example.wifiheatmap.ui.floorplan.CalibrationScreen
import com.example.wifiheatmap.ui.floorplan.FloorPlanScreen
import com.example.wifiheatmap.ui.survey.SurveyScreen
import com.example.wifiheatmap.ui.heatmap.HeatmapScreen
import com.example.wifiheatmap.viewmodel.FloorPlanViewModel

private const val WIFI_DEBUG_ROUTE = "wifi_debug"
private const val FLOOR_PLAN_ROUTE = "floor_plan"
private const val CALIBRATION_ROUTE = "calibration"
private const val SURVEY_ROUTE = "survey"
private const val HEATMAP_ROUTE = "heatmap"

@Composable
fun WifiHeatmapNavigation() {
    val navController = rememberNavController()
    val floorPlanViewModel: FloorPlanViewModel = viewModel()
    NavHost(navController = navController, startDestination = WIFI_DEBUG_ROUTE) {
        composable(WIFI_DEBUG_ROUTE) {
            WifiDebugScreen(onOpenFloorPlan = { navController.navigate(FLOOR_PLAN_ROUTE) })
        }
        composable(FLOOR_PLAN_ROUTE) {
            FloorPlanScreen(
                onBack = { navController.popBackStack() },
                onOpenCalibration = { navController.navigate(CALIBRATION_ROUTE) },
                viewModel = floorPlanViewModel,
            )
        }
        composable(CALIBRATION_ROUTE) {
            CalibrationScreen(
                onBack = { navController.popBackStack() },
                onOpenSurvey = { navController.navigate(SURVEY_ROUTE) },
                viewModel = floorPlanViewModel,
            )
        }
        composable(SURVEY_ROUTE) {
            SurveyScreen(
                onBack = { navController.popBackStack() },
                onOpenHeatmap = { navController.navigate(HEATMAP_ROUTE) },
                viewModel = floorPlanViewModel,
            )
        }
        composable(HEATMAP_ROUTE) {
            HeatmapScreen(
                onBack = { navController.popBackStack() },
                onNext = { },
                viewModel = floorPlanViewModel,
            )
        }
    }
}
