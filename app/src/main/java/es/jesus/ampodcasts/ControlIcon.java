package es.jesus.ampodcasts;

import android.graphics.*;
import android.graphics.drawable.Drawable;

final class ControlIcon extends Drawable {
    private final String type; private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    ControlIcon(String type, int color) { this.type = type; paint.setColor(color); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); }
    @Override public void draw(Canvas c) {
        c.save(); Rect b = getBounds(); c.translate(b.left, b.top); c.scale(b.width() / 24f, b.height() / 24f);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2);
        switch (type) {
            case "search": c.drawCircle(10, 10, 6, paint); c.drawLine(15, 15, 21, 21, paint); break;
            case "play": { paint.setStyle(Paint.Style.FILL); Path p = new Path(); p.moveTo(7, 4); p.lineTo(21, 12); p.lineTo(7, 20); p.close(); c.drawPath(p, paint); break; }
            case "pause": paint.setStyle(Paint.Style.FILL); c.drawRoundRect(6, 4, 10, 20, 1, 1, paint); c.drawRoundRect(14, 4, 18, 20, 1, 1, paint); break;
            case "add": c.drawLine(12, 5, 12, 19, paint); c.drawLine(5, 12, 19, 12, paint); break;
            case "close": c.drawLine(6, 6, 18, 18, paint); c.drawLine(18, 6, 6, 18, paint); break;
            case "check": c.drawLine(4, 12, 10, 18, paint); c.drawLine(10, 18, 20, 6, paint); break;
            case "download": c.drawLine(12, 3, 12, 15, paint); c.drawLine(7, 10, 12, 15, paint); c.drawLine(12, 15, 17, 10, paint); c.drawLine(4, 16, 4, 21, paint); c.drawLine(4, 21, 20, 21, paint); c.drawLine(20, 21, 20, 16, paint); break;
            case "list": for (int y = 6; y <= 18; y += 6) { c.drawCircle(4, y, .7f, paint); c.drawLine(9, y, 20, y, paint); } break;
            case "expand": c.drawLine(5, 8, 12, 15, paint); c.drawLine(12, 15, 19, 8, paint); break;
            case "refresh": c.drawArc(4, 4, 20, 20, 45, 285, false, paint); c.drawLine(18, 3, 20, 8, paint); c.drawLine(20, 8, 15, 8, paint); break;
            case "rewind": case "forward": {
                boolean back = type.equals("rewind"); c.drawArc(3, 5, 21, 23, back ? 210 : -30, back ? -285 : 285, false, paint);
                float x = back ? 4 : 20; c.drawLine(x, 3, x, 8, paint); c.drawLine(x, 8, back ? 9 : 15, 8, paint);
                paint.setStyle(Paint.Style.FILL); paint.setTextSize(8); paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); paint.setTextAlign(Paint.Align.CENTER); c.drawText(back ? "15" : "30", 12, 17, paint); break;
            }
        }
        c.restore();
    }
    @Override public void setAlpha(int a) { paint.setAlpha(a); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
