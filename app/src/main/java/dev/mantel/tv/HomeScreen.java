package dev.mantel.tv;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.format.DateFormat;
import android.view.KeyEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Home, redesigned: a row of slabs. The focused app opens wide over its own living scene and the
 * others fold into spines. A glass dock below holds Search, Library, Deck and Settings. Slabs never
 * reorder on their own; only focus moves.
 */
final class HomeScreen extends Screen {
    static final int ZONE_SHELF = 0, ZONE_DOCK = 1;
    private static final int DOCK_SEARCH = 0, DOCK_LIBRARY = 1, DOCK_DECK = 2, DOCK_SETTINGS = 3;
    private static final String[] DOCK_LABELS = {"Search", "Library", "Deck", "Settings"};
    private static final int[] DOCK_GLYPHS = {Glyphs.SEARCH, Glyphs.GRID, Glyphs.DECK, Glyphs.SETTINGS};

    private static final float ROW_LEFT = 96f, ROW_TOP = 210f, SLAB_H = 580f, SPINE = 128f, GAP = 14f;
    private static final float RADIUS = 40f;

    private int zone = ZONE_SHELF;
    private int shelfIdx = 0;
    private int dockIdx = DOCK_LIBRARY;

    private String[] pins = new String[Prefs.SLOTS];
    private String[] labels = new String[Prefs.SLOTS];

    private boolean editing = false;
    private int editIdx = 0;

    private final Tween[] wTw = new Tween[Prefs.SLOTS];
    private final Tween nudgeTw = new Tween(0f);
    private final Tween pressTw = new Tween(1f);
    private final Tween hintA = new Tween(0f);
    private final Tween launchTw = new Tween(0f);

    private boolean defaultHome = true;
    private String hintShown = "";

