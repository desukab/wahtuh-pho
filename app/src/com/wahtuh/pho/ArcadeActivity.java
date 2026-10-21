package com.wahtuh.pho;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wahtuh.pho.ui.ArcadeBackdrop;
import com.wahtuh.pho.ui.ArcadeGameView;
import com.wahtuh.pho.ui.Ui;

import java.util.Locale;

public final class ArcadeActivity extends android.app.Activity {
    private static final String[] NAMES = {"SNAKE", "BREAKOUT", "PONG"};
    private static final String[] HINTS = {
            "SWIPE TO STEER  ·  EAT THE PINK PIXELS",
            "DRAG THE PADDLE  ·  CLEAR EVERY BRICK",
            "DRAG UP AND DOWN  ·  FIRST TO SEVEN"};

    private SharedPreferences prefs;
    private ArcadeGameView game;
    private final TextView[] tabs = new TextView[3];
    private TextView leftLabel;
    private TextView leftValue;
    private TextView midLabel;
    private TextView midValue;
    private TextView rightLabel;
    private TextView rightValue;
    private TextView hint;
    private TextView status;
    private int high;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("wahtuh_arcade", MODE_PRIVATE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        root.addView(new ArcadeBackdrop(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 10));
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        content.addView(topBar());
        content.addView(tabsRow(), top(10));
        content.addView(scoreBar(), top(10));

        FrameLayout stage = new FrameLayout(this);
        stage.setBackground(Ui.panel(Ui.BG2, Ui.LINE, this));
        game = new ArcadeGameView(this);
        game.setListener((score, aux, over) -> updateHud(over));
        stage.addView(game, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(stage, weighted());

        content.addView(footer(), top(10));
        select(0);
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = Ui.pixel(this, "< BACK", 8, Ui.MUTED);
        back.setPadding(0, Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = Ui.pixel(this, "OFFLINE ARCADE", 11, Ui.ACCENT);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView badge = Ui.pixel(this, "NO CLOUD", 6, Ui.ACCENT2);
        badge.setGravity(Gravity.END);
        bar.addView(badge);
        return bar;
    }

    private View tabsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        for (int i = 0; i < NAMES.length; i++) {
            final int index = i;
            TextView tab = Ui.pixel(this, NAMES[i], 8, Ui.MUTED);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            tab.setOnClickListener(v -> select(index));
            tabs[i] = tab;
            row.addView(tab, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        return row;
    }

    private View scoreBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        bar.setPadding(Ui.dp(this, 12), Ui.dp(this, 9), Ui.dp(this, 12), Ui.dp(this, 9));

        View left = metric(true);
        bar.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(divider());
        View mid = metric(true);
        bar.addView(mid, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(divider());
        View right = metric(false);
        bar.addView(right, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return bar;
    }

    private View metric(boolean leftAlign) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView label = Ui.pixel(this, "SCORE", 6, Ui.MUTED);
        TextView value = Ui.pixel(this, "0000", 14, Ui.ACCENT);
        value.setPadding(0, Ui.dp(this, 4), 0, 0);
        column.addView(label);
        column.addView(value);
        if (!leftAlign) {
            label.setGravity(Gravity.END);
            value.setGravity(Gravity.END);
        }
        if (leftLabel == null) {
            leftLabel = label;
            leftValue = value;
        } else if (midLabel == null) {
            midLabel = label;
            midValue = value;
        } else {
            rightLabel = label;
            rightValue = value;
        }
        return column;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Ui.dp(this, 1), Ui.dp(this, 44));
        lp.leftMargin = Ui.dp(this, 8);
        lp.rightMargin = Ui.dp(this, 8);
        v.setLayoutParams(lp);
        return v;
    }

    private View footer() {
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        footer.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        status = Ui.pixel(this, "PLAYING", 7, Ui.ACCENT);
        top.addView(status, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView restart = Ui.pixel(this, "NEW GAME >", 8, Ui.MAGENTA);
        restart.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), 0, Ui.dp(this, 6));
        restart.setOnClickListener(v -> {
            game.reset();
            updateHud(false);
        });
        top.addView(restart);
        footer.addView(top);

        hint = Ui.body(this, HINTS[0], 15, Ui.MUTED);
        hint.setPadding(0, Ui.dp(this, 5), 0, 0);
        footer.addView(hint);
        return footer;
    }

    private void select(int index) {
        for (int i = 0; i < tabs.length; i++) {
            boolean on = i == index;
            tabs[i].setTextColor(on ? Ui.INK : Ui.MUTED);
            tabs[i].setBackground(on ? Ui.panel(Ui.ACCENT, Ui.ACCENT, this) : null);
        }
        high = prefs.getInt("hi_" + NAMES[index].toLowerCase(Locale.US), 0);
        game.setMode(index);
        hint.setText(HINTS[index]);
        updateHud(false);
    }

    private void updateHud(boolean over) {
        int mode = game.mode();
        if (mode == ArcadeGameView.PONG) {
            leftLabel.setText("YOU");
            midLabel.setText("FIRST TO");
            rightLabel.setText("CPU");
            leftValue.setText(Integer.toString(game.score()));
            midValue.setText("7");
            rightValue.setText(Integer.toString(game.aux()));
        } else {
            int score = game.score();
            if (score > high) {
                high = score;
                prefs.edit().putInt("hi_" + NAMES[mode].toLowerCase(Locale.US), high).apply();
            }
            leftLabel.setText("SCORE");
            leftValue.setText(pad(score));
            midLabel.setText(mode == ArcadeGameView.SNAKE ? "LENGTH" : "LIVES");
            midValue.setText(pad(game.aux()));
            rightLabel.setText("HI");
            rightValue.setText(pad(high));
        }
        if (over) {
            status.setText(game.won() ? "STAGE CLEAR" : "GAME OVER");
            status.setTextColor(game.won() ? Ui.ACCENT : Ui.MAGENTA);
        } else {
            status.setText("PLAYING");
            status.setTextColor(Ui.ACCENT);
        }
    }

    private static String pad(int value) {
        return String.format(Locale.US, "%04d", value);
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        lp.topMargin = Ui.dp(this, 10);
        return lp;
    }

    private LinearLayout.LayoutParams top(int value) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, value);
        return lp;
    }
}
