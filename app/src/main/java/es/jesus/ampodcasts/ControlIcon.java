package es.jesus.ampodcasts;

import android.graphics.*;
import android.graphics.drawable.Drawable;

final class ControlIcon extends Drawable {
    private final String type; private final int size; private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    ControlIcon(String type, int color, int size) { this.type = type; this.size = size; paint.setColor(color); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); }
    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
    @Override public void draw(Canvas c) {
        Rect b = getBounds(); if (b.isEmpty()) return;
        float scale = Math.min(b.width(), b.height()) / 24f;
        c.save(); c.translate(b.exactCenterX() - 12 * scale, b.exactCenterY() - 12 * scale); c.scale(scale, scale);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f);
        switch (type) {
            case "settings": {
                c.drawCircle(12, 12, 6, paint); c.drawCircle(12, 12, 2, paint);
                for (int angle = 0; angle < 360; angle += 60) { c.save(); c.rotate(angle, 12, 12); c.drawLine(12, 3, 12, 6, paint); c.restore(); } break;
            }
            case "back": c.drawLine(19, 12, 5, 12, paint); c.drawLine(5, 12, 11, 6, paint); c.drawLine(5, 12, 11, 18, paint); break;
            case "timer": c.drawCircle(12, 13, 8, paint); c.drawLine(9, 2, 15, 2, paint); c.drawLine(12, 5, 12, 2, paint); c.drawLine(12, 8, 12, 13, paint); c.drawLine(12, 13, 15, 15, paint); break;
            case "search": c.drawCircle(10, 10, 6, paint); c.drawLine(15, 15, 21, 21, paint); break;
            case "play": { paint.setStyle(Paint.Style.FILL); Path p = new Path(); p.moveTo(8, 5); p.lineTo(19, 12); p.lineTo(8, 19); p.close(); c.drawPath(p, paint); break; }
            case "pause": paint.setStyle(Paint.Style.FILL); c.drawRoundRect(7, 5, 10, 19, .75f, .75f, paint); c.drawRoundRect(14, 5, 17, 19, .75f, .75f, paint); break;
            case "add": c.drawLine(12, 5, 12, 19, paint); c.drawLine(5, 12, 19, 12, paint); break;
            case "close": c.drawLine(6, 6, 18, 18, paint); c.drawLine(18, 6, 6, 18, paint); break;
            case "check": c.drawLine(4, 12, 10, 18, paint); c.drawLine(10, 18, 20, 6, paint); break;
            case "download": c.drawLine(12, 3, 12, 15, paint); c.drawLine(7, 10, 12, 15, paint); c.drawLine(12, 15, 17, 10, paint); c.drawLine(4, 16, 4, 21, paint); c.drawLine(4, 21, 20, 21, paint); c.drawLine(20, 21, 20, 16, paint); break;
            case "list": for (int y = 6; y <= 18; y += 6) { paint.setStyle(Paint.Style.FILL); c.drawCircle(4, y, 1, paint); paint.setStyle(Paint.Style.STROKE); c.drawLine(9, y, 20, y, paint); } break;
            case "expand": c.drawLine(5, 8, 12, 15, paint); c.drawLine(12, 15, 19, 8, paint); break;
            case "refresh": c.drawArc(4, 4, 20, 20, 45, 285, false, paint); c.drawLine(18, 3, 20, 8, paint); c.drawLine(20, 8, 15, 8, paint); break;
            case "rewind": case "forward": {
                boolean back = type.equals("rewind"); c.drawArc(4, 5, 20, 21, back ? 210 : -30, back ? -285 : 285, false, paint);
                float x = back ? 5 : 19; c.drawLine(x, 3, x, 8, paint); c.drawLine(x, 8, back ? 10 : 14, 8, paint);
                paint.setStyle(Paint.Style.FILL); paint.setTextSize(7); paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); paint.setTextAlign(Paint.Align.CENTER); c.drawText(back ? "15" : "30", 12, 16, paint); break;
            }
        }
        c.restore();
    }
    @Override public void setAlpha(int a) { paint.setAlpha(a); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
