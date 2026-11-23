package org.hachile.tongjitap;

import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import org.json.JSONException;
import org.json.JSONObject;

final class Track {
    final String id;
    final String title;
    final String artist;
    final String uri;
    final boolean netease;

    Track(String id, String title, String artist, String uri, boolean netease) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.uri = uri;
        this.netease = netease;
    }

    boolean isLocal() { return uri.startsWith("content://") || uri.startsWith("file://"); }

    Track withLocal(Track local) { return new Track(id, title, artist, local.uri, netease); }

    MediaItem mediaItem() {
        return new MediaItem.Builder().setMediaId(id).setUri(Uri.parse(uri))
                .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).setArtist(artist).build()).build();
    }

    JSONObject json() throws JSONException {
        return new JSONObject().put("id", id).put("title", title).put("artist", artist)
                .put("uri", uri).put("netease", netease);
    }

    static Track fromJson(JSONObject value) throws JSONException {
        String uri = value.getString("uri");
        if (!uri.startsWith("content://") && !uri.startsWith("https://music.163.com/song/media/outer/url?id="))
            throw new JSONException("Unsupported stored audio URI");
        return new Track(value.getString("id"), value.getString("title"), value.optString("artist"),
                uri, value.optBoolean("netease"));
    }
}
