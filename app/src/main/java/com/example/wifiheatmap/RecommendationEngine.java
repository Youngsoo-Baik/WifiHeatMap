package com.example.wifiheatmap;

import android.graphics.Bitmap;

import java.util.List;

final class RecommendationEngine {
    static final class Result {
        final float x;
        final float y;
        final String reason;

        Result(float x, float y, String reason) {
            this.x = x;
            this.y = y;
            this.reason = reason;
        }
    }

    private static final int GRID_SIZE = 25;

    private RecommendationEngine() {}

    static Result recommend(Bitmap floorPlan, List<NetworkDevice> devices, List<Measurement> measurements) {
        if (floorPlan == null || devices.isEmpty()) return null;
        if (measurements.isEmpty()) {
            NetworkDevice baselineDevice = devices.get(0);
            for (NetworkDevice device : devices) {
                if (device.type == NetworkDevice.TYPE_ROUTER) {
                    baselineDevice = device;
                    break;
                }
            }
            return new Result(baselineDevice.x, baselineDevice.y, "공유기 근처 기준 신호를 먼저 측정하세요");
        }

        Result result = findBestCandidate(floorPlan, devices, measurements, true);
        if (result == null) result = findBestCandidate(floorPlan, devices, measurements, false);
        return result;
    }

    private static Result findBestCandidate(
            Bitmap floorPlan,
            List<NetworkDevice> devices,
            List<Measurement> measurements,
            boolean requireFloorContent) {
        float bestScore = -1f;
        float bestX = 0.5f;
        float bestY = 0.5f;
        boolean bestIsWeakBoundary = false;

        for (int row = 0; row < GRID_SIZE; row++) {
            float y = 0.04f + 0.92f * row / (GRID_SIZE - 1f);
            for (int column = 0; column < GRID_SIZE; column++) {
                float x = 0.04f + 0.92f * column / (GRID_SIZE - 1f);
                float content = floorContentConfidence(floorPlan, x, y);
                if (requireFloorContent && content < 0.16f) continue;

                float nearestMeasurement = Float.MAX_VALUE;
                for (Measurement measurement : measurements) {
                    nearestMeasurement = Math.min(nearestMeasurement,
                            normalizedDistance(floorPlan, x, y, measurement.x, measurement.y));
                }
                if (nearestMeasurement < 0.055f) continue;

                float nearestDevice = Float.MAX_VALUE;
                for (NetworkDevice device : devices) {
                    nearestDevice = Math.min(nearestDevice,
                            normalizedDistance(floorPlan, x, y, device.x, device.y));
                }

                int predictedRssi = predictedRssi(floorPlan, x, y, measurements);
                boolean weakBoundary = predictedRssi <= -70;
                float weakBonus = weakBoundary ? 0.07f : 0f;
                float anchorCoverage = Math.min(nearestMeasurement, nearestDevice * 0.72f);
                float contentFactor = requireFloorContent ? 0.78f + content * 0.22f : 1f;
                float score = (anchorCoverage * 0.68f + nearestMeasurement * 0.32f + weakBonus) * contentFactor;

                if (score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                    bestIsWeakBoundary = weakBoundary;
                }
            }
        }

        if (bestScore < 0f) return null;
        String reason = bestIsWeakBoundary
                ? "약한 신호 경계와 미측정 영역을 우선 확인하세요"
                : "기존 측정점에서 가장 멀리 떨어진 영역입니다";
        return new Result(bestX, bestY, reason);
    }

    private static int predictedRssi(Bitmap floorPlan, float x, float y, List<Measurement> measurements) {
        float weighted = 0f;
        float totalWeight = 0f;
        for (Measurement measurement : measurements) {
            float distance = normalizedDistance(floorPlan, x, y, measurement.x, measurement.y);
            float weight = 1f / (distance * distance + 0.0025f);
            weighted += measurement.rssi * weight;
            totalWeight += weight;
        }
        return totalWeight == 0f ? -127 : Math.round(weighted / totalWeight);
    }

    private static float normalizedDistance(Bitmap floorPlan, float x1, float y1, float x2, float y2) {
        float width = floorPlan.getWidth();
        float height = floorPlan.getHeight();
        float diagonal = (float) Math.sqrt(width * width + height * height);
        float dx = (x1 - x2) * width / diagonal;
        float dy = (y1 - y2) * height / diagonal;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float floorContentConfidence(Bitmap floorPlan, float normalizedX, float normalizedY) {
        int centerX = Math.max(0, Math.min(floorPlan.getWidth() - 1, Math.round(normalizedX * (floorPlan.getWidth() - 1))));
        int centerY = Math.max(0, Math.min(floorPlan.getHeight() - 1, Math.round(normalizedY * (floorPlan.getHeight() - 1))));
        int centerPixel = floorPlan.getPixel(centerX, centerY);
        int centerRed = (centerPixel >> 16) & 0xFF;
        int centerGreen = (centerPixel >> 8) & 0xFF;
        int centerBlue = centerPixel & 0xFF;
        if ((centerRed + centerGreen + centerBlue) / 3 < 75) return 0f;

        int radius = Math.max(3, Math.min(floorPlan.getWidth(), floorPlan.getHeight()) / 90);
        int contentPixels = 0;
        int sampleCount = 0;
        for (int offsetY = -radius; offsetY <= radius; offsetY += Math.max(1, radius / 2)) {
            for (int offsetX = -radius; offsetX <= radius; offsetX += Math.max(1, radius / 2)) {
                int pixelX = Math.max(0, Math.min(floorPlan.getWidth() - 1, centerX + offsetX));
                int pixelY = Math.max(0, Math.min(floorPlan.getHeight() - 1, centerY + offsetY));
                int pixel = floorPlan.getPixel(pixelX, pixelY);
                int alpha = (pixel >>> 24) & 0xFF;
                int red = (pixel >> 16) & 0xFF;
                int green = (pixel >> 8) & 0xFF;
                int blue = pixel & 0xFF;
                if (alpha > 20 && (red < 247 || green < 247 || blue < 247)) contentPixels++;
                sampleCount++;
            }
        }
        return sampleCount == 0 ? 0f : (float) contentPixels / sampleCount;
    }
}
