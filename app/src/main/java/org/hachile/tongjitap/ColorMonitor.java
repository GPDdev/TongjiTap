package org.hachile.tongjitap;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** API 30+. Screenshots are sampled in memory and immediately released. */
final class ColorMonitor {
    private final TapService service;
    private final SequenceRunner runner;
    private final ColorRule rule;
    private final ClickPlan plan;
    private final BooleanSupplier environment;
    private final Consumer<String> status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ColorRule.Detector detector;
    private long generation, requestId;
    private boolean stopped;

    ColorMonitor(TapService service, SequenceRunner runner, ColorRule rule, ClickPlan plan,
                 BooleanSupplier environment, Consumer<String> status) {
        this.service = service; this.runner = runner; this.rule = rule; this.plan = plan;
        this.environment = environment; this.status = status; detector = new ColorRule.Detector(rule);
    }

    void start() { poll(); }
    void stop() { stopped = true; generation++; requestId++; handler.removeCallbacksAndMessages(null); }
    private boolean valid(long token) { return !stopped && token == generation && runner.isRunning(); }
    private void fail(String message) { if (!stopped) runner.stop(message); }

    private void poll() {
        if (stopped || !runner.isRunning()) return;
        if (runner.state() == SequenceRunner.State.COUNTDOWN) { handler.postDelayed(this::poll, 500); return; }
        sample(color -> {
            if (detector.sample(color)) {
                if (!runner.interrupt(this::recover)) fail("无法暂停点击步骤，已停止");
            } else handler.postDelayed(this::poll, rule.intervalMs);
        }, 0);
    }

    private void recover(SequenceRunner.Result done) {
        // Recheck after the current gesture completes; its UI may have changed.
        handler.postDelayed(() -> sample(color -> {
            if (!rule.matches(color)) { waitUntilClear(done, SystemClock.uptimeMillis(), 1); return; }
            status.accept(rule.back ? "颜色命中，执行一次系统返回" : "颜色命中，点击恢复位置");
            long token = generation;
            SequenceRunner.Result actionDone = success -> {
                if (!valid(token)) return;
                if (!success) { fail("恢复动作未成功，已停止"); return; }
                status.accept("已执行恢复动作，等待颜色变化");
                handler.postDelayed(() -> waitUntilClear(done, SystemClock.uptimeMillis(), 0), 1000);
            };
            if (rule.back) actionDone.complete(service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK));
            else if (!service.dispatchClick(new ClickStep(rule.tapX, rule.tapY, 50, 0), actionDone))
                actionDone.complete(false);
        }, 0), 500);
    }

    private void waitUntilClear(SequenceRunner.Result done, long started, int misses) {
        if (SystemClock.uptimeMillis() - started >= 12000) {
            fail("目标颜色持续存在，已停止；请检查页面和规则"); return;
        }
        sample(color -> {
            detector.sample(color);
            int count = rule.matches(color) ? 0 : misses + 1;
            if (count >= 2) {
                // Re-arm only after the recovery has been confirmed on two frames.
                detector.sample(color);
                done.complete(true);
                if (!stopped && runner.isRunning()) handler.postDelayed(this::poll, rule.intervalMs);
            } else handler.postDelayed(() -> waitUntilClear(done, started, count), 500);
        }, 0);
    }

    private void sample(Consumer<Integer> consume, int retry) {
        if (android.os.Build.VERSION.SDK_INT < 30) { fail("颜色检测需要 Android 11 或更高版本"); return; }
        long token = generation;
        if (!valid(token)) return;
        if (!environment.getAsBoolean()) { fail("检测点被遮挡或屏幕状态变化，已停止"); return; }
        long ticket = ++requestId;
        Runnable timeout = () -> {
            if (valid(token) && ticket == requestId) { requestId++; fail("屏幕颜色读取超时，已停止"); }
        };
        handler.postDelayed(timeout, 5000);
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.getMainExecutor(), new AccessibilityService.TakeScreenshotCallback() {
                @Override public void onSuccess(AccessibilityService.ScreenshotResult result) {
                    handler.removeCallbacks(timeout);
                    Bitmap hardware = null, pixels = null;
                    HardwareBuffer buffer = result.getHardwareBuffer();
                    Integer sampled = null;
                    String error = null;
                    try {
                        if (!valid(token) || ticket != requestId) return;
                        requestId++;
                        if (!environment.getAsBoolean()) { error = "屏幕状态变化，已停止"; }
                        else {
                            hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                            if (hardware == null || hardware.getWidth() != plan.width || hardware.getHeight() != plan.height)
                                error = "截图尺寸变化，请重新取点";
                            else {
                                pixels = hardware.copy(Bitmap.Config.ARGB_8888, false);
                                if (pixels == null) error = "无法读取屏幕颜色，已停止";
                                else if (blackFrame(pixels)) error = "截图主体区域全黑，无法判断页面，已停止";
                                else sampled = pixels.getPixel(rule.x, rule.y);
                            }
                        }
                    } catch (RuntimeException e) { error = "无法读取屏幕颜色，已停止"; }
                    finally {
                        if (pixels != null) pixels.recycle();
                        if (hardware != null) hardware.recycle();
                        buffer.close();
                    }
                    if (!valid(token)) return;
                    if (error != null) fail(error);
                    else if (sampled != null) consume.accept(sampled);
                }
                @Override public void onFailure(int code) {
                    handler.removeCallbacks(timeout);
                    if (!valid(token) || ticket != requestId) return;
                    requestId++;
                    if (code == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT && retry < 2)
                        handler.postDelayed(() -> sample(consume, retry + 1), 500);
                    else fail("无法读取屏幕颜色（" + code + "），已停止；请检查权限或受保护页面");
                }
            });
        } catch (RuntimeException e) { handler.removeCallbacks(timeout); fail("截图权限不可用，已停止"); }
    }

    private boolean blackFrame(Bitmap pixels) {
        // Display captures may succeed while FLAG_SECURE content is masked black.
        // Ignore our controls and system bars. A fully black body is ambiguous, so stop.
        int checked = 0;
        for (int row = 1; row <= 9; row++) for (int col = 1; col <= 9; col++) {
            int x = pixels.getWidth() * col / 10, y = pixels.getHeight() * row / 10;
            if (service.obscured(x, y)) continue;
            checked++;
            if ((pixels.getPixel(x, y) & 0xFFFFFF) != 0) return false;
        }
        return checked >= 12;
    }
}
