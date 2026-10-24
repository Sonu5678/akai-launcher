package com.mylauncher.akaitv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * The single translucent "glass" highlight. It lives above the content and
 * springs from one focused item to the next.
 */
final class GlassView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cur = new RectF();
    private final RectF from = new RectF();
    private final RectF to = new RectF();
    private float curR, fromR, toR;
    private boolean has;
    private ValueAnimator anim;
    private final int topColor, bottomColor, edgeColor;
    private float shaderTop = Float.NaN, shaderBottom = Float.NaN;

    GlassView(Context c, int rgb, float opacity, float strokePx) {
        super(c);
        setFocusable(false);
        setClickable(false);
        int base = rgb & 0x00FFFFFF;
        topColor = (Math.round(255 * opacity) << 24) | base;
        bottomColor = (Math.round(255 * opacity * 0.45f) << 24) | base;
        edgeColor = (Math.round(255 * Math.min(1f, opacity * 2.2f)) << 24) | base;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokePx);
        stroke.setColor(edgeColor);
        setVisibility(INVISIBLE);
    }

    void moveTo(RectF target, float radius, boolean animate) {
        if (anim != null) anim.cancel();
        if (!has || !animate) {
            cur.set(target);
            curR = radius;
            has = true;
            setVisibility(VISIBLE);
            invalidate();
            return;
        }
        from.set(cur);
        fromR = curR;
        to.set(target);
        toR = radius;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(360);
        anim.setInterpolator(new OvershootInterpolator(1.2f));
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            cur.left = from.left + (to.left - from.left) * t;
            cur.top = from.top + (to.top - from.top) * t;
            cur.right = from.right + (to.right - from.right) * t;
            cur.bottom = from.bottom + (to.bottom - from.bottom) * t;
            curR = fromR + (toR - fromR) * t;
            invalidate();
        });
        anim.start();
    }

    void hide() {
        if (anim != null) anim.cancel();
        has = false;
        setVisibility(INVISIBLE);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!has) return;
        // rebuild the gradient only when the highlight has actually moved vertically
        if (Float.isNaN(shaderTop) || Math.abs(shaderTop - cur.top) > 0.5f
                || Math.abs(shaderBottom - cur.bottom) > 0.5f) {
            fill.setShader(new LinearGradient(0, cur.top, 0, cur.bottom, topColor, bottomColor,
                    Shader.TileMode.CLAMP));
            shaderTop = cur.top;
            shaderBottom = cur.bottom;
        }
        float r = Math.max(0f, curR);
        c.drawRoundRect(cur, r, r, fill);
        c.drawRoundRect(cur, r, r, stroke);
    }
}
