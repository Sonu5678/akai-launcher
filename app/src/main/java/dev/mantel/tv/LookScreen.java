package dev.mantel.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;

import java.util.List;

/**
 * Look (section 8): wallpaper, accent colour and dim level on one screen.
 * Focus previews live (the wallpaper crossfades over 600 ms); OK keeps the choice and Back puts
 * back whatever was not kept. Home and Settings are hidden while this is open, so the preview is
 * the wallpaper itself with nothing in the way.
 */
final class LookScreen extends Screen {
    static final int S_WALL = 0, S_ACCENT = 1, S_DIM = 2;
    private static final int Z_TABS = 0, Z_SOURCE = 1, Z_CONTENT = 2;
    private static final int SRC_BUILTIN = 0, SRC_USB = 1, SRC_SOLID = 2;

    private static final String[] TABS = {"Wallpaper", "Accent colour", "Dim level"};
    private static final String[] SOURCES = {"Built-in", "USB", "Solid colour"};
    private static final String[] SOLID_HEX = {
            "0A0A0E", "111318", "16120F", "0F1613", "121018", "0E141B", "1A1210", "000000"};
    private static final String[] SOLID_NAMES = {
            "Base", "Ink", "Umber", "Moss", "Plum", "Navy", "Rust", "Black"};
    private static final String PICK = "pick";

    private static final int COLS = 4;
    private static final float TW = 360f, TH = 203f, GAP = 40f;

    private int section;
    private int zone = Z_CONTENT;
    private int src = SRC_BUILTIN;
    private int gridIdx = 0;
    private int accentIdx;
    private int dimIdx;

    private final Bitmap[] builtinThumbs = new Bitmap[6];
    private Bitmap usbThumb;
    private boolean usbThumbLoading = false;

    private final Tween panelTop = new Tween(280f);
    private final Tween nudgeX = new Tween(0f);
    private final Tween nudgeY = new Tween(0f);
    private final Tween focusPulse = new Tween(1f);
    private LinearGradient scrim;

    LookScreen(MainActivity a, int section) {
        super(a);
        this.section = Math.max(0, Math.min(2, section));
        accentIdx = Math.max(0, Math.min(3, Prefs.accent()));
        dimIdx = Math.max(0, Math.min(3, Prefs.dim()));
        syncWallpaperFocus();
        panelTop.set(panelTopFor(this.section));
    }

    /** The wallpaper's display name for Settings and the grid. */
    static String wallpaperName(String id) {
        if (id == null) return "";
        if (id.startsWith("b")) {
            try {
                int i = Integer.parseInt(id.substring(1)) - 1;
                if (i >= 0 && i < WallpaperGen.NAMES.length) return WallpaperGen.NAMES[i];
            } catch (NumberFormatException e) {
                return "Built-in";
            }
            return "Built-in";
        }
        if (id.startsWith("s:")) return "Solid colour";
        if (id.startsWith("u:")) return "USB image";
        return "";
    }

