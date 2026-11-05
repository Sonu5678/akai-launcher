package dev.mantel.tv;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;

/** Mantel: a one-shelf Home. This activity owns the layer stack and routes every remote key. */
public class MainActivity extends Activity {
    private static final int REQ_PICK = 41;
    private static final int REQ_EXPORT = 42;
    private static final int REQ_IMPORT = 43;
    private static final long WAKE_GUARD_MS = 1500;

    FrameLayout root;
    WallpaperView wall;
    HomeScreen home;
    final ArrayList<Screen> stack = new ArrayList<Screen>();
    boolean offline = false;

    private AmbientScreen ambient;
    private ValueAnimator ambientAnim;
    private long guardUntil = 0;
    private boolean standbyFlag = false;
    private boolean okHeld = false;
    private boolean okLong = false;
    private boolean resumed = false;

    private ConnectivityManager.NetworkCallback netCb;
    private BroadcastReceiver pkgReceiver;

    private final Runnable okLongRun = new Runnable() {
        @Override public void run() {
            if (okHeld && !okLong) {
                okLong = true;
                Screen t = top();
                if (t != null && !t.closing) t.onLongOk();
            }
        }
    };

    private final Runnable idleRun = new Runnable() {
        @Override public void run() {
            maybeStartAmbient();
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Prefs.init(this);
        Ui.accent = Ui.ACCENTS[Math.max(0, Math.min(3, Prefs.accent()))];

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BASE);
        wall = new WallpaperView(this);
        root.addView(wall, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wall.setDim(Prefs.dim());
        wall.scenes = Prefs.scenes();
        wall.show(Prefs.wallpaper(), false);

        home = new HomeScreen(this);
        root.addView(home, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        stack.add(home);
        setContentView(root);

        IconCache.onLoaded = new Runnable() {
            @Override public void run() {
                invalidateAll();
            }
        };
        refreshApps();
        home.onEnter();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Home pressed while Mantel is already running, or another app returning here.
        goHome();
    }

    @Override protected void onStart() {
        super.onStart();
        registerListeners();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        Ui.refresh(this);
        if (standbyFlag) {
            guardUntil = SystemClock.uptimeMillis() + WAKE_GUARD_MS;   // wake guard
            standbyFlag = false;
        }
        refreshApps();
        home.reloadResume();
        resetIdle();
        invalidateAll();
    }

    @Override protected void onPause() {
        resumed = false;
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) standbyFlag = true;
        } catch (Throwable t) {
            // ignore
        }
        Ui.MAIN.removeCallbacks(idleRun);
        super.onPause();
    }

    @Override protected void onStop() {
        unregisterListeners();
        super.onStop();
    }

    // ------------------------------------------------------------------ keys

    private static boolean isNav(int code) {
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BACK;
    }

    private static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (down) resetIdle();
        if (!isNav(code)) return super.dispatchKeyEvent(e);

        // Ambient mode: the first key only wakes the screen.
        if (ambient != null) {
            if (down && e.getRepeatCount() == 0) wakeFromAmbient();
            return true;
        }
        // Standby wake guard: the first key after standby never selects anything.
        if (guardUntil != 0) {
            if (SystemClock.uptimeMillis() <= guardUntil) {
                if (down) guardUntil = 0;
                return true;
            }
            guardUntil = 0;
        }

        Screen top = top();
        if (top == null) return super.dispatchKeyEvent(e);
        if (top.closing) return true;
        if (!top.wantsKey(code)) return super.dispatchKeyEvent(e);

        if (isOk(code)) {
            if (down) {
                if (e.getRepeatCount() == 0) {
                    okHeld = true;
                    okLong = false;
                    Ui.MAIN.removeCallbacks(okLongRun);
                    Ui.MAIN.postDelayed(okLongRun, 500);
                    top.onOkDown();
                }
            } else {
                Ui.MAIN.removeCallbacks(okLongRun);
                boolean fire = okHeld && !okLong;
                okHeld = false;
                okLong = false;
                if (fire) top.onOk();
            }
            return true;
        }

        if (code == KeyEvent.KEYCODE_BACK) {
            if (down && e.getRepeatCount() == 0) top.onBack();
            return true;
        }

