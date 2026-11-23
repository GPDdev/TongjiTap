package org.hachile.tongjitap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.*;
import java.io.IOException;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** The document grant is transient; no image path, image bytes or grant is persisted. */
final class ScreenshotImport {
    private static final int REQUEST_IMAGE = 41;
    private final Activity activity;
    private final Store store;
    private final BooleanSupplier editable;
    private final Runnable saved;
    private ScreenInfo requestedScreen;
    private AlertDialog dialog;
    private int generation;
    private boolean closed;

    ScreenshotImport(Activity activity, Store store, BooleanSupplier editable, Runnable saved, Bundle state) {
        this.activity = activity; this.store = store; this.editable = editable; this.saved = saved;
        if (state != null && state.containsKey("screenshotWidth")) requestedScreen = new ScreenInfo(
                state.getInt("screenshotWidth"), state.getInt("screenshotHeight"), state.getInt("screenshotRotation"));
    }

    void saveState(Bundle state) {
        if (requestedScreen == null) return;
        state.putInt("screenshotWidth", requestedScreen.width); state.putInt("screenshotHeight", requestedScreen.height);
        state.putInt("screenshotRotation", requestedScreen.rotation);
    }

    @SuppressWarnings("deprecation")
    void open() {
        if (Build.VERSION.SDK_INT < 30 || !editable.getAsBoolean()) return;
        if (!store.accepted()) { toast("请先阅读并同意使用说明"); return; }
        try {
            ScreenInfo screen = ScreenInfo.read(activity);
            if (!screen.matches(store.load(screen))) { toast("屏幕已变化，请清空并重新设置坐标"); return; }
            Ui.dialog(activity, "选择一张完整截图")
                .setMessage("请先截取目标页面，再从本机选择截图。需要当前手机、当前方向的完整截图（"
                    + screen.width + " × " + screen.height + " 像素），不要裁剪或使用长截图。\n\n点选后会同时记录位置和颜色。图片只在本机处理，不保存副本、不上传。")
                .setNegativeButton("取消", null).setPositiveButton("选择截图", (d, w) -> {
                    if (!editable.getAsBoolean()) return;
                    requestedScreen = screen;
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("image/*").putExtra(Intent.EXTRA_LOCAL_ONLY, true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { activity.startActivityForResult(intent, REQUEST_IMAGE); }
                    catch (android.content.ActivityNotFoundException e) { requestedScreen = null; toast("未找到系统文件选择器"); }
                }).show();
        } catch (IllegalStateException e) { toast(e.getMessage()); }
    }

    void onResult(int request, int result, Intent data) {
        if (request != REQUEST_IMAGE) return;
        ScreenInfo screen = requestedScreen; requestedScreen = null;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        if (Build.VERSION.SDK_INT < 30 || !editable.getAsBoolean()) return;
        if (screen == null || !sameScreen(screen)) { toast("屏幕已变化，请重新选择截图"); return; }
        Uri uri = data.getData();
        if (!"content".equals(uri.getScheme())) { toast("无法读取这张图片，请通过系统文件选择器重新选择"); return; }
        int token = ++generation;
        dialog = Ui.dialog(activity, "正在读取截图").setMessage("图片仅用于本次选点。")
            .setNegativeButton("取消", (d, w) -> generation++).create();
        dialog.setOnCancelListener(d -> generation++); dialog.show();
        // Decode away from the UI thread; verify header dimensions before allocating pixels.
        new Thread(() -> {
            Bitmap bitmap = null; String error = null;
            try {
                bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(activity.getContentResolver(), uri), (decoder, info, source) -> {
                    if (info.getSize().getWidth() != screen.width || info.getSize().getHeight() != screen.height)
                        throw new IllegalArgumentException("截图尺寸不符，请选择 " + screen.width + " × " + screen.height + " 像素的完整截图");
                    if ((long) screen.width * screen.height > 16_000_000)
                        throw new IllegalArgumentException("截图像素过多，请使用悬浮面板选点并手动填写颜色");
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
                });
                if (bitmap.getWidth() != screen.width || bitmap.getHeight() != screen.height) {
                    bitmap.recycle(); bitmap = null;
                    throw new IllegalArgumentException("图片方向与当前屏幕不一致，请重新截取完整画面");
                }
            } catch (IllegalArgumentException e) { error = e.getMessage(); }
            catch (IOException | SecurityException e) { error = "无法读取图片，请重新选择一张有效截图"; }
            catch (OutOfMemoryError e) { error = "内存不足，请关闭其他应用后重试"; }
            Bitmap decoded = bitmap; String problem = error;
            activity.runOnUiThread(() -> {
                if (closed || token != generation || activity.isFinishing() || activity.isDestroyed()) {
                    if (decoded != null) decoded.recycle(); return;
                }
                dialog.dismiss(); dialog = null;
                if (problem != null || decoded == null) { toast(problem == null ? "无法读取图片" : problem); return; }
                if (!sameScreen(screen) || !editable.getAsBoolean()) { decoded.recycle(); toast("屏幕或运行状态已变化，请重新选择截图"); return; }
                try { showPicker(decoded, screen); }
                catch (IllegalStateException e) { decoded.recycle(); toast(e.getMessage()); }
            });
        }, "screenshot-import").start();
    }

