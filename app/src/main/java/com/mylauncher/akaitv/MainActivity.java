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
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
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
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Apple TV style home screen: top bar (greeting, clock, pills), a dock of
 * four apps, and a 4-column grid that slides up when you press Down.
 * Everything is built in code so there are no layout files to maintain.
 */
public class MainActivity extends Activity {
    private static final int Z_TOP = 0, Z_DOCK = 1, Z_GRID = 2;

    private SharedPreferences prefs;
    private PackageManager pm;
    private Config cfg;
    private long cfgStamp;
    private String sig = "";

    private FrameLayout root, page, topLayer;
    private GlassView glass;
    private RecyclerView grid;
    private LinearLayout dropdown;
    private final List<TextView> dropdownItems = new ArrayList<>();
    private TextView dockName, greetingView, wifiPill;
    private View glow;
    private GradientDrawable glowDrawable;
    private int glowColor = 0;
    private int glowSize;
    private ValueAnimator glowAnim;

    private final List<AppEntry> dockApps = new ArrayList<>();
    private final List<AppEntry> gridApps = new ArrayList<>();
    private final List<View> dockViews = new ArrayList<>();
    private final List<TextView> pills = new ArrayList<>();
    private int[] dockColors = new int[0];

    private Typeface tfThin, tfLight;
    private int W, H, M, G, tileW, tileH, cellH, labelH, pad, dockY, bandH, gridTop, shift;
    private float s;
    private int zone = -1;
    private int lastDock = 0;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        pm = getPackageManager();
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        root = new FrameLayout(this);
        root.setClipChildren(false);
        setContentView(root);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean rebuild = false;
        try {
            if (cfg == null || cfg.stamp() != cfgStamp) rebuild = true;
            else if (!signature(queryApps()).equals(sig)) rebuild = true;
        } catch (Throwable t) {
            rebuild = false;
        }
        if (rebuild) {
            build();
        } else {
            if (greetingView != null) greetingView.setText(greeting());
            if (wifiPill != null) wifiPill.setText(wifiLabel());
            if (getCurrentFocus() == null) restoreFocus();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while we are already showing: go back to the top.
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            closeDropdown(false);
            if (grid != null) grid.scrollToPosition(0);
            if (!dockViews.isEmpty()) focusDock(0);
        }
    }

    @Override
    public void onBackPressed() {
        if (dropdown != null && dropdown.getVisibility() == View.VISIBLE) {
            closeDropdown(true);
        } else if (zone == Z_GRID) {
            focusDock(lastDock);
        } else if (zone == Z_TOP && !dockViews.isEmpty()) {
            focusDock(lastDock);
        }
        // never call super: a launcher has nowhere to go back to
    }

    // ------------------------------------------------------------------ build

    private void build() {
        cfg = Config.load(this);
        cfgStamp = cfg.stamp();
        if (cfg.error != null) Toast.makeText(this, cfg.error, Toast.LENGTH_LONG).show();

        List<AppEntry> raw = queryApps();
        sig = signature(raw);
        partition(raw);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        W = dm.widthPixels;
        H = dm.heightPixels;
        s = H / 1080f;
        M = Math.round(W * 0.075f);
        G = Math.round(W * 0.012f);
        int cols = cfg.columns;
        tileW = (W - 2 * M - (cols - 1) * G) / cols;
        tileH = tileW * 9 / 16;
        labelH = Math.round(46 * s);
        cellH = tileH + labelH + Math.round(26 * s);
        pad = Math.round(28 * s);
        bandH = tileH + 2 * pad;
        dockY = Math.round(H * 0.665f);
        gridTop = dockY + bandH + Math.round(60 * s);
        int topPad = Math.round(56 * s);
        shift = gridTop - topPad;
        int gridH = H - topPad;

        tfThin = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        tfLight = Typeface.create("sans-serif-light", Typeface.NORMAL);
        File ff = cfg.find(cfg.font);
        if (ff != null) {
            try {
                Typeface t = Typeface.createFromFile(ff);
                tfThin = t;
                tfLight = t;
            } catch (Throwable ignored) {
                // keep system fonts
            }
        }

        root.removeAllViews();
        zone = -1;
        dockViews.clear();
        pills.clear();
        dropdownItems.clear();
        root.setBackgroundColor(cfg.background);

        addWallpaper();

        page = new FrameLayout(this);
        page.setClipChildren(false);
        root.addView(page, new FrameLayout.LayoutParams(W, gridTop + gridH));

        topLayer = new FrameLayout(this);
        topLayer.setClipChildren(false);
        page.addView(topLayer, new FrameLayout.LayoutParams(W, H));

        buildGlowAndName();
        buildDockBand();
        buildHeader();
        buildPills();
        buildDockTiles();
        buildGrid(gridH);
        buildDropdown();

        glass = new GlassView(this, cfg.glassColor, cfg.glassOpacity, Math.max(1.5f, 2f * s));
        page.addView(glass, new FrameLayout.LayoutParams(W, gridTop + gridH));

        wireNavigation();
        root.post(this::restoreFocus);
    }

    private void addWallpaper() {
        File wf = cfg.find(cfg.wallpaper);
        if (wf == null) return;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(wf.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= W && o.outHeight / (sample * 2) >= H) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeFile(wf.getPath(), o);
            if (bmp == null) return;
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(bmp);
            root.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            View scrim = new View(this);
            scrim.setBackgroundColor(0x66000000);
            root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        } catch (Throwable ignored) {
            // no wallpaper then
        }
    }

    private void buildGlowAndName() {
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
        gl.leftMargin = 0;
        gl.topMargin = dockY - glowSize / 2;
        topLayer.addView(glow, gl);

        dockName = text("", 70 * s, 0xFFFFFFFF, tfThin);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2);
        nl.leftMargin = M;
        nl.topMargin = dockY - Math.round(128 * s);
        topLayer.addView(dockName, nl);
    }

    private void buildDockBand() {
        int n = dockApps.size();
        if (n == 0) return;
        int bandW = n * tileW + (n - 1) * G + 2 * pad;
        View band = new View(this);
        band.setBackground(rr(0x1FFFFFFF, 44 * s));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(bandW, bandH);
        bl.leftMargin = M - pad;
        bl.topMargin = dockY;
        topLayer.addView(band, bl);
    }

    private void buildHeader() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        if (cfg.showGreeting) {
            greetingView = text(greeting(), 28 * s, 0xB3FFFFFF, tfLight);
            col.addView(greetingView);
        } else {
            greetingView = null;
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
        topLayer.addView(col, lp);
    }

    private void buildPills() {
        int pillH = Math.round(62 * s);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        String[] names = {"Inputs", "Wi-Fi", "Settings"};
        for (int i = 0; i < names.length; i++) {
            TextView p = text(names[i], 25 * s, 0xFFFFFFFF, tfLight);
            if (i == 1) {
                p.setText(wifiLabel());
                wifiPill = p;
            }
            p.setGravity(Gravity.CENTER);
            int ph = Math.round(30 * s);
            p.setPadding(ph, 0, ph, 0);
            p.setBackground(rr(0x33FFFFFF, pillH / 2f));
            p.setFocusable(true);
            p.setClickable(true);
            p.setId(View.generateViewId());
            final int idx = i;
            p.setOnFocusChangeListener((v, has) -> {
                if (has) {
                    setZone(Z_TOP);
                    moveGlass(v, true);
                }
            });
            p.setOnClickListener(v -> {
                if (idx == 0) openInputs(v);
                else if (idx == 1) openSettingsScreen(Settings.ACTION_WIFI_SETTINGS);
                else openSettingsScreen(Settings.ACTION_SETTINGS);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, pillH);
            if (i > 0) lp.leftMargin = Math.round(16 * s);
            row.addView(p, lp);
            pills.add(p);
        }
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-2, pillH, Gravity.TOP | Gravity.END);
        rl.rightMargin = M;
        rl.topMargin = Math.round(58 * s);
        topLayer.addView(row, rl);
    }

    private void buildDockTiles() {
        int n = dockApps.size();
        dockColors = new int[n];
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final AppEntry a = dockApps.get(i);
            FrameLayout card = makeCard();
            bindCard(card, a);
            card.setId(View.generateViewId());
            card.setFocusable(true);
            card.setClickable(true);
            Drawable art = a.banner(pm);
            if (art == null) art = a.icon(pm);
            dockColors[i] = dominant(art);

            card.setOnClickListener(v -> launch(a));
            card.setOnLongClickListener(v -> {
                showAppInfo(a);
                return true;
            });
            card.setOnFocusChangeListener((v, has) -> {
                if (has) {
                    lastDock = idx;
                    setZone(Z_DOCK);
                    saveFocus("dock", idx);
                    v.animate().scaleX(cfg.focusScale).scaleY(cfg.focusScale).setDuration(160).start();
                    moveGlass(v, true);
                    dockName.setText(a.shown);
                    animateGlow(dockColors[idx], M + idx * (tileW + G) + tileW / 2);
                } else {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                }
            });
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(tileW, tileH);
            lp.leftMargin = M + i * (tileW + G);
            lp.topMargin = dockY + pad;
            topLayer.addView(card, lp);
            dockViews.add(card);
        }
    }

    private void buildGrid(int gridH) {
        grid = new RecyclerView(this);
        grid.setLayoutManager(new GridLayoutManager(this, cfg.columns));
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(M, Math.round(24 * s), Math.max(0, M - G), Math.round(60 * s));
        grid.setItemAnimator(null);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setFocusable(false);
        grid.setAlpha(0f);
        grid.setAdapter(new GridAdapter());
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                View f = getCurrentFocus();
                if (f != null && f.getTag() instanceof Cell) moveGlass(f, false);
            }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(W, gridH);
        lp.topMargin = gridTop;
        page.addView(grid, lp);
    }

    private void buildDropdown() {
        dropdown = new LinearLayout(this);
        dropdown.setOrientation(LinearLayout.VERTICAL);
        dropdown.setBackground(rr(0xF21C1C1E, 30 * s));
        int p = Math.round(12 * s);
        dropdown.setPadding(p, p, p, p);
        dropdown.setVisibility(View.GONE);
        page.addView(dropdown, new FrameLayout.LayoutParams(Math.round(380 * s), -2));
    }

    /** Fixed D-pad rules for the top bar and the dock. The grid has its own in the adapter. */
    private void wireNavigation() {
        int n = pills.size();
        for (int i = 0; i < n; i++) {
            TextView p = pills.get(i);
            p.setNextFocusUpId(p.getId());
            p.setNextFocusLeftId(i > 0 ? pills.get(i - 1).getId() : p.getId());
            p.setNextFocusRightId(i < n - 1 ? pills.get(i + 1).getId() : p.getId());
            p.setOnKeyListener((v, key, e) -> {
                if (e.getAction() == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (!dockViews.isEmpty()) focusDock(lastDock);
                    else if (!gridApps.isEmpty()) focusGrid(0, 0);
                    return true;
                }
                return false;
            });
        }
        int d = dockViews.size();
        for (int i = 0; i < d; i++) {
            final int idx = i;
            View v = dockViews.get(i);
            v.setNextFocusLeftId(i > 0 ? dockViews.get(i - 1).getId() : v.getId());
            v.setNextFocusRightId(i < d - 1 ? dockViews.get(i + 1).getId() : v.getId());
            v.setNextFocusUpId(pills.get(0).getId());
            v.setOnKeyListener((view, key, e) -> {
                if (e.getAction() == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (!gridApps.isEmpty()) focusGrid(Math.min(idx, gridApps.size() - 1), 0);
                    return true;
                }
                return false;
            });
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

    private void partition(List<AppEntry> raw) {
        dockApps.clear();
        gridApps.clear();
        for (AppEntry a : raw) {
            a.shown = a.label;
            for (Map.Entry<String, String> e : cfg.rename.entrySet()) {
                if (a.matches(e.getKey().toLowerCase(Locale.ROOT).trim())) a.shown = e.getValue();
            }
        }
        for (List<String> options : cfg.dock) {
            AppEntry hit = null;
            for (String m : options) {
                hit = find(raw, m);
                if (hit != null) break;
            }
            if (hit != null && !dockApps.contains(hit)) dockApps.add(hit);
        }
        List<AppEntry> rest = new ArrayList<>();
        for (AppEntry a : raw) {
            if (!cfg.showDockAppsInGrid && dockApps.contains(a)) continue;
            boolean hide = false;
            for (String h : cfg.hidden) {
                if (a.matches(h.toLowerCase(Locale.ROOT).trim())) hide = true;
            }
            if (!hide) rest.add(a);
        }
        for (String p : cfg.pinned) {
            AppEntry hit = find(rest, p);
            if (hit != null) {
                rest.remove(hit);
                gridApps.add(hit);
            }
        }
        gridApps.addAll(rest);
    }

    /** exact package, then exact name, then "contains". */
    private static AppEntry find(List<AppEntry> list, String m) {
        String q = m.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return null;
        for (AppEntry a : list) if (a.pkg.equalsIgnoreCase(q)) return a;
        for (AppEntry a : list) if (a.label.equalsIgnoreCase(q)) return a;
        for (AppEntry a : list) if (a.matches(q)) return a;
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

    // ------------------------------------------------------------------ grid

    private final class Cell extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final TextView label;

        Cell(FrameLayout cell, FrameLayout card, TextView label) {
            super(cell);
            this.card = card;
            this.label = label;
        }
    }

    private final class GridAdapter extends RecyclerView.Adapter<Cell> {
        @Override
        public Cell onCreateViewHolder(ViewGroup parent, int type) {
            FrameLayout cell = new FrameLayout(MainActivity.this);
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, cellH));
            cell.setClipChildren(false);
            cell.setFocusable(true);
            cell.setClickable(true);

            FrameLayout card = makeCard();
            cell.addView(card, new FrameLayout.LayoutParams(tileW, tileH));

            TextView label = text("", 24 * s, 0xFFFFFFFF, tfLight);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setAlpha(0f);
            FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(tileW, labelH);
            ll.topMargin = tileH + Math.round(14 * s);
            cell.addView(label, ll);

            final Cell h = new Cell(cell, card, label);
            cell.setTag(h);

            cell.setOnFocusChangeListener((v, has) -> {
                if (has) {
                    setZone(Z_GRID);
                    int pos = h.getBindingAdapterPosition();
                    if (pos >= 0) saveFocus("grid", pos);
                    h.card.animate().scaleX(cfg.focusScale).scaleY(cfg.focusScale).setDuration(160).start();
                    h.label.animate().alpha(1f).setDuration(160).start();
                    moveGlass(v, true);
                } else {
                    h.card.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                    h.label.animate().alpha(0f).setDuration(120).start();
                }
            });
            cell.setOnKeyListener((v, key, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                int pos = h.getBindingAdapterPosition();
                if (pos < 0) return false;
                int cols = cfg.columns;
                int n = gridApps.size();
                int col = pos % cols;
                switch (key) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if (col > 0) focusGrid(pos - 1, 0);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (col < cols - 1 && pos + 1 < n) focusGrid(pos + 1, 0);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (pos + cols < n) focusGrid(pos + cols, 0);
                        else if (pos / cols < (n - 1) / cols) focusGrid(n - 1, 0);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (pos >= cols) focusGrid(pos - cols, 0);
                        else if (!dockViews.isEmpty()) focusDock(Math.min(col, dockViews.size() - 1));
                        else pills.get(0).requestFocus();
                        return true;
                    default:
                        return false;
                }
            });
            return h;
        }

        @Override
        public void onBindViewHolder(Cell h, int pos) {
            final AppEntry a = gridApps.get(pos);
            bindCard(h.card, a);
            h.label.setText(a.shown);
            h.itemView.setOnClickListener(v -> launch(a));
            h.itemView.setOnLongClickListener(v -> {
                showAppInfo(a);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return gridApps.size();
        }
    }

    /** Moves focus to a grid item, scrolling it into view first if it is not on screen. */
    private void focusGrid(final int pos, final int attempt) {
        if (grid == null || pos < 0 || pos >= gridApps.size()) return;
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            vh.itemView.requestFocus();
            return;
        }
        if (attempt > 8) return;
        grid.scrollToPosition(pos);
        grid.postDelayed(() -> focusGrid(pos, attempt + 1), 60);
    }

    private void focusDock(int i) {
        if (i < 0 || i >= dockViews.size()) return;
        dockViews.get(i).requestFocus();
    }

    private void restoreFocus() {
        String z = prefs.getString("z", "dock");
        int i = prefs.getInt("i", 0);
        if ("grid".equals(z) && i < gridApps.size()) {
            focusGrid(i, 0);
        } else if (!dockViews.isEmpty()) {
            focusDock(Math.min(i, dockViews.size() - 1));
        } else if (!gridApps.isEmpty()) {
            focusGrid(0, 0);
        } else if (!pills.isEmpty()) {
            pills.get(0).requestFocus();
        }
    }

    private void saveFocus(String z, int i) {
        prefs.edit().putString("z", z).putInt("i", i).apply();
    }

    // ------------------------------------------------------------------ zones, glass, glow

    private void setZone(int z) {
        if (z == zone || page == null) return;
        zone = z;
        boolean g = z == Z_GRID;
        page.animate().translationY(g ? -shift : 0).setDuration(320)
                .setInterpolator(new DecelerateInterpolator()).start();
        topLayer.animate().alpha(g ? 0f : 1f).setDuration(220).start();
        grid.animate().alpha(g ? 1f : 0f).setDuration(220).start();
    }

    private void moveGlass(View v, boolean animate) {
        if (glass == null || page == null) return;
        float m = 5 * s;
        RectF r;
        float rad;
        Object tag = v.getTag();
        if (tag instanceof Cell) {
            r = boundsIn(((Cell) tag).card, page);
            grow(r, cfg.focusScale);
            r.inset(-m, -m);
            rad = cfg.corner * s * cfg.focusScale + m;
        } else if (dockViews.contains(v)) {
            r = boundsIn(v, page);
            grow(r, cfg.focusScale);
            r.inset(-m, -m);
            rad = cfg.corner * s * cfg.focusScale + m;
        } else {
            r = boundsIn(v, page);
            rad = tag instanceof Float ? (Float) tag : r.height() / 2f;
        }
        glass.moveTo(r, rad, animate);
    }

    private static RectF boundsIn(View v, View ancestor) {
        float x = 0, y = 0;
        View c = v;
        while (c != null && c != ancestor) {
            x += c.getLeft() + c.getTranslationX();
            y += c.getTop() + c.getTranslationY();
            if (!(c.getParent() instanceof View)) break;
            c = (View) c.getParent();
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
        int target = (0x6B << 24) | (rgb & 0x00FFFFFF);
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

    // ------------------------------------------------------------------ inputs dropdown

    private static final class InputItem {
        final String id;
        final String label;
        final boolean passthrough;

        InputItem(String id, String label, boolean passthrough) {
            this.id = id;
            this.label = label;
            this.passthrough = passthrough;
        }
    }

    private List<InputItem> queryInputs() {
        List<InputItem> live = new ArrayList<>();
        List<InputItem> ports = new ArrayList<>();
        try {
            TvInputManager tm = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tm != null) {
                for (TvInputInfo i : tm.getTvInputList()) {
                    boolean pass = i.isPassthroughInput();
                    if (!pass && i.getType() != TvInputInfo.TYPE_TUNER) continue;
                    if (!pass) {
                        if (live.isEmpty()) live.add(new InputItem(i.getId(), "Live TV", false));
                        continue;
                    }
                    CharSequence custom = i.loadCustomLabel(this);
                    CharSequence lab = i.loadLabel(this);
                    String label = custom != null && custom.length() > 0 ? custom.toString()
                            : lab != null ? lab.toString() : i.getId();
                    ports.add(new InputItem(i.getId(), label, true));
                }
            }
        } catch (Throwable ignored) {
            // empty list shows "No inputs found"
        }
        Collections.sort(ports, new Comparator<InputItem>() {
            @Override
            public int compare(InputItem x, InputItem y) {
                return x.label.compareToIgnoreCase(y.label);
            }
        });
        List<InputItem> out = new ArrayList<>(live);
        out.addAll(ports);
        return out;
    }

    private void openInputs(View anchor) {
        dropdown.removeAllViews();
        dropdownItems.clear();
        List<InputItem> items = queryInputs();
        if (items.isEmpty()) items.add(new InputItem("", "No inputs found", false));
        for (int i = 0; i < items.size(); i++) {
            final InputItem it = items.get(i);
            final int idx = i;
            TextView t = text(it.label, 26 * s, 0xFFFFFFFF, tfLight);
            int ph = Math.round(24 * s), pv = Math.round(16 * s);
            t.setPadding(ph, pv, ph, pv);
            t.setSingleLine(true);
            t.setFocusable(true);
            t.setClickable(true);
            t.setTag(Float.valueOf(18 * s));
            t.setOnFocusChangeListener((v, has) -> {
                if (has) moveGlass(v, true);
            });
            t.setOnClickListener(v -> {
                closeDropdown(false);
                startInput(it);
            });
            t.setOnKeyListener((v, key, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                switch (key) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (idx > 0) dropdownItems.get(idx - 1).requestFocus();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (idx < dropdownItems.size() - 1) dropdownItems.get(idx + 1).requestFocus();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        return true;
                    default:
                        return false;
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) lp.topMargin = Math.round(4 * s);
            dropdown.addView(t, lp);
            dropdownItems.add(t);
        }
        RectF a = boundsIn(anchor, page);
        int w = Math.round(380 * s);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) dropdown.getLayoutParams();
        lp.leftMargin = Math.round(Math.max(M, Math.min(a.left, W - M - w)));
        lp.topMargin = Math.round(a.bottom + 12 * s);
        dropdown.setLayoutParams(lp);
        dropdown.setVisibility(View.VISIBLE);
        dropdownItems.get(0).post(() -> dropdownItems.get(0).requestFocus());
    }

    private void closeDropdown(boolean refocus) {
        if (dropdown == null || dropdown.getVisibility() != View.VISIBLE) return;
        dropdown.setVisibility(View.GONE);
        dropdownItems.clear();
        dropdown.removeAllViews();
        if (refocus && !pills.isEmpty()) pills.get(0).requestFocus();
    }

    private void startInput(InputItem it) {
        if (it.id.isEmpty()) return;
        try {
            Uri u = it.passthrough
                    ? TvContract.buildChannelUriForPassthroughInput(it.id)
                    : TvContract.Channels.CONTENT_URI;
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Can't switch to " + it.label, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ small helpers

    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g = h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon"
                : h < 22 ? "Good evening" : "Good night";
        return cfg.userName.isEmpty() ? g : g + ", " + cfg.userName;
    }

    private String wifiLabel() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc == null) return "Offline";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            String ssid = wi == null ? null : wi.getSSID();
            if (ssid != null && !ssid.contains("unknown ssid") && !ssid.equals("0x")) {
                if (ssid.length() > 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid = ssid.substring(1, ssid.length() - 1);
                }
                return ssid;
            }
        } catch (Throwable ignored) {
            // plain label
        }
        return "Wi-Fi";
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

    /** A rounded 16:9 tile holding the banner (or icon). */
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
        ImageView img = new ImageView(this);
        card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        return card;
    }

    private void bindCard(FrameLayout card, AppEntry a) {
        ImageView img = (ImageView) card.getChildAt(0);
        Drawable b = a.banner(pm);
        if (b != null) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setPadding(0, 0, 0, 0);
            img.setImageDrawable(b);
        } else {
            int p = Math.round(tileH * 0.16f);
            img.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            img.setPadding(p, p, p, p);
            img.setImageDrawable(a.icon(pm));
        }
    }
}
