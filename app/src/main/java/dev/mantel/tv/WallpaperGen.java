package dev.mantel.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * Renders the six built-in wallpapers in code: near-black, one soft focal glow upper right,
 * a quiet lower half (section 5). Rendering at runtime keeps the APK small and avoids any
 * image decoding for the built-in set.
 */
final class WallpaperGen {
    private WallpaperGen() {}

    static final String[] NAMES = {"Dusk", "Ember", "Tide", "Moss", "Rose", "Slate"};

    private static final int[][] GLOW1 = {
            {96, 82, 190}, {200, 120, 48}, {30, 130, 150}, {60, 140, 92}, {190, 90, 120}, {90, 110, 150}};
    private static final int[][] GLOW2 = {
            {40, 60, 120}, {110, 50, 40}, {20, 70, 110}, {30, 80, 70}, {90, 40, 80}, {45, 55, 85}};
    private static final float[] CX = {1480, 1500, 1430, 1520, 1460, 1500};
    private static final float[] CY = {250, 230, 290, 260, 240, 270};
    private static final float[] RAD = {760, 720, 780, 740, 740, 800};

    static Bitmap render(int idx, int w, int h) {
        idx = Math.max(0, Math.min(5, idx));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(w / 1920f, h / 1080f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        c.drawColor(Ui.BASE);

        // faint lift toward the top
        p.setShader(new LinearGradient(0, 0, 0, 1080, 0xFF15151D, 0xFF0A0A0E, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 1920, 1080, p);

        // secondary wide glow to the left of centre
        glow(c, p, CX[idx] - 620f, CY[idx] + 120f, RAD[idx] * 1.15f * 1.1f, RAD[idx] * 1.15f * 0.7f,
                GLOW2[idx], 0.30f);
        // primary glow, upper right (the focal interest)
        glow(c, p, CX[idx], CY[idx], RAD[idx], RAD[idx] * 0.8f, GLOW1[idx], 0.50f);

        // keep the lower half quiet: pull toward the base colour
        p.setShader(new LinearGradient(0, 540, 0, 900, 0x000A0A0E, 0x990A0A0E, Shader.TileMode.CLAMP));
        c.drawRect(0, 540, 1920, 1080, p);

        // very faint noise so dark gradients do not band
        Bitmap n = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Random rnd = new Random(7);
        int[] px = new int[128 * 128];
        for (int i = 0; i < px.length; i++) {
            int v = rnd.nextBoolean() ? 255 : 0;
            px[i] = Color.argb(3, v, v, v);
        }
        n.setPixels(px, 0, 128, 0, 0, 128, 128);
        p.setShader(new BitmapShader(n, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        c.drawRect(0, 0, 1920, 1080, p);
        p.setShader(null);
        n.recycle();
        return b;
    }

    private static void glow(Canvas c, Paint p, float cx, float cy, float rx, float ry, int[] col, float a) {
        int[] colors = {
                Color.argb(Math.round(255 * a), col[0], col[1], col[2]),
                Color.argb(Math.round(255 * a * 0.62f), col[0], col[1], col[2]),
                Color.argb(Math.round(255 * a * 0.20f), col[0], col[1], col[2]),
                Color.argb(0, col[0], col[1], col[2])};
        float[] stops = {0f, 0.35f, 0.70f, 1f};
        p.setShader(new RadialGradient(0, 0, 1f, colors, stops, Shader.TileMode.CLAMP));
        c.save();
        c.translate(cx, cy);
        c.scale(rx, ry);
        c.drawRect(-1f, -1f, 1f, 1f, p);
        c.restore();
        p.setShader(null);
    }
}
