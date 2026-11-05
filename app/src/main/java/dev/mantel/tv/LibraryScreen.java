package dev.mantel.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Library (section 8): a full-screen sheet rising from the bottom. Nine columns of 144 px
 * icons, a Recent row of the nine latest unpinned apps, then A to Z. Hold OK for the menu.
 */
final class LibraryScreen extends Screen {
    private static final int COLS = 9;
    private static final int Z_GRID = 0, Z_CHIP_ALL = 1, Z_CHIP_HIDDEN = 2, Z_SEARCH = 3;

    private boolean hiddenMode;
    private List<AppEntry> items = new ArrayList<AppEntry>();
    private int recentCount = 0;

    private int zone = Z_GRID;
    private int idx = 0;
    private int scrollRow = 0;

    private final Tween scrollTw = new Tween(0f);
    private final Tween scaleTw = new Tween(1f);
    private final Tween nudgeX = new Tween(0f);
    private final Tween nudgeY = new Tween(0f);
    private long openedAt;

    LibraryScreen(MainActivity a, boolean hiddenMode) {
        super(a);
        this.hiddenMode = hiddenMode;
    }

    @Override int enterFrom() {
        return 1;
    }

    // ------------------------------------------------------------------ model

    private void rebuild() {
        String keep = (idx >= 0 && idx < items.size()) ? items.get(idx).pkg : null;
        if (hiddenMode) {
            items = AppRepo.hiddenApps();
            recentCount = 0;
        } else {
            List<AppEntry> lib = AppRepo.library();
            List<AppEntry> rec = AppRepo.recentRow(lib);
            recentCount = rec.size();
            ArrayList<AppEntry> all = new ArrayList<AppEntry>(rec);
            for (AppEntry e : lib) if (!rec.contains(e)) all.add(e);
            items = all;
        }
        int found = -1;
        if (keep != null) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).pkg.equals(keep)) {
                    found = i;
                    break;
                }
            }
        }
        if (found >= 0) idx = found;
        else idx = Math.min(idx, Math.max(0, items.size() - 1));
        if (zone == Z_GRID && items.isEmpty()) zone = hiddenMode ? Z_CHIP_HIDDEN : Z_CHIP_ALL;
        if (zone == Z_CHIP_HIDDEN && AppRepo.hiddenCount() == 0 && !hiddenMode) zone = Z_CHIP_ALL;
        ensureVisible(false);
    }

    private int rowOf(int i) {
        if (i < recentCount) return 0;
        int j = i - recentCount;
        return (recentCount > 0 ? 1 : 0) + j / COLS;
    }

    private int colOf(int i) {
        if (i < recentCount) return i;
        return (i - recentCount) % COLS;
    }

    private int rowCount() {
        return items.isEmpty() ? 0 : rowOf(items.size() - 1) + 1;
    }

    private int indexAt(int row, int col) {
        int base = recentCount > 0 ? 1 : 0;
        if (recentCount > 0 && row == 0) return col < recentCount ? col : -1;
        int j = (row - base) * COLS + col;
        int i = recentCount + j;
        return (row >= base && j >= 0 && i < items.size()) ? i : -1;
    }

    private int rowLen(int row) {
        int n = 0;
        for (int c = 0; c < COLS; c++) if (indexAt(row, c) >= 0) n++;
        return n;
    }

    private void ensureVisible(boolean animate) {
        if (items.isEmpty()) return;
        int row = rowOf(idx);
        if (row < scrollRow) scrollRow = row;
        else if (row > scrollRow + 2) scrollRow = row - 2;
        if (animate) scrollTw.go(scrollRow, Ui.dur(180));
        else scrollTw.set(scrollRow);
    }

    private boolean hasHiddenChip() {
        return AppRepo.hiddenCount() > 0 || hiddenMode;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override void onEnter() {
        openedAt = android.os.SystemClock.uptimeMillis();
        idx = 0;
        rebuild();
        if (!hiddenMode && recentCount == 0) {
            String pkg = Prefs.libraryFocus();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).pkg.equals(pkg)) {
                    idx = i;
                    break;
                }
            }
            ensureVisible(false);
        }
        if (items.isEmpty()) zone = hiddenMode ? Z_CHIP_HIDDEN : Z_CHIP_ALL;
        speakFocus();
    }

    @Override void onAppsChanged() {
        rebuild();
        invalidate();
    }

    @Override void onReveal() {
        rebuild();
        invalidate();
    }

    private void speakFocus() {
        if (zone == Z_GRID && idx < items.size()) speak(items.get(idx).label);
        else if (zone == Z_SEARCH) speak("Search");
        else speak(zone == Z_CHIP_ALL ? "All" : "Hidden");
    }

    private void focusChanged() {
        scaleTw.set(1f);
        scaleTw.go(1.08f, Ui.dur(140));
        speakFocus();
        invalidate();
    }

    // ------------------------------------------------------------------ keys

    @Override void onDir(int key) {
        if (zone == Z_GRID) {
            gridDir(key);
        } else if (zone == Z_SEARCH) {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                zone = hiddenMode ? Z_CHIP_HIDDEN : Z_CHIP_ALL;
                focusChanged();
            } else {
                nudge(nudgeX, key == KeyEvent.KEYCODE_DPAD_LEFT ? -6f : (key == KeyEvent.KEYCODE_DPAD_RIGHT ? 6f : 0f));
            }
        } else {
            if (key == KeyEvent.KEYCODE_DPAD_UP) {
                zone = Z_SEARCH;
                focusChanged();
            } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!items.isEmpty()) {
                    zone = Z_GRID;
                    focusChanged();
                } else {
                    nudge(nudgeY, 6f);
                }
            } else if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (zone == Z_CHIP_HIDDEN) {
                    zone = Z_CHIP_ALL;
                    focusChanged();
                } else {
                    nudge(nudgeX, -6f);
                }
            } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (zone == Z_CHIP_ALL && hasHiddenChip()) {
                    zone = Z_CHIP_HIDDEN;
                    focusChanged();
                } else {
                    nudge(nudgeX, 6f);
                }
            }
        }
    }

    private void gridDir(int key) {
        if (items.isEmpty()) return;
        int row = rowOf(idx);
        int col = colOf(idx);
        if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
            int ni = idx - 1;
            if (ni >= 0 && rowOf(ni) == row) setIdx(ni);
            else nudge(nudgeX, -6f);
        } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int ni = idx + 1;
            if (ni < items.size() && rowOf(ni) == row) setIdx(ni);
            else nudge(nudgeX, 6f);
        } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
            if (row == 0) {
                zone = hiddenMode ? Z_CHIP_HIDDEN : Z_CHIP_ALL;
                focusChanged();
            } else {
                int target = indexAt(row - 1, Math.min(col, rowLen(row - 1) - 1));
                if (target >= 0) setIdx(target);
            }
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (row + 1 < rowCount()) {
                int target = indexAt(row + 1, Math.min(col, rowLen(row + 1) - 1));
                if (target >= 0) setIdx(target);
            } else {
                nudge(nudgeY, 6f);
            }
        }
    }

    private void setIdx(int ni) {
        idx = ni;
        ensureVisible(true);
        focusChanged();
    }

    @Override void onOk() {
        if (zone == Z_SEARCH) {
            act.openSearch(false);
        } else if (zone == Z_CHIP_ALL) {
            if (hiddenMode) switchMode(false);
        } else if (zone == Z_CHIP_HIDDEN) {
            if (!hiddenMode) switchMode(true);
        } else if (idx < items.size()) {
            AppEntry e = items.get(idx);
            if (hiddenMode) showItemMenu();
            else act.launchEntry(e);
        }
    }

    @Override void onLongOk() {
        if (zone == Z_GRID && idx < items.size()) showItemMenu();
    }

    @Override void onBack() {
        if (zone == Z_GRID && idx < items.size()) Prefs.setLibraryFocus(items.get(idx).pkg);
        act.pop(this);
    }

    private void switchMode(boolean hidden) {
        hiddenMode = hidden;
        idx = 0;
        scrollRow = 0;
        rebuild();
        focusChanged();
    }

    // ------------------------------------------------------------------ item menu

    private void showItemMenu() {
        if (idx >= items.size()) return;
        final AppEntry e = items.get(idx);
        final ArrayList<String> labels = new ArrayList<String>();
        if (hiddenMode) {
            labels.add("Unhide");
            labels.add("App info");
        } else {
            labels.add("Pin to shelf");
            labels.add("Hide");
            labels.add("App info");
            if (e.userInstalled) labels.add("Uninstall");
        }
        int row = rowOf(idx);
        float y = 260f + (row - scrollTw.cur) * 216f;
        float cx = 120f + colOf(idx) * 192f + 72f;
        boolean above = y > 500f;
        act.showMenu(labels.toArray(new String[0]), cx, above ? y - 8f : y + 152f, above,
                new MenuOverlay.Listener() {
                    @Override public void onChoice(int index) {
                        String what = labels.get(index);
                        if ("Pin to shelf".equals(what)) pin(e);
                        else if ("Hide".equals(what)) {
                            Prefs.setHidden(e.pkg, true);
                            rebuild();
                        } else if ("Unhide".equals(what)) {
                            Prefs.setHidden(e.pkg, false);
                            if (AppRepo.hiddenCount() == 0) {
                                hiddenMode = false;
                                zone = Z_CHIP_ALL;
                            }
                            rebuild();
                        } else if ("App info".equals(what)) {
                            act.openAppInfo(e.pkg);
                        } else if ("Uninstall".equals(what)) {
                            act.uninstall(e.pkg);
                        }
                        invalidate();
                    }
                });
    }

    /** Put an app on the shelf: the first empty slot, or the one you choose to replace. */
    private void pin(final AppEntry e) {
        final String[] pins = Prefs.pins();
        final String[] labs = Prefs.pinLabels();
        for (int i = 0; i < Prefs.SLOTS; i++) {
            if (pins[i].length() == 0) {
                pins[i] = e.pkg;
                labs[i] = e.label;
                Prefs.savePins(pins, labs);
                rebuild();
                return;
            }
        }
        String[] names = new String[Prefs.SLOTS];
        for (int i = 0; i < Prefs.SLOTS; i++) {
            AppEntry p = AppRepo.byPkg.get(pins[i]);
            names[i] = "Replace " + (p != null ? p.label : labs[i]);
        }
        act.showMenu(names, 960f, 240f, false, new MenuOverlay.Listener() {
            @Override public void onChoice(int index) {
                pins[index] = e.pkg;
                labs[index] = e.label;
                Prefs.savePins(pins, labs);
                rebuild();
                invalidate();
            }
        });
    }

    // ------------------------------------------------------------------ drawing

    @Override void draw1(Canvas c, long now) {
        tw(scrollTw, now);
        tw(scaleTw, now);
        tw(nudgeX, now);
        tw(nudgeY, now);

        Ui.fillAll(c, Ui.SHEET);
        Ui.text(c, hiddenMode ? "Hidden apps" : "Library", 120f, 126f, 56f, Ui.medium, Ui.TEXT, Paint.Align.LEFT);

        // search pill 480 x 64 at the right margin
        boolean sf = zone == Z_SEARCH;
        float sx = sf ? nudgeX.cur : 0f;
        Ui.rr(c, 1320f + sx, 72f, 1800f + sx, 136f, 32f, sf ? Ui.SURFACE2 : Ui.SURFACE1);
        if (sf) Ui.ring(c, 1320f + sx, 72f, 1800f + sx, 136f, 32f, 3f, Ui.TEXT);
        Glyphs.draw(c, Glyphs.SEARCH, 1320f + sx + 48f, 104f, 32f, 2.6f, sf ? Ui.TEXT : Ui.TEXT2);
        Ui.text(c, "Search apps", 1320f + sx + 84f, 114f, 28f, Ui.medium, sf ? Ui.TEXT : Ui.TEXT2, Paint.Align.LEFT);

        // chips
        float x = 120f;
        x = drawChip(c, x, "All", Z_CHIP_ALL, !hiddenMode);
        if (hasHiddenChip()) {
            drawChip(c, x + 16f, "Hidden (" + AppRepo.hiddenCount() + ")", Z_CHIP_HIDDEN, hiddenMode);
        }

        drawGrid(c, now);
    }

    private float drawChip(Canvas c, float x, String label, int z, boolean selected) {
        float w = Ui.measure(label, 28f, Ui.medium) + 56f + (selected ? 36f : 0f);
        boolean f = zone == z;
        float nx = f ? nudgeX.cur : 0f;
        int fill = selected ? Ui.accent : (f ? Ui.SURFACE2 : Ui.SURFACE1);
        Ui.rr(c, x + nx, 168f, x + w + nx, 224f, 28f, fill);
        if (f) Ui.ring(c, x + nx, 168f, x + w + nx, 224f, 28f, 3f, Ui.TEXT);
        int fg = selected ? Ui.BASE : (f ? Ui.TEXT : Ui.TEXT2);
        float tx = x + 28f + nx;
        if (selected) {
            Glyphs.draw(c, Glyphs.CHECK, tx + 12f, 196f, 28f, 2.6f, fg);
            tx += 36f;
        }
        Ui.text(c, label, tx, 205f, 28f, Ui.medium, fg, Paint.Align.LEFT);
        return x + w;
    }

    private void drawGrid(Canvas c, long now) {
        if (items.isEmpty()) {
            if (!AppRepo.loaded) {
                if (now - openedAt > 1000) drawSpinner(c, now);
                keepAnimating();
            } else {
                Ui.text(c, hiddenMode ? "No hidden apps" : "No apps to show yet", 960f, 580f, 32f,
                        Ui.regular, Ui.TEXT2, Paint.Align.CENTER);
            }
            return;
        }
        c.save();
        c.clipRect(0f, 240f, 1920f, 1080f);
        int focusIdx = zone == Z_GRID ? idx : -1;
        int rows = rowCount();
        int first = Math.max(0, (int) Math.floor(scrollTw.cur) - 0);
        for (int r = first; r < rows; r++) {
            float y = 260f + (r - scrollTw.cur) * 216f;
            if (y > 1080f) break;
            if (y + 216f < 240f) continue;
            for (int col = 0; col < COLS; col++) {
                int i = indexAt(r, col);
                if (i < 0) break;
                if (i == focusIdx) continue;
                drawCell(c, i, 120f + col * 192f, y, false);
            }
        }
        if (focusIdx >= 0) {
            float y = 260f + (rowOf(focusIdx) - scrollTw.cur) * 216f;
            drawCell(c, focusIdx, 120f + colOf(focusIdx) * 192f, y, true);
        }
        c.restore();
    }

    private void drawCell(Canvas c, int i, float x, float y, boolean focused) {
        AppEntry e = items.get(i);
        float sc = focused ? scaleTw.cur : 1f;
        float cx = x + 72f + (focused ? nudgeX.cur : 0f);
        float cy = y + 72f + (focused ? nudgeY.cur : 0f);
        c.save();
        c.translate(cx, cy);
        c.scale(sc, sc);
        TileArt.draw(c, act, e, 0f, 0f, 144f);
        c.restore();
        if (focused) {
            float half = 72f * sc;
            float grow = 5.5f;
            Ui.ring(c, cx - half - grow, cy - half - grow, cx + half + grow, cy + half + grow,
                    30f * sc + grow, 3f, Ui.TEXT);
        }
        String lab = Ui.fit(e.label, 184f, 26f, focused ? Ui.medium : Ui.regular);
        Ui.text(c, lab, x + 72f, y + 144f + 38f, 26f, focused ? Ui.medium : Ui.regular,
                focused ? Ui.TEXT : Ui.TEXT2, Paint.Align.CENTER);
    }

    private void drawSpinner(Canvas c, long now) {
        Ui.P.setShader(null);
        Ui.P.setStyle(Paint.Style.STROKE);
        Ui.P.setStrokeWidth(4f);
        Ui.P.setStrokeCap(Paint.Cap.ROUND);
        Ui.P.setColor(Ui.TEXT2);
        RectF r = Ui.R;
        r.set(940f, 560f, 980f, 600f);
        float start = (now % 900L) / 900f * 360f;
        c.drawArc(r, start, 250f, false, Ui.P);
        Ui.P.setStyle(Paint.Style.FILL);
        Ui.P.setStrokeCap(Paint.Cap.BUTT);
    }
}
