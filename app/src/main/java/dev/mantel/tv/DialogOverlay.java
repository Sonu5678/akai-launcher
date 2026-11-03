package dev.mantel.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.KeyEvent;

import java.util.List;

/**
 * Dialog (section 8): 880 px wide, centred, Sheet fill, 20 px radius. Title 40 px, body 32 px
 * in secondary text (three lines at most), buttons 72 px high pills with 24 px gaps. Focus
 * starts on the safe action and Back cancels.
 */
final class DialogOverlay extends Screen {
    interface Listener {
        void onChoice(int index);
    }

    private final String title;
    private final String body;
    private final String[] buttons;
    private final Listener listener;
    private int focus;

    DialogOverlay(MainActivity a, String title, String body, String[] buttons, int focus, Listener l) {
        super(a);
        this.title = title;
        this.body = body;
        this.buttons = buttons;
        this.focus = Math.max(0, Math.min(buttons.length - 1, focus));
        this.listener = l;
    }

    @Override void onEnter() {
        speak(title + ". " + body);
    }

    @Override void onDir(int key) {
        if (key == KeyEvent.KEYCODE_DPAD_LEFT && focus > 0) {
            focus--;
            invalidate();
        } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT && focus < buttons.length - 1) {
            focus++;
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
        Ui.fillAll(c, 0x8C000000);
        List<String> titleLines = Ui.wrap(title, 784f, 40f, Ui.medium, 2);
        List<String> bodyLines = Ui.wrap(body, 784f, 32f, Ui.regular, 3);
        float h = 48f + titleLines.size() * 50f + 16f + bodyLines.size() * 42f + 40f + 72f + 48f;
        float top = (1080f - h) / 2f;
        Ui.rr(c, 520f, top, 1400f, top + h, 20f, Ui.SHEET);
        Ui.ring(c, 520f, top, 1400f, top + h, 20f, 2f, Ui.SURFACE2);

        float y = top + 48f + 36f;
        for (String s : titleLines) {
            Ui.text(c, s, 568f, y, 40f, Ui.medium, Ui.TEXT, Paint.Align.LEFT);
            y += 50f;
        }
        y += 16f - 8f;
        for (String s : bodyLines) {
            Ui.text(c, s, 568f, y + 26f, 32f, Ui.regular, Ui.TEXT2, Paint.Align.LEFT);
            y += 42f;
        }

        float by = top + h - 48f - 72f;
        float total = 0f;
        float[] w = new float[buttons.length];
        for (int i = 0; i < buttons.length; i++) {
            w[i] = Math.max(200f, Ui.measure(buttons[i], 32f, Ui.medium) + 64f);
            total += w[i];
        }
        total += 24f * (buttons.length - 1);
        float x = 960f - total / 2f;
        for (int i = 0; i < buttons.length; i++) {
            boolean f = i == focus;
            Ui.rr(c, x, by, x + w[i], by + 72f, 36f, f ? Ui.SURFACE2 : Ui.SURFACE1);
            if (f) Ui.ring(c, x, by, x + w[i], by + 72f, 36f, 3f, Ui.TEXT);
            Ui.text(c, buttons[i], x + w[i] / 2f, by + 48f, 32f, Ui.medium,
                    f ? Ui.TEXT : Ui.TEXT2, Paint.Align.CENTER);
            x += w[i] + 24f;
        }
    }
}