        if (down) {
            // Holding Up or Down must not chain from Home into the Library and beyond.
            if (e.getRepeatCount() > 0 && top == home
                    && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)) {
                return true;
            }
            top.onDir(code);
        }
        return true;
    }

    @Override public void onBackPressed() {
        // Back is handled in dispatchKeyEvent; nothing here, so Back never leaves Mantel.
    }

    // ------------------------------------------------------------------ layer stack

    Screen top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    void push(Screen s) {
        root.addView(s, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        stack.add(s);
        updateSceneDim();
        s.onEnter();
        s.animateIn();
    }

    /** Scenes stay bright on Home and darken behind every screen opened above it. */
    void updateSceneDim() {
        if (wall != null) wall.setSceneDim(top() == home ? 0f : 0.5f);
    }

    void openDeck() {
        push(new DeckScreen(this));
    }

    void pop(final Screen s) {
        if (s == home || s.closing) return;
        s.closing = true;
        s.animateOut(new Runnable() {
            @Override public void run() {
                root.removeView(s);
                stack.remove(s);
                updateSceneDim();
                Screen t = top();
                if (t != null) t.onReveal();
            }
        });
    }

    void popAllInstant() {
        for (int i = stack.size() - 1; i >= 1; i--) {
            Screen s = stack.get(i);
            s.closing = true;
            s.animate().cancel();
            root.removeView(s);
            stack.remove(i);
        }
        updateSceneDim();
    }

    void invalidateAll() {
        wall.invalidate();
        for (Screen s : stack) s.invalidate();
    }

    void goHome() {
        if (ambient != null) wakeFromAmbient();
        popAllInstant();
        home.focusRest();
        resetIdle();
        invalidateAll();
    }

    // ------------------------------------------------------------------ screens

    void openLibrary(boolean hiddenMode) {
        push(new LibraryScreen(this, hiddenMode));
    }

    void openSearch() {
        openSearch(true);
    }

    /** {@code fromHome} is false when the Library's search pill opened it. */
    void openSearch(boolean fromHome) {
        push(new SearchScreen(this, fromHome));
    }

    void openSettings() {
        push(new SettingsScreen(this));
    }

    void openLook(int section) {
        push(new LookScreen(this, section));
    }

    void showDialog(String title, String body, String[] buttons, int focus,
                    DialogOverlay.Listener l) {
        push(new DialogOverlay(this, title, body, buttons, focus, l));
    }

    void showInfo(String title, String body) {
        showDialog(title, body, new String[]{"OK"}, 0, null);
    }

    void showMenu(String[] items, float anchorX, float anchorY, boolean above, MenuOverlay.Listener l) {
        push(new MenuOverlay(this, items, anchorX, anchorY, above, l));
    }

    // ------------------------------------------------------------------ apps

    void refreshApps() {
        AppRepo.refresh(this, new Runnable() {
            @Override public void run() {
                for (Screen s : new ArrayList<Screen>(stack)) s.onAppsChanged();
                invalidateAll();
            }
        });
    }

    /** Launch the app in a shelf slot. Focus is saved to storage first (a 2 GB TV may kill us). */
    void launchPin(int slot) {
        String[] pins = Prefs.pins();
        String[] labels = Prefs.pinLabels();
        if (slot < 0 || slot >= pins.length || pins[slot].length() == 0) return;
        Prefs.setRest(slot);
        AppEntry e = AppRepo.byPkg.get(pins[slot]);
        start(e, pins[slot], labels[slot], slot);
    }

    /** Launch an app from the Library or Search. */
    void launchEntry(AppEntry e) {
        Prefs.pushRecent(e.pkg);
        Prefs.setLibraryFocus(e.pkg);
        start(e, e.pkg, e.label, -1);
    }

    private void start(AppEntry e, final String pkg, final String label, final int slot) {
        Intent i = null;
        if (e != null) {
            i = e.launchIntent();
        } else {
            PackageManager pm = getPackageManager();
            i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        }
        boolean ok = false;
        if (i != null) {
            try {
                startActivity(i);
                ok = true;
            } catch (Throwable t) {
                ok = false;
            }
        }
        if (!ok) failedLaunch(e, pkg, label, slot);
    }

    void startIntent(Intent i) {
        try {
            startActivity(i);
        } catch (Throwable t) {
            showInfo("Couldn't open that", "This TV has no app that can handle it.");
        }
    }

    private void failedLaunch(final AppEntry e, final String pkg, final String label, final int slot) {
        String name = (label == null || label.length() == 0) ? pkg : label;
        String[] btns = slot >= 0 ? new String[]{"Try again", "Remove from shelf"} : new String[]{"Try again", "Close"};
        showDialog("Couldn't open " + name + ".", "It may be disabled or removed.", btns, 0,
                new DialogOverlay.Listener() {
                    @Override public void onChoice(int index) {
                        if (index == 0) {
                            if (slot >= 0) launchPin(slot);
                            else if (e != null) launchEntry(e);
                        } else if (index == 1 && slot >= 0) {
                            home.removeFromShelf(slot);
                        }
                    }
                });
    }

    void openAppInfo(String pkg) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startIntent(i);
    }

    void uninstall(String pkg) {
        Intent i = new Intent(Intent.ACTION_DELETE);
        i.setData(Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startIntent(i);
    }

    void openContentSearch(String query) {
        Intent[] tries = new Intent[3];
        tries[0] = new Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.katniss")
                .putExtra("query", query);
        tries[1] = new Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query);
        tries[2] = new Intent("android.search.action.GLOBAL_SEARCH").putExtra("query", query);
        for (Intent t : tries) {
            t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(t);
                return;
            } catch (Throwable ex) {
                // try the next one
            }
        }
        showInfo("Content search isn't available", "The TV's own search could not be opened from here.");
    }

    void openHomeSettings() {
        String[] actions = {"android.settings.HOME_SETTINGS", Settings.ACTION_SETTINGS};
        for (String a : actions) {
            Intent i = new Intent(a);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(i);
                return;
            } catch (Throwable ex) {
                // next
            }
        }
        showInfo("Settings didn't open", "Open the TV's Settings with the gear key, then Apps.");
    }

    // ------------------------------------------------------------------ home-app checks

    private ResolveInfo resolveHome() {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            return getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        } catch (Throwable t) {
            return null;
        }
    }

    boolean isDefaultHome() {
        ResolveInfo ri = resolveHome();
        return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
    }

    String defaultHomeLabel() {
        ResolveInfo ri = resolveHome();
        if (ri == null || ri.activityInfo == null) return "Unknown";
        if ("android".equals(ri.activityInfo.packageName)) return "None set (Android asks each time)";
        try {
            return ri.loadLabel(getPackageManager()).toString();
        } catch (Throwable t) {
            return ri.activityInfo.packageName;
        }
    }

    // ------------------------------------------------------------------ look (live preview)

    void applyLook() {
        Ui.accent = Ui.ACCENTS[Math.max(0, Math.min(3, Prefs.accent()))];
        wall.setDim(Prefs.dim());
        wall.scenes = Prefs.scenes();
        wall.show(Prefs.wallpaper(), true);
        invalidateAll();
    }

    void previewAccent(int idx) {
        Ui.accent = Ui.ACCENTS[Math.max(0, Math.min(3, idx))];
        invalidateAll();
    }

    void previewDim(int idx) {
        wall.setDim(idx);
    }

    void previewWallpaper(String id) {
        wall.show(id, true);
    }

    boolean pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, REQ_PICK);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ backup and restore

    void startBackupExport() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, BackupStore.FILE_NAME);
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (Throwable t) {
            // No file picker on this TV: use Mantel's own folder instead.
            java.io.File f = BackupStore.fallbackFile(this);
            if (BackupStore.writeFile(f)) {
                showInfo("Settings saved", "This TV has no file picker, so the backup is in Mantel's own folder: "
                        + BackupStore.describe(f));
            } else {
                showInfo("Couldn't save the backup", "Nothing was written. Your settings are unchanged.");
            }
        }
    }

    void startBackupImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (Throwable t) {
            java.io.File f = BackupStore.fallbackFile(this);
            if (!f.isFile()) {
                showInfo("No backup found", "This TV has no file picker, and Mantel's own folder has no backup in it.");
            } else if (BackupStore.readFile(f)) {
                applyImported();
            } else {
                showInfo("Couldn't restore", "That file isn't a Mantel backup. Your settings are unchanged.");
            }
        }
    }

    private void applyImported() {
        applyLook();
        home.onAppsChanged();
        home.reloadResume();
        home.focusRest();
        resetIdle();
        showInfo("Settings restored", "Your shelf, hidden apps and look are back.");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_EXPORT) {
            if (res == RESULT_OK && data != null && data.getData() != null) {
                if (BackupStore.writeTo(this, data.getData())) {
                    showInfo("Settings saved", "Your backup is in the file you chose.");
                } else {
                    showInfo("Couldn't save the backup", "Nothing was written. Your settings are unchanged.");
                }
            }
            return;
        }
        if (req == REQ_IMPORT) {
            if (res == RESULT_OK && data != null && data.getData() != null) {
                if (BackupStore.readFrom(this, data.getData())) {
                    applyImported();
                } else {
                    showInfo("Couldn't restore", "That file isn't a Mantel backup. Your settings are unchanged.");
                }
            }
            return;
        }
        if (req == REQ_PICK && res == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable t) {
                // not persistable; it still works for this session
            }
            for (Screen s : stack) {
                if (s instanceof LookScreen) ((LookScreen) s).onImagePicked(u);
            }
        }
    }

    // ------------------------------------------------------------------ shelf editing

    void startEditShelf(int slot) {
        popAllInstant();
        home.beginEdit(slot);
        invalidateAll();
    }

    // ------------------------------------------------------------------ ambient mode

    void resetIdle() {
        Ui.MAIN.removeCallbacks(idleRun);
        int m = Prefs.ambientMinutes();
        if (resumed && m > 0 && ambient == null) Ui.MAIN.postDelayed(idleRun, m * 60000L);
    }

    private void maybeStartAmbient() {
        if (!resumed || ambient != null) return;
        if (top() != home || home.isEditing()) {
            resetIdle();
            return;
        }
        ambient = new AmbientScreen(this);
        root.addView(ambient, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ambient.setAlpha(0f);
        // Chrome fades out over 1.5 s and the wallpaper dims to 30%.
        final float fromHome = home.getAlpha();
        ambientAnim = ValueAnimator.ofFloat(0f, 1f);
        ambientAnim.setDuration(1500);
        ambientAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float v = (Float) a.getAnimatedValue();
                home.setAlpha(fromHome * (1f - v));
                if (ambient != null) ambient.setAlpha(v);
                wall.ambient = 0.7f * v;
                wall.invalidate();
            }
        });
        ambientAnim.start();
        ambient.begin();
    }

    void wakeFromAmbient() {
        if (ambient == null) return;
        if (ambientAnim != null) ambientAnim.cancel();
        ambient.end();
        root.removeView(ambient);
        ambient = null;
        home.setAlpha(1f);
        wall.ambient = 0f;
        wall.drift = 0f;
        wall.invalidate();
        resetIdle();
    }

    // ------------------------------------------------------------------ listeners

    private boolean hasNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities c = cm.getNetworkCapabilities(n);
            return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable t) {
            return true;
        }
    }

    private void setOffline(boolean v) {
        if (offline == v) return;
        offline = v;
        home.invalidate();
    }

    private void registerListeners() {
        setOffline(!hasNetwork());
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && netCb == null) {
                netCb = new ConnectivityManager.NetworkCallback() {
                    @Override public void onAvailable(Network n) {
                        Ui.MAIN.post(new Runnable() {
                            @Override public void run() {
                                setOffline(!hasNetwork());
                            }
                        });
                    }

                    @Override public void onLost(Network n) {
                        Ui.MAIN.post(new Runnable() {
                            @Override public void run() {
                                setOffline(!hasNetwork());
                            }
                        });
                    }
                };
                cm.registerDefaultNetworkCallback(netCb);
            }
        } catch (Throwable t) {
            netCb = null;
        }
        if (pkgReceiver == null) {
            pkgReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    refreshApps();
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_PACKAGE_ADDED);
            f.addAction(Intent.ACTION_PACKAGE_REMOVED);
            f.addAction(Intent.ACTION_PACKAGE_REPLACED);
            f.addAction(Intent.ACTION_PACKAGE_CHANGED);
            f.addDataScheme("package");
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
                } else {
                    registerReceiver(pkgReceiver, f);
                }
            } catch (Throwable t) {
                pkgReceiver = null;
            }
        }
    }

    private void unregisterListeners() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && netCb != null) cm.unregisterNetworkCallback(netCb);
        } catch (Throwable t) {
            // ignore
        }
        netCb = null;
        try {
            if (pkgReceiver != null) unregisterReceiver(pkgReceiver);
        } catch (Throwable t) {
            // ignore
        }
        pkgReceiver = null;
    }
}
