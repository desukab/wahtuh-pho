package com.wahtuh.pho.audio;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * 16 kHz mono PCM capture. One capture session holds at most ten minutes,
 * which bounds memory to about 38 MB of float samples. Samples are kept in
 * small chunks and joined once at stop, so a long session never repeatedly
 * copies the whole recording.
 */
public final class AudioCapture {
    public static final int SAMPLE_RATE = 16000;
    public static final int MAX_SECONDS = 600;

    public interface LevelListener {
        void onLevel(float rms);

        /** Raw 16 kHz mono samples, delivered on the capture thread. */
        default void onAudio(float[] samples, int sampleRate) {}
    }

    public static final class Recording {
        public final float[] samples;
        public final int sampleRate;
        public final long durationMs;
        public final String error;

        Recording(float[] samples, int sampleRate, long durationMs, String error) {
            this.samples = samples;
            this.sampleRate = sampleRate;
            this.durationMs = durationMs;
            this.error = error == null ? "" : error;
        }

        public boolean empty() { return samples.length == 0; }
    }

    private final Object lock = new Object();
    private final ArrayList<float[]> chunks = new ArrayList<>();
    private volatile boolean running;
    private AudioRecord record;
    private Thread worker;
    private int totalSamples;
    private long startedAt;
    private volatile String error;

    @SuppressLint("MissingPermission")
    public void start(LevelListener listener) {
        synchronized (lock) {
            if (running) throw new IllegalStateException("already recording");
            int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) min = 2048;
            int bufferBytes = Math.max(min * 2, SAMPLE_RATE / 4);
            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                recorder.release();
                throw new IllegalStateException("microphone is unavailable");
            }
            chunks.clear();
            totalSamples = 0;
            error = null;
            record = recorder;
            running = true;
            startedAt = System.currentTimeMillis();
            try {
                recorder.startRecording();
            } catch (Throwable t) {
                running = false;
                record = null;
                recorder.release();
                throw new IllegalStateException("microphone did not start: " + message(t));
            }
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                running = false;
                record = null;
                recorder.release();
                throw new IllegalStateException("microphone did not start");
            }
            worker = new Thread(() -> loop(listener, recorder), "wahtuh-capture");
            try {
                worker.start();
            } catch (Throwable t) {
                running = false;
                record = null;
                recorder.release();
                worker = null;
                throw new IllegalStateException("microphone thread did not start: " + message(t));
            }
        }
    }

    private void loop(LevelListener listener, AudioRecord recorder) {
        short[] buf = new short[1600];
        int maxSamples = SAMPLE_RATE * MAX_SECONDS;
        try {
            while (running) {
                int n = recorder.read(buf, 0, buf.length);
                if (n < 0) {
                    if (running) {
                        error = "Microphone read failed (" + n + ")";
                        running = false;
                    }
                    break;
                }
                if (n == 0) continue;
                float[] chunk = new float[n];
                double sum = 0;
                for (int i = 0; i < n; i++) {
                    float f = buf[i] / 32768f;
                    chunk[i] = f;
                    sum += f * f;
                }
                boolean limited = false;
                synchronized (lock) {
                    if (!running) break;
                    int keep = Math.min(n, maxSamples - totalSamples);
                    if (keep <= 0) {
                        limited = true;
                    } else {
                        if (keep < n) chunk = Arrays.copyOf(chunk, keep);
                        chunks.add(chunk);
                        totalSamples += keep;
                        limited = totalSamples >= maxSamples;
                    }
                }
                if (listener != null) {
                    listener.onLevel((float) Math.sqrt(sum / Math.max(1, n)));
                    listener.onAudio(chunk, SAMPLE_RATE);
                }
                if (limited) {
                    error = "Recording limit reached";
                    running = false;
                    break;
                }
            }
        } catch (Throwable t) {
            if (running) {
                error = "Microphone stopped: " + message(t);
                running = false;
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
            try { t.join(1500); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        float[] samples;
        String failure;
        synchronized (lock) {
            samples = joinChunks(totalSamples);
            chunks.clear();
            totalSamples = 0;
            failure = error;
            long elapsed = Math.max(0, System.currentTimeMillis() - startedAt);
            return new Recording(samples, SAMPLE_RATE, elapsed, failure);
        }
    }

    public void cancel() {
        stop();
        synchronized (lock) {
            chunks.clear();
            totalSamples = 0;
        }
    }

    public boolean isRecording() { return running; }

    public String error() { return error; }

    private float[] joinChunks(int count) {
        if (count <= 0) return new float[0];
        float[] out = new float[count];
        int at = 0;
        for (float[] chunk : chunks) {
            int take = Math.min(chunk.length, count - at);
            if (take <= 0) break;
            System.arraycopy(chunk, 0, out, at, take);
            at += take;
        }
        return at == out.length ? out : Arrays.copyOf(out, at);
    }

    private static String message(Throwable t) {
        String text = t.getMessage();
        return text == null || text.trim().isEmpty() ? t.getClass().getSimpleName() : text;
    }
}
