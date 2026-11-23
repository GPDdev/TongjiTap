package org.hachile.tongjitap;

/** Finds a compact, filled green disc with a pale rim; ambiguous frames are rejected. */
final class GreenDot {
    static double[] find(int[] pixels, int width, int height) {
        if (pixels.length != width * height || width < 8 || height < 8) return null;
        boolean[] seen = new boolean[pixels.length]; int[] queue = new int[pixels.length];
        double[] match = null;
        for (int i = 0; i < pixels.length; i++) {
            if (seen[i] || !green(pixels[i])) continue;
            int head = 0, tail = 1; queue[0] = i; seen[i] = true;
            int minX = width, maxX = 0, minY = height, maxY = 0; long sx = 0, sy = 0;
            while (head < tail) {
                int p = queue[head++], x = p % width, y = p / width;
                sx += x; sy += y; minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                if (x > 0) tail = visit(p - 1, pixels, seen, queue, tail);
                if (x + 1 < width) tail = visit(p + 1, pixels, seen, queue, tail);
                if (y > 0) tail = visit(p - width, pixels, seen, queue, tail);
                if (y + 1 < height) tail = visit(p + width, pixels, seen, queue, tail);
            }
            int w = maxX - minX + 1, h = maxY - minY + 1;
            double ratio = w / (double) h, fill = tail / (double) (w * h);
            if (w < Math.max(5, width * .008) || h < 5 || w > width * .09 || h > width * .09
                    || ratio < .65 || ratio > 1.5 || fill < .58 || fill > .92) continue;
            double cx = sx / (double) tail, cy = sy / (double) tail, radius = (w + h) / 4.0 + 1;
            int pale = 0;
            for (int a = 0; a < 12; a++) {
                double angle = Math.PI * a / 6;
                int x = (int) Math.round(cx + radius * Math.cos(angle)), y = (int) Math.round(cy + radius * Math.sin(angle));
                if (x < 0 || y < 0 || x >= width || y >= height) continue;
                int color = pixels[y * width + x], r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
                if (r > 150 && g > 160 && b > 145) pale++;
            }
            if (pale < 5) continue;
            if (match != null) return null;
            match = new double[]{cx / width, cy / height};
        }
        return match;
    }
    private static int visit(int i, int[] p, boolean[] seen, int[] queue, int tail) {
        if (!seen[i] && green(p[i])) { seen[i] = true; queue[tail++] = i; } return tail;
    }
    private static boolean green(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return g >= 140 && r < 145 && b < 155 && g > r * 1.35 && g > b * 1.25;
    }
}
