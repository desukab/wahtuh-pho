package com.wahtuh.pho.asr;

import android.content.Context;

import com.wahtuh.pho.data.Settings;

import org.json.JSONObject;

public final class WhistleEngine implements AsrEngine {
    private static final String[] LANGUAGES = {"en", "de", "fr", "es", "it", "nl", "pl"};

    static {
        System.loadLibrary("whistle_jni");
    }

    private final Context app;
    private boolean loaded;

    public WhistleEngine(Context context) {
        this.app = context.getApplicationContext();
    }

    private static native boolean nativeLoad(String modelPath);
    private static native String nativeTranscribe(float[] pcm, String language, String keywords);
    private static native String nativeError();

    @Override
    public synchronized AsrOutput transcribe(float[] pcm, int sampleRate, String language)
            throws Exception {
        if (pcm == null || pcm.length == 0) return new AsrOutput("", "", id(), 0);
        float[] audio = sampleRate == 16000 ? pcm : resample(pcm, sampleRate, 16000);
        ensureLoaded();
        String code = normalize(language);
        String keywords = keywords(new Settings(app).userDictionary());
        long started = System.currentTimeMillis();
        String json = nativeTranscribe(audio, code, keywords);
        long latency = System.currentTimeMillis() - started;
        if (json == null) throw new IllegalStateException(nativeError());
        JSONObject result = new JSONObject(json);
        String text = result.optString("text", "");
        String detected = result.optString("language", code == null ? "" : code);
        return new AsrOutput(text, detected, id(), latency);
    }

    private void ensureLoaded() throws Exception {
        if (loaded) return;
        ModelInstaller.install(app);
        if (!nativeLoad(ModelInstaller.whistleModel(app).getAbsolutePath())) {
            throw new IllegalStateException("Could not load Whistle: " + nativeError());
        }
        loaded = true;
    }

    private static String normalize(String language) {
        if (language == null || language.isEmpty()) return null;
        for (String code : LANGUAGES) if (code.equals(language)) return code;
        return null;
    }

    private static String keywords(String dictionary) {
        if (dictionary == null || dictionary.trim().isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (String line : dictionary.split("\n")) {
            String entry = line.trim();
            if (entry.isEmpty() || entry.startsWith("#")) continue;
            int at = entry.indexOf('=');
            String word = (at > 0 ? entry.substring(0, at) : entry).trim();
            if (!word.isEmpty()) out.append(word).append('\n');
        }
        return out.length() == 0 ? null : out.toString();
    }

    private static float[] resample(float[] input, int from, int to) {
        int length = Math.max(1, Math.round(input.length * (float) to / from));
        float[] out = new float[length];
        double step = (double) from / to;
        for (int i = 0; i < length; i++) {
            double at = i * step;
            int a = (int) at;
            int b = Math.min(input.length - 1, a + 1);
            float frac = (float) (at - a);
            out[i] = input[Math.min(a, input.length - 1)] * (1f - frac) + input[b] * frac;
        }
        return out;
    }

    @Override
    public String id() {
        return "whistle";
    }

    @Override
    public void close() {
    }
}
