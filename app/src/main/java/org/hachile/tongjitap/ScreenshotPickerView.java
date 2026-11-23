package org.hachile.tongjitap;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

@android.annotation.SuppressLint("ViewConstructor") // Programmatic view requiring a transient bitmap and callback.
final class ScreenshotPickerView extends View {
    interface Listener { void selected(int x, int y, int color); }
    private Bitmap bitmap;
    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageBounds = new RectF(), loupeBounds = new RectF();
    private final Rect loupeSource = new Rect();
    private ScreenshotSelection geometry;
    private int selectedX = -1, selectedY = -1;
    private int anchorX = -1, anchorY = -1;

    ScreenshotPickerView(Context context, Bitmap bitmap, Listener listener) {
        super(context); this.bitmap = bitmap; this.listener = listener;
        setContentDescription(L.t("截图选点区域，点按或拖动选择检测位置"));
        setClickable(true); setFocusable(true);
        setBackgroundColor(Color.rgb(229, 232, 237));
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (bitmap == null || width <= 0 || height <= 0) { geometry = null; return; }
        geometry = new ScreenshotSelection(bitmap.getWidth(), bitmap.getHeight(), width, height);
        imageBounds.set(geometry.left, geometry.top, geometry.left + bitmap.getWidth() * geometry.scale,
                geometry.top + bitmap.getHeight() * geometry.scale);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || geometry == null) return;
        ScreenshotSelection g = geometry; paint.setFilterBitmap(true);
        canvas.drawBitmap(bitmap, null, imageBounds, paint);
        if (selectedX < 0) return;
        float x = g.left + (selectedX + .5f) * g.scale, y = g.top + (selectedY + .5f) * g.scale;
        if (anchorX >= 0) {
            float ax = g.left + anchorX * g.scale, ay = g.top + anchorY * g.scale;
            paint.setColor(0x332563eb); canvas.drawRect(Math.min(ax,x),Math.min(ay,y),Math.max(ax,x),Math.max(ay,y),paint);
            paint.setColor(Ui.BLUE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Ui.dp(getContext(),2));
            canvas.drawRect(Math.min(ax,x),Math.min(ay,y),Math.max(ax,x),Math.max(ay,y),paint); paint.setStyle(Paint.Style.FILL);
            crosshair(canvas,ax,ay);
        }
        crosshair(canvas, x, y);
        // An unfiltered loupe makes neighboring pixels visible even on a small preview.
        int radius = 14, size = Ui.dp(getContext(), 96), margin = Ui.dp(getContext(), 8);
        int cx = Math.max(radius, Math.min(bitmap.getWidth() - radius, selectedX));
        int cy = Math.max(radius, Math.min(bitmap.getHeight() - radius, selectedY));
        Rect src = loupeSource; src.set(cx - radius, cy - radius, cx + radius, cy + radius);
        RectF dest = loupeBounds; dest.set(getWidth() - size - margin, margin, getWidth() - margin, margin + size);
        paint.setFilterBitmap(false); canvas.drawBitmap(bitmap, src, dest, paint);
        paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Ui.dp(getContext(), 2));
        canvas.drawRect(dest, paint); paint.setStyle(Paint.Style.FILL);
        crosshair(canvas, dest.left + (selectedX - src.left + .5f) * size / src.width(),
                dest.top + (selectedY - src.top + .5f) * size / src.height());
    }

    private void crosshair(Canvas canvas, float x, float y) {
        int radius = Ui.dp(getContext(), 10);
        for (int i = 0; i < 2; i++) {
            paint.setColor(i == 0 ? Color.WHITE : Ui.BLUE); paint.setStrokeWidth(Ui.dp(getContext(), i == 0 ? 4 : 2));
            canvas.drawLine(x - radius, y, x + radius, y, paint);
            canvas.drawLine(x, y - radius, x, y + radius, paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null || geometry == null) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE
                || event.getActionMasked() == MotionEvent.ACTION_UP) {
            int[] point = geometry.pixel(event.getX(), event.getY());
            if (point != null) {
                selectedX = point[0]; selectedY = point[1];
                listener.selected(selectedX, selectedY, bitmap.getPixel(selectedX, selectedY)); invalidate();
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    void regionAnchor(int x, int y) { anchorX=x; anchorY=y; invalidate(); }
    // Drop the reference after the window detaches. The renderer may still hold the bitmap briefly.
    void release() { bitmap = null; }
}
