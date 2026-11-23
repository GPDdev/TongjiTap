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
            c.drawArc(4, 4, 20, 20, 0, 280, false, pen); path.reset(); path.moveTo(11, 1); path.lineTo(15, 4); path.lineTo(11, 7); c.drawPath(path, pen);
        } else if (kind == 1) {
            c.drawLine(5, 9, 5, 15, pen); c.drawLine(10, 4, 10, 20, pen); c.drawLine(15, 7, 15, 17, pen); c.drawLine(20, 10, 20, 14, pen);
        } else if (kind == 2) {
            c.drawCircle(6, 6, 3, pen); c.drawCircle(18, 18, 3, pen); path.reset(); path.moveTo(9, 6); path.cubicTo(24, 6, 0, 18, 15, 18); c.drawPath(path, pen);
        } else {
            for (int y = 4; y <= 14; y += 10) for (int x = 4; x <= 14; x += 10) c.drawRoundRect(x, y, x + 6, y + 6, 2, 2, pen);
        }
        c.restore();
    }
}
