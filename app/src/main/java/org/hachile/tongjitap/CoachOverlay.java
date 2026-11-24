package org.hachile.tongjitap;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** A transparent touch hole keeps the actual target interactive, including drag gestures. */
@android.annotation.SuppressLint("ViewConstructor") // Requires a live target; never inflated from XML.
final class CoachOverlay extends View {
    private final View target;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shade = new Path();
    private final RectF hole = new RectF();
    private final int[] here = new int[2], there = new int[2];
    private final android.view.ViewTreeObserver.OnScrollChangedListener scroll = this::invalidate;
    CoachOverlay(Context context, View target) {
        super(context); this.target = target;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }
    private void position() {
        getLocationOnScreen(here); target.getLocationOnScreen(there);
        hole.set(there[0] - here[0], there[1] - here[1], there[0] - here[0] + target.getWidth(), there[1] - here[1] + target.getHeight());
        hole.inset(-Ui.dp(getContext(), 3), -Ui.dp(getContext(), 3));
    }
    @Override protected void onDraw(Canvas canvas) {
        position(); shade.reset(); shade.setFillType(Path.FillType.EVEN_ODD);
        shade.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        shade.addRoundRect(hole, Ui.dp(getContext(), 14), Ui.dp(getContext(), 14), Path.Direction.CW);
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xb3091220); canvas.drawPath(shade, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Ui.dp(getContext(), 2)); paint.setColor(Color.WHITE);
        canvas.drawRoundRect(hole, Ui.dp(getContext(), 14), Ui.dp(getContext(), 14), paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        position(); boolean blocked = !hole.contains(event.getX(), event.getY());
        if (blocked && event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
        return blocked;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); getViewTreeObserver().addOnScrollChangedListener(scroll); }
    @Override protected void onDetachedFromWindow() { getViewTreeObserver().removeOnScrollChangedListener(scroll); super.onDetachedFromWindow(); }
}
