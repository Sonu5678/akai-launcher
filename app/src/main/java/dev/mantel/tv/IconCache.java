package dev.mantel.tv;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import java.util.HashSet;

/**
 * Square tiles per section 4: masked to a rounded square, transparent icons placed on a
 * surface plate, banners centred on the plate with padding. Rendered off the UI thread and
 * loaded lazily, so a screen only pays for the rows it shows.
 */
final class IconCache {
    private IconCache() {}

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(14 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static final HashSet<String> LOADING = new HashSet<String>();

    /** Called on the UI thread whenever an icon finishes loading. */
    static Runnable onLoaded;

    /** Cached tile or null (a load is then scheduled and onLoaded fires when it is ready). */
    static Bitmap get(Context ctx, final AppEntry e, int designSize, final float designRadius) {
        final int px = Math.max(16, Math.round(designSize * Ui.scale));
        final String key = e.pkg + "@" + px;
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        if (LOADING.add(key)) {
            final Context app = ctx.getApplicationContext();
            final float rad = designRadius * Ui.scale;
            Ui.IO.execute(new Runnable() {
                @Override public void run() {
                    Bitmap made = null;
                    try {
                        made = render(app, e, px, rad);
                    } catch (Throwable t) {
                        made = null;
                    }
                    final Bitmap result = made;
                    Ui.MAIN.post(new Runnable() {
                        @Override public void run() {
                            LOADING.remove(key);
                            if (result != null) {
                                CACHE.put(key, result);
                                if (onLoaded != null) onLoaded.run();
                            }
                        }
                    });
                }
            });
        }
        return null;
    }

    static void clear() {
        CACHE.evictAll();
    }

    private static Bitmap render(Context ctx, AppEntry e, int px, float radius) {
        PackageManager pm = ctx.getPackageManager();
        Bitmap content = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cc = new Canvas(content);
        Drawable d = null;
        try {
            d = e.ai.loadIcon(pm);
        } catch (Throwable t) {
            d = null;
        }
        if (d == null) {
            try {
                d = e.ai.loadBanner(pm);
            } catch (Throwable t) {
                d = null;
            }
        }
        float pad16 = 16f * Ui.scale;

        if (d == null) {
            cc.drawColor(Ui.SURFACE2);
            Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
            tp.setColor(Ui.TEXT2);
            tp.setTypeface(Ui.medium);
            tp.setTextSize(px * 0.42f);
            tp.setTextAlign(Paint.Align.CENTER);
            String ch = e.label.length() > 0 ? e.label.substring(0, 1).toUpperCase() : "?";
            cc.drawText(ch, px / 2f, px * 0.64f, tp);
        } else if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable ad = (AdaptiveIconDrawable) d;
            int q = px / 4;
            Rect big = new Rect(-q, -q, px + q, px + q);
            Drawable bg = ad.getBackground();
            Drawable fg = ad.getForeground();
            if (bg != null) {
                bg.setBounds(big);
                bg.draw(cc);
            }
            if (fg != null) {
                fg.setBounds(big);
                fg.draw(cc);
            }
        } else {
            int iw = d.getIntrinsicWidth();
            int ih = d.getIntrinsicHeight();
            boolean wide = iw > 0 && ih > 0 && Math.abs(iw / (float) ih - 1f) > 0.12f;
            if (wide) {
                // banner: centred on the plate with 16 px padding
                cc.drawColor(Ui.SURFACE1);
                float availW = px - 2 * pad16;
                float availH = px - 2 * pad16;
                float sc = Math.min(availW / iw, availH / ih);
                int dw = Math.round(iw * sc);
                int dh = Math.round(ih * sc);
                int l = (px - dw) / 2;
                int t = (px - dh) / 2;
                d.setBounds(l, t, l + dw, t + dh);
                d.draw(cc);
            } else {
                d.setBounds(0, 0, px, px);
                d.draw(cc);
                if (cornersTransparent(content)) {
                    // transparent icon: place it on a surface plate
                    content.eraseColor(Ui.SURFACE1);
                    int inset = Math.round(px * 0.14f);
                    d.setBounds(inset, inset, px - inset, px - inset);
                    d.draw(cc);
                }
            }
        }

        Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        mask.setColor(0xFFFFFFFF);
        oc.drawRoundRect(new RectF(0, 0, px, px), radius, radius, mask);
        Paint comp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        comp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        oc.drawBitmap(content, 0, 0, comp);
        content.recycle();
        return out;
    }

    private static boolean cornersTransparent(Bitmap b) {
        int w = b.getWidth();
        int h = b.getHeight();
        int[][] pts = {{2, 2}, {w - 3, 2}, {2, h - 3}, {w - 3, h - 3}};
        for (int[] p : pts) {
            int a = (b.getPixel(p[0], p[1]) >>> 24) & 0xFF;
            if (a < 240) return true;
        }
        return false;
    }
}
