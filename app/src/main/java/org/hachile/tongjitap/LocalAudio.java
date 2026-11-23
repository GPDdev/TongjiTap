package org.hachile.tongjitap;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.IOException;
import java.util.Locale;

final class LocalAudio {
    static Track read(Context context, Uri uri) throws Exception {
        if (!"content".equals(uri.getScheme())) throw new IOException("请选择设备上的音频文件");
        String name = "本地音频";
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        }
        if (name == null) name = "本地音频";
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ncm") || lower.endsWith(".qmc") || lower.endsWith(".kgm"))
            throw new IOException("加密缓存无法直接播放，请导入普通 MP3、FLAC、M4A 等音频");
        String title = name.replaceFirst("\\.[^.]+$", ""), artist = "本地音频";
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(context, uri);
            String duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration == null || Long.parseLong(duration) <= 0) throw new IOException("无法识别音频：" + name);
            String t = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String a = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (t != null && !t.trim().isEmpty()) title = t;
            if (a != null && !a.trim().isEmpty()) artist = a;
        } finally { metadata.release(); }
        return new Track("local:" + uri, title, artist, uri.toString(), false);
    }
}
