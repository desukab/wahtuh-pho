package com.wahtuh.pho.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class Ui {
    public static final int BG = 0xFF0B0A0D;
    public static final int SURFACE = 0xFF17151B;
    public static final int ACCENT = 0xFFD5C2FF;
    public static final int ACCENT2 = 0xFFA98BFF;
    public static final int TEXT = 0xFFF7F0E4;
    public static final int MUTED = 0xFF9B949F;
    public static final int DANGER = 0xFFF08A9B;
    public static final int SUCCESS = 0xFFA6E3B8;
    public static final int SURFACE2 = 0xFF211E26;
    public static final int INK = 0xFF17151B;
    public static final int LINE = 0xFF2A2630;

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
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

    public static Button button(Context c, String label, int bg, int fg) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(15);
        b.setBackground(rounded(bg, 24, c));
        b.setMinHeight(dp(c, 46));
        b.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        return b;
    }

    public static TextView text(Context c, String value, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(android.graphics.Typeface.create(bold ? "serif" : "sans",
                bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        return t;
    }

    public static void pad(View v, Context c, int l, int t, int r, int b) {
        v.setPadding(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
    }

    public static int alpha(int color, float a) {
        return Color.argb(Math.round(255 * a), Color.red(color), Color.green(color), Color.blue(color));
    }
}
