package org.hachile.lighttap;

import android.app.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Run only on disposable emulators: changes their accessibility, rotation and screen settings. */
public final class DeviceChecks extends Instrumentation {
    private UiAutomation automation;
    private Context target;
    private Store store;
    private MainActivity activity;
    private int passed;
    private String stage = "setup";

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            target = getTargetContext(); store = new Store(target);
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS; automation.setServiceInfo(info);
            shell("settings put system accelerometer_rotation 0"); shell("settings put system user_rotation 0");
            shell("input keyevent 224"); shell("wm dismiss-keyguard");

            stage = "Explicit consent"; launchMain();
            AccessibilityNodeInfo agree = node("同意并进入"); require(agree != null && !agree.isEnabled(), "Consent must start disabled");
            for (int i = 0; i < 18 && node("我已阅读并理解功能、权限和风险说明") == null; i++) {
                Rect screen = bounds(); swipe(screen.centerX(), screen.bottom - 380, screen.centerX(), screen.top + 350);
            }
            click("我已阅读并理解功能、权限和风险说明"); click("同意并进入");
            await(store::accepted, 3000); screenshot("01-home"); pass();

            stage = "Persistence and corrupted input";
            ScreenInfo screen = ScreenInfo.read(target); Store.Draft d = new Store.Draft(screen);
            d.steps.add(new ClickStep(12, 34, 50, 500)); d.loops = 7; store.save(d);
            Store.Draft loaded = store.load(screen); require(loaded.loops == 7 && loaded.steps.get(0).y == 34, "Config round trip");
            target.getSharedPreferences("lighttap", 0).edit().putString("draft", "{broken").commit();
            boolean rejected = false;
            try { store.load(screen); } catch (IllegalStateException expected) { rejected = true; }
            require(rejected, "Corrupt JSON must be rejected");
            require("{broken".equals(target.getSharedPreferences("lighttap", 0).getString("draft", "")), "Corrupt data must not be overwritten silently");
            store.save(new Store.Draft(screen)); pass();

            stage = "Manual editor validation and persistence";
            runOnMainSync(() -> { activity.recreate(); }); SystemClock.sleep(800); launchMain();
            scrollTo("＋ 手动添加步骤"); click("＋ 手动添加步骤"); setText("X 坐标（屏幕像素）", "100"); setText("Y 坐标（屏幕像素）", "900");
            setText("按住时长（1～10000 毫秒）", "0"); click("保存");
            require(store.load(screen).steps.isEmpty(), "Zero duration must not save");
            setText("按住时长（1～10000 毫秒）", "50"); click("保存");
            require(store.load(screen).steps.size() == 1, "Valid step must save");
            click("设置循环次数"); setText("整组循环次数（1～100000）", "0"); click("保存");
            require(store.load(screen).loops == 10, "Zero loops must not save");
            setText("整组循环次数（1～100000）", "2"); click("保存");
            require(store.load(screen).loops == 2, "Loops must persist"); screenshot("02-editor"); pass();

            stage = "Service connection and screen picking";
            store.save(new Store.Draft(screen)); launchMain();
            shell("settings put secure enabled_accessibility_services org.hachile.lighttap/.TapService");
            shell("settings put secure accessibility_enabled 1"); await(() -> TapService.alive() != null, 8000);
            click("打开悬浮控制面板"); click("了解并打开面板");
            fixture(true); Rect a = requiredNode("目标 A").bounds, b = requiredNode("目标 B").bounds;
            click("取点"); screenshot("03-picker"); tap(a.centerX(), a.centerY());
            await(() -> store.load(ScreenInfo.read(target)).steps.size() == 1, 3000);
            require(events().isEmpty(), "Picker must not click underlying app");
            click("取点"); tap(b.centerX(), b.centerY());
            await(() -> store.load(ScreenInfo.read(target)).steps.size() == 2, 3000);
            d = store.load(screen); require(d.steps.get(0).x == a.centerX() && d.steps.get(1).y == b.centerY(), "Raw screen coordinates");
            d.loops = 2; d.steps.set(0, new ClickStep(a.centerX(), a.centerY(), 50, 300));
            d.steps.set(1, new ClickStep(b.centerX(), b.centerY(), 700, 350)); store.save(d);
            screenshot("04-panel"); pass();

