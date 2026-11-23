package org.hachile.tongjitap;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;

final class RelayConfig {
    int width, height, rotation, wifiX = -1, wifiY = -1;
    Rect map, distance;
    int jumpPercent = 15;
    RelayConfig(Context c) {
        SharedPreferences p = c.getSharedPreferences("relay", Context.MODE_PRIVATE);
        width = p.getInt("width", 0); height = p.getInt("height", 0); rotation = p.getInt("rotation", 0);
        map = rect(p, "map"); distance = rect(p, "distance");
        wifiX = p.getInt("wifiX", -1); wifiY = p.getInt("wifiY", -1); jumpPercent = p.getInt("jump", 15);
    }
    private Rect rect(SharedPreferences p, String key) {
        return new Rect(p.getInt(key + "L", 0), p.getInt(key + "T", 0), p.getInt(key + "R", 0), p.getInt(key + "B", 0));
    }
    void prepare(ScreenInfo screen) {
        if (width != screen.width || height != screen.height || rotation != screen.rotation) {
            map.setEmpty(); distance.setEmpty(); wifiX = wifiY = -1;
            width = screen.width; height = screen.height; rotation = screen.rotation;
        }
    }
    void save(Context c, ScreenInfo screen) {
        prepare(screen);
        SharedPreferences.Editor e = c.getSharedPreferences("relay", Context.MODE_PRIVATE).edit();
        e.putInt("width", width).putInt("height", height).putInt("rotation", rotation)
            .putInt("wifiX", wifiX).putInt("wifiY", wifiY).putInt("jump", jumpPercent);
        put(e, "map", map); put(e, "distance", distance);
        if (!e.commit()) throw new IllegalStateException("无法保存配置");
    }
    private void put(SharedPreferences.Editor e, String k, Rect r) {
        e.putInt(k + "L", r.left).putInt(k + "T", r.top).putInt(k + "R", r.right).putInt(k + "B", r.bottom);
    }
    boolean matches(ScreenInfo s) { return width == s.width && height == s.height && rotation == s.rotation; }
    boolean ready(ScreenInfo s) { return matches(s) && valid(map) && valid(distance) && wifiX >= 0 && wifiX < width && wifiY >= 0 && wifiY < height; }
    private boolean valid(Rect r) { return r.width() >= 12 && r.height() >= 12 && r.left >= 0 && r.top >= 0 && r.right <= width && r.bottom <= height; }
}
