package org.hachile.tongjitap;

import android.content.Context;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

/** Shared phrase catalog also localizes live service statuses and imported music controls. */
final class L {
    private static final class Catalog {
        final HashMap<String,String> strings = new HashMap<>();
        final ArrayList<String> phrases = new ArrayList<>();
    }
    private static volatile Catalog catalog = new Catalog();
    static volatile String language = "zh";
    static void init(Context context) {
        language = new AppPreferences(context).language(); Catalog next = new Catalog();
        if (language.equals("zh")) { catalog = next; return; }
        try (java.io.InputStream input = context.getAssets().open("i18n/" + language + ".json")) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            JSONObject json = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) { String key = keys.next(); next.strings.put(key, json.getString(key)); next.phrases.add(key); }
            next.phrases.sort(Comparator.comparingInt(String::length).reversed());
        } catch (java.io.IOException | org.json.JSONException e) { language = "zh"; }
        catalog = next;
    }
    static String t(String text) {
        Catalog current = catalog;
        if (text == null || current.strings.isEmpty()) return text;
        String exact = current.strings.get(text); if (exact != null) return exact;
        // Longest-first interpolation supports numeric progress and user-entered coordinates.
        String result = text;
        for (String phrase : current.phrases) if (result.contains(phrase)) result = result.replace(phrase, current.strings.get(phrase));
        return result;
    }
    static CharSequence t(CharSequence text) { return text == null ? null : t(text.toString()); }
}
