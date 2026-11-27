package org.hachile.tongjitap;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class PreviewReleaseChecks extends Instrumentation {
    private String upgradeFrom;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        upgradeFrom = args == null ? null : args.getString("upgradeFrom");
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            JSONObject current = release("1.1.0");
            JSONObject wrongPackage = release("2.0.0");
            wrongPackage.getJSONArray("assets").getJSONObject(0).put("name", "tongjitap.apk");
            JSONObject wrongUrl = release("4.0.0");
            wrongUrl.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://example.org/file.apk");
            JSONObject empty = release("5.0.0");
            empty.getJSONArray("assets").getJSONObject(0).put("size", 0);
            JSONObject uploading = release("6.0.0");
            uploading.getJSONArray("assets").getJSONObject(0).put("state", "new");
            JSONArray releases = new JSONArray().put(current).put(wrongPackage).put(wrongUrl).put(empty).put(uploading)
                    .put(release("3.0.0").put("draft", true)).put(release("1.2.0-beta.1"))
                    .put(release("9.0.0").put("tag_name","v9.0.0"));
            String latest = PreviewReleaseFeed.newestOnPage(releases, null);
            check("1.1.0".equals(latest), "Selected an incompatible release");
            check("1.10.0".equals(PreviewReleaseFeed.newestOnPage(new JSONArray().put(release("1.10.0")).put(release("1.2.9")), latest)), "Page ordering");
            check(latest.equals(PreviewReleaseFeed.newestOnPage(new JSONArray().put(release("1.0.9")), latest)), "Older page won");
            check(PreviewReleaseFeed.compare(latest, "1.0.1").state == PreviewReleaseFeed.State.AVAILABLE, "Upgrade");
            check(PreviewReleaseFeed.compare(latest, "1.0.2-preview").state == PreviewReleaseFeed.State.AVAILABLE, "Previous preview");
            check(PreviewReleaseFeed.compare(latest, "1.1.0").state == PreviewReleaseFeed.State.CURRENT, "Same version");
            check(PreviewReleaseFeed.compare(latest, "1.2.0").state == PreviewReleaseFeed.State.CURRENT, "Downgrade");
            check(PreviewReleaseFeed.compare(null, "1.1.0").state == PreviewReleaseFeed.State.NO_RELEASE, "Unpublished");
            check(PreviewReleaseFeed.compare(latest,"1.0.1").downloadUrl().equals(current.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")), "Link mismatch");
            check(Tabs.ORDER.equals(Arrays.asList(Tabs.LOOP,Tabs.RELAY,Tabs.ROAM,Tabs.STRIDE,Tabs.MORE)), "Tab order");
            check(Tabs.restore(1) == Tabs.STRIDE && Tabs.restore(3) == Tabs.MORE && Tabs.restore(99) == Tabs.LOOP, "Legacy routing");
            String summary = "Preview release channel, update direction, download and stable tab IDs passed.";
            if (upgradeFrom != null) summary += "\n" + checkPublishedUpdate(upgradeFrom);
            result.putString("stream", summary);
            finish(Activity.RESULT_OK, result);
        } catch (Exception | AssertionError error) {
            result.putString("stream", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private String checkPublishedUpdate(String olderVersion) throws Exception {
        PreviewReleaseFeed feed = new PreviewReleaseFeed();
        try {
            CompletableFuture<PreviewReleaseFeed.Result> response = new CompletableFuture<>();
            feed.check(response::complete);
            PreviewReleaseFeed.Result installed = response.get(45, TimeUnit.SECONDS);
            check(installed.state == PreviewReleaseFeed.State.CURRENT && BuildConfig.VERSION_NAME.equals(installed.version),
                    "Expected the installed version on GitHub, got " + installed.state + " " + installed.version);
            PreviewReleaseFeed.Result older = PreviewReleaseFeed.compare(installed.version, olderVersion);
            check(older.state == PreviewReleaseFeed.State.AVAILABLE, "Expected an update from " + olderVersion);
            return "GitHub: " + olderVersion + " -> AVAILABLE, " + installed.version + " -> CURRENT, " + older.downloadUrl();
        } finally {
            feed.close();
        }
    }

    private static JSONObject release(String version) throws Exception {
        String tag = "ui-v" + version;
        JSONObject asset = new JSONObject().put("name", "tongjitap-preview.apk").put("state", "uploaded").put("size", 123)
                .put("browser_download_url", "https://github.com/GPDdev/TongjiTap/releases/download/" + tag + "/tongjitap-preview.apk");
        return new JSONObject().put("draft", false).put("prerelease", true).put("tag_name", tag).put("assets", new JSONArray().put(asset));
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
