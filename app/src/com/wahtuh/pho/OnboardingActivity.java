package com.wahtuh.pho;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.wahtuh.pho.data.SecureStore;
import com.wahtuh.pho.data.Settings;
import com.wahtuh.pho.service.DictationService;
import com.wahtuh.pho.service.InsertAccessibilityService;
import com.wahtuh.pho.ui.ArcadeBackdrop;
import com.wahtuh.pho.ui.Ui;

import java.util.Locale;

public final class OnboardingActivity extends android.app.Activity {
    private static final int REQ_MIC = 20;
    private static final int REQ_NOTIF = 21;

    private Settings settings;
    private SecureStore secure;
    private FrameLayout root;
    private int step;
    private String pendingProvider = Settings.LOCAL;
    private EditText keyInput;

    private static final String[] PROVIDERS = {
            Settings.LOCAL, Settings.GROQ, Settings.GEMINI, Settings.DEEPGRAM,
            Settings.ASSEMBLYAI, Settings.OPENAI};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new Settings(this);
        secure = new SecureStore(this);
        pendingProvider = settings.provider();

        root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(Ui.BG);
        ArcadeBackdrop backdrop = new ArcadeBackdrop(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (step == 5) render();
    }

    @Override
    public void onBackPressed() {
        if (step == 0) {
            super.onBackPressed();
        } else if (step == 4) {
            step = Settings.LOCAL.equals(pendingProvider) ? 1 : 2;
            render();
        } else if (step == 3 || step == 2) {
            step = 1;
            render();
        } else if (step == 5) {
            step = 4;
            render();
        } else if (step == 6) {
            step = 0;
            render();
        } else {
            step--;
            render();
        }
    }

