import com.wahtuh.pho.text.Dictionary;
import com.wahtuh.pho.text.FieldText;
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

        r = pipeline.process("um I I think uh the the cat is nice", options);
        check("fillers and debounce", "I think the cat is nice.", r.text);

        r = pipeline.process("hello comma world question mark", options);
        check("spoken punctuation", "Hello, world?", r.text);

        r = pipeline.process("let's meet at 5 actually 6 pm", options);
        check("self-correct number", "Let's meet at 6 pm.", r.text);

        r = pipeline.process("send it to John, I mean Jane", options);
        check("self-correct name", "Send it to Jane.", r.text);

        r = pipeline.process("I like tea actually I prefer coffee", options);
        check("self-correct clause", "I prefer coffee.", r.text);

        r = pipeline.process("I want the red one, actually the blue one", options);
        check("self-correct noun phrase", "I want the blue one.", r.text);

        r = pipeline.process("I actually like it", options);
        check("actually is not always a correction", "I actually like it.", r.text);

        r = pipeline.process("I will be there. Scratch that, I will be late", options);
        check("scratch that replaces sentence", "I will be late.", r.text);

        r = pipeline.process("camel case user name is useful", options);
        check("spoken camel case", "userName is useful.", r.text);

        r = pipeline.process("make this snake case billing api client", options);
        check("spoken snake case", "Make this billing_api_client.", r.text);

        r = pipeline.process("name it pascal case main window", options);
        check("spoken pascal case", "Name it MainWindow.", r.text);

        r = pipeline.process("add file tag main dot py to the import", options);
        check("spoken file tag", "Add @main.py to the import.", r.text);

        r = pipeline.process("first buy milk second call mom third go for a run", options);
        check("spoken numbered list", "1. Buy milk\n2. Call mom\n3. Go for a run.", r.text);

        r = pipeline.process("First sentence. Second sentence. Delete the last sentence.", options);
        check("delete last sentence", "First sentence.", r.text);

        r = pipeline.process("Buy milk now. Delete the last word.", options);
        check("delete last word", "Buy milk.", r.text);

        r = pipeline.process("Buy milk, call mom, and go for a run. Make that a bullet list.", options);
        check("make bullet list", "• Buy milk\n• Call mom\n• Go for a run.", r.text);

        r = pipeline.process("Buy milk, call mom. Make that a numbered list.", options);
        check("make numbered list", "1. Buy milk\n2. Call mom.", r.text);

        r = pipeline.process("ship it today. Make that uppercase.", options);
        check("make uppercase", "SHIP IT TODAY.", r.text);

        r = pipeline.process("SHIP IT TODAY. Make that lowercase.", options);
        check("make lowercase", "ship it today.", r.text);

        r = pipeline.process("hello world. Capitalize that.", options);
        check("capitalize that", "Hello world.", r.text);

        String terms = TextPipeline.contextTerms(
                "Hi John, update getUserName in main.py by Friday.", 10);
        check("context terms", "John, getUserName, main.py, Friday", terms);

        r = pipeline.process("bullet point milk and eggs", options);
        check("spoken bullet", "• milk and eggs.", r.text);

        check("selection uppercase", "SHIP IT",
                TextPipeline.selectionCommand("Ship it", "make that uppercase"));
        check("selection lowercase", "ship it",
                TextPipeline.selectionCommand("Ship it", "make that lowercase"));
        check("selection capitalize", "Hello world",
                TextPipeline.selectionCommand("hello world", "capitalize that"));
        check("selection bullet list", "• Buy milk\n• Call mom",
                TextPipeline.selectionCommand("Buy milk and Call mom", "make that a bullet list"));
        check("selection numbered list", "1. Buy milk\n2. Call mom",
                TextPipeline.selectionCommand("Buy milk and Call mom", "make that a numbered list"));
        check("selection delete word", "Buy",
                TextPipeline.selectionCommand("Buy milk.", "delete the last word"));
        check("selection delete sentence", "First sentence.",
                TextPipeline.selectionCommand("First sentence. Second sentence.",
                        "delete the last sentence"));
        check("selection delete only word", "",
                TextPipeline.selectionCommand("Buy", "delete the last word"));
        check("selection command miss", "null",
                String.valueOf(TextPipeline.selectionCommand("Ship it",
                        "make it professional")));

        r = pipeline.process("my email is john dot smith at example dot com", options);
        check("dotted email", "My email is john.smith@example.com.", r.text);

        r = pipeline.process("email john.smith@example.com now", options);
        check("literal email keeps case", "Email john.smith@example.com now.", r.text);

        TextPipeline.Options casual = new TextPipeline.Options(
                new Dictionary(1, new ArrayList<>()), true, true, "en");
        casual.style = "casual";
        r = pipeline.process("hello world", casual);
        check("casual drops final period", "Hello world", r.text);

        TextPipeline.Options lowercase = new TextPipeline.Options(
                new Dictionary(1, new ArrayList<>()), true, true, "en");
        lowercase.style = "very_casual";
        r = pipeline.process("Hello world", lowercase);
        check("very casual lowercases", "hello world", r.text);

        TextPipeline.Options excited = new TextPipeline.Options(
                new Dictionary(1, new ArrayList<>()), true, true, "en");
        excited.style = "excited";
        r = pipeline.process("we shipped it", excited);
        check("excited punctuation", "We shipped it!", r.text);

        TextPipeline.Options bare = new TextPipeline.Options(
                new Dictionary(1, new ArrayList<>()), false, true, "en");
        r = pipeline.process("hello world", bare);
        check("punctuation can be disabled", "Hello world", r.text);

        FieldText.Result search = FieldText.clean("Search YouTube", null, null, null,
                "com.google.android.youtube:id/search_edit_text",
                "com.google.android.youtube", 0, 0);
        check("search placeholder text", "", search.text);
        check("search placeholder flag", "true", String.valueOf(search.placeholder));

        FieldText.Result ask = FieldText.clean("Ask Google", null, null, null,
                "com.google.android.googlequicksearchbox:id/search_edit_text",
                "com.google.android.googlequicksearchbox", -1, -1);
        check("google placeholder", "", ask.text);

        FieldText.Result note = FieldText.clean("Note", null, null, null,
                "com.samsung.android.app.notes:id/title_edit_text",
                "com.samsung.android.app.notes", 0, 0);
        check("note placeholder", "", note.text);

        FieldText.Result hint = FieldText.clean("Type a message", "Type a message",
                null, null, "com.whatsapp:id/entry", "com.whatsapp", 14, 14);
        check("hint placeholder", "", hint.text);

        FieldText.Result real = FieldText.clean("Search results are here", null, null,
                null, "com.example:id/editor", "com.example", 21, 21);
        check("real text survives", "Search results are here", real.text);

        check("package tone chat", "casual",
                TextPipeline.styleForPackage("com.whatsapp"));
        check("package tone email", "formal",
                TextPipeline.styleForPackage("com.google.android.gm"));
        check("package tone unknown", "default",
                TextPipeline.styleForPackage("com.example.notes"));

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
