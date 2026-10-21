package com.wahtuh.pho.dictation;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import com.wahtuh.pho.asr.AsrManager;
import com.wahtuh.pho.asr.AsrOutput;
import com.wahtuh.pho.audio.AudioCapture;
import com.wahtuh.pho.audio.SpeechAudio;
import com.wahtuh.pho.audio.Wav;
import com.wahtuh.pho.cloud.CloudTranscriber;
import com.wahtuh.pho.cloud.TranscriptionRouter;
import com.wahtuh.pho.data.HistoryEntry;
import com.wahtuh.pho.data.HistoryStore;
import com.wahtuh.pho.data.SecureStore;
import com.wahtuh.pho.data.Settings;
import com.wahtuh.pho.text.Dictionary;
import com.wahtuh.pho.text.ProcessedResult;
import com.wahtuh.pho.text.TextPipeline;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns one dictation session at a time. Audio never leaves the device unless the
 * user explicitly selected a cloud provider with local-only mode off.
 */
public final class DictationController {
    public static final String IDLE = "idle";
    public static final String PREPARING = "preparing";
    public static final String RECORDING = "recording";
    public static final String TRANSCRIBING = "transcribing";
    public static final String ERROR = "error";

    private static volatile DictationController instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();

    private AudioCapture capture;
    private DictationListener listener;
    private String state = IDLE;
    private int session;
    private boolean busy;

    private DictationController(Context context) {
        this.app = context.getApplicationContext();
    }

    public static DictationController get(Context context) {
        if (instance == null) {
            synchronized (DictationController.class) {
                if (instance == null) instance = new DictationController(context);
            }
        }
        return instance;
    }

    public String state() {
        synchronized (lock) { return state; }
    }

    public boolean busy() {
        synchronized (lock) { return busy; }
    }

