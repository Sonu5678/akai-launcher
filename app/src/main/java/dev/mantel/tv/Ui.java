package dev.mantel.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Design tokens (section 5 of the spec), shared paints and small drawing helpers. */
final class Ui {
    private Ui() {}

    // Design canvas
    static final int W = 1920, H = 1080;

    // Colour tokens
    static final int BASE = 0xFF07080C;
    static final int SURFACE1 = 0xFF13151C;
    static final int SURFACE2 = 0xFF242733;
    static final int SHEET = 0xEB101116;          // #101116 at 92%
    static final int TEXT = 0xFFF4F1EA;
    static final int TEXT2 = 0xFFBDBAB4;
    static final int TEXT_OFF = 0xFF7C7A76;
    static final int ERROR = 0xFFFF6B5E;
    static final int SUCCESS = 0xFF6BD29A;
    static final int[] ACCENTS = {0xFFFF8A4C, 0xFF38D0EA, 0xFF7C7CFF, 0xFF2EE59D};
    static final String[] ACCENT_NAMES = {"Ember", "Cyan", "Indigo", "Emerald"};
    static final float[] DIM = {0f, 0.25f, 0.40f, 0.55f};
    static final String[] DIM_NAMES = {"Off", "25%", "40%", "55%"};

    static int accent = ACCENTS[0];

    // Pixel scale: device pixels per design pixel
    static float scale = 1f;
    static boolean animOff = false;

    static Typeface light = Typeface.DEFAULT;
    static Typeface regular = Typeface.DEFAULT;
    static Typeface medium = Typeface.DEFAULT;
    /** Display face for names and the clock. Falls back to Inter Medium if the font is not bundled. */
    static Typeface display = Typeface.DEFAULT;

    static final Handler MAIN = new Handler(Looper.getMainLooper());
    static final ExecutorService IO = Executors.newSingleThreadExecutor();

    // Shared paints (UI thread only)
    static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    static final Paint TP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    static final Paint BP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    static final RectF R = new RectF();

    static void init(Context c) {
        try {
            light = Typeface.createFromAsset(c.getAssets(), "fonts/Inter-Light.otf");
            regular = Typeface.createFromAsset(c.getAssets(), "fonts/Inter-Regular.otf");
            medium = Typeface.createFromAsset(c.getAssets(), "fonts/Inter-Medium.otf");
            display = medium;
        } catch (Throwable t) {
            light = Typeface.create("sans-serif-light", Typeface.NORMAL);
            regular = Typeface.create("sans-serif", Typeface.NORMAL);
            medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            display = medium;
        }
        try {
            // Optional variable display font (Bricolage Grotesque), fetched by the cloud build.
            java.io.InputStream in = c.getAssets().open("fonts/Display.ttf");
            in.close();
            display = new Typeface.Builder(c.getAssets(), "fonts/Display.ttf")
                    .setFontVariationSettings("'wght' 700, 'opsz' 48").build();
            if (display == null) display = medium;
        } catch (Throwable t) {
            display = medium;
        }
        refresh(c);
    }

    /** Re-read the display scale and the system animation setting. */
    static void refresh(Context c) {
        try {
            android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
            scale = Math.min(dm.widthPixels / 1920f, dm.heightPixels / 1080f);
            if (scale <= 0.1f) scale = 1f;
        } catch (Throwable t) {
            scale = 1f;
        }
        try {
            float a = Settings.Global.getFloat(c.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            animOff = a == 0f;
        } catch (Throwable t) {
            animOff = false;
        }
    }

    /** Duration helper: every duration drops to 0 when the system animation scale is 0. */
    static long dur(long ms) {
        return animOff ? 0L : ms;
    }

    static final String[] MOOD_NAMES = {"Auto", "Dawn", "Day", "Dusk", "Night"};
    static final int[] MOOD_TINT = {0, 0x1AFFAA78, 0x08FFFFFF, 0x1AFF6E3C, 0x33141E6E};

    /** Which of Dawn, Day, Dusk or Night is showing now (1 to 4). */
    static int moodNow() {
        int m = Prefs.mood();
        if (m > 0) return m;
        int hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hr >= 5 && hr < 9) return 1;
        if (hr >= 9 && hr < 17) return 2;
        if (hr >= 17 && hr < 22) return 3;
        return 4;
    }

    static int moodTint() {
        return MOOD_TINT[moodNow()];
    }

    static String moodName() {
        return MOOD_NAMES[moodNow()];
    }

    static int alpha(int color, float a) {
        int al = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
        return (al << 24) | (color & 0x00FFFFFF);
    }

    // ---- drawing helpers (design pixels) ----

    static void fillAll(Canvas c, int color) {
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        c.drawRect(-800, -800, 2720, 1880, P);
    }

    static void rr(Canvas c, float l, float t, float r, float b, float rad, int color) {
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        R.set(l, t, r, b);
        c.drawRoundRect(R, rad, rad, P);
    }

    static void ring(Canvas c, float l, float t, float r, float b, float rad, float sw, int color) {
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(sw);
        P.setColor(color);
        R.set(l, t, r, b);
        c.drawRoundRect(R, rad, rad, P);
        P.setStyle(Paint.Style.FILL);
    }

    static void circle(Canvas c, float cx, float cy, float r, int color) {
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        c.drawCircle(cx, cy, r, P);
    }

    static void text(Canvas c, String s, float x, float y, float size, Typeface tf,
                     int color, Paint.Align align) {
        if (s == null || s.length() == 0) return;
        TP.setTextSize(size);
        TP.setTypeface(tf);
        TP.setColor(color);
        TP.setTextAlign(align);
        TP.setFontFeatureSettings("tnum");
        c.drawText(s, x, y, TP);
    }

    static float measure(String s, float size, Typeface tf) {
        if (s == null) return 0f;
        TP.setTextSize(size);
        TP.setTypeface(tf);
        TP.setFontFeatureSettings("tnum");
        return TP.measureText(s);
    }

    /** One-line ellipsis fit. */
    static String fit(String s, float maxW, float size, Typeface tf) {
        if (s == null) return "";
        if (measure(s, size, tf) <= maxW) return s;
        int n = s.length();
        while (n > 1 && measure(s.substring(0, n) + "…", size, tf) > maxW) n--;
        return s.substring(0, Math.max(1, n)).trim() + "…";
    }

    /** Wrap into at most maxLines lines; the last line is ellipsised. */
    static java.util.List<String> wrap(String s, float maxW, float size, Typeface tf, int maxLines) {
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        if (s == null) return out;
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            String trial = line.length() == 0 ? w : line + " " + w;
            if (measure(trial, size, tf) <= maxW) {
                line.setLength(0);
                line.append(trial);
            } else {
                if (line.length() > 0) out.add(line.toString());
                line.setLength(0);
                line.append(w);
                if (out.size() == maxLines - 1) {
                    StringBuilder rest = new StringBuilder(w);
                    for (int j = i + 1; j < words.length; j++) rest.append(' ').append(words[j]);
                    out.add(fit(rest.toString(), maxW, size, tf));
                    return out;
                }
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
