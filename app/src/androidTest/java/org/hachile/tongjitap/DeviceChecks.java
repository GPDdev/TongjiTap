package org.hachile.tongjitap;

import android.app.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Run only on disposable emulators: changes their accessibility, rotation and screen settings. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class DeviceChecks extends IntegrationChecks {
    private UiAutomation automation;
    private Context target;
    private Store store;
    private MainActivity activity;
    private int passed;
    private String stage = "setup";
    private boolean newUiOnly, formalUi, integration, onboarding;

    @Override public void onCreate(Bundle args) { super.onCreate(args); newUiOnly = args != null && "new-ui".equals(args.getString("only")); formalUi = args != null && "v1".equals(args.getString("only")); integration = args != null && "integration".equals(args.getString("only")); onboarding = args != null && "onboarding".equals(args.getString("only")); start(); }
    @Override public void onStart() {
        if (onboarding) { new OnboardingChecks(this).run(); return; }
        if (integration) { super.onStart(); return; }
        Bundle result = new Bundle();
        try {
            target = getTargetContext(); store = new Store(target);
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS; automation.setServiceInfo(info);
            shell("settings put system accelerometer_rotation 0"); shell("settings put system user_rotation 0");
            shell("input keyevent 224"); shell("wm dismiss-keyguard");
            if (formalUi) {
                runFormalChecks(); result.putString("stream", "\nPASS: " + passed + " formal UI scenarios\n");
                finish(Activity.RESULT_OK, result); return;
            }
            target.getSharedPreferences("app", 0).edit().putBoolean("tutorialDone", true).putBoolean("starShown", true).putString("language", "zh").putString("theme", "light").commit();
            if (newUiOnly) {
                ScreenInfo screen = ScreenInfo.read(target); store.accept(); store.markRunNotice(); store.save(new Store.Draft(screen));
                launchMain(); shell("settings put secure enabled_accessibility_services ''"); SystemClock.sleep(500);
                shell("settings put secure enabled_accessibility_services org.hachile.tongjitap/.TapService");
                shell("settings put secure accessibility_enabled 1"); await(() -> TapService.alive() != null, 8000);
                fixture(true); Rect a = requiredNode("目标 A").bounds, b = requiredNode("目标 B").bounds;
                runPresentationChecks();
                if (Build.VERSION.SDK_INT >= 30) runScreenshotChecks(screen, a, b);
                result.putString("stream", "\nPASS: " + passed + " focused new-UI scenarios\n");
                finish(Activity.RESULT_OK, result); return;
            }

            stage = "Explicit consent"; launchMain();
            AccessibilityNodeInfo agree = node("同意并进入"); require(agree != null && !agree.isEnabled(), "Consent must start disabled");
            scrollConsentToEnd();
            click("我已阅读并理解功能、权限和风险说明"); click("同意并进入");
            await(store::accepted, 3000); screenshot("01-home"); pass();

            stage = "Persistence and corrupted input";
            ScreenInfo screen = ScreenInfo.read(target); Store.Draft d = new Store.Draft(screen);
            d.steps.add(new ClickStep(12, 34, 50, 500)); d.loops = 7; store.save(d);
            Store.Draft loaded = store.load(screen); require(loaded.loops == 7 && loaded.steps.get(0).y == 34, "Config round trip");
            target.getSharedPreferences("tongjitap", 0).edit().putString("draft", "{broken").commit();
            boolean rejected = false;
            try { store.load(screen); } catch (IllegalStateException expected) { rejected = true; }
            require(rejected, "Corrupt JSON must be rejected");
            require("{broken".equals(target.getSharedPreferences("tongjitap", 0).getString("draft", "")), "Corrupt data must not be overwritten silently");
            store.save(new Store.Draft(screen)); pass();

            stage = "Manual editor validation and persistence";
            runOnMainSync(() -> { activity.recreate(); }); SystemClock.sleep(800); launchMain();
            runOnMainSync(() -> {
                try { java.lang.reflect.Method m = MainActivity.class.getDeclaredMethod("editStep", int.class); m.setAccessible(true); m.invoke(activity, -1); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }); setText("X 坐标（屏幕像素）", "100"); setText("Y 坐标（屏幕像素）", "900");
            setText("按住时长（1～10000 毫秒）", "0"); click("保存");
            require(store.load(screen).steps.isEmpty(), "Zero duration must not save");
            setText("按住时长（1～10000 毫秒）", "50"); click("保存");
            require(store.load(screen).steps.size() == 1, "Valid step must save");
            click("设置循环次数"); setText("整组循环次数（1～100000）", "0"); click("保存");
            require(store.load(screen).loops == 10, "Zero loops must not save");
            setText("整组循环次数（1～100000）", "2"); click("保存");
            require(store.load(screen).loops == 2, "Loops must persist"); screenshot("02-editor"); pass();

            stage = "Disabled reorder controls and formal identity";
            scrollTo("上移"); require(!requiredNode("上移").node.isEnabled(), "First step cannot move up");
            require(!requiredNode("下移").node.isEnabled(), "Last step cannot move down");
            require("org.hachile.tongjitap".equals(target.getPackageName()), "Formal package ID");
            require(target.getString(R.string.app_name).equals(target.getApplicationInfo().loadLabel(target.getPackageManager()).toString()), "Localized formal application label");
            screenshot("06-disabled-buttons"); pass();

            stage = "Service connection and screen picking";
            store.save(new Store.Draft(screen)); launchMain();
            shell("settings put secure enabled_accessibility_services org.hachile.tongjitap/.TapService");
            shell("settings put secure accessibility_enabled 1"); await(() -> TapService.alive() != null, 8000);
            click("打开悬浮控制面板"); click("了解并打开面板"); runOnMainSync(() -> TapService.alive().showSelectionPanel());
            fixture(true); Rect a = requiredNode("目标 A").bounds, b = requiredNode("目标 B").bounds;
            click("取点"); screenshot("03-picker"); tap(a.centerX(), a.centerY());
            await(() -> store.load(ScreenInfo.read(target)).steps.size() == 1, 3000);
            require(events().isEmpty(), "Picker must not click underlying app");
            click("取点"); tap(b.centerX(), b.centerY());
            await(() -> store.load(ScreenInfo.read(target)).steps.size() == 2, 3000);
            d = store.load(screen); require(d.steps.get(0).x == a.centerX() && d.steps.get(1).y == b.centerY(), "Raw screen coordinates");
            d.loops = 2; d.steps.set(0, new ClickStep(a.centerX(), a.centerY(), 50, 300));
            d.steps.set(1, new ClickStep(b.centerX(), b.centerY(), 700, 350)); store.save(d);
            screenshot("04-panel"); pass();

            stage = "Actual cross-app sequence, timing and long press";
            click("开始"); SystemClock.sleep(750); require(events().isEmpty(), "Countdown must precede clicks");
            await(() -> !TapService.alive().isRunning(), 16000);
            List<JSONObject> records = events(); ArrayList<String> downs = new ArrayList<>();
            long lastUp = -1; String lastButton = ""; int shortClicks = 0, longClicks = 0;
            for (JSONObject e : records) {
                String action = e.getString("action"), button = e.getString("button"); long time = e.getLong("time");
                if ("down".equals(action)) {
                    downs.add(button);
                    if (lastUp >= 0) require(time - lastUp >= (lastButton.equals("A") ? 260 : 310), "Wait must follow actual release");
                }
                if ("up".equals(action)) { lastUp = time; lastButton = button; }
                if ("click".equals(action) && "A".equals(button)) shortClicks++;
                if ("long".equals(action) && "B".equals(button)) longClicks++;
                require(!"cancel".equals(action), "No cancelled fixture gesture");
            }
            require(downs.equals(Arrays.asList("A", "B", "A", "B")), "Whole sequence order: " + downs);
            require(shortClicks == 2 && longClicks == 2, "Expected two short clicks and two long presses: " + records);
            require(TapService.alive().status().contains("已完成"), "Successful completion state");
            saveText("actual-events.jsonl", shell("run-as org.hachile.tongjitap.fixture cat files/events.jsonl")); pass();

            stage = "Stop countdown, no delayed clicks";
            fixture(true); click("开始"); SystemClock.sleep(400); click("停止"); SystemClock.sleep(3300);
            require(events().isEmpty() && !TapService.alive().isRunning(), "Countdown cancelled"); pass();

            stage = "Stop during inter-step wait";
            d = store.load(screen); d.loops = 100; d.steps.set(0, new ClickStep(a.centerX(), a.centerY(), 50, 2000)); store.save(d);
            click("开始"); await(() -> {
                try { return events().stream().anyMatch(e -> "up".equals(e.optString("action"))); }
                catch (Exception e) { return false; }
            }, 7000);
            click("停止"); int count = events().size(); SystemClock.sleep(2400);
            require(events().size() == count && !TapService.alive().isRunning(), "No next step after stopping"); pass();

            stage = "Panel overlap protection";
            Rect handle = requiredNode("循济　移动面板").bounds;
            swipe(handle.centerX(), handle.centerY(), a.centerX(), a.centerY()); click("开始");
            require(!TapService.alive().isRunning(), "Panel overlap must prevent running");
            require(TapService.alive().status().contains("遮挡"), "Panel overlap error message");
            Rect shifted = requiredNode("循济　移动面板").bounds;
            swipe(shifted.centerX(), shifted.centerY(), handle.centerX(), handle.centerY()); pass();

            stage = "Orientation stop and stale-coordinate rejection";
            click("开始"); await(() -> TapService.alive().isRunning(), 2000);
            shell("settings put system user_rotation 1"); await(() -> !TapService.alive().isRunning(), 6000);
            click("开始"); require(!TapService.alive().isRunning(), "Old screen coordinates must be rejected");
            shell("settings put system user_rotation 0"); SystemClock.sleep(1200); pass();

            stage = "Screen off stops countdown";
            click("开始"); await(() -> TapService.alive().isRunning(), 2000);
            shell("input keyevent 223"); await(() -> !TapService.alive().isRunning(), 3000);
            shell("input keyevent 224"); shell("wm dismiss-keyguard"); SystemClock.sleep(1000); pass();

            stage = "Permission revoke and restart safety";
            fixture(true); click("开始"); shell("settings put secure enabled_accessibility_services ''");
            await(() -> TapService.alive() == null, 5000); SystemClock.sleep(3300);
            require(events().isEmpty(), "No gesture after permission revoked during countdown");
            shell("settings put secure enabled_accessibility_services org.hachile.tongjitap/.TapService");
            await(() -> TapService.alive() != null, 6000);
            require(!TapService.alive().isRunning(), "Reconnected service must stay idle");
            launchMain(); screenshot("05-after-checks"); pass();

            runColorChecks(screen, a, b);
            runPresentationChecks();
            if (Build.VERSION.SDK_INT >= 30) runScreenshotChecks(screen, a, b);

            result.putString("stream", "\nPASS: " + passed + " device scenarios on API " + Build.VERSION.SDK_INT + "\n");
            result.putInt("passed", passed); finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            try { screenshot("failure"); } catch (Throwable ignored) { }
            StringWriter trace = new StringWriter(); e.printStackTrace(new PrintWriter(trace));
            result.putString("stream", "\nFAIL: " + stage + "\n" + trace); finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void pass() {
        passed++; Bundle b = new Bundle(); b.putString("stream", "PASS " + passed + ": " + stage + "\n"); sendStatus(0, b);
    }
    private void runColorChecks(ScreenInfo screen, Rect a, Rect b) throws Exception {
        if (Build.VERSION.SDK_INT < 30) {
            stage = "Color feature is visibly unavailable below Android 11";
            scrollTo("设置颜色规则"); require(!requiredNode("设置颜色规则").node.isEnabled(), "Unsupported feature must be disabled");
            require(!requiredNode("从截图选点取色").node.isEnabled(), "Screenshot import must be disabled below Android 11");
            screenshot("07-color-unavailable"); pass(); return;
        }
        stage = "Color point picking does not insert normal steps";
        store.save(new Store.Draft(screen)); launchMain(); runOnMainSync(() -> TapService.alive().showSelectionPanel()); fixture(true);
        click("检测点"); tap(screen.width - 40, screen.height / 2);
        Store.Draft picked = store.load(screen);
        require(picked.steps.isEmpty() && picked.colorRule.x == screen.width - 40 && !picked.colorRule.enabled, "Detector point saved without enabling or extra steps");
        click("动作点"); tap(b.centerX(), b.centerY()); picked = store.load(screen);
        require(!picked.colorRule.back && picked.colorRule.tapY == b.centerY() && events().isEmpty(), "Action point does not click through"); pass();

        stage = "Color rule editor validation and persistence";
        click("关闭"); launchMain(); scrollTo("设置颜色规则"); click("设置颜色规则"); click("启用颜色规则");
        setText("目标颜色（#RRGGBB）", "bad"); click("保存"); require(!store.load(screen).colorRule.enabled, "Invalid color cannot save");
        setText("目标颜色（#RRGGBB）", "#FFFFFF"); click("保存"); require(store.load(screen).colorRule.enabled, "Valid color saves");
        screenshot("08-color-config"); launchMain(); click("打开悬浮控制面板"); pass();

        for (boolean back : new boolean[]{true, false}) {
            stage = back ? "Screen pixel match performs one system Back and resumes" : "Screen pixel match taps recovery target and resumes";
            colorFixture(back ? "BACK" : "TAP"); Rect targetPoint = requiredNode("恢复页面").bounds;
            Store.Draft d = colorDraft(screen, a, b, back, targetPoint); store.save(d);
            click("开始"); await(() -> !TapService.alive().isRunning(), 16000);
            require(TapService.alive().status().contains("已完成"), "Recovery finished: " + TapService.alive().status());
            long recoveries = events().stream().filter(e -> "RECOVERY".equals(e.optString("button"))).count();
            require(recoveries == 1, "Exactly one recovery action: " + events());
            await(() -> hasClick("B"), 2000);
            saveText(back ? "color-back-events.jsonl" : "color-tap-events.jsonl", shell("run-as org.hachile.tongjitap.fixture cat files/events.jsonl"));
            screenshot(back ? "09-color-back" : "10-color-tap"); pass();
        }
        stage = "Persistent white stops without repeating recovery";
        colorFixture("STUCK"); store.save(colorDraft(screen, a, b, true, b));
        click("开始"); await(() -> !TapService.alive().isRunning(), 23000);
        require(TapService.alive().status().contains("持续存在"), "Persistent match stops visibly: " + TapService.alive().status());
        require(events().stream().filter(e -> "RECOVERY".equals(e.optString("button"))).count() == 1, "No repeated Back"); pass();

        stage = "Stop cancels in-progress color recovery";
        colorFixture("STUCK"); store.save(colorDraft(screen, a, b, true, b)); click("开始");
        await(() -> {
            try { return events().stream().anyMatch(e -> "RECOVERY".equals(e.optString("button"))); }
            catch (Exception e) { return false; }
        }, 9000);
        click("停止"); int eventCount = events().size(); SystemClock.sleep(2200);
        require(events().size() == eventCount && !TapService.alive().isRunning(), "No recovery or next step after stop"); pass();

        stage = "Protected screen black mask stops color execution";
        colorFixture("SECURE"); store.save(colorDraft(screen, a, b, true, b)); click("开始");
        await(() -> !TapService.alive().isRunning(), 10000);
        require(TapService.alive().status().contains("无法读取屏幕颜色") || TapService.alive().status().contains("主体区域全黑"),
            "Protected capture refusal or black mask stops visibly: " + TapService.alive().status());
        require(events().stream().noneMatch(e -> "RECOVERY".equals(e.optString("button"))), "Masked screen must not trigger recovery"); pass();
        fixture(true); click("关闭"); store.save(new Store.Draft(screen)); launchMain();
    }
    private Store.Draft colorDraft(ScreenInfo screen, Rect a, Rect b, boolean back, Rect recovery) {
        Store.Draft d = new Store.Draft(screen); d.loops = 1;
        d.steps.add(new ClickStep(a.centerX(), a.centerY(), 50, 5000));
        d.steps.add(new ClickStep(b.centerX(), b.centerY(), 50, 0));
        d.colorRule = new ColorRule(true, screen.width - 40, screen.height / 2, 0xFFFFFF, 0, 2, 500, back, recovery.centerX(), recovery.centerY());
        return d;
    }
    private void runFormalChecks() throws Exception {
        target.getSharedPreferences("app", 0).edit().clear().commit();
        target.getSharedPreferences("tongjitap", 0).edit().clear().commit();
        stage = "First launch consent and complete tutorial"; launchMain();
        require(!requiredNode("同意并进入").node.isEnabled(), "Consent starts disabled");
        scrollTo("我已阅读并理解功能、权限和风险说明");
        scrollConsentToEnd();
        click("我已阅读并理解功能、权限和风险说明"); click("同意并进入");
        require(node("你想先学哪个功能？") != null, "Consent leads into tutorial chooser"); screenshot("v1-01-tutorial");
        click("稍后再学"); require(new AppPreferences(target).tutorialDone(), "Tutorial can be reopened from More"); pass();
        target.getSharedPreferences("app", 0).edit().putBoolean("starShown", true).commit();

        stage = "Four tabs, music and collapsed changelog";
        click("随步"); require(node("调速设置") != null, "Embedded Stride settings"); screenshot("v1-02-stride");
        click("跃点"); require(node("截图校准") != null, "Relay calibration exists"); screenshot("v1-03-relay");
        click("更多"); require(node("展开完整更新日志") != null, "Changelog collapsed initially");
        click("展开完整更新日志"); require(node("收起更新日志") != null, "Changelog expanded");
        click("收起更新日志"); screenshot("v1-04-more"); pass();

        stage = "Four catalogs and dark theme persisted without counting recreation";
        int launches = target.getSharedPreferences("app", 0).getInt("launches", 0);
        for (String lang : new String[]{"en", "ja", "de", "zh"}) {
            new AppPreferences(target).language(lang); new AppPreferences(target).theme("dark"); launchMain();
            runOnMainSync(() -> { require(Ui.dark, "Dark palette applied"); require(L.language.equals(lang), "Locale applied: " + lang); });
            screenshot("v1-language-" + lang);
        }
        new AppPreferences(target).theme("light"); launchMain(); pass();

        stage = "Fifth launch prompt exactly once, after tutorial";
        target.getSharedPreferences("app", 0).edit().putInt("launches", 4).putBoolean("starShown", false).commit(); launchMain();
        require(node("喜欢循济的话，给个 Star 吧") != null, "Fifth launch prompts"); click("下次再说");
        launchMain(); require(node("喜欢循济的话，给个 Star 吧") == null, "Prompt is not repeated"); pass();

        stage = "Default controls hide picking until explicit overlay selection";
        store.markRunNotice();
        shell("settings put secure enabled_accessibility_services org.hachile.tongjitap/.TapService");
        shell("settings put secure accessibility_enabled 1"); await(() -> TapService.alive() != null, 8000);
        runOnMainSync(() -> TapService.alive().showPanel()); SystemClock.sleep(400);
        require(node("取点") == null && node("检测点") == null && node("动作点") == null, "Default panel has no pick buttons");
        runOnMainSync(() -> TapService.alive().showSelectionPanel()); SystemClock.sleep(400);
        require(node("取点") != null, "Explicit mode shows pick buttons"); click("关闭"); pass();

        if (Build.VERSION.SDK_INT >= 30) {
            stage = "Bundled on-device distance OCR";
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final Integer[] meters = {null}; final Throwable[] error = {null};
            runOnMainSync(() -> {
                Bitmap image = Bitmap.createBitmap(600, 160, Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(image); canvas.drawColor(Color.WHITE);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.BLACK); paint.setTextSize(80); canvas.drawText("0.12 km", 20, 110, paint);
                com.google.mlkit.vision.text.TextRecognizer recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS);
                recognizer.process(com.google.mlkit.vision.common.InputImage.fromBitmap(image, 0))
                    .addOnSuccessListener(text -> meters[0] = RelayGate.parseMeters(text.getText()))
                    .addOnFailureListener(e -> error[0] = e)
                    .addOnCompleteListener(task -> { image.recycle(); recognizer.close(); latch.countDown(); });
            });
            require(latch.await(30, java.util.concurrent.TimeUnit.SECONDS), "OCR completes without model download");
            require(error[0] == null && Integer.valueOf(120).equals(meters[0]), "OCR produces actual 120 meters: " + meters[0] + " / " + error[0]); pass();
        }
        runPresentationChecks();
        stage = "Launch count persists but explicit recreation is not another launch";
        int before = target.getSharedPreferences("app", 0).getInt("launches", 0);
        runOnMainSync(() -> activity.recreate()); SystemClock.sleep(600);
        require(target.getSharedPreferences("app", 0).getInt("launches", 0) == before, "Recreation does not increment launch count"); pass();
        stage = "Screenshot-first click steps on every supported Android version";
        ScreenInfo screen=ScreenInfo.read(target); store.save(new Store.Draft(screen));
        importFixture("full",screen,ScreenshotImport.STEP); selectPixel(screen,270,960); click("保存");
        ClickStep step=store.load(screen).steps.get(0);
        require(Math.abs(step.x-270)<=2&&Math.abs(step.y-960)<=2,"Imported point retains full-screen coordinates"); pass();
        if(Build.VERSION.SDK_INT>=30){
            stage="Screenshot map, distance and Wi-Fi calibration";
            for(int mode:new int[]{ScreenshotImport.MAP,ScreenshotImport.DISTANCE}){
                importFixture("full",screen,mode); selectPixel(screen,20,mode==ScreenshotImport.MAP?900:1600); click("确认左上角");
                selectPixel(screen,screen.width-30,mode==ScreenshotImport.MAP?1500:1750); screenshot("v1-region-preview"); click("保存区域");
            }
            importFixture("full",screen,ScreenshotImport.WIFI); selectPixel(screen,290,604); click("保存");
            RelayConfig config=new RelayConfig(target); require(config.ready(screen),"Three screenshot selections form a valid calibration");
            require(Math.abs(config.map.left-20)<=2&&Math.abs(config.distance.top-1600)<=2&&Math.abs(config.wifiX-290)<=2,"Calibrated coordinates match image"); pass();
        }
    }
    private void runPresentationChecks() throws Exception {
        stage = "QQ clipboard and bottom project link";
        launchMain(); click("更多"); scrollTo("QQ 交流群：934139029"); click("QQ 交流群：934139029");
        runOnMainSync(() -> {
            ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
            require(clipboard.hasPrimaryClip() && "934139029".contentEquals(clipboard.getPrimaryClip().getItemAt(0).getText()), "Exact QQ group copied");
        });
        final String[] opened = {null};
        ActivityMonitor linkMonitor = new ActivityMonitor() {
            @Override public ActivityResult onStartActivity(Intent intent) {
                if (!Intent.ACTION_VIEW.equals(intent.getAction())) return null;
                opened[0] = intent.getDataString(); return new ActivityResult(Activity.RESULT_CANCELED, null);
            }
        };
        addMonitor(linkMonitor);
        try { scrollTo("Powered by GPDdev"); click("Powered by GPDdev"); }
        finally { removeMonitor(linkMonitor); }
        require("https://github.com/GPDdev/TongjiTap".equals(opened[0]), "Footer opens the project URL");
        screenshot("14-community"); pass();

        stage = "Native adaptive icon proportions and consistent typography";
        runOnMainSync(() -> {
            checkTypography(activity.getWindow().getDecorView());
            AlertDialog dialog = Ui.dialog(activity, "字号检查").setMessage("正文内容").setPositiveButton("关闭", null).show();
            try { checkTypography(dialog.getWindow().getDecorView()); } finally { dialog.dismiss(); }
        });
        Bitmap adaptive = drawIcon(target.getDrawable(R.mipmap.ic_launcher)), brand = drawIcon(target.getDrawable(R.drawable.ic_launcher));
        Rect adaptiveMark = artworkBounds(adaptive), brandMark = artworkBounds(brand);
        require(Math.abs(adaptiveMark.width() - brandMark.width()) <= 3 && Math.abs(adaptiveMark.height() - brandMark.height()) <= 3,
            "Launcher artwork matches brand proportions: " + adaptiveMark + " / " + brandMark);
        require(adaptiveMark.width() > 100 && adaptiveMark.width() < 130, "Arrow occupies about half the 216 px icon");
        saveBitmap("15-launcher-icon", adaptive); saveBitmap("16-brand-icon", brand); adaptive.recycle(); brand.recycle(); pass();
    }
    private void checkTypography(View view) {
        if (view instanceof android.widget.TextView) {
            android.widget.TextView text = (android.widget.TextView) view;
            if (text.getText().length() > 0) {
                float size = text.getTextSize(); boolean matched = false;
                for (int sp : new int[]{16, 20, 30}) matched |= Math.abs(size - android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_SP, sp, target.getResources().getDisplayMetrics())) < .5f;
                require(matched, "Unexpected font size " + size + " for " + text.getText());
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) checkTypography(((ViewGroup) view).getChildAt(i));
    }
    private View findTextView(View view, String text) {
        if(view instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView)view).getText())) return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findTextView(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    private Bitmap drawIcon(android.graphics.drawable.Drawable icon) {
        Bitmap bitmap = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, 216, 216); icon.draw(new Canvas(bitmap)); return bitmap;
    }
    private Rect artworkBounds(Bitmap bitmap) {
        Rect bounds = new Rect();
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            int c = bitmap.getPixel(x, y);
            if (Color.alpha(c) > 240 && Color.red(c) > 150 && Color.green(c) > 180 && Color.blue(c) > 200) bounds.union(x, y, x + 1, y + 1);
        }
        return bounds;
    }
    private void runScreenshotChecks(ScreenInfo screen, Rect a, Rect b) throws Exception {
        stage = "Imported pixel coordinates drive real screen color recovery";
        Store.Draft draft = new Store.Draft(screen); draft.loops = 1;
        draft.steps.add(new ClickStep(a.centerX(), a.centerY(), 50, 5000));
        draft.steps.add(new ClickStep(b.centerX(), b.centerY(), 50, 0)); store.save(draft); launchMain();
        importFixture("full", screen); require(!requiredNode("保存").node.isEnabled(), "Selection required before confirmation");
        selectPixel(screen, screen.width - 40, screen.height / 2); screenshot("17-screenshot-picker"); click("保存");
        ColorRule picked = store.load(screen).colorRule;
        require(picked.enabled && picked.back && picked.rgb == 0xFFFFFF, "Imported white enables default Back action");
        require(Math.abs(picked.x - (screen.width - 40)) < 4 && Math.abs(picked.y - screen.height / 2) < 4,
            "Original pixel coordinates persist: " + picked.x + ", " + picked.y);
        click("打开悬浮控制面板"); colorFixture("BACK"); click("开始");
        await(() -> !TapService.alive().isRunning(), 17000);
        require(TapService.alive().status().contains("已完成"), "Imported rule resumes sequence: " + TapService.alive().status());
        require(events().stream().filter(e -> "RECOVERY".equals(e.optString("button"))).count() == 1, "Imported rule triggers one actual Back");
        await(() -> hasClick("B"), 2000); // The fixture's posted OnClick can run after the gesture completion callback.
        saveText("import-color-events.jsonl", shell("run-as org.hachile.tongjitap.fixture cat files/events.jsonl")); click("关闭"); pass();

        stage = "Image selection preserves custom settings and cancellation preserves saved rule";
        draft = store.load(screen); draft.colorRule = new ColorRule(false, 20, 30, 0x123456, 8, 3, 700, false, 100, 900); store.save(draft); launchMain();
        importFixture("full", screen); selectPixel(screen, screen.width / 4, screen.height / 2); click("保存");
        ColorRule preserved = store.load(screen).colorRule;
        require(preserved.enabled && preserved.rgb == 0x12AB34 && preserved.tolerance == 8 && preserved.confirmations == 3
                && preserved.intervalMs == 700 && !preserved.back && preserved.tapX == 100 && preserved.tapY == 900, "Custom action and sampling settings preserved");
        require(store.load(screen).steps.size() == 2, "Import does not insert click steps");
        String before = target.getSharedPreferences("tongjitap", 0).getString("draft", "");
        importFixture("full", screen); selectPixel(screen, screen.width - 40, screen.height / 2); click("取消");
        require(before.equals(target.getSharedPreferences("tongjitap", 0).getString("draft", "")), "Cancel leaves saved data untouched"); pass();

        stage = "Cropped, corrupt and transparent images cannot save invalid rules";
        for (String kind : new String[]{"cropped", "broken"}) {
            importFixture(kind, screen); await(() -> node("正在读取截图") == null, 5000); SystemClock.sleep(300);
            require(node("保存") == null, "Invalid image must not open pixel selection: " + kind);
            require(before.equals(target.getSharedPreferences("tongjitap", 0).getString("draft", "")), "Bad image cannot alter config");
        }
        importFixture("transparent", screen); selectPixel(screen, screen.width / 2, screen.height / 2);
        require(!requiredNode("保存").node.isEnabled(), "Transparent pixel is not a screen color"); click("取消");
        store.save(new Store.Draft(screen)); launchMain(); pass();
    }
    private void importFixture(String kind, ScreenInfo screen) throws Exception {
        importFixture(kind,screen,ScreenshotImport.COLOR);
    }
    private void importFixture(String kind, ScreenInfo screen, int mode) throws Exception {
        // Intercept only the system picker result; exercise the real cross-process content URI, decoder, view and save flow.
        android.net.Uri uri = android.net.Uri.parse("content://org.hachile.tongjitap.fixture.screenshots/"
                + kind + "?width=" + screen.width + "&height=" + screen.height);
        Intent grant = new Intent().setComponent(new ComponentName("org.hachile.tongjitap.fixture", "org.hachile.tongjitap.fixture.TestActivity"))
            .setAction("org.hachile.tongjitap.fixture.GRANT_SCREENSHOT").setData(uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        target.startActivity(grant); SystemClock.sleep(350); launchMain();
        ActivityMonitor monitor = new ActivityMonitor() {
            @Override public ActivityResult onStartActivity(Intent intent) {
                if (!Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())) return null;
                require("image/*".equals(intent.getType()) && intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false), "Local image picker contract");
                return new ActivityResult(Activity.RESULT_OK, new Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            }
        };
        addMonitor(monitor);
        try {
            if(mode==ScreenshotImport.COLOR){scrollTo("从截图选点取色"); click("从截图选点取色");}
            else runOnMainSync(() -> {
                try { java.lang.reflect.Field f=MainActivity.class.getDeclaredField("screenshotImport"); f.setAccessible(true); ((ScreenshotImport)f.get(activity)).open(mode); }
                catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
            click("选择截图");
        }
        finally { removeMonitor(monitor); }
        if (!"cropped".equals(kind) && !"broken".equals(kind)) requiredNode(mode==ScreenshotImport.MAP||mode==ScreenshotImport.DISTANCE?"确认左上角":"保存");
    }
    private void selectPixel(ScreenInfo screen, int x, int y) {
        waitForIdleSync(); SystemClock.sleep(500); // Let the full-height dialog's opening animation settle.
        final int[] point=new int[2];
        runOnMainSync(() -> {
            try {
                java.lang.reflect.Field field=MainActivity.class.getDeclaredField("screenshotImport");field.setAccessible(true);
                Object importer=field.get(activity);field=ScreenshotImport.class.getDeclaredField("dialog");field.setAccessible(true);
                AlertDialog dialog=(AlertDialog)field.get(importer);
                View preview=findViewType(dialog.getWindow().getDecorView(),ScreenshotPickerView.class);
                int[] location=new int[2];preview.getLocationOnScreen(location);
                ScreenshotSelection geometry=new ScreenshotSelection(screen.width,screen.height,preview.getWidth(),preview.getHeight());
                point[0]=Math.round(location[0]+geometry.left+(x+.5f)*geometry.scale);
                point[1]=Math.round(location[1]+geometry.top+(y+.5f)*geometry.scale);
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        });
        tap(point[0],point[1]);
    }
    private View findViewType(View view, Class<?> type) {
        if(type.isInstance(view))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findViewType(((ViewGroup)view).getChildAt(i),type);if(found!=null)return found;}
        return null;
    }
    private void saveBitmap(String name, Bitmap bitmap) throws IOException {
        File folder = new File(target.getFilesDir(), "checks"); if (!folder.exists() && !folder.mkdirs()) throw new IOException("mkdir");
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
    }
    private void colorFixture(String mode) {
        Intent intent = new Intent().setComponent(new ComponentName("org.hachile.tongjitap.fixture", "org.hachile.tongjitap.fixture.TestActivity"))
            .setAction("org.hachile.tongjitap.fixture.COLOR_" + mode).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        target.startActivity(intent); SystemClock.sleep(600);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private void await(BooleanSupplier test, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < deadline) { if (test.getAsBoolean()) return; SystemClock.sleep(100); }
        throw new AssertionError("Timed out: " + stage);
    }
    private void launchMain() {
        Intent intent = new Intent(target, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity = (MainActivity) startActivitySync(intent); waitForIdleSync(); SystemClock.sleep(350);
    }
    private void fixture(boolean reset) {
        Intent intent = new Intent().setComponent(new ComponentName("org.hachile.tongjitap.fixture", "org.hachile.tongjitap.fixture.TestActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (reset) intent.setAction("org.hachile.tongjitap.fixture.RESET");
        target.startActivity(intent); SystemClock.sleep(600);
    }
    private static final class Found {
        final AccessibilityNodeInfo node; final Rect bounds = new Rect();
        Found(AccessibilityNodeInfo node) { this.node = node; node.getBoundsInScreen(bounds); }
    }
    private AccessibilityNodeInfo node(String text) {
        return node(text, false);
    }
    private AccessibilityNodeInfo node(String text, boolean editable) {
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            AccessibilityNodeInfo found = search(window.getRoot(), text, editable);
            if (found != null) return found;
        }
        return null;
    }
    private AccessibilityNodeInfo search(AccessibilityNodeInfo root, String text, boolean editable) {
        if (root == null) return null;
        if (root.isVisibleToUser() && (!editable || root.isEditable()) && (text.contentEquals(root.getText() == null ? "" : root.getText())
            || text.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription()))) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo result = search(root.getChild(i), text, editable); if (result != null) return result;
        }
        return null;
    }
    private Found requiredNode(String text) { await(() -> node(text) != null, 5000); return new Found(node(text)); }
    private void scrollTo(String text) {
        for (int i = 0; i < 6 && node(text) == null; i++) {
            Rect s = bounds(); swipe(s.centerX(), s.bottom - 560, s.centerX(), s.top + 350);
        }
    }
    private void click(String text) {
        Found f = requiredNode(text); require(f.node.isEnabled(), "Disabled control: " + text);
        require(f.node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Click action failed: " + text); SystemClock.sleep(200);
    }
    private void scrollConsentToEnd() {
        for (int i = 0; i < 18; i++) {
            AccessibilityNodeInfo checkbox = node("我已阅读并理解功能、权限和风险说明");
            if (checkbox != null && checkbox.isEnabled()) return;
            Rect screen = bounds(); swipe(screen.centerX(), screen.bottom - 380, screen.centerX(), screen.top + 350);
        }
        throw new AssertionError("Privacy checkbox must unlock at the bottom");
    }
    private void setText(String label, String text) {
        Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        await(() -> node(label, true) != null, 5000);
        require(node(label, true).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "Set text: " + label); SystemClock.sleep(100);
    }
    private Rect bounds() { ScreenInfo s = ScreenInfo.read(target); return new Rect(0, 0, s.width, s.height); }
    private void touch(int action, long down, int x, int y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN); automation.injectInputEvent(event, true); event.recycle();
    }
    private void tap(int x, int y) { long down = SystemClock.uptimeMillis(); touch(MotionEvent.ACTION_DOWN, down, x, y); SystemClock.sleep(60); touch(MotionEvent.ACTION_UP, down, x, y); SystemClock.sleep(300); }
    private void swipe(int x1, int y1, int x2, int y2) {
        long down = SystemClock.uptimeMillis(); touch(MotionEvent.ACTION_DOWN, down, x1, y1);
        for (int i = 1; i <= 12; i++) { SystemClock.sleep(30); touch(MotionEvent.ACTION_MOVE, down, x1 + (x2-x1)*i/12, y1 + (y2-y1)*i/12); }
        touch(MotionEvent.ACTION_UP, down, x2, y2); SystemClock.sleep(400);
    }
    private String shell(String command) throws IOException {
        ParcelFileDescriptor pipe = automation.executeShellCommand(command);
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pipe); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
            return out.toString("UTF-8");
        }
    }
    private List<JSONObject> events() throws Exception {
        String raw = shell("run-as org.hachile.tongjitap.fixture cat files/events.jsonl");
        ArrayList<JSONObject> events = new ArrayList<>();
        for (String line : raw.split("\n")) if (!line.trim().isEmpty()) events.add(new JSONObject(line));
        return events;
    }
    private boolean hasClick(String button) {
        try { return events().stream().anyMatch(e -> button.equals(e.optString("button")) && "click".equals(e.optString("action"))); }
        catch (Exception e) { throw new IllegalStateException("Cannot read fixture events", e); }
    }
    private void saveText(String name, String text) throws IOException {
        File folder = new File(target.getFilesDir(), "checks"); if (!folder.exists() && !folder.mkdirs()) throw new IOException("mkdir");
        try (FileOutputStream out = new FileOutputStream(new File(folder, name))) { out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    }
    private void screenshot(String name) throws IOException {
        Bitmap bitmap = automation.takeScreenshot(); if (bitmap == null) throw new IOException("No screenshot");
        File folder = new File(target.getFilesDir(), "checks"); if (!folder.exists() && !folder.mkdirs()) throw new IOException("mkdir");
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
        bitmap.recycle();
    }
}
