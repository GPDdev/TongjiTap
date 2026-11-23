package org.hachile.tongjitap;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pairs a sudden displacement with a strict >20 m increment, then confirms a second frame. */
final class RelayGate {
    static final class Frame {
        final double x, y; final int meters; final long time;
        Frame(double x, double y, int meters, long time) { this.x = x; this.y = y; this.meters = meters; this.time = time; }
    }
    private final double jump;
    private static final long MAX_FRAME_GAP_MS = 10000; // One 8 s capture/OCR deadline plus the polling interval.
    private Frame baseline, candidate;
    RelayGate(double jump) { this.jump = jump; }
    void reset() { baseline = candidate = null; }
    void seed(Frame frame) { baseline = frame; candidate = null; }
    boolean sample(Frame now) {
        if (now == null || !Double.isFinite(now.x) || !Double.isFinite(now.y) || now.meters < 0) { reset(); return false; }
        Frame previous = candidate == null ? baseline : candidate;
        if (previous == null || now.time <= previous.time || now.time - previous.time > MAX_FRAME_GAP_MS || now.meters < baseline.meters) {
            baseline = now; candidate = null; return false;
        }
        if (candidate != null) {
            boolean stable = Math.hypot(now.x - candidate.x, now.y - candidate.y) < .035
                && now.meters >= candidate.meters && now.meters - candidate.meters <= 20
                && now.time - candidate.time <= MAX_FRAME_GAP_MS;
            candidate = null; baseline = now;
            return stable;
        }
        int delta = now.meters - baseline.meters;
        if (delta > 20 && Math.hypot(now.x - baseline.x, now.y - baseline.y) >= jump) {
            candidate = now; return false;
        }
        baseline = now; return false;
    }
    static Integer parseMeters(String text) {
        if (text == null) return null;
        String normalized = text.toLowerCase(java.util.Locale.ROOT).replace('，', '.').replace(',', '.').replace("\u00a0", " ");
        if (normalized.matches("(?s).*[−-]\\s*[0-9].*")) return null;
        Matcher number = Pattern.compile("(?<![0-9.])([0-9]{1,3}(?:\\.[0-9]{1,3})?)(?![0-9.])").matcher(normalized);
        if (!number.find()) return null;
        String value = number.group(1); if (number.find()) return null; // Ambiguous ROI: timer, pace, etc.
        boolean km = normalized.contains("km") || normalized.contains("公里") || normalized.contains("千米");
        boolean m = !km && (normalized.matches(".*\\bm\\b.*") || normalized.contains("米"));
        if (!m && !km && !value.contains(".")) return null; // Missing decimal is too risky to guess.
        try {
            BigDecimal distance = new BigDecimal(value).multiply(BigDecimal.valueOf(m ? 1 : 1000));
            int meters = distance.setScale(0, RoundingMode.HALF_UP).intValueExact();
            return meters >= 0 && meters <= 200000 ? meters : null;
        } catch (ArithmeticException e) { return null; }
    }
}
