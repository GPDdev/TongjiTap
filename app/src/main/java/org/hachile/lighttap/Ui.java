package org.hachile.lighttap;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BLUE = Color.rgb(37, 99, 235), INK = Color.rgb(21, 32, 53), MUTED = Color.rgb(83, 98, 120);
    static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static LinearLayout column(Context c) {
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    static TextView text(Context c, String text, int size, int color) {
        TextView v = new TextView(c); v.setText(text); v.setTextSize(size); v.setTextColor(color);
        v.setLineSpacing(dp(c, 3), 1); return v;
    }
    static TextView heading(Context c, String text, int size) {
        TextView v = text(c, text, size, INK); v.setTypeface(null, Typeface.BOLD); return v;
    }
    static GradientDrawable background(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius)); return d;
    }
    static LinearLayout card(Context c) {
        LinearLayout v = column(c); int p = dp(c, 16); v.setPadding(p, p, p, p);
        v.setBackground(background(c, Color.WHITE, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(c, 14); v.setLayoutParams(lp);
        return v;
    }
    static Button button(Context c, String text, boolean primary, View.OnClickListener click) {
        Button b = new Button(c); b.setText(text); b.setAllCaps(false); b.setTextSize(15);
        b.setMinHeight(dp(c, 48)); b.setMinimumHeight(dp(c, 48)); b.setTextColor(primary ? Color.WHITE : BLUE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary ? BLUE : Color.rgb(232, 239, 255)));
        b.setOnClickListener(click); return b;
    }
    static void gap(LinearLayout parent, int height) {
        View spacer = new View(parent.getContext()); parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(parent.getContext(), height)));
    }
    static void enabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) enabled(group.getChildAt(i), enabled);
        }
        if (view instanceof Button) view.setAlpha(enabled ? 1f : 0.55f);
    }
}
