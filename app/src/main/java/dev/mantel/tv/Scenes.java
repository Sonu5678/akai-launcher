package dev.mantel.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The living scene behind each app: a dark ground, two soft glows and one drawn motif that drifts
 * slowly. Everything is drawn from paths on a 1600 x 900 virtual canvas, scaled to cover the
 * target rectangle, so there are no image files. Motion level (Alive, Calm, Still) scales time.
 */
final class Scenes {
    private Scenes() {}

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF RF = new RectF();
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private static float sA = 1f;
    private static int sHue = 0xFFFFFFFF;
    private static float tt = 0f;

    private static final Path BEAM1 = poly(640, -40, 960, -40, 1420, 940, 180, 940);
    private static final Path BEAM2 = poly(740, -40, 860, -40, 1100, 940, 500, 940);
    private static final Path PLAY = poly(700, 320, 700, 580, 930, 450);
    private static final Path STAR = new Path();
    private static final Path[] WAVES = new Path[6];
    private static final float[] WAVE_Y = {600f, 520f, 580f, 460f, 400f, 340f};
    private static final Path SQUIG1 = new Path();
    private static final Path SQUIG2 = new Path();
    private static final float[][] HS_PTS = {
            {220, 640}, {420, 520}, {620, 600}, {760, 430}, {1100, 350}, {360, 300}, {560, 220},
            {900, 700}, {1240, 640}, {1420, 760}, {1300, 190}, {1480, 260}};
    private static final int[][] HS_LINES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 4}, {1, 5}, {5, 6}, {6, 3}, {2, 7}, {7, 8}, {8, 9}, {4, 10}, {10, 11}};

    static {
        STAR.moveTo(1100, 120);
        STAR.cubicTo(1116, 270, 1190, 340, 1340, 350);
        STAR.cubicTo(1190, 362, 1116, 436, 1100, 590);
        STAR.cubicTo(1084, 436, 1010, 362, 860, 350);
        STAR.cubicTo(1010, 340, 1084, 270, 1100, 120);
        STAR.close();
        for (int i = 0; i < WAVES.length; i++) {
            float y = WAVE_Y[i];
            Path p = new Path();
            p.moveTo(-100, y);
            p.cubicTo(200, y - 130, 460, y + 140, 800, y);
            p.cubicTo(1140, y - 140, 1380, y - 130, 1700, y);
            if (i == 0) {
                p.lineTo(1700, 960);
                p.lineTo(-100, 960);
                p.close();
            }
            WAVES[i] = p;
        }
        SQUIG1.moveTo(120, 560);
        SQUIG1.quadTo(180, 490, 240, 560);
        SQUIG1.quadTo(300, 630, 360, 560);
        SQUIG1.quadTo(420, 490, 480, 560);
        SQUIG2.moveTo(1180, 470);
        SQUIG2.quadTo(1230, 410, 1280, 470);
        SQUIG2.quadTo(1330, 530, 1380, 470);
    }

    private static Path poly(float... p) {
        Path path = new Path();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        path.close();
        return path;
    }

    /** True when scenes move at all (so callers know whether to keep redrawing). */
    static boolean moving() {
        return Prefs.motion() != 2 && !Ui.animOff;
    }

    private static float speed() {
        int m = Prefs.motion();
        if (Ui.animOff || m == 2) return 0f;
        return m == 1 ? 0.4f : 1f;
    }

    // ---- small drawing helpers (virtual canvas space) ----

    private static float sw(float period, float phase) {
        return (float) Math.sin(TWO_PI * (tt / period) + phase);
    }

    private static int col(int c, float a) {
        return Ui.alpha(c, a * sA);
    }

    private static void fillCircle(Canvas c, float x, float y, float r, int color, float a) {
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(col(color, a));
        c.drawCircle(x, y, r, P);
    }

    private static void ringCircle(Canvas c, float x, float y, float r, float a, float sw) {
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(sw);
        P.setColor(col(sHue, a));
        c.drawCircle(x, y, r, P);
    }

    private static void fillPath(Canvas c, Path p, float a) {
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(col(sHue, a));
        c.drawPath(p, P);
    }

    private static void strokePath(Canvas c, Path p, float a, float sw) {
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(sw);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeJoin(Paint.Join.ROUND);
        P.setColor(col(sHue, a));
        c.drawPath(p, P);
        P.setStrokeCap(Paint.Cap.BUTT);
    }

    private static void roundRect(Canvas c, float l, float t, float r, float b, float rad, float a, boolean stroke, float sw) {
        P.setShader(null);
        RF.set(l, t, r, b);
        if (stroke) {
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(sw);
        } else {
            P.setStyle(Paint.Style.FILL);
        }
        P.setColor(col(sHue, a));
        c.drawRoundRect(RF, rad, rad, P);
    }

    /** Slow sideways drift for large shapes. */
    private static void drift(Canvas c, float phase) {
        float s = sw(36f, phase);
        c.translate(s * 26f, s * 6f);
    }

    /** Upward bob for small particles. */
    private static void bob(Canvas c, float period, float phase) {
        float s = 0.5f + 0.5f * sw(period, phase);
        c.translate(0f, -34f * s);
    }

    /**
     * Draw the scene for {@code brand} (or a generated one from {@code genericHue} when brand is
     * Brand.NONE) into the rectangle, at the given opacity.
     */
    static void draw(Canvas c, float l, float t, float r, float b, int brand, int genericHue, long now, float alpha) {
        if (alpha <= 0.01f) return;
        float w = r - l;
        float h = b - t;
        int bg, g1, g2;
        if (brand >= 0) {
            bg = Brand.BG[brand];
            g1 = Brand.G1[brand];
            g2 = Brand.G2[brand];
            sHue = Brand.HUE[brand];
        } else {
            sHue = genericHue;
            bg = Brand.shade(genericHue, 0.07f);
            g1 = Brand.shade(genericHue, 0.38f);
            g2 = Brand.shade(genericHue, 0.20f);
        }
        sA = alpha;
        tt = (now % 3600000L) / 1000f * speed();

        c.save();
        c.clipRect(l, t, r, b);
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(Ui.alpha(bg, alpha));
        c.drawRect(l, t, r, b, P);
        float big = Math.max(w, h);
        P.setShader(new RadialGradient(l + w * 0.18f, t + h * 0.12f, big * 0.75f,
                Ui.alpha(g1, alpha), Ui.alpha(g1, 0f), Shader.TileMode.CLAMP));
        c.drawRect(l, t, r, b, P);
        P.setShader(new RadialGradient(l + w * 0.86f, t + h * 0.96f, big * 0.70f,
                Ui.alpha(g2, alpha), Ui.alpha(g2, 0f), Shader.TileMode.CLAMP));
        c.drawRect(l, t, r, b, P);
        P.setShader(null);

        float k = Math.max(w / 1600f, h / 900f);
        c.translate(l + w / 2f, t + h / 2f);
        c.scale(k, k);
        c.translate(-800f, -450f);

        switch (brand) {
            case Brand.NETFLIX: netflix(c); break;
            case Brand.YOUTUBE: youtube(c, true); break;
            case Brand.SPOTIFY: spotify(c); break;
            case Brand.HOTSTAR: hotstar(c); break;
            case Brand.JIOTV: jiotv(c); break;
            case Brand.BROWSE: browse(c); break;
            case Brand.KARTOONS: kartoons(c); break;
            default: youtube(c, false); break;
        }
        c.restore();
    }

    private static void netflix(Canvas c) {
        c.save();
        drift(c, 0f);
        fillPath(c, BEAM1, 0.10f);
        c.restore();
        c.save();
        drift(c, 1.7f);
        fillPath(c, BEAM2, 0.14f);
        c.restore();
        roundRect(c, 560, 520, 1040, 790, 16, 0.5f, true, 3f);
        roundRect(c, 590, 550, 1010, 760, 8, 0.12f, false, 0f);
        float[][] d = {{300, 200, 5, .55f}, {1280, 300, 7, .4f}, {420, 640, 4, .5f},
                {1180, 700, 6, .35f}, {860, 160, 4, .5f}, {160, 480, 6, .3f}};
        for (int i = 0; i < d.length; i++) {
            c.save();
            bob(c, 10f + i, i * 1.3f);
            fillCircle(c, d[i][0], d[i][1], d[i][2], 0xFFFFFFFF, d[i][3]);
            c.restore();
        }
    }

    private static void youtube(Canvas c, boolean withPlay) {
        float[] rad = {150f, 270f, 390f, 530f, 690f};
        float[] al = {.6f, .38f, .24f, .14f, .08f};
        float[] sk = {4f, 3f, 3f, 2f, 2f};
        for (int i = 0; i < rad.length; i++) {
            float s = 1f + 0.035f * sw(7f, i * 1.5f);
            ringCircle(c, 800f, 450f, rad[i] * s, al[i], sk[i]);
        }
        if (withPlay) {
            c.save();
            drift(c, 0.8f);
            fillPath(c, PLAY, 0.20f);
            c.restore();
        }
    }

    private static void spotify(Canvas c) {
        float[] al = {.10f, .6f, .38f, .28f, .16f, .10f};
        float[] sk = {0f, 4f, 3f, 3f, 2f, 2f};
        for (int i = 0; i < WAVES.length; i++) {
            c.save();
            drift(c, i * 0.9f);
            if (i == 0) fillPath(c, WAVES[i], al[i]);
            else strokePath(c, WAVES[i], al[i], sk[i]);
            c.restore();
        }
    }

    private static void hotstar(Canvas c) {
        float s = 1f + 0.035f * sw(7f, 0f);
        c.save();
        c.translate(1100f, 350f);
        c.scale(s, s);
        c.translate(-1100f, -350f);
        fillPath(c, STAR, 0.16f);
        c.restore();
        for (int i = 0; i < HS_LINES.length; i++) {
            float[] a = HS_PTS[HS_LINES[i][0]];
            float[] b = HS_PTS[HS_LINES[i][1]];
            P.setShader(null);
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(2.5f);
            P.setColor(col(sHue, 0.38f));
            c.drawLine(a[0], a[1], b[0], b[1], P);
        }
        for (int i = 0; i < HS_PTS.length; i++) {
            c.save();
            bob(c, 10f, i * 0.9f);
            fillCircle(c, HS_PTS[i][0], HS_PTS[i][1], 6f + (i % 4) * 1.6f, sHue, 0.5f + (i % 3) * 0.15f);
            c.restore();
        }
    }

    private static void jiotv(Canvas c) {
        fillCircle(c, 260f, 760f, 14f + 2f * sw(7f, 0f), sHue, 1f);
        float[] rad = {210f, 400f, 600f, 820f, 1060f};
        float[] al = {.6f, .42f, .28f, .18f, .10f};
        for (int i = 0; i < rad.length; i++) {
            float s = 1f + 0.035f * sw(7f, i * 1.2f);
            ringCircle(c, 260f, 760f, rad[i] * s, al[i], i < 3 ? 4f : 3f);
        }
        c.save();
        drift(c, 0.4f);
        roundRect(c, 1020, 150, 1400, 364, 18, 0.10f, false, 0f);
        c.restore();
        c.save();
        drift(c, 2.2f);
        roundRect(c, 1120, 250, 1500, 464, 18, 0.4f, true, 3f);
        c.restore();
    }

    private static void browse(Canvas c) {
        ringCircle(c, 800f, 450f, 360f, 0.6f, 3f);
        float s1 = 1f + 0.035f * sw(7f, 0f);
        float s2 = 1f + 0.035f * sw(7f, 2.3f);
        RF.set(800f - 150f * s1, 450f - 360f, 800f + 150f * s1, 450f + 360f);
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(3f);
        P.setColor(col(sHue, 0.38f));
        c.drawOval(RF, P);
        RF.set(800f - 270f * s2, 450f - 360f, 800f + 270f * s2, 450f + 360f);
        P.setColor(col(sHue, 0.26f));
        c.drawOval(RF, P);
        P.setColor(col(sHue, 0.32f));
        c.drawLine(440f, 450f, 1160f, 450f, P);
        c.drawLine(470f, 320f, 1130f, 320f, P);
        c.drawLine(470f, 580f, 1130f, 580f, P);
        c.save();
        c.rotate(-18f + 4f * sw(36f, 0f), 800f, 450f);
        RF.set(240f, 300f, 1360f, 600f);
        P.setColor(col(sHue, 0.30f));
        c.drawOval(RF, P);
        c.restore();
        float[][] d = {{1300, 330, 10, .9f}, {300, 570, 8, .7f}, {1000, 90, 6, .6f}};
        for (int i = 0; i < d.length; i++) {
            c.save();
            bob(c, 10f + i, i * 1.1f);
            fillCircle(c, d[i][0], d[i][1], d[i][2], sHue, d[i][3]);
            c.restore();
        }
    }

    private static void kartoons(Canvas c) {
        c.save();
        drift(c, 0f);
        fillCircle(c, 300f, 260f, 160f, sHue, 0.20f);
        c.restore();
        c.save();
        drift(c, 1.1f);
        fillCircle(c, 1320f, 190f, 110f, sHue, 0.16f);
        c.restore();
        c.save();
        drift(c, 2.1f);
        fillCircle(c, 1120f, 720f, 210f, sHue, 0.14f);
        c.restore();
        c.save();
        drift(c, 3.0f);
        fillCircle(c, 520f, 760f, 90f, sHue, 0.24f);
        c.restore();
        c.save();
        drift(c, 4.0f);
        c.rotate(16f, 845f, 265f);
        roundRect(c, 760, 180, 930, 350, 44, 0.20f, false, 0f);
        c.restore();
        c.save();
        bob(c, 10f, 0f);
        strokePath(c, SQUIG1, 0.55f, 9f);
        c.restore();
        c.save();
        bob(c, 12f, 1.4f);
        strokePath(c, SQUIG2, 0.40f, 8f);
        c.restore();
        float[][] d = {{720, 620, 14, .8f}, {980, 420, 10, .6f}, {200, 120, 9, .7f}};
        for (int i = 0; i < d.length; i++) {
            c.save();
            bob(c, 9f + i, i * 1.7f);
            fillCircle(c, d[i][0], d[i][1], d[i][2], sHue, d[i][3]);
            c.restore();
        }
    }
}
