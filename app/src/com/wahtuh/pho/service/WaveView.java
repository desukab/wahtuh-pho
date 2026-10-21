package com.wahtuh.pho.service;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Small animated waveform used by the floating bubble. */
public final class WaveView extends View {
    private static final int BARS = 5;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] levels = new float[]{0.3f, 0.55f, 0.8f, 0.55f, 0.3f};
    private boolean active;
    private float phase;

    public WaveView(Context context) {
        super(context);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(4));
        paint.setColor(0xFF7A5AD6);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (active) postInvalidateOnAnimation();
        else invalidate();
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public void setLevel(float rms) {
        float level = Math.min(1f, 0.18f + rms * 6f);
        levels[0] = 0.25f + level * 0.25f;
        levels[1] = 0.4f + level * 0.4f;
        levels[2] = 0.55f + level * 0.45f;
        levels[3] = 0.4f + level * 0.4f;
        levels[4] = 0.25f + level * 0.25f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float gap = w / (BARS + 1f);
        if (active) phase += 0.16f;
        for (int i = 0; i < BARS; i++) {
            float wave = active ? 0.78f + 0.22f * (float) Math.sin(phase + i * 0.9f) : 1f;
            float bar = Math.min(h - dp(4), Math.max(dp(5), h * levels[i] * wave));
            float x = gap * (i + 1);
            float top = (h - bar) / 2f;
            canvas.drawLine(x, top, x, top + bar, paint);
        }
        if (active) postInvalidateOnAnimation();
    }
}
