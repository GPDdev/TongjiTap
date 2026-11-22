package org.hachile.tongjitap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClickPlan {
    public final int loops, width, height, rotation;
    public final List<ClickStep> steps;

    public ClickPlan(int loops, int width, int height, int rotation, List<ClickStep> steps) {
        if (loops < 1 || loops > 100000) throw new IllegalArgumentException("循环次数应为 1～100000");
        if (width <= 0 || height <= 0 || rotation < 0 || rotation > 3) throw new IllegalArgumentException("屏幕信息无效，请重新取点");
        if (steps == null || steps.isEmpty()) throw new IllegalArgumentException("请先添加至少一个点击步骤");
        for (ClickStep step : steps) {
            if (step == null || step.x >= width || step.y >= height) throw new IllegalArgumentException("坐标超出屏幕范围，请重新取点");
        }
        this.loops = loops; this.width = width; this.height = height; this.rotation = rotation;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public boolean matches(int w, int h, int r) { return width == w && height == h && rotation == r; }
}