            stage = "Actual cross-app sequence, timing and long press";
            click("开始"); SystemClock.sleep(750); require(events().isEmpty(), "Countdown must precede clicks");
            await(() -> !TapService.alive().isRunning(), 16000);
            List<JSONObject> records = events(); ArrayList<String> downs = new ArrayList<>();
            long lastUp = -1; String lastButton = ""; int shortClicks = 0, longClicks = 0;
            for (JSONObject e : records) {
                String action = e.getString("action"), button = e.getString("button"); long time = e.getLong("time");
                if ("down".equals(action)) {
                    downs.add(button);
                    if (lastUp >= 0) require(time - lastUp >= (lastButton.equals("A") ? 260 : 310), "Wait must follow actual release");
                }
                if ("up".equals(action)) { lastUp = time; lastButton = button; }
                if ("click".equals(action) && "A".equals(button)) shortClicks++;
                if ("long".equals(action) && "B".equals(button)) longClicks++;
                require(!"cancel".equals(action), "No cancelled fixture gesture");
            }
            require(downs.equals(Arrays.asList("A", "B", "A", "B")), "Whole sequence order: " + downs);
            require(shortClicks == 2 && longClicks == 2, "Expected two short clicks and two long presses: " + records);
            require(TapService.alive().status().contains("已完成"), "Successful completion state");
            saveText("actual-events.jsonl", shell("run-as org.hachile.lighttap.fixture cat files/events.jsonl")); pass();

            stage = "Stop countdown, no delayed clicks";
            fixture(true); click("开始"); SystemClock.sleep(400); click("停止"); SystemClock.sleep(3300);
            require(events().isEmpty() && !TapService.alive().isRunning(), "Countdown cancelled"); pass();

            stage = "Stop during inter-step wait";
            d = store.load(screen); d.loops = 100; d.steps.set(0, new ClickStep(a.centerX(), a.centerY(), 50, 2000)); store.save(d);
            click("开始"); await(() -> {
                try { return events().stream().anyMatch(e -> "up".equals(e.optString("action"))); }
                catch (Exception e) { return false; }
            }, 7000);
            click("停止"); int count = events().size(); SystemClock.sleep(2400);
            require(events().size() == count && !TapService.alive().isRunning(), "No next step after stopping"); pass();

            stage = "Panel overlap protection";
            Rect handle = requiredNode("轻点助手　移动面板").bounds;
            swipe(handle.centerX(), handle.centerY(), a.centerX(), a.centerY()); click("开始");
            require(!TapService.alive().isRunning(), "Panel overlap must prevent running");
            require(TapService.alive().status().contains("遮挡"), "Panel overlap error message");
            Rect shifted = requiredNode("轻点助手　移动面板").bounds;
            swipe(shifted.centerX(), shifted.centerY(), handle.centerX(), handle.centerY()); pass();

            stage = "Orientation stop and stale-coordinate rejection";
            click("开始"); await(() -> TapService.alive().isRunning(), 2000);
            shell("settings put system user_rotation 1"); await(() -> !TapService.alive().isRunning(), 6000);
            click("开始"); require(!TapService.alive().isRunning(), "Old screen coordinates must be rejected");
            shell("settings put system user_rotation 0"); SystemClock.sleep(1200); pass();

            stage = "Screen off stops countdown";
            click("开始"); await(() -> TapService.alive().isRunning(), 2000);
            shell("input keyevent 223"); await(() -> !TapService.alive().isRunning(), 3000);
            shell("input keyevent 224"); shell("wm dismiss-keyguard"); SystemClock.sleep(1000); pass();

            stage = "Permission revoke and restart safety";
            fixture(true); click("开始"); shell("settings put secure enabled_accessibility_services ''");
            await(() -> TapService.alive() == null, 5000); SystemClock.sleep(3300);
            require(events().isEmpty(), "No gesture after permission revoked during countdown");
            shell("settings put secure enabled_accessibility_services org.hachile.lighttap/.TapService");
            await(() -> TapService.alive() != null, 6000);
            require(!TapService.alive().isRunning(), "Reconnected service must stay idle");
            launchMain(); screenshot("05-after-checks"); pass();

