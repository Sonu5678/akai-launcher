package dev.mantel.tv;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

/**
 * Reads the single most recent Android Watch Next entry for the Resume line.
 * Third-party launchers are normally not granted read access to this data; when the read is
 * refused the Resume line is simply absent and Home does not change (section 3, zone 4).
 */
final class ResumeSource {
    private ResumeSource() {}

    static final class Item {
        String title = "";
        String detail = "";
        String pkg = "";
        float progress = -1f;      // 0..1, or -1 when unknown
        Intent intent;
    }

    private static final Uri URI = Uri.parse("content://android.media.tv/watch_next_program");

    /** Runs on a background thread; returns null when nothing can be read. */
    static Item read(Context ctx) {
        Cursor c = null;
        try {
            ContentResolver cr = ctx.getContentResolver();
            String[] cols = {"title", "short_description", "intent_uri", "package_name",
                    "last_playback_position_millis", "duration_millis"};
            c = cr.query(URI, cols, null, null, "last_engagement_time_utc_millis DESC");
            if (c == null || !c.moveToFirst()) return null;
            Item it = new Item();
            String t = c.getString(0);
            if (t == null || t.trim().length() == 0) return null;
            it.title = t;
            String desc = c.getString(1);
            it.pkg = c.isNull(3) ? "" : c.getString(3);
            long pos = c.isNull(4) ? -1 : c.getLong(4);
            long dur = c.isNull(5) ? -1 : c.getLong(5);
            if (dur > 0 && pos >= 0) {
                it.progress = Math.max(0f, Math.min(1f, pos / (float) dur));
                long left = Math.max(0, (dur - pos) / 60000L);
                it.detail = left + " min left";
            } else if (desc != null) {
                it.detail = desc;
            }
            String iu = c.getString(2);
            if (iu != null && iu.length() > 0) {
                try {
                    it.intent = Intent.parseUri(iu, Intent.URI_INTENT_SCHEME);
                    it.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                } catch (Throwable e) {
                    it.intent = null;
                }
            }
            return it;
        } catch (Throwable e) {
            // SecurityException when the permission is not granted, or no such provider
            return null;
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable e) {
                    // ignore
                }
            }
        }
    }
}
