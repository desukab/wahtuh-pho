package com.wahtuh.pho.asr;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;

/**
 * Vosk is used for Telugu. In measured testing it preserved Telugu script while
 * Whisper tiny/base emitted Perso-Arabic and CJK tokens for the same audio.
 */
public final class VoskEngine implements AsrEngine {
    private final String modelPath;
    private Model model;

    public VoskEngine(File modelDir) {
        this.modelPath = modelDir.getAbsolutePath();
    }

    @Override public String id() { return "vosk-te"; }

    private synchronized Model model() throws Exception {
        if (model == null) {
            if (!new File(modelPath).isDirectory()) {
                throw new IllegalStateException("Telugu model is not installed");
            }
            model = new Model(modelPath);
        }
        return model;
    }

    @Override
    public synchronized AsrOutput transcribe(float[] pcm, int sampleRate, String language) throws Exception {
        if (sampleRate != 16000) throw new IllegalArgumentException("Vosk expects 16 kHz");
        long t0 = System.currentTimeMillis();
        Model m = model();
        Recognizer r = new Recognizer(m, sampleRate);
        try {
            short[] s = new short[pcm.length];
            for (int i = 0; i < pcm.length; i++) {
                s[i] = (short) Math.max(-32768, Math.min(32767, Math.round(pcm[i] * 32767f)));
            }
            r.acceptWaveForm(s, s.length);
            String json = r.getFinalResult();
            String text = "";
            try {
                text = new JSONObject(json).optString("text", "");
            } catch (Exception ignored) {}
            return new AsrOutput(text, "te", id(), System.currentTimeMillis() - t0);
        } finally {
            r.close();
        }
    }

    @Override
    public synchronized void close() {
        if (model != null) {
            model.close();
            model = null;
        }
    }
}
