package org.hachile.tongjitap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.io.InputStream;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Transient document grant. No screenshot copies, uploads or persisted image paths. */
final class ScreenshotImport {
    static final int COLOR = 0, STEP = 1, ACTION = 2, MAP = 3, DISTANCE = 4, WIFI = 5;
    private static final int REQUEST_IMAGE = 41;
    private final Activity activity;
    private final Store store;
    private final BooleanSupplier editable;
    private final Runnable saved;
    private ScreenInfo requestedScreen;
    private AlertDialog dialog;
    private int generation, mode, stepIndex = -1;
    private boolean closed;
    ScreenshotImport(Activity activity, Store store, BooleanSupplier editable, Runnable saved, Bundle state) {
        this.activity = activity; this.store = store; this.editable = editable; this.saved = saved;
        if (state != null && state.containsKey("screenshotWidth")) {
            requestedScreen = new ScreenInfo(state.getInt("screenshotWidth"), state.getInt("screenshotHeight"), state.getInt("screenshotRotation"));
            mode = state.getInt("screenshotMode", COLOR); stepIndex = state.getInt("screenshotStep", -1);
        }
    }
    void saveState(Bundle state) {
        if (requestedScreen == null) return;
        state.putInt("screenshotWidth", requestedScreen.width); state.putInt("screenshotHeight", requestedScreen.height);
        state.putInt("screenshotRotation", requestedScreen.rotation); state.putInt("screenshotMode", mode); state.putInt("screenshotStep", stepIndex);
    }
    void open() { open(COLOR); }
    void open(int target) { open(target, -1); }
    void open(int target, int index) {
        if (!editable.getAsBoolean()) return;
        if (target != STEP && android.os.Build.VERSION.SDK_INT < 30) { toast("实时屏幕检测需要 Android 11 或更高版本"); return; }
        if (!store.accepted()) { toast("请先阅读并同意使用说明"); return; }
        ScreenInfo screen = ScreenInfo.read(activity);
        try { if (target <= ACTION && !screen.matches(store.load(screen))) { toast("屏幕已变化，请清空并重新设置坐标"); return; } }
        catch (IllegalStateException e) { toast(e.getMessage()); return; }
        Ui.dialog(activity, "选择一张完整截图")
            .setMessage(L.t("请使用当前手机、当前方向的完整截图，不要裁剪或使用长截图。") + "\n\n" + screen.width + " × " + screen.height + " px\n\n"
                + L.t(target == WIFI ? "选择展开快捷面板后的截图，点选能直接开关 Wi-Fi 的按钮。" : target == MAP || target == DISTANCE
                    ? "先选择区域左上角，再选择右下角。里程区域请只包含一个数字及单位，避开计时和配速。" : "点按截图选择位置；原图坐标会自动保存。图片仅在本机处理。"))
            .setNegativeButton("取消", null).setPositiveButton("选择截图", (d, w) -> {
                if (!editable.getAsBoolean()) return;
                mode = target; stepIndex = index; requestedScreen = screen;
                try { activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*").putExtra(Intent.EXTRA_LOCAL_ONLY, true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), REQUEST_IMAGE); }
                catch (android.content.ActivityNotFoundException e) { requestedScreen = null; toast("未找到系统文件选择器"); }
            }).show();
    }
    void onResult(int request, int result, Intent data) {
        if (request != REQUEST_IMAGE) return;
        ScreenInfo screen = requestedScreen; requestedScreen = null;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null || !editable.getAsBoolean()) return;
        if (screen == null || !sameScreen(screen)) { toast("屏幕已变化，请重新选择截图"); return; }
        Uri uri = data.getData();
        if (!"content".equals(uri.getScheme())) { toast("请通过系统文件选择器重新选择图片"); return; }
        int token = ++generation;
        dialog = Ui.dialog(activity, "正在读取截图").setMessage("图片仅用于本次选点。")
            .setNegativeButton("取消", (d, w) -> generation++).create();
        dialog.setOnCancelListener(d -> generation++); dialog.show();
        new Thread(() -> {
            Bitmap bitmap = null; String error = null;
            try {
                BitmapFactory.Options header = new BitmapFactory.Options(); header.inJustDecodeBounds = true;
                try (InputStream in = activity.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, header); }
                if (header.outWidth != screen.width || header.outHeight != screen.height || (long) screen.width * screen.height > 16000000)
                    throw new IllegalArgumentException("截图尺寸不符，请使用当前手机的完整截图");
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
                try (InputStream in = activity.getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(in, null, options); }
                if (bitmap == null) throw new IllegalArgumentException("无法读取图片，请重新选择一张有效截图");
            } catch (java.io.IOException | SecurityException | IllegalArgumentException e) { error = e.getMessage(); }
            catch (OutOfMemoryError e) { error = "内存不足，请关闭其他应用后重试"; }
            Bitmap decoded = bitmap; String problem = error;
            activity.runOnUiThread(() -> {
                if (closed || token != generation || activity.isFinishing() || activity.isDestroyed()) { if (decoded != null) decoded.recycle(); return; }
                dialog.dismiss(); dialog = null;
                if (problem != null || decoded == null) { if (decoded != null) decoded.recycle(); toast(problem == null ? "无法读取图片" : problem); return; }
                if (!sameScreen(screen) || !editable.getAsBoolean()) { decoded.recycle(); toast("屏幕或运行状态已变化，请重新选择截图"); return; }
                showPicker(decoded, screen);
            });
        }, "screenshot-import").start();
    }
    private void showPicker(Bitmap bitmap, ScreenInfo screen) {
        boolean region = mode == MAP || mode == DISTANCE;
        LinearLayout content = Ui.column(activity); int p = Ui.dp(activity, 16); content.setPadding(p, 0, p, p);
        content.addView(Ui.text(activity, region ? "点选左上角，确认后再点选右下角。" : "点按或拖动截图，右上角可以放大查看。", Ui.MUTED));
        TextView sample = Ui.text(activity, "请在截图上选择位置", Ui.INK);
        sample.setSingleLine(true); sample.setGravity(android.view.Gravity.CENTER_VERTICAL);
        // CJK and Latin fallback fonts have different line metrics. Keep the preview still while selecting.
        int sampleHeight=(int)Math.ceil(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,32,activity.getResources().getDisplayMetrics()));
        content.addView(sample,new LinearLayout.LayoutParams(-1,sampleHeight));
        int[] point = {-1, -1, 0}, first = {-1, -1};
        String[] titles = {"从截图选点取色", "从截图添加点击步骤", "选择动作点击点", "框选地图区域", "框选里程区域", "选择 Wi-Fi 开关"};
        AlertDialog pickerDialog = Ui.dialog(activity, titles[mode]).setView(content).setNegativeButton("取消", null)
            .setPositiveButton(region ? "确认左上角" : "保存", null).create();
        ScreenshotPickerView picker = new ScreenshotPickerView(activity, bitmap, (x, y, color) -> {
            boolean opaque = Color.alpha(color) == 255;
            point[0] = opaque ? x : -1; point[1] = y; point[2] = color & 0xffffff;
            sample.setText(opaque ? String.format(Locale.ROOT, "X %d   Y %d   #%06X", x, y, point[2]) : L.t("请选择不透明区域"));
            if (pickerDialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) pickerDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(opaque);
        });
        content.addView(picker, new LinearLayout.LayoutParams(-1, 0, 1));
        pickerDialog.setOnDismissListener(d -> { picker.release(); if (dialog == pickerDialog) dialog = null; });
        dialog = pickerDialog; pickerDialog.show(); pickerDialog.getWindow().setLayout(-1, -1);
        Button confirm = pickerDialog.getButton(AlertDialog.BUTTON_POSITIVE); confirm.setEnabled(false);
        confirm.setOnClickListener(v -> {
            if (point[0] < 0 || !editable.getAsBoolean()) return;
            if (!sameScreen(screen)) { toast("屏幕已变化，请重新选择截图"); pickerDialog.dismiss(); return; }
            if (region && first[0] < 0) {
                first[0] = point[0]; first[1] = point[1]; point[0] = -1; confirm.setEnabled(false);
                picker.regionAnchor(first[0],first[1]);
                confirm.setText(L.t("保存区域")); sample.setText("现在点选区域右下角"); return;
            }
            try {
                if (!store.accepted()) throw new IllegalStateException("请先阅读并同意使用说明");
                if (mode >= MAP) {
                    RelayConfig c = new RelayConfig(activity);
                    c.prepare(screen);
                    if (mode == WIFI) { c.wifiX = point[0]; c.wifiY = point[1]; }
                    else {
                        Rect rect = new Rect(first[0], first[1], point[0] + 1, point[1] + 1);
                        if (rect.width() < 12 || rect.height() < 12) throw new IllegalArgumentException("区域过小，请从左上到右下重新框选");
                        if (mode == MAP) c.map = rect; else c.distance = rect;
                    }
                    c.save(activity, screen);
                } else {
                    Store.Draft d = store.load(screen);
                    if (!screen.matches(d)) throw new IllegalStateException("坐标基准已变化，请重新选择截图");
                    if (mode == STEP) {
                        ClickStep old = stepIndex >= 0 && stepIndex < d.steps.size() ? d.steps.get(stepIndex) : null;
                        ClickStep step = new ClickStep(point[0], point[1], old == null ? 50 : old.durationMs, old == null ? 500 : old.waitMs);
                        if (old == null) d.steps.add(step); else d.steps.set(stepIndex, step);
                    } else {
                        ColorRule old = d.colorRule == null ? new ColorRule(false, point[0], point[1], 0xffffff, 16, 2, 500, true, 0, 0) : d.colorRule;
                        d.colorRule = new ColorRule(mode == COLOR || old.enabled, mode == COLOR ? point[0] : old.x, mode == COLOR ? point[1] : old.y,
                            mode == COLOR ? point[2] : old.rgb, old.tolerance, old.confirmations, old.intervalMs, mode == COLOR && old.back,
                            mode == ACTION ? point[0] : old.tapX, mode == ACTION ? point[1] : old.tapY);
                    }
                    store.save(d);
                }
                pickerDialog.dismiss(); saved.run(); toast("已保存");
            } catch (IllegalArgumentException | IllegalStateException e) { toast(e.getMessage()); }
        });
    }
    private boolean sameScreen(ScreenInfo expected) { ScreenInfo current = ScreenInfo.read(activity); return current.width == expected.width && current.height == expected.height && current.rotation == expected.rotation; }
    private void toast(String text) { Toast.makeText(activity, L.t(text), Toast.LENGTH_LONG).show(); }
    void close() { closed = true; generation++; if (dialog != null) dialog.dismiss(); dialog = null; }
}
