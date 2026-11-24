package org.hachile.tongjitap;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.*;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.util.function.IntConsumer;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class GuidedTutorial {
    final LinearLayout root;
    private final Activity host;
    private final IntConsumer finished;
    private final Runnable copyGroup;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int track = -1, step, cycles;
    private int duration = 50, delay = 500, repeats = 2;
    private SpeedConfig config = new SpeedConfig();
    private float speed = 1;
    private MediaPlayer player;
    private AudioFocusRequest audioFocus;
    private AlertDialog dialog;
    private FrameLayout stage;
    private LinearLayout page, help;
    private View target;
    private Button continueButton;
    private boolean closed;

    GuidedTutorial(Activity host, Bundle state, IntConsumer finished, Runnable copyGroup) {
        this.host = host; this.finished = finished; this.copyGroup = copyGroup; root = Ui.column(host); root.setBackgroundColor(Ui.BG);
        if (state != null) {
            track = state.getInt("guide.track", -1); step = state.getInt("guide.step", 0); cycles = state.getInt("guide.cycles", 0);
            duration = state.getInt("guide.duration", 50); delay = state.getInt("guide.delay", 500); repeats = state.getInt("guide.repeats", 2);
            speed = state.getFloat("guide.speed", 1);
        }
        if (track < -1 || track > 2) track = -1;
        step = Math.max(0, Math.min(step, total())); render();
    }
    void save(Bundle state) {
        state.putInt("guide.track", track); state.putInt("guide.step", step); state.putInt("guide.cycles", cycles);
        state.putInt("guide.duration", duration); state.putInt("guide.delay", delay); state.putInt("guide.repeats", repeats); state.putFloat("guide.speed", speed);
    }
    void pause() {
        if (player != null && player.isPlaying()) player.pause();
        if (audioFocus != null) { host.getSystemService(AudioManager.class).abandonAudioFocusRequest(audioFocus); audioFocus = null; }
    }
    void close() { closed = true; handler.removeCallbacksAndMessages(null); if (dialog != null) dialog.dismiss(); pause(); if (player != null) { player.release(); player = null; } }
    private int total() { return track == 1 ? 5 : 7; }
    private String name() { return L.t(track == 0 ? "循济" : track == 1 ? "随步" : "跃点"); }
    private void next() { step++; render(); }
    private void render() {
        if (closed) return; handler.removeCallbacksAndMessages(null); root.removeAllViews(); root.setOrientation(LinearLayout.VERTICAL); target = null;
        if (track == -1) { chooser(); return; }
        if (step >= total()) { completion(); return; }
        boolean landscape = host.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (landscape) root.setOrientation(LinearLayout.HORIZONTAL);
        stage = new FrameLayout(host); root.addView(stage,landscape ? new LinearLayout.LayoutParams(0,-1,1) : new LinearLayout.LayoutParams(-1,0,1));
        ScrollView scroll = new ScrollView(host); scroll.setFillViewport(true); page = Ui.column(host); int p = Ui.dp(host,20); page.setPadding(p,p,p,p);
        scroll.addView(page); stage.addView(scroll);
        page.addView(Ui.title(host,name())); page.addView(Ui.text(host,"新手练习：示例内容不会保存到实际配置",Ui.MUTED)); Ui.gap(page,16);
        help = Ui.card(host); int h = Ui.dp(host,16); help.setPadding(h,h,h,h);
        ScrollView helpScroll = new ScrollView(host) {
            @Override protected void onMeasure(int width, int height) {
                if (!landscape) {
                    int available = root.getHeight() > 0 ? root.getHeight() : host.getResources().getDisplayMetrics().heightPixels;
                    height = MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(height), available * 45 / 100), MeasureSpec.AT_MOST);
                }
                super.onMeasure(width, height);
            }
        };
        helpScroll.addView(help); root.addView(helpScroll,landscape ? new LinearLayout.LayoutParams(0,-1,1) : new LinearLayout.LayoutParams(-1,-2));
        if (track == 0) loop(); else if (track == 1) stride(); else relay();
        help.addView(Ui.button(host,"退出教程",false,v -> leave()));
        if (target != null) {
            View focus = target; CoachOverlay shade = new CoachOverlay(host,focus); stage.addView(shade,new FrameLayout.LayoutParams(-1,-1));
            root.post(() -> { if (!closed && focus.isAttachedToWindow()) { focus.requestRectangleOnScreen(new Rect(0,0,focus.getWidth(),focus.getHeight()),true); shade.invalidate(); focus.requestFocus(); } });
        }
    }
    private void chooser() {
        ScrollView scroll = new ScrollView(host); LinearLayout choices = Ui.column(host); int p=Ui.dp(host,24); choices.setPadding(p,p,p,p); scroll.addView(choices); root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        choices.addView(Ui.title(host,"你想先学哪个功能？")); Ui.gap(choices,12);
        choices.addView(Ui.text(host,"选一个，跟着高亮提示亲手试一遍。其他教程随时可以在“更多”查看。",Ui.MUTED)); Ui.gap(choices,24);
        String[] labels={"自动控制开始和暂停","跑步时切换音乐倍速","自动控制 Wi-Fi 开关"};
        for(int i=0;i<3;i++){final int choice=i; choices.addView(Ui.button(host,labels[i],true,v->{track=choice;step=0;cycles=0;render();}));Ui.gap(choices,12);}
        choices.addView(Ui.button(host,"稍后再学",false,v->leave()));
    }
    private void completion() {
        pause(); ScrollView scroll=new ScrollView(host); LinearLayout done=Ui.column(host);int p=Ui.dp(host,24);done.setPadding(p,p,p,p);scroll.addView(done);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        done.addView(Ui.title(host,"练习完成")); Ui.gap(done,16);
        done.addView(Ui.heading(host,name()));
        done.addView(Ui.text(host,"正式使用时，请换成自己的截图或音乐。可在“更多”的“新手教程”中查看其他栏目的教程。",Ui.INK));
        done.addView(Ui.button(host,"QQ 交流群："+MainActivity.QQ_GROUP,true,v->copyGroup.run()));
        done.addView(Ui.button(host,"开始使用",true,v->finished.accept(track)));
        done.addView(Ui.button(host,"查看其他教程",false,v->{track=-1;step=0;render();}));
    }
    private void leave() { pause(); finished.accept(track < 0 ? 0 : track); }
    private void hint(String text) { help.addView(Ui.heading(host,(step+1)+" / "+total())); help.addView(Ui.text(host,text,Ui.INK)); }
    private Button action(String label, int at, Runnable click) {
        Button button=Ui.button(host,label,true,v->click.run()); button.setEnabled(step==at); page.addView(button); if(step==at)target=button;return button;
    }
    private void loop() {
        page.addView(Ui.heading(host,"点击步骤"));
        if(step>0)page.addView(Ui.text(host,"1. 暂停跑步",Ui.INK));
        if(step>1)page.addView(Ui.text(host,"2. 继续跑步",Ui.INK));
        action("从截图添加步骤",0,()->pick(TutorialScreens.PAUSE,false,false));
        action("再添加一个点击步骤",1,()->pick(TutorialScreens.RESUME,false,false));
        action("编辑按住和等待时间",2,this::timing);
        action("设置循环次数",3,this::loops);
        action("打开悬浮控制面板",4,this::next);
        if(step>=5){ LinearLayout panel=Ui.card(host); panel.addView(Ui.heading(host,"悬浮控制面板（练习）"));
            Button run=Ui.button(host,"开始",true,v->next());run.setEnabled(step==5);panel.addView(run);
            Button stop=Ui.button(host,"停止",true,v->next());stop.setEnabled(step==6);panel.addView(stop);page.addView(panel);target=step==5?run:stop;
            if(step==6)panel.addView(Ui.text(host,"正在演练：暂停 → 等待 → 继续",Ui.BLUE));
        }
        String[] tips={"点亮着的按钮打开示例截图，再点“暂停跑步”并保存。正式使用时导入自己的完整截图。","再选一次截图里的“继续跑步”，把两个位置按顺序加入步骤。","打开编辑窗口，设置按住时长和释放后等待时间，然后保存。","设置整组循环次数并保存。全部步骤执行一遍，算作一次循环。","步骤准备好了。点击打开控制面板；正式使用时需要先授权辅助功能。","点击面板的“开始”试一次。正式运行时，先回到目标页面并移开面板。","点击“停止”结束演练。误入设置页时，还可在“颜色触发”里配置检测颜色和返回动作。"};hint(tips[step]);
    }
    private void timing() {
        LinearLayout form=Ui.column(host); EditText press=number(form,"按住时长（毫秒）",duration), wait=number(form,"等待时间（毫秒）",delay);
        showForm("编辑按住和等待时间",form,()->{int a=positive(press,1,10000),b=positive(wait,0,3600000);duration=a;delay=b;});
    }
    private void loops() { LinearLayout form=Ui.column(host);EditText count=number(form,"循环次数",repeats);showForm("设置循环次数",form,()->repeats=positive(count,1,100000)); }
    private EditText number(LinearLayout form,String label,int value){form.addView(Ui.text(host,label,Ui.MUTED));EditText e=new EditText(host);e.setTextSize(Ui.BODY_SP);e.setText(String.valueOf(value));e.setContentDescription(L.t(label));e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setSelectAllOnFocus(true);form.addView(e);return e;}
    private int positive(EditText field,int min,int max){try{int n=Integer.parseInt(field.getText().toString());if(n<min||n>max)throw new NumberFormatException();return n;}catch(NumberFormatException e){field.setError(L.t("请输入范围内的整数")+" ("+min+"–"+max+")");throw new IllegalArgumentException();}}
    private void showForm(String title,LinearLayout form,Runnable save){int p=Ui.dp(host,20);form.setPadding(p,p,p,p);dialog=Ui.dialog(host,title).setView(form).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{save.run();dialog.dismiss();next();}catch(IllegalArgumentException ignored){}});}
    private void stride() {
        action("曲库",0,()->{dialog=Ui.dialog(host,"添加音乐").setMessage("正式使用时，可以导入本地音频或添加网易云歌单。现在先用一段示例节拍练习。").setNegativeButton("取消",null).setPositiveButton("载入示例节拍",(d,w)->next()).show();});
        page.addView(Ui.text(host,step>0?"示例节拍":"先从曲库添加音乐",Ui.INK));
        action("播放",1,()->{play();next();});
        GesturePad pad=new GesturePad(host,config,value->{speed=value; if(player!=null&&player.isPlaying())player.setPlaybackParams(new PlaybackParams().setSpeed(value).setPitch(config.preservePitch?1:value));if(continueButton!=null)continueButton.setEnabled(Math.abs(value-1)>.04f);});
        pad.setSpeed(speed);pad.setEnabled(step==2);pad.setLocked(step!=2);page.addView(pad,new LinearLayout.LayoutParams(-1,Ui.dp(host,210)));if(step==2)target=pad;
        action("调速设置",3,()->dialog=SettingsDialog.show(host,config,updated->{config=updated;handler.post(this::next);}));
        action("暂停",4,()->{pause();next();});
        String[] tips={"打开曲库，先载入示例节拍。自己的音乐也从这里添加。","点击播放，听听这段节拍。","在亮着的调速区域左右滑动，亲手改变一次倍速，再点“继续”。","打开调速设置，试着调整速度范围或手势，再点保存。","点击暂停结束练习。正式播放时也可以在后台或用耳机控制。"};hint(tips[step]);
        continueButton=null;if(step==2){continueButton=Ui.button(host,"继续",true,v->next());continueButton.setEnabled(Math.abs(speed-1)>.04f);help.addView(continueButton);}
    }
    private void play() {
        try {
            audioFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(change -> { if (change < 0) pause(); }, handler).build();
            if (host.getSystemService(AudioManager.class).requestAudioFocus(audioFocus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                throw new IllegalStateException("Preview audio focus unavailable");
            if (player == null) { player = MediaPlayer.create(host, R.raw.tutorial_beat); if (player != null) player.setLooping(true); }
            if (player == null) throw new IllegalStateException("Preview audio unavailable");
            player.setVolume(.35f,.35f); player.start();
        } catch (RuntimeException e) { pause(); Toast.makeText(host,L.t("暂时无法试听，仍可练习调速"),Toast.LENGTH_LONG).show(); }
    }
    private void relay() {
        action("确认网络准备",0,this::preparation);
        action("框选地图区域",1,()->pick(TutorialScreens.MAP,true,false));
        action("框选里程区域",2,()->pick(TutorialScreens.DISTANCE,true,false));
        action("选择 Wi-Fi 开关",3,()->pick(TutorialScreens.WIFI,false,true));
        action("打开跃点控制面板",4,this::next);
        if(step>=5){action("开始",5,()->{cycles=0;next();});
            ImageView scene = new ImageView(host); scene.setImageBitmap(TutorialScreens.make(false, cycles));
            scene.setContentDescription(L.t("绿点与里程变化演示")); scene.setAdjustViewBounds(true);
            page.addView(scene, new LinearLayout.LayoutParams(-1, Ui.dp(host, 200)));
            TextView state=Ui.text(host,"演练不会改变手机的 Wi-Fi 或移动数据",Ui.INK);page.addView(state);
            Button stop=action("停止",6,this::next);if(step==6){stop.setEnabled(cycles>=2);if(cycles<2){
                Runnable tick=new Runnable(){@Override public void run(){if(closed||track!=2||step!=6)return;cycles++;scene.setImageBitmap(TutorialScreens.make(false, cycles));state.setText(L.t(cycles%2==1?"绿点移动，里程 +0.03 km：关闭 Wi-Fi（演练）":"绿点再次移动，里程 +0.03 km：打开 Wi-Fi（演练）"));if(cycles<2)handler.postDelayed(this,1300);else stop.setEnabled(true);}};handler.postDelayed(tick,1000);
            }}}
        String[] tips={"先了解网络准备。正式使用前，要开启 Wi-Fi 和移动数据，并为 TJ-WIFI 保存密码、开启自动连接。","框选示例地图：先点左上角并确认，再点右下角保存。地图要包含绿点可能出现的位置。","用同样的方法框选当前里程，只留下数字和单位，避开时间与其他距离。","在快捷设置示例中点选 Wi-Fi 开关，再保存。正式使用时需要用自己手机的快捷设置截图。","三个区域都已练习完成。打开跃点控制面板，了解如何运行。","点击开始，观察一次 Wi-Fi 关闭再打开的演练。真实检测需要 Android 11 或以上。","绿点明显移动且里程增加超过 0.02 km 才会触发。看完关、开两次演示后，点停止；正式停止后请自行确认 Wi-Fi 状态。"};hint(tips[step]);
    }
    private void preparation(){LinearLayout form=Ui.column(host);int p=Ui.dp(host,20);form.setPadding(p,p,p,p);form.addView(Ui.text(host,"这里只练习准备流程，不会改变手机网络。正式开始前请核对真实设置。",Ui.MUTED));CheckBox[] checks=new CheckBox[3];String[] labels={"开启 Wi-Fi","开启移动数据","TJ-WIFI 保存密码并自动连接"};for(int i=0;i<3;i++){checks[i]=new CheckBox(host);checks[i].setText(L.t(labels[i]));checks[i].setTextSize(Ui.BODY_SP);form.addView(checks[i]);}dialog=Ui.dialog(host,"网络准备").setView(form).setNegativeButton("取消",null).setPositiveButton("准备好了",(d,w)->next()).create();dialog.show();Button confirm=dialog.getButton(AlertDialog.BUTTON_POSITIVE);confirm.setEnabled(false);for(CheckBox check:checks)check.setOnCheckedChangeListener((b,c)->confirm.setEnabled(checks[0].isChecked()&&checks[1].isChecked()&&checks[2].isChecked()));}
    private void pick(Rect expected,boolean region,boolean wifi) {
        LinearLayout content=Ui.column(host);TextView tip=Ui.text(host,region?"先点区域左上角，再确认":"点选示例里对应的按钮，再保存",Ui.INK);content.addView(tip);
        int[] point={-1,-1},anchor={-1,-1};
        dialog=Ui.dialog(host,region?"框选练习":"截图选点练习").setView(content).setNegativeButton("取消",null).setPositiveButton(region?"确认左上角":"保存",null).create();AlertDialog current=dialog;
        ScreenshotPickerView picker=new ScreenshotPickerView(host,TutorialScreens.make(wifi,0),(x,y,color)->{point[0]=x;point[1]=y;Button confirm=current.getButton(AlertDialog.BUTTON_POSITIVE);if(confirm!=null)confirm.setEnabled(true);});
        content.addView(picker,new LinearLayout.LayoutParams(-1,0,1));current.setOnDismissListener(d->picker.release());current.show();current.getWindow().setLayout(-1,-1);
        Button confirm=current.getButton(AlertDialog.BUTTON_POSITIVE);confirm.setEnabled(false);
        confirm.setOnClickListener(v->{if(point[0]<0)return;
            if(region&&anchor[0]<0){anchor[0]=point[0];anchor[1]=point[1];picker.regionAnchor(anchor[0],anchor[1]);point[0]=-1;confirm.setEnabled(false);confirm.setText(L.t("保存区域"));tip.setText("再点区域右下角，然后保存");return;}
            boolean valid=region?anchor[0]<point[0]&&anchor[1]<point[1]&&Math.abs(anchor[0]-expected.left)<=28&&Math.abs(anchor[1]-expected.top)<=28&&Math.abs(point[0]-expected.right)<=28&&Math.abs(point[1]-expected.bottom)<=28:expected.contains(point[0],point[1]);
            if(!valid){tip.setText(region?"范围不对，请重新选择左上角和右下角":"请点选提示中的按钮位置");if(region){anchor[0]=-1;picker.regionAnchor(-1,-1);confirm.setText(L.t("确认左上角"));}return;}
            current.dismiss();next();
        });
    }
}