    private static float panelTopFor(int s) {
        return s == S_WALL ? 280f : 560f;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override void onEnter() {
        // Hide what is underneath so the preview is the wallpaper alone.
        for (Screen s : act.stack) if (s != this) s.setVisibility(View.INVISIBLE);
        loadThumbs();
        speak("Look. " + TABS[section]);
    }

    @Override protected void onDetachedFromWindow() {
        for (Screen s : act.stack) if (s != this) s.setVisibility(View.VISIBLE);
        for (int i = 0; i < builtinThumbs.length; i++) builtinThumbs[i] = null;
        usbThumb = null;
        super.onDetachedFromWindow();
    }

    @Override void onAppsChanged() {
        invalidate();
    }

    // ------------------------------------------------------------------ wallpaper data

    private String[] items() {
        if (src == SRC_BUILTIN) {
            return new String[]{"b1", "b2", "b3", "b4", "b5", "b6"};
        }
        if (src == SRC_USB) {
            String u = Prefs.usbUri();
            if (u.length() > 0) return new String[]{PICK, "u:" + u};
            return new String[]{PICK};
        }
        String[] out = new String[SOLID_HEX.length];
        for (int i = 0; i < out.length; i++) out[i] = "s:" + SOLID_HEX[i];
        return out;
    }

    private String itemName(String id) {
        if (PICK.equals(id)) return "Choose an image";
        if (id.startsWith("b")) return wallpaperName(id);
        if (id.startsWith("s:")) {
            for (int i = 0; i < SOLID_HEX.length; i++) if (id.equals("s:" + SOLID_HEX[i])) return SOLID_NAMES[i];
            return "Solid colour";
        }
        return "USB image";
    }

    /** Source and focus follow the wallpaper that is kept right now. */
    private void syncWallpaperFocus() {
        String cur = Prefs.wallpaper();
        if (cur.startsWith("u:")) src = SRC_USB;
        else if (cur.startsWith("s:")) src = SRC_SOLID;
        else src = SRC_BUILTIN;
        gridIdx = 0;
        String[] it = items();
        for (int i = 0; i < it.length; i++) if (it[i].equals(cur)) gridIdx = i;
    }

    private void loadThumbs() {
        Ui.IO.execute(new Runnable() {
            @Override public void run() {
                for (int i = 0; i < builtinThumbs.length; i++) {
                    final int idx = i;
                    Bitmap raw = null;
                    try {
                        raw = WallpaperGen.render(idx, (int) TW, (int) TH);
                    } catch (Throwable t) {
                        raw = null;
                    }
                    final Bitmap rounded = raw == null ? null : rounded(raw, 20f);
                    Ui.MAIN.post(new Runnable() {
                        @Override public void run() {
                            if (!isAttachedToWindow()) return;
                            builtinThumbs[idx] = rounded;
                            invalidate();
                        }
                    });
                }
            }
        });
        loadUsbThumb();
    }

    private void loadUsbThumb() {
        final String u = Prefs.usbUri();
        if (u.length() == 0 || usbThumbLoading) return;
        usbThumbLoading = true;
        final android.content.Context app = act.getApplicationContext();
        Ui.IO.execute(new Runnable() {
            @Override public void run() {
                Bitmap raw = null;
                try {
                    raw = WallpaperView.decodeUri(app, Uri.parse(u), (int) TW, (int) TH);
                } catch (Throwable t) {
                    raw = null;
                }
                final Bitmap rounded = raw == null ? null : rounded(raw, 20f);
                Ui.MAIN.post(new Runnable() {
                    @Override public void run() {
                        usbThumbLoading = false;
                        if (!isAttachedToWindow()) return;
                        usbThumb = rounded;
                        invalidate();
                    }
                });
            }
        });
    }

    /** Antialiased rounded copy of a bitmap; the source is recycled. */
    private static Bitmap rounded(Bitmap src, float radius) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        c.drawRoundRect(0f, 0f, src.getWidth(), src.getHeight(), radius, radius, p);
        src.recycle();
        return out;
    }

    /** Called by MainActivity when the system picker returns an image. */
    void onImagePicked(Uri u) {
        Prefs.setUsbUri(u.toString());
        String id = "u:" + u.toString();
        Prefs.setWallpaper(id);
        act.previewWallpaper(id);
        usbThumb = null;
        usbThumbLoading = false;
        loadUsbThumb();
        section = S_WALL;
        panelTop.go(panelTopFor(section), Ui.dur(140));
        src = SRC_USB;
        gridIdx = 1;
        zone = Z_CONTENT;
        invalidate();
    }

    // ------------------------------------------------------------------ keys

