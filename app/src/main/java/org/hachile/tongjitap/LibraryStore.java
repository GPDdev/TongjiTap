package org.hachile.tongjitap;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

final class LibraryStore {
    final SharedPreferences prefs;
    final SharedPreferences playback;

    LibraryStore(Context context) {
        prefs = context.getSharedPreferences("runmusic", Context.MODE_PRIVATE);
        // 调速和进度频繁保存，单独存放以避免反复重写整份曲库。
        playback = context.getSharedPreferences("playback", Context.MODE_PRIVATE);
    }

    List<Track> load() throws JSONException {
        List<Track> tracks = new ArrayList<>();
        JSONArray array = new JSONArray(prefs.getString("library", "[]"));
        for (int i = 0; i < array.length(); i++) tracks.add(Track.fromJson(array.getJSONObject(i)));
        return tracks;
    }

    void save(List<Track> tracks) throws JSONException {
        JSONArray array = new JSONArray();
        for (Track track : tracks) array.put(track.json());
        prefs.edit().putString("library", array.toString()).apply();
    }

    SpeedConfig settings() {
        SpeedConfig c = new SpeedConfig();
        c.min = prefs.getFloat("min", c.min);
        c.max = prefs.getFloat("max", c.max);
        c.sensitivity = prefs.getFloat("sensitivity", c.sensitivity);
        c.vertical = prefs.getBoolean("vertical", false);
        c.reverse = prefs.getBoolean("reverse", false);
        c.holdToDrag = prefs.getBoolean("hold", false);
        c.haptics = prefs.getBoolean("haptics", true);
        c.keepScreenOn = prefs.getBoolean("screen", true);
        c.preservePitch = prefs.getBoolean("pitch", true);
        c.doubleTapReset = prefs.getBoolean("doubleTap", true);
        try { c.validate(); } catch (IllegalArgumentException e) { return new SpeedConfig(); }
        return c;
    }

    void saveSettings(SpeedConfig c) {
        c.validate();
        prefs.edit().putFloat("min", c.min).putFloat("max", c.max).putFloat("sensitivity", c.sensitivity)
                .putBoolean("vertical", c.vertical).putBoolean("reverse", c.reverse)
                .putBoolean("hold", c.holdToDrag).putBoolean("haptics", c.haptics)
                .putBoolean("screen", c.keepScreenOn).putBoolean("pitch", c.preservePitch)
                .putBoolean("doubleTap", c.doubleTapReset).apply();
    }
}
