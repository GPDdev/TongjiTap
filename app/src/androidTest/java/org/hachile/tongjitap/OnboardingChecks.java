package org.hachile.tongjitap;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

/** Drives real touch targets, dialogs and audio on disposable project emulators. */
final class OnboardingChecks {
    private final Instrumentation runner;
    private Context context;
    private UiAutomation ui;
    private MainActivity activity;
    private String stage="setup";
    private int passed;
    OnboardingChecks(Instrumentation runner){this.runner=runner;}
    void run(){Bundle result=new Bundle();try{
        context=runner.getTargetContext();ui=runner.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        context.getSharedPreferences("app",0).edit().clear().putBoolean("starShown",true).commit();
        context.getSharedPreferences("tongjitap",0).edit().clear().commit();
        stage="Privacy requires bottom and consent";launch();require(node("请下滑并完整阅读隐私政策，读到末尾后才能同意并进入使用。")!=null,"Reading instruction visible");
        require(!node("同意并进入").isEnabled(),"Agree disabled initially");
        node("同意并进入").performAction(AccessibilityNodeInfo.ACTION_CLICK);
        require(!new Store(context).accepted(),"Disabled consent cannot be bypassed");
        shot("101-privacy");scrollTo("我已阅读并理解功能、权限和风险说明");
        // A checkbox becoming partially visible must not be sufficient: finish the scroll.
        for(int i=0;i<8&&!node("我已阅读并理解功能、权限和风险说明").isEnabled();i++)swipeUp();
        require(node("我已阅读并理解功能、权限和风险说明").isEnabled(),"Checkbox enabled at bottom");
        click("我已阅读并理解功能、权限和风险说明");click("同意并进入");require(new Store(context).accepted(),"Explicit consent persisted");pass();

        stage="Choice and dimmed interactive Loop";require(node("你想先学哪个功能？")!=null,"Three-choice start");shot("102-choices");
        String original=context.getSharedPreferences("tongjitap",0).getString("draft",null);
        click("自动控制开始和暂停");require(node("从截图添加步骤").isEnabled(),"Only current action enabled");require(!node("再添加一个点击步骤").isEnabled(),"Future action disabled");shot("103-loop-spotlight");
        click("从截图添加步骤");sample(30,60);click("保存");require(node("截图选点练习")!=null,"Wrong target does not advance");sample(94,548);click("保存");
        click("再添加一个点击步骤");sample(266,548);click("保存");click("编辑按住和等待时间");setText("按住时长（毫秒）","0");click("保存");require(node("等待时间（毫秒）")!=null,"Invalid duration rejected");setText("按住时长（毫秒）","75");click("保存");
        click("设置循环次数");setText("循环次数","3");click("保存");click("打开悬浮控制面板");click("开始");click("停止");require(node("练习完成")!=null,"Loop completion");require(node("QQ 交流群：934139029")!=null,"Community at completion");
        require(java.util.Objects.equals(original,context.getSharedPreferences("tongjitap",0).getString("draft",null)),"Practice must not write real coordinates");shot("104-tutorial-complete");pass();

        stage="Stride real audio and drag";click("查看其他教程");click("跑步时切换音乐倍速");click("曲库");click("载入示例节拍");click("播放");
        await(()->onMain(()->{android.media.MediaPlayer p=(android.media.MediaPlayer)field(guide(),"player");return p!=null&&p.isPlaying()&&p.getCurrentPosition()>50;}),5000);
        require(!node("继续").isEnabled(),"Cannot skip speed interaction");shot("105-stride-spotlight");
        Rect pad=rect(speedNode(ui.getRootInActiveWindow()));swipe(pad.left+pad.width()/4,pad.centerY(),pad.right-pad.width()/4,pad.centerY());
        require(node("继续").isEnabled(),"Actual gesture enables continuation");
        require(onMain(()->((android.media.MediaPlayer)field(guide(),"player")).getPlaybackParams().getSpeed()>1.03f),"Gesture changes actual sample playback speed");
        click("继续");click("调速设置");click("保存");click("暂停");require(node("练习完成")!=null,"Stride completion");pass();

        stage="Relay calibration and safe network demonstration";boolean wifi=context.getSystemService(android.net.wifi.WifiManager.class).isWifiEnabled();
        click("查看其他教程");click("自动控制 Wi-Fi 开关");click("确认网络准备");require(!node("准备好了").isEnabled(),"All network preparation required");click("开启 Wi-Fi");click("开启移动数据");click("TJ-WIFI 保存密码并自动连接");click("准备好了");
        click("框选地图区域");sample(28,104);click("确认左上角");sample(332,386);click("保存区域");
        click("框选里程区域");sample(28,420);click("确认左上角");sample(176,466);click("保存区域");
        click("选择 Wi-Fi 开关");sample(100,190);click("保存");click("打开跃点控制面板");click("开始");
        await(()->node("停止")!=null&&node("停止").isEnabled(),6000);shot("106-relay-demo");click("停止");require(node("练习完成")!=null,"Relay completion");
        require(context.getSystemService(android.net.wifi.WifiManager.class).isWifiEnabled()==wifi,"Tutorial must not toggle real Wi-Fi");require(java.util.Objects.equals(original,context.getSharedPreferences("tongjitap",0).getString("draft",null)),"All tutorials preserve config");pass();

        stage="Reopen, recreation, exit";click("开始使用");require(new AppPreferences(context).tutorialDone(),"Completion persists");click("更多");scrollTo("新手教程");click("新手教程");click("自动控制开始和暂停");click("从截图添加步骤");sample(94,548);click("保存");
        Instrumentation.ActivityMonitor monitor=runner.addMonitor(MainActivity.class.getName(),null,false);runner.runOnMainSync(()->activity.recreate());
        activity=(MainActivity)runner.waitForMonitorWithTimeout(monitor,5000);runner.removeMonitor(monitor);require(activity!=null,"Recreated Activity");idle();
        require(node("再添加一个点击步骤").isEnabled(),"Tutorial position survives recreation");click("退出教程");require(node("打开悬浮控制面板")!=null,"Exit returns to actual feature");pass();

        stage="Chinese titles and language-specific tutorials";
        for(int tab=0;tab<4;tab++){launch(tab);String word=new String[]{"Loop","Stride","Relay","More"}[tab];require(node(word)==null,"Chinese UI has no English subtitle: "+word);}
        for(String lang:new String[]{"en","ja","de"}){new AppPreferences(context).language(lang);launch(3);scrollTo(L.t("新手教程"));click(L.t("新手教程"));require(node(L.t("你想先学哪个功能？"))!=null,"Translated tutorial chooser "+lang);shot("107-tutorial-"+lang);click(L.t("稍后再学"));}
        new AppPreferences(context).language("zh");launch(3);pass();

        stage="Update preference and browser destination";require(!new AppPreferences(context).updateReminder(),"Update reminders opt-in");
        final String[] opened={null};Instrumentation.ActivityMonitor browser=new Instrumentation.ActivityMonitor(){@Override public Instrumentation.ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_VIEW.equals(intent.getAction())){opened[0]=intent.getDataString();return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);}return null;}};runner.addMonitor(browser);
        scrollTo("下载最新版");click("下载最新版");require(UpdateChecker.DOWNLOAD.equals(opened[0]),"Manual update opens stable APK browser URL");
        click("自动更新提醒");require(new AppPreferences(context).updateReminder(),"Preference on persists");click("自动更新提醒");require(!new AppPreferences(context).updateReminder(),"Preference off persists");pass();

        stage="Release parsing and update prompt";
        String release="{\"tag_name\":\"v9.1.2\",\"draft\":false,\"prerelease\":false,\"assets\":[{\"name\":\"tongjitap.apk\",\"state\":\"uploaded\",\"size\":42}]}";
        require("v9.1.2".equals(UpdateChecker.available(release)),"Stable newer APK accepted");
        require(UpdateChecker.available(release.replace("v9.1.2","v1.0.0"))==null,"Older release ignored");
        require(UpdateChecker.available(release.replace("\"prerelease\":false","\"prerelease\":true"))==null,"Prerelease ignored");
        require(UpdateChecker.available(release.replace("tongjitap.apk","source.zip"))==null,"Missing APK ignored");
        require(UpdateChecker.available("{}")==null,"Incomplete API reply ignored");
        new AppPreferences(context).updateReminder(true);runner.runOnMainSync(()->{setField(activity,"pendingVersion","v9.1.2");invoke(activity,"maybePrompts");});idle();require(node("发现新版本")!=null,"New release prompt visible");shot("108-update");click("下载更新");require(UpdateChecker.DOWNLOAD.equals(opened[0]),"Prompt uses same browser URL");
        runner.runOnMainSync(()->{setField(activity,"pendingVersion","v9.1.3");invoke(activity,"maybePrompts");});idle();require(node("发现新版本")==null,"No repeated prompt in one launch");runner.removeMonitor(browser);new AppPreferences(context).updateReminder(false);pass();

        stage="Quiet disabled update and new Star copy";context.getSharedPreferences("app",0).edit().putInt("launches",4).putBoolean("starShown",false).commit();launch();
        require(node("喜欢循济的话，给个 Star 吧")!=null,"Fifth launch invite preserved");require(node("如果循济帮到了你，欢迎到 GitHub 点一颗 Star，支持继续开发。谢谢！")!=null,"Friendly invitation without count");click("下次再说");launch();require(node("喜欢循济的话，给个 Star 吧")==null,"Star not repeated");pass();

        stage="Dark mode, large text, landscape and system back";new AppPreferences(context).theme("dark");launch(3);scrollTo("新手教程");click("新手教程");click("自动控制开始和暂停");require(node("从截图添加步骤").isEnabled(),"Dark tutorial usable");shot("109-dark-tutorial");
        float originalScale=android.provider.Settings.System.getFloat(context.getContentResolver(),"font_scale",1f);
        try {
            changeConfiguration(()->shell("settings put system font_scale 1.5"));
            require(onMain(()->activity.getResources().getConfiguration().fontScale>=1.49f),"Real 150 percent font scale");
            shot("110-large-text");click("从截图添加步骤");require(node("截图选点练习")!=null,"Large-text highlighted target reachable");click("取消");
            changeConfiguration(()->runner.runOnMainSync(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)));
            shot("111-landscape");click("从截图添加步骤");require(node("截图选点练习")!=null,"Landscape highlighted target reachable");click("取消");
            require(onMain(()->!activity.isDestroyed()&&(boolean)field(activity,"resumed")),"Current recreated activity resumed");
            shell("input keyevent KEYCODE_BACK");await(()->onMain(()->guide()==null),3000);
        } finally {
            shell("settings put system font_scale "+originalScale);
            runner.runOnMainSync(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            new AppPreferences(context).theme("light");
        }
        pass();
        result.putString("stream","\nPASS: "+passed+" onboarding scenarios\n");runner.finish(Activity.RESULT_OK,result);
    }catch(Throwable error){try{shot("onboarding-failure");}catch(Exception ignored){}result.putString("stream","\nFAIL: "+stage+"\n"+android.util.Log.getStackTraceString(error));runner.finish(Activity.RESULT_CANCELED,result);}}
    private void pass(){passed++;}
    private void changeConfiguration(Runnable change){Instrumentation.ActivityMonitor monitor=runner.addMonitor(MainActivity.class.getName(),null,false);try{change.run();MainActivity replacement=(MainActivity)runner.waitForMonitorWithTimeout(monitor,7000);require(replacement!=null,"Configuration recreated activity");activity=replacement;idle();}finally{runner.removeMonitor(monitor);}}
    private void shell(String command){try(java.io.InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))){byte[] buffer=new byte[512];while(input.read(buffer)!=-1){}}catch(java.io.IOException e){throw new AssertionError(e);}}
    private static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private void launch(){launch(0);} private void launch(int tab){if(activity!=null)runner.runOnMainSync(()->activity.finish());activity=(MainActivity)runner.startActivitySync(new Intent(context,MainActivity.class).putExtra("tab",tab).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle();}
    private void idle(){runner.waitForIdleSync();SystemClock.sleep(260);}
    private AccessibilityNodeInfo node(String text){AccessibilityNodeInfo root=ui.getRootInActiveWindow();return find(root,text);}
    private AccessibilityNodeInfo speedNode(AccessibilityNodeInfo n){if(n==null)return null;if(n.getContentDescription()!=null&&n.getContentDescription().toString().startsWith("调速区域"))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo found=speedNode(n.getChild(i));if(found!=null)return found;}return null;}
    private AccessibilityNodeInfo find(AccessibilityNodeInfo n,String text){if(n==null)return null;if(text.contentEquals(n.getText()==null?"":n.getText())||text.contentEquals(n.getContentDescription()==null?"":n.getContentDescription()))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo found=find(n.getChild(i),text);if(found!=null)return found;}return null;}
    private Rect rect(AccessibilityNodeInfo node){require(node!=null,"Missing target at "+stage);Rect r=new Rect();node.getBoundsInScreen(r);return r;}
    private void click(String text){AccessibilityNodeInfo n=node(text);require(n!=null&&n.isEnabled(),"Unavailable control: "+text);if(n.isCheckable())require(n.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Checkbox action: "+text);else{Rect r=rect(n);tap(r.centerX(),r.centerY());}idle();}
    private void tap(float x,float y){long t=SystemClock.uptimeMillis();event(t,t,MotionEvent.ACTION_DOWN,x,y);SystemClock.sleep(90);event(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y);}
    private void swipe(float x,float y,float endX,float endY){long t=SystemClock.uptimeMillis();event(t,t,0,x,y);for(int i=1;i<=15;i++){SystemClock.sleep(20);event(t,SystemClock.uptimeMillis(),2,x+(endX-x)*i/15,y+(endY-y)*i/15);}event(t,SystemClock.uptimeMillis(),1,endX,endY);idle();}
    private void event(long down,long now,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,now,action,x,y,0);e.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);ui.injectInputEvent(e,true);e.recycle();}
    private void swipeUp(){Rect r=rect(ui.getRootInActiveWindow());swipe(r.centerX(),r.bottom-r.height()*.25f,r.centerX(),r.top+r.height()*.3f);}
    private void scrollTo(String text){for(int i=0;i<20;i++){AccessibilityNodeInfo n=node(text);if(n!=null&&!rect(n).isEmpty()&&n.isVisibleToUser())return;swipeUp();SystemClock.sleep(850);}throw new AssertionError("Could not find "+text);}
    private AccessibilityNodeInfo input(AccessibilityNodeInfo node,String label){if(node==null)return null;if(node.isEditable()&&label.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=input(node.getChild(i),label);if(found!=null)return found;}return null;}
    private void setText(String label,String value){AccessibilityNodeInfo n=input(ui.getRootInActiveWindow(),label);require(n!=null,"Missing input "+label);Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);require(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b),"Input editable");idle();}
    private void sample(int x,int y){Rect r=rect(node("截图选点区域，点按或拖动选择检测位置"));float scale=Math.min(r.width()/360f,r.height()/640f);tap(r.left+(r.width()-360*scale)/2+x*scale,r.top+(r.height()-640*scale)/2+y*scale);idle();}
    private void shot(String name)throws Exception{Bitmap image=ui.takeScreenshot();File dir=new File(context.getFilesDir(),"checks");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    private void await(BooleanSupplier check,long ms){long end=SystemClock.uptimeMillis()+ms;while(SystemClock.uptimeMillis()<end){if(check.getAsBoolean())return;SystemClock.sleep(100);}throw new AssertionError("Timed out: "+stage);}
    private boolean onMain(BooleanSupplier test){boolean[] r={false};runner.runOnMainSync(()->r[0]=test.getAsBoolean());return r[0];}
    private Object guide(){return field(activity,"tutorial");}
    private static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new AssertionError(e);}}
    private static void setField(Object object,String name,Object value){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}catch(Exception e){throw new AssertionError(e);}}
    private static void invoke(Object object,String name){try{java.lang.reflect.Method m=object.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(object);}catch(Exception e){throw new AssertionError(e);}}
}
