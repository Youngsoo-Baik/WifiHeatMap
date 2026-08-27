package com.example.wifiheatmap;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.List;

final class HeatmapRenderer {
    private static final int GRID_WIDTH = 72;
    private static final int GRID_HEIGHT = 96;
    private static final float MAX_INFLUENCE = 0.34f;

    private HeatmapRenderer() {}

    static Bitmap render(List<Measurement> measurements) {
        Bitmap bitmap = Bitmap.createBitmap(GRID_WIDTH, GRID_HEIGHT, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[GRID_WIDTH * GRID_HEIGHT];

        for (int row = 0; row < GRID_HEIGHT; row++) {
            float y = (row + 0.5f) / GRID_HEIGHT;
            for (int column = 0; column < GRID_WIDTH; column++) {
                float x = (column + 0.5f) / GRID_WIDTH;
                float weightedRssi = 0f;
                float totalWeight = 0f;
                float nearestDistance = Float.MAX_VALUE;

                for (Measurement measurement : measurements) {
                    float dx = x - measurement.x;
                    float dy = y - measurement.y;
                    float distanceSquared = dx * dx + dy * dy;
                    nearestDistance = Math.min(nearestDistance, (float) Math.sqrt(distanceSquared));
                    float weight = 1f / (distanceSquared + 0.0025f);
                    weightedRssi += measurement.rssi * weight;
                    totalWeight += weight;
                }

                if (totalWeight > 0f && nearestDistance <= MAX_INFLUENCE) {
                    int rssi = Math.round(weightedRssi / totalWeight);
                    int alpha = Math.round(175f * (1f - nearestDistance / MAX_INFLUENCE * 0.45f));
                    pixels[row * GRID_WIDTH + column] = colorForRssi(rssi, alpha);
                }
            }
        }

        bitmap.setPixels(pixels, 0, GRID_WIDTH, 0, 0, GRID_WIDTH, GRID_HEIGHT);
        return bitmap;
    }

    static int colorForRssi(int rssi, int alpha) {
        float value = Math.max(-90f, Math.min(-30f, rssi));
        if (value >= -50f) {
            return blend(Color.rgb(34, 197, 94), Color.rgb(250, 204, 21), (-50f - value) / -20f, alpha);
        }
        if (value >= -67f) {
            return blend(Color.rgb(250, 204, 21), Color.rgb(249, 115, 22), (-50f - value) / 17f, alpha);
        }
        return blend(Color.rgb(249, 115, 22), Color.rgb(220, 38, 38), (-67f - value) / 23f, alpha);
    }

    private static int blend(int start, int end, float fraction, int alpha) {
        float bounded = Math.max(0f, Math.min(1f, fraction));
        int red = Math.round(Color.red(start) + (Color.red(end) - Color.red(start)) * bounded);
        int green = Math.round(Color.green(start) + (Color.green(end) - Color.green(start)) * bounded);
        int blue = Math.round(Color.blue(start) + (Color.blue(end) - Color.blue(start)) * bounded);
        return Color.argb(alpha, red, green, blue);
    }
}
