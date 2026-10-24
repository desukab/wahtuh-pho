import com.wahtuh.pho.update.UpdateChecker;

public final class UpdateCheckerTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        check("same version", false, UpdateChecker.isNewer("v0.4.1", "0.4.1"));
        check("patch newer", true, UpdateChecker.isNewer("v0.4.2", "0.4.1"));
        check("minor newer", true, UpdateChecker.isNewer("v0.5.0", "0.4.9"));
        check("numeric not lexical", true, UpdateChecker.isNewer("v0.4.10", "0.4.9"));
        check("shorter current", true, UpdateChecker.isNewer("v1", "0.9.9"));
        check("suffix ignored", false, UpdateChecker.isNewer("v0.4.1-beta", "0.4.1"));
        if (failed > 0) {
            System.out.println("RESULT: " + passed + "/" + (passed + failed) + " checks passed");
            System.exit(1);
        }
        System.out.println("RESULT: " + passed + "/" + (passed + failed) + " checks passed");
    }

    private static void check(String name, boolean expected, boolean actual) {
        if (expected == actual) {
            passed++;
            System.out.println("PASS  " + name + "  [" + actual + "]");
        } else {
            failed++;
            System.out.println("FAIL  " + name + "  expected " + expected + " got " + actual);
        }
    }
}
