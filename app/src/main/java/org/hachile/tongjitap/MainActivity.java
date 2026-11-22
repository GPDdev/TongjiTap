package org.hachile.tongjitap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.*;
import java.util.ArrayList;
import java.util.Collections;

public final class MainActivity extends Activity {
    private Store store;
    private Store.Draft draft;
    private TextView status, permission;
    private LinearLayout editor;
    private boolean consentShowing;
    private final ArrayList<Button> unavailableMoves = new ArrayList<>();
    private final Runnable stateObserver = this::refreshStatus;
    private final Runnable configObserver = this::render;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new Store(this); render();
    }
    @Override public void onResume() {
        super.onResume(); TapService.stateObserver = stateObserver; TapService.configObserver = configObserver;
        render(); if (!store.accepted()) showConsent();
    }
    @Override public void onPause() {
        if (TapService.stateObserver == stateObserver) TapService.stateObserver = null;
        if (TapService.configObserver == configObserver) TapService.configObserver = null;
        super.onPause();
    }

    private boolean busy() { return TapService.alive() != null && TapService.alive().isRunning(); }
    private boolean editable() {
        if (busy()) { toast("请先停止运行，再修改配置"); return false; }
        return true;
    }
    private void render() {
        unavailableMoves.clear();
        String problem = null;
        try { draft = store.load(ScreenInfo.read(this)); }
        catch (IllegalStateException e) { draft = null; problem = e.getMessage(); }

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245, 247, 251));
        LinearLayout page = Ui.column(this); int pad = Ui.dp(this, 20); page.setPadding(pad, pad, pad, pad);
        scroll.addView(page); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                page.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom);
            } else {
                page.setPadding(pad + insets.getSystemWindowInsetLeft(), pad + insets.getSystemWindowInsetTop(),
                    pad + insets.getSystemWindowInsetRight(), pad + insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        scroll.requestApplyInsets();
        scroll.post(() -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) controller.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            } else {
                int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (android.os.Build.VERSION.SDK_INT >= 27) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                getWindow().getDecorView().setSystemUiVisibility(flags);
            }
        });
        LinearLayout brand = new LinearLayout(this); brand.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.ic_launcher); icon.setContentDescription("循济图标");
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56));
        iconParams.rightMargin = Ui.dp(this, 14); brand.addView(icon, iconParams);
        LinearLayout brandText = Ui.column(this);
        brandText.addView(Ui.heading(this, "循济", 30));
        brandText.addView(Ui.text(this, "TongjiTap  /  0.2.0", 13, Ui.BLUE)); brand.addView(brandText); page.addView(brand); Ui.gap(page, 14);
        page.addView(Ui.text(this, "把重复点击交给一组清晰的步骤", 16, Ui.MUTED)); Ui.gap(page, 22);

        LinearLayout control = Ui.card(this);
        permission = Ui.text(this, "", 14, Ui.MUTED); control.addView(permission); Ui.gap(control, 8);
        status = Ui.heading(this, "", 17); control.addView(status); Ui.gap(control, 8);
        control.addView(Ui.button(this, "打开悬浮控制面板", true, v -> openPanel()));
        control.addView(Ui.button(this, "辅助功能权限设置", false, v -> permissions())); page.addView(control);

        editor = Ui.column(this); page.addView(editor);
        if (problem != null) {
            LinearLayout error = Ui.card(this); error.addView(Ui.text(this, problem, 16, Color.rgb(180, 35, 35)));
            error.addView(Ui.button(this, "重设本地配置", false, v -> reset())); editor.addView(error);
        } else {
            LinearLayout repeat = Ui.card(this);
            repeat.addView(Ui.heading(this, "整组循环 " + draft.loops + " 次", 20));
            repeat.addView(Ui.text(this, "依次执行全部步骤，计为一次循环。", 14, Ui.MUTED));
            repeat.addView(Ui.button(this, "设置循环次数", false, v -> editLoops())); editor.addView(repeat);

            editor.addView(Ui.heading(this, "点击步骤", 22)); Ui.gap(editor, 8);
            ScreenInfo current = ScreenInfo.read(this);
            String screenText = "坐标基准 " + draft.width + " × " + draft.height + " px，方向 " + (draft.rotation * 90) + "°";
            editor.addView(Ui.text(this, screenText, 13, Ui.MUTED)); Ui.gap(editor, 8);
            if (!current.matches(draft)) {
                editor.addView(Ui.text(this, "屏幕尺寸或方向已变化。请清空旧步骤，再重新取点或输入坐标。", 15, Color.rgb(180, 35, 35)));
            }
            if (draft.steps.isEmpty()) {
                LinearLayout empty = Ui.card(this);
                empty.addView(Ui.heading(this, "从第一个点击点开始", 18)); Ui.gap(empty, 8);
                empty.addView(Ui.text(this, "手动添加坐标，或打开面板后切换到目标界面，点击“取点”添加位置。", 15, Ui.MUTED));
                editor.addView(empty);
            }
            for (int i = 0; i < draft.steps.size(); i++) addStepCard(i);
            editor.addView(Ui.button(this, "＋ 手动添加步骤", true, v -> editStep(-1)));
            editor.addView(Ui.button(this, "清空步骤并重新取点", false, v -> reset())); Ui.gap(editor, 14);
            addColorCard();
        }
        LinearLayout help = Ui.card(this);
        help.addView(Ui.heading(this, "开始之前", 18)); Ui.gap(help, 8);
        help.addView(Ui.text(this, "先在测试页面校准坐标。页面变化可能导致误触，毫秒参数不代表实时精度。", 14, Ui.MUTED));
        help.addView(Ui.button(this, "操作说明", false, v -> instructions()));
        help.addView(Ui.button(this, "用途、隐私与免责声明", false, v -> fullNotice())); page.addView(help);
        page.addView(Ui.text(this, "个人开发，非官方工具。配置仅保存在本机。", 12, Ui.MUTED));
        refreshStatus();
    }

    private void addStepCard(int index) {
        ClickStep step = draft.steps.get(index); LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, String.format(java.util.Locale.ROOT, "%02d   坐标 (%d, %d)", index + 1, step.x, step.y), 19)); Ui.gap(card, 6);
        card.addView(Ui.text(this, "按住 " + step.durationMs + " 毫秒，释放后等待 " + step.waitMs + " 毫秒", 14, Ui.MUTED));
        LinearLayout actions = new LinearLayout(this);
        Button edit = Ui.button(this, "编辑", false, v -> editStep(index));
        Button delete = Ui.button(this, "删除", false, v -> {
            if (!editable()) return;
            new AlertDialog.Builder(this).setTitle("删除第 " + (index + 1) + " 步？")
                .setMessage("只移除这个步骤。").setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    if (!editable()) return;
                    change(() -> draft.steps.remove(index));
                }).show();
        });
        actions.addView(edit, Ui.actionParams(this));
        actions.addView(delete, Ui.actionParams(this)); card.addView(actions);
        LinearLayout order = new LinearLayout(this);
        Button up = Ui.button(this, "上移", false, v -> move(index, -1)); up.setEnabled(index > 0);
        Button down = Ui.button(this, "下移", false, v -> move(index, 1)); down.setEnabled(index + 1 < draft.steps.size());
        if (index == 0) unavailableMoves.add(up);
        if (index + 1 == draft.steps.size()) unavailableMoves.add(down);
        order.addView(up, Ui.actionParams(this));
        order.addView(down, Ui.actionParams(this)); card.addView(order); editor.addView(card);
    }

    private void refreshStatus() {
        if (status == null) return;
        TapService service = TapService.alive();
        permission.setText(service == null ? "辅助功能未连接，点击前需要手动授权" : "辅助功能已连接，操作仍由你手动开始");
        status.setText(service == null ? "尚未开始" : service.status());
        Ui.enabled(editor, !busy());
        for (Button button : unavailableMoves) button.setEnabled(false);
    }

    private void addColorCard() {
        LinearLayout card = Ui.card(this); card.addView(Ui.heading(this, "颜色触发", 22)); Ui.gap(card, 8);
        ColorRule rule = draft.colorRule;
        String summary = rule == null || !rule.enabled ? "未开启" : "已开启：(" + rule.x + ", " + rule.y + ") 变为 "
            + rule.hex() + " 时，" + (rule.back ? "执行系统返回" : "点击 (" + rule.tapX + ", " + rule.tapY + ")");
        card.addView(Ui.text(this, summary, 15, Ui.INK)); Ui.gap(card, 6);
        card.addView(Ui.text(this, android.os.Build.VERSION.SDK_INT < 30 ? "需要 Android 11 及以上。当前系统可使用普通点击步骤。"
            : "连续检测到目标颜色后，暂停步骤并执行一次动作；画面恢复后继续。", 14, Ui.MUTED));
        Button configure = Ui.button(this, "设置颜色规则", false, v -> editColorRule());
        if (android.os.Build.VERSION.SDK_INT < 30) unavailableMoves.add(configure);
        card.addView(configure); editor.addView(card);
    }

    private EditText textField(LinearLayout parent, String label, String value) {
        parent.addView(Ui.text(this, label, 14, Ui.MUTED));
        EditText field = new EditText(this); field.setSingleLine(true); field.setText(value); field.setSelectAllOnFocus(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        field.setContentDescription(label); parent.addView(field); Ui.gap(parent, 10); return field;
    }

    private void editColorRule() {
        if (!editable() || android.os.Build.VERSION.SDK_INT < 30) return;
        if (!ScreenInfo.read(this).matches(draft)) { toast("屏幕已变化，请清空并重新设置坐标"); return; }
        ColorRule old = draft.colorRule;
        LinearLayout form = form();
        CheckBox enabled = new CheckBox(this); enabled.setText("启用颜色规则"); enabled.setChecked(old != null && old.enabled);
        enabled.setMinHeight(Ui.dp(this, 48)); form.addView(enabled);
        form.addView(Ui.text(this, "开启后会在本机读取屏幕截图中的指定像素，图片随即释放，不保存、不上传。请选页面稳定区域，避开状态栏和面板。", 14, Ui.MUTED)); Ui.gap(form, 12);
        LinearLayout fields = Ui.column(this); form.addView(fields);
        EditText x = number(fields, "检测点 X", String.valueOf(old == null ? draft.width / 2 : old.x));
        EditText y = number(fields, "检测点 Y", String.valueOf(old == null ? draft.height / 2 : old.y));
        EditText rgb = textField(fields, "目标颜色（#RRGGBB）", old == null ? "#FFFFFF" : old.hex());
        EditText tolerance = number(fields, "颜色容差（0～64）", String.valueOf(old == null ? 16 : old.tolerance));
        EditText confirmations = number(fields, "连续命中次数（2～10）", String.valueOf(old == null ? 2 : old.confirmations));
        EditText interval = number(fields, "检测间隔（500～10000 毫秒）", String.valueOf(old == null ? 500 : old.intervalMs));
        fields.addView(Ui.heading(this, "命中后执行", 17));
        RadioGroup action = new RadioGroup(this);
        RadioButton back = new RadioButton(this); back.setId(View.generateViewId()); back.setText("手机返回键"); action.addView(back);
        RadioButton tap = new RadioButton(this); tap.setId(View.generateViewId()); tap.setText("点击指定坐标"); action.addView(tap);
        action.check(old == null || old.back ? back.getId() : tap.getId()); fields.addView(action);
        LinearLayout tapFields = Ui.column(this); fields.addView(tapFields);
        EditText tapX = number(tapFields, "动作点击点 X", String.valueOf(old == null ? 0 : old.tapX));
        EditText tapY = number(tapFields, "动作点击点 Y", String.valueOf(old == null ? 0 : old.tapY));
        Runnable update = () -> { Ui.enabled(fields, enabled.isChecked()); tapFields.setVisibility(back.isChecked() ? View.GONE : View.VISIBLE); };
        enabled.setOnCheckedChangeListener((b, checked) -> update.run()); action.setOnCheckedChangeListener((g, id) -> update.run()); update.run();
        form.addView(Ui.text(this, "动作后等待 1 秒，再确认颜色已变化。持续约 12 秒未恢复会停止，不会反复按返回。也可通过悬浮面板的“检测点”和“动作点”选取坐标。", 13, Ui.MUTED));
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("颜色触发规则").setView(scroll)
            .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!editable()) return;
            try {
                Store.Draft updated = store.load(ScreenInfo.read(this));
                if (!ScreenInfo.read(this).matches(updated)) throw new IllegalArgumentException("屏幕已变化，请重新取点");
                if (!enabled.isChecked()) updated.colorRule = old == null ? null : new ColorRule(false, old.x, old.y, old.rgb,
                    old.tolerance, old.confirmations, old.intervalMs, old.back, old.tapX, old.tapY);
                else updated.colorRule = new ColorRule(true, parse(x, "检测点 X"), parse(y, "检测点 Y"), ColorRule.parseRgb(rgb.getText().toString()),
                    parse(tolerance, "颜色容差"), parse(confirmations, "连续命中次数"), parse(interval, "检测间隔"), back.isChecked(),
                    back.isChecked() ? 0 : parse(tapX, "动作点击点 X"), back.isChecked() ? 0 : parse(tapY, "动作点击点 Y"));
                store.save(updated); dialog.dismiss(); render();
            } catch (IllegalArgumentException | IllegalStateException e) { toast(e.getMessage()); }
        })); dialog.show();
    }

    private void move(int index, int delta) {
        if (!editable()) return;
        int to = index + delta;
        if (to < 0 || to >= draft.steps.size()) return;
        change(() -> Collections.swap(draft.steps, index, to));
    }
    private void change(Runnable edit) {
        try { edit.run(); store.save(draft); render(); }
        catch (IllegalArgumentException | IllegalStateException e) { toast(e.getMessage()); render(); }
    }
    private LinearLayout form() {
        LinearLayout form = Ui.column(this); int p = Ui.dp(this, 20); form.setPadding(p, p, p, p); return form;
    }
    private EditText number(LinearLayout parent, String label, String value) {
        parent.addView(Ui.text(this, label, 14, Ui.MUTED));
        EditText field = new EditText(this); field.setSingleLine(true); field.setText(value); field.setTextSize(18);
        field.setInputType(InputType.TYPE_CLASS_NUMBER); field.setSelectAllOnFocus(true); field.setMinHeight(Ui.dp(this, 48));
        field.setContentDescription(label); parent.addView(field); Ui.gap(parent, 10); return field;
    }
    private int parse(EditText field, String label) {
        String text = field.getText().toString().trim();
        if (!text.matches("[0-9]+")) throw new IllegalArgumentException("请填写" + label + "，仅支持整数");
        try { return Integer.parseInt(text); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(label + "数值过大"); }
    }
    private void editLoops() {
        if (!editable()) return;
        LinearLayout form = form(); EditText input = number(form, "整组循环次数（1～100000）", String.valueOf(draft.loops));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("循环次数").setView(form)
            .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!editable()) return;
            try {
                int loops = parse(input, "循环次数");
                if (loops < 1 || loops > 100000) throw new IllegalArgumentException("循环次数应为 1～100000");
                Store.Draft updated = store.load(ScreenInfo.read(this)); updated.loops = loops; store.save(updated);
                dialog.dismiss(); render();
            } catch (IllegalArgumentException | IllegalStateException e) { input.setError(e.getMessage()); }
        })); dialog.show();
    }
    private void editStep(int index) {
        if (!editable()) return;
        if (!ScreenInfo.read(this).matches(draft)) { toast("屏幕已变化，请清空旧步骤并重新取点"); return; }
        ClickStep old = index < 0 ? null : draft.steps.get(index);
        LinearLayout form = form();
        EditText x = number(form, "X 坐标（屏幕像素）", old == null ? "" : String.valueOf(old.x));
        EditText y = number(form, "Y 坐标（屏幕像素）", old == null ? "" : String.valueOf(old.y));
        EditText duration = number(form, "按住时长（1～10000 毫秒）", old == null ? "50" : String.valueOf(old.durationMs));
        EditText wait = number(form, "释放后等待（0～3600000 毫秒）", old == null ? "500" : String.valueOf(old.waitMs));
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(index < 0 ? "添加步骤" : "编辑第 " + (index + 1) + " 步")
            .setView(scroll).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!editable()) return;
            try {
                Store.Draft updated = store.load(ScreenInfo.read(this));
                if (!ScreenInfo.read(this).matches(updated)) throw new IllegalArgumentException("屏幕已变化，请重新取点");
                ClickStep step = new ClickStep(parse(x, "X 坐标"), parse(y, "Y 坐标"), parse(duration, "按住时长"), parse(wait, "等待时间"));
                if (index < 0) updated.steps.add(step); else updated.steps.set(index, step);
                store.save(updated); dialog.dismiss(); render();
            } catch (IllegalArgumentException | IllegalStateException e) { toast(e.getMessage()); }
        })); dialog.show();
    }
    private void reset() {
        if (!editable()) return;
        new AlertDialog.Builder(this).setTitle("清空步骤并重设坐标基准？")
            .setMessage("删除已保存的步骤和颜色规则，使用当前屏幕尺寸和方向重新取点。这个操作无法撤销。")
            .setNegativeButton("取消", null).setPositiveButton("清空并重设", (d, w) -> {
                if (!editable()) return;
                try {
                    Store.Draft fresh = new Store.Draft(ScreenInfo.read(this));
                    if (draft != null) fresh.loops = draft.loops;
                    store.save(fresh); render();
                } catch (IllegalStateException e) { toast(e.getMessage()); }
            }).show();
    }
    private void openPanel() {
        if (!store.accepted()) { showConsent(); return; }
        if (draft == null) { toast("请先重设损坏的配置"); return; }
        TapService service = TapService.alive();
        if (service == null) { permissions(); return; }
        if (!store.runNoticeSeen()) {
            new AlertDialog.Builder(this).setTitle("首次运行提醒").setMessage(Notices.FIRST_RUN)
                .setNegativeButton("取消", null).setPositiveButton("了解并打开面板", (d, w) -> {
                    try { store.markRunNotice(); service.showPanel(); }
                    catch (IllegalStateException e) { toast(e.getMessage()); }
                }).show();
        } else service.showPanel();
    }
    private void permissions() {
        new AlertDialog.Builder(this).setTitle("开启前请了解权限")
            .setMessage("辅助功能允许循济在其他应用上模拟点击和执行返回。开启颜色规则时会读取屏幕截图，只在内存中采样指定像素，不保存、不上传。应用不联网。\n\n进入系统设置后，找到“循济”并自行开启。部分系统会限制侧载应用的辅助功能，请仅在确认安装来源可信时，按系统提供的选项操作。\n\n可随时在同一设置中关闭服务。")
            .setNegativeButton("取消", null).setPositiveButton("前往系统设置", (d, w) -> {
                try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                catch (android.content.ActivityNotFoundException e) { toast("无法打开设置，请手动进入系统辅助功能"); }
            }).show();
    }
    private View noticeView(boolean checkbox) {
        LinearLayout content = form(); TextView text = Ui.text(this, Notices.FULL, 15, Ui.INK);
        text.setTextIsSelectable(true); content.addView(text);
        if (checkbox) {
            CheckBox check = new CheckBox(this); check.setId(android.R.id.checkbox);
            check.setText("我已阅读并理解功能、权限和风险说明"); check.setMinHeight(Ui.dp(this, 48)); content.addView(check);
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(content); return scroll;
    }
    private void showConsent() {
        if (consentShowing || isFinishing()) return; consentShowing = true;
        View content = noticeView(true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("使用循济前")
            .setView(content).setCancelable(false).setNegativeButton("退出", (d, w) -> finish())
            .setPositiveButton("同意并进入", null).create();
        dialog.setOnDismissListener(d -> consentShowing = false);
        dialog.setOnShowListener(d -> {
            Button agree = dialog.getButton(AlertDialog.BUTTON_POSITIVE); agree.setEnabled(false);
            CheckBox check = content.findViewById(android.R.id.checkbox);
            check.setOnCheckedChangeListener((button, checked) -> agree.setEnabled(checked));
            agree.setOnClickListener(v -> {
                try { store.accept(); dialog.dismiss(); render(); }
                catch (IllegalStateException e) { toast(e.getMessage()); }
            });
        }); dialog.show();
    }
    private void fullNotice() {
        new AlertDialog.Builder(this).setTitle("用途、隐私与免责声明").setView(noticeView(false)).setPositiveButton("关闭", null).show();
    }
    private void instructions() {
        new AlertDialog.Builder(this).setTitle("使用步骤")
            .setMessage("1. 阅读说明，自行开启辅助功能。\n\n2. 设置整组循环次数。手动添加步骤，或打开悬浮面板。\n\n3. 切换到目标界面，点击面板的“取点”，再点选位置。选点只记录坐标，不点击下方页面；编号确认后可继续取下一个点。\n\n4. 回到应用调整每步按住时长、释放后等待时间及顺序。\n\n5. 在目标界面移开遮挡坐标的面板，点击“开始”。3 秒倒计时后依次执行。最后一步的等待用于下一轮，最后一轮完成后直接结束。\n\n6. 随时点击“停止”；关闭面板也会停止。锁屏、屏幕变化或异常时会停止，重新运行从第 1 步开始。\n\n屏幕方向或尺寸变化后，请清空旧步骤并重新取点。普通页面变化无法自动识别，请自行检查。")
            .setPositiveButton("知道了", null).show();
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
