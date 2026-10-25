package com.mylauncher.akaitv;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The home screen: clock, a tagline, a dock of four tiles, and rows that slide up when you
 * press Down (My Shows, Other Apps, Play). Everything is built in code, so there are no layout
 * files. Inputs, All apps and the game open as full-screen layers on top.
 */
public class MainActivity extends Activity {
    private static final int OWN_DOCK = 0, OWN_ROW = 1, OWN_GRID = 2, OWN_INPUTS = 3;
    private static final int ZONE_ICONS = -2, ZONE_DOCK = -1;
    private static boolean guardInstalled;

    // ------------------------------------------------------------------ data

    static final class Item {
        static final int APP = 0, SHOW = 1, MORE = 2, PLAY = 3, INPUT = 4, INPUTS_TILE = 5, PILL = 6;
        final int kind;
        AppEntry app;
        String title = "", sub = "", source = "", uri = "", pkg = "", posterRef = "";
        Bitmap poster;
        String inputId = "", action = "";
        boolean passthrough, connected, placeholder;
        int artKind = ArtView.OTHER;
        int glow = 0xFF3A3A3C;

        Item(int kind) {
            this.kind = kind;
        }
    }

    private static final class Row {
        String title = "";
        int top, cardW, cardH, cellH;
        int cols = 1, gapAfter = -1, zoneTop = -1, focusY = -1;
        boolean labelBelow, noTitle;
        final List<Item> items = new ArrayList<>();
        RecyclerView rv;
        MainActivity.CardAdapter adapter;
    }

    private interface PosSource {
        int pos();

        Item item();
    }

    // ------------------------------------------------------------------ state

    private SharedPreferences prefs;
    private PackageManager pm;
    private ExecutorService io;
    private Config cfg;
    private long cfgStamp;
    private String sig = "";
    private int gen;
    private boolean safe;
    private boolean settingsPanel;
    private static final String TAG = "AkaiLauncher";
    static final String ACTION_SETTINGS_PANEL = "com.mylauncher.akaitv.SETTINGS_PANEL";
    private boolean fg;

    private FrameLayout root, page;
    private GlassView pageGlass;
    private TextView taglineView;
    private View glow;
    private GradientDrawable glowDrawable;
    private int glowColor, glowSize;
    private ValueAnimator glowAnim;

    private final List<AppEntry> rawApps = new ArrayList<>();
    private final List<Item> dockItems = new ArrayList<>();
    private final List<Config.DockSpec> dockSpecs = new ArrayList<>();
    private final List<CardView> dockViews = new ArrayList<>();
    private final List<View> iconViews = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private Row showsRow;
    private int[] tagIdx = new int[0];

    private Typeface tfThin, tfLight, tfRegular;
    private int W, H, M, G, tileW, tileH, labelH, labelGap, dockCellH, pad, dockY, bandH;
    private int rowFocusY, rowTitleH, rowVpad, rowGapTitle, pageH;
    private float s = 1f;
    private int zone = Integer.MIN_VALUE;
    private int fOwner = OWN_DOCK, fIdx = 0, fPos = 0;
    private int lastDock = 0;

