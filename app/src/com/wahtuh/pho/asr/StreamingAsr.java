package com.wahtuh.pho.asr;

import android.content.Context;

import com.k2fsa.sherpa.onnx.EndpointConfig;
import com.k2fsa.sherpa.onnx.EndpointRule;
import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Streaming English preview backed by a small on-device zipformer. It only
 * produces words for the bubble/IME; the final transcript still comes from
 * Whistle so accuracy and cleanup are unchanged.
 */
public final class StreamingAsr {
    public interface Listener {
        void onPartial(String text);
    }

    private static final int SAMPLE_RATE = 16000;
    private static final int QUEUE_CHUNKS = 200;

    private final OnlineRecognizer recognizer;
    private final OnlineStream stream;
    private final Thread worker;
    private final ArrayBlockingQueue<float[]> queue = new ArrayBlockingQueue<>(QUEUE_CHUNKS);
    private final Listener listener;
    private volatile boolean running = true;
    private String committed = "";
    private String last = "";

    private StreamingAsr(OnlineRecognizer recognizer, OnlineStream stream, Listener listener) {
        this.recognizer = recognizer;
        this.stream = stream;
        this.listener = listener;
        this.worker = new Thread(this::loop, "wahtuh-streaming");
        this.worker.start();
    }

    public static StreamingAsr open(Context context, String language, Listener listener) {
        if (!supports(language) || !ModelInstaller.streamingReady(context)) return null;
        OnlineRecognizer recognizer = null;
        OnlineStream stream = null;
        try {
            File dir = ModelInstaller.streamingDir(context);
            OnlineTransducerModelConfig transducer = OnlineTransducerModelConfig.builder()
                    .setEncoder(new File(dir, "encoder.int8.onnx").getAbsolutePath())
                    .setDecoder(new File(dir, "decoder.int8.onnx").getAbsolutePath())
                    .setJoiner(new File(dir, "joiner.int8.onnx").getAbsolutePath())
                    .build();
            OnlineModelConfig model = OnlineModelConfig.builder()
                    .setTransducer(transducer)
                    .setTokens(new File(dir, "tokens.txt").getAbsolutePath())
                    .setNumThreads(2)
                    .setProvider("cpu")
                    .build();
            EndpointConfig endpoint = EndpointConfig.builder()
                    .setRule1(EndpointRule.builder()
                            .setMustContainNonSilence(true)
                            .setMinTrailingSilence(2.4f)
                            .setMinUtteranceLength(0f)
                            .build())
                    .setRule2(EndpointRule.builder()
                            .setMustContainNonSilence(false)
                            .setMinTrailingSilence(1.2f)
                            .setMinUtteranceLength(0f)
                            .build())
                    .setRule3(EndpointRule.builder()
                            .setMustContainNonSilence(false)
                            .setMinTrailingSilence(0f)
                            .setMinUtteranceLength(20f)
                            .build())
                    .build();
            OnlineRecognizerConfig config = OnlineRecognizerConfig.builder()
                    .setFeatureConfig(FeatureConfig.builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setFeatureDim(80)
                            .build())
                    .setOnlineModelConfig(model)
                    .setEndpointConfig(endpoint)
                    .setEnableEndpoint(true)
                    .setDecodingMethod("greedy_search")
                    .build();
            recognizer = new OnlineRecognizer(config);
            stream = recognizer.createStream();
            return new StreamingAsr(recognizer, stream, listener);
        } catch (Throwable t) {
            if (stream != null) {
                try { stream.release(); } catch (Throwable ignored) {}
            }
            if (recognizer != null) {
                try { recognizer.release(); } catch (Throwable ignored) {}
            }
            return null;
        }
    }

    public static boolean supports(String language) {
        return language == null || language.isEmpty()
                || "auto".equals(language) || "en".equals(language);
    }

    public void add(float[] samples) {
        if (!running || samples == null || samples.length == 0) return;
        if (!queue.offer(samples)) {
            queue.poll();
            queue.offer(samples);
        }
    }

    private void loop() {
        try {
            while (running) {
                float[] pcm = queue.poll(100, TimeUnit.MILLISECONDS);
                if (pcm == null) continue;
                stream.acceptWaveform(pcm, SAMPLE_RATE);
                while (recognizer.isReady(stream)) recognizer.decode(stream);
                String text = recognizer.getResult(stream).getText();
                if (recognizer.isEndpoint(stream)) {
                    committed = join(committed, text);
                    recognizer.reset(stream);
                    text = committed;
                } else {
                    text = join(committed, text);
                }
                emit(text);
            }
        } catch (Throwable ignored) {
        } finally {
            try { stream.release(); } catch (Throwable ignored) {}
            try { recognizer.release(); } catch (Throwable ignored) {}
        }
    }

    private void emit(String text) {
        String clean = readable(text);
        if (clean.equals(last)) return;
        last = clean;
        if (listener != null && !clean.isEmpty()) {
            try { listener.onPartial(clean); } catch (Throwable ignored) {}
        }
    }

    private static String readable(String text) {
        if (text == null) return "";
        String lower = text.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        boolean capitalize = true;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean left = i > 0 && Character.isLetter(lower.charAt(i - 1));
            boolean right = i + 1 < lower.length() && Character.isLetter(lower.charAt(i + 1));
            if (c == 'i' && !left && !right) c = 'I';
            if (capitalize && Character.isLetter(c)) {
                c = Character.toUpperCase(c);
                capitalize = false;
            }
            if ((c == '.' || c == '!' || c == '?')
                    && (i + 1 == lower.length() || Character.isWhitespace(lower.charAt(i + 1)))) {
                capitalize = true;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String join(String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return left + " " + right;
    }

    public void close() {
        running = false;
        queue.clear();
        worker.interrupt();
        try {
            worker.join(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
