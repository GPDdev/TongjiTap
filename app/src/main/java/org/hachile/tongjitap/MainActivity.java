package org.hachile.tongjitap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;
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

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class MainActivity extends Activity {
    static final String QQ_GROUP = "934139029";
    static final String PROJECT_URL = "https://github.com/GPDdev/TongjiTap";
    private Store store;
    private Store.Draft draft;
    private TextView status, permission;
    private LinearLayout editor;
    private boolean consentShowing;
    private ScreenshotImport screenshotImport;
    private AppPreferences preferences;
    private StridePage stride;
    private int selectedTab, tutorialStep;
    private Bundle restoredState;
    private boolean resumed;
    private final ArrayList<Button> unavailableMoves = new ArrayList<>();
    private final Runnable stateObserver = this::refreshStatus;
    private final Runnable configObserver = this::render;

    @Override protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(AppPreferences.wrap(base)); }
    @Override public void onCreate(Bundle state) {
        setTheme(R.style.AppTheme); super.onCreate(state); L.init(this); Ui.apply(this); store = new Store(this);
        preferences = new AppPreferences(this); restoredState = state;
        if (state == null) preferences.launch();
        selectedTab = state == null ? getIntent().getIntExtra("tab", 0) : state.getInt("tab", 0);
        selectedTab = Math.max(0, Math.min(3, selectedTab));
        if (Intent.ACTION_SEND.equals(getIntent().getAction())) selectedTab = 1;
        tutorialStep = state == null ? 0 : state.getInt("tutorial", 0);
        screenshotImport = new ScreenshotImport(this, store, this::editable, this::render, state);
        render();
    }
    @Override public void onResume() {
        super.onResume(); resumed = true; TapService.stateObserver = stateObserver; TapService.configObserver = configObserver;
        render(); if (!store.accepted()) showConsent(); else maybeStar();
    }
    @Override public void onPause() {
        resumed = false;
        if (TapService.stateObserver == stateObserver) TapService.stateObserver = null;
        if (TapService.configObserver == configObserver) TapService.configObserver = null;
        super.onPause();
    }
    @Override public void onDestroy() {
        screenshotImport.close(); if (stride != null) stride.onDestroy(); super.onDestroy();
    }
    @Override protected void onStop() { if (stride != null) stride.onStop(); super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); screenshotImport.saveState(state); state.putInt("tab", selectedTab); state.putInt("tutorial", tutorialStep);
        if (stride != null) stride.onSaveInstanceState(state);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        screenshotImport.onResult(request, result, data);
        if (request == 100) { if (stride == null) stride = new StridePage(this, restoredState); stride.onActivityResult(request, result, data); }
    }

    private boolean busy() { return TapService.alive() != null && TapService.alive().isRunning(); }
    private boolean editable() {
        if (busy()) { toast(L.t("请先停止运行，再修改配置")); return false; }
        return true;
    }
    private void render() {
        status = permission = null; editor = null; unavailableMoves.clear();
        if (store.accepted() && !preferences.tutorialDone()) { renderTutorial(); return; }
        LinearLayout shell = Ui.column(this); shell.setBackgroundColor(Ui.BG);
        FrameLayout content = new FrameLayout(this); shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        View separator = new View(this); separator.setBackgroundColor(Ui.LINE); shell.addView(separator, new LinearLayout.LayoutParams(-1, Ui.dp(this, 1)));
        LinearLayout nav = new LinearLayout(this); nav.setBackgroundColor(Ui.SURFACE); nav.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        String[] labels = {L.t("循济"), L.t("随步"), L.t("跃点"), L.t("更多")};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i; LinearLayout item = Ui.column(this); item.setGravity(android.view.Gravity.CENTER);
            item.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6)); item.setClickable(true); item.setFocusable(true);
            item.setContentDescription(L.t(labels[i])); item.setSelected(selectedTab == i);
            item.setBackground(Ui.background(this, selectedTab == i ? (Ui.dark ? 0xff233a5c : 0xffe8efff) : Ui.SURFACE, 18));
            NavIcon icon = new NavIcon(this, i, selectedTab == i); item.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26)));
            TextView label = Ui.text(this, labels[i], selectedTab == i ? Ui.BLUE : Ui.MUTED); label.setGravity(android.view.Gravity.CENTER); item.addView(label);
            item.setOnClickListener(v -> { selectedTab = tab; render(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0); nav.addView(item, lp);
        }
        shell.addView(nav); installRoot(shell, labels[selectedTab]);
        if (selectedTab == 1 && store.accepted()) {
            if (stride == null) stride = new StridePage(this, restoredState);
            if (stride.root.getParent() != null) ((android.view.ViewGroup) stride.root.getParent()).removeView(stride.root);
            content.addView(stride.root); stride.applyScreenFlag(); if (resumed) stride.onStart();
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); LinearLayout page = Ui.column(this);
            int p = Ui.dp(this, 20); page.setPadding(p, p, p, p); scroll.addView(page); content.addView(scroll);
            if (selectedTab == 2) relayPage(page); else if (selectedTab == 3) morePage(page); else loopPage(page);
        }
    }
    private void installRoot(View root, String paneTitle) {
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()); view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        }); root.requestApplyInsets();
        root.post(() -> {
            androidx.core.view.ViewCompat.setAccessibilityPaneTitle(root, paneTitle);
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController c = getWindow().getInsetsController();
                int flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                if (c != null) c.setSystemBarsAppearance(Ui.dark ? 0 : flags, flags);
            } else getWindow().getDecorView().setSystemUiVisibility(Ui.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (android.os.Build.VERSION.SDK_INT >= 27 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0));
        });
    }
    private void heading(LinearLayout page, String title, String english, String description) {
        page.addView(Ui.title(this, title)); page.addView(Ui.text(this, english, Ui.BLUE)); Ui.gap(page, 10);
        page.addView(Ui.text(this, description, Ui.MUTED)); Ui.gap(page, 22);
    }
    private void loopPage(LinearLayout page) {
        String problem = null;
        try { draft = store.load(ScreenInfo.read(this)); } catch (IllegalStateException e) { draft = null; problem = e.getMessage(); }
        heading(page, L.t("循济"), "Loop", L.t("把重复点击交给一组清晰的步骤"));
        LinearLayout control = Ui.card(this);
        permission = Ui.text(this, "", Ui.MUTED); control.addView(permission);
        status = Ui.heading(this, ""); control.addView(status);
        control.addView(Ui.button(this, L.t("打开悬浮控制面板"), true, v -> openPanel()));
        control.addView(Ui.button(this, L.t("辅助功能权限设置"), false, v -> permissions())); page.addView(control);
        editor = Ui.column(this); page.addView(editor);
        if (problem != null) { editor.addView(Ui.text(this, problem, Ui.MUTED)); editor.addView(Ui.button(this, L.t("重设本地配置"), false, v -> reset())); refreshStatus(); return; }
        LinearLayout repeat = Ui.card(this); repeat.addView(Ui.heading(this, L.t("整组循环 ") + draft.loops + L.t(" 次")));
        repeat.addView(Ui.text(this, L.t("依次执行全部步骤，计为一次循环。"), Ui.MUTED));
        repeat.addView(Ui.button(this, L.t("设置循环次数"), false, v -> editLoops())); editor.addView(repeat);
        editor.addView(Ui.heading(this, L.t("点击步骤"))); Ui.gap(editor, 8);
        editor.addView(Ui.text(this, draft.width + " × " + draft.height + " px", Ui.MUTED)); Ui.gap(editor, 8);
        if (!ScreenInfo.read(this).matches(draft)) editor.addView(Ui.text(this, L.t("屏幕尺寸或方向已变化，请清空并重新取点。"), Ui.MUTED));
        if (draft.steps.isEmpty()) { LinearLayout empty = Ui.card(this); empty.addView(Ui.heading(this, L.t("从第一个点击点开始"))); empty.addView(Ui.text(this, L.t("截取目标页面，导入截图后点选按钮的位置。"), Ui.MUTED)); editor.addView(empty); }
        for (int i = 0; i < draft.steps.size(); i++) addStepCard(i);
        editor.addView(Ui.button(this, L.t("从截图添加步骤"), true, v -> screenshotImport.open(ScreenshotImport.STEP)));
        editor.addView(Ui.button(this, L.t("悬浮窗选点"), false, v -> {
            if (!editable()) return; TapService service = TapService.alive(); if (service == null) permissions(); else service.showSelectionPanel();
        }));
        editor.addView(Ui.button(this, L.t("清空步骤并重新取点"), false, v -> reset())); Ui.gap(editor, 18); addColorCard();
        page.addView(Ui.button(this, L.t("操作说明"), false, v -> instructions())); refreshStatus();
    }
    private void relayPage(LinearLayout page) {
        heading(page, L.t("跃点"), "Relay", L.t("绿点位移与里程增量，同时确认后切换网络"));
        RelayConfig c = new RelayConfig(this); boolean supported = android.os.Build.VERSION.SDK_INT >= 30;
        LinearLayout intro = Ui.card(this); intro.addView(Ui.heading(this, L.t("开始之前")));
        intro.addView(Ui.text(this, L.t("开启 Wi-Fi 和移动数据；在系统设置中连接 TJ-WIFI，保存密码并开启自动连接。"), Ui.INK));
        intro.addView(Ui.text(this, L.t("应用不读取或保存校园网密码。关闭 Wi-Fi 后可能使用移动数据。"), Ui.MUTED));
        intro.addView(Ui.button(this, L.t("打开 Wi-Fi 设置"), false, v -> openSettings(Settings.ACTION_WIFI_SETTINGS)));
        intro.addView(Ui.button(this, L.t("打开移动网络设置"), false, v -> openSettings(Settings.ACTION_WIRELESS_SETTINGS))); page.addView(intro);
        LinearLayout calibrate = Ui.card(this); calibrate.addView(Ui.heading(this, L.t("截图校准")));
        calibrate.addView(Ui.text(this, L.t("使用运动中的完整截图。地图区域避开按钮，里程区域只保留数字和单位。"), Ui.MUTED));
        String[] names = {L.t("框选地图区域"), L.t("框选里程区域"), L.t("选择 Wi-Fi 开关")};
        boolean[] ready = {!c.map.isEmpty(), !c.distance.isEmpty(), c.wifiX >= 0};
        for (int i = 0; i < 3; i++) {
            final int kind = ScreenshotImport.MAP + i;
            Button b = Ui.button(this, names[i] + (ready[i] && c.matches(ScreenInfo.read(this)) ? "  ✓" : ""), false, v -> screenshotImport.open(kind)); b.setEnabled(supported && !busy()); calibrate.addView(b);
        }
        calibrate.addView(Ui.button(this, L.t("位移阈值：") + c.jumpPercent + "%", false, v -> {
            if (!editable()) return; String[] choices = {"10%", "15%", "20%", "25%"};
            Ui.dialog(this, L.t("位移占地图区域的比例")).setItems(choices, (d, which) -> { c.jumpPercent = 10 + which * 5; c.save(this, ScreenInfo.read(this)); render(); }).show();
        })); page.addView(calibrate);
        LinearLayout run = Ui.card(this); run.addView(Ui.heading(this, L.t("双条件检测")));
        run.addView(Ui.text(this, L.t("单次里程增加超过 0.02 km，且绿点明显移动；下一帧再次确认后，执行一次 Wi-Fi 开关。"), Ui.INK));
        run.addView(Ui.text(this, L.t("每次切换后重新建立基准。识别模糊时等待；连续识别失败、锁屏或切换失败时停止。"), Ui.MUTED));
        status = Ui.text(this, TapService.alive() == null ? L.t("尚未开始") : TapService.alive().status(), Ui.MUTED); run.addView(status);
        Button start = Ui.button(this, supported ? L.t("打开跃点控制面板") : L.t("需要 Android 11 或更高版本"), true, v -> prepareRelay()); start.setEnabled(supported && c.ready(ScreenInfo.read(this)) && !busy()); run.addView(start); page.addView(run);
    }
    private void prepareRelay() {
        if (!editable()) return; TapService service = TapService.alive(); if (service == null) { permissions(); return; }
        LinearLayout content = form(); content.addView(Ui.text(this, L.t("每次开始前，请确认以下设置。应用无法核实校园网密码和自动连接设置。"), Ui.MUTED));
        CheckBox data = new CheckBox(this); data.setText(L.t("已开启 Wi-Fi 和移动数据")); data.setTextSize(Ui.BODY_SP); content.addView(data);
        CheckBox campus = new CheckBox(this); campus.setText(L.t("TJ-WIFI 已保存密码并开启自动连接")); campus.setTextSize(Ui.BODY_SP); content.addView(campus);
        AlertDialog dialog = Ui.dialog(this, L.t("运行准备")).setView(content).setNegativeButton(L.t("取消"), null).setPositiveButton(L.t("打开面板"), null).create();
        dialog.setOnShowListener(d -> {
            Button b = dialog.getButton(AlertDialog.BUTTON_POSITIVE); b.setEnabled(false);
            android.widget.CompoundButton.OnCheckedChangeListener check = (v, checked) -> b.setEnabled(data.isChecked() && campus.isChecked()); data.setOnCheckedChangeListener(check); campus.setOnCheckedChangeListener(check);
            b.setOnClickListener(v -> {
                if (!getApplicationContext().getSystemService(android.net.wifi.WifiManager.class).isWifiEnabled()) { toast(L.t("请先开启 Wi-Fi 和移动数据")); return; }
                dialog.dismiss(); service.showRelayPanel();
            });
        }); dialog.show();
    }
    private void morePage(LinearLayout page) {
        heading(page, L.t("更多"), "More", L.t("让循济更合你的习惯"));
        LinearLayout about = Ui.card(this); about.addView(Ui.heading(this, L.t("循济 / TongjiTap"))); about.addView(Ui.text(this, L.t("当前版本 ") + BuildConfig.VERSION_NAME, Ui.MUTED));
        TextView history = Ui.text(this, ChangeLog.TEXT, Ui.INK); history.setVisibility(View.GONE); history.setTextIsSelectable(true);
        Button expand = Ui.button(this, L.t("展开完整更新日志"), false, v -> { boolean show = history.getVisibility() != View.VISIBLE; history.setVisibility(show ? View.VISIBLE : View.GONE); ((Button) v).setText(show ? L.t("收起更新日志") : L.t("展开完整更新日志")); }); about.addView(expand); about.addView(history); page.addView(about);
        LinearLayout preferencesCard = Ui.card(this); preferencesCard.addView(Ui.heading(this, L.t("偏好设置")));
        preferencesCard.addView(Ui.button(this, L.t("语言 / Language"), false, v -> {
            if (!editable()) return;
            Ui.dialog(this, L.t("语言 / Language")).setItems(new String[]{"简体中文", "English", "日本語", "Deutsch"}, (d, i) -> { preferences.language(new String[]{"zh", "en", "ja", "de"}[i]); recreate(); }).show();
        }));
        preferencesCard.addView(Ui.button(this, Ui.dark ? L.t("外观：黑夜模式") : L.t("外观：白天模式"), false, v -> {
            if (!editable()) return;
            Ui.dialog(this, L.t("外观")).setItems(new String[]{L.t("白天模式"), L.t("黑夜模式")}, (d, i) -> { preferences.theme(i == 0 ? "light" : "dark"); recreate(); }).show();
        }));
        preferencesCard.addView(Ui.button(this, L.t("重新查看教程"), false, v -> { tutorialStep = 0; renderTutorial(); }));
        preferencesCard.addView(Ui.button(this, L.t("隐私政策与使用说明"), false, v -> fullNotice())); page.addView(preferencesCard);
        LinearLayout community = Ui.card(this); community.addView(Ui.heading(this, L.t("交流与反馈")));
        community.addView(Ui.button(this, L.t("QQ 交流群：") + QQ_GROUP, true, v -> copyGroup()));
        community.addView(Ui.button(this, L.t("GitHub 项目主页"), false, v -> openUrl(PROJECT_URL)));
        community.addView(Ui.button(this, L.t("开发者个人主页"), false, v -> openUrl("https://home.hachile.org"))); page.addView(community);
        LinearLayout apps = Ui.card(this); apps.addView(Ui.heading(this, L.t("更多推荐应用")));
        apps.addView(Ui.button(this, L.t("今刻 / Jinke"), false, v -> openUrl("https://github.com/GPDdev/jinke")));
        apps.addView(Ui.button(this, "LiveCue", false, v -> openUrl("https://github.com/GPDdev/LiveCue")));
        apps.addView(Ui.button(this, "ScreenAnswer", false, v -> openUrl("https://github.com/GPDdev/screenanswer"))); page.addView(apps);
        page.addView(Ui.button(this, "Powered by GPDdev", false, v -> openUrl(PROJECT_URL)));
    }
    private void renderTutorial() {
        String[] titles = {L.t("欢迎使用循济"), L.t("循济 / Loop"), L.t("随步 / Stride"), L.t("跃点 / Relay"), L.t("更多 / More"), L.t("一起完善循济")};
        String[] descriptions = {
            L.t("接下来用几步了解主要功能。设置保存在本机，辅助功能需要你主动授权。"),
            L.t("导入目标页面截图，点选点击位置；设置顺序、按住时间、等待时间和循环次数。颜色触发可以返回或点击恢复位置。默认悬浮面板只有运行控制，选取悬浮窗选点后才显示取点按钮。"),
            L.t("导入本地音频或网易云公开歌单。单指滑动调节倍速，可设置方向、灵敏度、倍速范围、锁定手势和变速不变调。支持后台播放与耳机控制。在线音乐可能消耗流量，也可能受平台限制。"),
            L.t("先开启 Wi-Fi、移动数据，并设置 TJ-WIFI 自动连接。从截图框选地图、里程，再校准快捷面板的 Wi-Fi 按钮。绿点明显移动且里程单次增加超过 0.02 km 时，确认后切换网络。需要 Android 11；厂商面板和页面变化可能使操作失效。"),
            L.t("查看完整更新日志，切换中文、英语、日语、德语与明暗主题。这里也有项目、开发者主页和其他应用入口。"),
            L.t("遇到问题或有新想法，欢迎加入 QQ 交流群。点击下方群号即可复制。")};
        LinearLayout root = Ui.column(this); root.setBackgroundColor(Ui.BG); FrameLayout body = new FrameLayout(this); root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView scroll = new ScrollView(this); LinearLayout page = form(); scroll.addView(page); body.addView(scroll);
        Ui.gap(page, 32); ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.ic_launcher); page.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 80), Ui.dp(this, 80)));
        Ui.gap(page, 28); page.addView(Ui.text(this, (tutorialStep + 1) + " / " + titles.length, Ui.BLUE)); Ui.gap(page, 12);
        page.addView(Ui.title(this, titles[tutorialStep])); Ui.gap(page, 18); page.addView(Ui.text(this, descriptions[tutorialStep], Ui.INK));
        if (tutorialStep == 5) page.addView(Ui.button(this, L.t("QQ 交流群：") + QQ_GROUP, true, v -> copyGroup()));
        LinearLayout actions = form();
        actions.addView(Ui.button(this, tutorialStep == 5 ? L.t("开始使用") : L.t("下一步"), true, v -> {
            if (tutorialStep < 5) { tutorialStep++; renderTutorial(); }
            else { preferences.finishTutorial(); selectedTab = 0; render(); maybeStar(); }
        }));
        if (tutorialStep > 0) actions.addView(Ui.button(this, L.t("上一步"), false, v -> { tutorialStep--; renderTutorial(); }));
        root.addView(actions); installRoot(root, titles[tutorialStep]);
    }
    private void maybeStar() {
        if (!store.accepted() || !preferences.tutorialDone() || !preferences.starDue() || isFinishing()) return;
        preferences.starShown();
        Ui.dialog(this, L.t("喜欢循济的话，给个 Star 吧"))
            .setMessage(L.t("你已经打开循济 5 次了。如果它帮到了你，欢迎到 GitHub 点一颗 Star，支持继续开发。谢谢！"))
            .setNegativeButton(L.t("下次再说"), null).setPositiveButton(L.t("前往 GitHub"), (d, w) -> openUrl(PROJECT_URL)).show();
    }
    private void copyGroup() { getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("TongjiTap QQ", QQ_GROUP)); toast(L.t("已复制 QQ 群号 ") + QQ_GROUP); }
    private void openUrl(String url) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (android.content.ActivityNotFoundException e) { toast(L.t("未找到可打开链接的应用")); } }
    private void openSettings(String action) { try { startActivity(new Intent(action)); } catch (android.content.ActivityNotFoundException e) { toast(L.t("无法打开设置，请手动进入系统设置")); } }

    private void addStepCard(int index) {
        ClickStep step = draft.steps.get(index); LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, String.format(java.util.Locale.ROOT, L.t("%02d   坐标 (%d, %d)"), index + 1, step.x, step.y))); Ui.gap(card, 6);
        card.addView(Ui.text(this, L.t("按住 ") + step.durationMs + L.t(" 毫秒，释放后等待 ") + step.waitMs + L.t(" 毫秒"), Ui.MUTED));
        LinearLayout actions = new LinearLayout(this);
        Button edit = Ui.button(this, L.t("编辑"), false, v -> editStep(index));
        Button delete = Ui.button(this, L.t("删除"), false, v -> {
            if (!editable()) return;
            Ui.dialog(this, L.t("删除第 ") + (index + 1) + L.t(" 步？"))
                .setMessage(L.t("只移除这个步骤。")).setNegativeButton(L.t("取消"), null)
                .setPositiveButton(L.t("删除"), (d, w) -> {
                    if (!editable()) return;
                    change(() -> draft.steps.remove(index));
                }).show();
        });
        actions.addView(edit, Ui.actionParams(this));
        actions.addView(delete, Ui.actionParams(this)); card.addView(actions);
        LinearLayout order = new LinearLayout(this);
        Button up = Ui.button(this, L.t("上移"), false, v -> move(index, -1)); up.setEnabled(index > 0);
        Button down = Ui.button(this, L.t("下移"), false, v -> move(index, 1)); down.setEnabled(index + 1 < draft.steps.size());
        if (index == 0) unavailableMoves.add(up);
        if (index + 1 == draft.steps.size()) unavailableMoves.add(down);
        order.addView(up, Ui.actionParams(this));
        order.addView(down, Ui.actionParams(this)); card.addView(order); editor.addView(card);
    }

    private void refreshStatus() {
        if (status == null) return;
        TapService service = TapService.alive();

        status.setText(service == null ? L.t("尚未开始") : service.status());
        if (permission != null) permission.setText(service == null ? L.t("辅助功能未连接") : L.t("辅助功能已连接"));
        if (editor != null) Ui.enabled(editor, !busy());
        for (Button button : unavailableMoves) button.setEnabled(false);
    }

    private void addColorCard() {
        LinearLayout card = Ui.card(this); card.addView(Ui.heading(this, L.t("颜色触发"))); Ui.gap(card, 8);
        ColorRule rule = draft.colorRule;
        String summary = rule == null || !rule.enabled ? L.t("未开启") : L.t("已开启：(") + rule.x + ", " + rule.y + L.t(") 变为 ")
            + rule.hex() + L.t(" 时，") + (rule.back ? L.t("执行系统返回") : L.t("点击 (") + rule.tapX + ", " + rule.tapY + ")");
        card.addView(Ui.text(this, summary, Ui.INK)); Ui.gap(card, 6);
        card.addView(Ui.text(this, android.os.Build.VERSION.SDK_INT < 30 ? L.t("需要 Android 11 及以上。当前系统可使用普通点击步骤。")
            : L.t("连续检测到目标颜色后，暂停步骤并执行一次动作；画面恢复后继续。"), Ui.MUTED));
        Button configure = Ui.button(this, L.t("设置颜色规则"), false, v -> editColorRule());
        Button fromScreenshot = Ui.button(this, L.t("从截图选点取色"), true, v -> screenshotImport.open());
        if (android.os.Build.VERSION.SDK_INT < 30) { unavailableMoves.add(configure); unavailableMoves.add(fromScreenshot); }
        card.addView(fromScreenshot); card.addView(configure);
        Button actionPoint = Ui.button(this, L.t("从截图选择动作点"), false, v -> screenshotImport.open(ScreenshotImport.ACTION));
        actionPoint.setEnabled(android.os.Build.VERSION.SDK_INT >= 30); if (android.os.Build.VERSION.SDK_INT < 30) unavailableMoves.add(actionPoint); card.addView(actionPoint); editor.addView(card);
    }

    private EditText textField(LinearLayout parent, String label, String value) {
        parent.addView(Ui.text(this, label, Ui.MUTED));
        EditText field = new EditText(this); field.setSingleLine(true); field.setText(value); field.setSelectAllOnFocus(true); field.setTextSize(Ui.BODY_SP);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        field.setContentDescription(label); parent.addView(field); Ui.gap(parent, 10); return field;
    }

    private void editColorRule() {
        if (!editable() || android.os.Build.VERSION.SDK_INT < 30) return;
        if (!ScreenInfo.read(this).matches(draft)) { toast(L.t("屏幕已变化，请清空并重新设置坐标")); return; }
        ColorRule old = draft.colorRule;
        LinearLayout form = form();
        CheckBox enabled = new CheckBox(this); enabled.setText(L.t("启用颜色规则")); enabled.setTextSize(Ui.BODY_SP); enabled.setChecked(old != null && old.enabled);
        enabled.setMinHeight(Ui.dp(this, 48)); form.addView(enabled);
        form.addView(Ui.text(this, L.t("开启后会在本机读取屏幕截图中的指定像素，图片随即释放，不保存、不上传。请选页面稳定区域，避开状态栏和面板。"), Ui.MUTED)); Ui.gap(form, 12);
        LinearLayout fields = Ui.column(this); form.addView(fields);
        EditText x = number(fields, L.t("检测点 X"), String.valueOf(old == null ? draft.width / 2 : old.x));
        EditText y = number(fields, L.t("检测点 Y"), String.valueOf(old == null ? draft.height / 2 : old.y));
        EditText rgb = textField(fields, L.t("目标颜色（#RRGGBB）"), old == null ? "#FFFFFF" : old.hex());
        EditText tolerance = number(fields, L.t("颜色容差（0～64）"), String.valueOf(old == null ? 16 : old.tolerance));
        EditText confirmations = number(fields, L.t("连续命中次数（2～10）"), String.valueOf(old == null ? 2 : old.confirmations));
        EditText interval = number(fields, L.t("检测间隔（500～10000 毫秒）"), String.valueOf(old == null ? 500 : old.intervalMs));
        fields.addView(Ui.heading(this, L.t("命中后执行")));
        RadioGroup action = new RadioGroup(this);
        RadioButton back = new RadioButton(this); back.setId(View.generateViewId()); back.setText(L.t("手机返回键")); back.setTextSize(Ui.BODY_SP); action.addView(back);
        RadioButton tap = new RadioButton(this); tap.setId(View.generateViewId()); tap.setText(L.t("点击指定坐标")); tap.setTextSize(Ui.BODY_SP); action.addView(tap);
        action.check(old == null || old.back ? back.getId() : tap.getId()); fields.addView(action);
        LinearLayout tapFields = Ui.column(this); fields.addView(tapFields);
        EditText tapX = number(tapFields, L.t("动作点击点 X"), String.valueOf(old == null ? 0 : old.tapX));
        EditText tapY = number(tapFields, L.t("动作点击点 Y"), String.valueOf(old == null ? 0 : old.tapY));
        Runnable update = () -> { Ui.enabled(fields, enabled.isChecked()); tapFields.setVisibility(back.isChecked() ? View.GONE : View.VISIBLE); };
        enabled.setOnCheckedChangeListener((b, checked) -> update.run()); action.setOnCheckedChangeListener((g, id) -> update.run()); update.run();
        form.addView(Ui.text(this, L.t("动作后等待 1 秒，再确认颜色已变化。持续约 12 秒未恢复会停止，不会反复按返回。也可通过悬浮面板的“检测点”和“动作点”选取坐标。"), Ui.MUTED));
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = Ui.dialog(this, L.t("颜色触发规则")).setView(scroll)
            .setNegativeButton(L.t("取消"), null).setPositiveButton(L.t("保存"), null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!editable()) return;
            try {
                Store.Draft updated = store.load(ScreenInfo.read(this));
                if (!ScreenInfo.read(this).matches(updated)) throw new IllegalArgumentException(L.t("屏幕已变化，请重新取点"));
                if (!enabled.isChecked()) updated.colorRule = old == null ? null : new ColorRule(false, old.x, old.y, old.rgb,
                    old.tolerance, old.confirmations, old.intervalMs, old.back, old.tapX, old.tapY);
                else updated.colorRule = new ColorRule(true, parse(x, L.t("检测点 X")), parse(y, L.t("检测点 Y")), ColorRule.parseRgb(rgb.getText().toString()),
                    parse(tolerance, L.t("颜色容差")), parse(confirmations, L.t("连续命中次数")), parse(interval, L.t("检测间隔")), back.isChecked(),
                    back.isChecked() ? 0 : parse(tapX, L.t("动作点击点 X")), back.isChecked() ? 0 : parse(tapY, L.t("动作点击点 Y")));
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
        parent.addView(Ui.text(this, label, Ui.MUTED));
        EditText field = new EditText(this); field.setSingleLine(true); field.setText(value); field.setTextSize(Ui.BODY_SP);
        field.setInputType(InputType.TYPE_CLASS_NUMBER); field.setSelectAllOnFocus(true); field.setMinHeight(Ui.dp(this, 48));
        field.setContentDescription(label); parent.addView(field); Ui.gap(parent, 10); return field;
    }
    private int parse(EditText field, String label) {
        String text = field.getText().toString().trim();
        if (!text.matches("[0-9]+")) throw new IllegalArgumentException(L.t("请填写") + label + L.t("，仅支持整数"));
        try { return Integer.parseInt(text); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(label + L.t("数值过大")); }
    }
    private void editLoops() {
        if (!editable()) return;
        LinearLayout form = form(); EditText input = number(form, L.t("整组循环次数（1～100000）"), String.valueOf(draft.loops));
        AlertDialog dialog = Ui.dialog(this, L.t("循环次数")).setView(form)
            .setNegativeButton(L.t("取消"), null).setPositiveButton(L.t("保存"), null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!editable()) return;
            try {
                int loops = parse(input, L.t("循环次数"));
                if (loops < 1 || loops > 100000) throw new IllegalArgumentException(L.t("循环次数应为 1～100000"));
                Store.Draft updated = store.load(ScreenInfo.read(this)); updated.loops = loops; store.save(updated);
                dialog.dismiss(); render();
            } catch (IllegalArgumentException | IllegalStateException e) { input.setError(e.getMessage()); }
        })); dialog.show();
    }
    private void editStep(int index) {
        if (!editable()) return;
        if (!ScreenInfo.read(this).matches(draft)) { toast(L.t("屏幕已变化，请清空旧步骤并重新取点")); return; }
        ClickStep old = index < 0 ? null : draft.steps.get(index);
        LinearLayout form = form();
        EditText x = number(form, L.t("X 坐标（屏幕像素）"), old == null ? "" : String.valueOf(old.x));
        EditText y = number(form, L.t("Y 坐标（屏幕像素）"), old == null ? "" : String.valueOf(old.y));
        EditText duration = number(form, L.t("按住时长（1～10000 毫秒）"), old == null ? "50" : String.valueOf(old.durationMs));
        EditText wait = number(form, L.t("释放后等待（0～3600000 毫秒）"), old == null ? "500" : String.valueOf(old.waitMs));
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = Ui.dialog(this, index < 0 ? L.t("添加步骤") : L.t("编辑第 ") + (index + 1) + L.t(" 步"))
            .setView(scroll).setNegativeButton(L.t("取消"), null).setPositiveButton(L.t("保存"), null).create();
        form.addView(Ui.button(this, L.t("从截图重新选点"), false, v -> {
            dialog.dismiss(); screenshotImport.open(ScreenshotImport.STEP, index);
        }));
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!editable()) return;
            try {
                Store.Draft updated = store.load(ScreenInfo.read(this));
                if (!ScreenInfo.read(this).matches(updated)) throw new IllegalArgumentException(L.t("屏幕已变化，请重新取点"));
                ClickStep step = new ClickStep(parse(x, L.t("X 坐标")), parse(y, L.t("Y 坐标")), parse(duration, L.t("按住时长")), parse(wait, L.t("等待时间")));
                if (index < 0) updated.steps.add(step); else updated.steps.set(index, step);
                store.save(updated); dialog.dismiss(); render();
            } catch (IllegalArgumentException | IllegalStateException e) { toast(e.getMessage()); }
        })); dialog.show();
    }
    private void reset() {
        if (!editable()) return;
        Ui.dialog(this, L.t("清空步骤并重设坐标基准？"))
            .setMessage(L.t("删除已保存的步骤和颜色规则，使用当前屏幕尺寸和方向重新取点。这个操作无法撤销。"))
            .setNegativeButton(L.t("取消"), null).setPositiveButton(L.t("清空并重设"), (d, w) -> {
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
        if (draft == null) { toast(L.t("请先重设损坏的配置")); return; }
        TapService service = TapService.alive();
        if (service == null) { permissions(); return; }
        if (!store.runNoticeSeen()) {
            Ui.dialog(this, L.t("首次运行提醒")).setMessage(Notices.FIRST_RUN)
                .setNegativeButton(L.t("取消"), null).setPositiveButton(L.t("了解并打开面板"), (d, w) -> {
                    try { store.markRunNotice(); service.showPanel(); }
                    catch (IllegalStateException e) { toast(e.getMessage()); }
                }).show();
        } else service.showPanel();
    }
    private void permissions() {
        Ui.dialog(this, L.t("开启前请了解权限"))
            .setMessage(Notices.PERMISSION)
            .setNegativeButton(L.t("取消"), null).setPositiveButton(L.t("前往系统设置"), (d, w) -> {
                try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                catch (android.content.ActivityNotFoundException e) { toast(L.t("无法打开设置，请手动进入系统辅助功能")); }
            }).show();
    }
    private View noticeView(boolean checkbox) {
        LinearLayout content = form(); TextView text = Ui.text(this, Notices.FULL, Ui.INK);
        text.setTextIsSelectable(true); content.addView(text);
        if (checkbox) {
            CheckBox check = new CheckBox(this); check.setId(android.R.id.checkbox);
            check.setTextSize(Ui.BODY_SP);
            check.setText(L.t("我已阅读并理解功能、权限和风险说明")); check.setMinHeight(Ui.dp(this, 48)); content.addView(check);
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(content); return scroll;
    }
    private void showConsent() {
        if (consentShowing || isFinishing()) return; consentShowing = true;
        View content = noticeView(true);
        AlertDialog dialog = Ui.dialog(this, L.t("使用循济前"))
            .setView(content).setCancelable(false).setNegativeButton(L.t("退出"), (d, w) -> finish())
            .setPositiveButton(L.t("同意并进入"), null).create();
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
        Ui.dialog(this, L.t("用途、隐私与免责声明")).setView(noticeView(false)).setPositiveButton(L.t("关闭"), null).show();
    }
    private void instructions() {
        Ui.dialog(this, L.t("使用步骤"))
            .setMessage(Notices.INSTRUCTIONS)
            .setPositiveButton(L.t("知道了"), null).show();
    }
    private void toast(String message) { Toast.makeText(this, L.t(message), Toast.LENGTH_LONG).show(); }
}
