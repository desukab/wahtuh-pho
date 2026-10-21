package com.wahtuh.pho;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.wahtuh.pho.data.HistoryEntry;
import com.wahtuh.pho.data.HistoryStore;
import com.wahtuh.pho.data.SecureStore;
import com.wahtuh.pho.data.Settings;
import com.wahtuh.pho.service.DictationService;
import com.wahtuh.pho.service.InsertAccessibilityService;
import com.wahtuh.pho.text.Snippets;
import com.wahtuh.pho.ui.Ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends android.app.Activity {
    private static final int REQ_MIC = 10;
    private static final int REQ_NOTIF = 11;

    private Settings settings;
    private SecureStore secure;
    private FrameLayout root;
    private FrameLayout content;
    private final Handler main = new Handler(Looper.getMainLooper());

    private int tab = 0;
    private boolean refreshing;
    private View drawer;
    private boolean drawerOpen;

    private static final String[] PROVIDERS = {
            Settings.LOCAL, Settings.GROQ, Settings.GEMINI, Settings.DEEPGRAM, Settings.OPENAI};
    private static final String[] PROVIDER_NAMES = {
            "ON-DEVICE", "GROQ", "GEMINI", "DEEPGRAM", "OPENAI"};
    private static final String[] PROVIDER_NOTE = {
            "On-device · 7 languages · no key",
            "Fast cloud · your key",
            "Multilingual cloud · your key",
            "Batch cloud · your key",
            "Cloud · your key"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new Settings(this);
        secure = new SecureStore(this);
        if (!settings.onboarded()) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.root);
        content = findViewById(R.id.content);
        findViewById(R.id.menu_button).setOnClickListener(v -> openDrawer());
        findViewById(R.id.top_settings_button).setOnClickListener(v -> showSettings());
        findViewById(R.id.nav_home).setOnClickListener(v -> showTab(0));
        findViewById(R.id.nav_words).setOnClickListener(v -> showTab(1));
        findViewById(R.id.nav_snips).setOnClickListener(v -> showTab(2));
        findViewById(R.id.nav_style).setOnClickListener(v -> showTab(3));
        int start = getIntent() == null ? 0 : getIntent().getIntExtra("tab", 0);
        showTab(Math.max(0, Math.min(3, start)));
        if (getIntent() != null && getIntent().getBooleanExtra("request_mic", false)) {
            ensureMic();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!settings.onboarded()) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }
        render();
    }

    private void render() {
        refreshing = true;
        content.removeAllViews();
        switch (tab) {
            case 1: content.addView(wordsPage()); break;
            case 2: content.addView(snipsPage()); break;
            case 3: content.addView(stylePage()); break;
            case -1: content.addView(settingsPage()); break;
            default: content.addView(homePage()); break;
        }
        updateNav();
        refreshing = false;
    }

    private void showTab(int index) {
        tab = index;
        render();
    }

    private void showSettings() {
        tab = -1;
        render();
    }

    private void updateNav() {
        int[] ids = {R.id.nav_home_icon, R.id.nav_words_icon, R.id.nav_snips_icon, R.id.nav_style_icon};
        int[] labels = {R.id.nav_home_label, R.id.nav_words_label, R.id.nav_snips_label,
                R.id.nav_style_label};
        for (int i = 0; i < 4; i++) {
            int color = i == tab ? Ui.ACCENT : Ui.MUTED;
            ((ImageView) findViewById(ids[i])).setColorFilter(color);
            ((TextView) findViewById(labels[i])).setTextColor(color);
        }
    }

    private ScrollView page() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, Ui.dp(this, 18));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 24));
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setTag(list);
        return scroll;
    }

    @SuppressWarnings("unchecked")
    private LinearLayout body(ScrollView scroll) {
        return (LinearLayout) scroll.getTag();
    }

    private void header(LinearLayout list, String kicker, String title, String subtitle) {
        TextView k = Ui.pixel(this, kicker, 8, Ui.ACCENT2);
        k.setLetterSpacing(0.12f);
        list.addView(k);
        TextView t = Ui.pixel(this, title, 20, Ui.TEXT);
        t.setPadding(0, Ui.dp(this, 8), 0, 0);
        list.addView(t);
        if (subtitle != null) {
            TextView s = Ui.body(this, subtitle, 18, Ui.MUTED);
            s.setPadding(0, Ui.dp(this, 8), 0, 0);
            list.addView(s);
        }
        list.addView(space(18));
    }

    private View homePage() {
        ScrollView scroll = page();
        LinearLayout list = body(scroll);
        header(list, "1UP  //  " + (online() ? "ONLINE" : "NO SIGNAL"), "TRANSCRIPTS", null);

        List<HistoryEntry> entries = new HistoryStore(this).list();
        int words = 0;
        for (HistoryEntry e : entries) {
            String text = e.text == null ? "" : e.text.trim();
            if (!text.isEmpty()) words += text.split("\\s+").length;
        }

        list.addView(heroCard(), matchWrap());
        list.addView(scoreStrip(entries, words), top(10));
        list.addView(systemCard(), top(10));

        TextView recent = Ui.pixel(this, "RECENT RUNS", 9, Ui.MUTED);
        recent.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 8));
        list.addView(recent);

        if (entries.isEmpty()) {
            list.addView(quickStartCard());
        } else {
            String lastGroup = "";
            for (HistoryEntry entry : entries) {
                String group = groupLabel(entry.createdAt);
                if (!group.equals(lastGroup)) {
                    lastGroup = group;
                    TextView day = Ui.pixel(this, group, 8, Ui.ACCENT2);
                    day.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
                    list.addView(day);
                }
                list.addView(historyCard(entry));
            }
        }

        list.addView(arcadeStrip(), top(18));
        return scroll;
    }

    private View heroCard() {
        boolean live = DictationService.running;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.panel(Ui.SURFACE, live ? Ui.ACCENT : Ui.LINE, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_wave);
        icon.setColorFilter(live ? Ui.ACCENT : Ui.ACCENT2);
        icon.setBackground(Ui.panel(Ui.SURFACE2, Ui.LINE, this));
        icon.setPadding(Ui.dp(this, 11), Ui.dp(this, 11), Ui.dp(this, 11), Ui.dp(this, 11));
        head.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 50), Ui.dp(this, 50)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(Ui.dp(this, 12), 0, 0, 0);
        copy.addView(Ui.pixel(this, "DICTATION ENGINE", 7, Ui.MAGENTA));
        TextView title = Ui.pixel(this, live ? "BUBBLE IS LIVE" : "READY TO TALK", 12,
                live ? Ui.ACCENT : Ui.TEXT);
        title.setPadding(0, Ui.dp(this, 7), 0, 0);
        copy.addView(title);
        TextView status = Ui.body(this, settings.providerLabel() + "  ·  "
                + Settings.languageLabel(settings.language())
                + (settings.localOnly() ? "  ·  on device" : "  ·  your key")
                + polishStatus(), 16, Ui.MUTED);
        status.setPadding(0, Ui.dp(this, 4), 0, 0);
        copy.addView(status);
        head.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView dot = Ui.pixel(this, live ? "ON" : "OFF", 7,
                live ? Ui.ACCENT : Ui.DANGER);
        head.addView(dot);
        card.addView(head);

        TextView tip = Ui.body(this, live
                ? "It appears only when a text field opens. Hold to talk, tap for hands-free, or drag it to an X to snooze 5 or 10 minutes."
                : "Start it once. The bubble waits until you focus a text field, then floats over any app.",
                16, Ui.TEXT);
        tip.setPadding(0, Ui.dp(this, 12), 0, 0);
        card.addView(tip);

        TextView action = Ui.action(this, live ? "HIDE BUBBLE" : "START THE BUBBLE",
                live ? Ui.SURFACE2 : Ui.ACCENT, live ? Ui.DANGER : Ui.MAGENTA,
                live ? Ui.TEXT : Ui.INK);
        action.setOnClickListener(v -> {
            if (live) stopBubble(); else startBubble();
        });
        card.addView(action, top(12));
        return card;
    }

    private View scoreStrip(List<HistoryEntry> entries, int words) {
        int today = 0;
        String todayKey = groupLabel(System.currentTimeMillis());
        for (HistoryEntry e : entries) {
            if (!todayKey.equals(groupLabel(e.createdAt))) continue;
            String text = e.text == null ? "" : e.text.trim();
            if (!text.isEmpty()) today += text.split("\\s+").length;
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        row.addView(metric("WORDS", String.format(Locale.US, "%,d", words), Ui.ACCENT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(metricDivider());
        row.addView(metric("RUNS", String.format(Locale.US, "%,d", entries.size()), Ui.ACCENT2),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(metricDivider());
        row.addView(metric("TODAY", String.format(Locale.US, "%,d", today), Ui.MAGENTA),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View metric(String label, String value, int color) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(Ui.pixel(this, label, 6, Ui.MUTED));
        TextView number = Ui.pixel(this, value, 17, color);
        number.setPadding(0, Ui.dp(this, 5), 0, 0);
        column.addView(number);
        return column;
    }

    private View metricDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Ui.dp(this, 1), Ui.dp(this, 38));
        lp.leftMargin = Ui.dp(this, 8);
        lp.rightMargin = Ui.dp(this, 8);
        divider.setLayoutParams(lp);
        return divider;
    }

    private View systemCard() {
        boolean mic = micGranted();
        boolean bubble = android.provider.Settings.canDrawOverlays(this);
        boolean insertion = InsertAccessibilityService.isEnabled(this);
        int issues = (mic ? 0 : 1) + (bubble ? 0 : 1) + (insertion ? 0 : 1);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.panel(Ui.SURFACE, issues == 0 ? Ui.ACCENT : Ui.MAGENTA, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.pixel(this, "SYSTEM CHECK", 8, Ui.MAGENTA),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView state = Ui.pixel(this, issues == 0 ? "ALL CLEAR" : issues + " TO FIX", 7,
                issues == 0 ? Ui.ACCENT : Ui.DANGER);
        head.addView(state);
        card.addView(head);

        if (issues == 0) {
            TextView ready = Ui.body(this, "MICROPHONE  ·  BUBBLE  ·  INSERTION  //  READY",
                    15, Ui.ACCENT2);
            ready.setPadding(0, Ui.dp(this, 9), 0, 0);
            card.addView(ready);
            return card;
        }

        if (!mic) card.addView(systemRow("MICROPHONE", "Voice input is locked.", "ALLOW", this::ensureMic));
        if (!bubble) card.addView(systemRow("BUBBLE", "Overlay permission is needed.", "FIX", this::overlaySettings));
        if (!insertion) card.addView(systemRow("INSERTION", "Accessibility insertion is off.", "FIX", this::accessibilitySettings));
        return card;
    }

    private View systemRow(String label, String detail, String action, Runnable run) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 9), 0, 0);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.pixel(this, label, 7, Ui.TEXT));
        TextView d = Ui.body(this, detail, 15, Ui.MUTED);
        d.setPadding(0, Ui.dp(this, 3), 0, 0);
        text.addView(d);
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView fix = Ui.pixel(this, action + " >", 7, Ui.ACCENT);
        fix.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), 0, Ui.dp(this, 8));
        fix.setOnClickListener(v -> run.run());
        row.addView(fix);
        return row;
    }

    private View quickStartCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        card.addView(Ui.pixel(this, "NO SAVES YET", 10, Ui.TEXT));
        card.addView(stepRow("1", "Start the bubble above.", Ui.ACCENT));
        card.addView(stepRow("2", "Tap it and talk normally.", Ui.ACCENT2));
        card.addView(stepRow("3", "Your words appear in the app.", Ui.MAGENTA));
        return card;
    }

    private View stepRow(String number, String text, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 10), 0, 0);
        TextView n = Ui.pixel(this, number, 9, color);
        n.setGravity(Gravity.CENTER);
        n.setBackground(Ui.panel(Ui.SURFACE2, Ui.LINE, this));
        row.addView(n, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));
        TextView body = Ui.body(this, text, 17, Ui.MUTED);
        body.setPadding(Ui.dp(this, 10), 0, 0, 0);
        row.addView(body);
        return row;
    }

    private View arcadeStrip() {
        boolean offline = !online();
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.panel(Ui.SURFACE2, Ui.MAGENTA, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(Ui.pixel(this, offline ? "NO SIGNAL  //  INSERT COIN" : "INSERT COIN",
                7, Ui.MAGENTA));
        TextView title = Ui.body(this, "Offline arcade  ·  Snake, Breakout, Pong", 17, Ui.TEXT);
        title.setPadding(0, Ui.dp(this, 5), 0, 0);
        copy.addView(title);
        card.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(Ui.pixel(this, "PLAY >", 8, Ui.ACCENT));
        card.setOnClickListener(v -> openArcade());
        return card;
    }

    private boolean online() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return false;
        }
    }

    private View historyCard(HistoryEntry entry) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 10));
        card.setLayoutParams(top(8));

        String time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(entry.createdAt));
        TextView meta = Ui.pixel(this, time + "  //  " + providerName(entry.provider), 7, Ui.MUTED);
        card.addView(meta);

        TextView text = Ui.body(this, entry.text, 19, Ui.TEXT);
        text.setPadding(0, Ui.dp(this, 8), 0, 0);
        text.setMaxLines(5);
        text.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(text);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, Ui.dp(this, 8), 0, 0);
        TextView copy = Ui.pixel(this, "COPY", 8, Ui.ACCENT);
        copy.setPadding(0, Ui.dp(this, 6), Ui.dp(this, 18), Ui.dp(this, 6));
        copy.setOnClickListener(v -> copy(entry.text));
        actions.addView(copy);
        TextView del = Ui.pixel(this, "DEL", 8, Ui.DANGER);
        del.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        del.setOnClickListener(v -> confirmDelete(entry));
        actions.addView(del);
        card.addView(actions);
        return card;
    }

    private View wordsPage() {
        ScrollView scroll = page();
        LinearLayout list = body(scroll);
        header(list, "ITEMS", "DICTIONARY", "Names and words, exactly how you say them.");

        LinearLayout toggle = new LinearLayout(this);
        toggle.setOrientation(LinearLayout.HORIZONTAL);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        toggle.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        LinearLayout toggleText = new LinearLayout(this);
        toggleText.setOrientation(LinearLayout.VERTICAL);
        toggleText.addView(Ui.pixel(this, "USE DICTIONARY", 9, Ui.TEXT));
        toggleText.addView(Ui.body(this, "Fix names after dictation", 16, Ui.MUTED));
        toggle.addView(toggleText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch use = new Switch(this);
        use.setChecked(settings.dictionary());
        use.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        use.setTrackTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
        use.setOnCheckedChangeListener((b, c) -> settings.setDictionary(c));
        toggle.addView(use);
        list.addView(toggle);

        TextView add = Ui.action(this, "+ ADD WORD", Ui.ACCENT, Ui.MAGENTA, Ui.INK);
        add.setOnClickListener(v -> addWordDialog());
        list.addView(add, top(14));

        List<String> entries = dictionaryEntries();
        if (entries.isEmpty()) {
            list.addView(emptyCard("EMPTY SLOT", "Add a name, product, or place."), top(14));
        } else {
            for (int i = 0; i < entries.size(); i++) {
                list.addView(wordCard(entries.get(i), i), top(8));
            }
        }
        return scroll;
    }

    private View wordCard(String line, int index) {
        String from = line;
        String to = "";
        int at = line.indexOf('=');
        if (at > 0) {
            from = line.substring(0, at).trim();
            to = line.substring(at + 1).trim();
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.pixel(this, from.toUpperCase(Locale.getDefault()), 9, Ui.ACCENT));
        if (!to.isEmpty()) {
            TextView value = Ui.body(this, "→ " + to, 18, Ui.TEXT);
            value.setPadding(0, Ui.dp(this, 5), 0, 0);
            text.addView(value);
        }
        card.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView del = Ui.pixel(this, "X", 11, Ui.DANGER);
        del.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        del.setOnClickListener(v -> {
            List<String> all = dictionaryEntries();
            if (index >= 0 && index < all.size()) {
                all.remove(index);
                settings.setUserDictionary(join(all));
                render();
            }
        });
        card.addView(del);
        return card;
    }

    private void addWordDialog() {
        LinearLayout box = dialogBox();
        EditText from = input("Word or name", false);
        EditText to = input("Replace with (optional)", false);
        box.addView(from);
        box.addView(to, top(10));
        AlertDialog dialog = dialog("ADD WORD", box, "SAVE", () -> {
            String key = from.getText().toString().trim();
            String value = to.getText().toString().trim();
            if (key.isEmpty()) return;
            List<String> entries = dictionaryEntries();
            entries.add(key + (value.isEmpty() ? "" : "=" + value));
            settings.setUserDictionary(join(entries));
            render();
        });
        dialog.setOnShowListener(d -> from.requestFocus());
        dialog.show();
    }

    private View snipsPage() {
        ScrollView scroll = page();
        LinearLayout list = body(scroll);
        header(list, "ITEMS", "SNIPPETS", "Short triggers that expand into full text.");

        TextView add = Ui.action(this, "+ ADD SNIPPET", Ui.ACCENT, Ui.MAGENTA, Ui.INK);
        add.setOnClickListener(v -> addSnippetDialog());
        list.addView(add);

        Snippets snippets = Snippets.parse(settings.snippets());
        if (snippets.entries().isEmpty()) {
            list.addView(emptyCard("EMPTY SLOT", "Try: my email=you@example.com"), top(14));
        } else {
            for (Snippets.Entry entry : snippets.entries()) {
                list.addView(snippetCard(entry), top(8));
            }
        }
        return scroll;
    }

    private View snippetCard(Snippets.Entry entry) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.pixel(this, entry.trigger.toUpperCase(Locale.getDefault()), 9, Ui.ACCENT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView del = Ui.pixel(this, "X", 11, Ui.DANGER);
        del.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6));
        del.setOnClickListener(v -> {
            List<Snippets.Entry> all = snippetsList();
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).trigger.equals(entry.trigger)
                        && all.get(i).expansion.equals(entry.expansion)) {
                    all.remove(i);
                    break;
                }
            }
            settings.setSnippets(joinSnippets(all));
            render();
        });
        top.addView(del);
        card.addView(top);
        TextView value = Ui.body(this, entry.expansion, 18, Ui.TEXT);
        value.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(value);
        return card;
    }

    private void addSnippetDialog() {
        LinearLayout box = dialogBox();
        EditText trigger = input("Trigger", false);
        EditText expansion = input("Full text", true);
        box.addView(trigger);
        box.addView(expansion, top(10));
        dialog("ADD SNIPPET", box, "SAVE", () -> {
            String key = trigger.getText().toString().trim();
            String value = expansion.getText().toString().trim();
            if (key.isEmpty() || value.isEmpty()) return;
            List<Snippets.Entry> all = snippetsList();
            all.add(new Snippets.Entry(key, value));
            settings.setSnippets(joinSnippets(all));
            render();
        }).show();
    }

    private View stylePage() {
        ScrollView scroll = page();
        LinearLayout list = body(scroll);
        header(list, "MODE", "STYLE", "How your words land.");
        list.addView(styleCard(Settings.STYLE_DEFAULT, "DEFAULT", "Clean and natural."));
        list.addView(styleCard(Settings.STYLE_FORMAL, "FORMAL", "Polished and precise."));
        list.addView(styleCard(Settings.STYLE_CASUAL, "CASUAL", "Relaxed, no final period"));
        list.addView(styleCard(Settings.STYLE_VERY_CASUAL, "VERY CASUAL", "lowercase, minimal punctuation"));
        list.addView(styleCard(Settings.STYLE_EXCITED, "EXCITED!", "Upbeat and loud!"));
        if (settings.appTone()) {
            TextView note = Ui.body(this,
                    "APP-AWARE TONE is on: with DEFAULT selected, chat apps drop the final period "
                            + "and email stays formal. It uses only the app package name.",
                    16, Ui.MUTED);
            note.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
            list.addView(note);
        }
        return scroll;
    }

    private View styleCard(String value, String label, String sample) {
        boolean selected = value.equals(settings.style());
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.panel(Ui.SURFACE, selected ? Ui.ACCENT : Ui.LINE, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        card.setLayoutParams(top(8));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.pixel(this, label, 10, selected ? Ui.ACCENT : Ui.TEXT));
        TextView s = Ui.body(this, sample, 17, Ui.MUTED);
        s.setPadding(0, Ui.dp(this, 6), 0, 0);
        text.addView(s);
        card.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView mark = Ui.pixel(this, selected ? ">>" : "", 12, Ui.ACCENT);
        card.addView(mark);
        card.setOnClickListener(v -> {
            settings.setStyle(value);
            render();
        });
        return card;
    }

    private View settingsPage() {
        ScrollView scroll = page();
        LinearLayout list = body(scroll);
        header(list, "SYSTEM", "SETTINGS", null);

        section(list, "TRANSCRIBER");
        for (int i = 0; i < PROVIDERS.length; i++) {
            list.addView(providerCard(PROVIDERS[i], PROVIDER_NAMES[i], PROVIDER_NOTE[i]), top(8));
        }

        section(list, "LANGUAGE");
        LinearLayout row = null;
        for (int i = 0; i < Settings.LANGUAGES.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setPadding(0, Ui.dp(this, 6), 0, 0);
                list.addView(row, matchWrap());
            }
            String code = Settings.LANGUAGES[i];
            TextView chip = languageChip(code);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.rightMargin = Ui.dp(this, 6);
            lp.bottomMargin = Ui.dp(this, 6);
            row.addView(chip, lp);
        }

        section(list, "PRIVACY");
        LinearLayout privacy = new LinearLayout(this);
        privacy.setGravity(Gravity.CENTER_VERTICAL);
        privacy.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        privacy.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        LinearLayout privacyText = new LinearLayout(this);
        privacyText.setOrientation(LinearLayout.VERTICAL);
        privacyText.addView(Ui.pixel(this, "ON-DEVICE ONLY", 9, Ui.TEXT));
        privacyText.addView(Ui.body(this, "Never send audio to a cloud", 16, Ui.MUTED));
        privacy.addView(privacyText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch local = new Switch(this);
        local.setChecked(settings.localOnly());
        local.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        local.setTrackTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
        local.setOnCheckedChangeListener((b, checked) -> {
            if (refreshing) return;
            settings.setLocalOnly(checked);
            if (checked && !Settings.LOCAL.equals(settings.provider())) {
                settings.setProvider(Settings.LOCAL);
                settings.setCleanup(false);
            }
            Toast.makeText(this, checked ? "On-device only" : "Cloud mode unlocked",
                    Toast.LENGTH_SHORT).show();
            render();
        });
        privacy.addView(local);
        list.addView(privacy, top(6));

        section(list, "SETUP");
        list.addView(permissionRow("MICROPHONE", micGranted(), "FIX", this::ensureMic), top(6));
        list.addView(permissionRow("BUBBLE", android.provider.Settings.canDrawOverlays(this),
                "FIX", this::overlaySettings), top(6));
        list.addView(permissionRow("INSERTION", InsertAccessibilityService.isEnabled(this),
                "FIX", this::accessibilitySettings), top(6));
        list.addView(permissionRow("NOTIFICATIONS", notificationsGranted(),
                "FIX", this::askNotifications), top(6));
        list.addView(permissionRow("BATTERY", batteryExempt(),
                "FIX", this::batterySettings), top(6));

        section(list, "DICTATION");
        LinearLayout polish = new LinearLayout(this);
        polish.setOrientation(LinearLayout.HORIZONTAL);
        polish.setGravity(Gravity.CENTER_VERTICAL);
        polish.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        polish.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        LinearLayout polishText = new LinearLayout(this);
        polishText.setOrientation(LinearLayout.VERTICAL);
        polishText.addView(Ui.pixel(this, "AI POLISH", 9, Ui.TEXT));
        polishText.addView(Ui.body(this, settings.cleanup()
                ? "ON  ·  text-only polish with Groq, OpenAI, or Gemini"
                : "OFF  ·  text-only polish with Groq, OpenAI, or Gemini",
                16, settings.cleanup() ? Ui.ACCENT2 : Ui.MUTED));
        polish.addView(polishText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch clean = new Switch(this);
        clean.setChecked(settings.cleanup());
        clean.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        clean.setTrackTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
        clean.setOnCheckedChangeListener((b, c) -> {
            if (c && Settings.LOCAL.equals(settings.provider())) {
                clean.setChecked(false);
                Toast.makeText(this, "On-device mode never sends text to a cloud",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (c && Settings.DEEPGRAM.equals(settings.provider()) && !hasCleanupKey()) {
                clean.setChecked(false);
                Toast.makeText(this, "Deepgram transcribes; add a Groq, OpenAI, or Gemini key to polish",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (c && !Settings.DEEPGRAM.equals(settings.provider())
                    && !secure.has(settings.keyName(settings.provider()))) {
                clean.setChecked(false);
                Toast.makeText(this, "Add a " + Settings.providerName(settings.provider())
                        + " key first", Toast.LENGTH_SHORT).show();
                return;
            }
            settings.setCleanup(c);
            render();
        });
        polish.addView(clean);
        list.addView(polish, top(6));

        LinearLayout context = new LinearLayout(this);
        context.setOrientation(LinearLayout.HORIZONTAL);
        context.setGravity(Gravity.CENTER_VERTICAL);
        context.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        context.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        LinearLayout contextText = new LinearLayout(this);
        contextText.setOrientation(LinearLayout.VERTICAL);
        contextText.addView(Ui.pixel(this, "FIELD CONTEXT", 9, Ui.TEXT));
        contextText.addView(Ui.body(this,
                "Send nearby names and code terms to AI polish. Off by default.",
                16, Ui.MUTED));
        context.addView(contextText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch contextSwitch = new Switch(this);
        contextSwitch.setChecked(settings.fieldContext());
        contextSwitch.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        contextSwitch.setTrackTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
        contextSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (checked && !settings.cleanup()) {
                contextSwitch.setChecked(false);
                Toast.makeText(this, "Turn on AI Polish first", Toast.LENGTH_SHORT).show();
                return;
            }
            settings.setFieldContext(checked);
        });
        context.addView(contextSwitch);
        list.addView(context, top(6));

        LinearLayout command = new LinearLayout(this);
        command.setOrientation(LinearLayout.HORIZONTAL);
        command.setGravity(Gravity.CENTER_VERTICAL);
        command.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        command.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        LinearLayout commandText = new LinearLayout(this);
        commandText.setOrientation(LinearLayout.VERTICAL);
        commandText.addView(Ui.pixel(this, "COMMAND MODE", 9, Ui.TEXT));
        commandText.addView(Ui.body(this,
                "Select text, then hold the bubble. On-device: case, list, and delete. A cloud key adds freeform rewrites.",
                16, Ui.MUTED));
        command.addView(commandText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch commandSwitch = new Switch(this);
        commandSwitch.setChecked(settings.commandMode());
        commandSwitch.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        commandSwitch.setTrackTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
        commandSwitch.setOnCheckedChangeListener((b, checked) -> settings.setCommandMode(checked));
        command.addView(commandSwitch);
        list.addView(command, top(6));

        LinearLayout tone = new LinearLayout(this);
        tone.setOrientation(LinearLayout.HORIZONTAL);
        tone.setGravity(Gravity.CENTER_VERTICAL);
        tone.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        tone.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        LinearLayout toneText = new LinearLayout(this);
        toneText.setOrientation(LinearLayout.VERTICAL);
        toneText.addView(Ui.pixel(this, "APP-AWARE TONE", 9, Ui.TEXT));
        toneText.addView(Ui.body(this, "Chat gets casual, email formal. App name only.",
                16, Ui.MUTED));
        tone.addView(toneText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch toneSwitch = new Switch(this);
        toneSwitch.setChecked(settings.appTone());
        toneSwitch.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        toneSwitch.setTrackTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
        toneSwitch.setOnCheckedChangeListener((b, checked) -> settings.setAppTone(checked));
        tone.addView(toneSwitch);
        list.addView(tone, top(6));

        section(list, "HISTORY");
        LinearLayout retention = new LinearLayout(this);
        retention.setOrientation(LinearLayout.VERTICAL);
        retention.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        retention.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        retention.addView(Ui.pixel(this, "LOCAL RETENTION", 9, Ui.TEXT));
        retention.addView(Ui.body(this, "Transcripts stay on this device only.",
                16, Ui.MUTED));
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        String[] values = {Settings.RETENTION_KEEP, Settings.RETENTION_24H,
                Settings.RETENTION_NEVER};
        for (int i = 0; i < values.length; i++) {
            TextView chip = retentionChip(values[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i < values.length - 1) lp.rightMargin = Ui.dp(this, 6);
            choices.addView(chip, lp);
        }
        retention.addView(choices, top(10));
        list.addView(retention, top(6));

        TextView clear = Ui.action(this, "CLEAR TRANSCRIPTS", Ui.SURFACE2, Ui.DANGER, Ui.TEXT);
        clear.setOnClickListener(v -> confirmClear());
        list.addView(clear, top(14));

        section(list, "BUBBLE");
        LinearLayout bubbleBox = new LinearLayout(this);
        bubbleBox.setOrientation(LinearLayout.VERTICAL);
        bubbleBox.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        bubbleBox.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        bubbleBox.addView(Ui.pixel(this, "SIZE", 9, Ui.TEXT));
        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        addBubbleChip(sizeRow, "SMALL", settings.bubbleSize() == 0,
                () -> chooseBubbleSize(0), 0);
        addBubbleChip(sizeRow, "NORMAL", settings.bubbleSize() == 1,
                () -> chooseBubbleSize(1), 1);
        addBubbleChip(sizeRow, "LARGE", settings.bubbleSize() == 2,
                () -> chooseBubbleSize(2), 2);
        bubbleBox.addView(sizeRow, top(8));
        bubbleBox.addView(Ui.pixel(this, "OPACITY", 9, Ui.TEXT), top(14));
        LinearLayout alphaRow = new LinearLayout(this);
        alphaRow.setOrientation(LinearLayout.HORIZONTAL);
        addBubbleChip(alphaRow, "60%", settings.bubbleAlpha() == 60,
                () -> chooseBubbleAlpha(60), 0);
        addBubbleChip(alphaRow, "80%", settings.bubbleAlpha() == 80,
                () -> chooseBubbleAlpha(80), 1);
        addBubbleChip(alphaRow, "100%", settings.bubbleAlpha() == 100,
                () -> chooseBubbleAlpha(100), 2);
        bubbleBox.addView(alphaRow, top(8));
        list.addView(bubbleBox, top(6));

        section(list, "ABOUT");
        TextView version = Ui.body(this, "Wahtuh Pho 0.3.0  ·  local first",
                17, Ui.MUTED);
        list.addView(version);
        TextView source = Ui.pixel(this, "SOURCE CODE >", 9, Ui.ACCENT2);
        source.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        source.setOnClickListener(v -> openUrl("https://github.com/desukab/wahtuh-pho"));
        list.addView(source);
        return scroll;
    }

    private String polishStatus() {
        if (settings.localOnly() || Settings.LOCAL.equals(settings.provider())) return "";
        if (!settings.cleanup()) return "  ·  polish off";
        boolean ready = Settings.DEEPGRAM.equals(settings.provider())
                ? hasCleanupKey()
                : secure.has(settings.keyName(settings.provider()));
        return ready ? "  ·  polish" : "  ·  polish missing key";
    }

    private boolean hasCleanupKey() {
        return secure.has(settings.keyName(Settings.GROQ))
                || secure.has(settings.keyName(Settings.OPENAI))
                || secure.has(settings.keyName(Settings.GEMINI));
    }

    private View providerCard(String provider, String name, String note) {
        boolean selected = provider.equals(settings.provider());
        boolean cloud = !Settings.LOCAL.equals(provider);
        boolean hasKey = cloud && secure.has(settings.keyName(provider));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.panel(Ui.SURFACE, selected ? Ui.ACCENT : Ui.LINE, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.pixel(this, name, 9, selected ? Ui.ACCENT : Ui.TEXT));
        String sub = note + (hasKey ? "  ·  KEY OK" : (cloud ? "  ·  NO KEY" : ""));
        TextView s = Ui.body(this, sub, 16, hasKey ? Ui.SUCCESS : Ui.MUTED);
        s.setPadding(0, Ui.dp(this, 5), 0, 0);
        text.addView(s);
        card.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (cloud) {
            TextView key = Ui.pixel(this, hasKey ? "KEY" : "ADD", 8, Ui.ACCENT2);
            key.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8));
            key.setOnClickListener(v -> keyDialog(provider));
            card.addView(key);
        }
        card.setOnClickListener(v -> selectProvider(provider));
        return card;
    }

    private void selectProvider(String provider) {
        if (provider.equals(settings.provider())) {
            if (!Settings.LOCAL.equals(provider)) keyDialog(provider);
            return;
        }
        settings.setProvider(provider);
        settings.setLocalOnly(Settings.LOCAL.equals(provider));
        if (Settings.LOCAL.equals(provider)
                || (Settings.DEEPGRAM.equals(provider) && !hasCleanupKey())) {
            settings.setCleanup(false);
        }
        if (!Settings.LOCAL.equals(provider) && !secure.has(settings.keyName(provider))) {
            keyDialog(provider);
        }
        render();
    }

    private void keyDialog(String provider) {
        if (Settings.LOCAL.equals(provider)) return;
        LinearLayout box = dialogBox();
        EditText key = input("Paste " + Settings.providerName(provider) + " API key", true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(key);
        TextView link = Ui.pixel(this, "GET A KEY >", 8, Ui.ACCENT2);
        link.setPadding(0, Ui.dp(this, 12), 0, 0);
        link.setOnClickListener(v -> openUrl(keyUrl(provider)));
        box.addView(link);
        boolean existing = secure.has(settings.keyName(provider));
        AlertDialog dialog = dialog("CONNECT " + Settings.providerName(provider).toUpperCase(Locale.US),
                box, "SAVE", () -> {
                    String value = key.getText().toString().trim();
                    if (value.isEmpty()) return;
                    try {
                        secure.put(settings.keyName(provider), value);
                        Toast.makeText(this, "Key encrypted on this device", Toast.LENGTH_SHORT).show();
                        render();
                    } catch (Exception e) {
                        Toast.makeText(this, "Could not save key", Toast.LENGTH_LONG).show();
                    }
                });
        if (existing) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "REMOVE", (d, w) -> {
                secure.remove(settings.keyName(provider));
                render();
            });
        }
        dialog.show();
    }

    private TextView languageChip(String code) {
        boolean selected = code.equals(settings.language());
        TextView chip = Ui.body(this, Settings.languageLabel(code), 16,
                selected ? Ui.INK : Ui.TEXT);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(Ui.dp(this, 4), Ui.dp(this, 9), Ui.dp(this, 4), Ui.dp(this, 9));
        chip.setBackground(selected
                ? Ui.bevel(Ui.ACCENT, Ui.MAGENTA, Ui.TEXT, this)
                : Ui.panel(Ui.SURFACE2, Ui.LINE, this));
        chip.setOnClickListener(v -> {
            settings.setLanguage(code);
            render();
        });
        return chip;
    }

    private TextView retentionChip(String value) {
        boolean selected = value.equals(settings.retention());
        TextView chip = Ui.body(this, Settings.retentionLabel(value), 15,
                selected ? Ui.INK : Ui.TEXT);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(Ui.dp(this, 4), Ui.dp(this, 9), Ui.dp(this, 4), Ui.dp(this, 9));
        chip.setBackground(selected
                ? Ui.bevel(Ui.ACCENT, Ui.MAGENTA, Ui.TEXT, this)
                : Ui.panel(Ui.SURFACE2, Ui.LINE, this));
        chip.setOnClickListener(v -> chooseRetention(value));
        return chip;
    }

    private void addBubbleChip(LinearLayout row, String text, boolean selected,
                               Runnable action, int index) {
        TextView chip = Ui.body(this, text, 14, selected ? Ui.INK : Ui.TEXT);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(Ui.dp(this, 4), Ui.dp(this, 9), Ui.dp(this, 4), Ui.dp(this, 9));
        chip.setBackground(selected
                ? Ui.bevel(Ui.ACCENT, Ui.MAGENTA, Ui.TEXT, this)
                : Ui.panel(Ui.SURFACE2, Ui.LINE, this));
        chip.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        if (index < 2) lp.rightMargin = Ui.dp(this, 6);
        row.addView(chip, lp);
    }

    private void chooseBubbleSize(int value) {
        settings.setBubbleSize(value);
        render();
    }

    private void chooseBubbleAlpha(int value) {
        settings.setBubbleAlpha(value);
        render();
    }

    private void chooseRetention(String value) {
        if (!Settings.RETENTION_NEVER.equals(value)) {
            settings.setRetention(value);
            render();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Never save transcripts?")
                .setMessage("This deletes every saved transcript and clears saved audio, then stops "
                        + "saving new history on this device.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("NEVER SAVE", (d, w) -> {
                    settings.setRetention(Settings.RETENTION_NEVER);
                    new HistoryStore(this).clear();
                    render();
                }).show();
    }

    private View permissionRow(String name, boolean granted, String action, Runnable fix) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        row.addView(Ui.pixel(this, name, 9, Ui.TEXT), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView status = Ui.pixel(this, granted ? "ON" : "OFF", 8,
                granted ? Ui.SUCCESS : Ui.DANGER);
        status.setPadding(0, 0, Ui.dp(this, 12), 0);
        row.addView(status);
        if (!granted) {
            TextView fixView = Ui.pixel(this, action, 8, Ui.ACCENT);
            fixView.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), 0, Ui.dp(this, 6));
            fixView.setOnClickListener(v -> fix.run());
            row.addView(fixView);
        }
        return row;
    }

    private void section(LinearLayout list, String title) {
        TextView t = Ui.pixel(this, title, 9, Ui.MAGENTA);
        t.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 4));
        list.addView(t);
    }

    private View notice(String tag, String message, String action, Runnable run) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.panel(Ui.SURFACE2, Ui.MAGENTA, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        card.addView(Ui.pixel(this, tag, 8, Ui.MAGENTA));
        TextView body = Ui.body(this, message, 17, Ui.TEXT);
        body.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 10));
        card.addView(body);
        TextView doIt = Ui.pixel(this, action + " >", 8, Ui.ACCENT);
        doIt.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        doIt.setOnClickListener(v -> run.run());
        card.addView(doIt);
        return card;
    }

    private View emptyCard(String title, String hint) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20));
        card.addView(Ui.pixel(this, title, 10, Ui.TEXT));
        TextView h = Ui.body(this, hint, 18, Ui.MUTED);
        h.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(h);
        return card;
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        FrameLayout overlay = new FrameLayout(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View scrim = new View(this);
        scrim.setBackgroundColor(0xB3000000);
        scrim.setOnClickListener(v -> closeDrawer());
        overlay.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int screen = getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(Ui.dp(this, 330), Math.round(screen * 0.87f));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.panel(Ui.BG2, Ui.LINE, this));
        panel.setPadding(Ui.dp(this, 18), Ui.dp(this, 28), Ui.dp(this, 18), Ui.dp(this, 18));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                width, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        overlay.addView(panel, panelParams);

        LinearLayout profile = new LinearLayout(this);
        profile.setOrientation(LinearLayout.HORIZONTAL);
        profile.setGravity(Gravity.CENTER_VERTICAL);
        profile.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        profile.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_wave);
        icon.setColorFilter(Ui.ACCENT);
        icon.setBackground(Ui.panel(Ui.SURFACE2, Ui.LINE, this));
        icon.setPadding(Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9));
        profile.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.VERTICAL);
        who.setPadding(Ui.dp(this, 12), 0, 0, 0);
        who.addView(Ui.pixel(this, "WAHTUH PHO", 9, Ui.TEXT));
        who.addView(Ui.body(this, settings.providerLabel() + "  ·  "
                + Settings.languageLabel(settings.language()), 16, Ui.MUTED));
        profile.addView(who);
        panel.addView(profile);

        panel.addView(drawerSection("GENERAL"));
        panel.addView(drawerItem("SETTINGS", this::showSettings));
        panel.addView(drawerItem("CLEAR TRANSCRIPTS", this::confirmClear));
        panel.addView(drawerSection("SUPPORT"));
        panel.addView(drawerItem("REPORT A BUG", () ->
                openUrl("https://github.com/desukab/wahtuh-pho/issues")));
        panel.addView(drawerItem("SOURCE CODE", () ->
                openUrl("https://github.com/desukab/wahtuh-pho")));
        panel.addView(drawerSection("CARTRIDGE"));
        panel.addView(drawerItem("OFFLINE ARCADE", this::openArcade));
        View spacer = new View(this);
        panel.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        panel.addView(Ui.body(this, "v0.3.0  ·  local first  ·  insert coin", 16, Ui.MUTED));

        panel.setTranslationX(-width);
        overlay.setAlpha(0f);
        root.addView(overlay);
        drawer = overlay;
        overlay.animate().alpha(1f).setDuration(140).start();
        panel.animate().translationX(0f).setDuration(180).start();
    }

    private View drawerSection(String text) {
        TextView t = Ui.pixel(this, text, 8, Ui.MAGENTA);
        t.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 6));
        return t;
    }

    private View drawerItem(String text, Runnable run) {
        TextView item = Ui.pixel(this, text + "  >", 10, Ui.TEXT);
        item.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        item.setOnClickListener(v -> {
            closeDrawer();
            main.postDelayed(run, 140);
        });
        return item;
    }

    private void closeDrawer() {
        if (!drawerOpen || drawer == null) return;
        drawerOpen = false;
        View overlay = drawer;
        drawer = null;
        View panel = ((ViewGroup) overlay).getChildAt(1);
        panel.animate().translationX(-panel.getWidth()).setDuration(150).start();
        overlay.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            if (overlay.getParent() != null) root.removeView(overlay);
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) {
            closeDrawer();
            return;
        }
        if (tab != 0) {
            showTab(0);
            return;
        }
        super.onBackPressed();
    }

    private void confirmDelete(HistoryEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("Delete transcript?")
                .setMessage("This removes the saved text.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("DELETE", (d, w) -> {
                    new HistoryStore(this).remove(entry.id);
                    render();
                }).show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear transcripts?")
                .setMessage("Every saved dictation will be deleted.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CLEAR", (d, w) -> {
                    new HistoryStore(this).clear();
                    render();
                }).show();
    }

    private void ensureMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            render();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    private void overlaySettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, "Open display settings manually", Toast.LENGTH_LONG).show();
        }
    }

    private boolean batteryExempt() {
        android.os.PowerManager pm =
                (android.os.PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void batterySettings() {
        try {
            startActivity(new Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(
                        android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, "Open battery settings manually", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void accessibilitySettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Open accessibility settings manually", Toast.LENGTH_LONG).show();
        }
    }

    private void startBubble() {
        if (!InsertAccessibilityService.isEnabled(this)) {
            Toast.makeText(this, "Enable text insertion first; the bubble appears only on text fields",
                    Toast.LENGTH_LONG).show();
            accessibilitySettings();
            return;
        }
        try {
            startForegroundService(new Intent(this, DictationService.class));
            Toast.makeText(this, "Bubble online", Toast.LENGTH_SHORT).show();
            main.postDelayed(this::render, 350);
        } catch (Exception e) {
            Toast.makeText(this, "Could not start bubble", Toast.LENGTH_LONG).show();
        }
    }

    private void stopBubble() {
        try {
            startService(new Intent(this, DictationService.class)
                    .setAction(DictationService.ACTION_CLOSE));
            Toast.makeText(this, "Bubble hidden", Toast.LENGTH_SHORT).show();
            main.postDelayed(this::render, 250);
        } catch (Exception e) {
            Toast.makeText(this, "Could not hide bubble", Toast.LENGTH_LONG).show();
        }
    }

    private void openArcade() {
        startActivity(new Intent(this, ArcadeActivity.class));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_MIC && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Mic online", Toast.LENGTH_SHORT).show();
        }
        render();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        render();
    }

    private boolean micGranted() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean notificationsGranted() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private static String providerName(String provider) {
        if (provider == null) return "LOCAL";
        if (provider.startsWith("cloud:")) return provider.substring(6).toUpperCase(Locale.US);
        if (Settings.LOCAL.equals(provider)) return "LOCAL";
        return provider.toUpperCase(Locale.US);
    }

    private static String groupLabel(long ms) {
        String day = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(ms));
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        String yesterday = new SimpleDateFormat("yyyyMMdd", Locale.US)
                .format(new Date(System.currentTimeMillis() - 86400000L));
        if (day.equals(today)) return "TODAY";
        if (day.equals(yesterday)) return "YESTERDAY";
        return new SimpleDateFormat("d MMM", Locale.getDefault())
                .format(new Date(ms)).toUpperCase(Locale.getDefault());
    }

    private List<String> dictionaryEntries() {
        List<String> out = new ArrayList<>();
        for (String line : settings.userDictionary().split("\n")) {
            String s = line.trim();
            if (!s.isEmpty() && !s.startsWith("#")) out.add(s);
        }
        return out;
    }

    private List<Snippets.Entry> snippetsList() {
        return new ArrayList<>(Snippets.parse(settings.snippets()).entries());
    }

    private static String join(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        return out.toString();
    }

    private static String joinSnippets(List<Snippets.Entry> entries) {
        StringBuilder out = new StringBuilder();
        for (Snippets.Entry entry : entries) {
            if (out.length() > 0) out.append('\n');
            out.append(entry.trigger).append('=').append(entry.expansion.replace('\n', ' '));
        }
        return out.toString();
    }

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Wahtuh Pho", text));
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show();
        }
    }

    private static String keyUrl(String provider) {
        switch (provider) {
            case Settings.GROQ: return "https://console.groq.com/keys";
            case Settings.GEMINI: return "https://aistudio.google.com/apikey";
            case Settings.DEEPGRAM: return "https://console.deepgram.com/";
            default: return "https://platform.openai.com/api-keys";
        }
    }

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 4));
        return box;
    }

    private EditText input(String hint, boolean multiline) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(Ui.MUTED);
        input.setTextColor(Ui.TEXT);
        input.setTextSize(18);
        input.setTypeface(Ui.body(this));
        input.setBackground(Ui.panel(Ui.SURFACE2, Ui.LINE, this));
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        if (multiline) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setMinLines(3);
            input.setGravity(Gravity.TOP);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setSingleLine(true);
        }
        return input;
    }

    private AlertDialog dialog(String title, View body, String positive, Runnable action) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(body)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton(positive, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    action.run();
                    dialog.dismiss();
                }));
        return dialog;
    }

    private View space(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(this, dp)));
        return v;
    }

    private LinearLayout.LayoutParams top(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, dp);
        return lp;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
