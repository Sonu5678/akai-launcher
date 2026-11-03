package dev.mantel.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.format.DateFormat;
import android.view.KeyEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Home (section 3): clock, ghost rail, optional Resume line, one shelf of seven pins and the
 * Library tile, a focus label and a bottom scrim. Tiles never move; only focus does.
 */
final class HomeScreen extends Screen {
    static final int ZONE_SHELF = 0, ZONE_RAIL = 1, ZONE_RESUME = 2;

    private int zone = ZONE_SHELF;
    private int shelfIdx = 0;
    private int railIdx = 0;

    private String[] pins = new String[Prefs.SLOTS];
    private String[] labels = new String[Prefs.SLOTS];

    private boolean editing = false;
    private int editIdx = 0;

    private final Tween[] scale = new Tween[8];
    private final Tween[] off = new Tween[8];
    private final Tween[] railW = new Tween[2];
    private final Tween nudgeTw = new Tween(0f);
    private final Tween pressTw = new Tween(1f);
    private final Tween hintA = new Tween(0f);

    private ResumeSource.Item resume;
    private long resumeToken = 0;
    private boolean defaultHome = true;

    private String hintShown = "";
    private int coachCur = -1;
    private boolean coachStarted = false;
    private static final String[] COACH = {
            "Up reaches Search and Settings",
            "Down opens every app",
            "Back always goes one step back"};

