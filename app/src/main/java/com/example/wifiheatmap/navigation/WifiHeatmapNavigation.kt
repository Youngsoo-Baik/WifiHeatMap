package com.example.wifiheatmap.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.wifiheatmap.ui.debug.WifiDebugScreen
import com.example.wifiheatmap.ui.device.DeviceScreen
import com.example.wifiheatmap.ui.device.AccessPointReviewScreen
import com.example.wifiheatmap.ui.floorplan.CalibrationScreen
import com.example.wifiheatmap.ui.floorplan.FloorPlanScreen
import com.example.wifiheatmap.ui.home.HomeResultsScreen
import com.example.wifiheatmap.ui.settings.SettingsScreen
import com.example.wifiheatmap.ui.survey.AutoSurveyScreen
import com.example.wifiheatmap.ui.survey.SurveyScreen
import com.example.wifiheatmap.ui.wall.WallEditorScreen
import com.example.wifiheatmap.viewmodel.FloorPlanViewModel

internal const val HOME_ROUTE = "home"
internal const val SETTINGS_ROUTE = "settings"
internal const val FLOOR_PLAN_ROUTE = "settings/floor_plan"
internal const val CALIBRATION_ROUTE = "settings/calibration"
internal const val WALL_ROUTE = "settings/walls"
internal const val DEVICE_ROUTE = "settings/devices"
internal const val ACCESS_POINT_REVIEW_ROUTE = "review/access_points"
internal const val SURVEY_ROUTE = "survey/manual"
internal const val AUTO_SURVEY_ROUTE = "survey/automatic"
internal const val WIFI_DEBUG_ROUTE = "diagnostics/wifi"

@Composable
fun WifiHeatmapNavigation() {
    val navController = rememberNavController()
    val floorPlanViewModel: FloorPlanViewModel = viewModel()

    fun returnHome() = navController.returnHome()
    fun applyAndReturnHome() {
        floorPlanViewModel.saveProject()
        navController.returnHome()
    }

    NavHost(navController = navController, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) {
            HomeResultsScreen(
                onStartAutoSurvey = { navController.navigateSingleTop(AUTO_SURVEY_ROUTE) },
                onStartManualSurvey = { navController.navigateSingleTop(SURVEY_ROUTE) },
                onReviewAccessPoints = { navController.navigateSingleTop(ACCESS_POINT_REVIEW_ROUTE) },
                onOpenSettings = { navController.navigateSingleTop(SETTINGS_ROUTE) },
                onOpenDiagnostics = { navController.navigateSingleTop(WIFI_DEBUG_ROUTE) },
                viewModel = floorPlanViewModel,
            )
        }
        composable(SETTINGS_ROUTE) {
            SettingsScreen(
                onBackHome = ::returnHome,
                onOpenFloorPlan = { navController.navigateSingleTop(FLOOR_PLAN_ROUTE) },
                onOpenCalibration = { navController.navigateSingleTop(CALIBRATION_ROUTE) },
                onOpenWalls = { navController.navigateSingleTop(WALL_ROUTE) },
                onOpenDevices = { navController.navigateSingleTop(DEVICE_ROUTE) },
                viewModel = floorPlanViewModel,
            )
        }
        composable(FLOOR_PLAN_ROUTE) {
            FloorPlanScreen(
                onBack = ::applyAndReturnHome,
                onDone = ::applyAndReturnHome,
                viewModel = floorPlanViewModel,
            )
        }
        composable(CALIBRATION_ROUTE) {
            CalibrationScreen(
                onBack = ::applyAndReturnHome,
                onDone = ::applyAndReturnHome,
                viewModel = floorPlanViewModel,
            )
        }
        composable(WALL_ROUTE) {
            WallEditorScreen(
                onBack = ::applyAndReturnHome,
                onDone = ::applyAndReturnHome,
                viewModel = floorPlanViewModel,
            )
        }
        composable(DEVICE_ROUTE) {
            DeviceScreen(
                onBack = ::applyAndReturnHome,
                onDone = ::applyAndReturnHome,
                viewModel = floorPlanViewModel,
            )
        }
        composable(ACCESS_POINT_REVIEW_ROUTE) {
            AccessPointReviewScreen(
                onBackHome = ::returnHome,
                onDone = ::applyAndReturnHome,
                viewModel = floorPlanViewModel,
            )
        }
        composable(SURVEY_ROUTE) {
            SurveyScreen(
                onBack = ::applyAndReturnHome,
                onDone = ::applyAndReturnHome,
                viewModel = floorPlanViewModel,
            )
        }
        composable(AUTO_SURVEY_ROUTE) {
            AutoSurveyScreen(
                onBackHome = ::returnHome,
                onOpenCalibration = { navController.navigateSingleTop(CALIBRATION_ROUTE) },
                onOpenManualSurvey = { navController.navigateSingleTop(SURVEY_ROUTE) },
                floorPlanViewModel = floorPlanViewModel,
            )
        }
        composable(WIFI_DEBUG_ROUTE) {
            WifiDebugScreen(onBackHome = ::returnHome)
        }
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) { launchSingleTop = true }
}

private fun NavHostController.returnHome() {
    navigate(HOME_ROUTE) {
        popUpTo(HOME_ROUTE) { inclusive = false }
        launchSingleTop = true
    }
}
