package dev.mantel.tv;

import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.view.KeyEvent;

import java.util.List;

/**
 * Settings (section 8): a two-pane list with seven groups and no sub-pages.
 * Left pane x 120 to 760 holds the groups in 96 px rows; the right pane x 840 to 1800 shows the
 * focused group's items. Down the left pane previews each group; Right enters the pane; Left or
 * Back returns; Back from the left pane closes Settings and focuses the Settings glyph.
 */
final class SettingsScreen extends Screen {
    // Row kinds
    private static final int ACTION = 0, TOGGLE = 1, VALUE = 2, INFO = 3;

    // Row ids
    private static final int SHELF_EDIT = 1, SHELF_RESUME = 2, SHELF_RESET = 3,
            LIB_SYSTEM = 4, LIB_HIDDEN = 5,
            LOOK_WALL = 6, LOOK_ACCENT = 7, LOOK_DIM = 8,
            AMB_START = 9, AMB_CLOCK = 10,
            HOME_DEFAULT = 11, HOME_POWER = 12, HOME_OPEN = 13,
            BK_EXPORT = 14, BK_RESTORE = 15,
            ABOUT_VER = 16, ABOUT_TV = 17, ABOUT_LIC = 18;

    private static final String[] GROUPS = {
            "Shelf", "Library", "Look", "Ambient", "Home check", "Backup", "About"};

    private static final int[] AMBIENT_STEPS = {0, 5, 10, 30};

    private static final class Row {
        final int id, kind;
        final String label;

        Row(int id, int kind, String label) {
            this.id = id;
            this.kind = kind;
            this.label = label;
        }
    }

    private final Row[][] rows = {
            {new Row(SHELF_EDIT, ACTION, "Edit shelf"),
                    new Row(SHELF_RESUME, TOGGLE, "Show Resume line"),
                    new Row(SHELF_RESET, ACTION, "Reset to default order")},
            {new Row(LIB_SYSTEM, TOGGLE, "Show system apps"),
                    new Row(LIB_HIDDEN, ACTION, "Manage hidden apps")},
            {new Row(LOOK_WALL, ACTION, "Wallpaper"),
                    new Row(LOOK_ACCENT, ACTION, "Accent colour"),
                    new Row(LOOK_DIM, ACTION, "Dim level")},
            {new Row(AMB_START, VALUE, "Start after"),
                    new Row(AMB_CLOCK, TOGGLE, "Show clock")},
            {new Row(HOME_DEFAULT, INFO, "Default home app"),
                    new Row(HOME_POWER, INFO, "Start on power-on"),
                    new Row(HOME_OPEN, ACTION, "Open Android's home-app settings")},
            {new Row(BK_EXPORT, ACTION, "Export settings"),
                    new Row(BK_RESTORE, ACTION, "Restore settings")},
            {new Row(ABOUT_VER, INFO, "Mantel version"),
                    new Row(ABOUT_TV, INFO, "TV model"),
                    new Row(ABOUT_LIC, ACTION, "Open-source licences")},
    };

    private int group;
    private int item = 0;             // index into rows[group]; only used while inPane
    private boolean inPane = false;

    private final Tween leftY = new Tween(0f);     // y of the left focus (design px)
    private final Tween rightY = new Tween(0f);    // y of the right focus
    private final Tween nudgeY = new Tween(0f);
    private final Tween nudgeX = new Tween(0f);

    private boolean isDefaultHome = false;
    private String homeLabel = "";
    private String versionName = "";

    SettingsScreen(MainActivity a) {
        super(a);
        group = Math.max(0, Math.min(GROUPS.length - 1, Prefs.settingsGroup()));
        leftY.set(leftRowY(group));
        refreshHome();
        try {
            PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            versionName = pi.versionName == null ? "" : pi.versionName;
        } catch (Throwable t) {
            versionName = "";
        }
    }

    @Override int enterFrom() {
        return -1;      // the rail is above the shelf, so Settings drops in from the top, like Search
    }

    @Override void onEnter() {
        speak("Settings. " + GROUPS[group]);
    }

    @Override void onReveal() {
        refreshHome();
        invalidate();
    }

    @Override void onAppsChanged() {
        invalidate();
    }

