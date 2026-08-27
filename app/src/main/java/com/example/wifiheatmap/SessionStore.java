package com.example.wifiheatmap;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class SessionStore {
    private static final String PREFS_NAME = "wifi_heatmap_session";
    private static final String KEY_SESSION = "saved_session";

    static final class SessionData {
        final String floorPlanUri;
        final List<NetworkDevice> devices;
        final List<Measurement> measurements;
        final long savedAt;

        SessionData(String floorPlanUri, List<NetworkDevice> devices, List<Measurement> measurements, long savedAt) {
            this.floorPlanUri = floorPlanUri;
            this.devices = devices;
            this.measurements = measurements;
            this.savedAt = savedAt;
        }
    }

    private final SharedPreferences preferences;

    SessionStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    boolean hasSession() {
        return preferences.contains(KEY_SESSION);
    }

    boolean save(String floorPlanUri, List<NetworkDevice> devices, List<Measurement> measurements) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("savedAt", System.currentTimeMillis());
            if (floorPlanUri != null) root.put("floorPlanUri", floorPlanUri);

            JSONArray deviceArray = new JSONArray();
            for (NetworkDevice device : devices) {
                JSONObject item = new JSONObject();
                item.put("name", device.name);
                item.put("type", device.type);
                item.put("x", device.x);
                item.put("y", device.y);
                item.put("color", device.color);
                JSONArray bssidArray = new JSONArray();
                for (String bssid : device.bssids) bssidArray.put(bssid);
                item.put("bssids", bssidArray);
                deviceArray.put(item);
            }
            root.put("devices", deviceArray);

            JSONArray measurementArray = new JSONArray();
            for (Measurement measurement : measurements) {
                JSONObject item = new JSONObject();
                item.put("x", measurement.x);
                item.put("y", measurement.y);
                JSONArray sampleArray = new JSONArray();
                for (SignalSample sample : measurement.samples) {
                    JSONObject sampleItem = new JSONObject();
                    sampleItem.put("bssid", sample.bssid);
                    sampleItem.put("ssid", sample.ssid);
                    sampleItem.put("rssi", sample.rssi);
                    sampleItem.put("frequency", sample.frequency);
                    sampleItem.put("timestampMicros", sample.timestampMicros);
                    sampleArray.put(sampleItem);
                }
                item.put("samples", sampleArray);
                measurementArray.put(item);
            }
            root.put("measurements", measurementArray);
            return preferences.edit().putString(KEY_SESSION, root.toString()).commit();
        } catch (JSONException exception) {
            return false;
        }
    }

    SessionData load() {
        String encoded = preferences.getString(KEY_SESSION, null);
        if (encoded == null) return null;
        try {
            JSONObject root = new JSONObject(encoded);
            String floorPlanUri = root.has("floorPlanUri") ? root.optString("floorPlanUri", null) : null;
            List<NetworkDevice> devices = new ArrayList<>();
            JSONArray deviceArray = root.optJSONArray("devices");
            if (deviceArray != null) {
                for (int index = 0; index < deviceArray.length(); index++) {
                    JSONObject item = deviceArray.getJSONObject(index);
                    List<String> bssids = new ArrayList<>();
                    JSONArray bssidArray = item.optJSONArray("bssids");
                    if (bssidArray != null) {
                        for (int bssidIndex = 0; bssidIndex < bssidArray.length(); bssidIndex++) {
                            bssids.add(bssidArray.getString(bssidIndex));
                        }
                    }
                    devices.add(new NetworkDevice(
                            item.optString("name", "네트워크 장비"),
                            item.optInt("type", NetworkDevice.TYPE_ROUTER),
                            boundedCoordinate(item.optDouble("x", 0.5)),
                            boundedCoordinate(item.optDouble("y", 0.5)),
                            item.optInt("color", 0xFF2563EB),
                            bssids));
                }
            }

            List<Measurement> measurements = new ArrayList<>();
            JSONArray measurementArray = root.optJSONArray("measurements");
            if (measurementArray != null) {
                for (int index = 0; index < measurementArray.length(); index++) {
                    JSONObject item = measurementArray.getJSONObject(index);
                    List<SignalSample> samples = new ArrayList<>();
                    JSONArray sampleArray = item.optJSONArray("samples");
                    if (sampleArray != null) {
                        for (int sampleIndex = 0; sampleIndex < sampleArray.length(); sampleIndex++) {
                            JSONObject sample = sampleArray.getJSONObject(sampleIndex);
                            samples.add(new SignalSample(
                                    sample.optString("bssid", ""),
                                    sample.optString("ssid", "알 수 없는 Wi-Fi"),
                                    sample.optInt("rssi", -127),
                                    sample.optInt("frequency", 0),
                                    sample.optLong("timestampMicros", 0L)));
                        }
                    }
                    if (!samples.isEmpty()) {
                        measurements.add(new Measurement(
                                boundedCoordinate(item.optDouble("x", 0.5)),
                                boundedCoordinate(item.optDouble("y", 0.5)),
                                samples));
                    }
                }
            }
            return new SessionData(floorPlanUri, devices, measurements, root.optLong("savedAt", 0L));
        } catch (JSONException exception) {
            return null;
        }
    }

    private static float boundedCoordinate(double value) {
        return (float) Math.max(0.0, Math.min(1.0, value));
    }
}
