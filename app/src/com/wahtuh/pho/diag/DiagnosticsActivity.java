package com.wahtuh.pho.diag;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.wahtuh.pho.asr.AsrManager;
import com.wahtuh.pho.asr.AsrOutput;
import com.wahtuh.pho.asr.ModelInstaller;
import com.wahtuh.pho.audio.SpeechAudio;
import com.wahtuh.pho.audio.Wav;
import com.wahtuh.pho.data.SecureStore;
import com.wahtuh.pho.data.Settings;
import com.wahtuh.pho.text.Dictionary;
import com.wahtuh.pho.text.Metrics;
import com.wahtuh.pho.text.ProcessedResult;
import com.wahtuh.pho.text.TextPipeline;
import com.wahtuh.pho.ui.Ui;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs local correctness checks and repeats the ASR measurements this project
 * was designed around. It never uploads audio and never calls a cloud provider.
 */
public final class DiagnosticsActivity extends Activity {
    public static final String TAG = "WahtuhDiag";
    private static final String EN_REFERENCE =
            "AFTER EARLY NIGHTFALL THE YELLOW LAMPS WOULD LIGHT UP HERE AND THERE "
                    + "THE SQUALID QUARTER OF THE BROTHELS";
    private static final String TE_REFERENCE =
            "నమస్కారం ఈ రోజు వాతావరణం చాలా బాగుంది";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private TextView log;
    private Button testsButton;
    private Button enButton;
    private Button teButton;
    private int passed;
    private int total;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        final int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, Ui.dp(this, 12));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(pad, pad + insets.getSystemWindowInsetTop(), pad,
                    Ui.dp(this, 12) + insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        top.addView(Ui.text(this, "Diagnostics", 24, Ui.TEXT, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView back = Ui.text(this, "Back", 14, Ui.ACCENT2, false);
        back.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), 0, Ui.dp(this, 10));
        back.setOnClickListener(v -> finish());
        top.addView(back);
        root.addView(top);

        TextView note = Ui.text(this,
                "Local checks plus the exact benchmark path used by the app. English sample uses a "
                        + "bundled reference clip. Telugu sample is synthetic Google Translate TTS, not "
                        + "human-verified speech.", 11, Ui.MUTED, false);
        note.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
        root.addView(note);

        testsButton = Ui.button(this, "Run local tests", Ui.SURFACE, Ui.TEXT);
        testsButton.setOnClickListener(v -> runLocalTests());
        root.addView(testsButton);

        enButton = Ui.button(this, "Benchmark English (Whisper tiny)", Ui.SURFACE, Ui.TEXT);
        enButton.setOnClickListener(v -> benchmark("en"));
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        b1.topMargin = Ui.dp(this, 6);
        root.addView(enButton, b1);

        teButton = Ui.button(this, "Benchmark Telugu (Vosk small te)", Ui.SURFACE, Ui.TEXT);
        teButton.setOnClickListener(v -> benchmark("te"));
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        b2.topMargin = Ui.dp(this, 6);
        root.addView(teButton, b2);

        Button copy = Ui.button(this, "Copy log", Ui.SURFACE, Ui.TEXT);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Wahtuh diagnostics", log.getText()));
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams b3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        b3.topMargin = Ui.dp(this, 6);
        root.addView(copy, b3);

        ScrollView scroll = new ScrollView(this);
        log = Ui.text(this, "", 11, Ui.TEXT, false);
        log.setTextIsSelectable(true);
        log.setTypeface(android.graphics.Typeface.MONOSPACE);
        log.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        log.setBackground(Ui.rounded(Ui.SURFACE, 10, this));
        scroll.addView(log);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sl.topMargin = Ui.dp(this, 8);
        root.addView(scroll, sl);

        setContentView(root);
        append(deviceInfo());
        append("Models ready: " + ModelInstaller.ready(this)
                + " · " + fmt(ModelInstaller.installedBytes(this)) + " unpacked");
        append("Tap a button to run. Nothing here uploads audio.");

        if (getIntent() != null) {
            if (getIntent().getBooleanExtra("tests", false)) {
                main.postDelayed(this::runLocalTests, 300);
            } else if (getIntent().getBooleanExtra("bench_en", false)) {
                main.postDelayed(() -> benchmark("en"), 300);
            } else if (getIntent().getBooleanExtra("bench_te", false)) {
                main.postDelayed(() -> benchmark("te"), 300);
            } else if (getIntent().getBooleanExtra("bench_auto", false)) {
                main.postDelayed(() -> benchmark("auto"), 300);
            } else if (getIntent().hasExtra("mic")) {
                int seconds = Math.max(1, Math.min(10, getIntent().getIntExtra("mic", 3)));
                main.postDelayed(() -> micSmoke(seconds), 300);
            }
        }
    }

    private void micSmoke(int seconds) {
        if (!running.compareAndSet(false, true)) return;
        setBusy(true);
        reset();
        io.execute(() -> {
            try {
                main.post(() -> append("Recording " + seconds + " s from the microphone…"));
                com.wahtuh.pho.audio.AudioCapture capture = new com.wahtuh.pho.audio.AudioCapture();
                capture.start(null);
                Thread.sleep(seconds * 1000L);
                com.wahtuh.pho.audio.AudioCapture.Recording rec = capture.stop();
                double sum = 0;
                for (float s : rec.samples) sum += s * (double) s;
                final double rms = rec.samples.length == 0 ? 0 : Math.sqrt(sum / rec.samples.length);
                main.post(() -> append(String.format(Locale.US,
                        "Captured %d samples (%.2f s) · RMS %.4f · no crash: capture path OK",
                        rec.samples.length, rec.durationMs / 1000.0, rms)));
                asrMic(rec);
            } catch (Throwable e) {
                main.post(() -> append("FAIL mic smoke: " + e));
            } finally {
                running.set(false);
                main.post(() -> setBusy(false));
            }
        });
    }

    private void asrMic(com.wahtuh.pho.audio.AudioCapture.Recording rec) {
        try {
            float[] trimmed = SpeechAudio.trim(rec.samples, rec.sampleRate);
            if (trimmed.length < rec.sampleRate / 2) {
                main.post(() -> append("No speech above the silence threshold; skipping ASR."));
                return;
            }
            main.post(() -> append("Running hybrid ASR on the live capture…"));
            AsrManager.get(this).prepareModels();
            long t0 = System.currentTimeMillis();
            AsrOutput out = AsrManager.get(this).transcribe(trimmed, Settings.LANG_AUTO,
                    Settings.ENGINE_HYBRID);
            long wall = System.currentTimeMillis() - t0;
            main.post(() -> append("Live transcript (" + out.engine + ", lang " + out.language
                    + ", " + wall + " ms): " + out.text));
        } catch (Throwable e) {
            main.post(() -> append("Live ASR failed: " + e));
        }
    }

    private void runLocalTests() {
        if (!running.compareAndSet(false, true)) return;
        setBusy(true);
        reset();
        passed = 0;
        total = 0;
        io.execute(() -> {
            try {
                testPipeline();
                testDictionary();
                testMetrics();
                testWav();
                testAudio();
                testSecureStore();
                final int p = passed, t = total;
                main.post(() -> {
                    append("");
                    append("RESULT: " + p + "/" + t + " checks passed");
                    setBusy(false);
                });
            } catch (Throwable e) {
                main.post(() -> {
                    append("FAIL harness: " + e);
                    setBusy(false);
                });
            } finally {
                running.set(false);
            }
        });
    }

    private void testPipeline() {
        TextPipeline.Options opt = new TextPipeline.Options();
        opt.dictionary = false;
        opt.language = "en";
        ProcessedResult r = new TextPipeline().process("um hello comma world period", opt);
        check("pipeline punctuation + filler", "Hello, world.".equals(r.text), r.text);
        ProcessedResult te = new TextPipeline().process("నమస్కారం కామా ఈ రోజు", opt);
        check("pipeline Telugu comma", te.text.contains("నమస్కారం,"), te.text);
    }

    private void testDictionary() {
        List<Dictionary.Entry> entries = new ArrayList<>();
        entries.add(new Dictionary.Entry("open ai", "OpenAI", false, ""));
        entries.add(new Dictionary.Entry("వాట్సప్", "WhatsApp", false, ""));
        Dictionary dict = new Dictionary(1, entries);
        Dictionary.Applied a = dict.apply("open ai is useful");
        check("dictionary English", a.text.equals("OpenAI is useful"), a.text);
        Dictionary.Applied b = dict.apply("వాట్సప్ మీసేజ్");
        check("dictionary Telugu", b.text.startsWith("WhatsApp"), b.text);
    }

    private void testMetrics() {
        Metrics.Score s = Metrics.score("hello world", "hello world");
        check("WER exact", s.wer == 0.0 && s.cer == 0.0, s.summary());
        Metrics.Score d = Metrics.score("hello world", "hello");
        check("WER deletion", Math.abs(d.wer - 0.5) < 1e-9, d.summary());
    }

    private void testWav() {
        float[] pcm = {0f, 0.5f, -0.5f, 0.25f};
        byte[] wav = Wav.fromFloat(pcm, 16000);
        try {
            Wav.Decoded d = Wav.read(new java.io.ByteArrayInputStream(wav));
            boolean ok = d.sampleRate == 16000 && d.samples.length == pcm.length
                    && Math.abs(d.samples[1] - 0.5f) < 0.001f;
            check("WAV roundtrip", ok, d.samples.length + " samples");
        } catch (Exception e) {
            check("WAV roundtrip", false, e.toString());
        }
    }

    private void testAudio() {
        int sr = 16000;
        float[] split = new float[sr * 3];
        for (int i = 0; i < sr; i++) {
            split[i] = (float) (0.2 * Math.sin(2 * Math.PI * 220 * i / sr));
            split[sr * 2 + i] = split[i];
        }
        List<float[]> parts = SpeechAudio.utterances(split, sr);
        check("silence split", parts.size() == 2, parts.size() + " utterances");

        float[] middle = new float[sr * 3];
        for (int i = 0; i < sr; i++) {
            middle[sr + i] = (float) (0.2 * Math.sin(2 * Math.PI * 220 * i / sr));
        }
        float[] trimmed = SpeechAudio.trim(middle, sr);
        check("silence trim", trimmed.length > sr / 2 && trimmed.length < middle.length,
                trimmed.length + " samples");
    }

    private void testSecureStore() {
        SecureStore store = new SecureStore(this);
        String name = "selftest_key";
        String secret = "sk-selftest-not-a-real-key";
        try {
            store.put(name, secret);
            String raw = store.raw(name);
            boolean decrypted = secret.equals(store.get(name));
            boolean notPlain = raw != null && !raw.contains(secret);
            check("Keystore encryption", decrypted && notPlain,
                    notPlain ? "ciphertext only" : "plaintext leaked");
        } catch (Throwable e) {
            check("Keystore encryption", false, e.toString());
        } finally {
            store.remove(name);
        }
    }

    private void benchmark(String lang) {
        if (!running.compareAndSet(false, true)) return;
        setBusy(true);
        reset();
        final String language = lang;
        io.execute(() -> {
            try {
                String asset = "en".equals(language) ? "test/0.wav" : "test/te1.wav";
                String reference = "en".equals(language) ? EN_REFERENCE : TE_REFERENCE;
                final String engine;
                if ("en".equals(language)) {
                    engine = Settings.ENGINE_WHISPER;
                } else if ("te".equals(language)) {
                    engine = Settings.ENGINE_VOSK;
                } else {
                    engine = Settings.ENGINE_HYBRID;
                }
                main.post(() -> append("Loading " + asset));
                Wav.Decoded wav;
                try (InputStream in = getAssets().open(asset)) {
                    wav = Wav.read(in);
                }
                main.post(() -> append("Audio: " + String.format(Locale.US, "%.2f s @ %d Hz",
                        wav.samples.length / (double) wav.sampleRate, wav.sampleRate)));
                main.post(() -> append("Preparing on-device models (first run unpacks)…"));
                AsrManager.get(this).prepareModels();
                main.post(() -> append("Running " + engine + "…"));
                long t0 = System.currentTimeMillis();
                String asrLanguage = "auto".equals(language) ? Settings.LANG_AUTO : language;
                AsrOutput out = AsrManager.get(this).transcribe(wav.samples, asrLanguage, engine);
                long wall = System.currentTimeMillis() - t0;
                Metrics.Score score = Metrics.score(reference, out.text);
                double audioSec = wav.samples.length / (double) wav.sampleRate;
                String result = "Transcript: " + out.text + "\n"
                        + "Reference: " + reference + "\n"
                        + "Detected language: " + out.language + "\n"
                        + String.format(Locale.US, "Wall %.0f ms · engine %.0f ms · RTF %.3f\n",
                        (double) wall, (double) out.latencyMs, wall / 1000.0 / audioSec)
                        + score.summary();
                main.post(() -> append(result));
            } catch (Throwable e) {
                main.post(() -> append("FAIL: " + e));
            } finally {
                running.set(false);
                main.post(() -> setBusy(false));
            }
        });
    }

    private void setBusy(boolean busy) {
        testsButton.setEnabled(!busy);
        enButton.setEnabled(!busy);
        teButton.setEnabled(!busy);
    }

    private void reset() {
        log.setText("");
    }

    private void check(String name, boolean ok, String detail) {
        total++;
        if (ok) passed++;
        final String line = (ok ? "PASS  " : "FAIL  ") + name + "  [" + detail + "]";
        main.post(() -> append(line));
    }

    private void append(String s) {
        Log.i(TAG, s.replace('\n', ' '));
        log.append(s + "\n");
    }

    private String deviceInfo() {
        String abis = Build.SUPPORTED_ABIS.length > 0 ? String.join(", ", Build.SUPPORTED_ABIS) : "?";
        return "Wahtuh Pho diagnostics\n"
                + "Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                + "Device " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "ABIs " + abis + "\n"
                + "Page size " + android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE) + "\n"
                + "Engine path: hybrid = sherpa-onnx Whisper tiny int8 + Vosk small te 0.42\n"
                + "Cloud calls from this screen: none\n";
    }

    private static String fmt(long bytes) {
        if (bytes <= 0) return "0 MB";
        return String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
