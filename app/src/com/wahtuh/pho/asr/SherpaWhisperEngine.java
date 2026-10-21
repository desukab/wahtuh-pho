package com.wahtuh.pho.asr;

import android.content.res.AssetManager;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;

import java.io.File;

/**
 * sherpa-onnx running the multilingual Whisper tiny int8 model. English quality
 * and punctuation were excellent in testing; Telugu was not, which is why the
 * hybrid engine routes Telugu to Vosk.
 */
public final class SherpaWhisperEngine implements AsrEngine {
    private static final int MAX_CHUNK_SECONDS = 25;

    private final AssetManager assets;
    private final File modelDir;
    private OfflineRecognizer recognizer;
    private String configuredLanguage;

    public SherpaWhisperEngine(AssetManager assets, File modelDir) {
        this.assets = assets;
        this.modelDir = modelDir;
    }

    @Override public String id() { return "whisper-tiny-int8"; }

    private synchronized OfflineRecognizer recognizer(String language) {
        String lang = language == null ? "" : language;
        if (recognizer != null && lang.equals(configuredLanguage)) return recognizer;
        if (recognizer != null) {
            recognizer.release();
            recognizer = null;
        }
        File enc = new File(modelDir, "tiny-encoder.int8.onnx");
        File dec = new File(modelDir, "tiny-decoder.int8.onnx");
        File tokens = new File(modelDir, "tiny-tokens.txt");
        if (!enc.isFile() || !dec.isFile() || !tokens.isFile()) {
            throw new IllegalStateException("Whisper model is not installed");
        }

        FeatureConfig feat = new FeatureConfig();
        feat.setSampleRate(16000);
        feat.setFeatureDim(80);

        OfflineWhisperModelConfig whisper = new OfflineWhisperModelConfig();
        whisper.setEncoder(enc.getAbsolutePath());
        whisper.setDecoder(dec.getAbsolutePath());
        whisper.setLanguage(lang);
        whisper.setTask("transcribe");
        whisper.setTailPaddings(1000);

        OfflineModelConfig model = new OfflineModelConfig();
        model.setWhisper(whisper);
        model.setTokens(tokens.getAbsolutePath());
        model.setNumThreads(Math.min(4, Runtime.getRuntime().availableProcessors()));
        model.setProvider("cpu");
        model.setDebug(false);

        OfflineRecognizerConfig config = new OfflineRecognizerConfig();
        config.setFeatConfig(feat);
        config.setModelConfig(model);
        config.setDecodingMethod("greedy_search");

        // Pass null: model files are unpacked to real paths in app storage, so
        // sherpa-onnx must read them from the filesystem, not from assets.
        recognizer = new OfflineRecognizer(null, config);
        configuredLanguage = lang;
        return recognizer;
    }

    @Override
    public synchronized AsrOutput transcribe(float[] pcm, int sampleRate, String language) {
        long t0 = System.currentTimeMillis();
        OfflineRecognizer rec = recognizer(language);
        int chunk = MAX_CHUNK_SECONDS * sampleRate;
        StringBuilder text = new StringBuilder();
        String detected = "";
        for (int offset = 0; offset < pcm.length; offset += chunk) {
            int n = Math.min(chunk, pcm.length - offset);
            float[] part = new float[n];
            System.arraycopy(pcm, offset, part, 0, n);
            OfflineStream stream = rec.createStream();
            try {
                stream.acceptWaveform(part, sampleRate);
                rec.decode(stream);
                OfflineRecognizerResult r = rec.getResult(stream);
                if (r != null) {
                    String t = r.getText();
                    if (t != null) {
                        t = t.trim();
                        if (!t.isEmpty()) {
                            if (text.length() > 0) text.append(' ');
                            text.append(t);
                        }
                    }
                    if (detected.isEmpty() && r.getLang() != null) detected = r.getLang();
                }
            } finally {
                stream.release();
            }
        }
        if (detected.isEmpty()) detected = language == null ? "" : language;
        return new AsrOutput(text.toString(), detected, id(), System.currentTimeMillis() - t0);
    }

    @Override
    public synchronized void close() {
        if (recognizer != null) {
            recognizer.release();
            recognizer = null;
            configuredLanguage = null;
        }
    }
}
