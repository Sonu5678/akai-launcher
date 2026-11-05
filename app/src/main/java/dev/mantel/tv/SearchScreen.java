package dev.mantel.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Search (section 8): find an installed app fast, and hand content searches to the system.
 * The field is a real text field so the system on-screen keyboard (and its mic key) works.
 * Results update as you type, in the Library's icon grid; no network is used.
 */
final class SearchScreen extends Screen {
    private static final int Z_FIELD = 0, Z_GRID = 1, Z_CONTENT = 2;
    private static final int COLS = 9;

    private final boolean fromHome;
    private EditText edit;
    private String query = "";
    private List<AppEntry> results = new ArrayList<AppEntry>();
    private int zone = Z_FIELD;
    private int idx = 0;
    private int scrollRow = 0;

    private final Tween scrollTw = new Tween(0f);
    private final Tween scaleTw = new Tween(1f);
    private final Tween nudgeY = new Tween(0f);

    SearchScreen(MainActivity a, boolean fromHome) {
        super(a);
        this.fromHome = fromHome;

        edit = new EditText(a);
        edit.setBackground(null);
        edit.setTextColor(Ui.TEXT);
        edit.setHintTextColor(Ui.TEXT2);
        edit.setHint("Search apps");
        edit.setTypeface(Ui.regular);
        edit.setSingleLine(true);
        edit.setPadding(0, 0, 0, 0);
        edit.setGravity(Gravity.CENTER_VERTICAL);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        edit.setFocusable(true);
        edit.setFocusableInTouchMode(true);
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}

            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}

            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
                refresh();
            }
        });
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent ev) {
                hideIme();
                moveToResults();
                return true;
            }
        });
        addView(edit, new FrameLayout.LayoutParams(100, 100));
    }

    @Override int enterFrom() {
        return -1;
    }

    @Override boolean wantsKey(int key) {
        if (zone != Z_FIELD) return true;
        // In the field, arrows and OK belong to the text field and the keyboard.
        return key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layoutField(w, h);
    }

    private void layoutField(int w, int h) {
        if (w <= 0 || h <= 0) return;
        float s = Math.min(w / 1920f, h / 1080f);
        float ox = (w - 1920 * s) / 2f;
        float oy = (h - 1080 * s) / 2f;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Math.round((960f - 64f - 32f) * s), Math.round(80f * s));
        lp.leftMargin = Math.round(ox + (120f + 80f) * s);
        lp.topMargin = Math.round(oy + 160f * s);
        edit.setLayoutParams(lp);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32f * s);
    }

    @Override void onEnter() {
        refresh();
        postDelayed(new Runnable() {
            @Override public void run() {
                if (zone == Z_FIELD && !closing) {
                    edit.requestFocus();
                    showIme();
                }
            }
        }, 300);
    }

    @Override void onAppsChanged() {
        refresh();
    }

    private void showIme() {
        try {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(edit, 0);
        } catch (Throwable t) {
            // the keyboard is the system's; nothing more to do
        }
    }

    private void hideIme() {
        try {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
        } catch (Throwable t) {
            // ignore
        }
    }

    private void setZone(int z) {
        zone = z;
        boolean field = z == Z_FIELD;
        edit.setFocusable(field);
        edit.setFocusableInTouchMode(field);
        if (field) edit.requestFocus();
        else {
            edit.clearFocus();
            hideIme();
        }
        scaleTw.set(1f);
        scaleTw.go(1.08f, Ui.dur(140));
        if (zone == Z_GRID && idx < results.size()) speak(results.get(idx).label);
        invalidate();
    }

    private void refresh() {
        results = AppRepo.search(query);
        idx = 0;
        scrollRow = 0;
        scrollTw.set(0f);
        invalidate();
    }

    private void moveToResults() {
        if (!results.isEmpty()) setZone(Z_GRID);
        else setZone(Z_CONTENT);
    }

    private int rows() {
        return (results.size() + COLS - 1) / COLS;
    }

    // ------------------------------------------------------------------ keys

    @Override void onDir(int key) {
        if (zone == Z_FIELD) {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                hideIme();
                moveToResults();
            }
            return;
        }
        if (zone == Z_CONTENT) {
            if (key == KeyEvent.KEYCODE_DPAD_UP) {
                if (!results.isEmpty()) setZone(Z_GRID);
                else setZone(Z_FIELD);
            } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                nudge(nudgeY, 6f);
            }
            return;
        }
        int row = idx / COLS;
        int col = idx % COLS;
        if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (col > 0) move(idx - 1);
        } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (col < COLS - 1 && idx + 1 < results.size()) move(idx + 1);
        } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
            if (row == 0) setZone(Z_FIELD);
            else move(idx - COLS);
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            int n = idx + COLS;
            if (n < results.size()) move(n);
            else if (row + 1 < rows()) move(results.size() - 1);
            else setZone(Z_CONTENT);
        }
    }

    private void move(int n) {
        idx = n;
        int row = idx / COLS;
        if (row < scrollRow) scrollRow = row;
        else if (row > scrollRow + 2) scrollRow = row - 2;
        scrollTw.go(scrollRow, Ui.dur(180));
        scaleTw.set(1f);
        scaleTw.go(1.08f, Ui.dur(140));
        speak(results.get(idx).label);
        invalidate();
    }

    @Override void onOk() {
        if (zone == Z_GRID && idx < results.size()) {
            hideIme();
            act.launchEntry(results.get(idx));
        } else if (zone == Z_CONTENT) {
            act.openContentSearch(query);
        }
    }

    @Override void onBack() {
        hideIme();
        if (fromHome) act.home.focusRail(0);
        act.pop(this);
    }

    // ------------------------------------------------------------------ drawing

    @Override void draw1(Canvas c, long now) {
        tw(scrollTw, now);
        tw(scaleTw, now);
        tw(nudgeY, now);

        Ui.fillAll(c, Ui.SHEET);
        Ui.text(c, "Search", 120f, 126f, 56f, Ui.medium, Ui.TEXT, Paint.Align.LEFT);

        boolean ff = zone == Z_FIELD;
        Ui.rr(c, 120f, 160f, 1080f, 240f, 40f, Ui.SURFACE1);
        if (ff) Ui.ring(c, 120f, 160f, 1080f, 240f, 40f, 3f, Ui.TEXT);
        Glyphs.draw(c, Glyphs.SEARCH, 164f, 200f, 36f, 2.8f, ff ? Ui.TEXT : Ui.TEXT2);

        // results grid
        if (results.isEmpty() && query.length() > 0) {
            Ui.text(c, "No app named ‘" + Ui.fit(query, 700f, 32f, Ui.regular) + "’", 960f, 520f,
                    32f, Ui.regular, Ui.TEXT2, Paint.Align.CENTER);
        } else {
            c.save();
            c.clipRect(0f, 264f, 1920f, 900f);
            int total = results.size();
            for (int i = 0; i < total; i++) {
                int row = i / COLS;
                float y = 280f + (row - scrollTw.cur) * 216f;
                if (y > 900f || y + 216f < 264f) continue;
                if (zone == Z_GRID && i == idx) continue;
                drawCell(c, i, 120f + (i % COLS) * 192f, y, false);
            }
            if (zone == Z_GRID && idx < total) {
                float y = 280f + ((idx / COLS) - scrollTw.cur) * 216f;
                drawCell(c, idx, 120f + (idx % COLS) * 192f, y, true);
            }
            c.restore();
        }

        // last row: hand content searches to the system
        boolean cf = zone == Z_CONTENT;
        float ny = cf ? nudgeY.cur : 0f;
        Ui.rr(c, 120f, 912f + ny, 1800f, 1008f + ny, 20f, cf ? Ui.SURFACE2 : Ui.SURFACE1);
        if (cf) Ui.ring(c, 120f, 912f + ny, 1800f, 1008f + ny, 20f, 3f, Ui.TEXT);
        Glyphs.draw(c, Glyphs.SEARCH, 176f, 960f + ny, 40f, 3f, cf ? Ui.TEXT : Ui.TEXT2);
        Ui.text(c, "Search content in Google TV", 224f, 972f + ny, 32f, Ui.regular,
                cf ? Ui.TEXT : Ui.TEXT2, Paint.Align.LEFT);
    }

    private void drawCell(Canvas c, int i, float x, float y, boolean focused) {
        AppEntry e = results.get(i);
        float sc = focused ? scaleTw.cur : 1f;
        float cx = x + 72f;
        float cy = y + 72f;
        c.save();
        c.translate(cx, cy);
        c.scale(sc, sc);
        TileArt.draw(c, act, e, 0f, 0f, 144f);
        c.restore();
        if (focused) {
            float half = 72f * sc;
            Ui.ring(c, cx - half - 5.5f, cy - half - 5.5f, cx + half + 5.5f, cy + half + 5.5f,
                    30f * sc + 5.5f, 3f, Ui.TEXT);
        }
        Ui.text(c, Ui.fit(e.label, 184f, 26f, focused ? Ui.medium : Ui.regular), x + 72f, y + 182f, 26f,
                focused ? Ui.medium : Ui.regular, focused ? Ui.TEXT : Ui.TEXT2, Paint.Align.CENTER);
    }
}
