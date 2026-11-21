package org.hachile.lighttap;

public final class ClickStep {
    public final int x, y, durationMs, waitMs;

    public ClickStep(int x, int y, int durationMs, int waitMs) {
        if (x < 0 || y < 0) throw new IllegalArgumentException("坐标不能为负数");
        if (durationMs < 1 || durationMs > 10000) throw new IllegalArgumentException("按住时长应为 1～10000 毫秒");
        if (waitMs < 0 || waitMs > 3600000) throw new IllegalArgumentException("等待时间应为 0～3600000 毫秒");
        this.x = x; this.y = y; this.durationMs = durationMs; this.waitMs = waitMs;
    }
}