    private SimpleDateFormat timeFmt, apFmt, dateFmt;
    private boolean fmt24 = false;
    private LinearGradient scrim;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            invalidate();
        }
    };

    HomeScreen(MainActivity a) {
        super(a);
        for (int i = 0; i < 8; i++) {
            scale[i] = new Tween(1f);
            off[i] = new Tween(0f);
        }
        railW[0] = new Tween(48f);
        railW[1] = new Tween(48f);
        for (int i = 0; i < Prefs.SLOTS; i++) {
            pins[i] = "";
            labels[i] = "";
        }
    }

    // ------------------------------------------------------------------ state

    private void reloadPins() {
        pins = Prefs.pins();
        labels = Prefs.pinLabels();
        for (int i = 0; i < Prefs.SLOTS; i++) {
            AppEntry e = pins[i].length() > 0 ? AppRepo.byPkg.get(pins[i]) : null;
            if (e != null) labels[i] = e.label;
        }
    }

    private boolean pinsEmpty() {
        for (String p : pins) if (p.length() > 0) return false;
        return true;
    }

    private int restIndex() {
        int r = Prefs.rest();
        if (r >= 0 && r < Prefs.SLOTS && pins[r].length() > 0) return r;
        for (int i = 0; i < Prefs.SLOTS; i++) if (pins[i].length() > 0) return i;
        return 7;
    }

    boolean isEditing() {
        return editing;
    }

    private boolean resumeVisible() {
        return resume != null && Prefs.showResume();
    }

    @Override void onEnter() {
        reloadPins();
        focusRest();
        reloadResume();
        startCoach();
    }

    /** Refresh pins, the Home-app check and the Resume line (on resume and on app changes). */
    void reloadResume() {
        reloadPins();
        defaultHome = act.isDefaultHome();
        if (!Prefs.showResume()) {
            resume = null;
            return;
        }
        final long token = ++resumeToken;
        final android.content.Context ctx = getContext().getApplicationContext();
        Ui.IO.execute(new Runnable() {
            @Override public void run() {
                final ResumeSource.Item it = ResumeSource.read(ctx);
                Ui.MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (token != resumeToken) return;
                        resume = it;
                        if (resume == null && zone == ZONE_RESUME) setFocus(ZONE_SHELF, shelfIdx, railIdx);
                        invalidate();
                    }
                });
            }
        });
    }

    @Override void onAppsChanged() {
        boolean wasEmpty = pinsEmpty();
        reloadPins();
        if (wasEmpty && !pinsEmpty()) focusRest();
        if (zone == ZONE_SHELF && shelfIdx < 7 && pins[shelfIdx].length() == 0) {
            setFocus(ZONE_SHELF, restIndex(), railIdx);
        }
        invalidate();
    }

    @Override void onReveal() {
        reloadPins();
        defaultHome = act.isDefaultHome();
        if (zone == ZONE_SHELF && shelfIdx < 7 && pins[shelfIdx].length() == 0) {
            shelfIdx = restIndex();
        }
        setFocus(zone, shelfIdx, railIdx);
        invalidate();
    }

    /** Home inside Mantel, and returning from an app: focus the rest tile. */
    void focusRest() {
        reloadPins();
        editing = false;
        setFocus(ZONE_SHELF, restIndex(), Prefs.railFocus() == 1 ? 1 : 0);
        for (int i = 0; i < 8; i++) off[i].set(0f);
        invalidate();
    }

    void focusRail(int idx) {
        setFocus(ZONE_RAIL, shelfIdx, idx);
    }

    private void setFocus(int z, int tile, int rail) {
        zone = z;
        shelfIdx = Math.max(0, Math.min(7, tile));
        railIdx = Math.max(0, Math.min(1, rail));
        for (int i = 0; i < 8; i++) {
            float target = 1f;
            if (zone == ZONE_SHELF && i == shelfIdx) target = editing ? 1.14f : 1.10f;
            scale[i].go(target, Ui.dur(140));
        }
        for (int j = 0; j < 2; j++) {
            float target = (zone == ZONE_RAIL && railIdx == j) ? pillWidth(j) : 48f;
            railW[j].go(target, Ui.dur(140));
        }
        speak(focusName());
        invalidate();
    }

    private String railLabel(int j) {
        return j == 0 ? "Search" : "Settings";
    }

    private float pillWidth(int j) {
        return 24f + 32f + 16f + Ui.measure(railLabel(j), 28f, Ui.medium) + 24f;
    }

    private String tileLabel(int i) {
        if (i == 7) return "Library";
        AppEntry e = pins[i].length() > 0 ? AppRepo.byPkg.get(pins[i]) : null;
        if (e != null) return e.label;
        return labels[i];
    }

    private String focusName() {
        if (zone == ZONE_RAIL) return railLabel(railIdx);
        if (zone == ZONE_RESUME && resume != null) return "Resume " + resume.title;
        return tileLabel(shelfIdx);
    }

    // ------------------------------------------------------------------ keys

    private int nextShelf(int from, int dir) {
        int i = from + dir;
        while (i >= 0 && i <= 7) {
            if (i == 7 || pins[i].length() > 0) return i;
            i += dir;
        }
        return -1;
    }

    @Override void onDir(int key) {
        if (editing) {
            editMove(key);
            return;
        }
        if (zone == ZONE_SHELF) {
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int dir = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                int j = nextShelf(shelfIdx, dir);
                if (j >= 0) setFocus(ZONE_SHELF, j, railIdx);
                else nudge(nudgeTw, dir * 6f);
            } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
                if (shelfIdx <= 3 && resumeVisible()) setFocus(ZONE_RESUME, shelfIdx, railIdx);
                else setFocus(ZONE_RAIL, shelfIdx, Prefs.railFocus() == 1 ? 1 : 0);
            } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                act.openLibrary(false);
            }
        } else if (zone == ZONE_RAIL) {
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int dir = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                int j = railIdx + dir;
                if (j >= 0 && j <= 1) {
                    Prefs.setRailFocus(j);
                    setFocus(ZONE_RAIL, shelfIdx, j);
                } else {
                    nudge(nudgeTw, dir * 6f);
                }
            } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                setFocus(ZONE_SHELF, shelfIdx, railIdx);
            } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
                nudge(nudgeTw, 0f);
            }
        } else {
            if (key == KeyEvent.KEYCODE_DPAD_UP) setFocus(ZONE_RAIL, shelfIdx, Prefs.railFocus() == 1 ? 1 : 0);
            else if (key == KeyEvent.KEYCODE_DPAD_DOWN) setFocus(ZONE_SHELF, shelfIdx, railIdx);
        }
    }

    @Override void onOkDown() {
        if (zone == ZONE_SHELF && !editing) pressTw.go(0.96f, Ui.dur(100));
    }

    @Override void onOk() {
        if (editing) {
            endEdit();
            return;
        }
        if (zone == ZONE_SHELF) {
            pressTw.go(1f, Ui.dur(100));
            if (shelfIdx == 7) {
                act.openLibrary(false);
            } else if (pins[shelfIdx].length() > 0) {
                final int slot = shelfIdx;
                Ui.MAIN.postDelayed(new Runnable() {
                    @Override public void run() {
                        act.launchPin(slot);
                    }
                }, Ui.dur(100));
            }
        } else if (zone == ZONE_RAIL) {
            Prefs.setRailFocus(railIdx);
            if (railIdx == 0) act.openSearch();
            else act.openSettings();
        } else if (zone == ZONE_RESUME && resume != null) {
            if (resume.intent != null) {
                act.startIntent(resume.intent);
            } else {
                AppEntry e = AppRepo.byPkg.get(resume.pkg);
                if (e != null) act.launchEntry(e);
            }
        }
    }

    @Override void onLongOk() {
        pressTw.go(1f, Ui.dur(100));
        if (editing || zone != ZONE_SHELF || shelfIdx == 7 || pins[shelfIdx].length() == 0) return;
        final int slot = shelfIdx;
        final String pkg = pins[slot];
        float cx = 120f + slot * 216f + 84f;
        act.showMenu(new String[]{"Move", "Remove from shelf", "App info"}, cx, 672f, true,
                new MenuOverlay.Listener() {
                    @Override public void onChoice(int index) {
                        if (index == 0) beginEdit(slot);
                        else if (index == 1) removeFromShelf(slot);
                        else if (index == 2) act.openAppInfo(pkg);
                    }
                });
    }

    @Override void onBack() {
        if (editing) {
            endEdit();
            return;
        }
        int rest = restIndex();
        if (zone != ZONE_SHELF || shelfIdx != rest) {
            setFocus(ZONE_SHELF, rest, railIdx);
        }
        // On the rest tile Back does nothing, so it never leaves Mantel.
    }

    // ------------------------------------------------------------------ shelf editing

    void beginEdit(int slot) {
        reloadPins();
        if (slot < 0 || slot >= Prefs.SLOTS || pins[slot].length() == 0) slot = restIndex();
        if (slot > 6) return;
        editing = true;
        editIdx = slot;
        setFocus(ZONE_SHELF, slot, railIdx);
    }

    private void endEdit() {
        editing = false;
        Prefs.savePins(pins, labels);
        setFocus(ZONE_SHELF, shelfIdx, railIdx);
    }

    private void editMove(int key) {
        if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) return;
        int dir = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
        int tgt = editIdx + dir;
        if (tgt < 0 || tgt > 6) {
            nudge(nudgeTw, dir * 6f);
            return;
        }
        String p = pins[editIdx];
        pins[editIdx] = pins[tgt];
        pins[tgt] = p;
        String l = labels[editIdx];
        labels[editIdx] = labels[tgt];
        labels[tgt] = l;
        off[tgt].set((editIdx - tgt) * 216f);
        off[tgt].go(0f, Ui.dur(140));
        off[editIdx].set((tgt - editIdx) * 216f);
        off[editIdx].go(0f, Ui.dur(140));
        if (Prefs.rest() == editIdx) Prefs.setRest(tgt);
        else if (Prefs.rest() == tgt) Prefs.setRest(editIdx);
        editIdx = tgt;
        Prefs.savePins(pins, labels);
        setFocus(ZONE_SHELF, tgt, railIdx);
    }

    void removeFromShelf(int slot) {
        reloadPins();
        if (slot < 0 || slot >= Prefs.SLOTS) return;
        pins[slot] = "";
        labels[slot] = "";
        Prefs.savePins(pins, labels);
        if (Prefs.rest() == slot) Prefs.setRest(restIndex());
        if (zone == ZONE_SHELF && shelfIdx == slot) setFocus(ZONE_SHELF, restIndex(), railIdx);
        invalidate();
    }

    // ------------------------------------------------------------------ first-run hints

    private void startCoach() {
        if (coachStarted) return;
        coachStarted = true;
        Ui.MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                nextCoach();
            }
        }, 1500);
    }

    private void nextCoach() {
        int seen = Prefs.coachSeen();
        for (int k = 0; k < COACH.length; k++) {
            if ((seen & (1 << k)) == 0) {
                coachCur = k;
                Prefs.setCoachSeen(seen | (1 << k));
                invalidate();
                Ui.MAIN.postDelayed(new Runnable() {
                    @Override public void run() {
                        coachCur = -1;
                        invalidate();
                        Ui.MAIN.postDelayed(new Runnable() {
                            @Override public void run() {
                                nextCoach();
                            }
                        }, 1200);
                    }
                }, 6000);
                return;
            }
        }
    }

    private String currentLine() {
        if (editing) return "Left and Right to move, OK to drop, Back to finish";
        if (!defaultHome) return "Mantel isn’t your Home app yet. Settings, Home check shows how.";
        if (coachCur >= 0) return COACH[coachCur];
        return null;
    }

    // ------------------------------------------------------------------ drawing

    @Override void draw1(Canvas c, long now) {
        for (int i = 0; i < 8; i++) {
            tw(scale[i], now);
            tw(off[i], now);
        }
        tw(railW[0], now);
        tw(railW[1], now);
        tw(nudgeTw, now);
        tw(pressTw, now);

        drawScrim(c);
        drawClock(c);
        drawRail(c);
        if (resumeVisible()) drawResume(c);
        drawShelf(c);
        drawFocusLabel(c);
        drawHint(c, now);
    }

    private void drawScrim(Canvas c) {
        if (scrim == null) {
            scrim = new LinearGradient(0f, 540f, 0f, 1080f, 0x000A0A0E, 0xD90A0A0E, Shader.TileMode.CLAMP);
        }
        Ui.P.setStyle(Paint.Style.FILL);
        Ui.P.setShader(scrim);
        c.drawRect(-800f, 540f, 2720f, 1880f, Ui.P);
        Ui.P.setShader(null);
    }

    private void drawClock(Canvas c) {
        boolean is24 = DateFormat.is24HourFormat(getContext());
        if (timeFmt == null || is24 != fmt24) {
            fmt24 = is24;
            Locale loc = Locale.getDefault();
            timeFmt = new SimpleDateFormat(is24 ? "HH:mm" : "h:mm", loc);
            apFmt = new SimpleDateFormat("a", loc);
            dateFmt = new SimpleDateFormat("EEEE, d MMMM", loc);
        }
        Date d = new Date();
        String t = timeFmt.format(d);
        float tw = Ui.measure(t, 72f, Ui.light);
        Ui.text(c, t, 120f, 140f, 72f, Ui.light, Ui.TEXT, Paint.Align.LEFT);
        if (!is24) {
            Ui.text(c, apFmt.format(d).toUpperCase(Locale.ROOT), 120f + tw + 14f, 140f, 28f, Ui.medium,
                    Ui.TEXT, Paint.Align.LEFT);
        }
        Ui.text(c, dateFmt.format(d), 120f, 178f, 28f, Ui.regular, Ui.TEXT2, Paint.Align.LEFT);

        long ms = System.currentTimeMillis();
        removeCallbacks(clockTick);
        postDelayed(clockTick, 60000 - (ms % 60000) + 40);
    }

    private void drawRail(Canvas c) {
        float right = 1800f;
        float w1 = railW[1].cur;
        float w0 = railW[0].cur;
        float l1 = right - w1;
        float r0 = l1 - 32f;
        float l0 = r0 - w0;
        drawRailItem(c, 0, l0, r0);
        drawRailItem(c, 1, l1, right);
        if (act.offline) {
            // The Offline pill sits beside the glyphs so Search and Settings stay reachable.
            String label = "Offline";
            float w = 24f + 32f + 16f + Ui.measure(label, 28f, Ui.medium) + 24f;
            float pr = l0 - 32f;
            float pl = pr - w;
            Ui.rr(c, pl, 76f, pr, 140f, 32f, Ui.alpha(Ui.ERROR, 0.16f));
            Glyphs.draw(c, Glyphs.WIFI_OFF, pl + 40f, 108f, 32f, 2.5f, Ui.ERROR);
            Ui.text(c, label, pl + 72f, 118f, 28f, Ui.medium, Ui.ERROR, Paint.Align.LEFT);
        }
    }

    private void drawRailItem(Canvas c, int idx, float l, float r) {
        boolean focused = zone == ZONE_RAIL && railIdx == idx;
        float full = pillWidth(idx);
        float p = Math.max(0f, Math.min(1f, (r - l - 48f) / (full - 48f)));
        float nx = focused ? nudgeTw.cur : 0f;
        l += nx;
        r += nx;
        if (p > 0.02f) {
            Ui.rr(c, l, 76f, r, 140f, 32f, Ui.alpha(Ui.SURFACE2, p));
            if (focused) Ui.ring(c, l, 76f, r, 140f, 32f, 3f, Ui.alpha(Ui.TEXT, p));
        }
        float size = 48f - 16f * p;
        float gx = l + 24f + 16f * p;
        float gAlpha = 0.5f + 0.5f * p;
        Glyphs.draw(c, idx == 0 ? Glyphs.SEARCH : Glyphs.SETTINGS, gx, 108f, size,
                3f * size / 48f + 0.4f, Ui.alpha(Ui.TEXT, gAlpha));
        if (p > 0.6f) {
            Ui.text(c, railLabel(idx), l + 72f, 118f, 28f, Ui.medium,
                    Ui.alpha(Ui.TEXT, (p - 0.6f) / 0.4f), Paint.Align.LEFT);
        }
    }

    private void drawResume(Canvas c) {
        boolean focused = zone == ZONE_RESUME;
        Ui.rr(c, 120f, 440f, 440f, 620f, 20f, Ui.SURFACE1);
        AppEntry e = AppRepo.byPkg.get(resume.pkg);
        if (e != null) {
            Bitmap b = IconCache.get(act, e, 144, 30f);
            if (b != null) {
                Ui.R.set(208f, 458f, 352f, 602f);
                c.drawBitmap(b, null, Ui.R, Ui.BP);
            }
        }
        if (focused) Ui.ring(c, 114.5f, 434.5f, 445.5f, 625.5f, 25f, 3f, Ui.TEXT);
        String title = Ui.fit(resume.title, 1300f, 40f, Ui.medium);
        Ui.text(c, title, 472f, 498f, 40f, Ui.medium, Ui.TEXT, Paint.Align.LEFT);
        String detail = resume.detail;
        if (e != null && detail.length() > 0) detail = e.label + " · " + detail;
        else if (e != null) detail = e.label;
        Ui.text(c, Ui.fit(detail, 1300f, 28f, Ui.regular), 472f, 544f, 28f, Ui.regular, Ui.TEXT2, Paint.Align.LEFT);
        if (resume.progress >= 0f) {
            Ui.rr(c, 472f, 578f, 1112f, 584f, 3f, Ui.SURFACE2);
            Ui.rr(c, 472f, 578f, 472f + 640f * resume.progress, 584f, 3f, Ui.accent);
        }
    }

    private void drawShelf(Canvas c) {
        for (int i = 0; i < 8; i++) {
            if (zone == ZONE_SHELF && i == shelfIdx) continue;
            drawTile(c, i, false);
        }
        if (zone == ZONE_SHELF) drawTile(c, shelfIdx, true);
    }

    private void drawTile(Canvas c, int i, boolean focused) {
        if (i < 7 && pins[i].length() == 0) return;     // empty slots stay empty
        float x = 120f + i * 216f + off[i].cur;
        float cx = x + 84f + (focused ? nudgeTw.cur : 0f);
        float cy = 688f + 84f;
        float sc = scale[i].cur * (focused ? pressTw.cur : 1f);

        c.save();
        c.translate(cx, cy);
        c.scale(sc, sc);
        if (i == 7) {
            Ui.rr(c, -84f, -84f, 84f, 84f, 36f, Ui.SURFACE1);
            Glyphs.draw(c, Glyphs.GRID, 0f, 0f, 76f, 4f, focused ? Ui.TEXT : Ui.TEXT2);
        } else {
            AppEntry e = AppRepo.byPkg.get(pins[i]);
            Bitmap bm = e == null ? null : IconCache.get(act, e, 168, 36f);
            if (bm != null) {
                Ui.R.set(-84f, -84f, 84f, 84f);
                c.drawBitmap(bm, null, Ui.R, Ui.BP);
            } else {
                Ui.rr(c, -84f, -84f, 84f, 84f, 36f, Ui.SURFACE1);
                String lab = labels[i];
                if (lab.length() > 0) {
                    Ui.text(c, lab.substring(0, 1).toUpperCase(), 0f, 24f, 70f, Ui.medium,
                            e == null ? Ui.TEXT_OFF : Ui.TEXT2, Paint.Align.CENTER);
                }
            }
        }
        c.restore();

        if (focused) {
            float half = 84f * sc;
            float grow = 4f + 1.5f;
            Ui.ring(c, cx - half - grow, cy - half - grow, cx + half + grow, cy + half + grow,
                    36f * sc + grow, 3f, editing ? Ui.accent : Ui.TEXT);
        }
    }

    private void drawFocusLabel(Canvas c) {
        if (zone != ZONE_SHELF) return;
        String lab = tileLabel(shelfIdx);
        if (lab == null || lab.length() == 0) return;
        lab = Ui.fit(lab, 520f, 28f, Ui.medium);
        float w = Ui.measure(lab, 28f, Ui.medium);
        float cx = 120f + shelfIdx * 216f + 84f;
        cx = Math.max(120f + w / 2f, Math.min(1800f - w / 2f, cx));
        Ui.text(c, lab, cx, 910f, 28f, Ui.medium, Ui.TEXT, Paint.Align.CENTER);
    }

    private void drawHint(Canvas c, long now) {
        String line = currentLine();
        if (line != null) hintShown = line;
        float target = line != null ? 1f : 0f;
        if (hintA.to != target) hintA.go(target, Ui.dur(240));
        tw(hintA, now);
        float a = hintA.cur;
        if (a <= 0.01f || hintShown.length() == 0) return;
        float w = Ui.measure(hintShown, 28f, Ui.medium) + 64f;
        Ui.rr(c, 960f - w / 2f, 960f, 960f + w / 2f, 1026f, 33f, Ui.alpha(Ui.SURFACE1, 0.94f * a));
        Ui.text(c, hintShown, 960f, 1002f, 28f, Ui.medium,
                Ui.alpha(editing ? Ui.TEXT : Ui.TEXT2, a), Paint.Align.CENTER);
    }
}
