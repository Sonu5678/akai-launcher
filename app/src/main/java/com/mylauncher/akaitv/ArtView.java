package com.mylauncher.akaitv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Tiles and icons drawn in code, so the launcher needs no image files. */
final class ArtView extends View {
    static final int INPUT_TILE = 0, MORE = 1, PLAY = 2, WIFI = 3, GEAR = 4, HDMI = 5, AV = 6,
            LIVE = 7, OTHER = 8, SOUND = 9, DISPLAY = 10, BT = 11, WIFI_TILE = 12,
            GEAR_TILE = 13, APPS_TILE = 14;

    private int kind;
    private boolean connected;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path batPath = new Path();
    private final RectF rf = new RectF();
    private Shader bg;
    private int bgW = -1, bgH = -1, bgKind = -1;
    private Skyline skyline;
    private int holeColor = 0xFF2b2b30;

    ArtView(Context c, int kind) {
        super(c);
        this.kind = kind;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        setFocusable(false);
    }

    void set(int kind, boolean connected) {
        this.kind = kind;
        this.connected = connected;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        switch (kind) {
            case PLAY:
                drawPlay(c, w, h);
                return;
            case WIFI:
                drawWifiGlyph(c, w / 2f, h * 0.62f, Math.min(w, h));
                return;
            case GEAR:
                holeColor = 0xFF2b2b30;
                drawGearGlyph(c, w / 2f, h / 2f, Math.min(w, h));
                return;
            default:
                break;
        }
        if (bg == null || bgW != (int) w || bgH != (int) h || bgKind != kind) {
            int top, bottom;
            if (kind == INPUT_TILE) {
                top = 0xFF3b4fe0;
                bottom = 0xFF171b55;
            } else if (kind == MORE) {
                top = 0xFF34343a;
                bottom = 0xFF18181c;
            } else {
                top = 0xFF263250;
                bottom = 0xFF0f1424;
            }
            bg = new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP);
            bgW = (int) w;
            bgH = (int) h;
            bgKind = kind;
        }
        fill.setShader(bg);
        c.drawRect(0, 0, w, h, fill);
        fill.setShader(null);

