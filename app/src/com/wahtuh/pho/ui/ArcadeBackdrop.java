package com.wahtuh.pho.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/** Fixed starfield, grid and scanlines behind the app. */
public final class ArcadeBackdrop extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scan = new Paint();
    private float[] stars;

    public ArcadeBackdrop(Context context) {
        this(context, null);
    }

    public ArcadeBackdrop(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ArcadeBackdrop(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        paint.setStrokeWidth(dp(1));
        scan.setStrokeWidth(dp(1));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Random r = new Random(1993);
        stars = new float[90];
        for (int i = 0; i < stars.length; i += 2) {
            stars[i] = r.nextFloat() * w;
            stars[i + 1] = r.nextFloat() * h * 0.72f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        canvas.drawColor(Ui.BG);

        if (stars != null) {
            for (int i = 0; i < stars.length; i += 2) {
                boolean bright = i % 6 == 0;
                star.setColor(bright ? Ui.ACCENT2 : Ui.MUTED);
                star.setAlpha(bright ? 120 : 55);
                canvas.drawRect(stars[i], stars[i + 1], stars[i] + dp(2), stars[i + 1] + dp(2), star);
            }
        }

        float step = dp(42);
        paint.setColor(Ui.LINE);
        paint.setAlpha(40);
        for (float x = 0; x <= w; x += step) canvas.drawLine(x, 0, x, h, paint);
        for (float y = 0; y <= h; y += step) canvas.drawLine(0, y, w, y, paint);

        float horizon = h * 0.62f;
        paint.setColor(Ui.ACCENT);
        paint.setAlpha(36);
        canvas.drawLine(0, horizon, w, horizon, paint);
        for (int i = -10; i <= 10; i++) {
            float x = w / 2f + i * dp(34);
            canvas.drawLine(w / 2f + i * dp(8), horizon, x, h, paint);
        }

        scan.setColor(0xFF000000);
        scan.setAlpha(18);
        for (float y = 0; y < h; y += dp(4)) canvas.drawLine(0, y, w, y, scan);
    }
}
