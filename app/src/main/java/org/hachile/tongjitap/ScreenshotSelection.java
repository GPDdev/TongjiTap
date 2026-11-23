package org.hachile.tongjitap;

/** Fit-center geometry. Coordinates always refer to the original, unscaled screenshot. */
final class ScreenshotSelection {
    final int width, height;
    final float scale, left, top;

    ScreenshotSelection(int width, int height, int viewWidth, int viewHeight) {
        if (width <= 0 || height <= 0 || viewWidth <= 0 || viewHeight <= 0)
            throw new IllegalArgumentException("Invalid image or viewport dimensions");
        this.width = width; this.height = height;
        scale = Math.min((float) viewWidth / width, (float) viewHeight / height);
        left = (viewWidth - width * scale) / 2f; top = (viewHeight - height * scale) / 2f;
    }

    int[] pixel(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || x < left || y < top
                || x >= left + width * scale || y >= top + height * scale) return null;
        return new int[]{Math.min(width - 1, (int) ((x - left) / scale)),
                Math.min(height - 1, (int) ((y - top) / scale))};
    }
}
