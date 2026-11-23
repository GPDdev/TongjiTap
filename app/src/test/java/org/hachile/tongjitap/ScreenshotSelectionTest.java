package org.hachile.tongjitap;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenshotSelectionTest {
    @Test public void portraitRetainsOriginalPixelsWithSideLetterboxing() {
        ScreenshotSelection g = new ScreenshotSelection(1080, 1920, 800, 960);
        assertEquals(130f, g.left, 0.001f); assertEquals(0f, g.top, 0.001f);
        assertArrayEquals(new int[]{0, 0}, g.pixel(130, 0));
        assertArrayEquals(new int[]{1079, 1919}, g.pixel(669.9f, 959.9f));
        assertArrayEquals(new int[]{540, 960}, g.pixel(400, 480));
        assertNull(g.pixel(129.9f, 400)); assertNull(g.pixel(670, 400));
        assertNull(g.pixel(400, 960)); assertNull(g.pixel(400, -.1f));
    }
    @Test public void landscapeRejectsTopAndBottomMargins() {
        ScreenshotSelection g = new ScreenshotSelection(1920, 1080, 960, 800);
        assertEquals(130f, g.top, 0.001f);
        assertArrayEquals(new int[]{960, 540}, g.pixel(480, 400));
        assertNull(g.pixel(200, 129)); assertNull(g.pixel(200, 670));
    }
    @Test public void fractionalScaleMapsPixelCentersWithoutRoundingDrift() {
        ScreenshotSelection g = new ScreenshotSelection(1440, 3120, 973, 1251);
        for (int x : new int[]{0, 1, 719, 1438, 1439}) for (int y : new int[]{0, 1, 1559, 3118, 3119})
            assertArrayEquals(new int[]{x, y}, g.pixel(g.left + (x + .5f) * g.scale, g.top + (y + .5f) * g.scale));
        assertNull(g.pixel(Float.NaN, 12)); assertNull(g.pixel(12, Float.POSITIVE_INFINITY));
    }
    @Test public void invalidDimensionsRejectedBeforeMapping() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenshotSelection(0, 1080, 100, 200));
        assertThrows(IllegalArgumentException.class, () -> new ScreenshotSelection(1080, 1920, 0, 200));
    }
}
