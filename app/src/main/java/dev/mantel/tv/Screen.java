package dev.mantel.tv;

import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;

/**
 * One layer of the interface. Everything is drawn on the 1920 x 1080 design canvas, scaled to
 * the real screen, so every number in the spec can be used as written.
 */
abstract class Screen extends FrameLayout {
    final MainActivity act;
    boolean closing = false;
    private boolean animating = false;

    Screen(MainActivity a) {
        super(a);
        act = a;
        setWillNotDraw(false);
        setFocusable(false);
    }

    // ---- key handling (called by MainActivity) ----
    abstract void onDir(int keyCode);
    abstract void onOk();
    void onOkDown() {}
    void onLongOk() {}
    abstract void onBack();

    /** False lets the key pass through to a child view (the Search field and the keyboard). */
    boolean wantsKey(int keyCode) {
        return true;
    }

    // ---- lifecycle ----
    void onEnter() {}
    /** The layer above this one was closed. */
    void onReveal() {}
    void onAppsChanged() {}

    /** +1 rises from the bottom, -1 drops from the top, 0 appears in place. */
    int enterFrom() {
        return 0;
    }

    // ---- drawing ----
    abstract void draw1(Canvas c, long now);

    @Override protected final void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        float s = Math.min(w / 1920f, h / 1080f);
        c.save();
        c.translate((w - 1920 * s) / 2f, (h - 1080 * s) / 2f);
        c.scale(s, s);
        animating = false;
        draw1(c, SystemClock.uptimeMillis());
        c.restore();
        if (animating) postInvalidateOnAnimation();
    }

    /** Advance a tween; keeps the frame loop alive while it moves. */
    final void tw(Tween t, long now) {
        if (t.tick(now)) animating = true;
    }

    /** Keep drawing next frame (spinners and timers). */
    final void keepAnimating() {
        animating = true;
    }

    /** Spoken feedback when a screen reader is on. */
    final void speak(String s) {
        try {
            AccessibilityManager am = (AccessibilityManager) getContext().getSystemService(
                    android.content.Context.ACCESSIBILITY_SERVICE);
            if (am != null && am.isEnabled() && s != null) announceForAccessibility(s);
        } catch (Throwable t) {
            // not important
        }
    }

    /** Slide the layer in. */
    void animateIn() {
        int from = enterFrom();
        if (from == 0 || Ui.animOff) {
            setAlpha(1f);
            return;
        }
        float dist = getHeight() > 0 ? getHeight() : 1080f;
        setTranslationY(from > 0 ? dist : -dist);
        animate().translationY(0f).setDuration(240).setInterpolator(
                new android.view.animation.DecelerateInterpolator(2f)).start();
    }

    /** Slide the layer out, then run {@code done}. */
    void animateOut(final Runnable done) {
        int from = enterFrom();
        if (from == 0 || Ui.animOff) {
            done.run();
            return;
        }
        float dist = getHeight() > 0 ? getHeight() : 1080f;
        animate().translationY(from > 0 ? dist : -dist).setDuration(180).setInterpolator(
                new android.view.animation.AccelerateInterpolator(1.6f)).withEndAction(done).start();
    }

    /** Draw a 6 px nudge value helper: animates a value out and back. */
    static void nudge(Tween t, float amount) {
        t.set(amount);
        t.go(0f, Ui.dur(120));
    }

    /** True while no one should be sending keys (the layer is leaving). */
    boolean isBusy() {
        return closing;
    }

    View asView() {
        return this;
    }
}
