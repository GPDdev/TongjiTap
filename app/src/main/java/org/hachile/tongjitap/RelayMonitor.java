package org.hachile.tongjitap;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.function.Consumer;

/** Screenshots and cropped OCR bitmaps never leave memory or enter the music network client. */
final class RelayMonitor {
    private final TapService service;
    private final RelayConfig config;
    private final Consumer<String> status;
    private final Runnable ended;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final RelayGate gate;
    private final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private WifiPanel wifi;
    private boolean stopped, expectedWifi = true, switching;
    private int failures, cycles, generation;
    private final Runnable timeout = () -> stop("屏幕读取超时，已停止");
    RelayMonitor(TapService service, RelayConfig config, Consumer<String> status, Runnable ended) {
        this.service = service; this.config = config; this.status = status; this.ended = ended;
        gate = new RelayGate(config.jumpPercent / 100.0);
    }
    void start() {
        status.accept("跃点将在 5 秒后开始，请切换到运动页面");
        handler.postDelayed(this::poll, 5000);
    }
    boolean running() { return !stopped; }
    private boolean environment() { return config.matches(ScreenInfo.read(service)) && service.screenUnlocked(); }
    private void poll() {
        if (stopped) return;
        if (!environment()) { stop("屏幕尺寸、方向或锁屏状态变化，已停止"); return; }
        if (service.relayPanelOverlaps(config)) { stop("请将悬浮面板移到地图和里程区域之外"); return; }
        if (!service.relayTargetVisible()) {
            gate.reset(); status.accept("等待微信运动页面，当前不执行切换");
            handler.postDelayed(this::poll, 1200); return;
        }
        if (service.getApplicationContext().getSystemService(android.net.wifi.WifiManager.class).isWifiEnabled() != expectedWifi) {
            stop("Wi-Fi 状态被外部更改，请重新开始"); return;
        }
        if (android.os.Build.VERSION.SDK_INT < 30) { stop("实时屏幕检测需要 Android 11 或更高版本"); return; }
        final int ticket = generation;
        handler.postDelayed(timeout, 8000);
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.getMainExecutor(), new AccessibilityService.TakeScreenshotCallback() {
                @Override public void onSuccess(AccessibilityService.ScreenshotResult result) {
                    Bitmap hardware = null, pixels = null;
                    try (HardwareBuffer buffer = result.getHardwareBuffer()) {
                        if (stopped || ticket != generation) return;
                        hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                        if (hardware == null || hardware.getWidth() != config.width || hardware.getHeight() != config.height)
                            throw new IllegalStateException("截图尺寸变化，请重新校准");
                        pixels = hardware.copy(Bitmap.Config.ARGB_8888, false);
                        if (pixels == null) throw new IllegalStateException("无法读取屏幕");
                        analyze(pixels, ticket);
                    } catch (RuntimeException | OutOfMemoryError e) { stop("无法读取屏幕，已停止"); }
                    finally { if (pixels != null) pixels.recycle(); if (hardware != null) hardware.recycle(); }
                }
                @Override public void onFailure(int code) { stop("截图失败，已停止；请检查辅助功能与页面权限"); }
            });
        } catch (RuntimeException e) { stop("截图权限不可用，已停止"); }
    }
    private void analyze(Bitmap bitmap, int ticket) {
        Bitmap croppedMap = Bitmap.createBitmap(bitmap, config.map.left, config.map.top, config.map.width(), config.map.height());
        int width = Math.min(600, croppedMap.getWidth()), height = Math.max(1, croppedMap.getHeight() * width / croppedMap.getWidth());
        Bitmap reduced = Bitmap.createScaledBitmap(croppedMap, width, height, false);
        int[] colors = new int[width * height]; reduced.getPixels(colors, 0, width, 0, 0, width, height);
        double[] dot = GreenDot.find(colors, width, height);
        if (reduced != croppedMap) reduced.recycle(); if (croppedMap != bitmap) croppedMap.recycle();
        if (dot == null) { complete(null, null); return; }
        Bitmap text = Bitmap.createBitmap(bitmap, config.distance.left, config.distance.top, config.distance.width(), config.distance.height());
        // createBitmap may return the source if the ROI is the whole image; keep OCR ownership separate.
        int textWidth=Math.min(640,text.getWidth()), textHeight=Math.max(1,text.getHeight()*textWidth/text.getWidth());
        Bitmap ocr = Bitmap.createScaledBitmap(text,textWidth,textHeight,true);
        if(ocr==text)ocr=text.copy(Bitmap.Config.ARGB_8888,false);
        if (text != bitmap) text.recycle();
        if (ocr == null) { complete(null, null); return; }
        final Bitmap input=ocr;
        recognizer.process(InputImage.fromBitmap(input, 0))
            .addOnSuccessListener(result -> { if (!stopped && ticket == generation) complete(dot, RelayGate.parseMeters(result.getText())); })
            .addOnFailureListener(error -> { if (!stopped && ticket == generation) complete(null, null); })
            .addOnCompleteListener(task -> input.recycle());
    }
    private void complete(double[] dot, Integer meters) {
        handler.removeCallbacks(timeout);
        if (stopped) return;
        if (!environment() || !service.relayTargetVisible()) { gate.reset(); handler.postDelayed(this::poll, 1000); return; }
        if (dot == null || meters == null) {
            gate.reset(); failures++;
            if (failures >= 15) { stop("连续未识别到绿点或里程，请重新校准区域"); return; }
            status.accept("等待清晰的绿点和里程，不执行切换"); handler.postDelayed(this::poll, 1000); return;
        }
        failures = 0;
        status.accept((expectedWifi ? "监测中：Wi-Fi 开启" : "监测中：Wi-Fi 关闭")
            + "\n" + String.format(java.util.Locale.ROOT, "%.3f km", meters / 1000.0) + "  /  " + cycles);
        boolean confirmed = gate.sample(new RelayGate.Frame(dot[0], dot[1], meters, SystemClock.uptimeMillis()));
        if (BuildConfig.DEBUG) android.util.Log.d("TongjiTapRelay", "frame x="+dot[0]+" y="+dot[1]+" m="+meters+" confirmed="+confirmed);
        if (confirmed) {
            switching = true; boolean desired = !expectedWifi; wifi = new WifiPanel(service);
            status.accept(desired ? "双条件确认，正在开启 Wi-Fi" : "双条件确认，正在关闭 Wi-Fi");
            service.hideRelayPanel(true);
            wifi.change(desired, config, success -> {
                service.hideRelayPanel(false); switching = false;
                if (stopped) return;
                if (!success) { stop("未确认 Wi-Fi 切换成功，请校准快捷开关后重试"); return; }
                expectedWifi = desired; cycles++;
                // Preserve the last confirmed coordinates while the shade obscures the run screen.
                // Otherwise an immediate return jump would be lost before the first new screenshot.
                gate.seed(new RelayGate.Frame(dot[0], dot[1], meters, SystemClock.uptimeMillis()));
                handler.postDelayed(this::poll, 900);
            });
        } else handler.postDelayed(this::poll, 1000);
    }
    void checkEnvironment() { if (!stopped && !environment()) stop("屏幕状态变化，已停止"); }
    void stop(String message) {
        if (stopped) return;
        stopped = true; generation++; handler.removeCallbacksAndMessages(null);
        if (wifi != null) wifi.cancel();
        if (switching) service.hideRelayPanel(false);
        recognizer.close(); status.accept(message); ended.run();
    }
}
