package org.hachile.tongjitap;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class NeteaseClient {
    static final class Playlist {
        final String name;
        final List<Track> tracks;
        final int total;
        Playlist(String name, List<Track> tracks, int total) { this.name = name; this.tracks = tracks; this.total = total; }
    }

    Playlist fetch(String input) throws Exception {
        String id;
        try { id = NeteaseLinks.extract(input); }
        catch (IllegalArgumentException e) {
            if (!NeteaseLinks.shortLink(input)) throw e;
            id = NeteaseLinks.extract(resolveShortLink(NeteaseLinks.firstShortLink(input)));
        }
        // shortcut: 使用无需登录的公开歌单接口，若平台关闭则需接入获授权的 API。
        JSONObject data = new JSONObject(read("https://music.163.com/api/playlist/detail?id=" + id + "&n=1000"));
        if (data.optInt("code") != 200) throw new IOException("网易云暂时拒绝访问，请确认歌单公开后重试");
        JSONObject playlist = data.optJSONObject("result");
        if (playlist == null) playlist = data.optJSONObject("playlist");
        if (playlist == null) throw new IOException("歌单数据不完整");
        JSONArray songs = playlist.optJSONArray("tracks");
        List<Track> tracks = new ArrayList<>();
        if (songs != null) for (int i = 0; i < songs.length(); i++) {
            JSONObject song = songs.getJSONObject(i);
            String songId = song.getString("id");
            if (!songId.matches("[0-9]{1,20}")) continue;
            JSONArray artists = song.optJSONArray("artists");
            if (artists == null) artists = song.optJSONArray("ar");
            StringBuilder artist = new StringBuilder();
            if (artists != null) for (int a = 0; a < artists.length(); a++) {
                if (a > 0) artist.append(" / ");
                artist.append(artists.getJSONObject(a).optString("name"));
            }
            tracks.add(new Track("netease:" + songId, song.optString("name", "未命名歌曲"), artist.toString(),
                    "https://music.163.com/song/media/outer/url?id=" + songId + ".mp3", true));
        }
        if (tracks.isEmpty()) throw new IOException("未读到歌曲，请确认歌单公开且包含歌曲");
        return new Playlist(playlist.optString("name", "网易云歌单"), tracks, playlist.optInt("trackCount", tracks.size()));
    }

    private HttpURLConnection connection(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 TongjiTap/1.0.0");
        c.setRequestProperty("Referer", "https://music.163.com/");
        c.setInstanceFollowRedirects(false);
        return c;
    }

    private String resolveShortLink(String url) throws Exception {
        for (int hop = 0; hop < 5; hop++) {
            URI target = new URI(url);
            if (!"https".equals(target.getScheme()) || !(NeteaseLinks.trustedHost(target.getHost())
                    || "163cn.tv".equalsIgnoreCase(target.getHost()))) throw new IOException("短链接跳转到不支持的地址");
            if (NeteaseLinks.trustedHost(target.getHost())) return url;
            HttpURLConnection c = connection(url);
            try {
                int code = c.getResponseCode();
                if (code < 300 || code > 399) throw new IOException("短链接解析失败，请使用完整歌单链接或 ID");
                String location = c.getHeaderField("Location");
                if (location == null) throw new IOException("短链接缺少跳转地址");
                URI next = target.resolve(location);
                if ("http".equalsIgnoreCase(next.getScheme()) && NeteaseLinks.trustedHost(next.getHost()))
                    next = new URI("https", next.getRawAuthority(), next.getPath(), next.getQuery(), next.getFragment());
                url = next.toString();
            } finally { c.disconnect(); }
        }
        throw new IOException("短链接跳转次数过多，请使用完整歌单链接或 ID");
    }

    private String read(String url) throws IOException {
        HttpURLConnection c = connection(url);
        try {
            if (c.getResponseCode() != 200) throw new IOException("网络请求失败，请检查网络后重试");
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (out.size() + count > 8 * 1024 * 1024) throw new IOException("歌单过大，请拆分后导入");
                    out.write(buffer, 0, count);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally { c.disconnect(); }
    }
}
