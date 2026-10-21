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
import com.wahtuh.pho.service.InsertAccessibilityService;
import com.wahtuh.pho.text.Dictionary;
import com.wahtuh.pho.text.ProcessedResult;
import com.wahtuh.pho.text.Snippets;
import com.wahtuh.pho.text.TextPipeline;

import org.json.JSONArray;
import org.json.JSONObject;

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
    private String commandSource;

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
        startInternal(l, null);
    }

    public void startCommand(DictationListener l, String selectedText) {
        startInternal(l, selectedText);
    }

    public boolean commandReady() {
        Settings settings = new Settings(app);
        String provider = settings.provider();
        if (Settings.LOCAL.equals(provider)) return true;
        if (settings.localOnly()) return false;
        try {
            String key = new SecureStore(app).get(settings.keyName(provider));
            return key != null && !key.trim().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void startInternal(DictationListener l, String selectedText) {
        final int token;
        synchronized (lock) {
            if (busy) {
                if (l != null) l.onError("A dictation session is already running.");
                return;
            }
            busy = true;
            token = ++session;
            listener = l;
            commandSource = selectedText;
            state = PREPARING;
        }
        emitState(PREPARING, "Getting ready");
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
                        emitState(PREPARING, "Getting ready");
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
        Settings stopSettings = new Settings(app);
        final String style = effectiveStyle(stopSettings);
        final String context = stopSettings.fieldContext() && stopSettings.cleanup()
                && !Settings.LOCAL.equals(stopSettings.provider())
                ? TextPipeline.contextTerms(
                        InsertAccessibilityService.focusedContext(1200), 40)
                : "";
        synchronized (lock) {
            if (!busy) return;
            token = session;
            c = capture;
            capture = null;
            if (c == null) {
                // Still preparing; cancel the pending session.
                busy = false;
                session++;
                commandSource = null;
                state = IDLE;
                emitState(IDLE, "Cancelled");
                return;
            }
            state = TRANSCRIBING;
        }
        emitState(TRANSCRIBING, "Writing");
        io.execute(() -> {
            AudioCapture.Recording rec;
            try {
                rec = c.stop();
            } catch (Throwable e) {
                main.post(() -> fail(token, "Recording failed: " + message(e)));
                return;
            }
            finish(token, rec, style, context);
        });
    }

    public void cancel() {
        final AudioCapture c;
        synchronized (lock) {
            session++;
            busy = false;
            commandSource = null;
            state = IDLE;
            c = capture;
            capture = null;
        }
        if (c != null) io.execute(c::cancel);
        emitState(IDLE, "Cancelled");
    }

    private void finish(int token, AudioCapture.Recording rec, String style, String context) {
        long started = System.currentTimeMillis();
        String command;
        synchronized (lock) {
            command = commandSource;
            commandSource = null;
        }
        try {
            SpeechAudio.Plan plan = SpeechAudio.analyze(rec.samples, rec.sampleRate);
            if (!plan.hasSpeech()) {
                main.post(() -> fail(token, "No speech detected."));
                return;
            }
            float[] prepared = SpeechAudio.prepare(rec.samples, rec.sampleRate);
            if (prepared.length < rec.sampleRate / 6) {
                main.post(() -> fail(token, "No speech detected."));
                return;
            }

            Settings settings = new Settings(app);
            String provider = settings.provider();
            String language = settings.language();
            String detected = language;
            String engine;
            String raw;

            boolean split = plan.shouldSplit(rec.durationMs) && plan.segments.size() > 1;
            if (split) {
                emitState(TRANSCRIBING, "Writing " + plan.segments.size() + " phrases");
                StringBuilder merged = new StringBuilder();
                engine = Settings.LOCAL.equals(provider) ? "whistle" : "cloud:" + provider;
                for (SpeechAudio.Segment segment : plan.segments) {
                    float[] piece = SpeechAudio.prepare(segment.samples, rec.sampleRate);
                    if (piece.length < rec.sampleRate / 6) continue;
                    EngineResult result = transcribeClip(settings, provider, piece,
                            rec.sampleRate, language, detected);
                    engine = result.engine;
                    if (result.language != null && !result.language.isEmpty()) {
                        detected = result.language;
                    }
                    String part = result.text == null ? "" : result.text.trim();
                    if (part.isEmpty()) continue;
                    if (merged.length() > 0) {
                        merged.append(segment.pauseBeforeMs >= 1200 ? "\n\n" : " ");
                    }
                    merged.append(part);
                }
                raw = merged.toString();
            } else {
                emitState(TRANSCRIBING, Settings.LOCAL.equals(provider)
                        ? "Writing" : "Writing with " + settings.providerLabel());
                EngineResult result = transcribeClip(settings, provider, prepared,
                        rec.sampleRate, language, detected);
                raw = result.text;
                engine = result.engine;
                if (result.language != null && !result.language.isEmpty()) {
                    detected = result.language;
                }
            }

            if (raw == null || raw.trim().isEmpty()) {
                main.post(() -> fail(token, "No speech recognized."));
                return;
            }

            Dictionary dict = Dictionary.withUserEntries(Dictionary.load(app), settings.userDictionary());
            TextPipeline.Options opt = new TextPipeline.Options(
                    dict, settings.punctuation(), settings.fillers(), language);
            opt.snippets = Snippets.parse(settings.snippets());
            opt.style = style;
            ProcessedResult processed = new TextPipeline().process(raw, opt);

            String text = processed.text;
            boolean cleaned = false;
            if (command != null) {
                emitState(TRANSCRIBING, "Applying command");
                String offline = TextPipeline.selectionCommand(command, raw);
                if (offline != null) {
                    text = offline;
                    cleaned = true;
                    engine = "command:local";
                } else {
                    String[] target = cleanupTarget(settings, provider);
                    if (target == null) {
                        main.post(() -> fail(token, "Offline Command Mode supports case, "
                                + "list, and delete commands; add a Groq, OpenAI, or "
                                + "Gemini key for freeform rewrites"));
                        return;
                    }
                    try {
                        String rewritten = CloudTranscriber.command(
                                target[0], target[1], command, raw, style);
                        if (rewritten == null || rewritten.trim().isEmpty()) {
                            main.post(() -> fail(token, "The command returned no text"));
                            return;
                        }
                        text = rewritten.trim();
                        cleaned = true;
                        engine = "command:" + target[0];
                    } catch (Throwable e) {
                        main.post(() -> fail(token, "Command failed: " + message(e)));
                        return;
                    }
                }
            } else {
                String[] target = settings.cleanup() && !Settings.LOCAL.equals(provider)
                        && !text.trim().isEmpty() ? cleanupTarget(settings, provider) : null;
                if (target != null) {
                    emitState(TRANSCRIBING, "Polishing");
                    try {
                        String clean = CloudTranscriber.cleanup(
                                target[0], target[1], text, style, context);
                        if (clean != null && !clean.trim().isEmpty()) {
                            text = clean.trim();
                            cleaned = true;
                        }
                    } catch (Throwable ignored) {}
                    if (!cleaned) emitState(TRANSCRIBING, "Writing");
                }
            }

            long latency = System.currentTimeMillis() - started;
            String finalText = text;
            String finalLanguage = processed.language;
            if (detected != null && !detected.isEmpty()
                    && !Settings.LANG_AUTO.equals(detected)) {
                finalLanguage = detected;
            }
            String finalRaw = raw;
            String finalEngine = engine;
            String finalProvider = provider;
            boolean finalCleaned = cleaned;
            boolean finalCommand = command != null;
            long duration = rec.durationMs;

            String id = UUID.randomUUID().toString();
            HistoryEntry entry = new HistoryEntry(id, System.currentTimeMillis(), raw, text,
                    engine, provider, finalLanguage, duration, latency, cleaned,
                    editsJson(processed));
            new HistoryStore(app).append(entry);

            DictationResult result = new DictationResult(finalRaw, finalText, processed.edits,
                    finalLanguage, finalEngine, finalProvider, duration, latency, finalCleaned,
                    finalCommand);
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

    private static final class EngineResult {
        final String text;
        final String engine;
        final String language;

        EngineResult(String text, String engine, String language) {
            this.text = text;
            this.engine = engine;
            this.language = language;
        }
    }

    private EngineResult transcribeClip(Settings settings, String provider, float[] pcm,
            int sampleRate, String language, String detected) throws Exception {
        if (Settings.LOCAL.equals(provider)) {
            AsrOutput out = AsrManager.get(app).transcribe(pcm, language);
            String next = detected;
            if (out.language != null && !out.language.isEmpty()
                    && Settings.LANG_AUTO.equals(language)) {
                next = out.language;
            }
            return new EngineResult(out.text, out.engine, next);
        }
        TranscriptionRouter.assertCloudAllowed(settings);
        String key = new SecureStore(app).get(settings.keyName(provider));
        String prompt = CloudTranscriber.vocabularyPrompt(settings.userDictionary());
        String text = CloudTranscriber.transcribe(provider, key,
                Wav.fromFloat(pcm, sampleRate), settings.cloudLanguage(), prompt);
        return new EngineResult(text, "cloud:" + provider, detected);
    }

    /**
     * Transcription and cleanup can use different vendors. Deepgram has no
     * general-purpose rewrite model, so use the first configured Groq, OpenAI
     * or Gemini key for the text-only polish pass.
     */
    private String effectiveStyle(Settings settings) {
        String style = settings.style();
        if (!settings.appTone() || !Settings.STYLE_DEFAULT.equals(style)) return style;
        return TextPipeline.styleForPackage(InsertAccessibilityService.focusedPackage());
    }

    private String[] cleanupTarget(Settings settings, String preferred) {
        SecureStore store = new SecureStore(app);
        String[] order = { preferred, Settings.GROQ, Settings.OPENAI, Settings.GEMINI };
        for (String p : order) {
            if (Settings.LOCAL.equals(p) || Settings.DEEPGRAM.equals(p)) continue;
            if (!p.equals(preferred) && !Settings.DEEPGRAM.equals(preferred)) continue;
            try {
                String key = store.get(settings.keyName(p));
                if (key != null && !key.trim().isEmpty()) return new String[] { p, key };
            } catch (Throwable ignored) {}
            if (p.equals(preferred)) break;
        }
        return null;
    }

    private void fail(int token, String message) {
        synchronized (lock) {
            if (token != session) return;
            busy = false;
            commandSource = null;
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
