package com.wahtuh.pho.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Adaptive voice-activity detection for dictation. It learns the noise floor
 * from the clip, debounces speech onsets, keeps pre/post roll, removes dead air
 * and normalizes level before the audio reaches an engine.
 */
public final class SpeechAudio {
    public static final int FRAME_MS = 20;
    private static final int PRE_ROLL_MS = 300;
    private static final int POST_ROLL_MS = 300;
    private static final int GAP_MS = 900;
    private static final int MAX_SILENCE_MS = 700;
    private static final int KEEP_SILENCE_MS = 300;
    private static final int MAX_SEGMENT_MS = 28000;
    private static final int MIN_SEGMENT_MS = 180;

    private SpeechAudio() {}

    public static final class Segment {
        public final float[] samples;
        public final int pauseBeforeMs;
        public final int startMs;
        public final int endMs;

        Segment(float[] samples, int pauseBeforeMs, int startMs, int endMs) {
            this.samples = samples;
            this.pauseBeforeMs = pauseBeforeMs;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    public static final class Plan {
        public final List<Segment> segments;
        public final int speechMs;
        public final int removedSilenceMs;
        public final int totalMs;
        final boolean[] voiced;
        final boolean[] active;
        final int firstFrame;
        final int lastFrame;

        Plan(List<Segment> segments, int speechMs, int removedSilenceMs, int totalMs,
                boolean[] voiced, boolean[] active, int firstFrame, int lastFrame) {
            this.segments = segments;
            this.speechMs = speechMs;
            this.removedSilenceMs = removedSilenceMs;
            this.totalMs = totalMs;
            this.voiced = voiced;
            this.active = active;
            this.firstFrame = firstFrame;
            this.lastFrame = lastFrame;
        }

        public boolean hasSpeech() {
            return speechMs >= 140 && !segments.isEmpty();
        }

        public boolean shouldSplit(long durationMs) {
            return segments.size() > 1
                    && (durationMs >= 15000 || segments.size() > 2);
        }
    }

    public static Plan analyze(float[] pcm, int sampleRate) {
        return analyze(pcm, sampleRate, null, 0);
    }

    /** Uses neural probabilities when available; falls back to the adaptive gate. */
    public static Plan analyze(float[] pcm, int sampleRate, float[] probabilities,
                               int probabilityFrameSamples) {
        int hop = Math.max(1, sampleRate * FRAME_MS / 1000);
        int frames = pcm.length == 0 ? 0 : (pcm.length + hop - 1) / hop;
        if (frames == 0) return emptyPlan(0, sampleRate);

        boolean[] voiced = new boolean[frames];
        int speechFrames = 0;
        if (probabilities != null && probabilities.length > 0
                && probabilityFrameSamples > 0) {
            for (int i = 0; i < frames; i++) {
                int at = Math.min(probabilities.length - 1,
                        (i * hop) / probabilityFrameSamples);
                voiced[i] = probabilities[at] >= 0.5f;
                if (voiced[i]) speechFrames++;
            }
        } else {
            float[] rms = new float[frames];
            float[] zcr = new float[frames];
            float[] noiseSample = new float[frames];
            for (int i = 0; i < frames; i++) {
                int start = i * hop;
                int end = Math.min(pcm.length, start + hop);
                double sum = 0;
                int crossings = 0;
                float previous = pcm[start];
                for (int j = start; j < end; j++) {
                    float value = pcm[j];
                    sum += value * value;
                    if ((value >= 0) != (previous >= 0)) crossings++;
                    previous = value;
                }
                rms[i] = (float) Math.sqrt(sum / Math.max(1, end - start));
                zcr[i] = crossings / (float) Math.max(1, end - start - 1);
                noiseSample[i] = rms[i];
            }

            Arrays.sort(noiseSample);
            float floor = noiseSample[(int) Math.min(noiseSample.length - 1,
                    noiseSample.length * 0.15f)];
            float threshold = Math.max(0.0065f, Math.min(0.045f, floor * 2.8f));
            for (int i = 0; i < frames; i++) {
                boolean speechShape = zcr[i] > 0.004f && zcr[i] < 0.42f;
                boolean loud = rms[i] > threshold * 2.15f;
                voiced[i] = rms[i] > threshold && (speechShape || loud);
                if (voiced[i]) speechFrames++;
            }
        }

        boolean[] active = voiced.clone();
        for (int i = 1; i < frames - 1; i++) {
            if (voiced[i - 1] && voiced[i + 1]) active[i] = true;
        }
        int hangFrames = Math.max(1, 220 / FRAME_MS);
        int hold = 0;
        int firstFrame = -1;
        int lastFrame = -1;
        for (int i = 0; i < frames; i++) {
            if (voiced[i]) {
                hold = hangFrames;
                active[i] = true;
                if (firstFrame < 0) firstFrame = i;
                lastFrame = i;
            } else if (hold > 0) {
                active[i] = true;
                hold--;
            }
        }
        if (firstFrame < 0) return emptyPlan(frames * FRAME_MS, sampleRate);

        int gapFrames = Math.max(1, GAP_MS / FRAME_MS);
        int padFrames = Math.max(1, PRE_ROLL_MS / FRAME_MS);
        int maxFrames = Math.max(1, MAX_SEGMENT_MS / FRAME_MS);
        List<Segment> segments = new ArrayList<>();
        int previousLast = -1;
        int floorFrame = 0;
        int i = 0;
        while (i < frames) {
            while (i < frames && !active[i]) i++;
            if (i >= frames) break;
            int start = i;
            int last = i;
            int j = i;
            boolean hardCut = false;
            while (j < frames) {
                if (active[j]) last = j;
                if (j - last >= gapFrames && j - start > 1) break;
                if (j - start >= maxFrames) {
                    int cut = quietCut(pcm, hop, start, j);
                    if (cut > start && cut < j) {
                        j = cut;
                        last = Math.min(last, j - 1);
                        hardCut = true;
                    }
                    break;
                }
                j++;
            }
            int startFrame = Math.max(floorFrame, Math.max(0, start - padFrames));
            int endFrame = hardCut ? j : Math.min(frames, last + padFrames + 1);
            int s = startFrame * hop;
            int e = Math.min(pcm.length, endFrame * hop);
            int pause = previousLast < 0 ? 0 : Math.max(0, (start - previousLast) * FRAME_MS);
            if (e - s >= sampleRate * MIN_SEGMENT_MS / 1000) {
                float[] piece = new float[e - s];
                System.arraycopy(pcm, s, piece, 0, piece.length);
                segments.add(new Segment(piece, pause, s * 1000 / sampleRate,
                        e * 1000 / sampleRate));
            }
            previousLast = last;
            if (hardCut) floorFrame = j;
            i = Math.max(j, start + 1);
        }

        int totalMs = pcm.length * 1000 / Math.max(1, sampleRate);
        int speechMs = speechFrames * FRAME_MS;
        return new Plan(segments, speechMs, Math.max(0, totalMs - speechMs), totalMs,
                voiced, active, firstFrame, lastFrame);
    }

    private static int quietCut(float[] pcm, int hop, int start, int limit) {
        int from = Math.max(start + 25, limit - Math.max(1, 2000 / FRAME_MS));
        int best = limit;
        double quietest = Double.MAX_VALUE;
        for (int frame = from; frame < limit; frame++) {
            int at = frame * hop;
            int end = Math.min(pcm.length, at + hop);
            double energy = 0;
            for (int i = at; i < end; i++) energy += pcm[i] * pcm[i];
            if (energy < quietest) {
                quietest = energy;
                best = frame;
            }
        }
        return best;
    }

    private static Plan emptyPlan(int totalMs, int sampleRate) {
        return new Plan(new ArrayList<>(), 0, totalMs, totalMs, new boolean[0],
                new boolean[0], -1, -1);
    }

    public static float[] prepare(float[] pcm, int sampleRate) {
        return prepare(pcm, sampleRate, analyze(pcm, sampleRate));
    }

    public static float[] prepare(float[] pcm, int sampleRate, Plan plan) {
        if (pcm == null || pcm.length == 0) return new float[0];
        if (plan == null) plan = analyze(pcm, sampleRate);
        if (plan.firstFrame < 0) return new float[0];
        int hop = Math.max(1, sampleRate * FRAME_MS / 1000);
        int preFrames = Math.max(1, PRE_ROLL_MS / FRAME_MS);
        int postFrames = Math.max(1, POST_ROLL_MS / FRAME_MS);
        int startFrame = Math.max(0, plan.firstFrame - preFrames);
        int endFrame = Math.min(plan.active.length, plan.lastFrame + postFrames + 1);
        int start = startFrame * hop;
        int end = Math.min(pcm.length, endFrame * hop);
        if (end <= start) return new float[0];

        float[] out = new float[end - start];
        int outLength = 0;
        int keepFrames = Math.max(1, KEEP_SILENCE_MS / FRAME_MS);
        int silence = 0;
        for (int i = startFrame; i < endFrame; i++) {
            int from = Math.max(start, i * hop);
            int to = Math.min(end, (i + 1) * hop);
            if (plan.active[i]) {
                silence = 0;
            } else {
                silence++;
                if (silence > keepFrames) continue;
            }
            int count = to - from;
            if (count <= 0) continue;
            System.arraycopy(pcm, from, out, outLength, count);
            outLength += count;
        }
        if (outLength == 0) return new float[0];
        if (outLength != out.length) out = Arrays.copyOf(out, outLength);
        return normalize(out);
    }

    public static float[] trim(float[] pcm, int sampleRate) {
        if (pcm == null || pcm.length == 0) return new float[0];
        Plan plan = analyze(pcm, sampleRate);
        if (plan.firstFrame < 0) return new float[0];
        int hop = Math.max(1, sampleRate * FRAME_MS / 1000);
        int preFrames = Math.max(1, PRE_ROLL_MS / FRAME_MS);
        int postFrames = Math.max(1, POST_ROLL_MS / FRAME_MS);
        int start = Math.max(0, (plan.firstFrame - preFrames) * hop);
        int end = Math.min(pcm.length, (plan.lastFrame + postFrames + 1) * hop);
        float[] out = new float[Math.max(0, end - start)];
        if (out.length > 0) System.arraycopy(pcm, start, out, 0, out.length);
        return out;
    }

    /** Normalizes a copy of the clip without VAD trimming. */
    public static float[] normalizeForEngine(float[] pcm) {
        if (pcm == null || pcm.length == 0) return new float[0];
        return normalize(Arrays.copyOf(pcm, pcm.length));
    }

    public static double rms(float[] pcm) {
        if (pcm == null || pcm.length == 0) return 0;
        double sum = 0;
        for (float value : pcm) sum += value * value;
        return Math.sqrt(sum / pcm.length);
    }

    private static float[] normalize(float[] pcm) {
        if (pcm.length == 0) return pcm;
        double sum = 0;
        for (float value : pcm) sum += value;
        double mean = sum / pcm.length;
        double energy = 0;
        float peak = 0;
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (float) (pcm[i] - mean);
            energy += pcm[i] * pcm[i];
            peak = Math.max(peak, Math.abs(pcm[i]));
        }
        double rms = Math.sqrt(energy / pcm.length);
        if (rms < 1e-5 || peak < 1e-5) return pcm;
        double gain = 0.085 / rms;
        if (gain < 0.7) gain = 0.7;
        if (gain > 3.2) gain = 3.2;
        if (peak * gain > 0.97) gain = 0.97 / peak;
        for (int i = 0; i < pcm.length; i++) {
            float value = (float) (pcm[i] * gain);
            pcm[i] = Math.max(-1f, Math.min(1f, value));
        }
        return pcm;
    }

    public static List<float[]> utterances(float[] pcm, int sampleRate) {
        List<float[]> out = new ArrayList<>();
        for (Segment segment : analyze(pcm, sampleRate).segments) {
            float[] prepared = prepare(segment.samples, sampleRate);
            if (prepared.length > 0) out.add(prepared);
        }
        return out;
    }

    public static float rms(float[] pcm, int from, int to) {
        double sum = 0;
        int start = Math.max(0, from);
        int end = Math.min(pcm.length, to);
        int count = Math.max(1, end - start);
        for (int i = start; i < end; i++) sum += pcm[i] * pcm[i];
        return (float) Math.sqrt(sum / count);
    }
}
