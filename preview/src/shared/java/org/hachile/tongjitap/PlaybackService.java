package org.hachile.tongjitap;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

@UnstableApi
public final class PlaybackService extends MediaSessionService {
    private ExoPlayer player;
    private MediaSession session;
    private LibraryStore store;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable checkpoint = new Runnable() {
        @Override public void run() { savePosition(); handler.postDelayed(this, 5000); }
    };

    @Override public void onCreate() {
        super.onCreate();
        store = new LibraryStore(this);
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("TongjiTap/" + BuildConfig.VERSION_NAME)
                .setDefaultRequestProperties(Collections.singletonMap("Referer", "https://music.163.com/"))
                .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(12000).setReadTimeoutMs(15000);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http)))
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_LOCAL).build();
        SpeedConfig config = store.settings();
        float speed = config.clamp(store.playback.getFloat("speed", 1f));
        player.setPlaybackParameters(new PlaybackParameters(speed, config.preservePitch ? 1f : speed));
        player.setRepeatMode(store.playback.getInt("repeat", Player.REPEAT_MODE_ALL));
        player.setShuffleModeEnabled(store.playback.getBoolean("shuffle", false));
        try {
            List<Track> tracks = store.load();
            List<MediaItem> items = new ArrayList<>();
            int index = 0;
            String last = store.playback.getString("lastId", "");
            for (Track track : tracks) {
                if (track.id.equals(last)) index = items.size();
                items.add(track.mediaItem());
            }
            if (!items.isEmpty()) player.setMediaItems(items, index, Math.max(0, store.playback.getLong("position", 0)));
        } catch (Exception e) { store.prefs.edit().putBoolean("libraryReadError", true).apply(); }
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean playing) { savePosition(); }
            @Override public void onMediaItemTransition(MediaItem item, int reason) { savePosition(); }
            @Override public void onPlaybackParametersChanged(PlaybackParameters parameters) {
                store.playback.edit().putFloat("speed", parameters.speed).apply();
            }
            @Override public void onRepeatModeChanged(int mode) { store.playback.edit().putInt("repeat", mode).apply(); }
            @Override public void onShuffleModeEnabledChanged(boolean enabled) { store.playback.edit().putBoolean("shuffle", enabled).apply(); }
        });
        MediaSession.Builder builder = new MediaSession.Builder(this, player);
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch != null && launch.getComponent() != null) {
            Intent playerPage = new Intent().setComponent(launch.getComponent()).putExtra("tab", Tabs.STRIDE);
            builder.setSessionActivity(PendingIntent.getActivity(this, 0, playerPage,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        session = builder.setCallback(new MediaSession.Callback() {
            @Override public MediaSession.ConnectionResult onConnect(MediaSession s, MediaSession.ControllerInfo controller) {
                if (getPackageName().equals(controller.getPackageName()) || controller.isTrusted()) return MediaSession.Callback.super.onConnect(s, controller);
                return MediaSession.ConnectionResult.reject();
            }
        }).build();
        handler.postDelayed(checkpoint, 5000);
    }

    private void savePosition() {
        if (player == null) return;
        MediaItem item = player.getCurrentMediaItem();
        if (item != null) store.playback.edit().putString("lastId", item.mediaId)
                .putLong("position", Math.max(0, player.getCurrentPosition())).apply();
    }

    @Override public MediaSession onGetSession(MediaSession.ControllerInfo info) { return session; }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        savePosition();
        if (session != null) session.release();
        if (player != null) player.release();
        super.onDestroy();
    }
}