    public void start(DictationListener l) {
        final int token;
        synchronized (lock) {
            if (busy) {
                if (l != null) l.onError("A dictation session is already running.");
                return;
            }
            busy = true;
            token = ++session;
            listener = l;
            state = PREPARING;
        }
        emitState(PREPARING, "Preparing microphone");
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            fail(token, "Microphone permission is not granted.");
            return;
        }
        Settings settings = new Settings(app);
        try {
            TranscriptionRouter.assertCloudAllowed(settings);
        } catch (RuntimeException e) {
            fail(token, e.getMessage());
            return;
        }
        final boolean local = Settings.LOCAL.equals(settings.provider());
        io.execute(() -> {
            try {
                if (local) {
                    if (!AsrManager.get(app).modelsReady()) {
                        emitState(PREPARING, "Unpacking on-device models (one time)");
                        AsrManager.get(app).prepareModels();
                    }
                }
                main.post(() -> startCapture(token));
            } catch (Throwable e) {
                main.post(() -> fail(token, "Could not prepare models: " + message(e)));
            }
        });
    }

    private void startCapture(int token) {
        synchronized (lock) {
            if (token != session || !busy) return;
        }
        try {
            AudioCapture c = new AudioCapture();
            c.start(rms -> {
                DictationListener l;
                synchronized (lock) {
                    if (token != session) return;
                    l = listener;
                }
                if (l != null) main.post(() -> l.onLevel(rms));
            });
            synchronized (lock) {
                if (token != session) {
                    c.cancel();
                    return;
                }
                capture = c;
                state = RECORDING;
            }
            emitState(RECORDING, "Listening");
        } catch (Throwable e) {
            fail(token, "Could not start recording: " + message(e));
        }
    }

    public void stop() {
        final AudioCapture c;
        final int token;
        synchronized (lock) {
            if (!busy) return;
            token = session;
            c = capture;
            capture = null;
            if (c == null) {
                // Still preparing; cancel the pending session.
                busy = false;
                session++;
                state = IDLE;
                emitState(IDLE, "Cancelled");
                return;
            }
            state = TRANSCRIBING;
        }
        emitState(TRANSCRIBING, "Transcribing on device");
        io.execute(() -> {
            AudioCapture.Recording rec;
            try {
                rec = c.stop();
            } catch (Throwable e) {
                main.post(() -> fail(token, "Recording failed: " + message(e)));
                return;
            }
            finish(token, rec);
        });
    }

    public void cancel() {
        final AudioCapture c;
        synchronized (lock) {
            session++;
            busy = false;
            state = IDLE;
            c = capture;
            capture = null;
        }
        if (c != null) io.execute(c::cancel);
        emitState(IDLE, "Cancelled");
    }

    private void finish(int token, AudioCapture.Recording rec) {
        long started = System.currentTimeMillis();
        try {
            float[] trimmed = SpeechAudio.trim(rec.samples, rec.sampleRate);
            if (trimmed.length < rec.sampleRate / 4) {
                main.post(() -> fail(token, "No speech detected."));
                return;
            }
            Settings settings = new Settings(app);
            String raw;
            String engine;
            String provider = settings.provider();
            String language = settings.language();

            if (Settings.LOCAL.equals(provider)) {
                emitState(TRANSCRIBING, "Transcribing on device");
                AsrOutput out = AsrManager.get(app).transcribe(trimmed, language, settings.engine());
                raw = out.text;
                engine = out.engine;
                if (out.language != null && !out.language.isEmpty()
                        && Settings.LANG_AUTO.equals(language)) {
                    language = out.language;
                }
            } else {
                TranscriptionRouter.assertCloudAllowed(settings);
                emitState(TRANSCRIBING, "Uploading to " + settings.providerLabel());
                String key = new SecureStore(app).get(settings.keyName(provider));
                String prompt = CloudTranscriber.vocabularyPrompt(settings.userDictionary());
                raw = CloudTranscriber.transcribe(provider, key,
                        Wav.fromFloat(trimmed, rec.sampleRate), settings.cloudLanguage(), prompt);
                engine = "cloud:" + provider;
            }

            if (raw == null || raw.trim().isEmpty()) {
                main.post(() -> fail(token, "No speech recognized."));
                return;
            }

            Dictionary dict = Dictionary.withUserEntries(Dictionary.load(app), settings.userDictionary());
            TextPipeline.Options opt = new TextPipeline.Options(
                    dict, settings.punctuation(), settings.fillers(), language);
            ProcessedResult processed = new TextPipeline().process(raw, opt);

            String text = processed.text;
            boolean cleaned = false;
            if (settings.cleanup() && !Settings.LOCAL.equals(provider)
                    && !text.trim().isEmpty()) {
                emitState(TRANSCRIBING, "Cleaning up text");
                try {
                    String key = new SecureStore(app).get(settings.keyName(provider));
                    String clean = CloudTranscriber.cleanup(provider, key, text);
                    if (clean != null && !clean.trim().isEmpty()) {
                        text = clean.trim();
                        cleaned = true;
                    }
                } catch (Throwable e) {
                    // Cleanup is optional; keep the deterministic result and report it.
                    emitState(TRANSCRIBING, "Cleanup failed, keeping local text");
                }
            }

            long latency = System.currentTimeMillis() - started;
            String finalText = text;
            String finalLanguage = processed.language;
            String finalRaw = raw;
            String finalEngine = engine;
            String finalProvider = provider;
            boolean finalCleaned = cleaned;
            long duration = rec.durationMs;

            String id = UUID.randomUUID().toString();
            String audioPath = settings.keepAudio()
                    ? writeAudio(id, Wav.fromFloat(trimmed, rec.sampleRate)) : "";

            HistoryEntry entry = new HistoryEntry(id, System.currentTimeMillis(), raw, text,
                    engine, provider, finalLanguage, duration, latency, cleaned,
                    editsJson(processed));
            new HistoryStore(app).append(entry);

            DictationResult result = new DictationResult(finalRaw, finalText, processed.edits,
                    finalLanguage, finalEngine, finalProvider, duration, latency, finalCleaned);
            synchronized (lock) {
                if (token != session) return;
                busy = false;
                state = IDLE;
            }
            main.post(() -> {
                DictationListener l;
                synchronized (lock) { l = listener; }
                if (l != null) l.onResult(result);
                emitState(IDLE, "Done");
            });
        } catch (Throwable e) {
            main.post(() -> fail(token, message(e)));
        }
    }

    private void fail(int token, String message) {
        synchronized (lock) {
            if (token != session) return;
            busy = false;
            state = ERROR;
        }
        DictationListener l;
        synchronized (lock) { l = listener; }
        if (l != null) l.onError(message);
        emitState(ERROR, message);
    }

    private void emitState(String s, String message) {
        DictationListener l;
        synchronized (lock) { l = listener; }
        if (l != null) main.post(() -> l.onState(s, message));
    }

    private String writeAudio(String id, byte[] wav) {
        try {
            File dir = new File(app.getFilesDir(), "audio");
            dir.mkdirs();
            File f = new File(dir, id + ".wav");
            try (FileOutputStream out = new FileOutputStream(f)) { out.write(wav); }
            return f.getAbsolutePath();
        } catch (Exception ignored) {}
        return "";
    }

    private static String editsJson(ProcessedResult r) {
        try {
            JSONArray arr = new JSONArray();
            for (com.wahtuh.pho.text.Edit e : r.edits) {
                JSONObject o = new JSONObject();
                o.put("rule", e.rule);
                o.put("from", e.from);
                o.put("to", e.to);
                o.put("index", e.index);
                arr.put(o);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String message(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.trim().isEmpty()) m = t.getClass().getSimpleName();
        return m;
    }
}
