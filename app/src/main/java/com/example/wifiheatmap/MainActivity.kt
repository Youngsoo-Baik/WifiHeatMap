package com.example.wifiheatmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.wifiheatmap.navigation.WifiHeatmapNavigation
import com.example.wifiheatmap.ui.theme.WifiHeatmapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WifiHeatmapTheme {
                WifiHeatmapNavigation()
            }
        }
    }
}
