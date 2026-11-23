package org.hachile.tongjitap;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.util.Locale;

@android.annotation.SuppressLint("UseSwitchCompatOrMaterialCode") // Deliberate platform controls within platform dialogs.
final class SettingsDialog {
    static void show(Context context, SpeedConfig c, java.util.function.Consumer<SpeedConfig> save) {
        LinearLayout form = MusicUi.column(context);
        int pad = MusicUi.dp(context, 24); form.setPadding(pad, 0, pad, pad);
        TextView intro = MusicUi.text(context, L.t("按你的手感设置，保存后立即生效。"), 13, MusicUi.MUTED);
        form.addView(intro, MusicUi.size(context, -1, 40));
        EditText min = number(context, form, L.t("倍速下限（0.25–3.00）"), c.min);
        EditText max = number(context, form, L.t("倍速上限（0.25–3.00）"), c.max);
        TextView sensitivity = MusicUi.text(context, "", 14, MusicUi.TEXT); form.addView(sensitivity, MusicUi.size(context, -1, 48));
        SeekBar slider = new SeekBar(context); slider.setMax(190); slider.setProgress(Math.round(c.sensitivity * 100) - 10);
        form.addView(slider, MusicUi.size(context, -1, 48));
        sensitivity.setText(String.format(Locale.CHINA, L.t("灵敏度：滑满区域变化 %.2f×"), c.sensitivity));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                sensitivity.setText(String.format(Locale.CHINA, L.t("灵敏度：滑满区域变化 %.2f×"), (value + 10) / 100f));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        Switch vertical = toggle(context, form, L.t("使用上下滑动（默认左右）"), c.vertical);
        Switch reverse = toggle(context, form, L.t("反转加速方向"), c.reverse);
        Switch hold = toggle(context, form, L.t("先按住 0.2 秒再滑动"), c.holdToDrag);
        Switch doubleTap = toggle(context, form, L.t("双击调速区域恢复 1.00×"), c.doubleTapReset);
        Switch pitch = toggle(context, form, L.t("变速时保持原音调"), c.preservePitch);
        Switch haptics = toggle(context, form, L.t("调速时轻微震动"), c.haptics);
        Switch screen = toggle(context, form, L.t("停留在播放器时保持亮屏"), c.keepScreenOn);
        TextView foot = MusicUi.text(context, L.t("锁屏后仍可播放；屏幕关闭时无法接收滑动手势。"), 12, MusicUi.MUTED);
        form.addView(foot, MusicUi.size(context, -1, 64));
        ScrollView scroll = new ScrollView(context); scroll.addView(form);
        AlertDialog dialog = new Ui.LocalDialog(context).setTitle(L.t("调速设置")).setView(scroll)
                .setNegativeButton(L.t("取消"), null).setNeutralButton(L.t("恢复默认"), null).setPositiveButton(L.t("保存"), null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { save.accept(new SpeedConfig()); dialog.dismiss(); });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    SpeedConfig updated = new SpeedConfig();
                    updated.min = Float.parseFloat(min.getText().toString().trim());
                    updated.max = Float.parseFloat(max.getText().toString().trim());
                    updated.sensitivity = (slider.getProgress() + 10) / 100f;
                    updated.vertical = vertical.isChecked(); updated.reverse = reverse.isChecked();
                    updated.holdToDrag = hold.isChecked(); updated.doubleTapReset = doubleTap.isChecked();
                    updated.preservePitch = pitch.isChecked(); updated.haptics = haptics.isChecked(); updated.keepScreenOn = screen.isChecked();
                    updated.validate(); save.accept(updated); dialog.dismiss();
                } catch (IllegalArgumentException e) {
                    max.setError(e instanceof NumberFormatException ? L.t("请输入有效数字") : e.getMessage());
                }
            });
        });
        dialog.show();
    }

    private static EditText number(Context context, LinearLayout parent, String label, float value) {
        TextView title = MusicUi.text(context, label, 13, MusicUi.MUTED); parent.addView(title, MusicUi.size(context, -1, 32));
        EditText field = new EditText(context); field.setText(String.format(Locale.US, "%.2f", value)); field.setTextColor(MusicUi.TEXT);
        field.setTextSize(Ui.BODY_SP); field.setSingleLine(true); field.setContentDescription(label);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        parent.addView(field, MusicUi.size(context, -1, 52)); return field;
    }

    private static Switch toggle(Context c, LinearLayout parent, String label, boolean checked) {
        Switch toggle = new Switch(c); toggle.setText(L.t(label)); toggle.setTextSize(Ui.BODY_SP); toggle.setTextColor(MusicUi.TEXT); toggle.setChecked(checked);
        parent.addView(toggle, MusicUi.size(c, -1, 56)); return toggle;
    }
}
