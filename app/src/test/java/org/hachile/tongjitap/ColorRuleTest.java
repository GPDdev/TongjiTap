package org.hachile.tongjitap;

import org.junit.Test;
import static org.junit.Assert.*;

public class ColorRuleTest {
    ColorRule rule() { return new ColorRule(true, 10, 20, 0xFFFFFF, 16, 2, 500, true, 0, 0); }

    @Test public void comparesEveryRgbChannelAndIgnoresAlpha() {
        assertTrue(rule().matches(0x00F0F0F0)); assertTrue(rule().matches(0xFFF0F0F0));
        assertFalse(rule().matches(0xFFEEFFFF)); assertFalse(rule().matches(0xFFFFEEFF));
        assertFalse(rule().matches(0xFFFFFFEE));
    }
    @Test public void singleFrameAndIntermittentMatchesDoNotFire() {
        ColorRule.Detector detector = new ColorRule.Detector(rule());
        assertFalse(detector.sample(0xFFFFFF)); assertFalse(detector.sample(0));
        assertFalse(detector.sample(0xFFFFFF)); assertTrue(detector.sample(0xFFFFFF));
    }
    @Test public void persistentWhiteFiresOnlyOnceAndNeedsTwoMissesToRearm() {
        ColorRule.Detector d = new ColorRule.Detector(rule()); d.sample(0xFFFFFF); assertTrue(d.sample(0xFFFFFF));
        for (int i = 0; i < 100; i++) assertFalse(d.sample(0xFFFFFF));
        d.sample(0); assertTrue(d.latched()); assertFalse(d.sample(0xFFFFFF));
        d.sample(0); d.sample(0); assertFalse(d.latched());
        assertFalse(d.sample(0xFFFFFF)); assertTrue(d.sample(0xFFFFFF));
    }
    @Test public void validatesColorAndScreenBoundaries() {
        assertEquals(0xAABBCC, ColorRule.parseRgb(" #aAbBcC ")); assertEquals("#FFFFFF", rule().hex());
        assertThrows(IllegalArgumentException.class, () -> ColorRule.parseRgb("#FFF"));
        assertThrows(IllegalArgumentException.class, () -> ColorRule.parseRgb("FFFFFF00"));
        assertThrows(IllegalArgumentException.class, () -> rule().validateScreen(10, 20));
        assertThrows(IllegalArgumentException.class, () -> new ColorRule(true, 1, 2, 0, 65, 2, 500, true, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ColorRule(true, 1, 2, 0, 16, 1, 500, true, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ColorRule(true, 1, 2, 0, 16, 2, 10, true, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ColorRule(true, 1, 2, 0, 16, 2, 500, false, 100, 200).validateScreen(50, 50));
    }
}
