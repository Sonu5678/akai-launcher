package com.mylauncher.akaitv;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

/** One launchable app. */
final class AppEntry {
    final String pkg;
    final String label;
    final ComponentName cn;
    final ActivityInfo ai;
    String shown;

    AppEntry(String pkg, String label, ActivityInfo ai) {
        this.pkg = pkg;
        this.label = label;
        this.shown = label;
        this.ai = ai;
        this.cn = new ComponentName(ai.packageName, ai.name);
    }

    /** TV banner (16:9) or null. */
    Drawable banner(PackageManager pm) {
        try {
            return ai.loadBanner(pm);
        } catch (Throwable t) {
            return null;
        }
    }

    Drawable icon(PackageManager pm) {
        try {
            return ai.loadIcon(pm);
        } catch (Throwable t) {
            return null;
        }
    }

    boolean matches(String q) {
        return pkg.toLowerCase().contains(q) || label.toLowerCase().contains(q);
    }
}