    private SimpleDateFormat timeFmt, apFmt;
    private boolean fmt24 = false;
    private RadialGradient vignette;
    private final Path clip = new Path();
    private final RectF rf = new RectF();

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            invalidate();
        }
    };

    HomeScreen(MainActivity a) {
        super(a);
        for (int i = 0; i < Prefs.SLOTS; i++) {
            wTw[i] = new Tween(SPINE);
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

    /** Slots that hold an app, in shelf order. */
    private ArrayList<Integer> visible() {
        ArrayList<Integer> v = new ArrayList<Integer>();
        for (int i = 0; i < Prefs.SLOTS; i++) if (pins[i].length() > 0) v.add(i);
        return v;
    }

    private int restIndex() {
        int r = Prefs.rest();
        if (r >= 0 && r < Prefs.SLOTS && pins[r].length() > 0) return r;
        for (int i = 0; i < Prefs.SLOTS; i++) if (pins[i].length() > 0) return i;
        return -1;
    }

    private AppEntry entryAt(int slot) {
        if (slot < 0 || slot >= Prefs.SLOTS || pins[slot].length() == 0) return null;
        return AppRepo.byPkg.get(pins[slot]);
    }

    private String slotLabel(int slot) {
        AppEntry e = entryAt(slot);
        if (e != null) return e.label;
        if (slot >= 0 && slot < Prefs.SLOTS) return labels[slot];
        return "";
    }

    boolean isEditing() {
        return editing;
    }

    @Override void onEnter() {
        reloadPins();
        focusRest();
    }

    /** Refresh pins and the Home-app check (on resume and on app changes). */
    void reloadResume() {
        reloadPins();
        defaultHome = act.isDefaultHome();
        syncWidths(false);
        syncScene();
        invalidate();
    }

    @Override void onAppsChanged() {
        boolean wasEmpty = pinsEmpty();
        reloadPins();
        if (wasEmpty && !pinsEmpty()) focusRest();
        if (zone == ZONE_SHELF && pins[Math.max(0, Math.min(Prefs.SLOTS - 1, shelfIdx))].length() == 0) {
            int r = restIndex();
            if (r >= 0) setFocus(ZONE_SHELF, r, dockIdx);
        }
        syncWidths(false);
        syncScene();
        invalidate();
    }

    @Override void onReveal() {
        reloadPins();
        defaultHome = act.isDefaultHome();
        syncWidths(false);
        syncScene();
        invalidate();
    }

    /** Home inside Mantel, and returning from an app: focus the rest slab. */
    void focusRest() {
        reloadPins();
        editing = false;
        launchTw.set(0f);
        int r = restIndex();
        if (r < 0) {
            zone = ZONE_DOCK;
            dockIdx = DOCK_LIBRARY;
            shelfIdx = 0;
        } else {
            zone = ZONE_SHELF;
            shelfIdx = r;
        }
        syncWidths(true);
        syncScene();
        invalidate();
    }

    /** Called when Search (0) or Settings (1) closes: the dock keeps its place. */
    void focusRail(int idx) {
        zone = ZONE_DOCK;
        dockIdx = idx == 0 ? DOCK_SEARCH : DOCK_SETTINGS;
        invalidate();
    }

    private void setFocus(int z, int slot, int dock) {
        zone = z;
        shelfIdx = Math.max(0, Math.min(Prefs.SLOTS - 1, slot));
        dockIdx = Math.max(0, Math.min(3, dock));
        syncWidths(false);
        syncScene();
        speak(zone == ZONE_DOCK ? DOCK_LABELS[dockIdx] : slotLabel(shelfIdx));
        invalidate();
    }

    private float expandedWidth() {
        int n = Math.max(1, visible().size());
        return Math.min(1100f, 1728f - (n - 1) * (SPINE + GAP));
    }

    private void syncWidths(boolean instant) {
        float ex = expandedWidth();
        for (int i = 0; i < Prefs.SLOTS; i++) {
            float target = i == shelfIdx ? ex : SPINE;
            if (instant) wTw[i].set(target);
            else if (wTw[i].to != target || wTw[i].active) wTw[i].go(target, Ui.dur(440));
        }
    }

    private void syncScene() {
        if (act.wall == null) return;
        int s = zone == ZONE_SHELF || entryAt(shelfIdx) != null ? shelfIdx : -1;
        act.wall.setScene(entryAt(s));
    }

    // ------------------------------------------------------------------ keys

    private int neighbour(int slot, int dir) {
        ArrayList<Integer> v = visible();
        int p = v.indexOf(slot);
        if (p < 0) return -1;
        int q = p + dir;
        if (q < 0 || q >= v.size()) return -1;
        return v.get(q);
    }

    @Override void onDir(int key) {
        if (editing) {
            editMove(key);
            return;
        }
        if (zone == ZONE_SHELF) {
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int dir = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                int j = neighbour(shelfIdx, dir);
                if (j >= 0) setFocus(ZONE_SHELF, j, dockIdx);
                else nudge(nudgeTw, dir * 6f);
            } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                setFocus(ZONE_DOCK, shelfIdx, dockIdx);
            } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
                nudge(nudgeTw, 0f);
            }
        } else {
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int dir = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                int j = dockIdx + dir;
                if (j >= 0 && j <= 3) setFocus(ZONE_DOCK, shelfIdx, j);
                else nudge(nudgeTw, dir * 6f);
            } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
                if (restIndex() >= 0) setFocus(ZONE_SHELF, shelfIdx, dockIdx);
            }
        }
    }

    @Override void onOkDown() {
        if (zone == ZONE_SHELF && !editing) pressTw.go(0.97f, Ui.dur(100));
    }

    @Override void onOk() {
        if (editing) {
            endEdit();
            return;
        }
        if (zone == ZONE_SHELF) {
            pressTw.go(1f, Ui.dur(100));
            if (pins[shelfIdx].length() > 0) {
                final int slot = shelfIdx;
                launchTw.go(1f, Ui.dur(360));
                Ui.MAIN.postDelayed(new Runnable() {
                    @Override public void run() {
                        act.launchPin(slot);
                    }
                }, Ui.dur(300));
            }
        } else {
            Prefs.setRailFocus(dockIdx == DOCK_SETTINGS ? 1 : 0);
            if (dockIdx == DOCK_SEARCH) act.openSearch();
            else if (dockIdx == DOCK_LIBRARY) act.openLibrary(false);
            else if (dockIdx == DOCK_DECK) act.openDeck();
            else act.openSettings();
        }
    }

    @Override void onLongOk() {
        pressTw.go(1f, Ui.dur(100));
        if (editing || zone != ZONE_SHELF || pins[shelfIdx].length() == 0) return;
        final int slot = shelfIdx;
        final String pkg = pins[slot];
        float cx = ROW_LEFT + slotLeft(slot) + wTw[slot].cur / 2f;
        act.showMenu(new String[]{"Move", "Remove from shelf", "App info"}, cx, ROW_TOP + SLAB_H - 20f, true,
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
        if (zone == ZONE_DOCK && restIndex() >= 0) {
            setFocus(ZONE_SHELF, shelfIdx, dockIdx);
            return;
        }
        int rest = restIndex();
        if (rest >= 0 && shelfIdx != rest) setFocus(ZONE_SHELF, rest, dockIdx);
        // On the rest slab Back does nothing, so it never leaves Mantel.
    }

    // ------------------------------------------------------------------ shelf editing

    void beginEdit(int slot) {
        reloadPins();
        if (slot < 0 || slot >= Prefs.SLOTS || pins[slot].length() == 0) slot = restIndex();
        if (slot < 0) return;
        editing = true;
        editIdx = slot;
        setFocus(ZONE_SHELF, slot, dockIdx);
    }

    private void endEdit() {
        editing = false;
        Prefs.savePins(pins, labels);
        setFocus(ZONE_SHELF, shelfIdx, dockIdx);
    }

    private void editMove(int key) {
        if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) return;
        int dir = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
        int tgt = neighbour(editIdx, dir);
        if (tgt < 0) {
            nudge(nudgeTw, dir * 6f);
            return;
        }
        String p = pins[editIdx];
        pins[editIdx] = pins[tgt];
        pins[tgt] = p;
        String l = labels[editIdx];
        labels[editIdx] = labels[tgt];
        labels[tgt] = l;
        float a = wTw[editIdx].cur;
        float b = wTw[tgt].cur;
        wTw[editIdx].set(b);
        wTw[tgt].set(a);
        if (Prefs.rest() == editIdx) Prefs.setRest(tgt);
        else if (Prefs.rest() == tgt) Prefs.setRest(editIdx);
        editIdx = tgt;
        Prefs.savePins(pins, labels);
        setFocus(ZONE_SHELF, tgt, dockIdx);
    }

    void removeFromShelf(int slot) {
        reloadPins();
        if (slot < 0 || slot >= Prefs.SLOTS) return;
        pins[slot] = "";
        labels[slot] = "";
        Prefs.savePins(pins, labels);
        int r = restIndex();
        if (Prefs.rest() == slot && r >= 0) Prefs.setRest(r);
        if (r < 0) {
            zone = ZONE_DOCK;
            dockIdx = DOCK_LIBRARY;
        } else if (zone == ZONE_SHELF && shelfIdx == slot) {
            shelfIdx = r;
        }
        syncWidths(false);
        syncScene();
        invalidate();
    }

    private String currentLine() {
        if (editing) return "Left and Right to move, OK to drop, Back to finish";
        if (!defaultHome) return "Mantel isn’t your Home app yet. Settings, Home check shows how.";
        return null;
    }

    // ------------------------------------------------------------------ drawing

    /** Left edge of a slab relative to the row start, from the current widths. */
    private float slotLeft(int slot) {
        float x = 0f;
        for (int i = 0; i < slot; i++) {
            if (pins[i].length() == 0) continue;
            x += wTw[i].cur + GAP;
        }
        return x;
    }

    @Override void draw1(Canvas c, long now) {
        for (int i = 0; i < Prefs.SLOTS; i++) tw(wTw[i], now);
        tw(nudgeTw, now);
        tw(pressTw, now);
        tw(launchTw, now);

        drawVignette(c);
        drawHeader(c);
        drawShelf(c, now);
        drawDock(c);
        drawHints(c);
        drawMood(c);
        drawLine(c, now);
        drawLaunch(c, now);
        if (Scenes.moving() && !pinsEmpty() && act.top() == this) {
            postInvalidateDelayed(33);
        }
    }

    private void drawVignette(Canvas c) {
        if (vignette == null) {
            vignette = new RadialGradient(960f, 410f, 1100f, 0x1A07080C, 0xD107080C, Shader.TileMode.CLAMP);
        }
        Ui.P.setStyle(Paint.Style.FILL);
        Ui.P.setShader(vignette);
        c.drawRect(-800f, -800f, 2720f, 1880f, Ui.P);
        Ui.P.setShader(null);
    }

    private String greeting() {
        int m = Ui.moodNow();
        if (m == 1) return "Good morning";
        if (m == 2) return "Good afternoon";
        if (m == 3) return "Good evening";
        return "Late night";
    }

    private void drawHeader(Canvas c) {
        // arch mark and wordmark
        Path arch = new Path();
        arch.moveTo(98f, 90f);
        arch.lineTo(98f, 70f);
        rf.set(98f, 56f, 126f, 84f);
        arch.arcTo(rf, 180f, 180f);
        arch.lineTo(126f, 90f);
        arch.moveTo(94f, 90f);
        arch.lineTo(130f, 90f);
        Ui.P.setShader(null);
        Ui.P.setStyle(Paint.Style.STROKE);
        Ui.P.setStrokeWidth(3f);
        Ui.P.setStrokeCap(Paint.Cap.ROUND);
        Ui.P.setColor(Ui.TEXT);
        c.drawPath(arch, Ui.P);
        Ui.P.setStrokeCap(Paint.Cap.BUTT);
        Ui.P.setStyle(Paint.Style.FILL);
        Ui.text(c, "Mantel", 146f, 90f, 40f, Ui.display, Ui.TEXT, Paint.Align.LEFT);
        float nw = Ui.measure("Mantel", 40f, Ui.display);
        Ui.text(c, greeting(), 146f + nw + 28f, 90f, 28f, Ui.regular, Ui.alpha(Ui.TEXT, 0.74f), Paint.Align.LEFT);

        boolean is24 = DateFormat.is24HourFormat(getContext());
        if (timeFmt == null || is24 != fmt24) {
            fmt24 = is24;
            Locale loc = Locale.getDefault();
            timeFmt = new SimpleDateFormat(is24 ? "HH:mm" : "h:mm", loc);
            apFmt = new SimpleDateFormat("a", loc);
        }
        Date d = new Date();
        String t = timeFmt.format(d);
        float right = 1824f;
        if (!is24) {
            String ap = apFmt.format(d).toUpperCase(Locale.ROOT);
            Ui.text(c, ap, right, 90f, 26f, Ui.medium, Ui.alpha(Ui.TEXT, 0.74f), Paint.Align.RIGHT);
            right -= Ui.measure(ap, 26f, Ui.medium) + 12f;
        }
        Ui.text(c, t, right, 100f, 60f, Ui.display, Ui.TEXT, Paint.Align.RIGHT);
        float cw = Ui.measure(t, 60f, Ui.display);

        if (act.offline) {
            String label = "Offline";
            float w = 24f + 32f + 14f + Ui.measure(label, 26f, Ui.medium) + 24f;
            float pr = right - cw - 32f;
            float pl = pr - w;
            Ui.rr(c, pl, 56f, pr, 112f, 28f, Ui.alpha(0xFFFFB13B, 0.18f));
            Ui.ring(c, pl, 56f, pr, 112f, 28f, 1.5f, 0xFFFFB13B);
            Glyphs.draw(c, Glyphs.WIFI_OFF, pl + 40f, 84f, 30f, 2.4f, 0xFFFFC96B);
            Ui.text(c, label, pl + 68f, 94f, 26f, Ui.medium, 0xFFFFC96B, Paint.Align.LEFT);
        }
        long ms = System.currentTimeMillis();
        removeCallbacks(clockTick);
        postDelayed(clockTick, 60000 - (ms % 60000) + 40);

        ArrayList<Integer> v = visible();
        Ui.TP.setLetterSpacing(0.14f);
        Ui.text(c, "YOUR SHELF", 96f, 172f, 26f, Ui.medium, Ui.alpha(Ui.TEXT, 0.78f), Paint.Align.LEFT);
        Ui.TP.setLetterSpacing(0f);
        if (!v.isEmpty()) {
            int pos = Math.max(0, v.indexOf(shelfIdx)) + 1;
            Ui.text(c, (pos < 10 ? "0" : "") + pos + " of " + v.size(), 1824f, 172f, 26f, Ui.medium,
                    Ui.alpha(Ui.TEXT, 0.78f), Paint.Align.RIGHT);
        }
    }

    // ---- slabs ----

    private void drawShelf(Canvas c, long now) {
        if (pinsEmpty()) {
            drawEmpty(c);
            return;
        }
        for (int i = 0; i < Prefs.SLOTS; i++) {
            if (pins[i].length() == 0 || i == shelfIdx) continue;
            drawSlab(c, i, false, now);
        }
        if (pins[shelfIdx].length() > 0) drawSlab(c, shelfIdx, true, now);
    }

    private void drawEmpty(Canvas c) {
        Ui.rr(c, 96f, ROW_TOP, 1824f, ROW_TOP + SLAB_H, RADIUS, 0x14FFFFFF);
        Ui.ring(c, 96f, ROW_TOP, 1824f, ROW_TOP + SLAB_H, RADIUS, 1.5f, 0x29FFFFFF);
        Glyphs.draw(c, Glyphs.GRID, 960f, ROW_TOP + 190f, 110f, 4f, Ui.TEXT);
        Ui.text(c, "Nothing on your shelf yet", 960f, ROW_TOP + 330f, 56f, Ui.display, Ui.TEXT, Paint.Align.CENTER);
        Ui.text(c, "Open the Library, hold OK on an app, and pin it here.", 960f, ROW_TOP + 394f, 30f,
                Ui.regular, Ui.alpha(Ui.TEXT, 0.8f), Paint.Align.CENTER);
    }

    private void drawSlab(Canvas c, int slot, boolean focused, long now) {
        AppEntry e = entryAt(slot);
        float w = wTw[slot].cur;
        float l = ROW_LEFT + slotLeft(slot) + (focused ? nudgeTw.cur : 0f);
        float t = ROW_TOP;
        float r = l + w;
        float b = t + SLAB_H;
        boolean hasFocus = focused && zone == ZONE_SHELF;
        float press = focused ? pressTw.cur : 1f;
        float expand = Math.max(0f, Math.min(1f, (w - SPINE) / Math.max(1f, expandedWidth() - SPINE)));

        c.save();
        if (press != 1f) {
            c.translate((l + r) / 2f, (t + b) / 2f);
            c.scale(press, press);
            c.translate(-(l + r) / 2f, -(t + b) / 2f);
        }

        // scene, clipped to the slab
        rf.set(l, t, r, b);
        clip.reset();
        clip.addRoundRect(rf, RADIUS, RADIUS, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        int brand = Brand.of(e);
        int hue = Brand.hueFor(e);
        Scenes.draw(c, l, t, r, b, brand, hue, expand > 0.5f ? now : 0L, 1f);
        int top = Ui.alpha(0xFF000000, 0.30f - 0.30f * expand);
        int bottom = Ui.alpha(0xFF000000, 0.72f + 0.08f * expand);
        Ui.P.setStyle(Paint.Style.FILL);
        Ui.P.setShader(new LinearGradient(0f, t + SLAB_H * 0.32f * expand, 0f, b, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(l, t, r, b, Ui.P);
        Ui.P.setShader(null);

        // collapsed content (fades out as the slab opens)
        float cA = 1f - Math.min(1f, expand * 2.2f);
        if (cA > 0.02f) {
            c.save();
            TileArt.draw(c, act, e, l + SPINE / 2f, t + 32f + 36f, 72f);
            String name = Ui.fit(slotLabel(slot), 400f, 30f, Ui.medium);
            c.translate(l + SPINE / 2f + 10f, b - 36f);
            c.rotate(-90f);
            Ui.text(c, name, 0f, 0f, 30f, Ui.medium, Ui.alpha(Ui.TEXT, 0.92f * cA), Paint.Align.LEFT);
            c.restore();
        }

        // expanded content
        float eA = Math.max(0f, (expand - 0.55f) / 0.45f);
        if (eA > 0.02f) {
            float cw = r - l;
            TileArt.draw(c, act, e, l + 44f + 66f, t + 44f + 66f, 132f);
            String name = Ui.fit(slotLabel(slot), cw - 96f, 80f, Ui.display);
            float by = b - 44f;                                  // bottom of the button row
            float btnTop = by - 76f;
            float kindY = btnTop - 26f;
            float nameY = kindY - 62f;
            Ui.text(c, name, l + 48f, nameY, 80f, Ui.display, Ui.alpha(Ui.TEXT, eA), Paint.Align.LEFT);
            String kl = Brand.kind(e);
            String ln = Brand.line(e);
            if (ln.length() > 0) kl = kl + " · " + ln;
            Ui.text(c, Ui.fit(kl, cw - 96f, 30f, Ui.regular), l + 48f, kindY, 30f, Ui.regular,
                    Ui.alpha(Ui.TEXT, 0.9f * eA), Paint.Align.LEFT);
            // Open pill
            float ow = 30f + 30f + 14f + Ui.measure("Open", 30f, Ui.medium) + 40f;
            Ui.rr(c, l + 48f, btnTop, l + 48f + ow, by, 38f, Ui.alpha(Ui.TEXT, eA));
            Path play = new Path();
            float px = l + 48f + 30f;
            float py = btnTop + 38f;
            play.moveTo(px, py - 14f);
            play.lineTo(px, py + 14f);
            play.lineTo(px + 24f, py);
            play.close();
            Ui.P.setStyle(Paint.Style.FILL);
            Ui.P.setColor(Ui.alpha(0xFF0B0C10, eA));
            c.drawPath(play, Ui.P);
            Ui.text(c, "Open", px + 24f + 14f, btnTop + 49f, 30f, Ui.medium, Ui.alpha(0xFF0B0C10, eA), Paint.Align.LEFT);
            // Options hint
            float hw = Ui.measure("Hold OK for options", 27f, Ui.medium) + 60f;
            float hl = l + 48f + ow + 16f;
            Ui.rr(c, hl, btnTop, hl + hw, by, 38f, Ui.alpha(0xFFFFFFFF, 0.14f * eA));
            Ui.ring(c, hl, btnTop, hl + hw, by, 38f, 1.5f, Ui.alpha(0xFFFFFFFF, 0.22f * eA));
            Ui.text(c, "Hold OK for options", hl + 30f, btnTop + 48f, 27f, Ui.medium, Ui.alpha(Ui.TEXT, eA), Paint.Align.LEFT);
        }
        c.restore();   // clip

        if (hasFocus) {
            int halo = editing ? Ui.alpha(Ui.accent, 0.55f) : Ui.alpha(Ui.accent, 0.40f);
            Ui.ring(c, l - 8f, t - 8f, r + 8f, b + 8f, RADIUS + 8f, 12f, halo);
            Ui.ring(c, l - 2f, t - 2f, r + 2f, b + 2f, RADIUS + 2f, 4f, editing ? Ui.accent : Ui.TEXT);
        }
        c.restore();
    }

    // ---- dock ----

    private float dockItemWidth(int i) {
        return 30f + 34f + 14f + Ui.measure(DOCK_LABELS[i], 28f, Ui.medium) + 30f;
    }

    private void drawDock(Canvas c) {
        float total = 10f * 2f + 8f * 3f;
        for (int i = 0; i < 4; i++) total += dockItemWidth(i);
        float l = (1920f - total) / 2f;
        float t = 934f;
        float b = 1030f;
        Ui.rr(c, l, t, l + total, b, 48f, 0x2E0E1016);
        Ui.rr(c, l, t, l + total, b, 48f, 0x17FFFFFF);
        Ui.ring(c, l, t, l + total, b, 48f, 1.5f, 0x29FFFFFF);
        float x = l + 10f;
        for (int i = 0; i < 4; i++) {
            float w = dockItemWidth(i);
            boolean f = zone == ZONE_DOCK && dockIdx == i;
            float nx = f ? nudgeTw.cur : 0f;
            int ink = f ? 0xFF0B0C10 : Ui.TEXT;
            if (f) {
                Ui.ring(c, x - 6f + nx, 944f - 6f, x + w + 6f + nx, 1020f + 6f, 44f, 8f, Ui.alpha(Ui.accent, 0.45f));
                Ui.rr(c, x + nx, 944f, x + w + nx, 1020f, 38f, Ui.TEXT);
            }
            Glyphs.draw(c, DOCK_GLYPHS[i], x + 30f + 17f + nx, 982f, 34f, 2.2f, ink);
            Ui.text(c, DOCK_LABELS[i], x + 30f + 34f + 14f + nx, 992f, 28f, Ui.medium, ink, Paint.Align.LEFT);
            x += w + 8f;
        }
    }

    // ---- hints ----

    private float chip(Canvas c, float x, float y, String label) {
        float w = Math.max(44f, Ui.measure(label, 22f, Ui.medium) + 24f);
        Ui.ring(c, x, y, x + w, y + 40f, 12f, 1.5f, Ui.alpha(Ui.TEXT, 0.42f));
        Ui.text(c, label, x + w / 2f, y + 29f, 22f, Ui.medium, Ui.TEXT, Paint.Align.CENTER);
        return x + w;
    }

    private float arrowChip(Canvas c, float x, float y, int kind) {
        // kind 0: left and right triangles, 1: down triangle
        float w = kind == 0 ? 76f : 44f;
        Ui.ring(c, x, y, x + w, y + 40f, 12f, 1.5f, Ui.alpha(Ui.TEXT, 0.42f));
        Path p = new Path();
        if (kind == 0) {
            p.moveTo(x + 28f, y + 12f); p.lineTo(x + 28f, y + 28f); p.lineTo(x + 18f, y + 20f); p.close();
            p.moveTo(x + 48f, y + 12f); p.lineTo(x + 48f, y + 28f); p.lineTo(x + 58f, y + 20f); p.close();
        } else {
            p.moveTo(x + 14f, y + 15f); p.lineTo(x + 30f, y + 15f); p.lineTo(x + 22f, y + 26f); p.close();
        }
        Ui.P.setShader(null);
        Ui.P.setStyle(Paint.Style.FILL);
        Ui.P.setColor(Ui.TEXT);
        c.drawPath(p, Ui.P);
        return x + w;
    }

    private void drawHints(Canvas c) {
        int dim = Ui.alpha(Ui.TEXT, 0.8f);
        float x = arrowChip(c, 96f, 936f, 0);
        Ui.text(c, "Browse", x + 12f, 965f, 24f, Ui.regular, dim, Paint.Align.LEFT);
        x += 12f + Ui.measure("Browse", 24f, Ui.regular) + 22f;
        x = chip(c, x, 936f, "OK");
        Ui.text(c, "Open", x + 12f, 965f, 24f, Ui.regular, dim, Paint.Align.LEFT);

        x = chip(c, 96f, 990f, "Hold OK");
        Ui.text(c, "Options", x + 12f, 1019f, 24f, Ui.regular, dim, Paint.Align.LEFT);
        x += 12f + Ui.measure("Options", 24f, Ui.regular) + 22f;
        x = arrowChip(c, x, 990f, 1);
        Ui.text(c, "Dock", x + 12f, 1019f, 24f, Ui.regular, dim, Paint.Align.LEFT);
    }

    private void drawMood(Canvas c) {
        String s = Ui.moodName() + " palette";
        float w = Ui.measure(s, 24f, Ui.regular);
        Ui.text(c, s, 1824f, 1012f, 24f, Ui.regular, Ui.alpha(Ui.TEXT, 0.8f), Paint.Align.RIGHT);
        Ui.circle(c, 1824f - w - 24f, 1003f, 7f, Ui.accent);
    }

    private void drawLine(Canvas c, long now) {
        String line = currentLine();
        if (line != null) hintShown = line;
        float target = line != null ? 1f : 0f;
        if (hintA.to != target) hintA.go(target, Ui.dur(240));
        tw(hintA, now);
        float a = hintA.cur;
        if (a <= 0.01f || hintShown.length() == 0) return;
        float w = Ui.measure(hintShown, 28f, Ui.medium) + 64f;
        Ui.rr(c, 960f - w / 2f, 846f, 960f + w / 2f, 912f, 33f, Ui.alpha(Ui.SURFACE1, 0.94f * a));
        Ui.text(c, hintShown, 960f, 888f, 28f, Ui.medium,
                Ui.alpha(editing ? Ui.TEXT : Ui.TEXT2, a), Paint.Align.CENTER);
    }

    // ---- launch ----

    private void drawLaunch(Canvas c, long now) {
        float k = launchTw.cur;
        if (k <= 0.01f) return;
        AppEntry e = entryAt(shelfIdx);
        float s = 0.94f + 0.06f * k;
        c.save();
        c.translate(960f, 540f);
        c.scale(s, s);
        c.translate(-960f, -540f);
        Scenes.draw(c, 0f, 0f, 1920f, 1080f, Brand.of(e), Brand.hueFor(e), now, k);
        Ui.fillAll(c, Ui.alpha(0xFF07080C, 0.35f * k));
        TileArt.draw(c, act, e, 960f, 440f, 220f);
        Ui.text(c, "Opening " + slotLabel(shelfIdx), 960f, 640f, 56f, Ui.display, Ui.alpha(Ui.TEXT, k), Paint.Align.CENTER);
        c.restore();
    }
}
