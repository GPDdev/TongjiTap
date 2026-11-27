package org.hachile.tongjitap;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

final class BlueUi {
    private BlueUi() {}

    static void divider(LinearLayout group) {
        View line = new View(group.getContext());
        line.setBackgroundColor(Ui.LINE);
        group.addView(line, new LinearLayout.LayoutParams(-1, 1));
    }

    static RippleDrawable ripple(Context c, int fill, int radius) {
        return new RippleDrawable(ColorStateList.valueOf((Ui.BLUE & 0x00ffffff) | 0x24000000),
                Ui.background(c, fill, radius), null);
    }

    static String tabLabel(int index) {
        switch (index) {
            case Tabs.STRIDE: return L.t("随步");
            case Tabs.RELAY: return L.t("跃点");
            case Tabs.ROAM: return L.t("漫迹");
            case Tabs.MORE: return L.t("更多");
            default: return L.t("循济");
        }
    }

    static void roamAvailability(LinearLayout page) {
        Context c = page.getContext();
        LinearLayout body = Ui.column(c);
        body.addView(Ui.text(c, "iOS", Ui.MUTED));
        Ui.gap(body, 8);
        body.addView(Ui.heading(c, L.t("漫迹暂未提供")));
        page.addView(statusPanel(c, body));
    }

