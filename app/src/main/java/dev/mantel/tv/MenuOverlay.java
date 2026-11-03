package dev.mantel.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.KeyEvent;

/** The hold-OK context menu: a small Sheet card of rows next to the item. */
final class MenuOverlay extends Screen {
    interface Listener {
        void onChoice(int index);
    }

    private final String[] items;
    private final float anchorX, anchorY;
    private final boolean above;
    private final Listener listener;
    private int focus = 0;

    MenuOverlay(MainActivity a, String[] items, float anchorX, float anchorY, boolean above, Listener l) {
        super(a);
        this.items = items;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.above = above;
        this.listener = l;
    }

    @Override void onEnter() {
        speak(items.length > 0 ? items[0] : "");
    }

    @Override void onDir(int key) {
        if (key == KeyEvent.KEYCODE_DPAD_UP && focus > 0) {
            focus--;
            speak(items[focus]);
            invalidate();
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN && focus < items.length - 1) {
            focus++;
            speak(items[focus]);
            invalidate();
        }
    }

    @Override void onOk() {
        final int chosen = focus;
        act.pop(this);
        if (listener != null) listener.onChoice(chosen);
    }

    @Override void onBack() {
        act.pop(this);
    }

    @Override void draw1(Canvas c, long now) {
        Ui.fillAll(c, 0x66000000);
        float maxW = 0f;
        for (String s : items) maxW = Math.max(maxW, Ui.measure(s, 32f, Ui.medium));
        float w = Math.max(440f, maxW + 96f);
        float h = items.length * 88f + 32f;
        float x = Math.max(120f, Math.min(1800f - w, anchorX - w / 2f));
        float y = above ? anchorY - h : anchorY;
        y = Math.max(60f, Math.min(1020f - h, y));

        Ui.rr(c, x, y, x + w, y + h, 20f, Ui.SHEET);
        Ui.ring(c, x, y, x + w, y + h, 20f, 2f, Ui.SURFACE2);
        for (int i = 0; i < items.length; i++) {
            float ry = y + 16f + i * 88f;
            boolean f = i == focus;
            if (f) {
                Ui.rr(c, x + 12f, ry, x + w - 12f, ry + 88f, 16f, Ui.SURFACE2);
                Ui.ring(c, x + 12f, ry, x + w - 12f, ry + 88f, 16f, 3f, Ui.TEXT);
            }
            Ui.text(c, items[i], x + 40f, ry + 56f, 32f, f ? Ui.medium : Ui.regular,
                    f ? Ui.TEXT : Ui.TEXT2, Paint.Align.LEFT);
        }
    }
}
