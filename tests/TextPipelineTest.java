import com.wahtuh.pho.text.Dictionary;
import com.wahtuh.pho.text.Metrics;
import com.wahtuh.pho.text.ProcessedResult;
import com.wahtuh.pho.text.TextPipeline;

import java.util.ArrayList;

/** Dependency-free JVM checks for the deterministic text layer. */
public final class TextPipelineTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        TextPipeline pipeline = new TextPipeline();
        TextPipeline.Options options = new TextPipeline.Options(
                new Dictionary(1, new ArrayList<>()), true, true, "en");

        ProcessedResult r = pipeline.process("um hello ,  world", options);
        check("punctuation and spacing", "Hello, world.", r.text);

        TextPipeline.Options te = new TextPipeline.Options(
                new Dictionary(1, new ArrayList<>()), true, true, "te");
        r = pipeline.process("నమస్కారం, ఈ రోజు", te);
        check("Telugu comma", "నమస్కారం, ఈ రోజు.", r.text);

        Dictionary dict = Dictionary.withUserEntries(
                new Dictionary(1, new ArrayList<>()), "OpenAI=Open AI");
        TextPipeline.Options withDict = new TextPipeline.Options(dict, true, true, "en");
        r = pipeline.process("openai is useful", withDict);
        check("dictionary", "Open AI is useful.", r.text);

        Metrics.Score exact = Metrics.score("hello world", "hello world");
        check("WER exact", "0.0", String.format(java.util.Locale.US, "%.1f", exact.wer * 100));
        Metrics.Score deletion = Metrics.score("hello world", "hello");
        check("WER deletion", "50.0", String.format(java.util.Locale.US, "%.1f", deletion.wer * 100));

        if (failed > 0) {
            System.out.println("RESULT: " + passed + "/" + (passed + failed) + " checks passed");
            System.exit(1);
        }
        System.out.println("RESULT: " + passed + "/" + (passed + failed) + " checks passed");
    }

    private static void check(String name, String expected, String actual) {
        if (expected.equals(actual)) {
            passed++;
            System.out.println("PASS  " + name + "  [" + actual + "]");
        } else {
            failed++;
            System.out.println("FAIL  " + name + "  expected [" + expected + "] got [" + actual + "]");
        }
    }
}
