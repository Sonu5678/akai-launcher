package dev.mantel.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** All user state lives here: the shelf, hidden apps, look settings and remembered focus. */
final class Prefs {
    private Prefs() {}

    static final int SLOTS = 7;
    private static SharedPreferences sp;

    static void init(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("mantel", Context.MODE_PRIVATE);
    }

    // ---- shelf pins: seven slots, each "package" or "" ----

    static String[] pins() {
        String[] out = new String[SLOTS];
        for (int i = 0; i < SLOTS; i++) out[i] = "";
        try {
            JSONArray a = new JSONArray(sp.getString("pins", "[]"));
            for (int i = 0; i < SLOTS && i < a.length(); i++) out[i] = a.optString(i, "");
        } catch (JSONException e) {
            // keep empty
        }
        return out;
    }

    static String[] pinLabels() {
        String[] out = new String[SLOTS];
        for (int i = 0; i < SLOTS; i++) out[i] = "";
        try {
            JSONArray a = new JSONArray(sp.getString("pinLabels", "[]"));
            for (int i = 0; i < SLOTS && i < a.length(); i++) out[i] = a.optString(i, "");
        } catch (JSONException e) {
            // keep empty
        }
        return out;
    }

    static void savePins(String[] pins, String[] labels) {
        JSONArray a = new JSONArray();
        JSONArray l = new JSONArray();
        for (int i = 0; i < SLOTS; i++) {
            a.put(pins[i] == null ? "" : pins[i]);
            l.put(labels[i] == null ? "" : labels[i]);
        }
        sp.edit().putString("pins", a.toString()).putString("pinLabels", l.toString()).commit();
    }

    static boolean seeded() { return sp.getBoolean("seeded", false); }
    static void setSeeded(boolean v) { sp.edit().putBoolean("seeded", v).apply(); }

    // ---- hidden apps ----

    static Set<String> hidden() {
        return new HashSet<String>(sp.getStringSet("hidden", new HashSet<String>()));
    }

    static void setHidden(String pkg, boolean hide) {
        Set<String> h = hidden();
        if (hide) h.add(pkg); else h.remove(pkg);
        sp.edit().putStringSet("hidden", h).apply();
    }

    // ---- recent launches of unpinned apps (newest first) ----

    static List<String> recent() {
        ArrayList<String> out = new ArrayList<String>();
        try {
            JSONArray a = new JSONArray(sp.getString("recent", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        } catch (JSONException e) {
            // empty
        }
        return out;
    }

    static void pushRecent(String pkg) {
        List<String> r = recent();
        r.remove(pkg);
        r.add(0, pkg);
        JSONArray a = new JSONArray();
        for (int i = 0; i < r.size() && i < 24; i++) a.put(r.get(i));
        sp.edit().putString("recent", a.toString()).apply();
    }

    // ---- remembered focus ----

    static int rest() { return sp.getInt("rest", 0); }
    static void setRest(int i) { sp.edit().putInt("rest", i).commit(); }
    static int railFocus() { return sp.getInt("railFocus", 0); }
    static void setRailFocus(int i) { sp.edit().putInt("railFocus", i).apply(); }
    static String libraryFocus() { return sp.getString("libFocus", ""); }
    static void setLibraryFocus(String pkg) { sp.edit().putString("libFocus", pkg).apply(); }
    static int settingsGroup() { return sp.getInt("setGroup", 0); }
    static void setSettingsGroup(int i) { sp.edit().putInt("setGroup", i).apply(); }

    // ---- look ----

    static int accent() { return sp.getInt("accent", 0); }
    static void setAccent(int i) { sp.edit().putInt("accent", i).apply(); }
    static int dim() { return sp.getInt("dim", 1); }
    static void setDim(int i) { sp.edit().putInt("dim", i).apply(); }
    static String wallpaper() { return sp.getString("wall", "b1"); }
    static void setWallpaper(String id) { sp.edit().putString("wall", id).apply(); }
    static String usbUri() { return sp.getString("usbUri", ""); }
    static void setUsbUri(String u) { sp.edit().putString("usbUri", u).apply(); }

    // ---- toggles and values ----

    static boolean showResume() { return sp.getBoolean("showResume", true); }
    static void setShowResume(boolean v) { sp.edit().putBoolean("showResume", v).apply(); }
    static boolean showSystem() { return sp.getBoolean("showSystem", false); }
    static void setShowSystem(boolean v) { sp.edit().putBoolean("showSystem", v).apply(); }
    static int ambientMinutes() { return sp.getInt("ambientMin", 10); }
    static void setAmbientMinutes(int m) { sp.edit().putInt("ambientMin", m).apply(); }
    static boolean ambientClock() { return sp.getBoolean("ambientClock", true); }
    static void setAmbientClock(boolean v) { sp.edit().putBoolean("ambientClock", v).apply(); }
    static int coachSeen() { return sp.getInt("coach", 0); }
    static void setCoachSeen(int mask) { sp.edit().putInt("coach", mask).apply(); }

    // ---- backup / restore ----

    static String exportJson() {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                Object v = e.getValue();
                JSONObject cell = new JSONObject();
                if (v instanceof Boolean) {
                    cell.put("t", "b").put("v", ((Boolean) v).booleanValue());
                } else if (v instanceof Integer) {
                    cell.put("t", "i").put("v", ((Integer) v).intValue());
                } else if (v instanceof String) {
                    cell.put("t", "s").put("v", (String) v);
                } else if (v instanceof Set) {
                    JSONArray arr = new JSONArray();
                    for (Object x : (Set<?>) v) arr.put(String.valueOf(x));
                    cell.put("t", "set").put("v", arr);
                } else {
                    continue;
                }
                o.put(e.getKey(), cell);
            }
            JSONObject root = new JSONObject();
            root.put("app", "mantel");
            root.put("version", 1);
            root.put("prefs", o);
            return root.toString(2);
        } catch (JSONException e) {
            return "{}";
        }
    }

    /** Returns true when the file was a Mantel backup and was applied. */
    static boolean importJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (!"mantel".equals(root.optString("app"))) return false;
            JSONObject o = root.getJSONObject("prefs");
            SharedPreferences.Editor ed = sp.edit();
            ed.clear();
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject cell = o.getJSONObject(k);
                String t = cell.getString("t");
                if ("b".equals(t)) {
                    ed.putBoolean(k, cell.getBoolean("v"));
                } else if ("i".equals(t)) {
                    ed.putInt(k, cell.getInt("v"));
                } else if ("s".equals(t)) {
                    ed.putString(k, cell.getString("v"));
                } else if ("set".equals(t)) {
                    JSONArray arr = cell.getJSONArray("v");
                    Set<String> s = new HashSet<String>();
                    for (int i = 0; i < arr.length(); i++) s.add(arr.getString(i));
                    ed.putStringSet(k, s);
                }
            }
            return ed.commit();
        } catch (JSONException e) {
            return false;
        }
    }
}
