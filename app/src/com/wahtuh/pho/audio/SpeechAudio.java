package com.wahtuh.pho.audio;

import java.util.ArrayList;
import java.util.List;

/** Silence trimming and silence-separated utterance splitting. */
public final class SpeechAudio {
    private SpeechAudio() {}

    public static float[] trim(float[] pcm, int sampleRate) {
        int win = Math.max(1, sampleRate / 50);
        int frame = Math.max(1, pcm.length / win);
        double threshold = 0.008;
        int first = -1, last = -1;
        for (int i = 0; i < frame; i++) {
            int start = i * win;
            int end = Math.min(pcm.length, start + win);
            double sum = 0;
            for (int j = start; j < end; j++) sum += pcm[j] * pcm[j];
            double rms = Math.sqrt(sum / Math.max(1, end - start));
            if (rms > threshold) {
                if (first < 0) first = start;
                last = end;
            }
        }
        if (first < 0) return new float[0];
        int pad = sampleRate / 5;
        first = Math.max(0, first - pad);
        last = Math.min(pcm.length, last + pad);
        float[] out = new float[last - first];
        System.arraycopy(pcm, first, out, 0, out.length);
        return out;
    }

    /**
     * Splits at pauses of at least 300 ms, caps each segment at 20 s and pads
     * 150 ms around speech. Short clips come back as one segment.
     */
    public static List<float[]> utterances(float[] pcm, int sampleRate) {
        List<float[]> out = new ArrayList<>();
        if (pcm.length == 0) return out;
        int frame = sampleRate / 50; // 20 ms
        int frames = (pcm.length + frame - 1) / frame;
        boolean[] voiced = new boolean[frames];
        for (int i = 0; i < frames; i++) {
            int start = i * frame;
            int end = Math.min(pcm.length, start + frame);
            double sum = 0;
            for (int j = start; j < end; j++) sum += pcm[j] * pcm[j];
            voiced[i] = Math.sqrt(sum / Math.max(1, end - start)) > 0.008;
        }
        int gapFrames = Math.max(1, 300 / 20);
        int maxFrames = sampleRate * 20 / frame;
        int padFrames = Math.max(1, 150 / 20);

        int i = 0;
        while (i < frames) {
            while (i < frames && !voiced[i]) i++;
            if (i >= frames) break;
            int start = Math.max(0, i - padFrames);
            int lastVoice = i;
            int j = i;
            while (j < frames && j - start < maxFrames) {
                if (voiced[j]) lastVoice = j;
                if (j - lastVoice >= gapFrames && j - start > 10) break;
                j++;
            }
            int end = Math.min(frames - 1, lastVoice + padFrames);
            int s = start * frame;
            int e = Math.min(pcm.length, (end + 1) * frame);
            if (e - s >= sampleRate / 4) {
                float[] seg = new float[e - s];
                System.arraycopy(pcm, s, seg, 0, seg.length);
                out.add(seg);
            }
            i = Math.max(j, i + 1);
        }
        if (out.isEmpty() && pcm.length > 0) out.add(pcm);
        return out;
    }

    public static float rms(float[] pcm, int from, int to) {
        double sum = 0;
        int n = Math.max(1, to - from);
        for (int i = from; i < to; i++) sum += pcm[i] * pcm[i];
        return (float) Math.sqrt(sum / n);
    }
}
