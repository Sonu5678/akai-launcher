package dev.mantel.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Outlined, single-weight, rounded-cap glyphs on a 48 px grid (section 5, Icons).
 * Each glyph is defined once in a unit square (-0.5 to 0.5) and scaled when drawn.
 */
final class Glyphs {
    private Glyphs() {}

    static final int SEARCH = 0, SETTINGS = 1, WIFI_OFF = 2, WARNING = 3, CHECK = 4, GRID = 5, PLUS = 6,
            DECK = 7, WIFI = 8, MOON = 9, WAVE = 10, SUN = 11, SPEAKER = 12, DISPLAY = 13;

    private static final Paint GP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path[] PATHS = new Path[14];

    static {
        GP.setStyle(Paint.Style.STROKE);
        GP.setStrokeCap(Paint.Cap.ROUND);
        GP.setStrokeJoin(Paint.Join.ROUND);

        // search: ring plus handle
        Path p = new Path();
        p.addCircle(-0.06f, -0.06f, 0.27f, Path.Direction.CW);
        p.moveTo(0.14f, 0.14f);
        p.lineTo(0.37f, 0.37f);
        PATHS[SEARCH] = p;

        // settings: eight-tooth gear outline plus a centre hole
        p = new Path();
        float r1 = 0.31f, r2 = 0.41f;
        double[] off = {-17, -10, 10, 17};
        float[] rad = {r1, r2, r2, r1};
        boolean first = true;
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45.0);
            for (int k = 0; k < 4; k++) {
                double ang = a + Math.toRadians(off[k]);
                float x = (float) (Math.cos(ang) * rad[k]);
                float y = (float) (Math.sin(ang) * rad[k]);
                if (first) {
                    p.moveTo(x, y);
                    first = false;
                } else {
                    p.lineTo(x, y);
                }
            }
        }
        p.close();
        p.addCircle(0f, 0f, 0.12f, Path.Direction.CW);
        PATHS[SETTINGS] = p;

        // wifi off: three arcs, a dot, and a slash
        p = new Path();
        float cy = 0.30f;
        float[] radii = {0.16f, 0.30f, 0.44f};
        for (int i = 0; i < radii.length; i++) {
            RectF o = new RectF(-radii[i], cy - radii[i], radii[i], cy + radii[i]);
            p.addArc(o, 232f, 76f);
        }
        p.moveTo(0f, cy);
        p.lineTo(0f, cy + 0.001f);
        p.moveTo(-0.38f, -0.38f);
        p.lineTo(0.38f, 0.38f);
        PATHS[WIFI_OFF] = p;

        // warning: triangle with an exclamation mark
        p = new Path();
        p.moveTo(0f, -0.38f);
        p.lineTo(0.42f, 0.34f);
        p.lineTo(-0.42f, 0.34f);
        p.close();
        p.moveTo(0f, -0.08f);
        p.lineTo(0f, 0.10f);
        p.moveTo(0f, 0.22f);
        p.lineTo(0f, 0.2205f);
        PATHS[WARNING] = p;

        // check
        p = new Path();
        p.moveTo(-0.30f, 0.02f);
        p.lineTo(-0.09f, 0.23f);
        p.lineTo(0.31f, -0.22f);
        PATHS[CHECK] = p;

        // grid (the Library glyph): four rounded squares
        p = new Path();
        float s = 0.30f, rr = 0.07f;
        p.addRoundRect(new RectF(-0.36f, -0.36f, -0.36f + s, -0.36f + s), rr, rr, Path.Direction.CW);
        p.addRoundRect(new RectF(0.06f, -0.36f, 0.06f + s, -0.36f + s), rr, rr, Path.Direction.CW);
        p.addRoundRect(new RectF(-0.36f, 0.06f, -0.36f + s, 0.06f + s), rr, rr, Path.Direction.CW);
        p.addRoundRect(new RectF(0.06f, 0.06f, 0.06f + s, 0.06f + s), rr, rr, Path.Direction.CW);
        PATHS[GRID] = p;

        // plus
        p = new Path();
        p.moveTo(-0.3f, 0f);
        p.lineTo(0.3f, 0f);
        p.moveTo(0f, -0.3f);
        p.lineTo(0f, 0.3f);
        PATHS[PLUS] = p;

        // deck: a toggle pill with a knob
        p = new Path();
        p.addRoundRect(new RectF(-0.42f, -0.22f, 0.42f, 0.22f), 0.22f, 0.22f, Path.Direction.CW);
        p.addCircle(0.2f, 0f, 0.1f, Path.Direction.CW);
        PATHS[DECK] = p;

        // wifi: three arcs and a dot
        p = new Path();
        for (int i = 0; i < radii.length; i++) {
            RectF o = new RectF(-radii[i], cy - radii[i], radii[i], cy + radii[i]);
            p.addArc(o, 232f, 76f);
        }
        p.moveTo(0f, cy);
        p.lineTo(0f, cy + 0.001f);
        PATHS[WIFI] = p;

        // moon: a crescent
        p = new Path();
        p.addArc(new RectF(-0.38f, -0.38f, 0.38f, 0.38f), 60f, 240f);
        p.quadTo(-0.12f, 0f, 0.19f, 0.329f);
        PATHS[MOON] = p;

        // wave
        p = new Path();
        p.moveTo(-0.42f, 0f);
        p.cubicTo(-0.30f, -0.38f, -0.14f, -0.38f, 0f, 0f);
        p.cubicTo(0.14f, 0.38f, 0.30f, 0.38f, 0.42f, 0f);
        PATHS[WAVE] = p;

        // sun: a disc outline with eight rays
        p = new Path();
        p.addCircle(0f, 0f, 0.14f, Path.Direction.CW);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45.0);
            p.moveTo((float) (Math.cos(a) * 0.25), (float) (Math.sin(a) * 0.25));
            p.lineTo((float) (Math.cos(a) * 0.38), (float) (Math.sin(a) * 0.38));
        }
        PATHS[SUN] = p;

        // speaker with one wave
        p = new Path();
        p.moveTo(-0.38f, -0.14f);
        p.lineTo(-0.20f, -0.14f);
        p.lineTo(0.02f, -0.34f);
        p.lineTo(0.02f, 0.34f);
        p.lineTo(-0.20f, 0.14f);
        p.lineTo(-0.38f, 0.14f);
        p.close();
        p.addArc(new RectF(-0.12f, -0.22f, 0.32f, 0.22f), -50f, 100f);
        PATHS[SPEAKER] = p;

        // display: a screen on a stand
        p = new Path();
        p.addRoundRect(new RectF(-0.42f, -0.32f, 0.42f, 0.18f), 0.06f, 0.06f, Path.Direction.CW);
        p.moveTo(-0.18f, 0.38f);
        p.lineTo(0.18f, 0.38f);
        p.moveTo(0f, 0.18f);
        p.lineTo(0f, 0.38f);
        PATHS[DISPLAY] = p;
    }

    /** Draw glyph {@code id} centred at (cx, cy) with the given size (design px) and stroke (design px). */
    static void draw(Canvas c, int id, float cx, float cy, float size, float stroke, int color) {
        c.save();
        c.translate(cx, cy);
        c.scale(size, size);
        GP.setColor(color);
        GP.setStrokeWidth(stroke / size);
        c.drawPath(PATHS[id], GP);
        c.restore();
    }
}
