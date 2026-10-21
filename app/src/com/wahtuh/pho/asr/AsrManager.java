package com.wahtuh.pho.asr;

import android.content.Context;

import com.wahtuh.pho.data.Settings;

/** Lazily owns the local engines for the process lifetime. */
public final class AsrManager {
    private static AsrManager instance;
    private final Context app;
    private VoskEngine vosk;
    private SherpaWhisperEngine whisper;
    private HybridEngine hybrid;

    private AsrManager(Context context) {
        this.app = context.getApplicationContext();
    }

    public static synchronized AsrManager get(Context context) {
        if (instance == null) instance = new AsrManager(context);
        return instance;
    }

    public synchronized void prepareModels() throws Exception {
        ModelInstaller.install(app);
    }

    public boolean modelsReady() {
        return ModelInstaller.ready(app);
    }

    public long modelBytes() {
        return ModelInstaller.installedBytes(app);
    }

    public synchronized AsrOutput transcribe(float[] pcm, String language, String engine)
            throws Exception {
        if (!modelsReady()) prepareModels();
        String mode = engine == null ? Settings.ENGINE_HYBRID : engine;
        if (Settings.ENGINE_WHISPER.equals(mode)) {
            return whisper().transcribe(pcm, 16000, "en".equals(language) ? "en" : "");
        }
        if (Settings.ENGINE_VOSK.equals(mode)) {
            return vosk().transcribe(pcm, 16000, "te");
        }
        return hybrid().transcribe(pcm, 16000, language);
    }

    private VoskEngine vosk() {
        if (vosk == null) vosk = new VoskEngine(ModelInstaller.voskDir(app));
        return vosk;
    }

    private SherpaWhisperEngine whisper() {
        if (whisper == null) {
            whisper = new SherpaWhisperEngine(app.getAssets(), ModelInstaller.whisperDir(app));
        }
        return whisper;
    }

    private HybridEngine hybrid() {
        if (hybrid == null) hybrid = new HybridEngine(vosk(), whisper());
        return hybrid;
    }

    public synchronized void close() {
        if (hybrid != null) hybrid.close();
        else {
            if (vosk != null) vosk.close();
            if (whisper != null) whisper.close();
        }
        vosk = null;
        whisper = null;
        hybrid = null;
    }
}
