package org.hachile.tongjitap;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class UpdateChecker {
    static final String DOWNLOAD = "https://github.com/GPDdev/TongjiTap/releases/latest/download/tongjitap.apk";
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean running, checked;
    private volatile boolean closed;
    private volatile int generation;
    void check(Consumer<String> result) {
        if (closed || running || checked) return;
        running = true; int token = ++generation;
        io.execute(() -> {
            if (closed || token != generation) return;
            String latest = null;
            try { latest = available(fetch()); } catch (Exception ignored) { /* Offline, rate limits and bad responses remain quiet. */ }
            String value = latest;
            main.post(() -> {
                if (closed || token != generation) return;
                running = false; checked = true; result.accept(value);
            });
        });
    }
    void reset() { generation++; running = false; checked = false; }
    void close() { closed = true; generation++; io.shutdownNow(); main.removeCallbacksAndMessages(null); }
    static String available(String body) throws org.json.JSONException {
        JSONObject release = new JSONObject(body);
        if (release.optBoolean("draft", true) || release.optBoolean("prerelease", true)) return null;
        String tag = release.optString("tag_name", "");
        if (!ReleaseVersion.newer(tag, BuildConfig.VERSION_NAME)) return null;
        org.json.JSONArray assets = release.optJSONArray("assets");
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if ("tongjitap.apk".equals(asset.optString("name")) && "uploaded".equals(asset.optString("state"))
                && asset.optLong("size", 0) > 0) return tag;
        }
        return null;
    }
    private static String fetch() throws java.io.IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://api.github.com/repos/GPDdev/TongjiTap/releases/latest").openConnection();
        connection.setConnectTimeout(6000); connection.setReadTimeout(6000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "TongjiTap/" + BuildConfig.VERSION_NAME);
        try {
            if (connection.getResponseCode() != 200) throw new java.io.IOException("Release unavailable");
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int read;
                while ((read = in.read(buffer)) != -1) {
                    if (out.size() + read > 512 * 1024) throw new java.io.IOException("Response too large");
                    out.write(buffer, 0, read);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally { connection.disconnect(); }
    }
}
