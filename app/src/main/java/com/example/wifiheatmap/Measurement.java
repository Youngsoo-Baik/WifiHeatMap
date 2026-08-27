package com.example.wifiheatmap;

import java.util.ArrayList;
import java.util.List;

final class Measurement {
    final float x;
    final float y;
    final int rssi;
    final List<SignalSample> samples;

    Measurement(float x, float y, int rssi) {
        this.x = x;
        this.y = y;
        this.rssi = rssi;
        this.samples = new ArrayList<>();
    }

    Measurement(float x, float y, List<SignalSample> samples) {
        this.x = x;
        this.y = y;
        this.samples = new ArrayList<>(samples);
        int strongest = -127;
        for (SignalSample sample : samples) {
            strongest = Math.max(strongest, sample.rssi);
        }
        this.rssi = strongest;
    }

    Integer strongestFor(NetworkDevice device) {
        Integer strongest = null;
        for (SignalSample sample : samples) {
            if (device.matches(sample.bssid) && (strongest == null || sample.rssi > strongest)) {
                strongest = sample.rssi;
            }
        }
        return strongest;
    }
}
