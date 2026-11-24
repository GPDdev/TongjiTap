package org.hachile.tongjitap;

import android.graphics.*;

/** Original practice UI, independent of user screenshots and stored coordinates. */
final class TutorialScreens {
    static final Rect MAP = new Rect(24, 100, 336, 390), DISTANCE = new Rect(24, 416, 180, 470),
        PAUSE = new Rect(24, 514, 164, 582), RESUME = new Rect(196, 514, 336, 582), WIFI = new Rect(28, 140, 260, 240);
    static Bitmap make(boolean settings, int phase) {
        Bitmap bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawColor(0xfff5f7fb);
        text(c, p, settings ? "快捷设置示例" : "跑步页面示例", 24, 53, 23, 0xff152035);
        if (settings) {
            box(c,p,WIFI,0xff2563eb); text(c,p,"Wi-Fi",48,181,23,Color.WHITE); text(c,p,"TJ-WIFI",48,214,18,Color.WHITE);
            box(c,p,new Rect(28,270,260,370),0xffe3e8f1); text(c,p,"移动数据",48,326,23,0xff152035);
        } else {
            box(c,p,MAP,0xffdcefe7); p.setColor(Color.WHITE); p.setStrokeWidth(18);
            c.drawLine(36,320,310,135,p); c.drawLine(72,105,246,380,p);
            float x = phase % 2 == 0 ? 108 : 258, y = phase % 2 == 0 ? 262 : 177;
            p.setColor(0x4434c759); c.drawCircle(x,y,25,p); p.setColor(0xff14b744); c.drawCircle(x,y,12,p);
            box(c,p,DISTANCE,Color.WHITE); text(c,p,String.format(java.util.Locale.ROOT,"%.2f km", phase * .03),36,452,24,0xff152035);
            box(c,p,PAUSE,0xff2563eb); box(c,p,RESUME,0xff2563eb);
            text(c,p,"暂停跑步",35,555,22,Color.WHITE,118); text(c,p,"继续跑步",207,555,22,Color.WHITE,118);
        }
        return bitmap;
    }
    private static void box(Canvas c, Paint p, Rect r, int color) { p.setColor(color); c.drawRoundRect(new RectF(r),12,12,p); }
    private static void text(Canvas c, Paint p, String text, float x, float y, float size, int color) {
        text(c,p,text,x,y,size,color,336-x);
    }
    private static void text(Canvas c, Paint p, String text, float x, float y, float size, int color, float maxWidth) {
        String translated = L.t(text); p.setColor(color); p.setTextSize(size);
        float width = p.measureText(translated);
        if (width > maxWidth) p.setTextSize(size * maxWidth / width);
        c.drawText(translated,x,y,p);
    }
}
