package com.wahtuh.pho.audio;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/**
 * 16 kHz mono PCM capture. One capture session holds at most ten minutes,
 * which bounds memory to ~19 MB of float samples.
 */
public final class AudioCapture {
    public static final int SAMPLE_RATE = 16000;
    public static final int MAX_SECONDS = 600;

    public interface LevelListener {
        void onLevel(float rms);
    }

    public static final class Recording {
        public final float[] samples;
        public final int sampleRate;
        public final long durationMs;

        Recording(float[] samples, int sampleRate, long durationMs) {
            this.samples = samples;
            this.sampleRate = sampleRate;
            this.durationMs = durationMs;
        }

        public boolean empty() { return samples.length == 0; }
    }

    private final Object lock = new Object();
    private volatile boolean running;
    private AudioRecord record;
    private Thread worker;
    private float[] samples = new float[0];
    private long startedAt;
    private String error;

    @SuppressLint("MissingPermission")
    public void start(LevelListener listener) {
        synchronized (lock) {
            if (running) throw new IllegalStateException("already recording");
            int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) min = 2048;
            int bufferBytes = Math.max(min * 2, SAMPLE_RATE / 4);
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                record.release();
                record = null;
                throw new IllegalStateException("microphone is unavailable");
            }
            samples = new float[0];
            error = null;
            running = true;
            startedAt = System.currentTimeMillis();
            record.startRecording();
            worker = new Thread(() -> loop(listener), "wahtuh-capture");
            worker.start();
        }
    }

    private void loop(LevelListener listener) {
        short[] buf = new short[1600];
        float[] chunk = new float[0];
        int maxSamples = SAMPLE_RATE * MAX_SECONDS;
        while (running) {
            int n = record.read(buf, 0, buf.length);
            if (n <= 0) continue;
            if (chunk.length < n) chunk = new float[Math.max(n, chunk.length)];
            double sum = 0;
            for (int i = 0; i < n; i++) {
                float f = buf[i] / 32768f;
                chunk[i] = f;
                sum += f * f;
            }
            synchronized (lock) {
                int keep = Math.min(n, maxSamples - samples.length);
                if (keep > 0) {
                    float[] next = new float[samples.length + keep];
                    System.arraycopy(samples, 0, next, 0, samples.length);
                    System.arraycopy(chunk, 0, next, samples.length, keep);
                    samples = next;
                }
            }
            if (listener != null) {
                listener.onLevel((float) Math.sqrt(sum / Math.max(1, n)));
            }
        }
    }

    public Recording stop() {
        AudioRecord r;
        Thread t;
        synchronized (lock) {
            running = false;
            r = record;
            t = worker;
            record = null;
            worker = null;
        }
        if (r != null) {
            try { r.stop(); } catch (Exception ignored) {}
            r.release();
        }
        if (t != null) {
            try { t.join(1500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        synchronized (lock) {
            long elapsed = Math.max(0, System.currentTimeMillis() - startedAt);
            return new Recording(samples, SAMPLE_RATE, elapsed);
        }
    }

    public void cancel() {
        stop();
        synchronized (lock) { samples = new float[0]; }
    }

    public boolean isRecording() { return running; }

    public String error() { return error; }
}