    private FrameLayout inputsLayer, appsLayer;
    private GlassView inputsGlass, appsGlass;
    private RecyclerView inputsRv, appsRv;
    private final List<Item> inputItems = new ArrayList<>();
    private final List<Item> appsItems = new ArrayList<>();
    private int appsCols = 5;
    private GameView gameView;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        pm = getPackageManager();
        io = Executors.newFixedThreadPool(2);
        installCrashGuard();
        safe = recentCrashes().size() >= 2;
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        root = new FrameLayout(this);
        root.setClipChildren(false);
        setContentView(root);
        startUi();
        Intent first = getIntent();
        if (!safe && first != null && ACTION_SETTINGS_PANEL.equals(first.getAction())) {
            root.post(this::openSettingsPanel);
        }
    }

    @Override
    protected void onDestroy() {
        gen++;
        if (io != null) io.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        fg = false;
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fg = true;
        if (safe) return;
        refreshIfChanged();
    }

    /** Reloads the screen if the settings, wallpaper or installed apps changed. */
    private void refreshIfChanged() {
        boolean rebuild = false;
        try {
            if (cfg == null || cfg.stamp() != cfgStamp) rebuild = true;
            else if (!signature(queryApps()).equals(sig)) rebuild = true;
        } catch (Throwable t) {
            rebuild = false;
        }
        if (rebuild && gameView == null && inputsLayer == null && appsLayer == null) {
            startUi();
        } else if (getCurrentFocus() == null && root != null) {
            root.post(this::ensureFocus);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (safe) return;
        if (intent != null && ACTION_SETTINGS_PANEL.equals(intent.getAction())) {
            boolean wasOpenOnScreen = fg && settingsPanel;
            closeGame(false);
            if (wasOpenOnScreen) {
                closeOverlays(true);
            } else if (root != null) {
                root.post(() -> {
                    if (!safe && gameView == null) openSettingsPanel();
                });
            }
            return;
        }
        refreshIfChanged();
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            closeGame(false);
            closeOverlays(false);
            fOwner = OWN_DOCK;
            fIdx = 0;
            fPos = 0;
            if (root != null) root.post(this::refocusPage);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && root != null && !safe) {
            root.post(() -> {
                if (getCurrentFocus() == null) ensureFocus();
            });
        }
    }

    /** If a remote button arrives while nothing is focused, focus first and swallow the press. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            Log.i(TAG, "key " + KeyEvent.keyCodeToString(e.getKeyCode()));
        }
        if (!safe && gameView == null && e.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                if (settingsPanel) closeOverlays(true);
                else openSettingsPanel();
            }
            return true;
        }
        if (!safe && gameView == null && e.getAction() == KeyEvent.ACTION_DOWN
                && getCurrentFocus() == null) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    ensureFocus();
                    if (getCurrentFocus() != null) return true;
                    break;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        if (gameView != null) {
            closeGame(true);
        } else if (inputsLayer != null || appsLayer != null) {
            closeOverlays(true);
        } else if (!safe && zone != ZONE_DOCK && !dockViews.isEmpty()) {
            focusDock(lastDock);
        }
        // never call super: a launcher has nowhere to go back to
    }

    // ------------------------------------------------------------------ crash guard

    private void installCrashGuard() {
        if (guardInstalled) return;
        guardInstalled = true;
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                recordCrash();
            } catch (Throwable ignored) {
                // never let the guard itself fail
            }
            if (old != null) old.uncaughtException(t, e);
        });
    }

    private List<Long> recentCrashes() {
        List<Long> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        String raw = prefs.getString("crashes", "");
        for (String part : raw.split(",")) {
            if (part.isEmpty()) continue;
            try {
                long v = Long.parseLong(part);
                if (now - v < 120000L && v <= now + 1000L) out.add(v);
            } catch (NumberFormatException ignored) {
                // skip junk
            }
        }
        return out;
    }

    private void recordCrash() {
        StringBuilder sb = new StringBuilder();
        for (long v : recentCrashes()) sb.append(v).append(',');
        sb.append(System.currentTimeMillis()).append(',');
        prefs.edit().putString("crashes", sb.toString()).commit();
    }

    private void startUi() {
        try {
            if (safe) buildSafe();
            else build();
        } catch (Throwable t) {
            recordCrash();
            try {
                safe = true;
                buildSafe();
            } catch (Throwable ignored) {
                // nothing more we can do
            }
        }
    }

    /** A minimal screen that always works: lets you reach Settings and retry. */
    private void buildSafe() {
        gen++;
        metrics();
        tfThin = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        tfLight = Typeface.create("sans-serif-light", Typeface.NORMAL);
        tfRegular = Typeface.create("sans-serif", Typeface.NORMAL);
        root.removeAllViews();
        root.setBackgroundColor(Color.BLACK);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t1 = text("Safe mode", 72 * s, 0xFFFFFFFF, tfThin);
        col.addView(t1);
        TextView t2 = text("The launcher stopped working twice in a row. Your settings are untouched.",
                28 * s, 0xB3FFFFFF, tfLight);
        t2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(-2, -2);
        l2.topMargin = Math.round(14 * s);
        l2.bottomMargin = Math.round(40 * s);
        col.addView(t2, l2);
        final List<TextView> buttons = new ArrayList<>();
        String[] names = {"Settings", "Wi-Fi", "Try normal mode"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            final TextView b = text(names[i], 30 * s, 0xFFFFFFFF, tfLight);
            b.setGravity(Gravity.CENTER);
            int ph = Math.round(48 * s), pv = Math.round(18 * s);
            b.setPadding(ph, pv, ph, pv);
            b.setBackground(rr(0x22FFFFFF, 40 * s));
            b.setFocusable(true);
            b.setFocusableInTouchMode(true);
            b.setClickable(true);
            b.setOnFocusChangeListener((v, has) -> b.setBackground(rr(has ? 0x66FFFFFF : 0x22FFFFFF, 40 * s)));
            b.setOnClickListener(v -> {
                if (idx == 0) openSettingsScreen(Settings.ACTION_SETTINGS);
                else if (idx == 1) openSettingsScreen(Settings.ACTION_WIFI_SETTINGS);
                else {
                    prefs.edit().putString("crashes", "").commit();
                    safe = false;
                    startUi();
                }
            });
            b.setOnKeyListener((v, key, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (idx < buttons.size() - 1) buttons.get(idx + 1).requestFocus();
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    if (idx > 0) buttons.get(idx - 1).requestFocus();
                    return true;
                }
                return key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(520 * s), -2);
            lp.topMargin = Math.round(16 * s);
            col.addView(b, lp);
            buttons.add(b);
        }
        root.addView(col, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        col.setGravity(Gravity.CENTER);
        root.post(() -> buttons.get(0).requestFocus());
    }

    // ------------------------------------------------------------------ build

    private void metrics() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        W = dm.widthPixels;
        H = dm.heightPixels;
        s = H / 1080f;
        M = Math.round(W * 0.075f);
        G = Math.round(W * 0.012f);
        tileW = Math.round(W * 0.2f);
        tileH = tileW * 9 / 16;
        labelH = Math.round(40 * s);
        labelGap = Math.round(10 * s);
        dockCellH = tileH + labelGap + labelH;
        pad = Math.round(24 * s);
        bandH = dockCellH + 2 * pad;
        dockY = Math.round(H * 0.575f);
        rowFocusY = Math.round(150 * s);
        rowTitleH = Math.round(44 * s);
        rowVpad = Math.round(22 * s);
        rowGapTitle = Math.round(6 * s);
    }

    private void fonts() {
        tfThin = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        tfLight = Typeface.create("sans-serif-light", Typeface.NORMAL);
        tfRegular = Typeface.create("sans-serif", Typeface.NORMAL);
        File ff = cfg.find(cfg.font);
        if (ff != null) {
            try {
                Typeface t = Typeface.createFromFile(ff);
                tfThin = t;
                tfLight = t;
                tfRegular = t;
            } catch (Throwable ignored) {
                // keep system fonts
            }
        }
    }

    private void build() {
        gen++;
        final int myGen = gen;
        cfg = Config.load(this);
        cfgStamp = cfg.stamp();
        if (cfg.error != null) Toast.makeText(this, cfg.error, Toast.LENGTH_LONG).show();

        rawApps.clear();
        rawApps.addAll(queryApps());
        sig = signature(rawApps);
        applyRenames();
        metrics();
        fonts();

        root.removeAllViews();
        gameView = null;
        inputsLayer = null;
        inputsRv = null;
        inputsGlass = null;
        settingsPanel = false;
        appsLayer = null;
        appsRv = null;
        appsGlass = null;
        dockViews.clear();
        iconViews.clear();
        rows.clear();
        dockItems.clear();
        dockSpecs.clear();
        showsRow = null;
        zone = Integer.MIN_VALUE;
        root.setBackgroundColor(cfg.background);

        addWallpaper();
        buildDockItems();
        prepareRows();

        page = new FrameLayout(this);
        page.setClipChildren(false);
        root.addView(page, new FrameLayout.LayoutParams(W, pageH));

        buildAmbient();
        buildHeader();
        buildIcons();
        buildTagline();
        buildDock();
        buildRowViews();

        pageGlass = new GlassView(this, cfg.glassColor, cfg.glassOpacity, Math.max(1.5f, 2f * s));
        page.addView(pageGlass, new FrameLayout.LayoutParams(W, pageH));

        wireIcons();
        root.post(this::restoreFocus);
        loadShows(myGen);
    }

    private void addWallpaper() {
        File wf = cfg.find(cfg.wallpaper);
        Log.i(TAG, "wallpaper name=" + cfg.wallpaper + " file="
                + (wf == null ? "none" : wf.getPath() + " " + wf.length() + " bytes"));
        if (wf != null) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(wf.getPath(), o);
                int sample = 1;
                while (o.outWidth / (sample * 2) >= W && o.outHeight / (sample * 2) >= H) sample *= 2;
                o.inJustDecodeBounds = false;
                o.inSampleSize = sample;
                Bitmap bmp = BitmapFactory.decodeFile(wf.getPath(), o);
                if (bmp == null) {
                    Log.w(TAG, "wallpaper could not be decoded");
                    Toast.makeText(this, "Wallpaper file can't be read. Use a JPG or PNG picture.",
                            Toast.LENGTH_LONG).show();
                }
                if (bmp != null) {
                    Log.i(TAG, "wallpaper loaded " + bmp.getWidth() + "x" + bmp.getHeight());
                    ImageView iv = new ImageView(this);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setImageBitmap(bmp);
                    root.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                    View scrim = new View(this);
                    scrim.setBackgroundColor(0x66000000);
                    root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
                    return;
                }
            } catch (Throwable ex) {
                Log.w(TAG, "wallpaper error " + ex);
            }
        }
        if (cfg.background == 0xFF000000) {
            View amb = new View(this);
            amb.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF05070d, 0xFF0b1122, 0xFF04060b}));
            root.addView(amb, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    private void buildDockItems() {
        for (Config.DockSpec ds : cfg.dock) {
            if (dockItems.size() >= 5) break;
            if (ds.type.equals("inputs")) {
                Item it = new Item(Item.INPUTS_TILE);
                it.title = ds.label.isEmpty() ? "Inputs" : ds.label;
                it.glow = 0xFF5B7CFA;
                dockItems.add(it);
                dockSpecs.add(ds);
            } else {
                AppEntry a = null;
                for (String m : ds.match) {
                    a = find(rawApps, m);
                    if (a != null) break;
                }
                if (a == null) continue;
                Item it = new Item(Item.APP);
                it.app = a;
                it.pkg = a.pkg;
                it.title = ds.label.isEmpty() ? a.shown : ds.label;
                Drawable art = a.banner(pm);
                if (art == null) art = a.icon(pm);
                it.glow = dominant(art);
                dockItems.add(it);
                dockSpecs.add(ds);
            }
        }
        if (dockItems.isEmpty()) {
            Config.DockSpec ds = new Config.DockSpec();
            ds.type = "inputs";
            Item it = new Item(Item.INPUTS_TILE);
            it.title = "Inputs";
            it.glow = 0xFF5B7CFA;
            dockItems.add(it);
            dockSpecs.add(ds);
        }
    }

    private void prepareRows() {
        int y = Math.max(dockY + bandH + Math.round(140 * s), H + Math.round(10 * s));
        int gapRow = Math.round(56 * s);

        if (cfg.showsEnabled) {
            Row r = new Row();
            r.title = cfg.showsTitle;
            r.cols = 2;
            r.cardW = (W - 2 * M) / 2 - G;
            r.cardH = Math.round(r.cardW * 10f / 21f);
            r.labelBelow = false;
            for (Config.ShowSpec sp : cfg.shows) {
                if (r.items.size() >= 4) break;
                AppEntry a = sp.app.isEmpty() ? null : find(rawApps, sp.app);
                Item it = new Item(Item.SHOW);
                it.title = sp.title;
                it.pkg = a != null ? a.pkg : "";
                it.source = (a != null ? a.shown : sp.app).toUpperCase(Locale.ROOT);
                it.uri = sp.uri;
                it.posterRef = sp.poster;
                r.items.add(it);
            }
            if (r.items.isEmpty()) r.items.add(placeholderShow());
            showsRow = r;
            rows.add(r);
        }
        Row appsRow = null;
        if (cfg.otherEnabled) {
            Row r = new Row();
            r.title = cfg.otherTitle;
            r.cardW = tileW;
            r.cardH = tileH;
            r.labelBelow = true;
            Set<String> used = new HashSet<>();
            Set<String> dockPk = new HashSet<>();
            for (Item d : dockItems) if (!d.pkg.isEmpty()) dockPk.add(d.pkg);
            for (List<String> spec : cfg.otherApps) {
                if (spec.size() == 1 && spec.get(0).trim().equalsIgnoreCase("@inputs")) {
                    Item inp = new Item(Item.INPUTS_TILE);
                    inp.title = "Inputs";
                    r.items.add(inp);
                    continue;
                }
                AppEntry a = null;
                for (String m : spec) {
                    a = find(rawApps, m);
                    if (a != null) break;
                }
                if (a == null || !used.add(a.cn.flattenToShortString())) continue;
                Item it = new Item(Item.APP);
                it.app = a;
                it.pkg = a.pkg;
                it.title = a.shown;
                r.items.add(it);
            }
            // keep the row looking full: if some apps are not installed, borrow other installed apps
            if (r.items.size() < 4) {
                List<AppEntry> cand = new ArrayList<>();
                for (AppEntry a : rawApps) {
                    String pk = a.pkg;
                    if (dockPk.contains(pk) || pk.equals(getPackageName())) continue;
                    if (used.contains(a.cn.flattenToShortString())) continue;
                    if (pk.startsWith("com.android.") || pk.contains("launcher")
                            || pk.contains("setupwraith") || pk.contains("tvrecommendations")) continue;
                    cand.add(a);
                }
                java.util.Collections.sort(cand, (ea, eb) -> {
                    int ba = ea.ai.banner != 0 ? 0 : 1, bb = eb.ai.banner != 0 ? 0 : 1;
                    if (ba != bb) return ba - bb;
                    return ea.label.compareToIgnoreCase(eb.label);
                });
                for (AppEntry a : cand) {
                    if (r.items.size() >= 4) break;
                    Item it = new Item(Item.APP);
                    it.app = a;
                    it.pkg = a.pkg;
                    it.title = a.shown;
                    r.items.add(it);
                }
            }
            if (!r.items.isEmpty()) {
                r.gapAfter = cfg.moreApps ? Math.round(14 * s) : -1;
                r.focusY = Math.round(420 * s);
                rows.add(r);
                appsRow = r;
            }
        }
        if (cfg.moreApps) {
            Row r = new Row();
            r.noTitle = true;
            r.cardW = Math.round(W * 0.14f);
            r.cardH = Math.round(64 * s);
            r.labelBelow = false;
            Item more = new Item(Item.PILL);
            more.title = "More apps";
            r.items.add(more);
            if (appsRow != null) {
                r.zoneTop = -2; // follows the Other Apps row
            }
            rows.add(r);
        }
        if (cfg.playEnabled) {
            Row r = new Row();
            r.title = cfg.playTitle;
            r.cardW = Math.round(W * 0.4f);
            r.cardH = Math.round(r.cardW * 0.4f);
            r.labelBelow = false;
            Item play = new Item(Item.PLAY);
            play.title = "Night Glider";
            r.items.add(play);
            rows.add(r);
        }
        Row prevRow = null;
        for (Row r : rows) {
            r.cellH = r.cols > 1 ? r.cols * r.cardH + G
                    : r.cardH + (r.labelBelow ? labelGap + labelH : 0);
            r.top = y;
            int tH = r.noTitle ? 0 : rowTitleH + rowGapTitle;
            if (r.zoneTop == -2 && prevRow != null) {
                r.zoneTop = prevRow.zoneTop;
                r.focusY = prevRow.focusY;
            } else if (r.cols > 1) {
                r.zoneTop = y + tH; // the title scrolls away, like on Apple TV
                r.focusY = Math.round(40 * s);
            } else {
                r.zoneTop = y;
                if (r.focusY < 0) r.focusY = rowFocusY;
            }
            y += tH + r.cellH + (r.gapAfter >= 0 ? r.gapAfter : gapRow);
            prevRow = r;
        }
        pageH = Math.max(H, y + Math.round(60 * s));
    }

    private Item placeholderShow() {
        Item it = new Item(Item.SHOW);
        it.placeholder = true;
        it.title = "Nothing here yet";
        it.source = "MY SHOWS";
        return it;
    }

    private void buildAmbient() {
        glowSize = Math.round(H * 0.95f);
        glow = new View(this);
        glowDrawable = new GradientDrawable();
        glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowDrawable.setGradientRadius(glowSize / 2f);
        glowDrawable.setGradientCenter(0.5f, 0.5f);
        glowColor = 0;
        glowDrawable.setColors(new int[]{0, 0});
        glow.setBackground(glowDrawable);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(glowSize, glowSize);
        gl.topMargin = dockY + bandH / 2 - glowSize / 2;
        page.addView(glow, gl);

        int n = dockItems.size();
        int bandW = n * tileW + (n - 1) * G + 2 * pad;
        View band = new View(this);
        band.setBackground(rr(0x1FFFFFFF, 44 * s));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(bandW, bandH);
        bl.leftMargin = M - pad;
        bl.topMargin = dockY;
        page.addView(band, bl);
    }

    private void buildHeader() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        if (cfg.showGreeting) {
            col.addView(text(greeting(), 28 * s, 0xB3FFFFFF, tfLight));
        }
        TextClock clock = new TextClock(this);
        String f = cfg.clock24h ? "HH:mm" : "h:mm";
        clock.setFormat12Hour(f);
        clock.setFormat24Hour(f);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 128 * s);
        clock.setTextColor(0xFFFFFFFF);
        clock.setTypeface(tfThin);
        clock.setIncludeFontPadding(false);
        col.addView(clock);
        if (cfg.showDate) {
            TextClock date = new TextClock(this);
            date.setFormat12Hour("EEEE, d MMMM");
            date.setFormat24Hour("EEEE, d MMMM");
            date.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28 * s);
            date.setTextColor(0xB3FFFFFF);
            date.setTypeface(tfLight);
            col.addView(date);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.leftMargin = M;
        lp.topMargin = Math.round(54 * s);
        page.addView(col, lp);
    }

    private void buildIcons() {
        int d = Math.round(64 * s);
        int gap = Math.round(18 * s);
        for (int i = 0; i < 2; i++) {
            FrameLayout btn = new FrameLayout(this);
            btn.setBackground(rr(0x33FFFFFF, d / 2f));
            btn.setFocusable(true);
            btn.setFocusableInTouchMode(true);
            btn.setClickable(true);
            int p = Math.round(d * 0.2f);
            ArtView art = new ArtView(this, i == 0 ? ArtView.WIFI : ArtView.GEAR);
            FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(d - 2 * p, d - 2 * p);
            al.leftMargin = p;
            al.topMargin = p;
            btn.addView(art, al);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(d, d, Gravity.TOP | Gravity.END);
            lp.topMargin = Math.round(58 * s);
            lp.rightMargin = i == 0 ? M + d + gap : M;
            page.addView(btn, lp);
            iconViews.add(btn);
        }
    }

    private void wireIcons() {
        final int n = iconViews.size();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final View v = iconViews.get(i);
            v.setOnFocusChangeListener((view, has) -> {
                if (has) {
                    setZone(ZONE_ICONS);
                    float m = 5 * s;
                    RectF r = boundsIn(view, page);
                    r.inset(-m, -m);
                    pageGlass.moveTo(r, r.height() / 2f, true);
                }
            });
            v.setOnKeyListener((view, key, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                switch (key) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if (idx > 0) iconViews.get(idx - 1).requestFocus();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (idx < n - 1) iconViews.get(idx + 1).requestFocus();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        focusDock(lastDock);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_UP:
                        return true;
                    default:
                        return false;
                }
            });
            v.setOnClickListener(view -> {
                if (idx == 0) openSettingsScreen(Settings.ACTION_WIFI_SETTINGS);
                else openSettingsPanel();
            });
        }
    }

    private void buildTagline() {
        taglineView = text("", 58 * s, 0xFFFFFFFF, tfThin);
        taglineView.setSingleLine(true);
        taglineView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        taglineView.setMaxWidth(W - 2 * M);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.leftMargin = M;
        lp.topMargin = dockY - Math.round(110 * s);
        page.addView(taglineView, lp);
    }

    private void buildDock() {
        tagIdx = new int[dockItems.size()];
        for (int i = 0; i < dockItems.size(); i++) {
            final int idx = i;
            final Item it = dockItems.get(i);
            tagIdx[i] = (int) (System.nanoTime() % 7);
            final CardView cv = new CardView(it.kind, tileW, tileH, true);
            cv.bind(it);
            wire(cv, OWN_DOCK, 0, new PosSource() {
                @Override
                public int pos() {
                    return idx;
                }

                @Override
                public Item item() {
                    return it;
                }
            });
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(tileW, dockCellH);
            lp.leftMargin = M + i * (tileW + G);
            lp.topMargin = dockY + pad;
            page.addView(cv.cell, lp);
            dockViews.add(cv);
        }
    }

    private void buildRowViews() {
        for (int r = 0; r < rows.size(); r++) {
            final Row row = rows.get(r);
            if (!row.noTitle) {
                TextView title = text(row.title, 30 * s, 0xCCFFFFFF, tfLight);
                FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, rowTitleH);
                tl.leftMargin = M;
                tl.topMargin = row.top;
                page.addView(title, tl);
            }

            RecyclerView rv = new RecyclerView(this);
            if (row.cols > 1) rv.setLayoutManager(new GridLayoutManager(this, row.cols));
            else rv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            rv.setClipChildren(row.cols > 1); // the show grid must never draw over the rows below
            rv.setClipToPadding(false);
            rv.setPadding(M, rowVpad, M, rowVpad);
            rv.setItemAnimator(null);
            rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            rv.setFocusable(false);
            CardAdapter ad = new CardAdapter(row.items, OWN_ROW, r, row.cardW, row.cardH, row.labelBelow);
            ad.vGap = row.cols > 1 ? G : 0;
            row.adapter = ad;
            row.rv = rv;
            rv.setAdapter(ad);
            rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(RecyclerView v, int dx, int dy) {
                    glassFollow(v, pageGlass, page);
                }
            });
            FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(W,
                    row.cellH + (row.cols > 1 ? G : 0) + 2 * rowVpad);
            rl.topMargin = row.top + (row.noTitle ? 0 : rowTitleH + rowGapTitle) - rowVpad;
            page.addView(rv, rl);
        }
    }

    // ------------------------------------------------------------------ cards

    private final class CardView {
        final int kind, ch;
        final boolean hasLabel;
        final FrameLayout cell, card;
        final ImageView img;
        final ArtView art;
        final View scrim;
        final LinearLayout over;
        final TextView over1, over2, label;

        CardView(int kind, int cw, int ch, boolean labelBelow) {
            this.kind = kind;
            this.ch = ch;
            this.hasLabel = labelBelow;
            cell = new FrameLayout(MainActivity.this);
            cell.setClipChildren(false);
            cell.setFocusable(true);
            cell.setFocusableInTouchMode(true);
            cell.setClickable(true);
            cell.setTag(this);
            card = makeCard();
            cell.addView(card, new FrameLayout.LayoutParams(cw, ch));
            img = new ImageView(MainActivity.this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
            art = new ArtView(MainActivity.this, ArtView.OTHER);
            art.setVisibility(View.GONE);
            card.addView(art, new FrameLayout.LayoutParams(-1, -1));
            scrim = new View(MainActivity.this);
            scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x00000000, 0xD9000000}));
            scrim.setVisibility(View.GONE);
            card.addView(scrim, new FrameLayout.LayoutParams(-1, Math.round(ch * 0.62f), Gravity.BOTTOM));
            over = new LinearLayout(MainActivity.this);
            over.setOrientation(LinearLayout.VERTICAL);
            over.setGravity(Gravity.BOTTOM | Gravity.START);
            int op = Math.round(ch * 0.09f);
            over.setPadding(op, op, op, op);
            over.setVisibility(View.GONE);
            over1 = text("", 20 * s, 0xCCFFFFFF, tfRegular);
            over1.setSingleLine(true);
            over2 = text("", 28 * s, 0xFFFFFFFF, tfRegular);
            over2.setSingleLine(true);
            over2.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (kind == Item.SHOW) {
                over.addView(over2);
                over.addView(over1);
            } else {
                over.addView(over1);
                over.addView(over2);
            }
            card.addView(over, new FrameLayout.LayoutParams(-1, -1));
            label = text("", 24 * s, 0xFFFFFFFF, tfLight);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setAlpha(0f);
            if (labelBelow) {
                FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(cw, labelH);
                ll.topMargin = ch + labelGap;
                cell.addView(label, ll);
            }
        }

        void bind(Item it) {
            card.setBackgroundColor(0xFF1C1C1E);
            switch (it.kind) {
                case Item.APP: {
                    art.setVisibility(View.GONE);
                    scrim.setVisibility(View.GONE);
                    over.setVisibility(View.GONE);
                    img.setVisibility(View.VISIBLE);
                    Drawable b = it.app.banner(pm);
                    if (b != null) {
                        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        img.setPadding(0, 0, 0, 0);
                        img.setImageDrawable(b);
                    } else {
                        int p = Math.round(ch * 0.16f);
                        img.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        img.setPadding(p, p, p, p);
                        img.setImageDrawable(it.app.icon(pm));
                    }
                    label.setText(it.title);
                    break;
                }
                case Item.SHOW: {
                    art.setVisibility(View.GONE);
                    scrim.setVisibility(View.VISIBLE);
                    over.setVisibility(View.VISIBLE);
                    over1.setText(it.source);
                    over1.setTextColor(0xCCFFFFFF);
                    over2.setText(it.title);
                    img.setPadding(0, 0, 0, 0);
                    img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    if (it.poster != null) {
                        img.setVisibility(View.VISIBLE);
                        img.setImageBitmap(it.poster);
                    } else {
                        img.setImageDrawable(null);
                        card.setBackground(placeholder(it.title));
                    }
                    break;
                }
                case Item.PILL:
                    img.setVisibility(View.GONE);
                    art.setVisibility(View.GONE);
                    scrim.setVisibility(View.GONE);
                    card.setBackgroundColor(0x40FFFFFF);
                    over.setVisibility(View.VISIBLE);
                    over.setGravity(Gravity.CENTER);
                    over.setPadding(0, 0, 0, 0);
                    over1.setVisibility(View.GONE);
                    over2.setText(it.title);
                    over2.setGravity(Gravity.CENTER);
                    over2.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26 * s);
                    break;
                case Item.MORE:
                    img.setVisibility(View.GONE);
                    scrim.setVisibility(View.GONE);
                    over.setVisibility(View.GONE);
                    art.setVisibility(View.VISIBLE);
                    art.set(ArtView.MORE, false);
                    label.setText(it.title);
                    break;
                case Item.PLAY:
                    img.setVisibility(View.GONE);
                    art.setVisibility(View.VISIBLE);
                    art.set(ArtView.PLAY, false);
                    scrim.setVisibility(View.VISIBLE);
                    over.setVisibility(View.VISIBLE);
                    over1.setText("PRESS OK TO PLAY");
                    over1.setTextColor(0xCCFFFFFF);
                    over2.setText(it.title);
                    break;
                case Item.INPUT:
                    img.setVisibility(View.GONE);
                    scrim.setVisibility(View.GONE);
                    art.setVisibility(View.VISIBLE);
                    art.set(it.artKind, it.connected);
                    over.setVisibility(View.VISIBLE);
                    over1.setText(it.sub);
                    over1.setTextColor(it.connected ? 0xFF34D399 : 0x99FFFFFF);
                    over2.setText(it.title);
                    break;
                case Item.INPUTS_TILE:
                    img.setVisibility(View.GONE);
                    scrim.setVisibility(View.GONE);
                    over.setVisibility(View.GONE);
                    art.setVisibility(View.VISIBLE);
                    art.set(ArtView.INPUT_TILE, false);
                    label.setText(it.title);
                    break;
                default:
                    break;
            }
        }
    }

    private final class CardHolder extends RecyclerView.ViewHolder {
        final CardView cv;

        CardHolder(CardView cv) {
            super(cv.cell);
            this.cv = cv;
        }
    }

    private final class CardAdapter extends RecyclerView.Adapter<CardHolder> {
        final List<Item> items;
        final int owner, idx, cw, ch;
        final boolean labelBelow;
        int vGap;

        CardAdapter(List<Item> items, int owner, int idx, int cw, int ch, boolean labelBelow) {
            this.items = items;
            this.owner = owner;
            this.idx = idx;
            this.cw = cw;
            this.ch = ch;
            this.labelBelow = labelBelow;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).kind;
        }

        @Override
        public CardHolder onCreateViewHolder(ViewGroup parent, int type) {
            final CardView cv = new CardView(type, cw, ch, labelBelow);
            int cellH = ch + (labelBelow ? labelGap + labelH : 0);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(cw, cellH);
            lp.rightMargin = G;
            if (owner == OWN_GRID) lp.bottomMargin = Math.round(18 * s);
            else if (vGap > 0) lp.bottomMargin = vGap;
            cv.cell.setLayoutParams(lp);
            final CardHolder h = new CardHolder(cv);
            wire(cv, owner, idx, new PosSource() {
                @Override
                public int pos() {
                    return h.getBindingAdapterPosition();
                }

                @Override
                public Item item() {
                    int p = h.getBindingAdapterPosition();
                    return p >= 0 && p < items.size() ? items.get(p) : null;
                }
            });
            return h;
        }

        @Override
        public void onBindViewHolder(CardHolder h, int position) {
            h.cv.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private FrameLayout makeCard() {
        FrameLayout card = new FrameLayout(this);
        card.setBackgroundColor(0xFF1C1C1E);
        final float r = cfg.corner * s;
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        card.setClipToOutline(true);
        card.setFocusable(false);
        return card;
    }

    private Drawable placeholder(String t) {
        float hue = Math.abs(t.hashCode() % 360);
        int c1 = Color.HSVToColor(new float[]{hue, 0.50f, 0.42f});
        int c2 = Color.HSVToColor(new float[]{hue, 0.55f, 0.18f});
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
    }

    private void wire(final CardView cv, final int owner, final int idx, final PosSource ps) {
        cv.cell.setOnFocusChangeListener((v, has) -> onCardFocus(cv, owner, idx, ps.pos(), has));
        cv.cell.setOnKeyListener((v, key, e) -> e.getAction() == KeyEvent.ACTION_DOWN
                && navKey(owner, idx, ps.pos(), key));
        cv.cell.setOnClickListener(v -> activate(ps.item()));
        cv.cell.setOnLongClickListener(v -> {
            Item it = ps.item();
            if (it != null && it.kind == Item.APP && it.app != null) {
                showAppInfo(it.app);
                return true;
            }
            return false;
        });
    }

    private void onCardFocus(CardView cv, int owner, int idx, int pos, boolean has) {
        if (!has) {
            cv.card.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            if (cv.hasLabel) cv.label.animate().alpha(0f).setDuration(120).start();
            return;
        }
        cv.card.animate().scaleX(cfg.focusScale).scaleY(cfg.focusScale).setDuration(160).start();
        if (cv.hasLabel) cv.label.animate().alpha(1f).setDuration(160).start();
        if (pos < 0) return;
        if (owner == OWN_DOCK) {
            lastDock = pos;
            fOwner = owner;
            fIdx = 0;
            fPos = pos;
            setZone(ZONE_DOCK);
            saveFocus();
            onDockFocus(pos);
            moveGlassTo(pageGlass, page, cv, true);
        } else if (owner == OWN_ROW) {
            fOwner = owner;
            fIdx = idx;
            fPos = pos;
            setZone(idx);
            saveFocus();
            moveGlassTo(pageGlass, page, cv, true);
        } else if (owner == OWN_GRID) {
            moveGlassTo(appsGlass, appsLayer, cv, true);
        } else {
            moveGlassTo(inputsGlass, inputsLayer, cv, true);
        }
    }

    private void onDockFocus(int pos) {
        if (pos >= dockItems.size()) return;
        Item it = dockItems.get(pos);
        Config.DockSpec ds = dockSpecs.get(pos);
        if (!ds.taglines.isEmpty()) {
            String t = ds.taglines.get(tagIdx[pos] % ds.taglines.size());
            tagIdx[pos]++;
            taglineView.setText(t);
            taglineView.setAlpha(0f);
            taglineView.animate().alpha(1f).setDuration(260).start();
        } else {
            taglineView.setText("");
        }
        animateGlow(it.glow, M + pos * (tileW + G) + tileW / 2);
    }

    // ------------------------------------------------------------------ navigation

    private boolean navKey(int owner, int idx, int pos, int key) {
        if (pos < 0) return false;
        boolean arrow = key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN;
        if (!arrow) return false;
        switch (owner) {
            case OWN_DOCK: {
                int n = dockViews.size();
                if (key == KeyEvent.KEYCODE_DPAD_LEFT && pos > 0) focusDock(pos - 1);
                else if (key == KeyEvent.KEYCODE_DPAD_RIGHT && pos < n - 1) focusDock(pos + 1);
                else if (key == KeyEvent.KEYCODE_DPAD_UP && !iconViews.isEmpty()) {
                    iconViews.get(iconViews.size() - 1).requestFocus();
                } else if (key == KeyEvent.KEYCODE_DPAD_DOWN && !rows.isEmpty()) {
                    focusRow(0, entryPos(0, pos, false));
                }
                return true;
            }
            case OWN_ROW: {
                Row row = rows.get(idx);
                int n = row.items.size();
                int c = row.cols;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (c > 1 ? pos % c > 0 : pos > 0) focusRow(idx, pos - 1);
                } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (c > 1 ? (pos % c < c - 1 && pos + 1 < n) : pos < n - 1) focusRow(idx, pos + 1);
                } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    if (c > 1 && pos >= c) focusRow(idx, pos - c);
                    else if (idx > 0) focusRow(idx - 1, entryPos(idx - 1, mapX(row, pos), true));
                    else focusDock(Math.min(mapX(row, pos), dockViews.size() - 1));
                } else {
                    if (c > 1 && pos + c < n) focusRow(idx, pos + c);
                    else if (c > 1 && pos / c < (n - 1) / c) focusRow(idx, n - 1);
                    else if (idx < rows.size() - 1) {
                        focusRow(idx + 1, entryPos(idx + 1, mapX(row, pos), false));
                    }
                }
                return true;
            }
            case OWN_GRID: {
                int n = appsItems.size();
                int cols = appsCols;
                int col = pos % cols;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (col > 0) focusRv(appsRv, pos - 1, 0);
                } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (col < cols - 1 && pos + 1 < n) focusRv(appsRv, pos + 1, 0);
                } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    if (pos >= cols) focusRv(appsRv, pos - cols, 0);
                } else if (pos + cols < n) {
                    focusRv(appsRv, pos + cols, 0);
                } else if (pos / cols < (n - 1) / cols) {
                    focusRv(appsRv, n - 1, 0);
                }
                return true;
            }
            default: {
                int n = inputItems.size();
                if (key == KeyEvent.KEYCODE_DPAD_LEFT && pos > 0) focusRv(inputsRv, pos - 1, 0);
                else if (key == KeyEvent.KEYCODE_DPAD_RIGHT && pos < n - 1) focusRv(inputsRv, pos + 1, 0);
                return true;
            }
        }
    }

    /** A rough left-to-right position, so moving between rows of different shapes feels natural. */
    private static int mapX(Row from, int pos) {
        return from.cols > 1 ? (pos % from.cols) * 2 : pos;
    }

    /** Which item to land on when entering row r from above or below. */
    private int entryPos(int r, int x, boolean fromBelow) {
        Row row = rows.get(r);
        int n = row.items.size();
        if (n == 0) return 0;
        if (row.cols > 1) {
            int col = x < 2 ? 0 : row.cols - 1;
            int base = fromBelow ? ((n - 1) / row.cols) * row.cols : 0;
            return Math.min(base + col, n - 1);
        }
        return Math.min(x, n - 1);
    }

    private void focusDock(int i) {
        if (i < 0 || i >= dockViews.size()) return;
        dockViews.get(i).cell.requestFocus();
    }

    private void focusRow(int r, int pos) {
        if (r >= 0 && r < rows.size()) focusRv(rows.get(r).rv, pos, 0);
    }

    /** Focuses an item of a list, scrolling it into view first if it is not on screen yet. */
    private void focusRv(final RecyclerView rv, final int pos, final int attempt) {
        if (rv == null || rv.getAdapter() == null) return;
        int n = rv.getAdapter().getItemCount();
        if (n == 0) return;
        final int p = Math.max(0, Math.min(pos, n - 1));
        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(p);
        if (vh != null) {
            vh.itemView.requestFocus();
            return;
        }
        if (attempt > 8) return;
        rv.scrollToPosition(p);
        rv.postDelayed(() -> focusRv(rv, p, attempt + 1), 60);
    }

    private void restoreFocus() {
        int o = prefs.getInt("fo", OWN_DOCK);
        int p = prefs.getInt("fp", 0);
        fOwner = OWN_DOCK;
        fIdx = 0;
        fPos = o == OWN_DOCK ? p : 0;
        refocusPage();
    }

    private void refocusPage() {
        if (page == null) return;
        if (fOwner == OWN_ROW && fIdx < rows.size()) {
            focusRow(fIdx, fPos);
        } else if (!dockViews.isEmpty()) {
            focusDock(Math.max(0, Math.min(fPos, dockViews.size() - 1)));
        } else if (!rows.isEmpty()) {
            focusRow(0, 0);
        } else if (!iconViews.isEmpty()) {
            iconViews.get(0).requestFocus();
        }
    }

    private void ensureFocus() {
        if (inputsRv != null) focusRv(inputsRv, 0, 0);
        else if (appsRv != null) focusRv(appsRv, 0, 0);
        else refocusPage();
    }

    private void saveFocus() {
        prefs.edit().putInt("fo", fOwner).putInt("fi", fIdx).putInt("fp", fPos).apply();
    }

    private void setZone(int z) {
        if (z == zone || page == null) return;
        zone = z;
        float ty = 0f;
        if (z >= 0 && z < rows.size()) {
            Row zr = rows.get(z);
            ty = -(zr.zoneTop - zr.focusY);
        }
        page.animate().translationY(ty).setDuration(320).setInterpolator(new DecelerateInterpolator()).start();
    }

    // ------------------------------------------------------------------ glass and glow

    private void moveGlassTo(GlassView g, View anc, CardView cv, boolean animate) {
        if (g == null || anc == null) return;
        float m = 5 * s;
        RectF r = boundsIn(cv.card, anc);
        grow(r, cfg.focusScale);
        r.inset(-m, -m);
        g.moveTo(r, cfg.corner * s * cfg.focusScale + m, animate);
    }

    private void glassFollow(RecyclerView rv, GlassView g, View anc) {
        View f = getCurrentFocus();
        if (f != null && f.getParent() == rv && f.getTag() instanceof CardView) {
            moveGlassTo(g, anc, (CardView) f.getTag(), false);
        }
    }

    private static RectF boundsIn(View v, View ancestor) {
        float x = 0, y = 0;
        View c = v;
        while (c != null && c != ancestor) {
            x += c.getLeft() + c.getTranslationX();
            y += c.getTop() + c.getTranslationY();
            if (!(c.getParent() instanceof View)) break;
            View p = (View) c.getParent();
            if (p != ancestor) {
                x -= p.getScrollX();
                y -= p.getScrollY();
            }
            c = p;
        }
        return new RectF(x, y, x + v.getWidth(), y + v.getHeight());
    }

    private static void grow(RectF r, float sc) {
        float cx = r.centerX(), cy = r.centerY();
        float hw = r.width() * sc / 2f, hh = r.height() * sc / 2f;
        r.set(cx - hw, cy - hh, cx + hw, cy + hh);
    }

    private void animateGlow(int rgb, int centerX) {
        glow.animate().translationX(centerX - glowSize / 2f).setDuration(400).start();
        int target = (0x70 << 24) | (rgb & 0x00FFFFFF);
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, target);
        glowAnim.setDuration(450);
        glowAnim.addUpdateListener(a -> {
            glowColor = (int) a.getAnimatedValue();
            glowDrawable.setColors(new int[]{glowColor, glowColor & 0x00FFFFFF});
        });
        glowAnim.start();
    }

    /** Average colour of the art, weighted towards the saturated parts. */
    private static int dominant(Drawable d) {
        if (d == null) return 0xFF3A3A3C;
        try {
            Bitmap b = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, 16, 9);
            d.draw(c);
            float[] hsv = new float[3];
            double r = 0, g = 0, bl = 0, wsum = 0;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 9; y++) {
                    int p = b.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    Color.colorToHSV(p, hsv);
                    double w = hsv[1] * hsv[2] + 0.02;
                    r += Color.red(p) * w;
                    g += Color.green(p) * w;
                    bl += Color.blue(p) * w;
                    wsum += w;
                }
            }
            b.recycle();
            if (wsum <= 0) return 0xFF3A3A3C;
            int rgb = Color.rgb((int) (r / wsum), (int) (g / wsum), (int) (bl / wsum));
            Color.colorToHSV(rgb, hsv);
            hsv[2] = Math.max(hsv[2], 0.7f);
            return Color.HSVToColor(255, hsv);
        } catch (Throwable t) {
            return 0xFF3A3A3C;
        }
    }

    // ------------------------------------------------------------------ apps

    private List<AppEntry> queryApps() {
        List<AppEntry> out = new ArrayList<>();
        Set<String> leanbackPkgs = new HashSet<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(cat);
            List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
            Set<String> seenThisPass = new HashSet<>();
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                // phone-style launcher entries only for apps with no TV entry
                if (cat.equals(Intent.CATEGORY_LAUNCHER) && leanbackPkgs.contains(pkg)) continue;
                String key = pkg + "/" + ri.activityInfo.name;
                if (!seenThisPass.add(key)) continue;
                CharSequence lab = ri.loadLabel(pm);
                out.add(new AppEntry(pkg, lab == null ? pkg : lab.toString(), ri.activityInfo));
            }
            if (cat.equals(Intent.CATEGORY_LEANBACK_LAUNCHER)) {
                for (AppEntry a : out) leanbackPkgs.add(a.pkg);
            }
        }
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry x, AppEntry y) {
                return x.label.compareToIgnoreCase(y.label);
            }
        });
        return out;
    }

    private String signature(List<AppEntry> apps) {
        StringBuilder sb = new StringBuilder();
        for (AppEntry a : apps) sb.append(a.cn.flattenToShortString()).append('|');
        return sb.toString();
    }

    private void applyRenames() {
        for (AppEntry a : rawApps) {
            a.shown = a.label;
            for (Map.Entry<String, String> e : cfg.rename.entrySet()) {
                String k = e.getKey().toLowerCase(Locale.ROOT).trim();
                if (a.pkg.equalsIgnoreCase(k) || a.label.equalsIgnoreCase(k)) a.shown = e.getValue();
            }
        }
    }

    private boolean isHidden(AppEntry a) {
        for (String h : cfg.hidden) {
            String k = h.toLowerCase(Locale.ROOT).trim();
            if (a.pkg.equalsIgnoreCase(k) || a.label.equalsIgnoreCase(k)) return true;
        }
        return false;
    }

    /** exact package, then exact name, then name contains, then package contains. */
    private static AppEntry find(List<AppEntry> list, String m) {
        String q = m.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return null;
        for (AppEntry a : list) if (a.pkg.equalsIgnoreCase(q)) return a;
        for (AppEntry a : list) if (a.label.equalsIgnoreCase(q)) return a;
        for (AppEntry a : list) if (a.label.toLowerCase(Locale.ROOT).contains(q)) return a;
        for (AppEntry a : list) if (a.pkg.toLowerCase(Locale.ROOT).contains(q)) return a;
        return null;
    }

    private AppEntry findPkg(String pkg) {
        for (AppEntry a : rawApps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    private void launch(AppEntry a) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setComponent(a.cn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Can't open " + a.shown, Toast.LENGTH_SHORT).show();
        }
    }

    /** Long-press: shows the package name and opens app info (uninstall / disable). */
    private void showAppInfo(AppEntry a) {
        Toast.makeText(this, a.pkg, Toast.LENGTH_LONG).show();
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + a.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable ignored) {
            // the toast already showed the name
        }
    }

    private void openSettingsScreen(String action) {
        try {
            Intent i = new Intent(action);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        } catch (Throwable ignored) {
            // try the TV settings app directly
        }
        try {
            Intent i = pm.getLeanbackLaunchIntentForPackage("com.android.tv.settings");
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        Toast.makeText(this, "Can't open settings", Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ actions

    private void activate(Item it) {
        if (it == null) return;
        switch (it.kind) {
            case Item.APP:
                if (it.app != null) launch(it.app);
                break;
            case Item.SHOW:
                openShow(it);
                break;
            case Item.MORE:
            case Item.PILL:
                openApps();
                break;
            case Item.PLAY:
                openGame();
                break;
            case Item.INPUT:
                if (!it.action.isEmpty()) {
                    closeOverlays(false);
                    openSettingsScreen(it.action);
                } else {
                    startInput(it);
                }
                break;
            case Item.INPUTS_TILE:
                openInputs();
                break;
            default:
                break;
        }
    }

    /**
     * Opens a show inside its own app. The link may only be handled by that app (we pin the
     * package), and if the link fails we just open the app.
     */
    private void openShow(Item it) {
        if (it.placeholder) {
            Toast.makeText(this, "Add shows in config.json, or watch something in your apps",
                    Toast.LENGTH_LONG).show();
            return;
        }
        AppEntry a = it.pkg.isEmpty() ? null : findPkg(it.pkg);
        if (a != null && !it.uri.isEmpty()) {
            try {
                Intent i = it.uri.startsWith("intent:")
                        ? Intent.parseUri(it.uri, Intent.URI_INTENT_SCHEME)
                        : new Intent(Intent.ACTION_VIEW, Uri.parse(it.uri));
                i.setComponent(null);
                i.setSelector(null);
                i.setPackage(a.pkg);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Throwable ignored) {
                // fall back to opening the app
            }
        }
        if (a != null) launch(a);
        else Toast.makeText(this, "App not found for " + it.title, Toast.LENGTH_SHORT).show();
    }

    private void startInput(Item it) {
        closeOverlays(false);
        if (it.inputId.isEmpty()) return;
        try {
            Uri u = it.passthrough
                    ? TvContract.buildChannelUriForPassthroughInput(it.inputId)
                    : TvContract.Channels.CONTENT_URI;
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Can't switch to " + it.title, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ overlays

    private List<Item> queryInputs() {
        List<Item> live = new ArrayList<>();
        List<Item> ports = new ArrayList<>();
        try {
            TvInputManager tm = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tm != null) {
                for (TvInputInfo i : tm.getTvInputList()) {
                    if (!i.isPassthroughInput()) {
                        if (i.getType() == TvInputInfo.TYPE_TUNER && live.isEmpty()) {
                            Item it = new Item(Item.INPUT);
                            it.title = "Live TV";
                            it.sub = "Antenna / cable";
                            it.inputId = i.getId();
                            it.passthrough = false;
                            it.artKind = ArtView.LIVE;
                            live.add(it);
                        }
                        continue;
                    }
                    Item it = new Item(Item.INPUT);
                    CharSequence custom = i.loadCustomLabel(this);
                    CharSequence lab = i.loadLabel(this);
                    it.title = custom != null && custom.length() > 0 ? custom.toString()
                            : (lab != null ? lab.toString() : i.getId());
                    it.inputId = i.getId();
                    it.passthrough = true;
                    int type = i.getType();
                    if (type == TvInputInfo.TYPE_HDMI) it.artKind = ArtView.HDMI;
                    else if (type == TvInputInfo.TYPE_COMPOSITE || type == TvInputInfo.TYPE_COMPONENT
                            || type == TvInputInfo.TYPE_SVIDEO || type == TvInputInfo.TYPE_SCART) {
                        it.artKind = ArtView.AV;
                    } else it.artKind = ArtView.OTHER;
                    int st = TvInputManager.INPUT_STATE_DISCONNECTED;
                    try {
                        st = tm.getInputState(i.getId());
                    } catch (Throwable ignored) {
                        // leave as not connected
                    }
                    it.connected = st == TvInputManager.INPUT_STATE_CONNECTED;
                    it.sub = st == TvInputManager.INPUT_STATE_CONNECTED ? "Connected"
                            : (st == TvInputManager.INPUT_STATE_CONNECTED_STANDBY ? "Standby" : "No signal");
                    ports.add(it);
                }
            }
        } catch (Throwable ignored) {
            // an empty list shows "No inputs found"
        }
        Collections.sort(ports, new Comparator<Item>() {
            @Override
            public int compare(Item x, Item y) {
                return x.title.compareToIgnoreCase(y.title);
            }
        });
        List<Item> out = new ArrayList<>(live);
        out.addAll(ports);
        return out;
    }

    private void openSettingsPanel() {
        closeOverlays(false);
        inputItems.clear();
        inputItems.add(settingsItem("Wi-Fi", "NETWORK", ArtView.WIFI_TILE, Settings.ACTION_WIFI_SETTINGS));
        inputItems.add(settingsItem("Display", "SCREEN", ArtView.DISPLAY, Settings.ACTION_DISPLAY_SETTINGS));
        inputItems.add(settingsItem("Sound", "AUDIO", ArtView.SOUND, Settings.ACTION_SOUND_SETTINGS));
        inputItems.add(settingsItem("Bluetooth", "REMOTES", ArtView.BT, Settings.ACTION_BLUETOOTH_SETTINGS));
        inputItems.add(settingsItem("Apps", "MANAGE", ArtView.APPS_TILE, Settings.ACTION_APPLICATION_SETTINGS));
        inputItems.add(settingsItem("All settings", "SYSTEM", ArtView.GEAR_TILE, Settings.ACTION_SETTINGS));
        showPanel("Settings", "Quick settings", 0.125f);
        settingsPanel = true;
    }

    private Item settingsItem(String title, String sub, int art, String action) {
        Item it = new Item(Item.INPUT);
        it.title = title;
        it.sub = sub;
        it.artKind = art;
        it.action = action;
        return it;
    }

    private void openInputs() {
        closeOverlays(false);
        inputItems.clear();
        inputItems.addAll(queryInputs());
        if (inputItems.isEmpty()) {
            Item none = new Item(Item.INPUT);
            none.title = "No inputs found";
            none.sub = "";
            none.artKind = ArtView.OTHER;
            inputItems.add(none);
        }
        showPanel("Inputs", "Choose what to watch", 0.155f);
    }

    /** The full-screen row of big cards shared by Inputs and Settings (items are in inputItems). */
    private void showPanel(String heading, String sub, float cardFrac) {
        FrameLayout layer = new FrameLayout(this);
        layer.setBackgroundColor(0xE6000000);

        TextView title = text(heading, 72 * s, 0xFFFFFFFF, tfThin);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2);
        tl.leftMargin = M;
        tl.topMargin = Math.round(H * 0.2f);
        layer.addView(title, tl);
        TextView hint = text(sub, 28 * s, 0x99FFFFFF, tfLight);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2);
        hl.leftMargin = M;
        hl.topMargin = Math.round(H * 0.2f + 92 * s);
        layer.addView(hint, hl);

        int cw = Math.round(W * cardFrac);
        int ch = Math.round(cw * 0.8f);
        int vpad = Math.round(24 * s);
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rv.setClipChildren(false);
        rv.setClipToPadding(false);
        rv.setPadding(M, vpad, M, vpad);
        rv.setItemAnimator(null);
        rv.setFocusable(false);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setAdapter(new CardAdapter(inputItems, OWN_INPUTS, 0, cw, ch, false));
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(W, ch + 2 * vpad);
        rl.topMargin = Math.round(H * 0.44f);
        layer.addView(rv, rl);

        GlassView g = new GlassView(this, cfg.glassColor, cfg.glassOpacity, Math.max(1.5f, 2f * s));
        layer.addView(g, new FrameLayout.LayoutParams(-1, -1));
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView v, int dx, int dy) {
                glassFollow(v, inputsGlass, inputsLayer);
            }
        });
        root.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        inputsLayer = layer;
        inputsGlass = g;
        inputsRv = rv;
        layer.setAlpha(0f);
        layer.animate().alpha(1f).setDuration(180).start();
        final RecyclerView target = rv;
        layer.post(() -> focusRv(target, 0, 0));
    }

    private void openApps() {
        closeOverlays(false);
        appsItems.clear();
        for (AppEntry a : rawApps) {
            if (isHidden(a)) continue;
            Item it = new Item(Item.APP);
            it.app = a;
            it.pkg = a.pkg;
            it.title = a.shown;
            appsItems.add(it);
        }
        appsCols = cfg.columns;
        int cw = (W - 2 * M - (appsCols - 1) * G) / appsCols;
        int ch = cw * 9 / 16;
        FrameLayout layer = new FrameLayout(this);
        layer.setBackgroundColor(0xF2000000);

        TextView title = text("All apps", 56 * s, 0xFFFFFFFF, tfThin);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2);
        tl.leftMargin = M;
        tl.topMargin = Math.round(60 * s);
        layer.addView(title, tl);

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new GridLayoutManager(this, appsCols));
        rv.setClipChildren(false);
        rv.setClipToPadding(false);
        rv.setPadding(M, Math.round(24 * s), Math.max(0, M - G), Math.round(80 * s));
        rv.setItemAnimator(null);
        rv.setFocusable(false);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setAdapter(new CardAdapter(appsItems, OWN_GRID, 0, cw, ch, true));
        int top = Math.round(160 * s);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(W, H - top);
        rl.topMargin = top;
        layer.addView(rv, rl);

        GlassView g = new GlassView(this, cfg.glassColor, cfg.glassOpacity, Math.max(1.5f, 2f * s));
        layer.addView(g, new FrameLayout.LayoutParams(-1, -1));
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView v, int dx, int dy) {
                glassFollow(v, appsGlass, appsLayer);
            }
        });
        root.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        appsLayer = layer;
        appsGlass = g;
        appsRv = rv;
        layer.setAlpha(0f);
        layer.animate().alpha(1f).setDuration(180).start();
        final RecyclerView target = rv;
        layer.post(() -> focusRv(target, 0, 0));
    }

    private void closeOverlays(boolean refocus) {
        boolean any = inputsLayer != null || appsLayer != null;
        settingsPanel = false;
        if (inputsLayer != null) {
            root.removeView(inputsLayer);
            inputsLayer = null;
            inputsRv = null;
            inputsGlass = null;
        }
        if (appsLayer != null) {
            root.removeView(appsLayer);
            appsLayer = null;
            appsRv = null;
            appsGlass = null;
        }
        if (any && refocus) refocusPage();
    }

    private void openGame() {
        closeOverlays(false);
        if (gameView != null) return;
        gameView = new GameView(this, new GameView.Host() {
            @Override
            public void onExit() {
                closeGame(true);
            }
        });
        root.addView(gameView, new FrameLayout.LayoutParams(-1, -1));
        root.post(() -> {
            if (gameView != null) gameView.requestFocus();
        });
    }

    private void closeGame(boolean refocus) {
        if (gameView == null) return;
        root.removeView(gameView);
        gameView = null;
        if (refocus) refocusPage();
    }

    // ------------------------------------------------------------------ My Shows

    private void submit(Runnable r) {
        try {
            io.execute(r);
        } catch (Throwable ignored) {
            // shutting down
        }
    }

    private void loadShows(final int myGen) {
        final Row row = showsRow;
        if (row == null) return;
        for (Item it : new ArrayList<>(row.items)) startPoster(it, row, myGen);
        if (!cfg.watchNext) return;
        submit(() -> {
            final List<WatchNext.Program> progs = WatchNext.read(MainActivity.this);
            runOnUiThread(() -> {
                if (myGen != gen || isDestroyed()) return;
                applyWatchNext(row, progs, myGen);
            });
        });
    }

    private void applyWatchNext(Row row, List<WatchNext.Program> progs, int myGen) {
        List<Item> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (WatchNext.Program p : progs) {
            if (merged.size() >= Math.min(cfg.maxShows, 4)) break;
            AppEntry a = findPkg(p.pkg);
            if (a == null) continue;
            if (!seen.add(p.title.toLowerCase(Locale.ROOT))) continue;
            Item it = new Item(Item.SHOW);
            it.title = p.title;
            it.pkg = p.pkg;
            it.source = a.shown.toUpperCase(Locale.ROOT);
            it.uri = p.intentUri == null ? "" : p.intentUri;
            it.posterRef = p.posterUri == null ? "" : p.posterUri;
            merged.add(it);
        }
        if (merged.isEmpty()) return; // keep whatever is shown now
        for (Item it : row.items) {
            if (it.placeholder || merged.size() >= Math.min(cfg.maxShows, 4)) continue;
            if (seen.add(it.title.toLowerCase(Locale.ROOT))) merged.add(it);
        }
        row.items.clear();
        row.items.addAll(merged);
        row.adapter.notifyDataSetChanged();
        for (Item it : new ArrayList<>(row.items)) startPoster(it, row, myGen);
    }

    private void startPoster(final Item it, final Row row, final int myGen) {
        if (it.poster != null || it.posterRef.isEmpty()) return;
        final Config c = cfg;
        final int reqW = row.cardW, reqH = row.cardH;
        submit(() -> {
            final Bitmap b = WatchNext.loadPoster(MainActivity.this, c, it.posterRef, reqW, reqH);
            if (b == null) return;
            runOnUiThread(() -> {
                if (myGen != gen || isDestroyed()) return;
                it.poster = b;
                int pos = row.items.indexOf(it);
                if (pos >= 0 && row.adapter != null) row.adapter.notifyItemChanged(pos);
            });
        });
    }

    // ------------------------------------------------------------------ small helpers

    private String greeting() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String g = h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon"
                : h < 22 ? "Good evening" : "Good night";
        return cfg.userName.isEmpty() ? g : g + ", " + cfg.userName;
    }

    private TextView text(String t, float px, int color, Typeface tf) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        v.setTextColor(color);
        v.setTypeface(tf);
        v.setIncludeFontPadding(false);
        return v;
    }

    private GradientDrawable rr(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }
}
