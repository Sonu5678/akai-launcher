package com.mylauncher.akaitv;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Reads the "continue watching" list that streaming apps publish to Android TV (the Watch Next
 * channel). Whether this works depends on the TV allowing the launcher to read it and on each
 * app publishing to it, so every failure here is silent and the launcher simply shows the shows
 * you picked by hand instead.
 */
final class WatchNext {
    private WatchNext() {
    }

    static final class Program {
        String pkg = "";
        String title = "";
        String intentUri = "";
        String posterUri = "";
        long engaged;
    }

    static List<Program> read(Context c) {
        List<Program> out = new ArrayList<>();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, null,
                    null, null, null);
            if (cur == null) return out;
            int iPkg = cur.getColumnIndex("package_name");
            int iTitle = cur.getColumnIndex("title");
            int iIntent = cur.getColumnIndex("intent_uri");
            int iPoster = cur.getColumnIndex("poster_art_uri");
            int iThumb = cur.getColumnIndex("thumbnail_uri");
            int iEng = cur.getColumnIndex("last_engagement_time_utc_millis");
            while (cur.moveToNext() && out.size() < 80) {
                Program p = new Program();
                if (iPkg >= 0 && !cur.isNull(iPkg)) p.pkg = cur.getString(iPkg);
                if (iTitle >= 0 && !cur.isNull(iTitle)) p.title = cur.getString(iTitle);
                if (iIntent >= 0 && !cur.isNull(iIntent)) p.intentUri = cur.getString(iIntent);
                if (iPoster >= 0 && !cur.isNull(iPoster)) p.posterUri = cur.getString(iPoster);
                if ((p.posterUri == null || p.posterUri.isEmpty()) && iThumb >= 0
                        && !cur.isNull(iThumb)) {
                    p.posterUri = cur.getString(iThumb);
                }
                if (iEng >= 0 && !cur.isNull(iEng)) p.engaged = cur.getLong(iEng);
                if (p.title != null && !p.title.isEmpty() && p.pkg != null && !p.pkg.isEmpty()) {
                    out.add(p);
                }
            }
        } catch (Throwable t) {
            // no permission, no provider, or an unsupported TV: nothing to show
            out.clear();
        } finally {
            if (cur != null) {
                try {
                    cur.close();
                } catch (Throwable ignored) {
                    // nothing to do
                }
            }
        }
        Collections.sort(out, new Comparator<Program>() {
            @Override
            public int compare(Program a, Program b) {
                return Long.compare(b.engaged, a.engaged);
            }
        });
        return out;
    }

    /** Loads a poster from an https address, a content:// address, or a file in the config folder. */
    static Bitmap loadPoster(Context c, Config cfg, String ref, int reqW, int reqH) {
        if (ref == null || ref.isEmpty()) return null;
        try {
            byte[] data;
            if (ref.startsWith("https://")) {
                HttpURLConnection con = (HttpURLConnection) new URL(ref).openConnection();
                con.setConnectTimeout(6000);
                con.setReadTimeout(8000);
                con.setInstanceFollowRedirects(true);
                InputStream in = con.getInputStream();
                data = readCapped(in);
                con.disconnect();
            } else if (ref.startsWith("content://") || ref.startsWith("android.resource://")) {
                InputStream in = c.getContentResolver().openInputStream(Uri.parse(ref));
                if (in == null) return null;
                data = readCapped(in);
            } else if (ref.contains("://")) {
                return null; // http:// and anything else is refused on purpose
            } else {
                File f = cfg.find(ref);
                if (f == null) return null;
                return decodeFile(f, reqW, reqH);
            }
            return decode(data, reqW, reqH);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap decodeFile(File f, int reqW, int reqH) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        o.inSampleSize = sample(o.outWidth, o.outHeight, reqW, reqH);
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    private static Bitmap decode(byte[] data, int reqW, int reqH) {
        if (data == null || data.length == 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        o.inSampleSize = sample(o.outWidth, o.outHeight, reqW, reqH);
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    private static int sample(int w, int h, int reqW, int reqH) {
        int s = 1;
        while (w / (s * 2) >= reqW && h / (s * 2) >= reqH) s *= 2;
        return s;
    }

    private static byte[] readCapped(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        int total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > 4 * 1024 * 1024) break; // posters are small; never read more than 4 MB
            bo.write(buf, 0, n);
        }
        in.close();
        return bo.toByteArray();
    }
}
