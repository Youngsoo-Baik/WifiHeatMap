package com.example.wifiheatmap;

final class SignalSample {
    final String bssid;
    final String ssid;
    final int rssi;
    final int frequency;
    final long timestampMicros;

    SignalSample(String bssid, String ssid, int rssi, int frequency, long timestampMicros) {
        this.bssid = bssid;
        this.ssid = ssid;
        this.rssi = rssi;
        this.frequency = frequency;
        this.timestampMicros = timestampMicros;
    }

    String bandLabel() {
        if (frequency >= 5925) return "6GHz";
        if (frequency >= 4900) return "5GHz";
        if (frequency >= 2400) return "2.4GHz";
        return frequency + "MHz";
    }
}
