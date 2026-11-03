package dev.mantel.tv;

import android.os.SystemClock;

/** A single float that eases (ease-out cubic) toward a target. UI thread only. */
final class Tween {
    float cur, from, to;
    long start, dur;
    boolean active;

    Tween(float v) {
        cur = from = to = v;
    }

    void set(float v) {
        cur = from = to = v;
        active = false;
    }

    void go(float target, long ms) {
        if (ms <= 0) {
            set(target);
            return;
        }
        from = cur;
        to = target;
        start = SystemClock.uptimeMillis();
        dur = ms;
        active = true;
    }

    /** Advance to {@code now}; returns true while still animating. */
    boolean tick(long now) {
        if (!active) return false;
        float t = (now - start) / (float) dur;
        if (t >= 1f) {
            cur = to;
            active = false;
            return false;
        }
        if (t < 0f) t = 0f;
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        cur = from + (to - from) * e;
        return true;
    }
}
