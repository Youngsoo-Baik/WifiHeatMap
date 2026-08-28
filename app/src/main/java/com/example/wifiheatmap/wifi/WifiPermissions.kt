package com.example.wifiheatmap.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

fun requiredWifiPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}.toTypedArray()

fun hasWifiPermissions(context: Context): Boolean = requiredWifiPermissions().all { permission ->
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
