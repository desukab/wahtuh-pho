package com.wahtuh.pho;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.wahtuh.pho.data.HistoryEntry;
import com.wahtuh.pho.data.HistoryStore;
import com.wahtuh.pho.ui.Ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Local transcript history, grouped by day. Raw ASR text is always kept. */
public final class HistoryActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        final int pad = Ui.dp(this, 20);
        root.setPadding(pad, pad, pad, Ui.dp(this, 14));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(pad, pad + insets.getSystemWindowInsetTop(), pad,
                    Ui.dp(this, 14) + insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "‹", 30, Ui.ACCENT, false);
        back.setPadding(Ui.dp(this, 2), 0, Ui.dp(this, 14), Ui.dp(this, 4));
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView title = Ui.text(this, "History", 31, Ui.TEXT, true);
        top.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(top);

        TextView note = Ui.text(this,
                "Tap to copy. Long-press for the raw transcript.",
                12, Ui.MUTED, false);
        note.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 16));
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        List<HistoryEntry> entries = new HistoryStore(this).list();
        if (entries.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setBackground(Ui.rounded(Ui.SURFACE, 20, this));
            empty.setPadding(Ui.dp(this, 20), Ui.dp(this, 24),
                    Ui.dp(this, 20), Ui.dp(this, 24));
            TextView t = Ui.text(this, "Nothing here yet", 20, Ui.TEXT, true);
            empty.addView(t);
            TextView s = Ui.text(this,
                    "Your dictations will appear here, grouped by day.",
                    13, Ui.MUTED, false);
            s.setPadding(0, Ui.dp(this, 6), 0, 0);
            empty.addView(s);
            list.addView(empty);
        } else {
            String lastGroup = "";
            int count = Math.min(entries.size(), 150);
            for (int i = 0; i < count; i++) {
                HistoryEntry e = entries.get(i);
                String group = groupLabel(e.createdAt);
                if (!group.equals(lastGroup)) {
                    lastGroup = group;
                    TextView header = Ui.text(this, group, 12, Ui.MUTED, false);
                    header.setLetterSpacing(0.08f);
                    header.setPadding(0, Ui.dp(this, i == 0 ? 0 : 14),
                            0, Ui.dp(this, 8));
                    list.addView(header);
                }
                list.addView(card(e));
            }
        }
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView footer = Ui.text(this, entries.size()
                        + " saved · raw transcripts are never overwritten",
                11, Ui.MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, Ui.dp(this, 10), 0, 0);
        root.addView(footer);

        setContentView(root);
    }

    private String groupLabel(long ms) {
        String day = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(ms));
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        String yesterday = new SimpleDateFormat("yyyyMMdd", Locale.US)
                .format(new Date(System.currentTimeMillis() - 86400000L));
        if (day.equals(today)) return "TODAY";
        if (day.equals(yesterday)) return "YESTERDAY";
        return new SimpleDateFormat("d MMMM", Locale.getDefault()).format(new Date(ms)).toUpperCase();
    }

    private View card(HistoryEntry e) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(Ui.SURFACE2, 18, this));
        card.setPadding(Ui.dp(this, 15), Ui.dp(this, 14), Ui.dp(this, 15), Ui.dp(this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 8);
        card.setLayoutParams(lp);

        String time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(e.createdAt));
        String meta = time + "  ·  " + e.engine
                + (e.language == null || e.language.isEmpty() ? "" : "  ·  " + e.language)
                + "  ·  " + e.latencyMs + " ms";
        card.addView(Ui.text(this, meta, 10, Ui.MUTED, false));

        TextView body = Ui.text(this, e.text, 15, Ui.TEXT, false);
        body.setTextIsSelectable(true);
        body.setLineSpacing(Ui.dp(this, 2), 1f);
        body.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(body);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, Ui.dp(this, 8), 0, 0);
        TextView copy = Ui.text(this, "Copy", 12, Ui.ACCENT, false);
        copy.setPadding(0, Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4));
        copy.setOnClickListener(v -> copy(e.text, "Transcript copied"));
        actions.addView(copy);
        if (e.raw != null && !e.raw.isEmpty() && !e.raw.equals(e.text)) {
            TextView raw = Ui.text(this, "Copy raw", 12, Ui.MUTED, false);
            raw.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            raw.setOnClickListener(v -> copy(e.raw, "Raw transcript copied"));
            actions.addView(raw);
        }
        card.addView(actions);

        card.setOnClickListener(v -> copy(e.text, "Transcript copied"));
        card.setOnLongClickListener(v -> {
            copy(e.raw, "Raw transcript copied");
            return true;
        });
        return card;
    }

    private void copy(String text, String toast) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Wahtuh Pho", text));
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }
}