    private void showPicker(Bitmap bitmap, ScreenInfo screen) {
        LinearLayout content = Ui.column(activity); int padding = Ui.dp(activity, 16);
        content.setPadding(padding, 0, padding, padding);
        content.addView(Ui.text(activity, "点按或拖动截图，避开状态栏和悬浮面板。右上角可查看放大的像素。", Ui.MUTED));
        Ui.gap(content, 8);
        TextView sample = Ui.text(activity, "请在截图上选择检测点", Ui.INK);
        LinearLayout row = new LinearLayout(activity); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.view.View swatch = new android.view.View(activity);
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(Ui.dp(activity, 24), Ui.dp(activity, 24));
        swatchParams.rightMargin = Ui.dp(activity, 10); row.addView(swatch, swatchParams); row.addView(sample);
        content.addView(row); Ui.gap(content, 8);
        int[] point = {-1, -1, 0};
        AlertDialog pickerDialog = Ui.dialog(activity, "从截图选点取色").setView(content)
            .setNegativeButton("取消", null).setPositiveButton("使用并开启", null).create();
        ScreenshotPickerView picker = new ScreenshotPickerView(activity, bitmap, (x, y, color) -> {
            boolean opaque = Color.alpha(color) == 255;
            point[0] = opaque ? x : -1; point[1] = y; point[2] = color & 0xFFFFFF;
            sample.setText(opaque ? String.format(Locale.ROOT, "X %d   Y %d   #%06X", x, y, point[2]) : "此处为透明像素，请选择不透明区域");
            swatch.setBackground(Ui.background(activity, color, 4));
            pickerDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(opaque);
        });
        content.addView(picker, new LinearLayout.LayoutParams(-1, 0, 1)); Ui.gap(content, 8);
        ColorRule old = store.load(screen).colorRule;
        content.addView(Ui.text(activity, old == null ? "确认后开启颜色规则，命中时执行手机返回。可在“设置颜色规则”中更改动作。"
                : "确认后开启颜色规则，保留原有动作、容差和检测间隔。", Ui.MUTED));
        pickerDialog.setOnDismissListener(d -> { picker.release(); if (dialog == pickerDialog) dialog = null; });
        dialog = pickerDialog; pickerDialog.show();
        pickerDialog.getWindow().setLayout(-1, -1);
        Button confirm = pickerDialog.getButton(AlertDialog.BUTTON_POSITIVE); confirm.setEnabled(false);
        confirm.setOnClickListener(v -> {
            if (point[0] < 0 || !editable.getAsBoolean()) return;
            if (!sameScreen(screen)) { toast("屏幕已变化，请重新选择截图"); pickerDialog.dismiss(); return; }
            try {
                Store.Draft updated = store.load(screen);
                if (!store.accepted() || !screen.matches(updated)) throw new IllegalStateException("坐标基准已变化，请重新选择截图");
                ColorRule previous = updated.colorRule;
                updated.colorRule = new ColorRule(true, point[0], point[1], point[2], previous == null ? 16 : previous.tolerance,
                    previous == null ? 2 : previous.confirmations, previous == null ? 500 : previous.intervalMs,
                    previous == null || previous.back, previous == null ? 0 : previous.tapX, previous == null ? 0 : previous.tapY);
                store.save(updated); pickerDialog.dismiss(); saved.run(); toast("检测点和颜色已保存，颜色规则已开启");
            } catch (IllegalArgumentException | IllegalStateException e) { toast(e.getMessage()); }
        });
    }

    private boolean sameScreen(ScreenInfo expected) {
        ScreenInfo current = ScreenInfo.read(activity);
        return current.width == expected.width && current.height == expected.height && current.rotation == expected.rotation;
    }
    private void toast(String text) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
    void close() { closed = true; generation++; if (dialog != null) dialog.dismiss(); dialog = null; }
}
