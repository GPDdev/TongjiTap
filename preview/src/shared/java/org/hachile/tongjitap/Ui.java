package org.hachile.tongjitap;

import android.content.Context;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

@android.annotation.SuppressLint("AppCompatCustomView") // Platform Activity and themes; Media3 adds AppCompat only transitively.
final class Ui {
    static final int TITLE_SP = 30, SUBTITLE_SP = 20, BODY_SP = 16;
    static int BLUE = 0xff2155d6, INK = 0xff152238, MUTED = 0xff526075, BG = 0xfff7f9fd,
        SURFACE = Color.WHITE, LINE = 0xffdce3ef, TONAL = 0xffe4edfd, ON_BLUE = Color.WHITE;
    static boolean dark;
    static void apply(Context c) {
        dark = (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        INK = c.getColor(R.color.ink); MUTED = c.getColor(R.color.muted);
        BG = c.getColor(R.color.app_background); SURFACE = c.getColor(R.color.surface);
        LINE = c.getColor(R.color.divider); BLUE = c.getColor(R.color.accent);
        TONAL = c.getColor(R.color.tonal); ON_BLUE = c.getColor(R.color.on_accent);
        MusicUi.apply();
    }
    static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static LinearLayout column(Context c) {
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    static TextView text(Context c, String text, int color) {
        TextView v = new TextView(c) {
            @Override public void setText(CharSequence value, BufferType type) { super.setText(L.t(value), type); }
        }; v.setText(text); v.setTextSize(BODY_SP); v.setTextColor(color);
        v.setLineSpacing(dp(c, 3), 1); return v;
    }
    static TextView literalText(Context c, String text, int color) {
        TextView v = new TextView(c); v.setText(text); v.setTextSize(BODY_SP); v.setTextColor(color);
        v.setLineSpacing(dp(c,3),1); return v;
    }
    static TextView heading(Context c, String text) {
        TextView v = text(c, text, INK); v.setTextSize(SUBTITLE_SP); v.setTypeface(null, Typeface.BOLD); return v;
    }
    static TextView title(Context c, String text) {
        TextView v = heading(c, text); v.setTextSize(TITLE_SP); return v;
    }
    static AlertDialog.Builder dialog(Context c, String title) {
        TextView heading = heading(c, title);
        heading.setPadding(dp(c, 24), dp(c, 20), dp(c, 24), dp(c, 8));
        return new LocalDialog(c).setCustomTitle(heading);
    }
    static class LocalDialog extends AlertDialog.Builder {
        LocalDialog(Context c) { super(c); }
        @Override public AlertDialog.Builder setTitle(CharSequence text) { return setCustomTitle(heading(getContext(), L.t(text).toString())); }
        @Override public AlertDialog.Builder setMessage(CharSequence text) { return super.setMessage(L.t(text)); }
        @Override public AlertDialog.Builder setPositiveButton(CharSequence text, android.content.DialogInterface.OnClickListener l) { return super.setPositiveButton(L.t(text), l); }
        @Override public AlertDialog.Builder setNegativeButton(CharSequence text, android.content.DialogInterface.OnClickListener l) { return super.setNegativeButton(L.t(text), l); }
        @Override public AlertDialog.Builder setNeutralButton(CharSequence text, android.content.DialogInterface.OnClickListener l) { return super.setNeutralButton(L.t(text), l); }
        @Override public AlertDialog.Builder setItems(CharSequence[] items, android.content.DialogInterface.OnClickListener l) {
            CharSequence[] translated = new CharSequence[items.length];
            for (int i = 0; i < items.length; i++) translated[i] = L.t(items[i]);
            return super.setItems(translated, l);
        }
    }
    static GradientDrawable background(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius)); return d;
    }
    static LinearLayout card(Context c) {
        LinearLayout v = column(c); int p = dp(c, 16); v.setPadding(p, p, p, p);
        v.setBackground(background(c, SURFACE, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(c, 14); v.setLayoutParams(lp);
        return v;
    }
    static Button button(Context c, String text, boolean primary, View.OnClickListener click) {
        Button b = new Button(c) {
            @Override public void setText(CharSequence value, BufferType type) { super.setText(L.t(value), type); }
        }; b.setText(text); b.setAllCaps(false); b.setTextSize(BODY_SP);
        b.setMinHeight(dp(c, 48)); b.setMinimumHeight(dp(c, 48)); b.setMinWidth(0); b.setMinimumWidth(0);
        int[][] states = {{-android.R.attr.state_enabled}, {}};
        b.setTextColor(new ColorStateList(states, new int[]{dark ? 0xff8793a6 : 0xff6f7886, primary ? ON_BLUE : BLUE}));
        GradientDrawable shape = background(c, BLUE, 12);
        shape.setColor(new ColorStateList(states, new int[]{dark ? 0xff303b4d : 0xffe5e8ed, primary ? BLUE : TONAL}));
        b.setBackgroundTintList(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x332563EB), shape, null));
        b.setStateListAnimator(null); b.setElevation(0);
        b.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(c, 8); b.setLayoutParams(lp);
        b.setOnClickListener(click); return b;
    }
    static LinearLayout.LayoutParams actionParams(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(c, 3), dp(c, 8), dp(c, 3), 0); return lp;
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
    }
}
