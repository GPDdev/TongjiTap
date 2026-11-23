package org.hachile.tongjitap;
import org.junit.Test;
import static org.junit.Assert.*;
public class GreenDotTest {
    private int[] screen() { int[] p = new int[300 * 400]; java.util.Arrays.fill(p, 0xffe8e6db); return p; }
    private void disc(int[] p, int x, int y, int radius, int color) {
        for (int yy = y - radius; yy <= y + radius; yy++) for (int xx = x - radius; xx <= x + radius; xx++)
            if ((xx - x) * (xx - x) + (yy - y) * (yy - y) <= radius * radius) p[yy * 300 + xx] = color;
    }
    @Test public void recognizesFilledGreenLocationDot() {
        int[] p = screen(); disc(p, 140, 180, 8, 0xffffffff); disc(p, 140, 180, 6, 0xff05d619);
        double[] point = GreenDot.find(p, 300, 400); assertNotNull(point);
        assertEquals(140 / 300.0, point[0], .005); assertEquals(180 / 400.0, point[1], .005);
    }
    @Test public void rejectsParkWithWhiteCenterAndAmbiguousMarkers() {
        int[] p = screen(); disc(p, 50, 50, 9, 0xff25d825); disc(p, 50, 50, 7, 0xffffffff);
        assertNull(GreenDot.find(p, 300, 400));
        disc(p, 140, 180, 8, 0xffffffff); disc(p, 140, 180, 6, 0xff05d619);
        disc(p, 240, 280, 8, 0xffffffff); disc(p, 240, 280, 6, 0xff05d619);
        assertNull(GreenDot.find(p, 300, 400));
    }
}