        float s;
        float cx = w / 2f, cy;
        if (kind == INPUT_TILE || kind == MORE) {
            s = h * 0.52f;
            cy = h * 0.5f;
        } else {
            s = Math.min(w, h * 1.3f) * 0.46f;
            cy = h * 0.36f;
        }
        switch (kind) {
            case INPUT_TILE:
            case HDMI:
                drawHdmi(c, cx, cy, s);
                break;
            case AV:
                drawAv(c, cx, cy, s);
                break;
            case LIVE:
                drawLive(c, cx, cy, s);
                break;
            case MORE:
                drawMore(c, cx, cy, s);
                break;
            case SOUND:
                drawSound(c, cx, cy, s);
                break;
            case DISPLAY:
                drawDisplay(c, cx, cy, s);
                break;
            case BT:
                drawBt(c, cx, cy, s);
                break;
            case WIFI_TILE:
                drawWifiGlyph(c, cx, cy + s * 0.22f, s * 1.1f);
                break;
            case GEAR_TILE:
                holeColor = 0xFF1d2740;
                drawGearGlyph(c, cx, cy, s * 1.25f);
                break;
            case APPS_TILE:
                drawMore(c, cx, cy, s * 0.8f);
                break;
            default:
                drawOther(c, cx, cy, s);
                break;
        }
        if (connected && kind != INPUT_TILE && kind != MORE) {
            fill.setColor(0xFF34D399);
            float r = Math.min(w, h) * 0.035f;
            c.drawCircle(w - Math.min(w, h) * 0.1f, Math.min(w, h) * 0.1f, r, fill);
        }
    }

    private void drawHdmi(Canvas c, float cx, float cy, float s) {
        float l = cx - s * 0.5f, r = cx + s * 0.5f, t = cy - s * 0.27f, b = cy + s * 0.27f;
        float ch = s * 0.13f;
        path.reset();
        path.moveTo(l, t);
        path.lineTo(r, t);
        path.lineTo(r, b - ch);
        path.lineTo(r - ch * 1.1f, b);
        path.lineTo(l + ch * 1.1f, b);
        path.lineTo(l, b - ch);
        path.close();
        line.setColor(0xFFFFFFFF);
        line.setStrokeWidth(s * 0.06f);
        c.drawPath(path, line);
        line.setStrokeWidth(s * 0.04f);
        for (int i = 0; i < 7; i++) {
            float x = l + s * 0.14f + i * (s * 0.72f / 6f);
            c.drawLine(x, t + s * 0.11f, x, t + s * 0.2f, line);
        }
    }

    private void drawAv(Canvas c, float cx, float cy, float s) {
        int[] cols = {0xFFFFC83D, 0xFFF2F2F2, 0xFFEF5350};
        for (int i = 0; i < 3; i++) {
            float x = cx + (i - 1) * s * 0.36f;
            fill.setColor(cols[i]);
            c.drawCircle(x, cy, s * 0.13f, fill);
            line.setColor(0x99FFFFFF);
            line.setStrokeWidth(s * 0.03f);
            c.drawCircle(x, cy, s * 0.18f, line);
        }
    }

    private void drawLive(Canvas c, float cx, float cy, float s) {
        line.setColor(0xFFFFFFFF);
        line.setStrokeWidth(s * 0.06f);
        rf.set(cx - s * 0.5f, cy - s * 0.24f, cx + s * 0.5f, cy + s * 0.22f);
        c.drawRoundRect(rf, s * 0.07f, s * 0.07f, line);
        c.drawLine(cx - s * 0.18f, cy + s * 0.34f, cx + s * 0.18f, cy + s * 0.34f, line);
        c.drawLine(cx, cy + s * 0.22f, cx, cy + s * 0.34f, line);
        line.setStrokeWidth(s * 0.04f);
        c.drawLine(cx, cy - s * 0.24f, cx - s * 0.2f, cy - s * 0.46f, line);
        c.drawLine(cx, cy - s * 0.24f, cx + s * 0.2f, cy - s * 0.46f, line);
    }

    private void drawOther(Canvas c, float cx, float cy, float s) {
        line.setColor(0xFFFFFFFF);
        line.setStrokeWidth(s * 0.06f);
        rf.set(cx - s * 0.4f, cy - s * 0.3f, cx + s * 0.4f, cy + s * 0.3f);
        c.drawRoundRect(rf, s * 0.08f, s * 0.08f, line);
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, s * 0.07f, fill);
    }

    private void drawMore(Canvas c, float cx, float cy, float s) {
        float q = s * 0.36f, g = s * 0.1f;
        fill.setColor(0xE6FFFFFF);
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                float x = cx - q - g / 2f + i * (q + g);
                float y = cy - q - g / 2f + j * (q + g);
                rf.set(x, y, x + q, y + q);
                c.drawRoundRect(rf, q * 0.25f, q * 0.25f, fill);
            }
        }
    }

    private void drawWifiGlyph(Canvas c, float cx, float cy, float s) {
        line.setColor(0xFFFFFFFF);
        line.setStrokeWidth(s * 0.075f);
        for (int i = 1; i <= 3; i++) {
            float r = s * 0.15f * i;
            rf.set(cx - r, cy - r, cx + r, cy + r);
            c.drawArc(rf, 225f, 90f, false, line);
        }
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, s * 0.055f, fill);
    }

    private void drawGearGlyph(Canvas c, float cx, float cy, float s) {
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, s * 0.24f, fill);
        for (int i = 0; i < 8; i++) {
            c.save();
            c.rotate(i * 45f, cx, cy);
            rf.set(cx - s * 0.06f, cy - s * 0.37f, cx + s * 0.06f, cy - s * 0.2f);
            c.drawRoundRect(rf, s * 0.02f, s * 0.02f, fill);
            c.restore();
        }
        // hole: paint it with the background's own colour
        fill.setColor(holeColor);
        c.drawCircle(cx, cy, s * 0.1f, fill);
    }

    private void drawSound(Canvas c, float cx, float cy, float s) {
        line.setColor(0xFFFFFFFF);
        line.setStrokeWidth(s * 0.06f);
        float l = cx - s * 0.5f;
        path.reset();
        path.moveTo(l, cy - s * 0.14f);
        path.lineTo(l + s * 0.2f, cy - s * 0.14f);
        path.lineTo(l + s * 0.5f, cy - s * 0.34f);
        path.lineTo(l + s * 0.5f, cy + s * 0.34f);
        path.lineTo(l + s * 0.2f, cy + s * 0.14f);
        path.lineTo(l, cy + s * 0.14f);
        path.close();
        c.drawPath(path, line);
        for (int i = 1; i <= 2; i++) {
            float r = s * 0.2f * i;
            rf.set(cx - r, cy - r, cx + r, cy + r);
            c.drawArc(rf, -45f, 90f, false, line);
        }
    }

    private void drawDisplay(Canvas c, float cx, float cy, float s) {
        line.setColor(0xFFFFFFFF);
        line.setStrokeWidth(s * 0.06f);
        rf.set(cx - s * 0.5f, cy - s * 0.32f, cx + s * 0.5f, cy + s * 0.22f);
        c.drawRoundRect(rf, s * 0.07f, s * 0.07f, line);
        c.drawLine(cx - s * 0.2f, cy + s * 0.36f, cx + s * 0.2f, cy + s * 0.36f, line);
        c.drawLine(cx, cy + s * 0.22f, cx, cy + s * 0.36f, line);
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy - s * 0.05f, s * 0.09f, fill);
    }

    private void drawBt(Canvas c, float cx, float cy, float s) {
        line.setColor(0xFFFFFFFF);
        line.setStrokeWidth(s * 0.06f);
        path.reset();
        path.moveTo(cx - s * 0.25f, cy - s * 0.2f);
        path.lineTo(cx + s * 0.25f, cy + s * 0.2f);
        path.lineTo(cx, cy + s * 0.4f);
        path.lineTo(cx, cy - s * 0.4f);
        path.lineTo(cx + s * 0.25f, cy - s * 0.2f);
        path.lineTo(cx - s * 0.25f, cy + s * 0.2f);
        c.drawPath(path, line);
    }

    private void drawPlay(Canvas c, float w, float h) {
        if (skyline == null) skyline = new Skyline();
        skyline.draw(c, w, h, w * 0.4f, true);
        fill.setShader(null);
        // two towers
        float tw = w * 0.07f;
        Skyline.drawTower(c, fill, path, w * 0.62f, tw, -10f, h * 0.36f, true);
        Skyline.drawTower(c, fill, path, w * 0.62f, tw, h * 0.7f, h + 10f, false);
        BatArt.draw(c, batPath, fill, w * 0.3f, h * 0.45f, h * 0.34f, 0.35f, -8f);
    }
}
