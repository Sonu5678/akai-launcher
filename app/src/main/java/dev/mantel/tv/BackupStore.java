package dev.mantel.tv;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Backup and restore (section 6): the shelf, pins, hidden apps and look settings in one small JSON
 * file, so a factory reset costs minutes. The file goes wherever the system file picker sends it
 * (a USB stick, ideally). If the TV has no file picker, Mantel falls back to its own folder.
 */
final class BackupStore {
    private BackupStore() {}

    static final String FILE_NAME = "mantel-backup.json";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int MAX_BYTES = 512 * 1024;

    // ---- through the system file picker ----

    static boolean writeTo(Context c, Uri uri) {
        OutputStream out = null;
        try {
            out = c.getContentResolver().openOutputStream(uri, "wt");
            if (out == null) return false;
            out.write(Prefs.exportJson().getBytes(UTF8));
            out.flush();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            close(out);
        }
    }

    static boolean readFrom(Context c, Uri uri) {
        InputStream in = null;
        try {
            in = c.getContentResolver().openInputStream(uri);
            return in != null && apply(in);
        } catch (Throwable t) {
            return false;
        } finally {
            close(in);
        }
    }

    // ---- fallback: Mantel's own folder ----

    static File fallbackFile(Context c) {
        File dir = c.getExternalFilesDir(null);
        if (dir == null) dir = c.getFilesDir();
        return new File(dir, FILE_NAME);
    }

    static boolean writeFile(File f) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            out.write(Prefs.exportJson().getBytes(UTF8));
            out.flush();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            close(out);
        }
    }

    static boolean readFile(File f) {
        if (f == null || !f.isFile()) return false;
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            return apply(in);
        } catch (Throwable t) {
            return false;
        } finally {
            close(in);
        }
    }

    /** A short, readable place for the user: the folder without the storage prefix. */
    static String describe(File f) {
        String p = f.getAbsolutePath();
        int i = p.indexOf("Android/data/");
        return i >= 0 ? p.substring(i) : p;
    }

    // ---- shared ----

    private static boolean apply(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) {
            buf.write(b, 0, n);
            if (buf.size() > MAX_BYTES) return false;   // not one of ours
        }
        return Prefs.importJson(new String(buf.toByteArray(), UTF8));
    }

    private static void close(java.io.Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException e) {
            // nothing to do
        }
    }
}
