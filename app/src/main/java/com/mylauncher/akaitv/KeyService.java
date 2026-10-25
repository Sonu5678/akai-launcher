package com.mylauncher.akaitv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Catches the remote's Settings button before the TV throws it away, and opens the launcher's
 * quick settings panel. It looks at no screen content and ignores every other button.
 * Turn it on or off with scripts/enable-settings-key.sh and scripts/disable-settings-key.sh.
 */
public class KeyService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // not interested in screen events
    }

    @Override
    public void onInterrupt() {
        // nothing to interrupt
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        try {
            if (event.getKeyCode() != KeyEvent.KEYCODE_SETTINGS) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                Log.i("AkaiLauncher", "settings button caught");
                Intent i = new Intent(this, MainActivity.class);
                i.setAction(MainActivity.ACTION_SETTINGS_PANEL);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
            return true;
        } catch (Throwable t) {
            Log.w("AkaiLauncher", "key service: " + t);
            return false;
        }
    }
}
