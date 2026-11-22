package org.hachile.tongjitap;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

final class ScreenInfo {
    final int width, height, rotation;
    ScreenInfo(int width, int height, int rotation) {
        this.width = width; this.height = height; this.rotation = rotation;
    }
    @SuppressWarnings("deprecation")
    static ScreenInfo read(Context context) {
        WindowManager manager = context.getSystemService(WindowManager.class);
        int rotation = manager.getDefaultDisplay().getRotation();
        if (Build.VERSION.SDK_INT >= 30) {
            Rect bounds = manager.getMaximumWindowMetrics().getBounds();
            return new ScreenInfo(bounds.width(), bounds.height(), rotation);
        }
        DisplayMetrics metrics = new DisplayMetrics();
        manager.getDefaultDisplay().getRealMetrics(metrics);
        return new ScreenInfo(metrics.widthPixels, metrics.heightPixels, rotation);
    }
    boolean matches(Store.Draft draft) {
        return width == draft.width && height == draft.height && rotation == draft.rotation;
    }
}
