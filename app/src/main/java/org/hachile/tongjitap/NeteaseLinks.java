package org.hachile.tongjitap;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NeteaseLinks {
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"，。]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID = Pattern.compile("(?:[?&]id=|/playlist/)([0-9]{1,20})(?:[^0-9]|$)");

    static String extract(String text) {
        if (text == null) throw new IllegalArgumentException("请输入网易云歌单链接或数字 ID");
        text = text.trim();
        if (text.matches("[0-9]{1,20}")) return text;
        Matcher urls = URL.matcher(text);
        while (urls.find()) {
            try {
                URI uri = new URI(urls.group());
                if (!trustedHost(uri.getHost())) continue;
                String path = uri.getPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                        + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
                if (!path.contains("playlist")) continue;
                Matcher ids = ID.matcher(path);
                if (ids.find()) return ids.group(1);
            } catch (URISyntaxException ignored) { /* A share message may contain punctuation after its URL. */ }
        }
        throw new IllegalArgumentException("未找到网易云歌单 ID，请复制完整歌单链接或输入数字 ID");
    }

    static boolean trustedHost(String host) {
        return host != null && (host.equalsIgnoreCase("music.163.com") || host.equalsIgnoreCase("y.music.163.com"));
    }

    static boolean shortLink(String text) {
        Matcher matcher = URL.matcher(text == null ? "" : text);
        while (matcher.find()) {
            try {
                URI uri = new URI(matcher.group());
                if ("163cn.tv".equalsIgnoreCase(uri.getHost())) return true;
            } catch (URISyntaxException ignored) { }
        }
        return false;
    }

    static String firstShortLink(String text) {
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            try {
                URI uri = new URI(matcher.group());
                if ("163cn.tv".equalsIgnoreCase(uri.getHost())) return "https://163cn.tv" + uri.getRawPath()
                        + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            } catch (URISyntaxException ignored) { }
        }
        throw new IllegalArgumentException("短链接无效");
    }
}
