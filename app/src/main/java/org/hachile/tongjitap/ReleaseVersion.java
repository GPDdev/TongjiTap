package org.hachile.tongjitap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stable release tags only; malformed or prerelease tags never trigger an update. */
final class ReleaseVersion {
    private static final Pattern VERSION = Pattern.compile("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");
    static boolean newer(String candidate, String installed) {
        int[] a = parse(candidate), b = parse(installed);
        if (a == null || b == null) return false;
        for (int i = 0; i < 3; i++) if (a[i] != b[i]) return a[i] > b[i];
        return false;
    }
    private static int[] parse(String value) {
        if (value == null) return null;
        Matcher m = VERSION.matcher(value.trim()); if (!m.matches()) return null;
        try { return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))}; }
        catch (NumberFormatException e) { return null; }
    }
}