            result.putString("stream", "\nPASS: " + passed + " device scenarios on API " + Build.VERSION.SDK_INT + "\n");
            result.putInt("passed", passed); finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            try { screenshot("failure"); } catch (Throwable ignored) { }
            StringWriter trace = new StringWriter(); e.printStackTrace(new PrintWriter(trace));
            result.putString("stream", "\nFAIL: " + stage + "\n" + trace); finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void pass() {
        passed++; Bundle b = new Bundle(); b.putString("stream", "PASS " + passed + ": " + stage + "\n"); sendStatus(0, b);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private void await(BooleanSupplier test, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < deadline) { if (test.getAsBoolean()) return; SystemClock.sleep(100); }
        throw new AssertionError("Timed out: " + stage);
    }
    private void launchMain() {
        Intent intent = new Intent(target, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity = (MainActivity) startActivitySync(intent); waitForIdleSync(); SystemClock.sleep(350);
    }
    private void fixture(boolean reset) {
        Intent intent = new Intent().setComponent(new ComponentName("org.hachile.lighttap.fixture", "org.hachile.lighttap.fixture.TestActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (reset) intent.setAction("org.hachile.lighttap.fixture.RESET");
        target.startActivity(intent); SystemClock.sleep(600);
    }
    private static final class Found {
        final AccessibilityNodeInfo node; final Rect bounds = new Rect();
        Found(AccessibilityNodeInfo node) { this.node = node; node.getBoundsInScreen(bounds); }
    }
    private AccessibilityNodeInfo node(String text) {
        return node(text, false);
    }
    private AccessibilityNodeInfo node(String text, boolean editable) {
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            AccessibilityNodeInfo found = search(window.getRoot(), text, editable);
            if (found != null) return found;
        }
        return null;
    }
    private AccessibilityNodeInfo search(AccessibilityNodeInfo root, String text, boolean editable) {
        if (root == null) return null;
        if (root.isVisibleToUser() && (!editable || root.isEditable()) && (text.contentEquals(root.getText() == null ? "" : root.getText())
            || text.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription()))) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo result = search(root.getChild(i), text, editable); if (result != null) return result;
        }
        return null;
    }
    private Found requiredNode(String text) { await(() -> node(text) != null, 5000); return new Found(node(text)); }
    private void scrollTo(String text) {
        for (int i = 0; i < 6 && node(text) == null; i++) {
            Rect s = bounds(); swipe(s.centerX(), s.bottom - 300, s.centerX(), s.top + 350);
        }
    }
    private void click(String text) {
        Found f = requiredNode(text); require(f.node.isEnabled(), "Disabled control: " + text);
        require(f.node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Click action failed: " + text); SystemClock.sleep(200);
    }
    private void setText(String label, String text) {
        Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        await(() -> node(label, true) != null, 5000);
        require(node(label, true).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "Set text: " + label); SystemClock.sleep(100);
    }
    private Rect bounds() { ScreenInfo s = ScreenInfo.read(target); return new Rect(0, 0, s.width, s.height); }
    private void touch(int action, long down, int x, int y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN); automation.injectInputEvent(event, true); event.recycle();
    }
    private void tap(int x, int y) { long down = SystemClock.uptimeMillis(); touch(MotionEvent.ACTION_DOWN, down, x, y); SystemClock.sleep(60); touch(MotionEvent.ACTION_UP, down, x, y); SystemClock.sleep(300); }
    private void swipe(int x1, int y1, int x2, int y2) {
        long down = SystemClock.uptimeMillis(); touch(MotionEvent.ACTION_DOWN, down, x1, y1);
        for (int i = 1; i <= 12; i++) { SystemClock.sleep(30); touch(MotionEvent.ACTION_MOVE, down, x1 + (x2-x1)*i/12, y1 + (y2-y1)*i/12); }
        touch(MotionEvent.ACTION_UP, down, x2, y2); SystemClock.sleep(400);
    }
    private String shell(String command) throws IOException {
        ParcelFileDescriptor pipe = automation.executeShellCommand(command);
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pipe); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
            return out.toString("UTF-8");
        }
    }
    private List<JSONObject> events() throws Exception {
        String raw = shell("run-as org.hachile.lighttap.fixture cat files/events.jsonl");
        ArrayList<JSONObject> events = new ArrayList<>();
        for (String line : raw.split("\n")) if (!line.trim().isEmpty()) events.add(new JSONObject(line));
        return events;
    }
    private void saveText(String name, String text) throws IOException {
        File folder = new File(target.getFilesDir(), "checks"); if (!folder.exists() && !folder.mkdirs()) throw new IOException("mkdir");
        try (FileOutputStream out = new FileOutputStream(new File(folder, name))) { out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    }
    private void screenshot(String name) throws IOException {
        Bitmap bitmap = automation.takeScreenshot(); if (bitmap == null) throw new IOException("No screenshot");
        File folder = new File(target.getFilesDir(), "checks"); if (!folder.exists() && !folder.mkdirs()) throw new IOException("mkdir");
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
        bitmap.recycle();
    }
}
