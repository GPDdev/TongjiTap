package org.hachile.tongjitap;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import androidx.media3.common.*;
import androidx.media3.session.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real decoder, accessibility screenshots/OCR and system toggles, on project AVDs only. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class IntegrationChecks extends Instrumentation {
    private Context context;
    private UiAutomation automation;
    private MainActivity activity;
    private String stage = "setup";
    private int passed;
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            context = getTargetContext(); automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();
            info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS; automation.setServiceInfo(info);
            shell("cmd statusbar collapse"); shell("input keyevent 224"); shell("wm dismiss-keyguard");
            context.getSharedPreferences("app", 0).edit().putBoolean("tutorialDone",true).putBoolean("starShown",true).putString("language","zh").commit();
            Store store = new Store(context); store.accept(); store.markRunNotice(); store.save(new Store.Draft(ScreenInfo.read(context)));
            context.startActivity(new Intent().setComponent(new ComponentName("org.hachile.tongjitap.fixture","org.hachile.tongjitap.fixture.TestActivity"))
                .setAction("org.hachile.tongjitap.fixture.GRANT_SCREENSHOT").setData(android.net.Uri.parse("content://org.hachile.tongjitap.fixture.screenshots/audio")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(500);
            new LibraryStore(context).save(java.util.Collections.singletonList(new Track("test", "Test tone", "Integration fixture", "content://org.hachile.tongjitap.fixture.screenshots/audio", false)));
            activity = (MainActivity) startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            music();
            if (Build.VERSION.SDK_INT >= 30) relay();
            result.putString("stream", "\nPASS: " + passed + " integration scenarios\n"); finish(Activity.RESULT_OK,result);
        } catch (Throwable error) {
            try { snapshot("failure"); } catch(Exception ignored) {}
            result.putString("stream", "\nFAIL: " + stage + "\n" + android.util.Log.getStackTraceString(error)); finish(Activity.RESULT_CANCELED,result);
        }
    }
    private void music() throws Exception {
        stage = "Actual local decode and pitch-preserving speed";
        click("随步");
        @SuppressWarnings("unchecked") com.google.common.util.concurrent.ListenableFuture<MediaController>[] future = new com.google.common.util.concurrent.ListenableFuture[1];
        runOnMainSync(() -> future[0] = new MediaController.Builder(context,new SessionToken(context,new ComponentName(context,PlaybackService.class))).buildAsync());
        MediaController player = future[0].get(15,TimeUnit.SECONDS);
        try {
            runOnMainSync(() -> { player.prepare(); player.play(); });
            awaitMain(() -> player.isPlaying() && player.getCurrentPosition()>300,15000);
            runOnMainSync(() -> { player.setPlaybackParameters(new PlaybackParameters(1.25f,1f)); });
            awaitMain(() -> Math.abs(player.getPlaybackParameters().speed-1.25f)<.01 && player.getPlaybackParameters().pitch==1f,3000); pass();
            stage = "Stride gesture changes the real playback speed";
            final Rect[] pad = {null};
            runOnMainSync(() -> { View v=find(activity.getWindow().getDecorView(),GesturePad.class); int[] at=new int[2]; v.getLocationOnScreen(at); pad[0]=new Rect(at[0],at[1],at[0]+v.getWidth(),at[1]+v.getHeight()); });
            Rect p=pad[0]; swipe(p.centerX(),p.centerY(),p.right-30,p.centerY());
            awaitMain(() -> player.getPlaybackParameters().speed>1.26f,3000); snapshot("stride-playing"); pass();
            stage = "Music continues in the background and stops on pause";
            shell("input keyevent 3"); SystemClock.sleep(1200);
            awaitMain(player::isPlaying,3000);
            final long[] position={0}; runOnMainSync(() -> position[0]=player.getCurrentPosition()); SystemClock.sleep(1500);
            runOnMainSync(() -> { check(player.getCurrentPosition()>position[0]+800,"Background position advances"); player.pause(); });
            awaitMain(() -> !player.isPlaying(),3000); pass();
        } finally { runOnMainSync(() -> { player.pause(); player.release(); }); }
    }
    private void relay() throws Exception {
        stage = "Wi-Fi quick setting off and on with verified panel dismissal";
        shell("svc wifi enable"); SystemClock.sleep(1000);
        shell("settings put secure enabled_accessibility_services org.hachile.tongjitap/.TapService"); shell("settings put secure accessibility_enabled 1");
        awaitMain(() -> TapService.alive()!=null,8000);
        runOnMainSync(() -> TapService.alive().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)); SystemClock.sleep(1200);
        AccessibilityNodeInfo tile = node("Internet"); check(tile!=null,"AOSP Internet tile visible"); Rect bounds=new Rect(); tile.getBoundsInScreen(bounds);
        RelayConfig config=new RelayConfig(context); ScreenInfo screen=ScreenInfo.read(context); config.prepare(screen);
        config.wifiX=bounds.centerX(); config.wifiY=bounds.centerY(); config.map=new Rect(20,900,screen.width-20,screen.height-360);
        config.distance=new Rect(20,screen.height-300,screen.width-20,screen.height-130); config.save(context,screen);
        snapshot("quick-settings-calibration"); shell("cmd statusbar collapse"); SystemClock.sleep(500);
        fixture(false);
        for (boolean desired : new boolean[]{false,true}) {
            CountDownLatch latch=new CountDownLatch(1); boolean[] success={false};
            runOnMainSync(() -> new WifiPanel(TapService.alive()).change(desired,config,ok->{success[0]=ok;latch.countDown();}));
            check(latch.await(15,TimeUnit.SECONDS)&&success[0],"Wi-Fi reaches requested state: "+desired);
            awaitMain(() -> TapService.alive().relayTargetVisible(),3500);
        }
        pass();
        stage = "Two Relay transitions using real screenshots, OCR and Wi-Fi state";
        fixture(true); runOnMainSync(() -> TapService.alive().showRelayPanel()); SystemClock.sleep(400); click("开始");
        awaitMain(() -> TapService.alive().isRunning(),3000);
        awaitMain(() -> TapService.alive().status().contains("0.100 km"),20000);
        context.startActivity(new Intent().setComponent(new ComponentName("org.hachile.tongjitap.fixture","org.hachile.tongjitap.fixture.TestActivity"))
            .setAction("org.hachile.tongjitap.fixture.RELAY_JUMP").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        awaitMain(() -> !context.getSystemService(android.net.wifi.WifiManager.class).isWifiEnabled(),26000);
        awaitMain(() -> context.getSystemService(android.net.wifi.WifiManager.class).isWifiEnabled(),18000);
        SystemClock.sleep(2000); check(node("Relay fixture")!=null,"Returned to fixture after two toggles");
        snapshot("relay-two-transitions"); click("停止");
        awaitMain(() -> !TapService.alive().isRunning(),3000); click("关闭"); pass();
        stage = "Calibration is invalidated when the screen baseline changes";
        RelayConfig stale=new RelayConfig(context); stale.save(context,new ScreenInfo(screen.width+1,screen.height,screen.rotation));
        check(!stale.ready(new ScreenInfo(screen.width+1,screen.height,screen.rotation))&&stale.wifiX<0&&stale.map.isEmpty(),"Stale points cleared"); pass();
    }
    private void fixture(boolean relay) {
        context.startActivity(new Intent().setComponent(new ComponentName("org.hachile.tongjitap.fixture","org.hachile.tongjitap.fixture.TestActivity"))
            .setAction("org.hachile.tongjitap.fixture."+(relay?"RELAY":"RESET")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); SystemClock.sleep(700);
    }
    private View find(View root,Class<?> type) {
        if(type.isInstance(root))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View v=find(((ViewGroup)root).getChildAt(i),type);if(v!=null)return v;}
        return null;
    }
    private AccessibilityNodeInfo node(String text) { for(AccessibilityWindowInfo w:automation.getWindows()){AccessibilityNodeInfo n=search(w.getRoot(),text);if(n!=null)return n;}return null; }
    private AccessibilityNodeInfo search(AccessibilityNodeInfo root,String text) {
        if(root==null)return null;
        if(root.isVisibleToUser()&&(text.contentEquals(root.getText()==null?"":root.getText())||text.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())))return root;
        for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo found=search(root.getChild(i),text);if(found!=null)return found;}return null;
    }
    private void click(String text) { long end=SystemClock.uptimeMillis()+4000; AccessibilityNodeInfo found; while((found=node(text))==null&&SystemClock.uptimeMillis()<end)SystemClock.sleep(100);check(found!=null,"Missing "+text);check(found.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Click "+text);SystemClock.sleep(300); }
    private void awaitMain(BooleanSupplier condition,long timeout) {long end=SystemClock.uptimeMillis()+timeout;boolean[] ok={false};while(SystemClock.uptimeMillis()<end){runOnMainSync(()->ok[0]=condition.getAsBoolean());if(ok[0])return;SystemClock.sleep(150);}throw new AssertionError("Timeout: "+stage);}
    private void swipe(int x1,int y1,int x2,int y2){long down=SystemClock.uptimeMillis();for(int i=0;i<=12;i++){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),i==0?0:i==12?1:2,x1+(x2-x1)*i/12f,y1+(y2-y1)*i/12f,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);automation.injectInputEvent(e,true);e.recycle();SystemClock.sleep(40);}}
    private String shell(String command)throws java.io.IOException{try(java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command));java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    private void snapshot(String name)throws java.io.IOException{java.io.File dir=new java.io.File(context.getFilesDir(),"checks");dir.mkdirs();android.graphics.Bitmap b=automation.takeScreenshot();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
    private void pass(){passed++;Bundle status=new Bundle();status.putString("stream","PASS "+passed+": "+stage+"\n");sendStatus(0,status);}
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}
