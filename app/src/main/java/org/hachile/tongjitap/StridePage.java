package org.hachile.tongjitap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.BaseAdapter;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
final class StridePage extends android.view.ContextThemeWrapper {
    private final Activity host;
    View root;
    private android.view.Window getWindow() { return host.getWindow(); }
    private boolean isDestroyed() { return host.isDestroyed(); }
    private Intent getIntent() { return host.getIntent(); }
    private void setContentView(View view) { root = view; }
    private void startActivityForResult(Intent intent, int request) { host.startActivityForResult(intent, request); }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LibraryStore store;
    private SpeedConfig config;
    private List<Track> tracks = new ArrayList<>();
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController controller;
    private GesturePad pad;
    private TextView title, artist, status, elapsed, duration, libraryCount;
    private SeekBar seek;
    private MusicUi.Icon play;
    private Button mode, lock;
    private boolean seeking, busy, locked, libraryReadFailed, pendingQueueSync;
    private String bindingId;

    private final Player.Listener listener = new Player.Listener() {
        @Override public void onEvents(Player player, Player.Events events) { render(); }
        @Override public void onPlayerError(PlaybackException error) {
            Track track = currentTrack();
            toast(track != null && track.isLocal() ? L.t("本地文件不可用，请长按歌曲重新绑定音频") : L.t("该在线音频暂不可用，可重试、切歌或绑定本地文件"));
        }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() { renderProgress(); handler.postDelayed(this, 400); }
    };