    @Override void onDir(int key) {
        boolean left = key == KeyEvent.KEYCODE_DPAD_LEFT;
        boolean right = key == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean up = key == KeyEvent.KEYCODE_DPAD_UP;
        boolean down = key == KeyEvent.KEYCODE_DPAD_DOWN;

        if (zone == Z_TABS) {
            if (left || right) {
                int n = section + (left ? -1 : 1);
                if (n < 0 || n > 2) {
                    nudge(nudgeX, left ? -6f : 6f);
                } else {
                    switchSection(n);
                }
            } else if (down) {
                zone = section == S_WALL ? Z_SOURCE : Z_CONTENT;
                speakFocus();
            } else if (up) {
                nudge(nudgeY, -6f);
            }
            invalidate();
            return;
        }

        if (section == S_WALL && zone == Z_SOURCE) {
            if (left || right) {
                int n = src + (left ? -1 : 1);
                if (n < 0 || n > 2) {
                    nudge(nudgeX, left ? -6f : 6f);
                } else {
                    src = n;
                    gridIdx = 0;
                    String cur = Prefs.wallpaper();
                    String[] it = items();
                    for (int i = 0; i < it.length; i++) if (it[i].equals(cur)) gridIdx = i;
                    if (src == SRC_USB) loadUsbThumb();
                    speak(SOURCES[src]);
                }
            } else if (up) {
                zone = Z_TABS;
                speak(TABS[section]);
            } else if (down) {
                zone = Z_CONTENT;
                previewGrid();
                speakFocus();
            }
            invalidate();
            return;
        }

        // content
        if (section == S_WALL) {
            String[] it = items();
            int row = gridIdx / COLS;
            int col = gridIdx % COLS;
            if (left) {
                if (col > 0) moveGrid(gridIdx - 1);
                else nudge(nudgeX, -6f);
            } else if (right) {
                if (col < COLS - 1 && gridIdx + 1 < it.length) moveGrid(gridIdx + 1);
                else nudge(nudgeX, 6f);
            } else if (up) {
                if (row == 0) {
                    zone = Z_SOURCE;
                    // Leaving the grid puts the kept wallpaper back until something is chosen.
                    act.previewWallpaper(Prefs.wallpaper());
                    speak(SOURCES[src]);
                } else {
                    moveGrid(gridIdx - COLS);
                }
            } else if (down) {
                if (gridIdx + COLS < it.length) moveGrid(gridIdx + COLS);
                else nudge(nudgeY, 6f);
            }
        } else if (section == S_ACCENT) {
            if (left || right) {
                int n = accentIdx + (left ? -1 : 1);
                if (n < 0 || n > 3) {
                    nudge(nudgeX, left ? -6f : 6f);
                } else {
                    accentIdx = n;
                    act.previewAccent(n);
                    speak(Ui.ACCENT_NAMES[n]);
                }
            } else if (up) {
                act.previewAccent(Prefs.accent());
                accentIdx = Prefs.accent();
                zone = Z_TABS;
                speak(TABS[section]);
            } else if (down) {
                nudge(nudgeY, 6f);
            }
        } else {
            if (left || right) {
                int n = dimIdx + (left ? -1 : 1);
                if (n < 0 || n > 3) {
                    nudge(nudgeX, left ? -6f : 6f);
                } else {
                    dimIdx = n;
                    act.previewDim(n);
                    speak(Ui.DIM_NAMES[n]);
                }
            } else if (up) {
                act.previewDim(Prefs.dim());
                dimIdx = Prefs.dim();
                zone = Z_TABS;
                speak(TABS[section]);
            } else if (down) {
                nudge(nudgeY, 6f);
            }
        }
        invalidate();
    }

    private void switchSection(int n) {
        // Whatever was only being previewed goes back before the next section opens.
        act.applyLook();
        accentIdx = Math.max(0, Math.min(3, Prefs.accent()));
        dimIdx = Math.max(0, Math.min(3, Prefs.dim()));
        section = n;
        syncWallpaperFocus();
        panelTop.go(panelTopFor(section), Ui.dur(140));
        speak(TABS[section]);
    }

    private void moveGrid(int n) {
        gridIdx = n;
        focusPulse.set(0.96f);
        focusPulse.go(1f, Ui.dur(140));
        previewGrid();
        speakFocus();
    }

    private void previewGrid() {
        String[] it = items();
        if (gridIdx < 0 || gridIdx >= it.length) return;
        if (PICK.equals(it[gridIdx])) {
            act.previewWallpaper(Prefs.wallpaper());
        } else {
            act.previewWallpaper(it[gridIdx]);
        }
    }

    private void speakFocus() {
        if (section == S_WALL && zone == Z_CONTENT) {
            String[] it = items();
            if (gridIdx < it.length) speak(itemName(it[gridIdx]));
        } else if (section == S_WALL && zone == Z_SOURCE) {
            speak(SOURCES[src]);
        }
    }

