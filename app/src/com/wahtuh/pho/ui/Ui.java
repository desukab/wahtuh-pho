package com.wahtuh.pho.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class Ui {
    public static final int BG = 0xFF08081A;
    public static final int BG2 = 0xFF0E0E26;
    public static final int SURFACE = 0xFF161335;
    public static final int SURFACE2 = 0xFF201A44;
    public static final int ACCENT = 0xFFB7FF3C;
    public static final int ACCENT2 = 0xFF4DE8FF;
    public static final int MAGENTA = 0xFFFF4FAF;
    public static final int TEXT = 0xFFFFF4D6;
    public static final int MUTED = 0xFFA79FC7;
    public static final int DANGER = 0xFFFF6B6B;
    public static final int SUCCESS = 0xFFB7FF3C;
    public static final int INK = 0xFF08081A;
    public static final int LINE = 0xFF3A2F6B;

    private static Typeface pixel;
    private static Typeface body;

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static Typeface pixel(Context c) {
        if (pixel == null) {
            try {
                pixel = Typeface.createFromAsset(c.getAssets(), "fonts/PressStart2P-Regular.ttf");
            } catch (Exception e) {
                pixel = Typeface.create("monospace", Typeface.BOLD);
            }
        }
        return pixel;
    }

    public static Typeface body(Context c) {
        if (body == null) {
            try {
                body = Typeface.createFromAsset(c.getAssets(), "fonts/VT323-Regular.ttf");
            } catch (Exception e) {
                body = Typeface.MONOSPACE;
            }
        }
        return body;
    }

    public static TextView text(Context c, String value, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(bold ? pixel(c) : body(c));
        return t;
    }

    public static TextView pixel(Context c, String value, float sp, int color) {
        TextView t = text(c, value, sp, color, true);
        t.setLetterSpacing(0.04f);
        return t;
    }

    public static TextView body(Context c, String value, float sp, int color) {
        TextView t = text(c, value, sp, color, false);
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    public static GradientDrawable panel(int fill, int stroke, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke(dp(c, 2), stroke);
        d.setCornerRadius(0);
        return d;
    }

    public static GradientDrawable panel(int fill, int stroke, int radiusDp, Context c) {
        GradientDrawable d = panel(fill, stroke, c);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable rounded(int color, int radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable rounded(int color, int radiusDp, int strokeColor, Context c) {
        GradientDrawable d = rounded(color, radiusDp, c);
        d.setStroke(dp(c, 1), strokeColor);
        return d;
    }

    public static Drawable bevel(int face, int shadow, int border, Context c) {
        GradientDrawable back = new GradientDrawable();
        back.setColor(shadow);
        back.setStroke(dp(c, 2), border);
        GradientDrawable front = new GradientDrawable();
        front.setColor(face);
        front.setStroke(dp(c, 2), border);
        return new LayerDrawable(new Drawable[]{
                back, new InsetDrawable(front, 0, 0, dp(c, 5), dp(c, 5))});
    }

    public static TextView action(Context c, String label, int face, int shadow, int fg) {
        TextView b = pixel(c, label, 10, fg);
        b.setGravity(android.view.Gravity.CENTER);
        b.setBackground(bevel(face, shadow, TEXT, c));
        b.setMinHeight(dp(c, 50));
        b.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    public static Button button(Context c, String label, int bg, int fg) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(12);
        b.setTypeface(pixel(c));
        b.setBackground(bevel(bg, MAGENTA, TEXT, c));
        b.setMinHeight(dp(c, 50));
        b.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        return b;
    }

    public static void pad(View v, Context c, int l, int t, int r, int b) {
        v.setPadding(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
    }

    public static int alpha(int color, float a) {
        return Color.argb(Math.round(255 * a), Color.red(color), Color.green(color), Color.blue(color));
    }
}
