package org.hachile.tongjitap;

/** One opt-in, screen-relative RGB condition. No Android dependencies. */
public final class ColorRule {
    public final boolean enabled, back;
    public final int x, y, rgb, tolerance, confirmations, intervalMs, tapX, tapY;

    public ColorRule(boolean enabled, int x, int y, int rgb, int tolerance,
                     int confirmations, int intervalMs, boolean back, int tapX, int tapY) {
        if (x < 0 || y < 0 || tapX < 0 || tapY < 0) throw new IllegalArgumentException("坐标不能小于 0");
        if (rgb < 0 || rgb > 0xFFFFFF) throw new IllegalArgumentException("颜色应为 #RRGGBB");
        if (tolerance < 0 || tolerance > 64) throw new IllegalArgumentException("颜色容差应为 0～64");
        if (confirmations < 2 || confirmations > 10) throw new IllegalArgumentException("连续命中次数应为 2～10");
        if (intervalMs < 500 || intervalMs > 10000) throw new IllegalArgumentException("检测间隔应为 500～10000 毫秒");
        this.enabled = enabled; this.x = x; this.y = y; this.rgb = rgb; this.tolerance = tolerance;
        this.confirmations = confirmations; this.intervalMs = intervalMs; this.back = back; this.tapX = tapX; this.tapY = tapY;
    }

    public void validateScreen(int width, int height) {
        if (x >= width || y >= height || (!back && (tapX >= width || tapY >= height)))
            throw new IllegalArgumentException("颜色规则的坐标超出屏幕范围");
    }

    public boolean matches(int color) {
        return Math.abs(((color >> 16) & 255) - ((rgb >> 16) & 255)) <= tolerance
            && Math.abs(((color >> 8) & 255) - ((rgb >> 8) & 255)) <= tolerance
            && Math.abs((color & 255) - (rgb & 255)) <= tolerance;
    }

    public static int parseRgb(String text) {
        if (text == null || !text.trim().matches("#?[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("颜色请填写 6 位色值，例如 #FFFFFF");
        return Integer.parseInt(text.trim().replace("#", ""), 16);
    }

    public String hex() { return String.format(java.util.Locale.ROOT, "#%06X", rgb); }

    /** Fires once after consecutive matches. Two misses rearm the condition. */
    public static final class Detector {
        private final ColorRule rule;
        private int hits, misses;
        private boolean latched;
        public Detector(ColorRule rule) { this.rule = rule; }
        public boolean sample(int color) {
            if (!rule.matches(color)) {
                hits = 0;
                if (++misses >= 2) latched = false;
                return false;
            }
            misses = 0;
            if (latched) return false;
            if (++hits < rule.confirmations) return false;
            latched = true; return true;
        }
        public boolean latched() { return latched; }
    }
}
