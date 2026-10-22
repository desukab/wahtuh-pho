import com.wahtuh.pho.audio.SpeechAudio;

import java.util.Arrays;
import java.util.List;

/** Dependency-free checks for the deterministic VAD and level normalizer. */
public final class SpeechAudioTest {
    private static int passed;
    private static int failed;
    private static final int RATE = 16000;

    public static void main(String[] args) {
        float[] silence = new float[RATE];
        SpeechAudio.Plan quiet = SpeechAudio.analyze(silence, RATE);
        check("silence has no speech", false, quiet.hasSpeech());
        check("silence prepares empty", 0, SpeechAudio.prepare(silence, RATE).length);

        float[] tone = tone(700, 0.2f);
        SpeechAudio.Plan single = SpeechAudio.analyze(tone, RATE);
        check("tone detected", true, single.hasSpeech());
        check("tone one segment", 1, single.segments.size());
        float[] prepared = SpeechAudio.prepare(tone, RATE);
        check("prepare keeps audio", true, prepared.length > RATE / 4);
        check("prepare normalizes peak", true, peak(prepared) <= 0.97f);
        float[] padded = join(new float[RATE / 2], tone, new float[RATE / 2]);
        check("trim removes padding", true,
                SpeechAudio.trim(padded, RATE).length < padded.length);

        float[] neural = new float[(tone.length + 511) / 512];
        Arrays.fill(neural, 0.9f);
        SpeechAudio.Plan silero = SpeechAudio.analyze(tone, RATE, neural, 512);
        check("neural probabilities detect speech", true, silero.hasSpeech());
        check("neural plan prepares audio", true,
                SpeechAudio.prepare(tone, RATE, silero).length > RATE / 4);
        float[] silentNeural = new float[(silence.length + 511) / 512];
        check("neural silence rejected", false,
                SpeechAudio.analyze(silence, RATE, silentNeural, 512).hasSpeech());

        float[] split = join(tone(420, 0.2f), new float[RATE * 13 / 10], tone(420, 0.2f));
        SpeechAudio.Plan two = SpeechAudio.analyze(split, RATE);
        check("pause splits segments", 2, two.segments.size());
        List<float[]> utterances = SpeechAudio.utterances(split, RATE);
        check("utterances match segments", 2, utterances.size());
        for (float[] utterance : utterances) {
            check("utterance non-empty", true, utterance.length > 0);
        }

        if (failed > 0) {
            System.out.println("RESULT: " + passed + "/" + (passed + failed) + " checks passed");
            System.exit(1);
        }
        System.out.println("RESULT: " + passed + "/" + (passed + failed) + " checks passed");
    }

    private static float[] tone(int ms, float amplitude) {
        float[] out = new float[RATE * ms / 1000];
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) (Math.sin(2 * Math.PI * 440 * i / RATE) * amplitude);
        }
        return out;
    }

    private static float[] join(float[]... parts) {
        int size = 0;
        for (float[] part : parts) size += part.length;
        float[] out = new float[size];
        int at = 0;
        for (float[] part : parts) {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }

    private static float peak(float[] samples) {
        float peak = 0;
        for (float sample : samples) peak = Math.max(peak, Math.abs(sample));
        return peak;
    }

    private static void check(String name, Object expected, Object actual) {
        if (expected.equals(actual)) {
            passed++;
            System.out.println("PASS  " + name + "  [" + actual + "]");
        } else {
            failed++;
            System.out.println("FAIL  " + name + "  expected [" + expected + "] got [" + actual + "]");
        }
    }
}
