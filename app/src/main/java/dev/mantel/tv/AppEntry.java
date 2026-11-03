package dev.mantel.tv;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;

/** One launchable app. */
final class AppEntry {
    String pkg;
    String label;
    String norm;            // lower-case letters and digits only, for matching
    ComponentName cn;
    ActivityInfo ai;
    boolean leanback;       // has a TV (leanback) launch entry
    boolean system;         // preinstalled and never updated by the user
    boolean userInstalled;  // not a system app at all (can be uninstalled)

    Intent launchIntent() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
        i.setComponent(cn);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }

    static String normalise(String s) {
        StringBuilder b = new StringBuilder();
        if (s != null) {
            for (int i = 0; i < s.length(); i++) {
                char ch = Character.toLowerCase(s.charAt(i));
                if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) b.append(ch);
            }
        }
        return b.toString();
    }
}
