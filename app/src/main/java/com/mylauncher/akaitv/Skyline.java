package com.mylauncher.akaitv;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * An original rainy night-city backdrop drawn with plain shapes: hazy sky, three parallax
 * layers of building silhouettes, and falling rain. Used by the game and the Play tile.
 */
final class Skyline {
    private static final int LAYERS = 3;
    private static final int[] COLORS = {0xFF55627f, 0xFF2a3550, 0xFF0b101a};
    private static final float[] SPEED = {0.12f, 0.32f, 0.7f};
    private static final float[] MIN_H = {0.22f, 0.16f, 0.08f};
    private static final float[] MAX_H = {0.50f, 0.38f, 0.24f};

    private final float[][] bx = new float[LAYERS][];
    private final float[][] bw = new float[LAYERS][];
    private final float[][] bh = new float[LAYERS][];
    private final float[] period = new float[LAYERS];
    private final float[] rainX = new float[70];
    private final float[] rainY = new float[70];
    private final float[] rainV = new float[70];

    private final Paint skyPaint = new Paint();
    private final Paint glowPaint = new Paint();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float lw = -1, lh = -1;

    Skyline() {
        rainPaint.setColor(0x55DDE6FF);
        rainPaint.setStrokeCap(Paint.Cap.ROUND);
        Random r = new Random(11);
        for (int i = 0; i < rainX.length; i++) {
            rainX[i] = r.nextFloat();
            rainY[i] = r.nextFloat();
            rainV[i] = 0.7f + r.nextFloat() * 0.8f;
        }
    }

    private void build(float w, float h) {
        lw = w;
        lh = h;
        skyPaint.setShader(new LinearGradient(0, 0, 0, h * 0.95f, 0xFF2d3850, 0xFF9eaac4,
                Shader.TileMode.CLAMP));
        glowPaint.setShader(new RadialGradient(w * 0.78f, h * 0.22f, Math.max(w, h) * 0.45f,
                0x66C9D6FF, 0x00C9D6FF, Shader.TileMode.CLAMP));
        rainPaint.setStrokeWidth(Math.max(1.5f, h * 0.0035f));
        for (int l = 0; l < LAYERS; l++) {
            Random r = new Random(7 + l * 13);
            int cap = 60;
            float[] xs = new float[cap];
            float[] ws = new float[cap];
            float[] hs = new float[cap];
            float x = 0;
            int n = 0;
            float target = w * 2.2f;
            while (x < target && n < cap) {
                float width = w * (0.035f + r.nextFloat() * 0.06f);
                float height = h * (MIN_H[l] + r.nextFloat() * (MAX_H[l] - MIN_H[l]));
                xs[n] = x;
                ws[n] = width;
                hs[n] = height;
                x += width + (l == 2 ? 0 : w * r.nextFloat() * 0.01f);
                n++;
            }
            bx[l] = new float[n];
            bw[l] = new float[n];
            bh[l] = new float[n];
            System.arraycopy(xs, 0, bx[l], 0, n);
            System.arraycopy(ws, 0, bw[l], 0, n);
            System.arraycopy(hs, 0, bh[l], 0, n);
            period[l] = x;
        }
    }

    /** scroll is in pixels travelled by the world; rain animates from the same value. */
    void draw(Canvas c, float w, float h, float scroll, boolean rain) {
        if (w != lw || h != lh) build(w, h);
        c.drawRect(0, 0, w, h, skyPaint);
        c.drawRect(0, 0, w, h, glowPaint);
        for (int l = 0; l < LAYERS; l++) {
            fill.setColor(COLORS[l]);
            float p = period[l];
            float off = -((scroll * SPEED[l]) % p);
            for (int k = 0; k < 2; k++) {
                float base = off + k * p;
                for (int i = 0; i < bx[l].length; i++) {
                    float x = base + bx[l][i];
                    if (x > w || x + bw[l][i] < 0) continue;
                    c.drawRect(x, h - bh[l][i], x + bw[l][i] + 1f, h, fill);
                }
            }
        }
        if (rain) {
            float len = h * 0.035f;
            for (int i = 0; i < rainX.length; i++) {
                float y = (rainY[i] * h + scroll * 0.9f * rainV[i] + i * 31f) % h;
                float x = ((rainX[i] * w - y * 0.18f) % w + w) % w;
                c.drawLine(x, y, x - len * 0.18f, y + len, rainPaint);
            }
        }
    }

    /** A tower used as an obstacle: a column of "building" with faint lit windows. */
    static void drawTower(Canvas c, Paint p, Path scratch, float x, float width, float top,
                          float bottom, boolean capAtBottom) {
        if (bottom <= top) return;
        p.setShader(new LinearGradient(x, 0, x + width, 0, 0xFF3a465e, 0xFF1a2133,
                Shader.TileMode.CLAMP));
        c.drawRect(x, top, x + width, bottom, p);
        p.setShader(null);
        p.setColor(0xFF4c5a76);
        float capH = Math.max(6f, width * 0.12f);
        float capY = capAtBottom ? bottom - capH : top;
        c.drawRect(x - capH * 0.4f, capY, x + width + capH * 0.4f, capY + capH, p);
        p.setColor(0x33FFE9A8);
        float step = Math.max(18f, width * 0.32f);
        float ww = step * 0.42f;
        for (float y = top + step; y < bottom - step * 0.6f; y += step) {
            for (float xx = x + width * 0.16f; xx < x + width - ww; xx += step) {
                c.drawRect(xx, y, xx + ww, y + ww * 0.8f, p);
            }
        }
    }
}