    static LinearLayout navigation(Context c, int selected, IntConsumer onSelect) {
        LinearLayout navigation = Ui.column(c);
        navigation.setBackgroundColor(Ui.BG);
        divider(navigation);
        LinearLayout tabs = new LinearLayout(c);
        tabs.setBaselineAligned(false);
        navigation.addView(tabs);
        for (int index : Tabs.ORDER) {
            boolean active = index == selected;
            String name = tabLabel(index);
            LinearLayout item = Ui.column(c);
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(Ui.dp(c, 68));
            item.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 5));
            item.setContentDescription(name);
            item.setTag("tab-" + index);
            item.setSelected(active);
            item.setFocusable(true);
            item.setBackground(ripple(c, android.graphics.Color.TRANSPARENT, 12));
            item.setOnClickListener(v -> onSelect.accept(index));
            item.addView(new NavIcon(c, index, active), MusicUi.size(c, 24, 24));
            TextView label = Ui.text(c, name, active ? Ui.BLUE : Ui.MUTED);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            label.setGravity(Gravity.CENTER);
            if (active) label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            item.addView(label);
            Ui.gap(item, 3);
            View indicator = new View(c);
            indicator.setBackground(Ui.background(c, Ui.BLUE, 2));
            indicator.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
            item.addView(indicator, MusicUi.size(c, 40, 3));
            tabs.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
        }
        return navigation;
    }

    static LinearLayout group(Context c) {
        LinearLayout block = Ui.column(c);
        block.setBackground(Ui.background(c, Ui.SURFACE, 16));
        block.setClipToOutline(true);
        return block;
    }

    /** A row grows with its labels. A chevron only appears when it opens something. */
    @android.annotation.SuppressLint("ViewConstructor") // Composed in Java with the row label and action.
    static final class Row extends LinearLayout {
        final TextView value;
        private Mark arrow;
        Row(Context c, String title, String detail, Runnable action) {
            this(c, title, detail, action, false, null);
        }
        Row(Context c, String title, String detail, Runnable action, boolean primary, Mark.Kind icon) {
            super(c);
            int foreground = primary ? Ui.ON_BLUE : Ui.INK;
            int inset = Ui.dp(c, 16);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(Ui.dp(c, 58));
            setPadding(inset, Ui.dp(c, 10), inset, Ui.dp(c, 10));
            if (action != null) {
                setBackground(ripple(c, primary ? Ui.BLUE : android.graphics.Color.TRANSPARENT, primary ? 13 : 0));
                setFocusable(true);
                setOnClickListener(v -> action.run());
            }
            if (icon != null) {
                LinearLayout.LayoutParams iconParams = MusicUi.size(c, 24, 24);
                iconParams.setMarginEnd(Ui.dp(c, 12));
                addView(new Mark(c, icon, foreground), iconParams);
            }
            TextView name = Ui.text(c, title, foreground);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            value = Ui.text(c, detail, primary ? Ui.ON_BLUE : Ui.MUTED);
            value.setGravity(Gravity.END);
            value.setMaxEms(11);
            value.setVisibility(detail.isEmpty() ? GONE : VISIBLE);
            addView(value);
            if (action != null) {
                LinearLayout.LayoutParams end = MusicUi.size(c, 16, 20);
                end.setMarginStart(Ui.dp(c, 8));
                arrow = new Mark(c, Mark.Kind.NEXT, primary ? foreground : Ui.MUTED);
                addView(arrow, end);
            }
        }
        void setExpanded(boolean expanded) {
            arrow.setRotation(expanded ? -90 : 90);
            androidx.core.view.ViewCompat.setStateDescription(this, getContext().getString(expanded ? R.string.expanded : R.string.collapsed));
        }
        @Override public CharSequence getAccessibilityClassName() {
            return isClickable() ? android.widget.Button.class.getName() : super.getAccessibilityClassName();
        }
    }

    static FrameLayout statusPanel(Context c, LinearLayout body) {
        FrameLayout panel = new FrameLayout(c) {
            final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Path curve = new Path();
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float w = getWidth(), h = getHeight();
                ink.setColor(c.getColor(R.color.wave));
                ink.setStyle(Paint.Style.STROKE);
                ink.setStrokeWidth(getResources().getDisplayMetrics().density);
                for (int i = 0; i < 5; i++) {
                    float offset = i * .065f;
                    curve.reset();
                    curve.moveTo(w * (.20f + offset), h * 1.04f);
                    curve.cubicTo(w * (.72f + offset), h, w * (.71f + offset),
                            h * .40f, w * 1.04f, h * (.16f + offset));
                    canvas.drawPath(curve, ink);
                }
            }
        };
        panel.setWillNotDraw(false);
        panel.setBackground(Ui.background(c, Ui.TONAL, 16));
        panel.setClipToOutline(true);
        int pad = Ui.dp(c, 20);
        body.setPadding(pad, pad, pad, pad);
        panel.addView(body, new FrameLayout.LayoutParams(-1, -2));
        return panel;
    }

    @android.annotation.SuppressLint("ViewConstructor")
    static final class Mark extends View {
        enum Kind { NEXT, BOOK, NOTE, LIBRARY, WIFI }
        private final Kind kind;
        private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        Mark(Context c, Kind kind, int color) {
            super(c);
            this.kind = kind;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            pen.setColor(color);
            pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeWidth(1.9f);
            pen.setStrokeCap(Paint.Cap.ROUND);
            pen.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override protected void onDraw(Canvas canvas) {
            float unit = Math.min(getWidth(), getHeight()) / 24f;
            canvas.save();
            canvas.translate((getWidth() - 24 * unit) / 2, (getHeight() - 24 * unit) / 2);
            canvas.scale(unit, unit);
            path.reset();
            switch (kind) {
                case NEXT:
                    path.moveTo(9, 5); path.lineTo(16, 12); path.lineTo(9, 19);
                    break;
                case BOOK:
                    path.moveTo(12, 6); path.quadTo(7, 3, 2, 5); path.lineTo(2, 19);
                    path.quadTo(7, 17, 12, 20); path.quadTo(17, 17, 22, 19);
                    path.lineTo(22, 5); path.quadTo(17, 3, 12, 6); path.lineTo(12, 20);
                    break;
                case NOTE:
                    path.moveTo(7, 17); path.lineTo(7, 6); path.lineTo(19, 3); path.lineTo(19, 14);
                    canvas.drawOval(2, 16, 7, 20, pen); canvas.drawOval(14, 13, 19, 17, pen);
                    break;
                case LIBRARY:
                    canvas.drawLine(2, 5, 13, 5, pen); canvas.drawLine(2, 10, 11, 10, pen);
                    canvas.drawLine(2, 15, 7, 15, pen);
                    path.moveTo(16, 17); path.lineTo(16, 6); path.lineTo(21, 4);
                    canvas.drawOval(11, 16, 16, 20, pen);
                    break;
                case WIFI:
                    canvas.drawArc(-3, 4, 27, 31, 226, 88, false, pen);
                    canvas.drawArc(2, 9, 22, 27, 226, 88, false, pen);
                    canvas.drawArc(7, 14, 17, 23, 226, 88, false, pen);
                    canvas.drawCircle(12, 20, .8f, pen);
                    break;
            }
            canvas.drawPath(path, pen);
            canvas.restore();
        }
    }
}
