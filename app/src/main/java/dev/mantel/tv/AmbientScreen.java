package dev.mantel.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Ambient mode (section 8): a quiet resting state. MainActivity fades the chrome out over 1.5 s and
 * dims the wallpaper to 30%; this layer adds the single clock (120 px Light, centred) with the date
 * beneath it. The image drifts 3% over 90 s and the clock shifts 24 px every 10 minutes, which is
 * cosmetic on a QLED panel. Any key wakes it, and that first key never also selects anything.
 *
 * The layer redraws twice a second at most, so ambient mode costs almost nothing.
 */
final class AmbientScreen extends Screen {
    private static final long TICK_MS = 500;
    private static final long DRIFT_MS = 90000L;
    private static final float DRIFT_MAX = 0.03f;
    /** Clock positions, visited in turn (design pixels). */
    private static final int[][] SHIFTS = {{0, 0}, {24, 0}, {24, 24}, {0, 24}};

    private long startedAt = 0;
    private boolean running = false;
    private int lastMinute = -1;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long el = SystemClock.uptimeMillis() - startedAt;
            // One slow out-and-back, so there is never a jump and the motion never loops sharply.
            long phase = el % (2 * DRIFT_MS);
            float t = phase < DRIFT_MS ? phase / (float) DRIFT_MS : (2 * DRIFT_MS - phase) / (float) DRIFT_MS;
            act.wall.drift = DRIFT_MAX * t;
            act.wall.invalidate();
            int minute = (int) (System.currentTimeMillis() / 60000L);
            if (minute != lastMinute) {
                lastMinute = minute;
                invalidate();
            }
            Ui.MAIN.postDelayed(this, TICK_MS);
        }
    };

    AmbientScreen(MainActivity a) {
        super(a);
        setClickable(false);
    }

    void begin() {
        running = true;
        startedAt = SystemClock.uptimeMillis();
        lastMinute = -1;
        Ui.MAIN.removeCallbacks(tick);
        Ui.MAIN.post(tick);
    }

    void end() {
        running = false;
        Ui.MAIN.removeCallbacks(tick);
    }

    // MainActivity routes every key to "wake" while ambient mode is showing, so these never run.
    @Override void onDir(int keyCode) {}

    @Override void onOk() {}

    @Override void onBack() {}

    @Override void draw1(Canvas c, long now) {
        if (!Prefs.ambientClock()) return;
        long ms = System.currentTimeMillis();
        Date d = new Date(ms);
        boolean h24 = DateFormat.is24HourFormat(getContext());
        String time = new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(d);
        String date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(d);

        int step = (int) ((ms / 600000L) % SHIFTS.length);
        float dx = SHIFTS[step][0] - 12f;
        float dy = SHIFTS[step][1] - 12f;

        // 120 px clock centred on (960, 480); the baseline sits half a cap height below centre.
        Ui.text(c, time, 960f + dx, 480f + 44f + dy, 120f, Ui.light, Ui.TEXT, Paint.Align.CENTER);
        Ui.text(c, date, 960f + dx, 480f + 44f + 64f + dy, 32f, Ui.regular, Ui.TEXT2, Paint.Align.CENTER);
    }
}
