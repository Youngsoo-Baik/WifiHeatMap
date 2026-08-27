package com.example.wifiheatmap.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.wifiheatmap.ui.debug.WifiDebugScreen
import com.example.wifiheatmap.ui.floorplan.FloorPlanScreen

private const val WIFI_DEBUG_ROUTE = "wifi_debug"
private const val FLOOR_PLAN_ROUTE = "floor_plan"

@Composable
fun WifiHeatmapNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = WIFI_DEBUG_ROUTE) {
        composable(WIFI_DEBUG_ROUTE) {
            WifiDebugScreen(onOpenFloorPlan = { navController.navigate(FLOOR_PLAN_ROUTE) })
        }
        composable(FLOOR_PLAN_ROUTE) {
            FloorPlanScreen(onBack = { navController.popBackStack() })
        }
    }
}
