package com.example.wifiheatmap;

import java.util.ArrayList;
import java.util.List;

final class NetworkDevice {
    static final int TYPE_ROUTER = 0;
    static final int TYPE_EXTENDER = 1;

    final String name;
    final int type;
    final float x;
    final float y;
    final int color;
    final List<String> bssids;

    NetworkDevice(String name, int type, float x, float y, int color, List<String> bssids) {
        this.name = name;
        this.type = type;
        this.x = x;
        this.y = y;
        this.color = color;
        this.bssids = new ArrayList<>(bssids);
    }

    boolean matches(String bssid) {
        if (bssid == null) return false;
        for (String candidate : bssids) {
            if (candidate.equalsIgnoreCase(bssid)) return true;
        }
        return false;
    }

    String typeLabel() {
        return type == TYPE_ROUTER ? "공유기" : "증폭기";
    }
}