    @Override void onOk() {
        if (zone == Z_TABS) {
            onDir(KeyEvent.KEYCODE_DPAD_DOWN);
            return;
        }
        if (zone == Z_SOURCE) {
            onDir(KeyEvent.KEYCODE_DPAD_DOWN);
            return;
        }
        if (section == S_WALL) {
            String[] it = items();
            if (gridIdx >= it.length) return;
            String id = it[gridIdx];
            if (PICK.equals(id)) {
                if (!act.pickImage()) {
                    act.showInfo("No file picker",
                            "This TV can't open a file picker, so a USB image can't be chosen. "
                                    + "The built-in wallpapers still work.");
                }
                return;
            }
            Prefs.setWallpaper(id);
            act.previewWallpaper(id);
            speak("Kept " + itemName(id));
        } else if (section == S_ACCENT) {
            Prefs.setAccent(accentIdx);
            act.previewAccent(accentIdx);
            speak("Kept " + Ui.ACCENT_NAMES[accentIdx]);
        } else {
            Prefs.setDim(dimIdx);
            act.previewDim(dimIdx);
            speak("Kept " + Ui.DIM_NAMES[dimIdx]);
        }
        invalidate();
    }

    @Override void onBack() {
        // Back puts back anything that was only previewed.
        act.applyLook();
        act.pop(this);
    }

    // ------------------------------------------------------------------ drawing

    @Override void draw1(Canvas c, long now) {
        tw(panelTop, now);
        tw(nudgeX, now);
        tw(nudgeY, now);
        tw(focusPulse, now);

        // Top scrim so the title stays legible over any wallpaper.
        if (scrim == null) {
            scrim = new LinearGradient(0f, 0f, 0f, 300f, 0xB3000000, 0x00000000, Shader.TileMode.CLAMP);
        }
        Ui.P.setShader(scrim);
        Ui.P.setStyle(Paint.Style.FILL);
        c.drawRect(-800f, 0f, 2720f, 300f, Ui.P);
        Ui.P.setShader(null);

        Ui.text(c, "Look", 120f, 126f, 56f, Ui.medium, Ui.TEXT, Paint.Align.LEFT);
        drawTabs(c);

        if (act.wall.failed) {
            Glyphs.draw(c, Glyphs.WARNING, 136f, 252f, 28f, 2.6f, Ui.ERROR);
            Ui.text(c, "That image couldn't be read, so Mantel is showing the base colour.", 164f, 262f, 26f,
                    Ui.regular, Ui.ERROR, Paint.Align.LEFT);
        }

        float top = panelTop.cur;
        Ui.rr(c, -80f, top, 2000f, 1200f, 40f, Ui.SHEET);

        if (section == S_WALL) drawWallpaper(c, top);
        else if (section == S_ACCENT) drawAccent(c, top);
        else drawDim(c, top);

        // hint
        Ui.text(c, "Move to preview. OK keeps it. Back puts the old one back.", 120f, 1000f, 26f,
                Ui.regular, Ui.TEXT2, Paint.Align.LEFT);
    }

    private void drawTabs(Canvas c) {
        float x = 120f;
        for (int i = 0; i < TABS.length; i++) {
            float w = 24f + Ui.measure(TABS[i], 28f, Ui.medium) + 24f;
            boolean sel = i == section;
            boolean foc = zone == Z_TABS && sel;
            float dx = foc ? nudgeX.cur : 0f;
            float dy = foc ? nudgeY.cur : 0f;
            Ui.rr(c, x + dx, 160f + dy, x + w + dx, 224f + dy, 32f, sel ? Ui.SURFACE2 : Ui.SURFACE1);
            if (foc) Ui.ring(c, x + dx, 160f + dy, x + w + dx, 224f + dy, 32f, 3f, Ui.TEXT);
            else if (sel) Ui.ring(c, x, 160f, x + w, 224f, 32f, 2f, Ui.accent);
            Ui.text(c, TABS[i], x + 24f + dx, 200f + dy, 28f, Ui.medium, sel ? Ui.TEXT : Ui.TEXT2,
                    Paint.Align.LEFT);
            x += w + 16f;
        }
    }

    // ---- wallpaper ----

