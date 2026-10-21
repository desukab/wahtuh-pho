package com.wahtuh.pho.asr;

import android.content.Context;

public final class AsrManager {
    private static AsrManager instance;
    private final Context app;
    private WhistleEngine engine;

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

    public synchronized AsrOutput transcribe(float[] pcm, String language) throws Exception {
        if (engine == null) engine = new WhistleEngine(app);
        return engine.transcribe(pcm, 16000, language);
    }

    public synchronized void close() {
        if (engine != null) {
            engine.close();
            engine = null;
        }
    }
}
