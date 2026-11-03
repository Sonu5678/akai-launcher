package dev.mantel.tv;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The wallpaper layer: one decoded image at rest, two only during the 600 ms crossfade.
 * Wallpaper ids: "b1".."b6" built-in, "s:RRGGBB" solid colour, "u:uri" an image from USB.
 */
final class WallpaperView extends View {
    private static final ExecutorService WALL = Executors.newSingleThreadExecutor();

    private static final class Layer {
        Bitmap bmp;
        int color = Ui.BASE;
    }

    private Layer cur = new Layer();
    private Layer prev = null;
    private final Tween fade = new Tween(1f);
    private String curId = "";
    private int seq = 0;
    boolean failed = false;

    /** Share of black laid over the image by the Look "Dim level" (0 to 0.55). */
    float dimLevel = 0.25f;
    /** Extra darkening used by ambient mode (0 to 0.7, so the image reads as 30%). */
    float ambient = 0f;
    /** Extra scale used by ambient drift (0 to 0.03). */
    float drift = 0f;

    private final Context ctx;
    private final Rect src = new Rect();
    private final RectF dst = new RectF(0, 0, 1920, 1080);

    WallpaperView(Context c) {
        super(c);
        ctx = c.getApplicationContext();
        setWillNotDraw(false);
        setFocusable(false);
    }

    String currentId() {
        return curId;
    }

    void setDim(int level) {
        dimLevel = Ui.DIM[Math.max(0, Math.min(3, level))];
        invalidate();
    }

    void show(final String id, final boolean animate) {
        if (id == null || id.equals(curId)) return;
        curId = id;
        final int mySeq = ++seq;
        failed = false;

        if (id.startsWith("s:")) {
            int col = Ui.BASE;
            try {
                col = 0xFF000000 | Integer.parseInt(id.substring(2), 16);
            } catch (NumberFormatException e) {
                col = Ui.BASE;
            }
            Layer l = new Layer();
            l.color = col;
            swap(l, animate);
            return;
        }

        WALL.execute(new Runnable() {
            @Override public void run() {
                Bitmap b = null;
                try {
                    b = load(id);
                } catch (Throwable t) {
                    b = null;
                }
                final Bitmap result = b;
                Ui.MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (mySeq != seq) {
                            if (result != null) result.recycle();
                            return;
                        }
                        Layer l = new Layer();
                        if (result == null) {
                            failed = true;      // Base colour plus scrim, and the Look page says so
                        } else {
                            l.bmp = result;
                        }
                        swap(l, animate);
                    }
                });
            }
        });
    }

    private void swap(Layer next, boolean animate) {
        if (prev != null && prev.bmp != null) prev.bmp.recycle();
        prev = cur;
        cur = next;
        if (animate) {
            fade.set(0f);
            fade.go(1f, Ui.dur(600));
            if (!fade.active) releasePrev();
        } else {
            fade.set(1f);
            releasePrev();
        }
        invalidate();
    }

    private void releasePrev() {
        if (prev != null && prev.bmp != null) prev.bmp.recycle();
        prev = null;
    }

    private Bitmap load(String id) {
        if (id.startsWith("b")) {
            int idx = 0;
            try {
                idx = Integer.parseInt(id.substring(1)) - 1;
            } catch (NumberFormatException e) {
                idx = 0;
            }
            return WallpaperGen.render(idx, 1920, 1080);
        }
        if (id.startsWith("u:")) {
            return decodeUri(ctx, Uri.parse(id.substring(2)), 1920, 1080);
        }
        return null;
    }

    /** Decode an image from a content or file URI, bounded to roughly 1080p. */
    static Bitmap decodeUri(Context c, Uri u, int maxW, int maxH) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = c.getContentResolver().openInputStream(u);
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int sample = 1;
            while (o.outWidth / (sample * 2) >= maxW && o.outHeight / (sample * 2) >= maxH) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            o2.inPreferredConfig = Bitmap.Config.ARGB_8888;
            in = c.getContentResolver().openInputStream(u);
            if (in == null) return null;
            Bitmap raw = BitmapFactory.decodeStream(in, null, o2);
            in.close();
            if (raw == null) return null;
            // Cover-crop to 16:9 at no more than 1080p so memory stays at one 1080p image.
            float ar = raw.getWidth() / (float) raw.getHeight();
            int sw = raw.getWidth();
            int sh = raw.getHeight();
            int cx = 0;
            int cy = 0;
            if (ar > 16f / 9f) {
                sw = Math.round(sh * 16f / 9f);
                cx = (raw.getWidth() - sw) / 2;
            } else {
                sh = Math.round(sw * 9f / 16f);
                cy = (raw.getHeight() - sh) / 2;
            }
            Bitmap cropped = Bitmap.createBitmap(raw, cx, cy, sw, sh);
            Bitmap out = cropped;
            if (sw > maxW) {
                out = Bitmap.createScaledBitmap(cropped, maxW, maxH, true);
            }
            if (out != raw) raw.recycle();
            if (out != cropped) cropped.recycle();
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        float s = Math.min(w / 1920f, h / 1080f);
        long now = SystemClock.uptimeMillis();
        boolean anim = fade.tick(now);
        if (!fade.active && prev != null) releasePrev();

        c.drawColor(Ui.BASE);
        c.save();
        c.translate((w - 1920 * s) / 2f, (h - 1080 * s) / 2f);
        c.scale(s, s);
        if (drift > 0f) {
            c.translate(960f, 540f);
            c.scale(1f + drift, 1f + drift);
            c.translate(-960f, -540f);
        }
        if (prev != null) drawLayer(c, prev, 255);
        drawLayer(c, cur, Math.round(255 * fade.cur));
        c.restore();

        float total = 1f - (1f - dimLevel) * (1f - ambient);
        if (total > 0.001f) c.drawColor(Color.argb(Math.round(255 * total), 0, 0, 0));
        if (anim) postInvalidateOnAnimation();
    }

    private void drawLayer(Canvas c, Layer l, int alpha) {
        if (l.bmp == null) {
            Ui.fillAll(c, Ui.alpha(l.color, alpha / 255f));
            return;
        }
        int bw = l.bmp.getWidth();
        int bh = l.bmp.getHeight();
        float ar = bw / (float) bh;
        if (ar > 16f / 9f) {
            int sw = Math.round(bh * 16f / 9f);
            src.set((bw - sw) / 2, 0, (bw - sw) / 2 + sw, bh);
        } else {
            int sh = Math.round(bw * 9f / 16f);
            src.set(0, (bh - sh) / 2, bw, (bh - sh) / 2 + sh);
        }
        Ui.BP.setAlpha(alpha);
        c.drawBitmap(l.bmp, src, dst, Ui.BP);
        Ui.BP.setAlpha(255);
    }
}