    private void render() {
        while (root.getChildCount() > 1) {
            root.removeViewAt(root.getChildCount() - 1);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, Ui.dp(this, 20));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this, 22), Ui.dp(this, 34), Ui.dp(this, 22), Ui.dp(this, 26));
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        switch (step) {
            case 1: privacy(list); break;
            case 2: providers(list); break;
            case 3: key(list); break;
            case 4: language(list); break;
            case 5: permissions(list); break;
            case 6: done(list); break;
            default: attract(list); break;
        }
        if (step > 0) {
            TextView back = Ui.pixel(this, "< BACK", 8, Ui.MUTED);
            back.setPadding(0, Ui.dp(this, 26), 0, Ui.dp(this, 8));
            back.setOnClickListener(v -> onBackPressed());
            list.addView(back);
        }
        TextView foot = Ui.pixel(this, "LOCAL FIRST  //  YOUR KEYS  //  NO SUBSCRIPTION",
                6, Ui.LINE);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Ui.dp(this, 22), 0, 0);
        list.addView(foot);
    }

    private void attract(LinearLayout list) {
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        list.addView(logo, centered(Ui.dp(this, 108), Ui.dp(this, 108)));

        TextView title = Ui.pixel(this, "WAHTUH PHO", 26, Ui.ACCENT);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(this, 26), 0, 0);
        list.addView(title);

        TextView sub = Ui.pixel(this, "VOICE TO TEXT", 10, Ui.ACCENT2);
        sub.setGravity(Gravity.CENTER);
        sub.setLetterSpacing(0.28f);
        sub.setPadding(0, Ui.dp(this, 14), 0, 0);
        list.addView(sub);

        TextView body = Ui.body(this,
                "Talk anywhere. Your words land in the app you are already using.",
                20, Ui.TEXT);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, Ui.dp(this, 22), 0, 0);
        list.addView(body);

        LinearLayout score = new LinearLayout(this);
        score.setOrientation(LinearLayout.HORIZONTAL);
        score.setGravity(Gravity.CENTER);
        score.setPadding(0, Ui.dp(this, 22), 0, 0);
        score.addView(chip("ON-DEVICE", Ui.ACCENT));
        score.addView(chip("7 LANGUAGES", Ui.ACCENT2));
        score.addView(chip("NO CLOUD", Ui.MAGENTA));
        list.addView(score);

        TextView start = Ui.action(this, "PRESS START", Ui.ACCENT, Ui.MAGENTA, Ui.INK);
        start.setOnClickListener(v -> {
            step = 1;
            render();
        });
        list.addView(start, top(30));

        TextView hint = Ui.body(this, "Runs offline  ·  no account needed",
                17, Ui.MUTED);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.dp(this, 14), 0, 0);
        list.addView(hint);
    }

    private void privacy(LinearLayout list) {
        kicker(list, "PLAYER 1");
        title(list, "USE YOUR OWN\nCLOUD KEYS?");
        paragraph(list, "On-device dictation always works with no account. "
                + "Cloud models are faster on some phones, but they need your own API key. "
                + "Nothing is ever sent unless you choose a cloud model.");

        TextView yes = Ui.action(this, "YES, I HAVE KEYS", Ui.ACCENT, Ui.MAGENTA, Ui.INK);
        yes.setOnClickListener(v -> {
            step = 2;
            render();
        });
        list.addView(yes, top(26));

        TextView no = Ui.action(this, "NO, JUST ON-DEVICE", Ui.SURFACE2, Ui.LINE, Ui.TEXT);
        no.setOnClickListener(v -> {
            pendingProvider = Settings.LOCAL;
            settings.setProvider(Settings.LOCAL);
            settings.setLocalOnly(true);
            step = 4;
            render();
        });
        list.addView(no, top(12));

        TextView note = Ui.body(this, "You can change this any time in Settings.",
                16, Ui.MUTED);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, Ui.dp(this, 16), 0, 0);
        list.addView(note);
    }

    private void providers(LinearLayout list) {
        kicker(list, "SELECT MODE");
        title(list, "PICK A\nTRANSCRIBER");
        paragraph(list, "Whistle runs fully on this phone. Cloud modes use your key "
                + "and send audio only while you dictate.");
        for (String provider : PROVIDERS) {
            list.addView(providerRow(provider), top(9));
        }
    }

    private View providerRow(String provider) {
        boolean selected = provider.equals(pendingProvider);
        boolean local = Settings.LOCAL.equals(provider);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.panel(Ui.SURFACE, selected ? Ui.ACCENT : Ui.LINE, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.pixel(this, Settings.providerName(provider).toUpperCase(Locale.US),
                10, selected ? Ui.ACCENT : Ui.TEXT));
        String note = local ? "OFFLINE  ·  7 LANGUAGES"
                : secure.has(settings.keyName(provider)) ? "YOUR KEY IS SAVED" : "YOUR API KEY";
        TextView sub = Ui.body(this, note, 17, local ? Ui.ACCENT2 : Ui.MUTED);
        sub.setPadding(0, Ui.dp(this, 6), 0, 0);
        text.addView(sub);
        card.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(Ui.pixel(this, selected ? ">>" : "", 12, Ui.MAGENTA));

        card.setOnClickListener(v -> {
            pendingProvider = provider;
            settings.setProvider(provider);
            settings.setLocalOnly(local);
            if (local) {
                step = 4;
            } else if (secure.has(settings.keyName(provider))) {
                step = 4;
            } else {
                step = 3;
            }
            render();
        });
        return card;
    }

    private void key(LinearLayout list) {
        kicker(list, "CONNECT");
        title(list, "PASTE YOUR\n" + Settings.providerName(pendingProvider).toUpperCase(Locale.US)
                + " KEY");
        paragraph(list, "The key is encrypted on this device and used only for your requests. "
                + "It is never uploaded to us.");

        keyInput = new EditText(this);
        keyInput.setHint("API key");
        keyInput.setHintTextColor(Ui.MUTED);
        keyInput.setTextColor(Ui.TEXT);
        keyInput.setTextSize(18);
        keyInput.setTypeface(Ui.body(this));
        keyInput.setSingleLine(true);
        keyInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setBackground(Ui.panel(Ui.SURFACE2, Ui.LINE, this));
        keyInput.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        list.addView(keyInput, top(18));

        TextView save = Ui.action(this, "SAVE KEY", Ui.ACCENT, Ui.MAGENTA, Ui.INK);
        save.setOnClickListener(v -> {
            String value = keyInput.getText().toString().trim();
            if (value.isEmpty()) {
                Toast.makeText(this, "Paste a key first", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                secure.put(settings.keyName(pendingProvider), value);
                step = 4;
                render();
            } catch (Exception e) {
                Toast.makeText(this, "Could not save key", Toast.LENGTH_LONG).show();
            }
        });
        list.addView(save, top(15));

        TextView link = Ui.pixel(this, "WHERE DO I GET A KEY? >", 8, Ui.ACCENT2);
        link.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 8));
        link.setOnClickListener(v -> openUrl(keyUrl(pendingProvider)));
        list.addView(link);

        TextView skip = Ui.body(this, "No key? Go back and use on-device.", 16, Ui.MUTED);
        skip.setPadding(0, Ui.dp(this, 10), 0, 0);
        skip.setOnClickListener(v -> {
            pendingProvider = Settings.LOCAL;
            settings.setProvider(Settings.LOCAL);
            settings.setLocalOnly(true);
            step = 4;
            render();
        });
        list.addView(skip);
    }

    private void language(LinearLayout list) {
        kicker(list, "LANGUAGE");
        title(list, "WHAT DO YOU\nSPEAK?");
        paragraph(list, "Auto-detect works for most people. Pick one language for the "
                + "sharpest local results.");

        LinearLayout row = null;
        for (int i = 0; i < Settings.LANGUAGES.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setPadding(0, Ui.dp(this, 10), 0, 0);
                list.addView(row);
            }
            TextView chip = languageChip(Settings.LANGUAGES[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.rightMargin = Ui.dp(this, 8);
            row.addView(chip, lp);
        }

        TextView next = Ui.action(this, "NEXT", Ui.ACCENT, Ui.MAGENTA, Ui.INK);
        next.setOnClickListener(v -> {
            step = 5;
            render();
        });
        list.addView(next, top(24));
    }

    private TextView languageChip(String code) {
        boolean selected = code.equals(settings.language());
        TextView chip = Ui.body(this, Settings.languageLabel(code), 18,
                selected ? Ui.INK : Ui.TEXT);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(Ui.dp(this, 6), Ui.dp(this, 13), Ui.dp(this, 6), Ui.dp(this, 13));
        chip.setBackground(selected
                ? Ui.bevel(Ui.ACCENT, Ui.MAGENTA, Ui.TEXT, this)
                : Ui.panel(Ui.SURFACE2, Ui.LINE, this));
        chip.setOnClickListener(v -> {
            settings.setLanguage(code);
            render();
        });
        return chip;
    }

    private void permissions(LinearLayout list) {
        kicker(list, "FINAL CHECK");
        title(list, "POWER UP");
        paragraph(list, "Three switches make dictation work. Each opens an Android "
                + "screen; come back when it is on.");

        list.addView(permissionRow("MICROPHONE", micGranted(), this::ensureMic), top(14));
        list.addView(permissionRow("FLOATING BUBBLE", android.provider.Settings.canDrawOverlays(this),
                this::overlaySettings), top(9));
        list.addView(permissionRow("TEXT INSERTION", InsertAccessibilityService.isEnabled(this),
                this::accessibilitySettings), top(9));
        list.addView(permissionRow("NOTIFICATIONS", notificationsGranted(),
                this::askNotifications), top(9));

        boolean ready = micGranted() && android.provider.Settings.canDrawOverlays(this);
        TextView next = Ui.action(this, ready ? "CONTINUE" : "CHECK AGAIN",
                Ui.ACCENT, Ui.MAGENTA, Ui.INK);
        next.setOnClickListener(v -> {
            if (!micGranted() || !android.provider.Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Turn on mic and bubble to continue",
                        Toast.LENGTH_LONG).show();
                render();
                return;
            }
            step = 6;
            render();
        });
        list.addView(next, top(24));

        TextView later = Ui.body(this, "I'll do this later", 16, Ui.MUTED);
        later.setGravity(Gravity.CENTER);
        later.setPadding(0, Ui.dp(this, 16), 0, 0);
        later.setOnClickListener(v -> {
            step = 6;
            render();
        });
        list.addView(later);
    }

    private View permissionRow(String label, boolean on, Runnable fix) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        row.addView(Ui.pixel(this, label, 8, Ui.TEXT), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView status = Ui.pixel(this, on ? "ON" : "OFF", 8,
                on ? Ui.SUCCESS : Ui.DANGER);
        row.addView(status);
        if (!on) {
            TextView go = Ui.pixel(this, "FIX >", 8, Ui.ACCENT);
            go.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8));
            go.setOnClickListener(v -> fix.run());
            row.addView(go);
        }
        return row;
    }

    private void done(LinearLayout list) {
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        list.addView(logo, centered(Ui.dp(this, 88), Ui.dp(this, 88)));

        TextView title = Ui.pixel(this, "YOU'RE ALL SET", 20, Ui.ACCENT);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(this, 24), 0, 0);
        list.addView(title);

        TextView sub = Ui.body(this, settings.providerLabel() + "  ·  "
                + Settings.languageLabel(settings.language()) + "  ·  "
                + (settings.localOnly() ? "stays on device" : "your key"), 20, Ui.TEXT);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Ui.dp(this, 14), 0, 0);
        list.addView(sub);

        paragraph(list, "Open any app and focus a text field. The bubble appears, "
                + "tap it and talk. Drag it to an X to snooze for 5 or 10 minutes.");

        TextView start = Ui.action(this, "ENTER THE ARCADE", Ui.ACCENT, Ui.MAGENTA, Ui.INK);
        start.setOnClickListener(v -> finishOnboarding());
        list.addView(start, top(24));

        TextView bubble = Ui.action(this, "LAUNCH BUBBLE NOW", Ui.SURFACE2, Ui.LINE, Ui.TEXT);
        bubble.setOnClickListener(v -> {
            finishOnboarding();
            if (micGranted() && android.provider.Settings.canDrawOverlays(this)
                    && InsertAccessibilityService.isEnabled(this)) {
                try {
                    startForegroundService(new Intent(this, DictationService.class));
                } catch (Exception ignored) {}
            } else {
                Toast.makeText(this, "Enable text insertion to launch the bubble",
                        Toast.LENGTH_LONG).show();
            }
        });
        list.addView(bubble, top(12));
    }

    private void finishOnboarding() {
        settings.setOnboarded(true);
        if (micGranted() && android.provider.Settings.canDrawOverlays(this)) {
            try {
                startForegroundService(new Intent(this, DictationService.class));
            } catch (Exception ignored) {}
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private View chip(String text, int color) {
        TextView chip = Ui.pixel(this, text, 6, color);
        chip.setBackground(Ui.panel(Ui.SURFACE, Ui.LINE, this));
        chip.setPadding(Ui.dp(this, 8), Ui.dp(this, 7), Ui.dp(this, 8), Ui.dp(this, 7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 6);
        chip.setLayoutParams(lp);
        return chip;
    }

    private void kicker(LinearLayout list, String text) {
        TextView k = Ui.pixel(this, text, 8, Ui.MAGENTA);
        k.setLetterSpacing(0.18f);
        list.addView(k);
    }

    private void title(LinearLayout list, String text) {
        TextView t = Ui.pixel(this, text, 21, Ui.TEXT);
        t.setLineSpacing(Ui.dp(this, 6), 1f);
        t.setPadding(0, Ui.dp(this, 14), 0, 0);
        list.addView(t);
    }

    private void paragraph(LinearLayout list, String text) {
        TextView t = Ui.body(this, text, 20, Ui.MUTED);
        t.setPadding(0, Ui.dp(this, 14), 0, 0);
        list.addView(t);
    }

    private LinearLayout.LayoutParams centered(int width, int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }

    private LinearLayout.LayoutParams top(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, dp);
        return lp;
    }

    private boolean micGranted() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean notificationsGranted() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureMic() {
        if (micGranted()) {
            render();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted()) {
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

    private void accessibilitySettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Open accessibility settings manually", Toast.LENGTH_LONG).show();
        }
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
            case Settings.ASSEMBLYAI: return "https://www.assemblyai.com/dashboard";
            default: return "https://platform.openai.com/api-keys";
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        render();
    }
}