    private void drawWallpaper(Canvas c, float top) {
        // source chips
        float x = 120f;
        float cy = top + 48f;
        for (int i = 0; i < SOURCES.length; i++) {
            float w = 24f + Ui.measure(SOURCES[i], 28f, Ui.medium) + 24f;
            boolean sel = i == src;
            boolean foc = zone == Z_SOURCE && sel;
            float dx = foc ? nudgeX.cur : 0f;
            Ui.rr(c, x + dx, cy, x + w + dx, cy + 64f, 32f, sel ? Ui.SURFACE2 : Ui.SURFACE1);
            if (foc) Ui.ring(c, x + dx, cy, x + w + dx, cy + 64f, 32f, 3f, Ui.TEXT);
            else if (sel) Ui.ring(c, x, cy, x + w, cy + 64f, 32f, 2f, Ui.accent);
            Ui.text(c, SOURCES[i], x + 24f + dx, cy + 42f, 28f, Ui.medium, sel ? Ui.TEXT : Ui.TEXT2,
                    Paint.Align.LEFT);
            x += w + 16f;
        }

        String[] it = items();
        String kept = Prefs.wallpaper();
        float gy = top + 152f;
        c.save();
        c.clipRect(0f, top + 128f, 1920f, 1080f);
        for (int i = 0; i < it.length; i++) {
            if (zone == Z_CONTENT && i == gridIdx) continue;     // drawn last, on top
            drawThumb(c, it[i], i, gy, false, kept);
        }
        if (zone == Z_CONTENT && gridIdx < it.length) drawThumb(c, it[gridIdx], gridIdx, gy, true, kept);
        c.restore();
    }

    private void drawThumb(Canvas c, String id, int i, float gy, boolean focused, String kept) {
        int row = i / COLS;
        int col = i % COLS;
        float x = 120f + col * (TW + GAP);
        float y = gy + row * (TH + GAP);
        float sc = focused ? 1.04f * focusPulse.cur : 1f;
        float dx = focused ? nudgeX.cur : 0f;
        float dy = focused ? nudgeY.cur : 0f;
        float cx = x + TW / 2f + dx;
        float cyy = y + TH / 2f + dy;

        c.save();
        c.translate(cx, cyy);
        c.scale(sc, sc);
        float l = -TW / 2f, t = -TH / 2f, r = TW / 2f, b = TH / 2f;
        if (PICK.equals(id)) {
            Ui.rr(c, l, t, r, b, 20f, Ui.SURFACE1);
            Glyphs.draw(c, Glyphs.PLUS, 0f, -14f, 56f, 3f, focused ? Ui.TEXT : Ui.TEXT2);
            Ui.text(c, "Choose an image", 0f, 56f, 26f, Ui.regular, focused ? Ui.TEXT : Ui.TEXT2,
                    Paint.Align.CENTER);
        } else if (id.startsWith("b")) {
            Bitmap bm = null;
            try {
                bm = builtinThumbs[Integer.parseInt(id.substring(1)) - 1];
            } catch (Throwable e) {
                bm = null;
            }
            drawBitmapOrPlate(c, bm, l, t, r, b);
        } else if (id.startsWith("s:")) {
            int col2 = Ui.BASE;
            try {
                col2 = 0xFF000000 | Integer.parseInt(id.substring(2), 16);
            } catch (NumberFormatException e) {
                col2 = Ui.BASE;
            }
            Ui.rr(c, l, t, r, b, 20f, col2);
            Ui.ring(c, l, t, r, b, 20f, 2f, Ui.SURFACE2);
        } else {
            drawBitmapOrPlate(c, usbThumb, l, t, r, b);
        }

        // kept marker: an accent badge with a check, so it never relies on colour alone
        if (id.equals(kept)) {
            Ui.circle(c, r - 32f, t + 32f, 20f, Ui.accent);
            Glyphs.draw(c, Glyphs.CHECK, r - 32f, t + 32f, 24f, 3f, Ui.BASE);
        }
        if (focused) {
            Ui.ring(c, l - 5.5f, t - 5.5f, r + 5.5f, b + 5.5f, 25.5f, 3f, Ui.TEXT);
            String name = itemName(id);
            if (!PICK.equals(id)) {
                float nw = 24f + Ui.measure(name, 26f, Ui.medium) + 24f;
                Ui.rr(c, l + 16f, b - 56f, l + 16f + nw, b - 16f, 20f, 0xCC0A0A0E);
                Ui.text(c, name, l + 16f + 24f, b - 28f, 26f, Ui.medium, Ui.TEXT, Paint.Align.LEFT);
            }
        }
        c.restore();
    }

