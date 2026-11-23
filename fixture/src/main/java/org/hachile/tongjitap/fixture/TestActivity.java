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
    private RelayView relayView;
    private android.window.OnBackInvokedCallback backCallback;
    @Override public void onCreate(Bundle state) { super.onCreate(state); build(); }
    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if ("org.hachile.tongjitap.fixture.RELAY_JUMP".equals(intent.getAction()) && relayView!=null) { relayView.phase=1; relayView.invalidate(); return; }
        build();
    }
    private void build() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback); backCallback = null;
        }
        blocker = null;
        String action = getIntent().getAction() == null ? "" : getIntent().getAction();
        if ("org.hachile.tongjitap.fixture.GRANT_SCREENSHOT".equals(action) && getIntent().getData() != null)
            grantUriPermission("org.hachile.tongjitap", getIntent().getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if ("org.hachile.tongjitap.fixture.RESET".equals(action) || action.contains(".COLOR_")) {
            try (FileOutputStream out = openFileOutput("events.jsonl", MODE_PRIVATE)) { out.flush(); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
        page = new FrameLayout(this); page.setBackgroundColor(Color.rgb(245, 247, 251)); setContentView(page);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        relayView=null;
        if (action.endsWith(".RELAY")) { relayView=new RelayView(); page.addView(relayView, new FrameLayout.LayoutParams(-1, -1)); return; }
        TextView title = new TextView(this); title.setText("独立触摸测试页面\nA 测试短按，B 测试长按\n记录真实触摸事件，不连接第三方应用");
        title.setTextSize(18); title.setTextColor(Color.rgb(21, 32, 53)); title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams heading = new FrameLayout.LayoutParams(-1, 240, Gravity.TOP); heading.topMargin = 80;
        page.addView(title, heading);
        addButton(page, "A", 560); addButton(page, "B", 220);
        if (action.contains(".COLOR_")) {
            // The restored screen must differ from white by more than the default tolerance (16).
            page.setBackgroundColor(Color.rgb(218, 231, 250));
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
    /** Deterministic map fixture, reacting to the actual emulator Wi-Fi state. */
    private final class RelayView extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(3);
        private int phase;
        RelayView() { super(TestActivity.this); setContentDescription("Relay fixture"); }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            boolean wifi = getApplicationContext().getSystemService(android.net.wifi.WifiManager.class).isWifiEnabled();
            if (phase == 1 && !wifi) phase = 2;
            if (phase == 2 && wifi) phase = 3;
            int[] offset = new int[2]; getLocationOnScreen(offset);
            int h = getWindowManager().getCurrentWindowMetrics().getBounds().height();
            canvas.save(); canvas.translate(-offset[0], -offset[1]); canvas.drawColor(Color.WHITE);
            paint.setColor(0xffefebe0); canvas.drawRect(20, 900, getWidth() - 20, h - 360, paint);
            float x = getWidth() * (phase % 2 == 0 ? .25f : .75f), y = (900 + h - 360) / 2f;
            paint.setColor(0xffcbe9c1); canvas.drawCircle(x, y, 38, paint);
            paint.setColor(Color.WHITE); canvas.drawCircle(x, y, 22, paint);
            paint.setColor(0xff09cb24); canvas.drawCircle(x, y, 18, paint);
            paint.setColor(Color.BLACK); paint.setTextSize(76);
            canvas.drawText(String.format(java.util.Locale.ROOT, "%.2f km", .10 + phase * .05), 60, h - 180, paint);
            canvas.restore(); postInvalidateDelayed(150);
        }
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
