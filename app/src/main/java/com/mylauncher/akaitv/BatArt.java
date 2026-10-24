package com.mylauncher.akaitv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** A small original flying-bat silhouette. flap runs from -1 (wings down) to 1 (wings up). */
final class BatArt {
    private BatArt() {
    }

    static void draw(Canvas c, Path p, Paint fill, float cx, float cy, float s, float flap,
                     float tilt) {
        c.save();
        c.translate(cx, cy);
        c.rotate(tilt);
        fill.setShader(null);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xFF070a11);

        for (int side = -1; side <= 1; side += 2) {
            float tipY = -s * 0.28f - flap * s * 0.42f;
            p.reset();
            p.moveTo(side * s * 0.08f, -s * 0.05f);
            p.quadTo(side * s * 0.46f, -s * 0.42f - flap * s * 0.22f, side * s * 0.98f, tipY);
            p.quadTo(side * s * 0.86f, tipY + s * 0.22f, side * s * 0.76f, tipY + s * 0.36f);
            p.quadTo(side * s * 0.66f, tipY + s * 0.22f, side * s * 0.54f, tipY + s * 0.38f);
            p.quadTo(side * s * 0.42f, tipY + s * 0.26f, side * s * 0.30f, s * 0.12f);
            p.lineTo(side * s * 0.08f, s * 0.14f);
            p.close();
            c.drawPath(p, fill);
        }

        // body and head
        c.drawOval(-s * 0.14f, -s * 0.10f, s * 0.14f, s * 0.22f, fill);
        c.drawCircle(0, -s * 0.12f, s * 0.10f, fill);

        // ears
        for (int side = -1; side <= 1; side += 2) {
            p.reset();
            p.moveTo(side * s * 0.02f, -s * 0.17f);
            p.lineTo(side * s * 0.13f, -s * 0.34f);
            p.lineTo(side * s * 0.11f, -s * 0.12f);
            p.close();
            c.drawPath(p, fill);
        }

        // eyes
        fill.setColor(0xFFDCE6FF);
        c.drawCircle(-s * 0.04f, -s * 0.13f, s * 0.014f, fill);
        c.drawCircle(s * 0.04f, -s * 0.13f, s * 0.014f, fill);
        c.restore();
    }
}
