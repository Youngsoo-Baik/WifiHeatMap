package com.example.wifiheatmap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

final class HeatmapView extends View {
    interface Listener {
        void onImportRequested();
        void onMeasureRequested(float normalizedX, float normalizedY);
        void onAddDevicePositionRequested(float normalizedX, float normalizedY);
        void onMeasurementDetailsRequested(Measurement measurement);
        void onDeviceDetailsRequested(NetworkDevice device);
        void onSaveRequested();
        void onLoadRequested();
    }

    private static final int[] DEVICE_COLORS = {
            Color.rgb(37, 99, 235), Color.rgb(147, 51, 234), Color.rgb(8, 145, 178),
            Color.rgb(219, 39, 119), Color.rgb(5, 150, 105), Color.rgb(202, 138, 4)
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final List<Measurement> measurements = new ArrayList<>();
    private final List<NetworkDevice> devices = new ArrayList<>();
    private final RectF importButton = new RectF();
    private final RectF deviceButton = new RectF();
    private final RectF undoButton = new RectF();
    private final RectF resetButton = new RectF();
    private final RectF measureButton = new RectF();
    private final RectF saveButton = new RectF();
    private final RectF loadButton = new RectF();
    private final RectF recommendButton = new RectF();
    private final RectF filterButton = new RectF();
    private final RectF mapRect = new RectF();
    private final RectF imageRect = new RectF();
    private Bitmap floorPlan;
    private Bitmap heatmap;
    private Listener listener;
    private float selectedX = -1f;
    private float selectedY = -1f;
    private boolean placementMode;
    private boolean hasSavedSession;
    private boolean recommendationActive;
    private String recommendationReason;
    private int filterDeviceIndex = -1;

    HeatmapView(Context context) {
        super(context);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        setBackgroundColor(Color.rgb(248, 250, 252));
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setFloorPlan(Bitmap floorPlan) {
        this.floorPlan = floorPlan;
        measurements.clear();
        devices.clear();
        placementMode = false;
        recommendationActive = false;
        filterDeviceIndex = -1;
        selectedX = -1f;
        selectedY = -1f;
        replaceHeatmap(null);
        invalidate();
    }

    void addMeasurement(float x, float y, List<SignalSample> samples) {
        measurements.add(new Measurement(x, y, samples));
        recommendationActive = false;
        selectedX = -1f;
        selectedY = -1f;
        rebuildHeatmap();
    }

    void addDevice(NetworkDevice device) {
        devices.add(device);
        placementMode = false;
        recommendationActive = false;
        selectedX = -1f;
        selectedY = -1f;
        rebuildHeatmap();
    }

    void restoreSession(Bitmap floorPlan, List<NetworkDevice> restoredDevices, List<Measurement> restoredMeasurements) {
        this.floorPlan = floorPlan;
        devices.clear();
        devices.addAll(restoredDevices);
        measurements.clear();
        measurements.addAll(restoredMeasurements);
        placementMode = false;
        recommendationActive = false;
        filterDeviceIndex = -1;
        selectedX = -1f;
        selectedY = -1f;
        rebuildHeatmap();
    }

    void setHasSavedSession(boolean hasSavedSession) {
        this.hasSavedSession = hasSavedSession;
        invalidate();
    }

    List<NetworkDevice> snapshotDevices() {
        return new ArrayList<>(devices);
    }

    List<Measurement> snapshotMeasurements() {
        return new ArrayList<>(measurements);
    }

    void removeDevice(NetworkDevice device) {
        int removedIndex = devices.indexOf(device);
        if (removedIndex < 0) return;
        devices.remove(removedIndex);
        recommendationActive = false;
        if (filterDeviceIndex == removedIndex) filterDeviceIndex = -1;
        else if (filterDeviceIndex > removedIndex) filterDeviceIndex--;
        rebuildHeatmap();
    }

    int deviceCount() {
        return devices.size();
    }

    int nextDeviceColor() {
        return DEVICE_COLORS[devices.size() % DEVICE_COLORS.length];
    }

    String[] describeMeasurement(Measurement measurement) {
        if (measurement.samples.isEmpty()) return new String[]{"측정된 AP가 없습니다."};
        String[] descriptions = new String[measurement.samples.size()];
        for (int index = 0; index < measurement.samples.size(); index++) {
            SignalSample sample = measurement.samples.get(index);
            NetworkDevice device = findDevice(sample.bssid);
            String owner = device == null ? "미등록 AP" : device.name + " · " + device.typeLabel();
            descriptions[index] = owner + "\n" + sample.ssid + " · " + sample.bandLabel()
                    + " · " + sample.rssi + " dBm\n" + sample.bssid;
        }
        return descriptions;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float margin = dp(16);
        drawHeader(canvas, width, margin);
        layoutMap(width, height, margin);
        drawMap(canvas);
        drawSummary(canvas, width, height, margin);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        float x = event.getX();
        float y = event.getY();
        if (importButton.contains(x, y)) {
            if (listener != null) listener.onImportRequested();
        } else if (deviceButton.contains(x, y)) {
            placementMode = !placementMode;
            recommendationActive = false;
            selectedX = -1f;
            selectedY = -1f;
            invalidate();
        } else if (undoButton.contains(x, y) && !measurements.isEmpty()) {
            measurements.remove(measurements.size() - 1);
            recommendationActive = false;
            rebuildHeatmap();
        } else if (resetButton.contains(x, y) && (!measurements.isEmpty() || selectedX >= 0f)) {
            measurements.clear();
            selectedX = -1f;
            selectedY = -1f;
            recommendationActive = false;
            replaceHeatmap(null);
            invalidate();
        } else if (recommendButton.contains(x, y) && !devices.isEmpty()) {
            recommendNextPosition();
        } else if (saveButton.contains(x, y) && (!measurements.isEmpty() || !devices.isEmpty())) {
            if (listener != null) listener.onSaveRequested();
        } else if (loadButton.contains(x, y) && hasSavedSession) {
            if (listener != null) listener.onLoadRequested();
        } else if (filterButton.contains(x, y) && !devices.isEmpty()) {
            filterDeviceIndex++;
            if (filterDeviceIndex >= devices.size()) filterDeviceIndex = -1;
            rebuildHeatmap();
        } else if (measureButton.contains(x, y) && selectedX >= 0f && !placementMode) {
            if (listener != null) listener.onMeasureRequested(selectedX, selectedY);
        } else if (floorPlan != null && imageRect.contains(x, y)) {
            float normalizedX = (x - imageRect.left) / imageRect.width();
            float normalizedY = (y - imageRect.top) / imageRect.height();
            if (placementMode) {
                if (listener != null) listener.onAddDevicePositionRequested(normalizedX, normalizedY);
                return true;
            }
            NetworkDevice touchedDevice = findDeviceNear(x, y);
            if (touchedDevice != null) {
                if (listener != null) listener.onDeviceDetailsRequested(touchedDevice);
                return true;
            }
            Measurement touchedMeasurement = findMeasurementNear(x, y);
            if (touchedMeasurement != null) {
                if (listener != null) listener.onMeasurementDetailsRequested(touchedMeasurement);
                return true;
            }
            selectedX = normalizedX;
            selectedY = normalizedY;
            recommendationActive = false;
            invalidate();
        }
        return true;
    }

    private void drawHeader(Canvas canvas, float width, float margin) {
        paint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, width, dp(124), paint);
        paint.setColor(Color.rgb(15, 23, 42));
        paint.setTextSize(dp(24));
        paint.setFakeBoldText(true);
        canvas.drawText("Wi-Fi Heatmap", margin, dp(36), paint);
        paint.setFakeBoldText(false);
        paint.setTextSize(dp(13));
        paint.setColor(Color.rgb(100, 116, 139));
        canvas.drawText("공유기·증폭기별 신호와 음영지역을 측정하세요", margin, dp(58), paint);

        float top = dp(72);
        float height = dp(38);
        importButton.set(margin, top, margin + dp(106), top + height);
        deviceButton.set(margin + dp(112), top, margin + dp(204), top + height);
        undoButton.set(width - margin - dp(104), top, width - margin - dp(54), top + height);
        resetButton.set(width - margin - dp(48), top, width - margin, top + height);
        drawButton(canvas, importButton, "평면도 변경", true, true);
        drawButton(canvas, deviceButton, placementMode ? "배치 취소" : "장비 추가", placementMode, true);
        drawButton(canvas, undoButton, "취소", false, !measurements.isEmpty());
        drawButton(canvas, resetButton, "초기화", false, !measurements.isEmpty() || selectedX >= 0f);
    }

    private void layoutMap(float width, float height, float margin) {
        mapRect.set(margin, dp(140), width - margin, height - dp(208));
        if (floorPlan == null) {
            imageRect.set(mapRect);
            return;
        }
        float scale = Math.min(mapRect.width() / floorPlan.getWidth(), mapRect.height() / floorPlan.getHeight());
        float imageWidth = floorPlan.getWidth() * scale;
        float imageHeight = floorPlan.getHeight() * scale;
        float left = mapRect.centerX() - imageWidth / 2f;
        float top = mapRect.centerY() - imageHeight / 2f;
        imageRect.set(left, top, left + imageWidth, top + imageHeight);
    }

    private void drawMap(Canvas canvas) {
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(mapRect, dp(14), dp(14), paint);
        if (floorPlan == null) {
            drawCenteredText(canvas, "평면도 이미지를 불러오세요", mapRect.centerX(), mapRect.centerY(), dp(17), Color.rgb(71, 85, 105), true);
            return;
        }

        canvas.save();
        canvas.clipRect(imageRect);
        canvas.drawBitmap(floorPlan, null, imageRect, paint);
        if (heatmap != null) canvas.drawBitmap(heatmap, null, imageRect, paint);
        drawDevices(canvas);
        drawMeasurements(canvas);
        drawSelectedPosition(canvas);
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.rgb(148, 163, 184));
        canvas.drawRect(imageRect, paint);
        paint.setStyle(Paint.Style.FILL);

        if (devices.isEmpty()) {
            filterButton.setEmpty();
        } else {
            float buttonWidth = dp(132);
            filterButton.set(imageRect.right - buttonWidth - dp(8), imageRect.top + dp(8), imageRect.right - dp(8), imageRect.top + dp(42));
            String label = filterDeviceIndex < 0 ? "히트맵: 전체" : "히트맵: " + devices.get(filterDeviceIndex).name;
            drawButton(canvas, filterButton, label, false, true);
        }
    }

