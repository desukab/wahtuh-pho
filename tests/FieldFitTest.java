import com.wahtuh.pho.text.FieldFit;

/** Dependency-free JVM checks for character-limit handling. */
public final class FieldFitTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        FieldFit unknown = FieldFit.fit("hello", 0, "", 0, 0);
        check("unknown limit keeps text", "hello", unknown.text);
        check("unknown limit no overflow", "", unknown.overflow);

        FieldFit room = FieldFit.fit("hello world", 14, "abc", 3, 3);
        check("fits with room", "hello world", room.text);
        check("fits no overflow", "", room.overflow);

        FieldFit clipped = FieldFit.fit("hello world", 5, "", 0, 0);
        check("clips to limit", "hello", clipped.text);
        check("reports clipped tail", " world", clipped.overflow);

        FieldFit replace = FieldFit.fit("new text", 12, "old text", 0, 4);
        check("replacement fits", "new text", replace.text);
        check("replacement no overflow", "", replace.overflow);

        FieldFit selected = FieldFit.fit("replacement", 23, "one two three", 4, 7);
        check("selection fits", "replacement", selected.text);

        FieldFit full = FieldFit.fit("hello", 3, "abc", 0, 0);
        check("full field inserts nothing", "", full.text);
        check("full field reports all", "hello", full.overflow);

        check("prefix read reports tail", " world",
                FieldFit.overflowAfterRead("hello", "", 0, 0, "hello world"));
        check("full read no overflow", "",
                FieldFit.overflowAfterRead("hello world", "", 0, 0, "hello world"));
        check("null read no overflow", "",
                FieldFit.overflowAfterRead(null, "", 0, 0, "hello"));
        check("mismatched prefix no overflow", "",
                FieldFit.overflowAfterRead("nope", "abc", 3, 3, "hello"));
        check("preserved suffix reports inserted tail", "friend",
                FieldFit.overflowAfterRead("hi there END", "hi END", 3, 3, "there friend"));
        check("nothing inserted no overflow", "",
                FieldFit.overflowAfterRead("abc", "abc", 3, 3, "hello"));
        check("reformatted read no overflow", "",
                FieldFit.overflowAfterRead("Hello world", "", 0, 0, "hello world"));

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
