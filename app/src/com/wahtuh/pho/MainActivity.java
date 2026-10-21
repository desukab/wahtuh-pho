package com.wahtuh.pho;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.wahtuh.pho.asr.AsrManager;
import com.wahtuh.pho.asr.ModelInstaller;
import com.wahtuh.pho.data.HistoryEntry;
import com.wahtuh.pho.data.HistoryStore;
import com.wahtuh.pho.data.SecureStore;
import com.wahtuh.pho.data.Settings;
import com.wahtuh.pho.dictation.DictationController;
import com.wahtuh.pho.dictation.DictationListener;
import com.wahtuh.pho.dictation.DictationResult;
import com.wahtuh.pho.diag.DiagnosticsActivity;
import com.wahtuh.pho.service.DictationService;
import com.wahtuh.pho.service.InsertAccessibilityService;
import com.wahtuh.pho.ui.Ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends android.app.Activity implements DictationListener {
    private static final int REQ_MIC = 10;
    private static final int REQ_NOTIF = 11;
    private static final int REQ_OVERLAY = 12;

    private Settings settings;
    private SecureStore secure;
    private DictationController controller;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView status;
    private TextView result;
    private TextView setupStatus;
    private TextView modelStatus;
    private TextView keyStatus;
    private Button testButton;
    private Button bubbleButton;
    private Spinner providerSpinner;
    private Spinner languageSpinner;
    private Spinner engineSpinner;
    private Switch localOnly;
    private Switch cleanup;
    private Switch punctuation;
    private Switch fillers;
    private Switch dictionary;
    private Switch keepAudio;
    private EditText apiKey;
    private EditText userDictionary;

    private int currentTab;
    private boolean loading;
    private boolean startedHere;
    private boolean pendingTest;
    private boolean pendingBubble;
    private String lastResult = "";
    private String lastRaw = "";

    private static final String[] PROVIDER_VALUES = {
            Settings.LOCAL, Settings.GROQ, Settings.OPENAI, Settings.GEMINI};
    private static final String[] PROVIDER_LABELS = {
            "On-device (private)", "Groq Whisper (cloud)", "OpenAI (cloud)", "Google Gemini (cloud)"};
    private static final String[] LANGUAGE_VALUES = {
            Settings.LANG_AUTO, Settings.LANG_EN, Settings.LANG_TE};
    private static final String[] LANGUAGE_LABELS = {
            "Auto (English + Telugu)", "English", "Telugu"};
    private static final String[] ENGINE_VALUES = {
            Settings.ENGINE_HYBRID, Settings.ENGINE_WHISPER, Settings.ENGINE_VOSK};
    private static final String[] ENGINE_LABELS = {
            "Hybrid: Whisper English + Vosk Telugu", "Whisper tiny only", "Vosk Telugu only"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = new Settings(this);
        secure = new SecureStore(this);
        controller = DictationController.get(this);

        status = findViewById(R.id.status);
        result = findViewById(R.id.result);
        setupStatus = findViewById(R.id.setup_status);
        modelStatus = findViewById(R.id.model_status);
        keyStatus = findViewById(R.id.key_status);
        testButton = findViewById(R.id.test_button);
        bubbleButton = findViewById(R.id.bubble_button);
        providerSpinner = findViewById(R.id.provider_spinner);
        languageSpinner = findViewById(R.id.language_spinner);
        engineSpinner = findViewById(R.id.engine_spinner);
        localOnly = findViewById(R.id.local_only_switch);
        cleanup = findViewById(R.id.cleanup_switch);
        punctuation = findViewById(R.id.punctuation_switch);
        fillers = findViewById(R.id.fillers_switch);
        dictionary = findViewById(R.id.dictionary_switch);
        keepAudio = findViewById(R.id.keep_audio_switch);
        apiKey = findViewById(R.id.api_key);
        userDictionary = findViewById(R.id.user_dictionary);

        providerSpinner.setAdapter(darkAdapter(PROVIDER_LABELS));
        languageSpinner.setAdapter(darkAdapter(LANGUAGE_LABELS));
        engineSpinner.setAdapter(darkAdapter(ENGINE_LABELS));

        bindData();
        bindActions();
        bindTabs();
        refreshSetup();
        refreshModelStatus();
        refreshKeyStatus();
        refreshHome();

        if (getIntent() != null && getIntent().getBooleanExtra("request_mic", false)) {
            ensureMic();
        }
    }

    private ArrayAdapter<String> darkAdapter(String[] labels) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                t.setTextColor(Ui.TEXT);
                t.setTextSize(15);
                t.setPadding(Ui.dp(MainActivity.this, 4), 0, 0, 0);
                return t;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getDropDownView(position, convertView, parent);
                t.setTextColor(Ui.TEXT);
                t.setBackgroundColor(Ui.SURFACE);
                t.setTextSize(15);
                t.setPadding(Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 10),
                        Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 10));
                return t;
            }
        };
    }

    private void bindData() {
        loading = true;
        localOnly.setChecked(settings.localOnly());
        cleanup.setChecked(settings.cleanup());
        punctuation.setChecked(settings.punctuation());
        fillers.setChecked(settings.fillers());
        dictionary.setChecked(settings.dictionary());
        keepAudio.setChecked(settings.keepAudio());
        userDictionary.setText(settings.userDictionary());
        providerSpinner.setSelection(indexOf(PROVIDER_VALUES, settings.provider()));
        languageSpinner.setSelection(indexOf(LANGUAGE_VALUES, settings.language()));
        engineSpinner.setSelection(indexOf(ENGINE_VALUES, settings.engine()));
        loading = false;
        updateCleanupState();
    }

    private void bindActions() {
        testButton.setOnClickListener(v -> {
            if (startedHere && controller.busy()) {
                controller.stop();
                return;
            }
            if (controller.busy()) {
                status.setText("A dictation session is running from the bubble or keyboard.");
                return;
            }
            if (!ensureMic()) return;
            startedHere = true;
            controller.start(this);
        });

        findViewById(R.id.copy_button).setOnClickListener(v -> copy(lastResult, "Transcript copied"));
        findViewById(R.id.clear_result_button).setOnClickListener(v -> {
            lastResult = "";
            lastRaw = "";
            result.setText("Your transcript will appear here.");
            status.setText("Ready");
        });
        result.setOnLongClickListener(v -> {
            if (!lastRaw.isEmpty()) {
                copy(lastRaw, "Raw transcript copied");
                return true;
            }
            return false;
        });

        providerSpinner.setOnItemSelectedListener(new SimpleItemSelected(pos -> {
            if (loading) return;
            chooseProvider(pos);
        }));
        languageSpinner.setOnItemSelectedListener(new SimpleItemSelected(pos -> {
            if (loading) return;
            settings.setLanguage(LANGUAGE_VALUES[pos]);
        }));
        engineSpinner.setOnItemSelectedListener(new SimpleItemSelected(pos -> {
            if (loading) return;
            settings.setEngine(ENGINE_VALUES[pos]);
        }));

        localOnly.setOnCheckedChangeListener((b, checked) -> {
            if (loading) return;
            settings.setLocalOnly(checked);
            if (checked && !Settings.LOCAL.equals(settings.provider())) {
                settings.setProvider(Settings.LOCAL);
                loading = true;
                providerSpinner.setSelection(0);
                loading = false;
                Toast.makeText(this, "Provider switched to on-device", Toast.LENGTH_SHORT).show();
            }
            updateCleanupState();
            refreshKeyStatus();
        });
        cleanup.setOnCheckedChangeListener((b, checked) -> {
            if (loading) return;
            if (!cleanup.isEnabled()) {
                loading = true;
                cleanup.setChecked(false);
                loading = false;
                Toast.makeText(this, "AI cleanup requires a cloud provider and local-only off",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            settings.setCleanup(checked);
        });
        punctuation.setOnCheckedChangeListener((b, c) -> settings.setPunctuation(c));
        fillers.setOnCheckedChangeListener((b, c) -> settings.setFillers(c));
        dictionary.setOnCheckedChangeListener((b, c) -> settings.setDictionary(c));
        keepAudio.setOnCheckedChangeListener((b, c) -> settings.setKeepAudio(c));

        findViewById(R.id.save_settings_button).setOnClickListener(v -> {
            settings.setUserDictionary(userDictionary.getText().toString());
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.save_key_button).setOnClickListener(v -> saveKey());
        findViewById(R.id.clear_key_button).setOnClickListener(v -> {
            String provider = settings.provider();
            if (Settings.LOCAL.equals(provider)) {
                Toast.makeText(this, "On-device mode needs no key", Toast.LENGTH_SHORT).show();
                return;
            }
            secure.remove(settings.keyName(provider));
            apiKey.setText("");
            refreshKeyStatus();
            Toast.makeText(this, "Key removed", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.enable_ime_button).setOnClickListener(v -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Open keyboard settings manually", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.pick_ime_button).setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        findViewById(R.id.accessibility_button).setOnClickListener(v -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Open accessibility settings manually", Toast.LENGTH_SHORT).show();
            }
        });
        bubbleButton.setOnClickListener(v -> toggleBubble());

        findViewById(R.id.prepare_models_button).setOnClickListener(v -> prepareModels());
        findViewById(R.id.history_button).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.diagnostics_button).setOnClickListener(v ->
                startActivity(new Intent(this, DiagnosticsActivity.class)));
        findViewById(R.id.clear_history_button).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Clear history?")
                .setMessage("This deletes local transcripts and edit records. Recordings are not affected.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    new HistoryStore(this).clear();
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                }).show());
    }

    private void chooseProvider(int pos) {
        String chosen = PROVIDER_VALUES[pos];
        if (chosen.equals(settings.provider())) {
            refreshKeyStatus();
            return;
        }
        if (!Settings.LOCAL.equals(chosen) && settings.localOnly()) {
            new AlertDialog.Builder(this)
                    .setTitle("Allow cloud transcription?")
                    .setMessage("Local-only mode is on. Selecting " + PROVIDER_LABELS[pos]
                            + " sends the audio you record to that provider. Turn local-only off?")
                    .setNegativeButton("Keep local", (d, w) -> resetProviderSpinner())
                    .setPositiveButton("Allow cloud", (d, w) -> {
                        settings.setLocalOnly(false);
                        loading = true;
                        localOnly.setChecked(false);
                        loading = false;
                        settings.setProvider(chosen);
                        updateCleanupState();
                        refreshKeyStatus();
                    })
                    .setOnCancelListener(d -> resetProviderSpinner())
                    .show();
            return;
        }
        settings.setProvider(chosen);
        updateCleanupState();
        refreshKeyStatus();
    }

    private void resetProviderSpinner() {
        loading = true;
        providerSpinner.setSelection(indexOf(PROVIDER_VALUES, settings.provider()));
        loading = false;
    }

    private void updateCleanupState() {
        boolean canCloud = !Settings.LOCAL.equals(settings.provider()) && !settings.localOnly();
        cleanup.setEnabled(canCloud);
        if (!canCloud && settings.cleanup()) {
            settings.setCleanup(false);
            loading = true;
            cleanup.setChecked(false);
            loading = false;
        }
    }

    private void saveKey() {
        String provider = settings.provider();
        if (Settings.LOCAL.equals(provider)) {
            Toast.makeText(this, "Choose a cloud provider first", Toast.LENGTH_SHORT).show();
            return;
        }
        String value = apiKey.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, secure.has(settings.keyName(provider))
                    ? "A key is already saved. Enter a new key to replace it."
                    : "Enter an API key first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            secure.put(settings.keyName(provider), value);
            apiKey.setText("");
            refreshKeyStatus();
            Toast.makeText(this, "Key encrypted and saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save key: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshKeyStatus() {
        String provider = settings.provider();
        if (Settings.LOCAL.equals(provider)) {
            keyStatus.setText("On-device mode: no API key needed. Audio is not uploaded.");
        } else if (secure.has(settings.keyName(provider))) {
            keyStatus.setText("Key saved for " + settings.providerLabel() + " (encrypted, not displayed).");
        } else {
            keyStatus.setText("No key saved for " + settings.providerLabel() + ".");
        }
    }

    private boolean ensureMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        pendingTest = true;
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        return false;
    }

    private void toggleBubble() {
        if (DictationService.running) {
            stopService(new Intent(this, DictationService.class));
            DictationService.running = false;
            bubbleButton.setText("Start floating bubble");
            return;
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            pendingBubble = true;
            try {
                startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())), REQ_OVERLAY);
            } catch (Exception e) {
                Toast.makeText(this, "Grant 'Display over other apps' manually", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingBubble = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        Intent i = new Intent(this, DictationService.class);
        startForegroundService(i);
        main.postDelayed(this::refreshSetup, 400);
    }

    private void bindTabs() {
        findViewById(R.id.tab_home).setOnClickListener(v -> showTab(0));
        findViewById(R.id.tab_dictionary).setOnClickListener(v -> showTab(1));
        findViewById(R.id.tab_settings).setOnClickListener(v -> showTab(2));
        findViewById(R.id.home_settings_button).setOnClickListener(v -> showTab(2));
        findViewById(R.id.home_history_button).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.recent_view_all).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.home_setup_card).setOnClickListener(v -> showTab(2));
        int startTab = getIntent() == null ? 0 : getIntent().getIntExtra("tab", 0);
        showTab(Math.max(0, Math.min(2, startTab)));
    }

    private void showTab(int index) {
        currentTab = index;
        int[] panels = {R.id.home_content, R.id.dictionary_content, R.id.settings_content};
        int[] icons = {R.id.tab_home_icon, R.id.tab_dictionary_icon, R.id.tab_settings_icon};
        int[] labels = {R.id.tab_home_label, R.id.tab_dictionary_label, R.id.tab_settings_label};
        for (int i = 0; i < panels.length; i++) {
            findViewById(panels[i]).setVisibility(i == index ? View.VISIBLE : View.GONE);
            int color = i == index ? Ui.ACCENT : Ui.MUTED;
            ((ImageView) findViewById(icons[i])).setColorFilter(color);
            ((TextView) findViewById(labels[i])).setTextColor(color);
        }
        if (index == 0) refreshHome();
    }

    private void refreshHome() {
        List<HistoryEntry> entries = new HistoryStore(this).list();
        int words = 0;
        for (HistoryEntry e : entries) {
            String text = e.text == null ? "" : e.text.trim();
            if (!text.isEmpty()) words += text.split("\\s+").length;
        }
        ((TextView) findViewById(R.id.stat_words)).setText(String.valueOf(words));
        ((TextView) findViewById(R.id.stat_sessions)).setText(String.valueOf(entries.size()));

        LinearLayout recent = findViewById(R.id.home_recent);
        recent.removeAllViews();
        if (entries.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setBackground(Ui.rounded(Ui.SURFACE, 18, this));
            empty.setPadding(Ui.dp(this, 14), Ui.dp(this, 16),
                    Ui.dp(this, 14), Ui.dp(this, 16));
            TextView t = Ui.text(this, "No dictations yet. Tap Start dictation to try it.", 14,
                    Ui.MUTED, false);
            empty.addView(t);
            recent.addView(empty);
            return;
        }
        for (int i = 0; i < Math.min(3, entries.size()); i++) {
            recent.addView(recentCard(entries.get(i)));
        }
    }

    private View recentCard(HistoryEntry entry) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(Ui.SURFACE2, 18, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        card.setLayoutParams(lp);

        TextView text = Ui.text(this, entry.text, 15, Ui.TEXT, false);
        text.setMaxLines(3);
        text.setLineSpacing(Ui.dp(this, 2), 1f);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(text);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(android.view.Gravity.CENTER_VERTICAL);
        meta.setPadding(0, Ui.dp(this, 8), 0, 0);
        TextView when = Ui.text(this, relativeTime(entry.createdAt) + " · " + entry.engine
                + " · " + entry.language, 11, Ui.MUTED, false);
        when.setMaxLines(1);
        meta.addView(when, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView copy = Ui.text(this, "Copy", 12, Ui.ACCENT, false);
        copy.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), 0, Ui.dp(this, 4));
        copy.setOnClickListener(v -> copy(entry.text, "Transcript copied"));
        meta.addView(copy);
        card.addView(meta);
        card.setOnClickListener(v -> copy(entry.text, "Transcript copied"));
        return card;
    }

    private static String relativeTime(long ms) {
        Date date = new Date(ms);
        String day = new SimpleDateFormat("yyyyMMdd", Locale.US).format(date);
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        String yesterday = new SimpleDateFormat("yyyyMMdd", Locale.US)
                .format(new Date(System.currentTimeMillis() - 86400000L));
        if (day.equals(today)) return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
        if (day.equals(yesterday)) return "Yesterday";
        return new SimpleDateFormat("dd MMM", Locale.getDefault()).format(date);
    }

    private void prepareModels() {
        prepareButton(false, "Preparing models…");
        modelStatus.setText("Unpacking bundled models. This runs once and uses local storage only.");
        io.execute(() -> {
            try {
                AsrManager.get(this).prepareModels();
                main.post(() -> {
                    prepareButton(true, "Prepare on-device models");
                    modelStatus.setText(modelSummary());
                    Toast.makeText(this, "On-device models are ready", Toast.LENGTH_SHORT).show();
                });
            } catch (Throwable e) {
                main.post(() -> {
                    prepareButton(true, "Retry model preparation");
                    modelStatus.setText("Model preparation failed: " + e.getMessage());
                });
            }
        });
    }

    private Button prepareButton(boolean enabled, String text) {
        Button b = findViewById(R.id.prepare_models_button);
        b.setEnabled(enabled);
        b.setText(text);
        return b;
    }

    private void refreshModelStatus() {
        io.execute(() -> {
            boolean ready = ModelInstaller.ready(this);
            long bytes = ModelInstaller.installedBytes(this);
            String summary = ready
                    ? "On-device models ready · " + fmt(bytes) + " unpacked"
                    : "On-device models not unpacked yet" + (bytes > 0 ? " · partial " + fmt(bytes) : "")
                    + ". Hybrid uses Whisper tiny + Vosk Telugu.";
            main.post(() -> modelStatus.setText(summary));
        });
    }

    private static String fmt(long bytes) {
        if (bytes <= 0) return "0 MB";
        double mb = bytes / (1024.0 * 1024.0);
        return mb >= 1024 ? String.format(java.util.Locale.US, "%.2f GB", mb / 1024)
                : String.format(java.util.Locale.US, "%.0f MB", mb);
    }

    private String modelSummary() {
        boolean ready = ModelInstaller.ready(this);
        long bytes = ModelInstaller.installedBytes(this);
        return ready ? "On-device models ready · " + fmt(bytes) + " unpacked"
                : "Models not ready · " + fmt(bytes);
    }

    private void refreshSetup() {
        boolean ime = isImeEnabled();
        boolean access = InsertAccessibilityService.isEnabled(this);
        boolean overlay = android.provider.Settings.canDrawOverlays(this);
        setupStatus.setText("Voice keyboard: " + (ime ? "enabled" : "off")
                + " · Text insertion: " + (access ? "enabled" : "off")
                + " · Floating bubble: " + (overlay ? "allowed" : "permission needed"));
        bubbleButton.setText(DictationService.running
                ? "Stop floating bubble" : "Start floating bubble");
    }

    private boolean isImeEnabled() {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) return false;
        for (android.view.inputmethod.InputMethodInfo info : imm.getEnabledInputMethodList()) {
            if (getPackageName().equals(info.getPackageName())) return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSetup();
        refreshKeyStatus();
        refreshModelStatus();
        if (currentTab == 0) refreshHome();
        if (pendingBubble && android.provider.Settings.canDrawOverlays(this)) {
            pendingBubble = false;
            main.postDelayed(this::toggleBubble, 200);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) refreshSetup();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_MIC) {
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingBubble) {
                pendingBubble = false;
                main.postDelayed(this::toggleBubble, 200);
            } else if (granted && pendingTest) {
                pendingTest = false;
                startedHere = true;
                controller.start(this);
            } else if (!granted) {
                pendingTest = false;
                pendingBubble = false;
                status.setText("Microphone permission is required");
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (startedHere && controller.busy()) controller.cancel();
        io.shutdownNow();
        super.onDestroy();
    }

    private void copy(String text, String toast) {
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Wahtuh Pho", text));
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return 0;
    }

    @Override
    public void onState(String state, String message) {
        int dot = Ui.SUCCESS;
        if (DictationController.RECORDING.equals(state)) {
            testButton.setText("Stop and transcribe");
            testButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.DANGER));
            dot = Ui.DANGER;
        } else if (DictationController.TRANSCRIBING.equals(state)
                || DictationController.PREPARING.equals(state)) {
            testButton.setText("Working…");
            testButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT2));
            dot = Ui.ACCENT;
        } else if (DictationController.ERROR.equals(state)) {
            testButton.setText("Start dictation");
            testButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
            dot = Ui.DANGER;
        } else {
            testButton.setText("Start dictation");
            testButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        }
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        shape.setColor(dot);
        findViewById(R.id.status_dot).setBackground(shape);
        if (message != null && !message.isEmpty()) status.setText(message);
        if (DictationController.IDLE.equals(state)) startedHere = false;
    }

    @Override
    public void onLevel(float rms) {
        testButton.setAlpha(0.75f + Math.min(0.25f, rms * 4f));
    }

    @Override
    public void onResult(DictationResult result) {
        if (result == null) return;
        lastResult = result.text;
        lastRaw = result.raw;
        this.result.setText(result.text);
        status.setText(result.engine + " · " + result.language + " · " + result.latencyMs
                + " ms for " + (result.durationMs / 1000.0) + " s");
        testButton.setText("Start dictation");
        testButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        startedHere = false;
        refreshHome();
    }

    @Override
    public void onError(String message) {
        status.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        testButton.setText("Start dictation");
        testButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        startedHere = false;
    }

    private static class SimpleItemSelected
            implements android.widget.AdapterView.OnItemSelectedListener {
        private final java.util.function.IntConsumer onSelected;
        SimpleItemSelected(java.util.function.IntConsumer onSelected) {
            this.onSelected = onSelected;
        }
        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position,
                                   long id) {
            onSelected.accept(position);
        }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    }
}