    private void drawBitmapOrPlate(Canvas c, Bitmap bm, float l, float t, float r, float b) {
        if (bm == null) {
            Ui.rr(c, l, t, r, b, 20f, Ui.SURFACE1);
            return;
        }
        Ui.R.set(l, t, r, b);
        c.drawBitmap(bm, null, Ui.R, Ui.BP);
    }

    // ---- accent ----

    private void drawAccent(Canvas c, float top) {
        for (int i = 0; i < 4; i++) {
            float cx = 120f + 120f + i * 240f;
            float cy = top + 120f;
            boolean foc = zone == Z_CONTENT && i == accentIdx;
            boolean kept = i == Prefs.accent();
            float dx = foc ? nudgeX.cur : 0f;
            float dy = foc ? nudgeY.cur : 0f;
            Ui.circle(c, cx + dx, cy + dy, 48f, Ui.ACCENTS[i]);
            if (kept) Glyphs.draw(c, Glyphs.CHECK, cx + dx, cy + dy, 40f, 4f, Ui.BASE);
            if (foc) Ui.ring(c, cx - 58f + dx, cy - 58f + dy, cx + 58f + dx, cy + 58f + dy, 58f, 3f, Ui.TEXT);
            Ui.text(c, Ui.ACCENT_NAMES[i], cx, cy + 48f + 52f, 28f, foc ? Ui.medium : Ui.regular,
                    foc ? Ui.TEXT : Ui.TEXT2, Paint.Align.CENTER);
        }
        List<String> lines = Ui.wrap("The accent shows on toggles, kept choices and the shelf-edit ring.",
                960f, 26f, Ui.regular, 2);
        float y = top + 120f + 48f + 52f + 56f;
        for (String s : lines) {
            Ui.text(c, s, 120f, y, 26f, Ui.regular, Ui.TEXT2, Paint.Align.LEFT);
            y += 34f;
        }
    }

    // ---- dim ----

    private void drawDim(Canvas c, float top) {
        float y = top + 64f;
        float w = 240f;
        Ui.rr(c, 120f, y, 120f + w * 4f, y + 96f, 20f, Ui.SURFACE1);
        for (int i = 0; i < 4; i++) {
            float x = 120f + i * w;
            boolean foc = zone == Z_CONTENT && i == dimIdx;
            boolean kept = i == Prefs.dim();
            float dx = foc ? nudgeX.cur : 0f;
            float dy = foc ? nudgeY.cur : 0f;
            if (kept) Ui.rr(c, x + 8f + dx, y + 8f + dy, x + w - 8f + dx, y + 88f + dy, 14f, Ui.accent);
            else if (foc) Ui.rr(c, x + 8f + dx, y + 8f + dy, x + w - 8f + dx, y + 88f + dy, 14f, Ui.SURFACE2);
            if (foc) Ui.ring(c, x + 4f + dx, y + 4f + dy, x + w - 4f + dx, y + 92f + dy, 16f, 3f, Ui.TEXT);
            int tc = kept ? Ui.BASE : (foc ? Ui.TEXT : Ui.TEXT2);
            float tx = x + w / 2f + dx;
            if (kept) {
                float labelW = Ui.measure(Ui.DIM_NAMES[i], 32f, Ui.medium);
                Glyphs.draw(c, Glyphs.CHECK, tx - labelW / 2f - 16f, y + 48f + dy, 28f, 3f, tc);
                Ui.text(c, Ui.DIM_NAMES[i], tx + 8f, y + 60f + dy, 32f, Ui.medium, tc, Paint.Align.CENTER);
            } else {
                Ui.text(c, Ui.DIM_NAMES[i], tx, y + 60f + dy, 32f, foc ? Ui.medium : Ui.regular, tc,
                        Paint.Align.CENTER);
            }
        }
        Ui.text(c, "How dark the wallpaper sits behind the shelf. Move to preview it above.", 120f,
                y + 96f + 56f, 26f, Ui.regular, Ui.TEXT2, Paint.Align.LEFT);
    }
}
