package dev.mantel.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The icon pack. An app in the pack gets a gradient tile with an original white glyph. Any other
 * app gets, in order: its own icon or banner on a plate (IconCache), then a monogram tile with a
 * hue taken from its package name. No brand logos are drawn.
 */
final class TileArt {
    private TileArt() {}

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF RF = new RectF();
    private static final Path TMP = new Path();

    /** Draw the tile for {@code e} centred on (cx, cy), {@code size} design px square. */
    static void draw(Canvas c, MainActivity act, AppEntry e, float cx, float cy, float size) {
        float l = cx - size / 2f;
        float t = cy - size / 2f;
        float rad = size * 0.225f;
        int brand = Brand.of(e);
        if (brand >= 0) {
            gradientTile(c, l, t, size, rad, Brand.HUE[brand], Brand.DEEP[brand]);
            glyph(c, brand, l, t, size);
            return;
        }
        Bitmap bm = e == null ? null : IconCache.get(act, e, Math.round(size), rad);
        if (bm != null) {
            RF.set(l, t, l + size, t + size);
            c.drawBitmap(bm, null, RF, Ui.BP);
            return;
        }
        int hue = Brand.hueFor(e);
        gradientTile(c, l, t, size, rad, hue, Brand.shade(hue, 0.28f));
        String label = e != null && e.label != null && e.label.length() > 0
                ? e.label.substring(0, 1).toUpperCase() : "?";
        Ui.text(c, label, cx, cy + size * 0.19f, size * 0.55f, Ui.display, 0xF2FFFFFF, Paint.Align.CENTER);
    }

    /** A plain mark by brand index, used where there is no AppEntry (settings previews). */
    static void drawBrand(Canvas c, int brand, float cx, float cy, float size) {
        float l = cx - size / 2f;
        float t = cy - size / 2f;
        gradientTile(c, l, t, size, size * 0.225f, Brand.HUE[brand], Brand.DEEP[brand]);
        glyph(c, brand, l, t, size);
    }

    private static void gradientTile(Canvas c, float l, float t, float size, float rad, int top, int bottom) {
        RF.set(l, t, l + size, t + size);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(l, t, l + size, t + size, top, bottom, Shader.TileMode.CLAMP));
        c.drawRoundRect(RF, rad, rad, P);
        c.save();
        TMP.reset();
        TMP.addRoundRect(RF, rad, rad, Path.Direction.CW);
        c.clipPath(TMP);
        P.setShader(new RadialGradient(l + size * 0.25f, t, size * 0.9f, 0x47FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(RF, P);
        c.restore();
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(1f, size * 0.006f));
        P.setColor(0x2EFFFFFF);
        c.drawRoundRect(RF, rad, rad, P);
        P.setStyle(Paint.Style.FILL);
    }

    private static void glyph(Canvas c, int brand, float l, float t, float size) {
        c.save();
        c.translate(l, t);
        float s = size / 240f;
        c.scale(s, s);
        P.setShader(null);
        P.setColor(0xFFFFFFFF);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeJoin(Paint.Join.ROUND);
        switch (brand) {
            case Brand.NETFLIX:
                stroke(9f);
                RF.set(58, 96, 182, 178);
                c.drawRoundRect(RF, 14, 14, P);
                fill();
                TMP.reset();
                TMP.moveTo(56, 90); TMP.lineTo(68, 58); TMP.lineTo(98, 58); TMP.lineTo(86, 90); TMP.close();
                TMP.moveTo(104, 90); TMP.lineTo(116, 58); TMP.lineTo(146, 58); TMP.lineTo(134, 90); TMP.close();
                TMP.moveTo(152, 90); TMP.lineTo(164, 58); TMP.lineTo(184, 58); TMP.lineTo(184, 90); TMP.close();
                c.drawPath(TMP, P);
                break;
            case Brand.YOUTUBE:
                stroke(9f);
                c.drawCircle(120, 120, 62, P);
                fill();
                TMP.reset();
                TMP.moveTo(106, 90); TMP.lineTo(106, 150); TMP.lineTo(152, 120); TMP.close();
                c.drawPath(TMP, P);
                break;
            case Brand.SPOTIFY:
                fill();
                bar(c, 54, 100, 140);
                bar(c, 82, 72, 168);
                bar(c, 111, 88, 152);
                bar(c, 140, 56, 184);
                bar(c, 168, 94, 146);
                break;
            case Brand.HOTSTAR:
                fill();
                TMP.reset();
                TMP.moveTo(116, 50);
                TMP.cubicTo(121, 96, 144, 116, 190, 122);
                TMP.cubicTo(144, 128, 121, 148, 116, 194);
                TMP.cubicTo(111, 148, 88, 128, 42, 122);
                TMP.cubicTo(88, 116, 111, 96, 116, 50);
                TMP.close();
                c.drawPath(TMP, P);
                TMP.reset();
                TMP.moveTo(186, 44);
                TMP.cubicTo(187, 58, 192, 63, 206, 64);
                TMP.cubicTo(192, 65, 187, 70, 186, 84);
                TMP.cubicTo(185, 70, 180, 65, 166, 64);
                TMP.cubicTo(180, 63, 185, 58, 186, 44);
                TMP.close();
                c.drawPath(TMP, P);
                break;
            case Brand.JIOTV:
                stroke(9f);
                RF.set(70, 104.7f, 170, 204.7f);
                c.drawArc(RF, 224f, 92f, false, P);
                RF.set(38, 71.9f, 202, 235.9f);
                c.drawArc(RF, 223f, 94f, false, P);
                c.drawLine(120, 162, 120, 190, P);
                fill();
                c.drawCircle(120, 150, 12, P);
                break;
            case Brand.BROWSE:
                stroke(9f);
                c.drawCircle(120, 120, 64, P);
                stroke(7f);
                RF.set(94, 56, 146, 184);
                c.drawOval(RF, P);
                c.drawLine(56, 120, 184, 120, P);
                break;
            case Brand.KARTOONS:
                fill();
                RF.set(50, 60, 190, 170);
                c.drawRoundRect(RF, 22, 22, P);
                TMP.reset();
                TMP.moveTo(92, 160); TMP.lineTo(128, 160); TMP.lineTo(92, 200); TMP.close();
                c.drawPath(TMP, P);
                P.setColor(Brand.DEEP[Brand.KARTOONS]);
                c.drawCircle(96, 108, 9, P);
                c.drawCircle(144, 108, 9, P);
                stroke(9f);
                P.setColor(Brand.DEEP[Brand.KARTOONS]);
                TMP.reset();
                TMP.moveTo(92, 132);
                TMP.quadTo(120, 158, 148, 132);
                c.drawPath(TMP, P);
                break;
            default:
                break;
        }
        P.setStrokeCap(Paint.Cap.BUTT);
        P.setStyle(Paint.Style.FILL);
        c.restore();
    }

    private static void stroke(float w) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(w);
        P.setColor(0xFFFFFFFF);
    }

    private static void fill() {
        P.setStyle(Paint.Style.FILL);
        P.setColor(0xFFFFFFFF);
    }

    private static void bar(Canvas c, float x, float top, float bottom) {
        RF.set(x, top + 0f, x + 18f, bottom);
        c.drawRoundRect(RF, 9f, 9f, P);
    }
}
