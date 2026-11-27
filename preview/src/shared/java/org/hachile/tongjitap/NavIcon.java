package org.hachile.tongjitap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

@android.annotation.SuppressLint("ViewConstructor") // Programmatic navigation icon with an explicit tab kind.
final class NavIcon extends View {
    private final int kind; private final boolean selected;
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    NavIcon(Context c, int kind, boolean selected) { super(c); this.kind = kind; this.selected = selected; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); c.save(); c.scale(getWidth() / 24f, getHeight() / 24f);
        pen.setColor(selected ? Ui.BLUE : Ui.MUTED); pen.setStrokeWidth(1.8f); pen.setStrokeCap(Paint.Cap.ROUND); pen.setStrokeJoin(Paint.Join.ROUND); pen.setStyle(Paint.Style.STROKE);
        if (kind == 0) {
            c.drawArc(3, 3, 21, 21, 10, 306, false, pen);
            path.reset(); path.moveTo(19, 2.5f); path.lineTo(19, 7.5f); path.lineTo(14, 7.5f); c.drawPath(path, pen);
        } else if (kind == 1) {
            pen.setStrokeWidth(3.4f);
            c.drawLine(5, 14, 5, 19, pen); c.drawLine(12, 9, 12, 19, pen); c.drawLine(19, 4, 19, 19, pen);
        } else if (kind == 2) {
            c.drawLine(8.5f, 14.5f, 15.5f, 9.5f, pen);
            if (selected) pen.setStyle(Paint.Style.FILL);
            c.drawCircle(5.5f, 16, 3.5f, pen); c.drawCircle(18.5f, 8, 3.5f, pen);
        } else if (kind == Tabs.ROAM) {
            c.drawCircle(12, 12, 9, pen);
            path.reset(); path.moveTo(16, 7); path.lineTo(13, 14); path.lineTo(8, 17); path.lineTo(11, 10); path.close();
            if (selected) pen.setStyle(Paint.Style.FILL);
            c.drawPath(path, pen);
        } else {
            if (selected) pen.setStyle(Paint.Style.FILL);
            for (int y = 3; y <= 14; y += 11) for (int x = 3; x <= 14; x += 11) c.drawRoundRect(x, y, x + 7, y + 7, 2, 2, pen);
        }
        c.restore();
    }
}
