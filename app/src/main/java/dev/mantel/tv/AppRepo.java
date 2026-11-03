package dev.mantel.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finds launchable apps and answers the Library's questions about them. */
final class AppRepo {
    private AppRepo() {}

    static final ArrayList<AppEntry> all = new ArrayList<AppEntry>();
    static final HashMap<String, AppEntry> byPkg = new HashMap<String, AppEntry>();
    static boolean loaded = false;

    private static final String VENDING = "com.android.vending";

    // Your seven most-used apps, in your order (section 4). Matching is by normalised label,
    // with a package hint where one is known. YouTube must match exactly so that YouTube Music
    // and YouTube Kids are never picked by mistake.
    private static final String[][] SEED_KEYS = {
            {"netflix"}, {"jiohotstar", "hotstar"}, {"browsehere"}, {"youtube"},
            {"jiotv"}, {"spotify"}, {"kartoons"}};
    private static final boolean[] SEED_EXACT = {false, false, false, true, false, false, false};
    private static final String[] SEED_PKG = {
            "com.netflix.ninja", "", "com.internet.tvbrowser", "com.google.android.youtube.tv",
            "", "", ""};

    static void refresh(final Context ctx, final Runnable done) {
        final Context app = ctx.getApplicationContext();
        Ui.IO.execute(new Runnable() {
            @Override public void run() {
                final ArrayList<AppEntry> list = scan(app);
                Ui.MAIN.post(new Runnable() {
                    @Override public void run() {
                        all.clear();
                        all.addAll(list);
                        byPkg.clear();
                        for (AppEntry e : list) byPkg.put(e.pkg, e);
                        loaded = true;
                        seedIfNeeded();
                        if (done != null) done.run();
                    }
                });
            }
        });
    }

    private static ArrayList<AppEntry> scan(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        HashMap<String, AppEntry> m = new HashMap<String, AppEntry>();
        String self = ctx.getPackageName();

        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> l1 = pm.queryIntentActivities(tv, 0);
        for (ResolveInfo ri : l1) {
            if (ri.activityInfo == null || self.equals(ri.activityInfo.packageName)) continue;
            if (m.containsKey(ri.activityInfo.packageName)) continue;
            m.put(ri.activityInfo.packageName, make(pm, ri, true));
        }
        // Sideloaded phone apps (for example a cartoon app with no TV entry) still deserve a tile.
        // Preinstalled phone-only apps are vendor clutter and stay out.
        Intent ph = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> l2 = pm.queryIntentActivities(ph, 0);
        for (ResolveInfo ri : l2) {
            if (ri.activityInfo == null || self.equals(ri.activityInfo.packageName)) continue;
            if (m.containsKey(ri.activityInfo.packageName)) continue;
            AppEntry e = make(pm, ri, false);
            if (e.userInstalled) m.put(e.pkg, e);
        }
        ArrayList<AppEntry> out = new ArrayList<AppEntry>(m.values());
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    private static AppEntry make(PackageManager pm, ResolveInfo ri, boolean leanback) {
        ActivityInfo ai = ri.activityInfo;
        ApplicationInfo a = ai.applicationInfo;
        AppEntry e = new AppEntry();
        e.pkg = ai.packageName;
        CharSequence lab = null;
        try {
            lab = ri.loadLabel(pm);
        } catch (Throwable t) {
            // fall through
        }
        e.label = lab == null ? e.pkg : lab.toString();
        e.norm = AppEntry.normalise(e.label);
        e.ai = ai;
        e.cn = new android.content.ComponentName(ai.packageName, ai.name);
        e.leanback = leanback;
        boolean sys = (a.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean upd = (a.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        e.userInstalled = !sys;
        e.system = sys && !upd;
        return e;
    }

    // ---- first-run shelf ----

    static void seedIfNeeded() {
        if (!Prefs.seeded() && !all.isEmpty()) seed();
    }

    /** Fills the seven slots in your order; a missing app leaves its slot empty. */
    static void seed() {
        String[] pins = new String[Prefs.SLOTS];
        String[] labels = new String[Prefs.SLOTS];
        HashSet<String> used = new HashSet<String>();
        for (int i = 0; i < Prefs.SLOTS; i++) {
            pins[i] = "";
            labels[i] = "";
            AppEntry hit = null;
            if (SEED_PKG[i].length() > 0) {
                AppEntry e = byPkg.get(SEED_PKG[i]);
                if (e != null && !used.contains(e.pkg)) hit = e;
            }
            for (int pass = 0; pass < 2 && hit == null; pass++) {
                for (AppEntry e : all) {
                    if (used.contains(e.pkg)) continue;
                    for (String k : SEED_KEYS[i]) {
                        boolean ok = pass == 0 ? e.norm.equals(k) : (!SEED_EXACT[i] && e.norm.contains(k));
                        if (ok) {
                            hit = e;
                            break;
                        }
                    }
                    if (hit != null) break;
                }
            }
            if (hit != null) {
                pins[i] = hit.pkg;
                labels[i] = hit.label;
                used.add(hit.pkg);
            }
        }
        Prefs.savePins(pins, labels);
        Prefs.setSeeded(true);
        Prefs.setRest(0);
    }

    // ---- Library queries ----

    static boolean listed(AppEntry e) {
        return Prefs.showSystem() || !e.system || VENDING.equals(e.pkg);
    }

    static boolean isPinned(String pkg) {
        for (String p : Prefs.pins()) if (p.equals(pkg)) return true;
        return false;
    }

    /** Unpinned, unhidden apps in A to Z order. */
    static List<AppEntry> library() {
        Set<String> hidden = Prefs.hidden();
        String[] pins = Prefs.pins();
        HashSet<String> pinned = new HashSet<String>();
        for (String p : pins) if (p.length() > 0) pinned.add(p);
        ArrayList<AppEntry> out = new ArrayList<AppEntry>();
        for (AppEntry e : all) {
            if (pinned.contains(e.pkg) || hidden.contains(e.pkg) || !listed(e)) continue;
            out.add(e);
        }
        return out;
    }

    /** The nine latest launched unpinned apps that are still in the Library. */
    static List<AppEntry> recentRow(List<AppEntry> lib) {
        HashSet<String> inLib = new HashSet<String>();
        for (AppEntry e : lib) inLib.add(e.pkg);
        ArrayList<AppEntry> out = new ArrayList<AppEntry>();
        for (String pkg : Prefs.recent()) {
            if (out.size() >= 9) break;
            if (inLib.contains(pkg)) {
                AppEntry e = byPkg.get(pkg);
                if (e != null) out.add(e);
            }
        }
        return out;
    }

    static List<AppEntry> hiddenApps() {
        Set<String> hidden = Prefs.hidden();
        ArrayList<AppEntry> out = new ArrayList<AppEntry>();
        for (AppEntry e : all) if (hidden.contains(e.pkg)) out.add(e);
        return out;
    }

    static int hiddenCount() {
        return hiddenApps().size();
    }

    /** Local, instant app search: prefix matches first, then anywhere in the name. */
    static List<AppEntry> search(String q) {
        String n = AppEntry.normalise(q);
        ArrayList<AppEntry> out = new ArrayList<AppEntry>();
        if (n.length() == 0) {
            for (AppEntry e : all) if (listed(e) || Prefs.hidden().contains(e.pkg)) out.add(e);
            return out;
        }
        ArrayList<AppEntry> rest = new ArrayList<AppEntry>();
        for (AppEntry e : all) {
            if (e.norm.startsWith(n)) out.add(e);
            else if (e.norm.contains(n)) rest.add(e);
        }
        out.addAll(rest);
        return out;
    }
}
