package org.hachile.tongjitap;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import java.util.Locale;

final class AppPreferences {
    private final SharedPreferences prefs;
    AppPreferences(Context context) { prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE); }
    String language() { return prefs.getString("language", "zh"); }
    String theme() { return prefs.getString("theme", "light"); }
    void language(String value) { prefs.edit().putString("language", value).apply(); }
    void theme(String value) { prefs.edit().putString("theme", value).apply(); }
    boolean tutorialDone() { return prefs.getBoolean("tutorialDone", false); }
    void finishTutorial() { prefs.edit().putBoolean("tutorialDone", true).apply(); }
    void launch() { prefs.edit().putInt("launches", prefs.getInt("launches", 0) + 1).apply(); }
    boolean starDue() { return prefs.getInt("launches", 0) >= 5 && !prefs.getBoolean("starShown", false); }
    void starShown() { prefs.edit().putBoolean("starShown", true).apply(); }
    static Context wrap(Context base) {
        AppPreferences p = new AppPreferences(base);
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.setLocale(Locale.forLanguageTag(p.language()));
        c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
            | (p.theme().equals("dark") ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(c);
    }
}
