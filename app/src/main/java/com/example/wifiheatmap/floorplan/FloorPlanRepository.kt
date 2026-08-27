package com.example.wifiheatmap.floorplan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.wifiheatmap.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FloorPlanRepository(private val context: Context) {
    fun loadDefault(): Bitmap = BitmapFactory.decodeResource(
        context.resources,
        R.drawable.default_floor_plan,
    )

    suspend fun load(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: error("선택한 이미지를 열 수 없습니다.")
    }
}
