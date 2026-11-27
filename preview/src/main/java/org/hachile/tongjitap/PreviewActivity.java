package org.hachile.tongjitap;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.util.Collections;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PreviewActivity extends ComponentActivity {
    private static final String GROUP = "934139029";
    private static final String[] LANGUAGES = {"zh", "en", "ja", "de"};
    private AppPreferences preferences;
    private FrameLayout frame, content;
    private LinearLayout shell, navigation;
    private StridePage stride;
    private NetworkStatus network;
    private Bundle playerState;
    private int tab, guideStep;
    private boolean started, historyOpen;
    private PreviewReleaseFeed updates;
    private PreviewReleaseFeed.Result pendingUpdate;
    private BlueUi.Row checkRow;
    private boolean resumed, checkedThisLaunch, manualCheck, manualResult;
    private String lastOffered;
    private final OnBackPressedCallback dismissGuide = new OnBackPressedCallback(false) {
        @Override public void handleOnBackPressed() { endGuide(); }
    };

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppPreferences.wrap(base)); }

    @Override protected void onCreate(Bundle saved) {
        setTheme(R.style.AppTheme);
        super.onCreate(saved);
        L.init(this);
        Ui.apply(this);
        preferences = new AppPreferences(this);
        updates = new PreviewReleaseFeed();
        network = new NetworkStatus(this);
        tab = saved == null ? launchTab(getIntent()) : Tabs.restore(saved.getInt("tab"));
        playerState = saved;
        if (saved != null) {
            historyOpen = saved.getBoolean("historyOpen");
            guideStep = saved.getInt("guideStep");
            lastOffered = saved.getString("lastOffered");
        }
        getOnBackPressedDispatcher().addCallback(this, dismissGuide);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT < 35) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        frame = new FrameLayout(this);
        frame.setBackgroundColor(Ui.BG);
        ViewCompat.setOnApplyWindowInsetsListener(frame, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        setContentView(frame);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), frame);
        bars.setAppearanceLightStatusBars(!Ui.dark);
        bars.setAppearanceLightNavigationBars(!Ui.dark);
        display();
        if (!getPreferences(MODE_PRIVATE).getBoolean("privacySeen", false)) showPrivacy(true);
    }

    private int launchTab(Intent intent) { return Tabs.restore(intent.getIntExtra("tab", Tabs.LOOP)); }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.hasExtra("tab")) { guideStep = 0; select(launchTab(intent)); }
    }

    private void select(int next) {
        if ((guideStep == 1 || guideStep == 2) && next != Tabs.STRIDE) return;
        if (guideStep == 3 && next != Tabs.MORE) return;
        boolean done = guideStep == 3 && next == Tabs.MORE;
        if (guideStep == 1 && next == Tabs.STRIDE) guideStep = 2;
        if (done) guideStep = 0;
        tab = next;
        display();
        if (done) {
            Ui.dialog(this, getString(R.string.guide_done)).setMessage(getString(R.string.done_body))
                    .setPositiveButton(getString(R.string.help_done), null)
                    .setOnDismissListener(d -> maybeCheckAutomatically()).show();
        }
    }

    private void display() {
        network.stop();
        checkRow = null;
        frame.removeAllViews();
        shell = Ui.column(this);
        frame.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        content = new FrameLayout(this);
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = BlueUi.navigation(this, tab, this::select);
        shell.addView(navigation);
        if (tab == Tabs.STRIDE) {
            ensurePlayer();
            if (stride.root.getParent() != null) ((ViewGroup) stride.root.getParent()).removeView(stride.root);
            content.addView(stride.root);
            stride.applyScreenFlag();
            if (started) stride.onStart();
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setVerticalScrollBarEnabled(false);
            LinearLayout page = Ui.column(this);
            int gutter = Ui.dp(this, 20);
            page.setPadding(gutter, gutter, gutter, gutter);
            page.addView(Ui.title(this, BlueUi.tabLabel(tab)));
            Ui.gap(page, 22);
            scroll.addView(page);
            content.addView(scroll);
            if (tab == Tabs.RELAY) network.show(page);
            else if (tab == Tabs.ROAM) BlueUi.roamAvailability(page);
            else if (tab == Tabs.MORE) more(page);
            else home(page);
        }
        if (started && tab == Tabs.RELAY) network.start();
        ViewCompat.setAccessibilityPaneTitle(content, BlueUi.tabLabel(tab));
        showGuideStep();
    }

    private void ensurePlayer() {
        if (stride != null) return;
        LibraryStore library = new LibraryStore(this);
        if (!library.prefs.contains("library")) {
            try {
                library.save(Collections.singletonList(new Track("preview-beat", "示例节拍", "TongjiTap", Track.SAMPLE_URI, false)));
            } catch (org.json.JSONException error) { throw new IllegalStateException(error); }
        }
        stride = new StridePage(this, playerState);
        stride.onUserSpeedChange(() -> {
            if (guideStep == 2) {
                guideStep = 3;
                frame.post(this::showGuideStep);
            }
        });
    }

    private void home(LinearLayout page) {
        LinearLayout status = Ui.column(this);
        status.addView(Ui.text(this, getString(R.string.current_state), Ui.INK));
        Ui.gap(status, 8);
        status.addView(Ui.title(this, getString(R.string.idle)));
        status.setMinimumHeight(Ui.dp(this, 126));
        status.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(BlueUi.statusPanel(this, status));
        Ui.gap(page, 14);
        page.addView(new BlueUi.Row(this, getString(R.string.access), getString(R.string.no_access),
                () -> message(R.string.access, R.string.access_body)));
        BlueUi.divider(page);
        Ui.gap(page, 20);
        page.addView(new BlueUi.Row(this, getString(R.string.guide), "", this::beginGuide, true, BlueUi.Mark.Kind.BOOK));
        Ui.gap(page, 16);
        row(page, R.string.help, "", () -> message(R.string.help, R.string.home_help));
        BlueUi.divider(page);
        row(page, R.string.privacy, "", () -> showPrivacy(false));
        BlueUi.divider(page);
    }

    private void row(LinearLayout group, int name, String value, Runnable action) {
        group.addView(new BlueUi.Row(this, getString(name), value, action));
    }

    private void more(LinearLayout page) {
        LinearLayout version = BlueUi.group(this);
        row(version, R.string.version, BuildConfig.VERSION_NAME, null);
        BlueUi.divider(version);
        TextView notes = Ui.text(this, getString(R.string.changes), Ui.MUTED);
        int p = Ui.dp(this, 16);
        notes.setPadding(p, 0, p, p);
        BlueUi.Row history = new BlueUi.Row(this, getString(R.string.changelog), "", () -> {
            historyOpen = !historyOpen;
            notes.setVisibility(historyOpen ? View.VISIBLE : View.GONE);
            ((BlueUi.Row) version.findViewWithTag("history")).setExpanded(historyOpen);
        });
        history.setTag("history");
        history.setExpanded(historyOpen);
        version.addView(history);
        notes.setVisibility(historyOpen ? View.VISIBLE : View.GONE);
        version.addView(notes);
        BlueUi.divider(version);
        checkRow = new BlueUi.Row(this, getString(R.string.check_updates), "", () -> checkUpdates(true));
        version.addView(checkRow);
        showCheckProgress();
        BlueUi.divider(version);
        addUpdateSwitch(version);
        page.addView(version);
        Ui.gap(page, 22);
        page.addView(Ui.heading(this, getString(R.string.preferences)));
        Ui.gap(page, 8);
        LinearLayout prefs = BlueUi.group(this);
        row(prefs, R.string.language, AppPreferences.languageName(preferences.language()), this::chooseLanguage);
        BlueUi.divider(prefs);
        row(prefs, R.string.appearance, getString(Ui.dark ? R.string.dark : R.string.light), this::chooseTheme);
        page.addView(prefs);
        Ui.gap(page, 18);
        LinearLayout help = BlueUi.group(this);
        row(help, R.string.guide, "", this::beginGuide);
        BlueUi.divider(help);
        row(help, R.string.qq, GROUP, () -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText(getString(R.string.qq), GROUP));
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        });
        page.addView(help);
    }

    private void chooseLanguage() {
        String[] names = new String[LANGUAGES.length];
        int selected = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = AppPreferences.languageName(LANGUAGES[i]);
            if (preferences.language().equals(LANGUAGES[i])) selected = i;
        }
        new AlertDialog.Builder(this).setTitle(R.string.language).setSingleChoiceItems(names, selected, (dialog, which) -> {
            dialog.dismiss(); preferences.language(LANGUAGES[which]); recreate();
        }).setNegativeButton(R.string.cancel, null).show();
    }

    private void chooseTheme() {
        String[] themes = {getString(R.string.light), getString(R.string.dark)};
        new AlertDialog.Builder(this).setTitle(R.string.appearance).setSingleChoiceItems(themes, Ui.dark ? 1 : 0, (dialog, which) -> {
            dialog.dismiss(); preferences.theme(which == 1 ? "dark" : "light"); recreate();
        }).setNegativeButton(R.string.cancel, null).show();
    }

    private void message(int title, int body) {
        Ui.dialog(this, getString(title)).setMessage(getString(body)).setPositiveButton(getString(R.string.help_done), null).show();
    }

    @android.annotation.SuppressLint("UseSwitchCompatOrMaterialCode")
    private void addUpdateSwitch(LinearLayout version) {
        Switch toggle = new Switch(this);
        int pad = Ui.dp(this, 16);
        toggle.setPadding(pad, Ui.dp(this, 8), pad, 0);
        toggle.setText(R.string.auto_updates);
        toggle.setTextSize(Ui.BODY_SP);
        toggle.setTextColor(Ui.INK);
        toggle.setMinHeight(Ui.dp(this, 52));
        toggle.setChecked(preferences.updateReminder());
        toggle.setOnCheckedChangeListener((button, on) -> {
            preferences.updateReminder(on);
            checkedThisLaunch = false;
            if (on) maybeCheckAutomatically();
            else if (!manualResult) pendingUpdate = null;
        });
        version.addView(toggle);
        TextView info = Ui.text(this, getString(R.string.update_privacy), Ui.MUTED);
        info.setPadding(pad, 0, pad, pad);
        version.addView(info);
    }

    private void showCheckProgress() {
        if (checkRow == null) return;
        checkRow.value.setText(R.string.checking_updates);
        checkRow.value.setVisibility(manualCheck ? View.VISIBLE : View.GONE);
        checkRow.setEnabled(!manualCheck);
    }

    private void maybeCheckAutomatically() {
        if (resumed && !checkedThisLaunch && guideStep == 0 && preferences.updateReminder()
                && getPreferences(MODE_PRIVATE).getBoolean("privacySeen", false)) checkUpdates(false);
    }

    private void checkUpdates(boolean manual) {
        checkedThisLaunch = true;
        manualCheck |= manual;
        showCheckProgress();
        updates.check(result -> {
            manualResult = manualCheck;
            manualCheck = false;
            showCheckProgress();
            if (!manualResult && (!preferences.updateReminder() || result.state != PreviewReleaseFeed.State.AVAILABLE
                    || result.version.equals(lastOffered))) return;
            pendingUpdate = result;
            presentUpdate();
        });
    }

    private void presentUpdate() {
        if (pendingUpdate == null || !resumed || guideStep != 0 || isFinishing() || isDestroyed()) return;
        PreviewReleaseFeed.Result result = pendingUpdate;
        pendingUpdate = null;
        manualResult = false;
        if (result.state == PreviewReleaseFeed.State.AVAILABLE) {
            lastOffered = result.version;
            Ui.dialog(this, getString(R.string.update_found)).setMessage(getString(R.string.update_version, result.version))
                    .setNegativeButton(getString(R.string.cancel), null)
                    .setPositiveButton(getString(R.string.get_update), (d, which) -> {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(result.downloadUrl()))); }
                        catch (ActivityNotFoundException e) { message(R.string.check_updates, R.string.no_browser); }
                    }).show();
            return;
        }
        int text = result.state == PreviewReleaseFeed.State.FAILED ? R.string.check_failed
                : result.state == PreviewReleaseFeed.State.NO_RELEASE ? R.string.no_channel_release : R.string.up_to_date;
        message(R.string.check_updates, text);
    }

    private void showPrivacy(boolean first) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout copy = Ui.column(this);
        int p = Ui.dp(this, 24);
        copy.setPadding(p, Ui.dp(this, 8), p, Ui.dp(this, 8));
        if (first) {
            copy.addView(Ui.heading(this, getString(R.string.policy_lead)));
            Ui.gap(copy, 12);
        }
        copy.addView(Ui.text(this, getString(R.string.policy_body), Ui.INK));
        scroll.addView(copy);
        AlertDialog.Builder builder = Ui.dialog(this, getString(R.string.privacy)).setView(scroll);
        if (!first) { builder.setPositiveButton(getString(R.string.close), null).show(); return; }
        AlertDialog dialog = builder.setCancelable(false).setNegativeButton(getString(R.string.cancel), (d, i) -> finish())
                .setPositiveButton(getString(R.string.accept), (d, i) -> {
                    getPreferences(MODE_PRIVATE).edit().putBoolean("privacySeen", true).apply();
                    beginGuide();
                }).create();
        dialog.setOnShowListener(d -> {
            Button accept = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            accept.setEnabled(false);
            scroll.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                if (!scroll.canScrollVertically(1)) accept.setEnabled(true);
            });
            scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
                if (!scroll.canScrollVertically(1)) accept.setEnabled(true);
            });
            scroll.post(() -> { if (!scroll.canScrollVertically(1)) accept.setEnabled(true); });
        });
        dialog.show();
    }

    private void beginGuide() {
        guideStep = 1;
        tab = 0;
        display();
    }

    private void endGuide() {
        guideStep = 0;
        showGuideStep();
        presentUpdate();
        maybeCheckAutomatically();
    }

    private void showGuideStep() {
        while (frame.getChildCount() > 1) frame.removeViewAt(frame.getChildCount() - 1);
        dismissGuide.setEnabled(guideStep != 0);
        if (guideStep == 0) return;
        if (guideStep == 2 && stride.speedControlLocked()) guideStep = 3;
        boolean speed = guideStep == 2;
        View target = speed ? stride.speedControl() : navigation.findViewWithTag("tab-" + (guideStep == 1 ? Tabs.STRIDE : Tabs.MORE));
        if (speed) ((ScrollView) stride.root).scrollTo(0, 0);
        CoachOverlay shade = new CoachOverlay(this, target);
        frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        int title = guideStep == 1 ? R.string.guide_start : speed ? R.string.guide_speed : R.string.guide_more;
        int body = guideStep == 1 ? R.string.guide_start_body : speed ? R.string.guide_speed_body : R.string.guide_more_body;
        LinearLayout hint = BlueUi.group(this);
        int pad = Ui.dp(this, 16);
        hint.setPadding(pad, pad, pad, pad);
        hint.addView(Ui.heading(this, getString(title)));
        Ui.gap(hint, 4);
        hint.addView(Ui.text(this, getString(body), Ui.INK));
        hint.addView(Ui.button(this, getString(R.string.end_guide), false, v -> endGuide()));
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-1, -2, speed ? Gravity.BOTTOM : Gravity.TOP);
        position.setMargins(pad, pad, pad, pad);
        frame.addView(hint, position);
        ViewCompat.setAccessibilityPaneTitle(hint, getString(title));
    }

    @Override protected void onStart() {
        super.onStart(); started = true;
        if (tab == Tabs.STRIDE && stride != null) stride.onStart();
        if (tab == Tabs.RELAY) network.start();
    }

    @Override protected void onPostResume() {
        super.onPostResume();
        resumed = true;
        presentUpdate();
        maybeCheckAutomatically();
    }

    @Override protected void onPause() { resumed = false; super.onPause(); }

    @Override protected void onStop() {
        started = false; network.stop();
        if (stride != null) stride.onStop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        updates.close();
        if (stride != null) stride.onDestroy();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("tab", tab);
        state.putInt("guideStep", guideStep);
        state.putBoolean("historyOpen", historyOpen);
        state.putString("lastOffered", lastOffered);
        if (stride != null) stride.onSaveInstanceState(state);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 100) { ensurePlayer(); stride.onActivityResult(request, result, data); }
    }
}