    private void refreshHome() {
        isDefaultHome = act.isDefaultHome();
        homeLabel = act.defaultHomeLabel();
    }

    // ------------------------------------------------------------------ geometry

    private static float leftRowY(int g) {
        return 184f + g * 104f;
    }

    private static float rightRowY(int i) {
        return 184f + i * 104f;
    }

    private Row[] cur() {
        return rows[group];
    }

    private boolean focusable(Row r) {
        return r.kind != INFO;
    }

    private int firstFocusable() {
        Row[] r = cur();
        for (int i = 0; i < r.length; i++) if (focusable(r[i])) return i;
        return -1;
    }

    // ------------------------------------------------------------------ keys

    @Override void onDir(int key) {
        if (!inPane) {
            if (key == KeyEvent.KEYCODE_DPAD_UP) {
                if (group > 0) setGroup(group - 1);
                else nudge(nudgeY, -6f);
            } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (group < GROUPS.length - 1) setGroup(group + 1);
                else nudge(nudgeY, 6f);
            } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int f = firstFocusable();
                if (f >= 0) {
                    inPane = true;
                    item = f;
                    rightY.set(rightRowY(item));
                    speak(cur()[item].label);
                    invalidate();
                } else {
                    nudge(nudgeX, 6f);
                }
            } else if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                nudge(nudgeX, -6f);
            }
            return;
        }
        Row[] r = cur();
        if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
            inPane = false;
            speak(GROUPS[group]);
            invalidate();
        } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
            int n = item - 1;
            while (n >= 0 && !focusable(r[n])) n--;
            if (n >= 0) moveItem(n);
            else nudge(nudgeY, -6f);
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            int n = item + 1;
            while (n < r.length && !focusable(r[n])) n++;
            if (n < r.length) moveItem(n);
            else nudge(nudgeY, 6f);
        } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            nudge(nudgeX, 6f);
        }
        invalidate();
    }

    private void setGroup(int g) {
        group = g;
        Prefs.setSettingsGroup(g);
        leftY.go(leftRowY(g), Ui.dur(140));
        if (g == 4) refreshHome();
        speak(GROUPS[g]);
        invalidate();
    }

    private void moveItem(int n) {
        item = n;
        rightY.go(rightRowY(n), Ui.dur(140));
        speak(cur()[n].label);
        invalidate();
    }

    @Override void onBack() {
        if (inPane) {
            inPane = false;
            speak(GROUPS[group]);
            invalidate();
            return;
        }
        act.home.focusRail(1);
        act.pop(this);
    }

    @Override void onOk() {
        if (!inPane) {
            onDir(KeyEvent.KEYCODE_DPAD_RIGHT);
            return;
        }
        Row r = cur()[item];
        switch (r.id) {
            case SHELF_EDIT:
                act.startEditShelf(-1);
                break;
            case SHELF_RESUME:
                Prefs.setShowResume(!Prefs.showResume());
                act.home.reloadResume();
                break;
            case SHELF_RESET:
                act.showDialog("Reset the shelf?", "Your apps go back to the original order. Nothing is uninstalled.",
                        new String[]{"Cancel", "Reset"}, 0, new DialogOverlay.Listener() {
                            @Override public void onChoice(int index) {
                                if (index == 1) {
                                    AppRepo.seed();
                                    act.home.onAppsChanged();
                                    act.home.focusRest();
                                }
                            }
                        });
                break;
            case LIB_SYSTEM:
                Prefs.setShowSystem(!Prefs.showSystem());
                break;
            case LIB_HIDDEN:
                if (AppRepo.hiddenCount() == 0) {
                    act.showInfo("Nothing is hidden", "Hold OK on an app in the Library to hide it.");
                } else {
                    act.openLibrary(true);
                }
                break;
            case LOOK_WALL:
                act.openLook(0);
                break;
            case LOOK_ACCENT:
                act.openLook(1);
                break;
            case LOOK_DIM:
                act.openLook(2);
                break;
            case AMB_START:
                cycleAmbient();
                break;
            case AMB_CLOCK:
                Prefs.setAmbientClock(!Prefs.ambientClock());
                break;
            case HOME_OPEN:
                act.openHomeSettings();
                break;
            case BK_EXPORT:
                act.startBackupExport();
                break;
            case BK_RESTORE:
                act.showDialog("Restore settings?",
                        "This replaces your shelf, hidden apps and look with the ones in the backup file.",
                        new String[]{"Cancel", "Choose file"}, 0, new DialogOverlay.Listener() {
                            @Override public void onChoice(int index) {
                                if (index == 1) act.startBackupImport();
                            }
                        });
                break;
            case ABOUT_LIC:
                act.showInfo("Open-source licences",
                        "Inter typeface, SIL Open Font License 1.1. Mantel uses no other third-party code.");
                break;
            default:
                break;
        }
        invalidate();
    }

    private void cycleAmbient() {
        int m = Prefs.ambientMinutes();
        int idx = 0;
        for (int i = 0; i < AMBIENT_STEPS.length; i++) if (AMBIENT_STEPS[i] == m) idx = i;
        Prefs.setAmbientMinutes(AMBIENT_STEPS[(idx + 1) % AMBIENT_STEPS.length]);
        act.resetIdle();
    }

    // ------------------------------------------------------------------ values

    private boolean on(int id) {
        switch (id) {
            case SHELF_RESUME:
                return Prefs.showResume();
            case LIB_SYSTEM:
                return Prefs.showSystem();
            case AMB_CLOCK:
                return Prefs.ambientClock();
            default:
                return false;
        }
    }

    private String value(int id) {
        switch (id) {
            case LOOK_WALL:
                return LookScreen.wallpaperName(Prefs.wallpaper());
            case LOOK_ACCENT:
                return Ui.ACCENT_NAMES[Math.max(0, Math.min(3, Prefs.accent()))];
            case LOOK_DIM:
                return Ui.DIM_NAMES[Math.max(0, Math.min(3, Prefs.dim()))];
            case AMB_START: {
                int m = Prefs.ambientMinutes();
                return m <= 0 ? "Off" : m + " minutes";
            }
            case HOME_DEFAULT:
                return isDefaultHome ? "Mantel" : homeLabel;
            case HOME_POWER:
                return "Not readable here";
            case ABOUT_VER:
                return versionName;
            case ABOUT_TV: {
                String mfr = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
                String mdl = Build.MODEL == null ? "" : Build.MODEL;
                if (mdl.toLowerCase().startsWith(mfr.toLowerCase())) return mdl;
                return (mfr + " " + mdl).trim();
            }
            default:
                return "";
        }
    }

    private String caption() {
        Row[] r = cur();
        if (group == 4) {
            if (isDefaultHome) {
                return "Mantel is your Home app. At power-on Android opens the Home app, unless the TV's own "
                        + "power-on source setting says otherwise.";
            }
            return "The Home key opens " + homeLabel + ". Use the blue key to open Mantel by hand, then open "
                    + "Android's home-app settings below to switch.";
        }
        if (inPane && r[item].id == AMB_START) return "OK changes the time.";
        if (group == 3) return "Ambient mode starts only while Mantel is on screen. Any key wakes it.";
        if (group == 2) return "Pick a wallpaper, an accent colour and how dark the image sits behind the shelf.";
        if (group == 1) return "Hidden apps stay installed. They only leave the Library grid.";
        if (group == 0) return "You can also hold OK on a shelf tile to move or remove it.";
        if (group == 5) return "A backup keeps your shelf, hidden apps and look in one small file.";
        return "";
    }

    // ------------------------------------------------------------------ drawing

    @Override void draw1(Canvas c, long now) {
        tw(leftY, now);
        tw(rightY, now);
        tw(nudgeY, now);
        tw(nudgeX, now);

        Ui.fillAll(c, Ui.SHEET);
        Ui.text(c, "Settings", 120f, 126f, 56f, Ui.medium, Ui.TEXT, Paint.Align.LEFT);

        // ---- left pane: groups ----
        float lx = 120f + (inPane ? 0f : nudgeX.cur);
        float ly = leftY.cur + (inPane ? 0f : nudgeY.cur);
        Ui.rr(c, lx, ly, 760f + (inPane ? 0f : nudgeX.cur), ly + 96f, 20f,
                inPane ? Ui.SURFACE1 : Ui.SURFACE2);
        if (!inPane) Ui.ring(c, lx, ly, 760f + nudgeX.cur, ly + 96f, 20f, 3f, Ui.TEXT);
        for (int g = 0; g < GROUPS.length; g++) {
            boolean sel = g == group;
            float y = leftRowY(g);
            Ui.text(c, GROUPS[g], 120f + 40f, y + 62f, 32f, sel ? Ui.medium : Ui.regular,
                    sel ? Ui.TEXT : Ui.TEXT2, Paint.Align.LEFT);
        }

        // ---- right pane: items ----
        Row[] r = cur();
        for (int i = 0; i < r.length; i++) {
            float y = rightRowY(i);
            Ui.rr(c, 840f, y, 1800f, y + 96f, 20f, Ui.SURFACE1);
        }
        if (inPane) {
            // The focus plate travels between rows (140 ms) and sits under the row text.
            float ry = rightY.cur + nudgeY.cur;
            Ui.rr(c, 840f + nudgeX.cur, ry, 1800f + nudgeX.cur, ry + 96f, 20f, Ui.SURFACE2);
            Ui.ring(c, 840f + nudgeX.cur, ry, 1800f + nudgeX.cur, ry + 96f, 20f, 3f, Ui.TEXT);
        }
        for (int i = 0; i < r.length; i++) {
            drawRow(c, r[i], i, inPane && i == item);
        }

        // ---- caption ----
        String cap = caption();
        if (cap.length() > 0) {
            List<String> lines = Ui.wrap(cap, 960f, 26f, Ui.regular, 3);
            float y = 184f + r.length * 104f + 24f;
            for (String s : lines) {
                Ui.text(c, s, 840f, y + 26f, 26f, Ui.regular, Ui.TEXT2, Paint.Align.LEFT);
                y += 34f;
            }
        }
    }

    private void drawRow(Canvas c, Row r, int i, boolean focused) {
        float y = rightRowY(i);
        boolean info = r.kind == INFO;
        int labelColor = info ? Ui.TEXT2 : (focused ? Ui.TEXT : Ui.TEXT2);
        Ui.text(c, r.label, 840f + 40f, y + 62f, 32f, focused ? Ui.medium : Ui.regular, labelColor,
                Paint.Align.LEFT);

        float right = 1800f - 40f;
        if (r.kind == TOGGLE) {
            boolean v = on(r.id);
            float tx = right - 88f;
            Ui.rr(c, tx, y + 24f, tx + 88f, y + 72f, 24f, v ? Ui.accent : Ui.SURFACE2);
            if (!v) Ui.ring(c, tx, y + 24f, tx + 88f, y + 72f, 24f, 2f, Ui.TEXT_OFF);
            float kx = v ? tx + 88f - 24f : tx + 24f;
            Ui.circle(c, kx, y + 48f, 16f, v ? Ui.BASE : Ui.TEXT2);
            if (v) Glyphs.draw(c, Glyphs.CHECK, kx, y + 48f, 20f, 2.6f, Ui.accent);
            Ui.text(c, v ? "On" : "Off", tx - 24f, y + 60f, 28f, Ui.medium, v ? Ui.TEXT : Ui.TEXT2,
                    Paint.Align.RIGHT);
            return;
        }
        String shown = Ui.fit(value(r.id), 520f, 28f, Ui.medium);
        if (r.id == HOME_DEFAULT) {
            // Meaning is never carried by colour alone: a glyph and the words both say it.
            Glyphs.draw(c, isDefaultHome ? Glyphs.CHECK : Glyphs.WARNING,
                    right - Ui.measure(shown, 28f, Ui.medium) - 36f, y + 48f, 32f, 2.8f,
                    isDefaultHome ? Ui.SUCCESS : Ui.ERROR);
            Ui.text(c, shown, right, y + 60f, 28f, Ui.medium,
                    isDefaultHome ? Ui.TEXT : Ui.ERROR, Paint.Align.RIGHT);
        } else if (shown.length() > 0) {
            Ui.text(c, shown, right, y + 60f, 28f, Ui.medium,
                    focused ? Ui.TEXT : Ui.TEXT2, Paint.Align.RIGHT);
        }
    }
}
