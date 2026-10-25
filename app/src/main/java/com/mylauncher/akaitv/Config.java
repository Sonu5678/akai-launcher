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
 * User settings. Read from config.json in the launcher's files folder, so it can be edited and
 * pushed with adb without rebuilding the app. A broken file never crashes the launcher: it falls
 * back to the built-in defaults and shows the error.
 */
final class Config {
    static final String FILE = "config.json";

    /** One dock tile: an app (found by name or package) or the Inputs tile. */
    static final class DockSpec {
        String type = "app";
        List<String> match = new ArrayList<>();
        String label = "";
        List<String> taglines = new ArrayList<>();
    }

    /** One hand-picked show for the My Shows row. */
    static final class ShowSpec {
        String title = "";
        String app = "";
        String uri = "";
        String poster = "";
    }

    final List<File> dirs = new ArrayList<>();
    final List<DockSpec> dock = new ArrayList<>();
    final List<ShowSpec> shows = new ArrayList<>();
    final List<List<String>> otherApps = new ArrayList<>();
    final List<String> hidden = new ArrayList<>();
    final Map<String, String> rename = new LinkedHashMap<>();

    int configVersion = 0;
    String userName = "";
    boolean showGreeting = false;
    boolean showDate = true;
    boolean clock24h = false;
    int columns = 5;
    int background = 0xFF000000;
    int glassColor = 0xFFFFFFFF;
    float glassOpacity = 0.28f;
    float focusScale = 1.08f;
    float corner = 22f;
    String wallpaper = "wallpaper.jpg";
    String font = "font.ttf";

    boolean showsEnabled = true;
    String showsTitle = "My Shows";
    boolean watchNext = true;
    int maxShows = 10;

    boolean otherEnabled = true;
    String otherTitle = "Other Apps";
    boolean moreApps = true;

    boolean playEnabled = true;
    String playTitle = "Play";

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
                    FileOutputStream o = new FileOutputStream(new File(cfg.dirs.get(0), FILE));
                    o.write(text.getBytes("UTF-8"));
                    o.close();
                } catch (Exception ignored) {
                    // read-only is fine, defaults still work
                }
            } else {
                text = new String(readAll(new FileInputStream(best)), "UTF-8");
                // a newer built-in config replaces an older saved one (the old one is kept as a backup)
                String assetText = readAsset(c);
                int av = versionOf(assetText), fv = versionOf(text);
                if (fv >= 0 && av > fv) {
                    try {
                        File dir = best.getParentFile();
                        FileOutputStream b = new FileOutputStream(new File(dir, "config.backup.json"));
                        b.write(text.getBytes("UTF-8"));
                        b.close();
                    } catch (Exception ignored) {
                        // backup is best effort
                    }
                    for (File d : cfg.dirs) {
                        try {
                            FileOutputStream o = new FileOutputStream(new File(d, FILE));
                            o.write(assetText.getBytes("UTF-8"));
                            o.close();
                        } catch (Exception ignored) {
                            // keep going
                        }
                    }
                    text = assetText;
                }
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

    /** The configVersion of a config text, or -1 if it cannot be read. */
    private static int versionOf(String text) {
        try {
            return new JSONObject(text).optInt("configVersion", 0);
        } catch (Exception e) {
            return -1;
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

    /** Changes whenever anything in the config folders changes (config, wallpaper, font, posters). */
    long stamp() {
        long t = 0;
        for (File d : dirs) {
            File[] list = d.listFiles();
            if (list == null) continue;
            for (File f : list) {
                if (f.isFile()) t += f.lastModified() + f.length();
            }
        }
        return t;
    }

    File find(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        // plain file names only, so a config can never point outside its own folder
        String n = new File(name.trim()).getName();
        for (File d : dirs) {
            File f = new File(d, n);
            if (f.isFile()) return f;
        }
        return null;
    }

    private void parse(String text) throws Exception {
        JSONObject o = new JSONObject(text);
        configVersion = o.optInt("configVersion", 0);
        userName = o.optString("name", "");
        showGreeting = o.optBoolean("showGreeting", false);
        showDate = o.optBoolean("showDate", true);
        clock24h = o.optBoolean("clock24h", false);
        columns = Math.max(3, Math.min(7, o.optInt("columns", 5)));
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
                Object e = d.get(i);
                DockSpec ds = new DockSpec();
                if (e instanceof JSONObject) {
                    JSONObject eo = (JSONObject) e;
                    ds.type = eo.optString("type", "app");
                    ds.match = strings(eo.opt("match"));
                    ds.label = eo.optString("label", "");
                    ds.taglines = strings(eo.opt("taglines"));
                } else {
                    ds.match = strings(e); // old format: ["netflix", "com.netflix.ninja"]
                }
                if (ds.type.equals("inputs") || !ds.match.isEmpty()) dock.add(ds);
            }
        }

        JSONObject ms = o.optJSONObject("myShows");
        shows.clear();
        if (ms != null) {
            showsEnabled = ms.optBoolean("enabled", true);
            showsTitle = ms.optString("title", "My Shows");
            watchNext = ms.optBoolean("watchNext", true);
            maxShows = Math.max(1, Math.min(20, ms.optInt("max", 10)));
            JSONArray items = ms.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject so = items.optJSONObject(i);
                    if (so == null) continue;
                    ShowSpec s = new ShowSpec();
                    s.title = so.optString("title", "");
                    s.app = so.optString("app", "");
                    s.uri = so.optString("uri", "");
                    s.poster = so.optString("poster", "");
                    if (!s.title.isEmpty()) shows.add(s);
                }
            }
        }

        JSONObject oa = o.optJSONObject("otherApps");
        otherApps.clear();
        if (oa != null) {
            otherEnabled = oa.optBoolean("enabled", true);
            otherTitle = oa.optString("title", "Other Apps");
            moreApps = oa.optBoolean("moreApps", true);
            JSONArray apps = oa.optJSONArray("apps");
            if (apps != null) {
                for (int i = 0; i < apps.length(); i++) {
                    List<String> m = strings(apps.get(i));
                    if (!m.isEmpty()) otherApps.add(m);
                }
            }
        }

        JSONObject pl = o.optJSONObject("play");
        if (pl != null) {
            playEnabled = pl.optBoolean("enabled", true);
            playTitle = pl.optString("title", "Play");
        }

        hidden.clear();
        hidden.addAll(strings(o.opt("hidden")));
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
    static List<String> strings(Object v) {
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
