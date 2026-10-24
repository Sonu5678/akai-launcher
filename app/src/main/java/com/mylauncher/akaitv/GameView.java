package com.mylauncher.akaitv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.View;

/**
 * A small Flappy-style game played with the remote: press OK to glide between the towers.
 * Entirely original art, drawn in code. Back exits.
 */
final class GameView extends View {
    interface Host {
        void onExit();
    }

    private static final int READY = 0, PLAY = 1, DEAD = 2;
    private static final int TOWERS = 4;

    private final Host host;
    private final SharedPreferences prefs;
    private final Skyline skyline = new Skyline();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private int state = READY;
    private float w, h;
    private float batY, vy, scroll, flapAnim;
    private final float[] tx = new float[TOWERS];
    private final float[] gapY = new float[TOWERS];
    private final boolean[] passed = new boolean[TOWERS];
    private int score, best;
    private long last;
    private float deadTime;
    private boolean running;

    GameView(Context c, Host host) {
        super(c);
        this.host = host;
        prefs = c.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        setFocusable(true);
        setFocusableInTouchMode(true);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        text.setColor(0xFFFFFFFF);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        last = 0;
        requestFocus();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        running = visibility == VISIBLE;
        last = 0;
        if (running) postInvalidateOnAnimation();
    }

    private void reset() {
        batY = h * 0.45f;
        vy = 0;
        score = 0;
        scroll = 0;
        for (int i = 0; i < TOWERS; i++) {
            tx[i] = w * 1.0f + i * w * 0.42f;
            gapY[i] = randomGap();
            passed[i] = false;
        }
    }

    private float randomGap() {
        return h * (0.28f + (float) Math.random() * 0.36f);
    }

    private void flap() {
        if (state == READY) {
            reset();
            state = PLAY;
        } else if (state == DEAD) {
            if (deadTime < 0.4f) return; // don't restart by accident
            reset();
            state = PLAY;
        }
        vy = -h * 0.82f;
        flapAnim = 1f;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (event.getRepeatCount() == 0) flap();
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                host.onExit();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true; // swallow so focus never wanders off the game
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void die() {
        state = DEAD;
        deadTime = 0;
        if (score > best) {
            best = score;
            prefs.edit().putInt("best", best).apply();
        }
    }

    private void update(float dt) {
        if (flapAnim > 0) flapAnim = Math.max(0f, flapAnim - dt * 3.2f);
        if (state == READY) {
            batY = h * 0.45f + (float) Math.sin(scroll / (w * 0.12f)) * h * 0.02f;
            scroll += w * 0.1f * dt;
            return;
        }
        if (state == DEAD) {
            deadTime += dt;
            vy += h * 2.6f * dt;
            batY = Math.min(batY + vy * dt, h * 0.94f);
            return;
        }
        float speed = w * 0.3f;
        scroll += speed * dt;
        vy += h * 2.4f * dt;
        batY += vy * dt;
        float r = h * 0.035f;
        float tw = w * 0.075f;
        float gap = h * 0.30f;
        float bx = w * 0.28f;
        if (batY < r) {
            batY = r;
            if (vy < 0) vy = 0;
        }
        if (batY + r > h * 0.93f) {
            die();
            return;
        }
        for (int i = 0; i < TOWERS; i++) {
            tx[i] -= speed * dt;
            if (tx[i] + tw < 0) {
                float far = 0;
                for (int j = 0; j < TOWERS; j++) far = Math.max(far, tx[j]);
                tx[i] = far + w * 0.42f;
                gapY[i] = randomGap();
                passed[i] = false;
            }
            if (!passed[i] && tx[i] + tw < bx - r) {
                passed[i] = true;
                score++;
            }
            if (bx + r * 0.8f > tx[i] && bx - r * 0.8f < tx[i] + tw) {
                if (batY - r * 0.8f < gapY[i] - gap / 2f || batY + r * 0.8f > gapY[i] + gap / 2f) {
                    die();
                    return;
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        w = getWidth();
        h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (tx[0] == 0 && tx[1] == 0) reset();

        long now = System.nanoTime();
        float dt = last == 0 ? 0f : Math.min(0.033f, (now - last) / 1e9f);
        last = now;
        update(dt);

        skyline.draw(c, w, h, scroll, true);
        float tw = w * 0.075f;
        float gap = h * 0.30f;
        for (int i = 0; i < TOWERS; i++) {
            if (state == READY && tx[i] > w) continue;
            if (tx[i] > w + tw) continue;
            Skyline.drawTower(c, fill, path, tx[i], tw, -10f, gapY[i] - gap / 2f, true);
            Skyline.drawTower(c, fill, path, tx[i], tw, gapY[i] + gap / 2f, h + 10f, false);
        }
        float flap = state == DEAD ? -0.8f : (flapAnim > 0 ? 0.3f + flapAnim * 0.7f : -0.4f);
        float tilt = state == PLAY ? Math.max(-25f, Math.min(35f, vy / h * 22f)) : (state == DEAD ? 60f : 0f);
        BatArt.draw(c, path, fill, w * 0.28f, batY, h * 0.075f, flap, tilt);

        text.setStyle(Paint.Style.FILL);
        if (state != READY) {
            text.setTextSize(h * 0.1f);
            text.setColor(0xF2FFFFFF);
            c.drawText(String.valueOf(score), w / 2f, h * 0.16f, text);
        }
        if (state == READY) {
            text.setColor(0xF2FFFFFF);
            text.setTextSize(h * 0.045f);
            c.drawText("Press OK to glide", w / 2f, h * 0.58f, text);
            text.setTextSize(h * 0.03f);
            text.setColor(0xB3FFFFFF);
            c.drawText("Best  " + best + "     Back to exit", w / 2f, h * 0.635f, text);
        } else if (state == DEAD) {
            text.setColor(0xF2FFFFFF);
            text.setTextSize(h * 0.07f);
            c.drawText("Game over", w / 2f, h * 0.42f, text);
            text.setTextSize(h * 0.035f);
            c.drawText("Score " + score + "    Best " + best, w / 2f, h * 0.49f, text);
            text.setColor(0xB3FFFFFF);
            text.setTextSize(h * 0.03f);
            c.drawText("OK to try again  -  Back to exit", w / 2f, h * 0.55f, text);
        }
        if (running) postInvalidateOnAnimation();
    }
}