    private void drawDevices(Canvas canvas) {
        for (NetworkDevice device : devices) {
            float x = imageRect.left + device.x * imageRect.width();
            float y = imageRect.top + device.y * imageRect.height();
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, dp(16), paint);
            paint.setColor(device.color);
            canvas.drawCircle(x, y, dp(13), paint);
            drawCenteredText(canvas, device.type == NetworkDevice.TYPE_ROUTER ? "R" : "E", x, y + dp(5), dp(12), Color.WHITE, true);
            drawCenteredText(canvas, device.name, x, y + dp(31), dp(10), Color.rgb(15, 23, 42), true);
        }
    }

    private void drawMeasurements(Canvas canvas) {
        for (int index = 0; index < measurements.size(); index++) {
            Measurement measurement = measurements.get(index);
            float x = imageRect.left + measurement.x * imageRect.width();
            float y = imageRect.top + measurement.y * imageRect.height();
            NetworkDevice owner = strongestDeviceFor(measurement);
            paint.setColor(owner == null ? Color.WHITE : owner.color);
            canvas.drawCircle(x, y, dp(11), paint);
            paint.setColor(owner == null ? HeatmapRenderer.colorForRssi(measurement.rssi, 255) : Color.WHITE);
            canvas.drawCircle(x, y, dp(7), paint);
            int textColor = owner == null ? Color.WHITE : owner.color;
            drawCenteredText(canvas, String.valueOf(index + 1), x, y + dp(4), dp(9), textColor, true);
        }
    }

    private void drawSelectedPosition(Canvas canvas) {
        if (selectedX < 0f) return;
        float x = imageRect.left + selectedX * imageRect.width();
        float y = imageRect.top + selectedY * imageRect.height();
        if (recommendationActive) {
            NetworkDevice nearestDevice = nearestDevice(selectedX, selectedY);
            if (nearestDevice != null) {
                float deviceX = imageRect.left + nearestDevice.x * imageRect.width();
                float deviceY = imageRect.top + nearestDevice.y * imageRect.height();
                paint.setStrokeWidth(dp(2));
                paint.setColor((nearestDevice.color & 0x00FFFFFF) | 0x77000000);
                canvas.drawLine(deviceX, deviceY, x, y, paint);
            }
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));
        paint.setColor(recommendationActive ? Color.rgb(124, 58, 237) : Color.rgb(37, 99, 235));
        canvas.drawCircle(x, y, dp(recommendationActive ? 18 : 14), paint);
        canvas.drawCircle(x, y, dp(4), paint);
        paint.setStyle(Paint.Style.FILL);
        if (recommendationActive) {
            drawCenteredText(canvas, "추천", x, y - dp(23), dp(11), Color.rgb(91, 33, 182), true);
        }
    }

    private void drawSummary(Canvas canvas, float width, float height, float margin) {
        float top = height - dp(190);
        int deadZones = 0;
        int totalRssi = 0;
        int visibleMeasurements = 0;
        for (Measurement measurement : measurements) {
            Integer rssi = displayRssi(measurement);
            if (rssi == null) continue;
            visibleMeasurements++;
            totalRssi += rssi;
            if (rssi <= -75) deadZones++;
        }

        String instruction;
        if (placementMode) instruction = "공유기 또는 증폭기 위치를 평면도에서 탭하세요";
        else if (recommendationActive && recommendationReason != null) instruction = recommendationReason;
        else if (selectedX < 0f) instruction = "측정 위치를 탭하거나 기존 측정점을 선택하세요";
        else instruction = "선택한 위치에서 주변 AP 신호를 측정하세요";
        drawCenteredText(canvas, instruction, width / 2f, top, dp(13), Color.rgb(71, 85, 105), false);

        float legendTop = top + dp(18);
        float segmentWidth = (width - margin * 2) / 4f;
        int[] colors = {Color.rgb(34, 197, 94), Color.rgb(250, 204, 21), Color.rgb(249, 115, 22), Color.rgb(220, 38, 38)};
        String[] labels = {"강함", "양호", "약함", "음영"};
        for (int index = 0; index < 4; index++) {
            paint.setColor(colors[index]);
            canvas.drawRect(margin + segmentWidth * index, legendTop, margin + segmentWidth * (index + 1), legendTop + dp(7), paint);
            drawCenteredText(canvas, labels[index], margin + segmentWidth * (index + 0.5f), legendTop + dp(24), dp(11), Color.rgb(100, 116, 139), false);
        }

        String stats = visibleMeasurements == 0
                ? "표시할 측정값 없음  ·  등록 장비 " + devices.size() + "개"
                : "측정 " + visibleMeasurements + "개  ·  평균 " + Math.round((float) totalRssi / visibleMeasurements)
                + " dBm  ·  음영 후보 " + deadZones + "곳";
        drawCenteredText(canvas, stats, width / 2f, legendTop + dp(47), dp(12), deadZones > 0 ? Color.rgb(185, 28, 28) : Color.rgb(71, 85, 105), deadZones > 0);

        float sessionTop = height - dp(112);
        float sessionBottom = height - dp(72);
        float gap = dp(6);
        float sessionWidth = (width - margin * 2 - gap * 2) / 3f;
        recommendButton.set(margin, sessionTop, margin + sessionWidth, sessionBottom);
        saveButton.set(recommendButton.right + gap, sessionTop, recommendButton.right + gap + sessionWidth, sessionBottom);
        loadButton.set(saveButton.right + gap, sessionTop, width - margin, sessionBottom);
        drawButton(canvas, recommendButton, "위치 추천", false, !devices.isEmpty());
        drawButton(canvas, saveButton, "결과 저장", false, !measurements.isEmpty() || !devices.isEmpty());
        drawButton(canvas, loadButton, "불러오기", false, hasSavedSession);

        measureButton.set(margin, height - dp(62), width - margin, height - dp(14));
        String measureLabel = placementMode ? "장비를 배치할 위치를 탭하세요"
                : selectedX >= 0f ? "이 위치에서 모든 Wi-Fi 신호 측정" : "평면도에서 위치를 선택하세요";
        drawButton(canvas, measureButton, measureLabel, true, floorPlan != null && selectedX >= 0f && !placementMode);
    }

    private void drawButton(Canvas canvas, RectF bounds, String label, boolean primary, boolean enabled) {
        int background;
        int foreground;
        if (!enabled) {
            background = Color.rgb(226, 232, 240);
            foreground = Color.rgb(148, 163, 184);
        } else if (primary) {
            background = Color.rgb(37, 99, 235);
            foreground = Color.WHITE;
        } else {
            background = Color.argb(235, 241, 245, 249);
            foreground = Color.rgb(71, 85, 105);
        }
        paint.setColor(background);
        canvas.drawRoundRect(bounds, dp(10), dp(10), paint);
        drawCenteredText(canvas, label, bounds.centerX(), bounds.centerY() + dp(4), dp(primary ? 12 : 11), foreground, primary);
    }

    private void drawCenteredText(Canvas canvas, String text, float x, float baseline, float size, int color, boolean bold) {
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setFakeBoldText(bold);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, x, baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(false);
    }

    private void rebuildHeatmap() {
        List<Measurement> visible = new ArrayList<>();
        for (Measurement measurement : measurements) {
            Integer rssi = displayRssi(measurement);
            if (rssi != null) visible.add(new Measurement(measurement.x, measurement.y, rssi));
        }
        replaceHeatmap(visible.isEmpty() ? null : HeatmapRenderer.render(visible));
        invalidate();
    }

    private void recommendNextPosition() {
        RecommendationEngine.Result result = RecommendationEngine.recommend(floorPlan, devices, measurements);
        if (result == null) {
            Toast.makeText(getContext(), "먼저 공유기 또는 증폭기 위치를 등록해 주세요.", Toast.LENGTH_LONG).show();
            return;
        }
        selectedX = result.x;
        selectedY = result.y;
        recommendationReason = result.reason;
        recommendationActive = true;
        placementMode = false;
        invalidate();
    }

    private Integer displayRssi(Measurement measurement) {
        if (filterDeviceIndex < 0 || filterDeviceIndex >= devices.size()) return measurement.rssi <= -127 ? null : measurement.rssi;
        return measurement.strongestFor(devices.get(filterDeviceIndex));
    }

    private NetworkDevice strongestDeviceFor(Measurement measurement) {
        NetworkDevice strongestDevice = null;
        Integer strongestRssi = null;
        for (NetworkDevice device : devices) {
            Integer rssi = measurement.strongestFor(device);
            if (rssi != null && (strongestRssi == null || rssi > strongestRssi)) {
                strongestRssi = rssi;
                strongestDevice = device;
            }
        }
        return strongestDevice;
    }

    private NetworkDevice findDevice(String bssid) {
        for (NetworkDevice device : devices) {
            if (device.matches(bssid)) return device;
        }
        return null;
    }

    private NetworkDevice nearestDevice(float x, float y) {
        NetworkDevice nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        for (NetworkDevice device : devices) {
            float dx = x - device.x;
            float dy = y - device.y;
            float distance = dx * dx + dy * dy;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = device;
            }
        }
        return nearest;
    }

    private NetworkDevice findDeviceNear(float touchX, float touchY) {
        for (NetworkDevice device : devices) {
            float x = imageRect.left + device.x * imageRect.width();
            float y = imageRect.top + device.y * imageRect.height();
            if (distance(touchX, touchY, x, y) <= dp(20)) return device;
        }
        return null;
    }

    private Measurement findMeasurementNear(float touchX, float touchY) {
        for (Measurement measurement : measurements) {
            float x = imageRect.left + measurement.x * imageRect.width();
            float y = imageRect.top + measurement.y * imageRect.height();
            if (distance(touchX, touchY, x, y) <= dp(18)) return measurement;
        }
        return null;
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void replaceHeatmap(Bitmap next) {
        if (heatmap != null && heatmap != next) heatmap.recycle();
        heatmap = next;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
