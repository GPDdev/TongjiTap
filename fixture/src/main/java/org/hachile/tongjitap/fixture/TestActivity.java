package org.hachile.tongjitap.fixture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Separate APK: records actual MotionEvents received from the clicker's gestures. */
public final class TestActivity extends Activity {
    private FrameLayout page, blocker;
    private boolean stayWhite;
    private android.window.OnBackInvokedCallback backCallback;
    @Override public void onCreate(Bundle state) { super.onCreate(state); build(); }
    @Override public void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); build(); }
    private void build() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback); backCallback = null;
        }
        blocker = null;
        String action = getIntent().getAction() == null ? "" : getIntent().getAction();
        if ("org.hachile.tongjitap.fixture.RESET".equals(action) || action.contains(".COLOR_")) {
            try (FileOutputStream out = openFileOutput("events.jsonl", MODE_PRIVATE)) { out.flush(); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
        page = new FrameLayout(this); page.setBackgroundColor(Color.rgb(245, 247, 251)); setContentView(page);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        TextView title = new TextView(this); title.setText("独立触摸测试页面\nA 测试短按，B 测试长按\n记录真实触摸事件，不连接第三方应用");
        title.setTextSize(18); title.setTextColor(Color.rgb(21, 32, 53)); title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams heading = new FrameLayout.LayoutParams(-1, 240, Gravity.TOP); heading.topMargin = 80;
        page.addView(title, heading);
        addButton(page, "A", 560); addButton(page, "B", 220);
        if (action.contains(".COLOR_")) {
            stayWhite = action.endsWith("STUCK");
            blocker = new FrameLayout(this); blocker.setBackgroundColor(Color.WHITE); blocker.setClickable(true);
            // Material buttons have elevation: the test overlay must cover their touch surfaces too.
            blocker.setElevation(24 * getResources().getDisplayMetrics().density);
            page.addView(blocker, new FrameLayout.LayoutParams(-1, -1));
            Button recover = new Button(this); recover.setText("恢复页面");
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(420, 160, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            params.bottomMargin = 220; blocker.addView(recover, params); recover.setOnClickListener(v -> recover("tap"));
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                backCallback = () -> recover("back");
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
            }
            if (action.endsWith("SECURE")) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
    private void recover(String action) {
        record("RECOVERY", action, SystemClock.uptimeMillis());
        if (!stayWhite && blocker != null) { page.removeView(blocker); blocker = null; }
    }
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { if (blocker != null) recover("back"); else super.onBackPressed(); }
    private void addButton(FrameLayout page, String name, int bottom) {
        Button button = new Button(this); button.setText("目标 " + name); button.setTextSize(22);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(420, 220, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = bottom; page.addView(button, params);
        button.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) record(name, "down", event.getEventTime());
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP) record(name, "up", event.getEventTime());
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) record(name, "cancel", event.getEventTime());
            return false;
        });
        button.setOnClickListener(v -> record(name, "click", SystemClock.uptimeMillis()));
        button.setOnLongClickListener(v -> { record(name, "long", SystemClock.uptimeMillis()); return true; });
    }
    private void record(String button, String action, long time) {
        try (FileOutputStream out = openFileOutput("events.jsonl", MODE_APPEND)) {
            String record = new JSONObject().put("button", button).put("action", action).put("time", time).toString() + "\n";
            out.write(record.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
