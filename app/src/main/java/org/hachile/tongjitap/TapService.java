package org.hachile.tongjitap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import java.lang.ref.WeakReference;

@android.annotation.SuppressLint("AppCompatCustomView") // Accessibility overlays use the platform theme, not AppCompat.
public final class TapService extends AccessibilityService {
    private static WeakReference<TapService> instance = new WeakReference<>(null);
    static Runnable stateObserver, configObserver;
    static TapService alive() { return instance.get(); }

    private final Handler engineHandler = new Handler(Looper.getMainLooper());
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private SequenceRunner runner;
    private ClickPlan activePlan;
    private ColorMonitor colorMonitor;
    private RelayMonitor relayMonitor;
    private boolean selectionMode, relayMode;
    private String foregroundPackage = "";
    private WindowManager windows;
    private Context themed;
    private Store store;
    private LinearLayout panel;
    private TextView panelStatus;
    private Button startButton, stopButton, pickButton, colorPickButton, actionPickButton;
    private View picker, marker;
    private WindowManager.LayoutParams panelParams;
    private String status = L.t("待命，尚未开始");
    private boolean receiverRegistered;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                cancelPicker(); removeMarker(); stopAll(L.t("屏幕已关闭，已停止"));
            }
        }
    };

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        windows = getSystemService(WindowManager.class); themed = new ContextThemeWrapper(AppPreferences.wrap(this), R.style.AppTheme);
        L.init(themed); Ui.apply(themed);
        store = new Store(this);
        runner = new SequenceRunner(new SequenceRunner.Scheduler() {
            public void post(Runnable task, long delayMs) { engineHandler.postDelayed(task, delayMs); }
            public void cancel(Runnable task) { engineHandler.removeCallbacks(task); }
            public void clear() { engineHandler.removeCallbacksAndMessages(null); }
        }, this::dispatchClick, (state, cycle, step, message) -> {
            if (state != SequenceRunner.State.COUNTDOWN && state != SequenceRunner.State.RUNNING) stopColorMonitor();
            status = message;
            if (activePlan != null && (state == SequenceRunner.State.COUNTDOWN || state == SequenceRunner.State.RUNNING))
                status += L.t("\n第 ") + cycle + "/" + activePlan.loops + L.t(" 轮，第 ") + step + "/" + activePlan.steps.size() + L.t(" 步");
            updatePanel(); notifyState();
        });
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenReceiver, filter);
            receiverRegistered = true;
        }
        instance = new WeakReference<>(this); notifyState();
    }

    boolean isRunning() { return loopRunning() || relayMonitor != null && relayMonitor.running(); }
    private boolean loopRunning() { return runner != null && runner.isRunning(); }
    boolean screenUnlocked() { return unlocked(); }
    android.view.accessibility.AccessibilityNodeInfo foregroundRoot() {
        // An accessibility overlay can be the active window after its Start button is pressed.
        // The focused application/system window identifies the page beneath our non-focusable panel.
        for (android.view.accessibility.AccessibilityWindowInfo window : getWindows()) {
            if (window.getType() != android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY && window.isFocused())
                return window.getRoot();
        }
        return getRootInActiveWindow();
    }
    boolean relayTargetVisible() {
        android.view.accessibility.AccessibilityNodeInfo root = foregroundRoot();
        if (root == null) return false;
        try {
            String name = String.valueOf(root.getPackageName());
            return "com.tencent.mm".equals(name) || BuildConfig.DEBUG && "org.hachile.tongjitap.fixture".equals(name);
        } finally { root.recycle(); }
    }
    void hideRelayPanel(boolean hide) { if (panel != null) panel.setVisibility(hide ? View.INVISIBLE : View.VISIBLE); }
    boolean relayPanelOverlaps(RelayConfig config) {
        if (panel == null || panel.getVisibility() != View.VISIBLE) return false;
        int[] location = new int[2]; panel.getLocationOnScreen(location);
        Rect r = new Rect(location[0], location[1], location[0]+panel.getWidth(), location[1]+panel.getHeight());
        return Rect.intersects(r,config.map) || Rect.intersects(r,config.distance);
    }
    private void stopAll(String reason) {
        if (runner != null && runner.isRunning()) runner.stop(reason);
        if (relayMonitor != null) { relayMonitor.stop(reason); relayMonitor = null; }
        status = reason; updatePanel(); notifyState();
    }
    String status() { return status; }
    private void notifyState() { if (stateObserver != null) stateObserver.run(); }

    void showPanel() { showPanel(false, false); }
    void showSelectionPanel() { showPanel(true, false); }
    void showRelayPanel() { showPanel(false, true); }
    private void showPanel(boolean selections, boolean relay) {
        if (alive() != this) { toast(L.t("辅助功能尚未连接，请返回应用重试")); return; }
        if (!store.accepted()) { toast(L.t("请先在应用中阅读并同意用途说明")); return; }
        if (panel != null && (selectionMode != selections || relayMode != relay)) {
            if (isRunning()) { toast(L.t("请先停止运行，再切换功能")); return; }
            closePanel();
        }
        selectionMode = selections; relayMode = relay;
        if (panel != null) { panel.setVisibility(View.VISIBLE); return; }
        themed = new ContextThemeWrapper(AppPreferences.wrap(this), R.style.AppTheme); L.init(themed); Ui.apply(themed);
        panel = Ui.column(themed); int pad = Ui.dp(this, 10); panel.setPadding(pad, pad, pad, pad);
        panel.setBackground(Ui.background(this, Ui.SURFACE, 16)); panel.setElevation(Ui.dp(this, 8));
        TextView handle = new TextView(themed) {
            @Override public boolean performClick() { super.performClick(); return true; }
        };
        handle.setText(L.t(relayMode ? L.t("跃点　移动面板") : L.t("循济　移动面板"))); handle.setTextColor(Ui.INK); handle.setTextSize(Ui.SUBTITLE_SP);
        handle.setGravity(Gravity.CENTER); handle.setMinHeight(Ui.dp(this, 48)); handle.setClickable(true);
        handle.setContentDescription(L.t("移动控制面板，点击左右移位，也可拖动位置"));
        handle.setOnClickListener(v -> {
            ScreenInfo screen = ScreenInfo.read(this);
            panelParams.x = panelParams.x < screen.width / 2 ? screen.width - panel.getWidth() - Ui.dp(this, 12) : Ui.dp(this, 12);
            repositionPanel();
        }); panel.addView(handle);
        panelStatus = Ui.text(themed, status, Ui.MUTED); panelStatus.setMinHeight(Ui.dp(this, 48)); panel.addView(panelStatus);
        LinearLayout row = new LinearLayout(themed);
        startButton = Ui.button(themed, L.t("开始"), true, v -> startRun());
        stopButton = Ui.button(themed, L.t("停止"), false, v -> {
            cancelPicker(); removeMarker(); stopAll(L.t("已手动停止"));
        });
        row.addView(startButton, Ui.actionParams(this));
        row.addView(stopButton, Ui.actionParams(this)); panel.addView(row);
        LinearLayout bottom = new LinearLayout(themed);
        pickButton = Ui.button(themed, L.t("取点"), false, v -> choosePoint());
        if (selectionMode) bottom.addView(pickButton, Ui.actionParams(this));
        bottom.addView(Ui.button(themed, L.t("关闭"), false, v -> closePanel()), Ui.actionParams(this));
        panel.addView(bottom);
        LinearLayout colors = new LinearLayout(themed);
        colorPickButton = Ui.button(themed, L.t("检测点"), false, v -> choosePoint(1));
        actionPickButton = Ui.button(themed, L.t("动作点"), false, v -> choosePoint(2));
        colors.addView(colorPickButton, Ui.actionParams(this)); colors.addView(actionPickButton, Ui.actionParams(this));
        if (selectionMode) panel.addView(colors);
        panelParams = layout(Ui.dp(this, 220), -2, false);
        panelParams.x = Ui.dp(this, 12); panelParams.y = Ui.dp(this, 100);
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int originalX, originalY; boolean moved;
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX(); downY = event.getRawY(); originalX = panelParams.x; originalY = panelParams.y; moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                        if (Math.hypot(dx, dy) > ViewConfiguration.get(TapService.this).getScaledTouchSlop()) moved = true;
                        if (moved) {
                            panelParams.x = originalX + Math.round(dx); panelParams.y = originalY + Math.round(dy); repositionPanel();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) view.performClick(); return true;
                    case MotionEvent.ACTION_CANCEL: return true;
                    default: return false;
                }
            }
        });
        try { windows.addView(panel, panelParams); updatePanel(); }
        catch (RuntimeException e) { panel = null; toast(L.t("无法显示控制面板，请检查辅助功能状态")); }
    }

    @android.annotation.SuppressLint("RtlHardcoded") // Pixel coordinates use the physical left edge in every locale.
    private WindowManager.LayoutParams layout(int width, int height, boolean untouchable) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (untouchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(width, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        if (Build.VERSION.SDK_INT >= 30) p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        else if (Build.VERSION.SDK_INT >= 28) p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        return p;
    }
    private void repositionPanel() {
        if (panel == null) return;
        ScreenInfo screen = ScreenInfo.read(this);
        panelParams.x = Math.max(0, Math.min(panelParams.x, screen.width - panel.getWidth()));
        panelParams.y = Math.max(Ui.dp(this, 28), Math.min(panelParams.y, screen.height - panel.getHeight() - Ui.dp(this, 28)));
        try { windows.updateViewLayout(panel, panelParams); }
        catch (RuntimeException e) { closePanel(); toast(L.t("控制面板已断开，请重新打开")); }
    }
    private void updatePanel() {
        if (panel == null) return;
        panelStatus.setText(status); startButton.setEnabled(!isRunning() && picker == null);
        pickButton.setEnabled(!isRunning() && picker == null); stopButton.setEnabled(isRunning() || picker != null);
        colorPickButton.setEnabled(Build.VERSION.SDK_INT >= 30 && !isRunning() && picker == null);
        actionPickButton.setEnabled(Build.VERSION.SDK_INT >= 30 && !isRunning() && picker == null);
    }
    private boolean unlocked() {
        return getSystemService(PowerManager.class).isInteractive() && !getSystemService(KeyguardManager.class).isKeyguardLocked();
    }
    private boolean environmentMatches() {
        ScreenInfo s = ScreenInfo.read(this);
        return activePlan != null && activePlan.matches(s.width, s.height, s.rotation) && unlocked();
    }

    private void startRun() {
        if (isRunning()) return;
        if (relayMode) { startRelay(); return; }
        removeMarker();
        try {
            if (!store.accepted() || !store.runNoticeSeen()) throw new IllegalStateException(L.t("请先在应用中阅读并确认用途和运行说明"));
            Store.Draft draft = store.load(ScreenInfo.read(this));
            activePlan = draft.plan();
            if (!environmentMatches()) throw new IllegalArgumentException(L.t("屏幕尺寸、方向变化或屏幕已锁定，请重新检查坐标"));
            Rect bounds = new Rect(); int[] location = new int[2]; panel.getLocationOnScreen(location);
            bounds.set(location[0], location[1], location[0] + panel.getWidth(), location[1] + panel.getHeight());
            if (bounds.isEmpty()) throw new IllegalStateException(L.t("面板正在显示，请稍后再试"));
            for (ClickStep step : activePlan.steps) {
                if (bounds.contains(step.x, step.y)) throw new IllegalArgumentException(L.t("控制面板遮挡了点击坐标，请拖动移开后再开始"));
            }
            ColorRule rule = draft.colorRule;
            if (rule != null && rule.enabled) {
                if (Build.VERSION.SDK_INT < 30) throw new IllegalArgumentException(L.t("颜色检测需要 Android 11 或更高版本"));
                rule.validateScreen(activePlan.width, activePlan.height);
                if (obscured(rule.x, rule.y) || (!rule.back && obscured(rule.tapX, rule.tapY)))
                    throw new IllegalArgumentException(L.t("控制面板遮挡颜色规则坐标，请移开后开始"));
            }
            stopColorMonitor();
            runner.start(activePlan);
            if (rule != null && rule.enabled && Build.VERSION.SDK_INT >= 30) {
                colorMonitor = new ColorMonitor(this, runner, rule, activePlan,
                    () -> environmentMatches() && panel != null && panel.getVisibility() == View.VISIBLE
                        && !obscured(rule.x, rule.y) && (rule.back || !obscured(rule.tapX, rule.tapY)),
                    text -> { status = text; updatePanel(); notifyState(); });
                colorMonitor.start();
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            status = e.getMessage(); updatePanel(); notifyState(); toast(status);
        }
    }
    private void startRelay() {
        if (Build.VERSION.SDK_INT < 30) { toast(L.t("实时屏幕检测需要 Android 11 或更高版本")); return; }
        RelayConfig config = new RelayConfig(this);
        if (!config.ready(ScreenInfo.read(this))) { toast(L.t("请先在跃点页面校准地图、里程与 Wi-Fi 开关")); return; }
        if (!getApplicationContext().getSystemService(android.net.wifi.WifiManager.class).isWifiEnabled()) { toast(L.t("请先开启 Wi-Fi 和移动数据")); return; }
        if (!unlocked() || !store.accepted()) return;
        int[] location = new int[2]; panel.getLocationOnScreen(location);
        Rect panelRect = new Rect(location[0], location[1], location[0] + panel.getWidth(), location[1] + panel.getHeight());
        if (Rect.intersects(panelRect, config.map) || Rect.intersects(panelRect, config.distance)) {
            toast(L.t("请将悬浮面板移到地图和里程区域之外")); return;
        }
        relayMonitor = new RelayMonitor(this, config,
            message -> { status = message; updatePanel(); notifyState(); }, () -> { updatePanel(); notifyState(); });
        relayMonitor.start(); updatePanel(); notifyState();
    }
    boolean dispatchClick(ClickStep step, SequenceRunner.Result result) {
        if (!environmentMatches() || panel == null || panel.getVisibility() != View.VISIBLE) {
            runner.stop(L.t("屏幕或控制面板状态变化，已停止")); return false;
        }
        int[] location = new int[2]; panel.getLocationOnScreen(location);
        if (step.x >= location[0] && step.x < location[0] + panel.getWidth()
            && step.y >= location[1] && step.y < location[1] + panel.getHeight()) {
            runner.stop(L.t("控制面板遮挡点击坐标，已停止")); return false;
        }
        Path path = new Path(); path.moveTo(step.x, step.y);
        GestureDescription description = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, step.durationMs)).build();
        return dispatchGesture(description, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) { result.complete(true); }
            @Override public void onCancelled(GestureDescription gesture) { result.complete(false); }
        }, engineHandler);
    }

    boolean obscured(int x, int y) {
        if (panel == null) return true;
        int[] location = new int[2]; panel.getLocationOnScreen(location);
        return x >= location[0] && x < location[0] + panel.getWidth()
            && y >= location[1] && y < location[1] + panel.getHeight();
    }
    private void stopColorMonitor() {
        if (colorMonitor != null) { colorMonitor.stop(); colorMonitor = null; }
    }

    private void choosePoint() {
        choosePoint(0);
    }
    private void choosePoint(int kind) {
        if (isRunning() || picker != null) return;
        if (kind != 0 && Build.VERSION.SDK_INT < 30) return;
        if (!unlocked()) { toast(L.t("请先解锁屏幕")); return; }
        final ScreenInfo screen = ScreenInfo.read(this);
        try {
            Store.Draft d = store.load(screen);
            if (!screen.matches(d) && (!d.steps.isEmpty() || d.colorRule != null)) throw new IllegalStateException(L.t("屏幕已变化，请返回应用清空旧步骤后重新取点"));
        } catch (IllegalStateException e) { toast(e.getMessage()); return; }
        removeMarker();
        FrameLayout layer = new FrameLayout(themed); layer.setBackgroundColor(0x180F172A);
        View target = new View(themed) {
            @Override public boolean performClick() { super.performClick(); return true; }
        };
        target.setContentDescription(L.t("点选屏幕上的坐标，选点不会点击下方应用"));
        layer.addView(target, new FrameLayout.LayoutParams(-1, -1));
        TextView hint = Ui.text(themed, (kind == 1 ? L.t("点选颜色检测位置") : kind == 2 ? L.t("点选颜色命中后的点击位置") : L.t("点选位置，记录坐标")) + L.t("\n不会点击下方页面"), Ui.INK);
        hint.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        hint.setBackground(Ui.background(this, Color.WHITE, 12));
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hintParams.topMargin = Ui.dp(this, 48); layer.addView(hint, hintParams);
        Button cancel = Ui.button(themed, L.t("取消取点"), false, v -> cancelPicker());
        FrameLayout.LayoutParams cancelParams = new FrameLayout.LayoutParams(Ui.dp(this, 180), -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cancelParams.bottomMargin = Ui.dp(this, 52); layer.addView(cancel, cancelParams);
        target.setOnTouchListener((view, event) -> {
            if (event.getPointerCount() > 1) { cancelPicker(); toast(L.t("请一次选择一个位置")); return true; }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                view.performClick(); int x = Math.round(event.getRawX()), y = Math.round(event.getRawY());
                cancelPicker(); savePoint(screen, x, y, kind);
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) cancelPicker();
            return true;
        });
        target.setClickable(true); picker = layer; panel.setVisibility(View.GONE);
        try { windows.addView(picker, layout(-1, -1, false)); updatePanel(); }
        catch (RuntimeException e) { cancelPicker(); toast(L.t("无法显示取点层，请检查辅助功能状态")); }
    }
    private void savePoint(ScreenInfo screen, int x, int y, int kind) {
        try {
            ScreenInfo now = ScreenInfo.read(this);
            if (screen.width != now.width || screen.height != now.height || screen.rotation != now.rotation || !unlocked())
                throw new IllegalStateException(L.t("屏幕已变化，本次取点已取消"));
            Store.Draft d = store.load(now);
            if (!now.matches(d)) {
                if (!d.steps.isEmpty() || d.colorRule != null) throw new IllegalStateException(L.t("坐标基准已变化，请重新取点"));
                d.width = now.width; d.height = now.height; d.rotation = now.rotation;
            }
            if (kind == 0) {
                d.steps.add(new ClickStep(x, y, 50, 500));
                status = L.t("已添加第 ") + d.steps.size() + L.t(" 步 (") + x + ", " + y + ")";
            } else {
                ColorRule old = d.colorRule == null ? new ColorRule(false, now.width / 2, now.height / 2, 0xFFFFFF, 16, 2, 500, true, 0, 0) : d.colorRule;
                d.colorRule = new ColorRule(old.enabled, kind == 1 ? x : old.x, kind == 1 ? y : old.y, old.rgb,
                    old.tolerance, old.confirmations, old.intervalMs, kind == 1 && old.back, kind == 2 ? x : old.tapX, kind == 2 ? y : old.tapY);
                status = L.t("已记录") + (kind == 1 ? L.t("检测点") : L.t("动作点")) + " (" + x + ", " + y + L.t(")\n回应用检查并开启颜色规则");
            }
            store.save(d);
            showMarker(x, y, kind == 0 ? String.valueOf(d.steps.size()) : kind == 1 ? L.t("色") : L.t("点")); updatePanel(); notifyState();
            if (configObserver != null) configObserver.run();
        } catch (IllegalArgumentException | IllegalStateException e) { toast(e.getMessage()); }
    }
    private void showMarker(int x, int y, String number) {
        removeMarker();
        TextView label = Ui.heading(themed, String.valueOf(number)); label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER); label.setBackground(Ui.background(this, Ui.BLUE, 24)); marker = label;
        int size = Ui.dp(this, 40); WindowManager.LayoutParams params = layout(size, size, true);
        params.x = x - size / 2; params.y = y - size / 2;
        try { windows.addView(marker, params); uiHandler.postDelayed(this::removeMarker, 1200); }
        catch (RuntimeException e) { marker = null; }
    }
    private void removeMarker() {
        uiHandler.removeCallbacksAndMessages(null); if (marker != null) { remove(marker); marker = null; }
    }
    private void cancelPicker() {
        if (picker != null) { remove(picker); picker = null; }
        if (panel != null) panel.setVisibility(View.VISIBLE); updatePanel();
    }
    private void closePanel() {
        cancelPicker(); removeMarker(); stopAll(L.t("面板已关闭，已停止"));
        if (panel != null) { remove(panel); panel = null; }
    }
    private void remove(View view) {
        try { windows.removeView(view); }
        catch (IllegalArgumentException ignored) { /* Already removed by the system. */ }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName() != null) {
            String name = event.getPackageName().toString();
            if (!name.equals(getPackageName())) foregroundPackage = name;
        }
        if (loopRunning() && !environmentMatches()) runner.stop(L.t("屏幕状态变化，已停止"));
        if (relayMonitor != null) relayMonitor.checkEnvironment();
    }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config); cancelPicker(); removeMarker();
        if (loopRunning() && !environmentMatches()) runner.stop(L.t("屏幕尺寸或方向变化，已停止"));
        if (relayMonitor != null) relayMonitor.checkEnvironment();
        repositionPanel();
    }
    @Override public void onInterrupt() { cancelPicker(); removeMarker(); stopAll(L.t("辅助功能被中断，已停止")); }
    private void shutdown() {
        closePanel(); engineHandler.removeCallbacksAndMessages(null);
        if (receiverRegistered) { unregisterReceiver(screenReceiver); receiverRegistered = false; }
        if (alive() == this) instance.clear();
        notifyState();
    }
    @Override public boolean onUnbind(Intent intent) { shutdown(); return super.onUnbind(intent); }
    @Override public void onDestroy() { shutdown(); super.onDestroy(); }
    private void toast(String message) { Toast.makeText(themed == null ? this : themed, L.t(message), Toast.LENGTH_LONG).show(); }
}
