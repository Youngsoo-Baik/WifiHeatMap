package com.example.wifiheatmap;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MainActivity extends Activity implements HeatmapView.Listener {
    private static final int OPEN_FLOOR_PLAN = 10;
    private static final int WIFI_PERMISSION = 11;
    private static final int ACTION_NONE = 0;
    private static final int ACTION_MEASURE = 1;
    private static final int ACTION_ADD_DEVICE = 2;

    private HeatmapView heatmapView;
    private WifiManager wifiManager;
    private SessionStore sessionStore;
    private String currentFloorPlanUri;
    private int pendingAction = ACTION_NONE;
    private float pendingX = -1f;
    private float pendingY = -1f;
    private boolean receiverRegistered;

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction()) && pendingAction != ACTION_NONE) {
                completePendingScan(false);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        sessionStore = new SessionStore(this);
        heatmapView = new HeatmapView(this);
        heatmapView.setListener(this);
        heatmapView.setFloorPlan(BitmapFactory.decodeResource(getResources(), R.drawable.default_floor_plan));
        heatmapView.setHasSavedSession(sessionStore.hasSession());
        setContentView(heatmapView);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(scanReceiver, filter);
        }
        receiverRegistered = true;
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(scanReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    public void onImportRequested() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, OPEN_FLOOR_PLAN);
    }

    @Override
    public void onMeasureRequested(float normalizedX, float normalizedY) {
        beginWifiScan(ACTION_MEASURE, normalizedX, normalizedY);
    }

    @Override
    public void onAddDevicePositionRequested(float normalizedX, float normalizedY) {
        beginWifiScan(ACTION_ADD_DEVICE, normalizedX, normalizedY);
    }

    @Override
    public void onMeasurementDetailsRequested(Measurement measurement) {
        new AlertDialog.Builder(this)
                .setTitle("측정 위치 신호")
                .setItems(heatmapView.describeMeasurement(measurement), null)
                .setPositiveButton("확인", null)
                .show();
    }

    @Override
    public void onDeviceDetailsRequested(final NetworkDevice device) {
        StringBuilder message = new StringBuilder();
        message.append(device.typeLabel()).append("\n\n연결된 BSSID");
        for (String bssid : device.bssids) {
            message.append("\n").append(bssid);
        }
        new AlertDialog.Builder(this)
                .setTitle(device.name)
                .setMessage(message.toString())
                .setNegativeButton("장비 삭제", (dialog, which) -> heatmapView.removeDevice(device))
                .setPositiveButton("확인", null)
                .show();
    }

    @Override
    public void onSaveRequested() {
        boolean saved = sessionStore.save(
                currentFloorPlanUri,
                heatmapView.snapshotDevices(),
                heatmapView.snapshotMeasurements());
        heatmapView.setHasSavedSession(saved || sessionStore.hasSession());
        Toast.makeText(this, saved ? "측정 결과를 저장했습니다." : "측정 결과를 저장하지 못했습니다.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onLoadRequested() {
        SessionStore.SessionData session = sessionStore.load();
        if (session == null) {
            heatmapView.setHasSavedSession(false);
            Toast.makeText(this, "저장된 측정 결과를 읽을 수 없습니다.", Toast.LENGTH_LONG).show();
            return;
        }
        String savedTime = session.savedAt > 0
                ? DateFormat.format("yyyy-MM-dd HH:mm", session.savedAt).toString()
                : "저장 시각 없음";
        String message = savedTime + "\n장비 " + session.devices.size() + "개 · 측정점 "
                + session.measurements.size() + "개\n\n현재 화면을 저장된 결과로 바꿀까요?";
        new AlertDialog.Builder(this)
                .setTitle("측정 결과 불러오기")
                .setMessage(message)
                .setNegativeButton("취소", null)
                .setPositiveButton("불러오기", (dialog, which) -> restoreSession(session))
                .show();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != OPEN_FLOOR_PLAN || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap == null) throw new IOException("Unsupported image");
            currentFloorPlanUri = uri.toString();
            heatmapView.setFloorPlan(bitmap);
        } catch (IOException exception) {
            Toast.makeText(this, "평면도 이미지를 열 수 없습니다.", Toast.LENGTH_LONG).show();
        }
    }

    private void restoreSession(SessionStore.SessionData session) {
        Bitmap floorPlan = null;
        if (session.floorPlanUri != null) {
            try (InputStream stream = getContentResolver().openInputStream(Uri.parse(session.floorPlanUri))) {
                floorPlan = BitmapFactory.decodeStream(stream);
                if (floorPlan != null) currentFloorPlanUri = session.floorPlanUri;
            } catch (IOException | SecurityException ignored) {
            }
        }
        if (floorPlan == null) {
            floorPlan = BitmapFactory.decodeResource(getResources(), R.drawable.default_floor_plan);
            currentFloorPlanUri = null;
            if (session.floorPlanUri != null) {
                Toast.makeText(this, "저장된 평면도를 열 수 없어 기본 평면도를 사용합니다.", Toast.LENGTH_LONG).show();
            }
        }
        heatmapView.restoreSession(floorPlan, session.devices, session.measurements);
        Toast.makeText(this, "측정 결과를 불러왔습니다.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != WIFI_PERMISSION) return;
        if (hasWifiPermissions()) {
            startWifiScan();
        } else {
            pendingAction = ACTION_NONE;
            Toast.makeText(this, "주변 AP 구분을 위해 위치 및 근처 기기 권한이 필요합니다.", Toast.LENGTH_LONG).show();
        }
    }

    private void beginWifiScan(int action, float x, float y) {
        pendingAction = action;
        pendingX = x;
        pendingY = y;
        if (!hasWifiPermissions()) {
            requestWifiPermissions();
            return;
        }
        startWifiScan();
    }

    @SuppressWarnings("deprecation")
    private void startWifiScan() {
        if (!wifiManager.isWifiEnabled()) {
            pendingAction = ACTION_NONE;
            Toast.makeText(this, "Wi-Fi를 켜 주세요.", Toast.LENGTH_LONG).show();
            if (Build.VERSION.SDK_INT >= 29) startActivity(new Intent(Settings.Panel.ACTION_WIFI));
            return;
        }
        boolean started;
        try {
            started = wifiManager.startScan();
        } catch (SecurityException exception) {
            pendingAction = ACTION_NONE;
            Toast.makeText(this, "Wi-Fi 스캔 권한을 확인해 주세요.", Toast.LENGTH_LONG).show();
            return;
        }
        if (started) {
            Toast.makeText(this, "주변 Wi-Fi 신호를 스캔 중입니다…", Toast.LENGTH_SHORT).show();
            heatmapView.postDelayed(() -> {
                if (pendingAction != ACTION_NONE) completePendingScan(true);
            }, 8000L);
        } else {
            completePendingScan(true);
        }
    }

    @SuppressWarnings("deprecation")
    private void completePendingScan(boolean cached) {
        int action = pendingAction;
        float x = pendingX;
        float y = pendingY;
        pendingAction = ACTION_NONE;

        List<SignalSample> samples = new ArrayList<>();
        try {
            List<ScanResult> scanResults = wifiManager.getScanResults();
            for (ScanResult result : scanResults) {
                if (result.BSSID == null || result.BSSID.length() == 0 || result.level >= 0) continue;
                String ssid = result.SSID == null || result.SSID.length() == 0 ? "숨김 네트워크" : result.SSID;
                samples.add(new SignalSample(result.BSSID, ssid, result.level, result.frequency, result.timestamp));
            }
        } catch (SecurityException ignored) {
        }
        if (samples.isEmpty()) addConnectedNetworkFallback(samples);
        Collections.sort(samples, Comparator.comparingInt((SignalSample sample) -> sample.rssi).reversed());

        if (samples.isEmpty()) {
            Toast.makeText(this, "주변 Wi-Fi 신호를 찾지 못했습니다. 위치 서비스를 확인해 주세요.", Toast.LENGTH_LONG).show();
            return;
        }
        if (action == ACTION_MEASURE) {
            heatmapView.addMeasurement(x, y, samples);
            String suffix = cached ? " (최근 스캔 결과)" : "";
            Toast.makeText(this, "측정 완료: AP " + samples.size() + "개" + suffix, Toast.LENGTH_SHORT).show();
        } else if (action == ACTION_ADD_DEVICE) {
            showDeviceDetailsDialog(x, y, samples);
        }
    }

    @SuppressWarnings("deprecation")
    private void addConnectedNetworkFallback(List<SignalSample> samples) {
        WifiInfo info = wifiManager.getConnectionInfo();
        String bssid = info.getBSSID();
        int rssi = info.getRssi();
        if (bssid == null || "02:00:00:00:00:00".equals(bssid) || rssi <= -127 || rssi >= 0) return;
        String ssid = info.getSSID();
        if (ssid == null || WifiManager.UNKNOWN_SSID.equals(ssid)) ssid = "연결된 Wi-Fi";
        ssid = ssid.replace("\"", "");
        samples.add(new SignalSample(bssid, ssid, rssi, info.getFrequency(), SystemClock.elapsedRealtimeNanos() / 1000L));
    }

    private void showDeviceDetailsDialog(final float x, final float y, final List<SignalSample> samples) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);

        EditText nameInput = new EditText(this);
        nameInput.setHint("장비 이름");
        nameInput.setText("공유기 " + (heatmapView.deviceCount() + 1));
        content.addView(nameInput, new LinearLayout.LayoutParams(-1, -2));

        RadioGroup typeGroup = new RadioGroup(this);
        typeGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton router = new RadioButton(this);
        router.setId(View.generateViewId());
        router.setText("공유기");
        RadioButton extender = new RadioButton(this);
        extender.setId(View.generateViewId());
        extender.setText("증폭기");
        typeGroup.addView(router);
        typeGroup.addView(extender);
        typeGroup.check(router.getId());
        content.addView(typeGroup, new LinearLayout.LayoutParams(-1, -2));

        new AlertDialog.Builder(this)
                .setTitle("장비 정보")
                .setView(content)
                .setNegativeButton("취소", null)
                .setPositiveButton("다음", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.length() == 0) name = "네트워크 장비 " + (heatmapView.deviceCount() + 1);
                    int type = typeGroup.getCheckedRadioButtonId() == extender.getId()
                            ? NetworkDevice.TYPE_EXTENDER : NetworkDevice.TYPE_ROUTER;
                    showBssidSelectionDialog(x, y, name, type, samples);
                })
                .show();
    }

    private void showBssidSelectionDialog(float x, float y, String name, int type, List<SignalSample> samples) {
        int count = Math.min(samples.size(), 16);
        String[] items = new String[count];
        boolean[] checked = new boolean[count];
        for (int index = 0; index < count; index++) {
            SignalSample sample = samples.get(index);
            items[index] = sample.ssid + "  " + sample.bandLabel() + "  " + sample.rssi + " dBm\n" + sample.bssid;
        }
        checked[0] = true;

        new AlertDialog.Builder(this)
                .setTitle("이 장비의 BSSID 선택")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("취소", null)
                .setPositiveButton("장비 추가", (dialog, which) -> {
                    List<String> bssids = new ArrayList<>();
                    for (int index = 0; index < count; index++) {
                        if (checked[index]) bssids.add(samples.get(index).bssid);
                    }
                    if (bssids.isEmpty()) {
                        Toast.makeText(this, "하나 이상의 BSSID를 선택해 주세요.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    NetworkDevice device = new NetworkDevice(name, type, x, y, heatmapView.nextDeviceColor(), bssids);
                    heatmapView.addDevice(device);
                })
                .show();
    }

    private boolean hasWifiPermissions() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false;
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestWifiPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES}, WIFI_PERMISSION);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, WIFI_PERMISSION);
        }
    }
}
