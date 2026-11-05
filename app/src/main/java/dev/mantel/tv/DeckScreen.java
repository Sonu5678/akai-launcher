package dev.mantel.tv;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.provider.Settings;
import android.view.KeyEvent;

/**
 * The Control Deck: a sheet that drops from the top with the switches Mantel can really flip
 * (look, motion, mood) and shortcuts into the TV's own Wi-Fi, sound and display panels.
 * Screen off, HDMI input and picture modes are not offered: Android does not let a launcher do them.
 */
final class DeckScreen extends Screen {
    private static final int COLS = 4;
    private static final int COUNT = 8;
    private static final int[] GLYPH = {Glyphs.WIFI, Glyphs.MOON, Glyphs.WAVE, Glyphs.SUN,
            Glyphs.SUN, Glyphs.SPEAKER, Glyphs.DISPLAY, Glyphs.DECK};
    private static final String[] NAME = {"Wi-Fi", "Night dim", "Scene motion", "Scenes",
            "Mood palette", "Sound", "Display", "Look"};
    private static final String[] MOTION = {"Alive", "Calm", "Still"};

    private int focus = 1;
    private final Tween nudgeX = new Tween(0f);
    private final Tween nudgeY = new Tween(0f);

    DeckScreen(MainActivity a) {
        super(a);
    }

    @Override int enterFrom() {
        return -1;
    }

    @Override void onEnter() {
        speak("Control Deck");
    }

    private String value(int i) {
        switch (i) {
            case 0: return act.offline ? "Offline" : "Connected";
            case 1: return Ui.DIM_NAMES[Math.max(0, Math.min(3, Prefs.dim()))];
            case 2: return MOTION[Math.max(0, Math.min(2, Prefs.motion()))];
            case 3: return Prefs.scenes() ? "Living scenes" : "Your wallpaper";
            case 4: return Prefs.mood() == 0 ? "Auto · " + Ui.moodName() : Ui.MOOD_NAMES[Prefs.mood()];
            case 5: return "Opens Sound";
            case 6: return "Opens Display";
            default: return "Wallpaper, accent";
        }
    }

    @Override void onDir(int key) {
        int col = focus % COLS;
        int row = focus / COLS;
        if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (col > 0) focus--;
            else nudge(nudgeX, -6f);
        } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (col < COLS - 1) focus++;
            else nudge(nudgeX, 6f);
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (row == 0) focus += COLS;
            else nudge(nudgeY, 6f);
        } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
            if (row == 1) focus -= COLS;
            else act.pop(this);
        }
        speak(NAME[focus] + ", " + value(focus));
        invalidate();
    }

    private void system(String action) {
        try {
            act.startIntent(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            // the TV may not have that panel
        }
    }

    @Override void onOk() {
        switch (focus) {
            case 0:
                system(Settings.ACTION_WIFI_SETTINGS);
                break;
            case 1:
                Prefs.setDim((Prefs.dim() + 1) % 4);
                act.applyLook();
                break;
            case 2:
                Prefs.setMotion((Prefs.motion() + 1) % 3);
                act.invalidateAll();
                break;
            case 3:
                Prefs.setScenes(!Prefs.scenes());
                act.applyLook();
                break;
            case 4:
                Prefs.setMood((Prefs.mood() + 1) % 5);
                act.invalidateAll();
                break;
            case 5:
                system(Settings.ACTION_SOUND_SETTINGS);
                break;
            case 6:
                system(Settings.ACTION_DISPLAY_SETTINGS);
                break;
            default:
                act.pop(this);
                act.openLook(0);
                return;
        }
        speak(NAME[focus] + ", " + value(focus));
        invalidate();
    }

    @Override void onBack() {
        act.pop(this);
    }

    @Override void draw1(Canvas c, long now) {
        tw(nudgeX, now);
        tw(nudgeY, now);
        Ui.fillAll(c, 0x73000000);
        // sheet
        Ui.rr(c, 96f, 54f, 1824f, 700f, 56f, 0xE60E1016);
        Ui.ring(c, 96f, 54f, 1824f, 700f, 56f, 1.5f, 0x2EFFFFFF);
        Ui.text(c, "Control Deck", 144f, 142f, 64f, Ui.display, Ui.TEXT, Paint.Align.LEFT);
        Ui.text(c, "Up or Back to close", 1776f, 130f, 26f, Ui.regular, Ui.alpha(Ui.TEXT, 0.78f), Paint.Align.RIGHT);

        float tw0 = (1824f - 144f - 48f - 3f * 22f) / 4f;
        for (int i = 0; i < COUNT; i++) {
            if (i == focus) continue;
            drawTile(c, i, tw0, false);
        }
        drawTile(c, focus, tw0, true);
        Ui.text(c, "Changes apply at once. Wi-Fi, Sound and Display open the TV's own panels.",
                144f, 664f, 26f, Ui.regular, Ui.alpha(Ui.TEXT, 0.78f), Paint.Align.LEFT);
    }

    private void drawTile(Canvas c, int i, float w, boolean f) {
        int col = i % COLS;
        int row = i / COLS;
        float x = 144f + col * (w + 22f) + (f ? nudgeX.cur : 0f);
        float y = 190f + row * (204f + 22f) + (f ? nudgeY.cur : 0f);
        int ink = f ? 0xFF0B0C10 : Ui.TEXT;
        int ink2 = f ? Ui.alpha(0xFF0B0C10, 0.74f) : Ui.alpha(Ui.TEXT, 0.78f);
        if (f) Ui.ring(c, x - 6f, y - 6f, x + w + 6f, y + 204f + 6f, 40f, 8f, Ui.alpha(Ui.accent, 0.45f));
        Ui.rr(c, x, y, x + w, y + 204f, 34f, f ? Ui.TEXT : 0x17FFFFFF);
        if (!f) Ui.ring(c, x, y, x + w, y + 204f, 34f, 1.5f, 0x24FFFFFF);
        Glyphs.draw(c, GLYPH[i], x + 28f + 23f, y + 24f + 23f, 46f, 2.4f, ink);
        Ui.text(c, NAME[i], x + 28f, y + 150f, 32f, Ui.medium, ink, Paint.Align.LEFT);
        Ui.text(c, Ui.fit(value(i), w - 56f, 26f, Ui.regular), x + 28f, y + 186f, 26f, Ui.regular, ink2, Paint.Align.LEFT);
    }
}
