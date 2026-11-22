package org.hachile.tongjitap;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;

final class Store {
    static final int NOTICE_VERSION = 2;
    static final class Draft {
        int loops = 10, width, height, rotation;
        final ArrayList<ClickStep> steps = new ArrayList<>();
        ColorRule colorRule;
        Draft(ScreenInfo screen) { width = screen.width; height = screen.height; rotation = screen.rotation; }
        ClickPlan plan() { return new ClickPlan(loops, width, height, rotation, steps); }
    }
    private final SharedPreferences prefs;
    Store(Context context) { prefs = context.getSharedPreferences("tongjitap", Context.MODE_PRIVATE); }
    boolean accepted() { return prefs.getInt("noticeVersion", 0) == NOTICE_VERSION; }
    void accept() { commit(prefs.edit().putInt("noticeVersion", NOTICE_VERSION)); }
    boolean runNoticeSeen() { return prefs.getBoolean("runNotice", false); }
    void markRunNotice() { commit(prefs.edit().putBoolean("runNotice", true)); }

    Draft load(ScreenInfo screen) {
        String raw = prefs.getString("draft", null);
        if (raw == null) return new Draft(screen);
        try {
            JSONObject obj = new JSONObject(raw);
            int version = integer(obj, "version");
            if (version != 1 && version != 2) throw new JSONException("Unsupported version");
            Draft d = new Draft(new ScreenInfo(integer(obj, "width"), integer(obj, "height"), integer(obj, "rotation")));
            d.loops = integer(obj, "loops");
            JSONArray steps = obj.getJSONArray("steps");
            for (int i = 0; i < steps.length(); i++) {
                JSONObject s = steps.getJSONObject(i);
                d.steps.add(new ClickStep(integer(s, "x"), integer(s, "y"), integer(s, "duration"), integer(s, "wait")));
            }
            if (obj.has("colorRule")) {
                JSONObject c = obj.getJSONObject("colorRule");
                if (!(c.get("enabled") instanceof Boolean) || !(c.get("back") instanceof Boolean))
                    throw new JSONException("Expected boolean");
                d.colorRule = new ColorRule(c.getBoolean("enabled"), integer(c, "x"), integer(c, "y"),
                    integer(c, "rgb"), integer(c, "tolerance"), integer(c, "confirmations"), integer(c, "interval"),
                    c.getBoolean("back"), integer(c, "tapX"), integer(c, "tapY"));
            }
            validate(d);
            return d;
        } catch (JSONException | IllegalArgumentException e) {
            throw new IllegalStateException("本地配置损坏或版本不兼容，请确认后重设；原配置尚未覆盖", e);
        }
    }

    void save(Draft d) {
        validate(d);
        try {
            JSONObject obj = new JSONObject().put("version", 2).put("loops", d.loops)
                .put("width", d.width).put("height", d.height).put("rotation", d.rotation);
            JSONArray steps = new JSONArray();
            for (ClickStep s : d.steps) steps.put(new JSONObject().put("x", s.x).put("y", s.y)
                .put("duration", s.durationMs).put("wait", s.waitMs));
            ColorRule c = d.colorRule;
            if (c != null) obj.put("colorRule", new JSONObject().put("enabled", c.enabled).put("x", c.x).put("y", c.y)
                .put("rgb", c.rgb).put("tolerance", c.tolerance).put("confirmations", c.confirmations)
                .put("interval", c.intervalMs).put("back", c.back).put("tapX", c.tapX).put("tapY", c.tapY));
            commit(prefs.edit().putString("draft", obj.put("steps", steps).toString()));
        } catch (JSONException e) { throw new IllegalStateException("无法保存配置", e); }
    }

    private static void validate(Draft d) {
        if (d.colorRule != null) d.colorRule.validateScreen(d.width, d.height);
        if (d.steps.isEmpty()) {
            if (d.loops < 1 || d.loops > 100000 || d.width <= 0 || d.height <= 0 || d.rotation < 0 || d.rotation > 3)
                throw new IllegalArgumentException("配置无效，请重新设置");
        } else d.plan();
    }
    private static int integer(JSONObject obj, String key) throws JSONException {
        Object value = obj.get(key);
        if (!(value instanceof Integer)) throw new JSONException("Expected integer: " + key);
        return (Integer) value;
    }
    private static void commit(SharedPreferences.Editor editor) {
        if (!editor.commit()) throw new IllegalStateException("保存失败，请检查设备可用空间后重试");
    }
}
