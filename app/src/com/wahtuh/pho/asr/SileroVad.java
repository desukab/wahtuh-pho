package com.wahtuh.pho.asr;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

/** Silero VAD v5 over ONNX Runtime. One 512-sample window at 16 kHz per call. */
public final class SileroVad implements AutoCloseable {
    public static final int CHUNK = 512;
    public static final int SAMPLE_RATE = 16000;
    private static final String ASSET = "models/silero_vad.onnx";
    private static final int STATE_SIZE = 2 * 128;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final float[] state = new float[STATE_SIZE];

    public SileroVad(Context context) throws Exception {
        byte[] model = readAsset(context, ASSET);
        env = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(1);
            options.setInterOpNumThreads(1);
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            session = env.createSession(model, options);
        }
    }

    /** Returns one speech probability per 512-sample window, or null if unsupported. */
    public float[] probabilities(float[] pcm, int sampleRate) {
        if (pcm == null || sampleRate != SAMPLE_RATE) return null;
        int frames = (pcm.length + CHUNK - 1) / CHUNK;
        if (frames == 0) return new float[0];
        float[] out = new float[frames];
        float[] chunk = new float[CHUNK];
        reset();
        for (int i = 0; i < frames; i++) {
            int from = i * CHUNK;
            int count = Math.min(CHUNK, pcm.length - from);
            System.arraycopy(pcm, from, chunk, 0, count);
            if (count < CHUNK) {
                java.util.Arrays.fill(chunk, count, CHUNK, 0f);
            }
            out[i] = infer(chunk);
        }
        return out;
    }

    public void reset() {
        java.util.Arrays.fill(state, 0f);
    }

    private float infer(float[] chunk) {
        try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(chunk),
                new long[]{1, CHUNK});
             OnnxTensor hidden = OnnxTensor.createTensor(env, FloatBuffer.wrap(state),
                     new long[]{2, 1, 128});
             OnnxTensor rate = OnnxTensor.createTensor(env, LongBuffer.wrap(
                     new long[]{SAMPLE_RATE}), new long[]{});
             OrtSession.Result result = session.run(inputs(input, hidden, rate))) {
            float[][] output = (float[][]) result.get(0).getValue();
            float[][][] next = (float[][][]) result.get(1).getValue();
            int at = 0;
            for (int layer = 0; layer < 2; layer++) {
                System.arraycopy(next[layer][0], 0, state, at, 128);
                at += 128;
            }
            return output.length == 0 || output[0].length == 0 ? 0f : output[0][0];
        } catch (Exception e) {
            return 0f;
        }
    }

    private static Map<String, OnnxTensor> inputs(OnnxTensor input, OnnxTensor hidden,
                                                  OnnxTensor rate) {
        Map<String, OnnxTensor> map = new HashMap<>();
        map.put("input", input);
        map.put("state", hidden);
        map.put("sr", rate);
        return map;
    }

    private static byte[] readAsset(Context context, String name) throws Exception {
        try (InputStream in = context.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    @Override
    public void close() {
        try { session.close(); } catch (Exception ignored) {}
    }
}
