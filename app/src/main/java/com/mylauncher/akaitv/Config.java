package com.mylauncher.akaitv;

import android.content.Context;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User settings. Read from config.json in the launcher's files folder
 * (/sdcard/Android/data/com.mylauncher.akaitv/files/config.json), so it can be
 * edited and pushed with adb without rebuilding the app.
 */
final class Config {
    static final String FILE = "config.json";

    final List<File> dirs = new ArrayList<>();
    final List<List<String>> dock = new ArrayList<>();
    final List<String> hidden = new ArrayList<>();
    final List<String> pinned = new ArrayList<>();
    final Map<String, String> rename = new LinkedHashMap<>();

    String userName = "";
    boolean showGreeting = true;
    boolean showDate = true;
    boolean clock24h = false;
    boolean showDockAppsInGrid = false;
    int columns = 4;
    int background = 0xFF000000;
    int glassColor = 0xFFFFFFFF;
    float glassOpacity = 0.28f;
    float focusScale = 1.08f;
    float corner = 22f;
    String wallpaper = "wallpaper.jpg";
    String font = "font.ttf";
    String error;

    static Config load(Context c) {
        Config cfg = new Config();
        cfg.initDirs(c);
        try {
            File best = cfg.bestFile();
            String text;
            if (best == null) {
                text = readAsset(c);
                try {
                    File out = new File(cfg.dirs.get(0), FILE);
                    FileOutputStream o = new FileOutputStream(out);
                    o.write(text.getBytes("UTF-8"));
                    o.close();
                } catch (Exception ignored) {
                    // read-only is fine, defaults still work
                }
            } else {
                text = new String(readAll(new FileInputStream(best)), "UTF-8");
            }
            cfg.parse(text);
            return cfg;
        } catch (Exception e) {
            Config fb = new Config();
            fb.initDirs(c);
            try {
                fb.parse(readAsset(c));
            } catch (Exception ignored) {
                // built-in defaults
            }
            fb.error = "config.json: " + e.getMessage();
            return fb;
        }
    }

    private void initDirs(Context c) {
        File ext = c.getExternalFilesDir(null);
        if (ext != null) {
            ext.mkdirs();
            dirs.add(ext);
        }
        dirs.add(c.getFilesDir());
    }

    private File bestFile() {
        File best = null;
        for (File d : dirs) {
            File f = new File(d, FILE);
            if (f.isFile() && (best == null || f.lastModified() > best.lastModified())) best = f;
        }
        return best;
    }

    /** Changes whenever config.json, the wallpaper or the font file changes. */
    long stamp() {
        long t = 0;
        File b = bestFile();
        if (b != null) t += b.lastModified();
        File w = find(wallpaper);
        if (w != null) t += w.lastModified();
        File f = find(font);
        if (f != null) t += f.lastModified();
        return t;
    }

    File find(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        for (File d : dirs) {
            File f = new File(d, name);
            if (f.isFile()) return f;
        }
        return null;
    }

    private void parse(String text) throws Exception {
        JSONObject o = new JSONObject(text);
        userName = o.optString("name", "");
        showGreeting = o.optBoolean("showGreeting", true);
        showDate = o.optBoolean("showDate", true);
        clock24h = o.optBoolean("clock24h", false);
        showDockAppsInGrid = o.optBoolean("showDockAppsInGrid", false);
        columns = Math.max(3, Math.min(6, o.optInt("columns", 4)));
        background = color(o, "background", 0xFF000000);
        glassColor = color(o, "glassColor", 0xFFFFFFFF);
        glassOpacity = (float) Math.max(0.05, Math.min(0.8, o.optDouble("glassOpacity", 0.28)));
        focusScale = (float) Math.max(1.0, Math.min(1.25, o.optDouble("focusScale", 1.08)));
        corner = (float) Math.max(0, Math.min(60, o.optDouble("cornerRadius", 22)));
        wallpaper = o.optString("wallpaper", "wallpaper.jpg");
        font = o.optString("font", "font.ttf");

        dock.clear();
        JSONArray d = o.optJSONArray("dock");
        if (d != null) {
            for (int i = 0; i < d.length(); i++) {
                List<String> m = strings(d.get(i));
                if (!m.isEmpty()) dock.add(m);
            }
        }
        hidden.clear();
        hidden.addAll(strings(o.opt("hidden")));
        pinned.clear();
        pinned.addAll(strings(o.opt("pinned")));
        rename.clear();
        JSONObject r = o.optJSONObject("rename");
        if (r != null) {
            Iterator<String> it = r.keys();
            while (it.hasNext()) {
                String k = it.next();
                rename.put(k, r.optString(k, k));
            }
        }
    }

    /** Accepts "x", ["x","y"] or {"match": "x" | ["x","y"]}. */
    private static List<String> strings(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof String) {
            out.add((String) v);
        } else if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            for (int i = 0; i < a.length(); i++) out.add(a.optString(i, ""));
        } else if (v instanceof JSONObject) {
            return strings(((JSONObject) v).opt("match"));
        }
        List<String> clean = new ArrayList<>();
        for (String s : out) if (s != null && !s.trim().isEmpty()) clean.add(s.trim());
        return clean;
    }

    private static int color(JSONObject o, String key, int def) {
        try {
            return Color.parseColor(o.optString(key, ""));
        } catch (Exception e) {
            return def;
        }
    }

    private static String readAsset(Context c) throws Exception {
        InputStream in = c.getAssets().open(FILE);
        return new String(readAll(in), "UTF-8");
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toByteArray();
    }
}