    StridePage(Activity host, Bundle state) {
        super(host, R.style.AppTheme); this.host = host;
        store = new LibraryStore(this); config = store.settings();
        if (state != null) { bindingId = state.getString("bindingId"); locked = state.getBoolean("locked"); }
        try { tracks = store.load(); } catch (Exception e) { libraryReadFailed = true; toast(L.t("曲库读取失败，已停止写入以保留原数据")); }
        buildUi(); applyScreenFlag();
        if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
            String shared = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) handler.post(() -> showNetease(shared));
        }
    }

    private void buildUi() {
        LinearLayout root = MusicUi.column(this); root.setBackgroundColor(MusicUi.BG);
        int gutter = MusicUi.dp(this, 24); root.setPadding(gutter, 0, gutter, MusicUi.dp(this, 8));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
        LinearLayout header = MusicUi.row(this);
        TextView brand = MusicUi.text(this, L.t("随步"), 24, MusicUi.TEXT); brand.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, -1, 1));
        TextView wordmark = MusicUi.text(this, "Stride", 10, MusicUi.MUTED); wordmark.setLetterSpacing(.14f);
        if (!L.language.equals("zh")) header.addView(wordmark, MusicUi.size(this, 80, -1));
        header.addView(new MusicUi.Icon(this, "settings", L.t("调速设置"), false, this::settings), MusicUi.size(this, 48, 48));
        root.addView(header, MusicUi.size(this, -1, 64));
        LinearLayout topline = MusicUi.row(this);
        TextView badge = MusicUi.text(this, L.t("●  跟上自己的节奏"), 12, MusicUi.ACCENT);
        topline.addView(badge, new LinearLayout.LayoutParams(0, -1, 1));
        libraryCount = MusicUi.text(this, L.t("0 首"), 12, MusicUi.MUTED); topline.addView(libraryCount);
        root.addView(topline, MusicUi.size(this, -1, 32));
        pad = new GesturePad(this, config, this::setSpeed);
        pad.setSpeed(config.clamp(store.playback.getFloat("speed", 1f))); pad.setLocked(locked);
        root.addView(pad, new LinearLayout.LayoutParams(-1, MusicUi.dp(this, 280)));
        View line = new View(this); line.setBackgroundColor(MusicUi.LINE);
        LinearLayout.LayoutParams lineParams = MusicUi.size(this, -1, 1); lineParams.topMargin = MusicUi.dp(this, 16); root.addView(line, lineParams);
        LinearLayout song = MusicUi.column(this); song.setGravity(Gravity.CENTER_VERTICAL);
        title = MusicUi.metadata(this, L.t("为这次跑步选首歌"), 21, MusicUi.TEXT); title.setTypeface(Typeface.DEFAULT_BOLD); title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        artist = MusicUi.metadata(this, L.t("导入本地音频，或粘贴网易云歌单"), 13, MusicUi.MUTED); artist.setMaxLines(1); artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        song.addView(title); song.addView(artist); song.setOnClickListener(v -> showLibrary()); root.addView(song, MusicUi.size(this, -1, 76));
        status = MusicUi.text(this, L.t("首次使用，先添加你的音乐"), 12, MusicUi.MUTED); status.setMaxLines(2);
        status.setOnClickListener(v -> { Track t = currentTrack(); if (t != null) trackActions(t); else showLibrary(); });
        root.addView(status, MusicUi.size(this, -1, 52));
        seek = new SeekBar(this); seek.setMax(1000); seek.setPadding(0, 0, 0, 0); seek.setContentDescription(L.t("歌曲播放进度"));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && controller != null && controller.getDuration() > 0) elapsed.setText(time(controller.getDuration() * progress / 1000));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (controller != null && controller.getDuration() > 0) controller.seekTo(controller.getDuration() * bar.getProgress() / 1000);
                seeking = false;
            }
        });
        root.addView(seek, MusicUi.size(this, -1, 32));
        LinearLayout times = MusicUi.row(this); elapsed = MusicUi.text(this, "0:00", 11, MusicUi.MUTED); duration = MusicUi.text(this, "0:00", 11, MusicUi.MUTED);
        duration.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        times.addView(elapsed, new LinearLayout.LayoutParams(0, -1, 1)); times.addView(duration); root.addView(times, MusicUi.size(this, -1, 28));
        LinearLayout controls = MusicUi.row(this); controls.setGravity(Gravity.CENTER); controls.setPadding(0, MusicUi.dp(this, 8), 0, MusicUi.dp(this, 8));
        controls.addView(new MusicUi.Icon(this, "prev", L.t("上一首"), false, () -> skip(false)), MusicUi.size(this, 56, 56));
        play = new MusicUi.Icon(this, "play", L.t("播放"), true, this::togglePlay);
        LinearLayout.LayoutParams playParams = MusicUi.size(this, 72, 64); playParams.setMargins(MusicUi.dp(this, 28), 0, MusicUi.dp(this, 28), 0);
        controls.addView(play, playParams);
        controls.addView(new MusicUi.Icon(this, "next", L.t("下一首"), false, () -> skip(true)), MusicUi.size(this, 56, 56));
        root.addView(controls, MusicUi.size(this, -1, 88));
        LinearLayout bottom = MusicUi.row(this);
        Button library = MusicUi.button(this, L.t("曲库"), this::showLibrary);
        mode = MusicUi.button(this, L.t("列表循环"), this::cycleMode);
        lock = MusicUi.button(this, locked ? L.t("解锁手势") : L.t("锁定手势"), () -> {
            locked = !locked; pad.setLocked(locked); lock.setText(locked ? L.t("解锁手势") : L.t("锁定手势"));
        });
        for (Button b : new Button[]{library, mode, lock}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, MusicUi.dp(this, 48), 1); p.setMargins(MusicUi.dp(this, 3), 0, MusicUi.dp(this, 3), 0); bottom.addView(b, p);
        }
        root.addView(bottom, MusicUi.size(this, -1, 56)); render();
    }

    void onStart() {
        if (controllerFuture != null) return;
        controllerFuture = new MediaController.Builder(this, new SessionToken(this, new ComponentName(this, PlaybackService.class))).buildAsync();
        ListenableFuture<MediaController> expected = controllerFuture;
        expected.addListener(() -> {
            if (controllerFuture != expected || isDestroyed()) return;
            try {
                controller = expected.get(); controller.addListener(listener);
                if (pendingQueueSync) commitLibrary(new ArrayList<>(tracks));
                setSpeed(config.clamp(store.playback.getFloat("speed", 1f))); render();
            }
            catch (Exception e) { toast(L.t("播放器连接失败，请重新打开应用")); }
        }, command -> handler.post(command));
        handler.post(tick);
    }

    void onStop() {
        handler.removeCallbacks(tick);
        if (controller != null) { controller.removeListener(listener); controller = null; }
        if (controllerFuture != null) { MediaController.releaseFuture(controllerFuture); controllerFuture = null; }

    }

    void onDestroy() { io.shutdownNow(); onStop(); }
    void onSaveInstanceState(Bundle state) { state.putString("bindingId", bindingId); state.putBoolean("locked", locked);  }

    void applyScreenFlag() {
        if (config.keepScreenOn) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void settings() {
        SettingsDialog.show(this, config, updated -> {
            config = updated; store.saveSettings(config); pad.config = config; setSpeed(config.clamp(pad.speed)); applyScreenFlag(); pad.invalidate();
        });
    }

    private void setSpeed(float value) {
        value = config.clamp(value); pad.setSpeed(value); store.playback.edit().putFloat("speed", value).apply();
        if (controller != null) controller.setPlaybackParameters(new PlaybackParameters(value, config.preservePitch ? 1f : value));
    }

    private Track currentTrack() {
        if (controller == null || controller.getCurrentMediaItem() == null) return null;
        String id = controller.getCurrentMediaItem().mediaId;
        for (Track track : tracks) if (track.id.equals(id)) return track;
        return null;
    }

    private void render() {
        if (pad == null) return;
        libraryCount.setText(String.format(Locale.ROOT, "%d%s", tracks.size(), L.t(" 首")));
        Track track = currentTrack();
        if (track != null) { title.setText(track.title); artist.setText(track.artist); }
        else if (tracks.isEmpty()) { title.setText(L.t("为这次跑步选首歌")); artist.setText(L.t("导入本地音频，或粘贴网易云歌单")); }
        boolean playing = controller != null && controller.getPlayWhenReady() && controller.getPlaybackState() != Player.STATE_ENDED;
        play.symbol(playing ? "pause" : "play", playing ? L.t("暂停") : L.t("播放"));
        if (controller != null) {
            pad.setSpeed(controller.getPlaybackParameters().speed);
            mode.setText(controller.getShuffleModeEnabled() ? L.t("随机播放") : controller.getRepeatMode() == Player.REPEAT_MODE_ONE ? L.t("单曲循环") : L.t("列表循环"));
        }
        String message = track == null ? L.t("首次使用，先添加你的音乐") : track.isLocal() ? L.t("本地音频，可离线播放") : L.t("网易云在线音频，将使用网络流量");
        if (controller != null && controller.getPlaybackState() == Player.STATE_BUFFERING) message = L.t("正在缓冲…");
        if (controller != null && controller.getPlayerError() != null) message = L.t("音频不可用，点此绑定本地文件；播放键可重试");
        if (busy) message = L.t("正在导入，请稍候…");
        status.setText(message); renderProgress();
    }

    private void renderProgress() {
        if (controller == null || seeking || seek == null) return;
        long total = controller.getDuration(), position = controller.getCurrentPosition();
        boolean available = total > 0 && total != C.TIME_UNSET;
        seek.setEnabled(available);
        int progress = available ? (int) Math.min(1000, position * 1000 / total) : 0;
        if (seek.getProgress() != progress) seek.setProgress(progress);
        String currentTime = time(position), totalTime = time(available ? total : 0);
        if (!elapsed.getText().toString().equals(currentTime)) elapsed.setText(currentTime);
        if (!duration.getText().toString().equals(totalTime)) duration.setText(totalTime);
    }

    private static String time(long ms) { long s = Math.max(0, ms / 1000); return String.format(Locale.US, "%d:%02d", s / 60, s % 60); }

    private boolean connected() {
        if (controller != null) return true;
        toast(L.t("播放器正在连接，请稍候")); return false;
    }

    private void togglePlay() {
        if (!connected()) return;
        if (tracks.isEmpty()) { showLibrary(); return; }
        if (controller.getPlayWhenReady() && controller.getPlaybackState() != Player.STATE_ENDED && controller.getPlayerError() == null) controller.pause();
        else {
            if (controller.getPlaybackState() == Player.STATE_IDLE || controller.getPlayerError() != null) controller.prepare();
            if (controller.getPlaybackState() == Player.STATE_ENDED) controller.seekTo(0);
            controller.play();
        }
    }

    private void skip(boolean next) {
        if (!connected() || tracks.isEmpty()) return;
        if (next) controller.seekToNextMediaItem(); else controller.seekToPreviousMediaItem();
        if (controller.getPlaybackState() == Player.STATE_IDLE) controller.prepare();
    }

    private void cycleMode() {
        if (!connected()) return;
        if (controller.getShuffleModeEnabled()) { controller.setShuffleModeEnabled(false); controller.setRepeatMode(Player.REPEAT_MODE_ALL); }
        else if (controller.getRepeatMode() == Player.REPEAT_MODE_ALL) controller.setRepeatMode(Player.REPEAT_MODE_ONE);
        else { controller.setRepeatMode(Player.REPEAT_MODE_ALL); controller.setShuffleModeEnabled(true); }
    }

    private void commitLibrary(List<Track> next) {
        if (libraryReadFailed) { toast(L.t("曲库数据异常，已保留原数据，请先修复后再导入")); return; }
        try { store.save(next); } catch (Exception e) { toast(L.t("曲库保存失败，未更改列表")); return; }
        Track current = currentTrack();
        boolean wasPlaying = controller != null && controller.getPlayWhenReady();
        long position = controller == null ? 0 : controller.getCurrentPosition();
        tracks = next;
        pendingQueueSync = controller == null;
        if (controller != null) {
            if (tracks.isEmpty()) { controller.stop(); controller.clearMediaItems(); }
            else {
                int index = 0; boolean kept = false;
                List<MediaItem> items = new ArrayList<>();
                for (Track t : tracks) {
                    if (current != null && t.id.equals(current.id)) { index = items.size(); kept = true; }
                    items.add(t.mediaItem());
                }
                controller.setMediaItems(items, index, kept ? position : 0); controller.prepare(); controller.setPlayWhenReady(wasPlaying);
            }
        }
        render();
    }

    private void showLibrary() {
        LinearLayout content = MusicUi.column(this); int margin = MusicUi.dp(this, 16); content.setPadding(margin, 0, margin, 0);
        LinearLayout actions = MusicUi.row(this);
        AlertDialog dialog = new Ui.LocalDialog(this).setTitle(L.t("跑步曲库")).setView(content).setNegativeButton(L.t("关闭"), null).create();
        Button local = MusicUi.button(this, L.t("+ 本地音频"), () -> { dialog.dismiss(); pickAudio(null); });
        Button netease = MusicUi.button(this, L.t("+ 网易云歌单"), () -> { dialog.dismiss(); showNetease(""); });
        actions.addView(local, new LinearLayout.LayoutParams(0, MusicUi.dp(this, 48), 1));
        actions.addView(netease, new LinearLayout.LayoutParams(0, MusicUi.dp(this, 48), 1)); content.addView(actions);
        TextView hint = MusicUi.text(this, tracks.isEmpty() ? L.t("添加音乐后就能开始，文件保留在原位置。") : L.t("点击播放，长按绑定本地文件或移除。"), 12, MusicUi.MUTED);
        content.addView(hint, MusicUi.size(this, -1, 48));
        List<Track> snapshot = new ArrayList<>(tracks);
        ListView list = new ListView(this); list.setDividerHeight(MusicUi.dp(this, 1));
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return snapshot.size(); }
            @Override public Object getItem(int index) { return snapshot.get(index); }
            @Override public long getItemId(int index) { return index; }
            @Override public View getView(int index, View recycled, android.view.ViewGroup parent) {
                Track t = snapshot.get(index); LinearLayout row = MusicUi.column(StridePage.this); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(MusicUi.dp(StridePage.this, 8), MusicUi.dp(StridePage.this, 10), MusicUi.dp(StridePage.this, 8), MusicUi.dp(StridePage.this, 10));
                TextView name = MusicUi.metadata(StridePage.this, String.format(Locale.CHINA, "%02d  %s", index + 1, t.title), 16, MusicUi.TEXT);
                name.setMaxLines(2); row.addView(name);
                row.addView(MusicUi.metadata(StridePage.this, t.artist + " / " + (t.isLocal() ? L.t("本地") : L.t("在线，部分曲目可能受限")), 12, MusicUi.MUTED));
                return row;
            }
        });
        list.setOnItemClickListener((parent, view, index, id) -> {
            if (!connected()) return;
            controller.seekTo(index, 0); controller.prepare(); controller.play(); dialog.dismiss();
        });
        list.setOnItemLongClickListener((parent, view, index, id) -> { dialog.dismiss(); trackActions(snapshot.get(index)); return true; });
        content.addView(list, MusicUi.size(this, -1, Math.min(380, Math.max(80, tracks.size() * 72))));
        dialog.show();
    }

    private void trackActions(Track track) {
        new Ui.LocalDialog(this).setTitle(track.title).setItems(new String[]{L.t("绑定 / 更换本地音频"), L.t("从曲库移除")}, (dialog, choice) -> {
            if (choice == 0) pickAudio(track.id);
            else { List<Track> next = new ArrayList<>(tracks); next.removeIf(t -> t.id.equals(track.id)); commitLibrary(next); }
        }).setNegativeButton(L.t("取消"), null).show();
    }

    private void pickAudio(String id) {
        if (busy) { toast(L.t("正在导入，请稍候")); return; }
        bindingId = id;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, id == null)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, 100); } catch (android.content.ActivityNotFoundException e) { toast(L.t("系统没有可用的文件选择器")); }
    }

    void onActivityResult(int request, int result, Intent data) {

        if (request != 100 || result != Activity.RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>(); ClipData clip = data.getClipData();
        if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        if (uris.isEmpty()) return;
        String bind = bindingId; bindingId = null; busy = true; render();
        io.execute(() -> {
            List<Track> added = new ArrayList<>(); List<String> errors = new ArrayList<>();
            for (Uri uri : uris) try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                added.add(LocalAudio.read(this, uri));
            } catch (Exception e) { errors.add(e.getMessage() == null ? L.t("文件不可读或无法保留访问权限") : e.getMessage()); }
            handler.post(() -> {
                if (isDestroyed()) return;
                busy = false;
                if (!added.isEmpty()) {
                    if (bind != null) {
                        List<Track> next = new ArrayList<>(tracks);
                        for (int i = 0; i < next.size(); i++) if (next.get(i).id.equals(bind)) next.set(i, next.get(i).withLocal(added.get(0)));
                        commitLibrary(next); toast(L.t("已绑定本地音频"));
                    } else { commitLibrary(LibraryOps.merge(tracks, added)); toast(L.t("已读取 ") + added.size() + L.t(" 首本地音频，同名曲目已尝试匹配")); }
                }
                render();
                if (!errors.isEmpty()) new Ui.LocalDialog(this).setTitle(errors.size() + L.t(" 个文件未导入"))
                        .setMessage(String.join("\n", errors.subList(0, Math.min(3, errors.size())))).setPositiveButton(L.t("知道了"), null).show();
            });
        });
    }

    private void showNetease(String initial) {
        if (busy) { toast(L.t("正在导入，请稍候")); return; }
        EditText input = new EditText(this); input.setText(initial); input.setHint(L.t("粘贴歌单链接、分享文本或数字 ID"));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout form = MusicUi.column(this); int p = MusicUi.dp(this, 24); form.setPadding(p, 0, p, 0);
        form.addView(MusicUi.text(this, L.t("公开歌单可导入；部分在线曲目可能受限或仅能试听。\n可绑定你已有的普通本地音频，不能直接播放 NCM。"), 13, MusicUi.MUTED), MusicUi.size(this, -1, 72));
        form.addView(input, MusicUi.size(this, -1, 96));
        AlertDialog dialog = new Ui.LocalDialog(this).setTitle(L.t("导入网易云歌单")).setView(form).setNegativeButton(L.t("取消"), null).setPositiveButton(L.t("导入"), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) { input.setError(L.t("请输入歌单链接或 ID")); return; }
            dialog.dismiss(); busy = true; render();
            io.execute(() -> {
                try {
                    NeteaseClient.Playlist playlist = new NeteaseClient().fetch(text);
                    handler.post(() -> {
                        if (isDestroyed()) return;
                        busy = false; commitLibrary(LibraryOps.merge(tracks, playlist.tracks));
                        String message = L.t("已导入《") + playlist.name + L.t("》的 ") + playlist.tracks.size() + L.t(" 首歌曲。");
                        if (playlist.total > playlist.tracks.size()) message += L.t("平台只返回了部分曲目（歌单共 ") + playlist.total + L.t(" 首）。");
                        new Ui.LocalDialog(this).setTitle(L.t("歌单已导入")).setMessage(message + L.t("\n在线音频会使用流量，跑前请确认能播放；长按歌曲可绑定本地音频。"))
                                .setPositiveButton(L.t("查看曲库"), (d, which) -> showLibrary()).show();
                    });
                } catch (Exception e) {
                    handler.post(() -> {
                        if (isDestroyed()) return;
                        busy = false; render();
                        new Ui.LocalDialog(this).setTitle(L.t("导入未完成")).setMessage(e instanceof java.net.UnknownHostException ? L.t("无法连接网络，请检查网络后重试") : e.getMessage())
                                .setPositiveButton(L.t("重试"), (d, which) -> showNetease(text)).setNegativeButton(L.t("取消"), null).show();
                    });
                }
            });
        }));
        dialog.show();
    }

    private void toast(String text) { Toast.makeText(this, L.t(text), Toast.LENGTH_LONG).show(); }
}
