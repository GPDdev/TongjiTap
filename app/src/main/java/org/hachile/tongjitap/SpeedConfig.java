package org.hachile.tongjitap;

public final class SpeedConfig {
    public float min = .75f;
    public float max = 1.5f;
    public float sensitivity = .5f;
    public boolean vertical;
    public boolean reverse;
    public boolean holdToDrag;
    public boolean haptics = true;
    public boolean keepScreenOn = true;
    public boolean preservePitch = true;
    public boolean doubleTapReset = true;

    public void validate() {
        if (!Float.isFinite(min) || !Float.isFinite(max) || min < .25f || max > 3f || max - min < .04999f)
            throw new IllegalArgumentException("倍速范围需在 0.25–3.00 之间，上限至少比下限大 0.05");
        if (!Float.isFinite(sensitivity) || sensitivity < .1f || sensitivity > 2f)
            throw new IllegalArgumentException("滑过整个区域的变化量需在 0.10–2.00 之间");
    }

    public float clamp(float speed) {
        if (!Float.isFinite(speed)) speed = 1f;
        return Math.max(min, Math.min(max, Math.round(speed * 100f) / 100f));
    }

    public float drag(float start, float distance, float size) {
        if (!Float.isFinite(distance) || !Float.isFinite(size) || size <= 0) return clamp(start);
        return clamp(start + distance / size * sensitivity * (reverse ? -1f : 1f));
    }
}
