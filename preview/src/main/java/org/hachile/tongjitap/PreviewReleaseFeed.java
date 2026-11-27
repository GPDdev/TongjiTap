package org.hachile.tongjitap;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** The independent APK has its own application ID, tag prefix and asset name. */
final class PreviewReleaseFeed {
    static final String ASSET = "tongjitap-preview.apk";
    static final String PREFIX = "ui-v";
    private static final String INDEX = "https://api.github.com/repos/GPDdev/TongjiTap/releases?per_page=30&page=";
    private static final String DOWNLOADS = "https://github.com/GPDdev/TongjiTap/releases/download/";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean running, closed;

    enum State { AVAILABLE, CURRENT, NO_RELEASE, FAILED }
    static final class Result {
        final State state;
        final String version;

        Result(State state, String version) { this.state = state; this.version = version; }

        String downloadUrl() { return DOWNLOADS + PREFIX + version + "/" + ASSET; }
    }

    static String newestOnPage(JSONArray page, String previous) {
        String newest = previous;
        for (int i = 0; i < page.length(); i++) {
            JSONObject release = page.optJSONObject(i);
            if (release == null || release.optBoolean("draft", true)) continue;
            String tag = release.optString("tag_name");
            if (!tag.startsWith(PREFIX)) continue;
            String version = tag.substring(PREFIX.length());
            if (!ReleaseVersion.isVersionNumber(version) || (newest != null && !ReleaseVersion.newer(version, newest))) continue;
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) continue;
            String expected = DOWNLOADS + tag + "/" + ASSET;
            for (int j = 0; j < assets.length(); j++) {
                JSONObject asset = assets.optJSONObject(j);
                if (asset != null && ASSET.equals(asset.optString("name")) && "uploaded".equals(asset.optString("state"))
                        && asset.optLong("size") > 0 && expected.equals(asset.optString("browser_download_url"))) {
                    newest = version;
                    break;
                }
            }
        }
        return newest;
    }

    static Result compare(String published, String installed) {
        if (published == null) return new Result(State.NO_RELEASE, null);
        String number = installed != null && installed.endsWith("-preview")
                ? installed.substring(0, installed.length() - "-preview".length()) : installed;
        if (!ReleaseVersion.isVersionNumber(number)) return new Result(State.FAILED, null);
        return new Result(ReleaseVersion.newer(published, number) ? State.AVAILABLE : State.CURRENT, published);
    }

    void check(Consumer<Result> done) {
        if (running || closed) return;
        running = true;
        io.execute(() -> {
            Result result;
            try { result = readLatest(); }
            catch (IOException | JSONException e) { result = new Result(State.FAILED, null); }
            Result value = result;
            main.post(() -> {
                running = false;
                if (!closed) done.accept(value);
            });
        });
    }

    private Result readLatest() throws IOException, JSONException {
        String newest = null;
        for (int page = 1; !Thread.currentThread().isInterrupted(); page++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(INDEX + page).openConnection();
            connection.setConnectTimeout(6500);
            connection.setReadTimeout(6500);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "TongjiTap-Preview/" + BuildConfig.VERSION_NAME);
            try {
                if (connection.getResponseCode() != 200) throw new IOException("Release listing unavailable");
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream data = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (data.size() + count > 2 * 1024 * 1024) throw new IOException("Release listing too large");
                        data.write(buffer, 0, count);
                    }
                    newest = newestOnPage(new JSONArray(data.toString(StandardCharsets.UTF_8.name())), newest);
                }
                String links = connection.getHeaderField("Link");
                if (links == null || !links.contains("rel=\"next\"")) return compare(newest, BuildConfig.VERSION_NAME);
            } finally { connection.disconnect(); }
        }
        throw new IOException("Update check cancelled");
    }

    void close() { closed = true; io.shutdownNow(); main.removeCallbacksAndMessages(null); }
}
