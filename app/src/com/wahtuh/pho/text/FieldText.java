package com.wahtuh.pho.text;

/**
 * Detects placeholder text that some editable views expose as their value.
 * This is metadata only: it never reads or stores surrounding screen content.
 */
public final class FieldText {
    public static final class Result {
        public final String text;
        public final boolean placeholder;

        Result(String text, boolean placeholder) {
            this.text = text;
            this.placeholder = placeholder;
        }
    }

    private FieldText() {}

    public static Result clean(String raw, String hint, String tooltip, String description,
                               String viewId, String packageName, int selectionStart,
                               int selectionEnd) {
        if (raw == null || raw.isEmpty()) return new Result("", false);
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return new Result("", false);
        if (same(trimmed, hint) || same(trimmed, tooltip)) {
            return new Result("", true);
        }

        boolean atStart = selectionStart <= 0 && selectionEnd <= 0;
        if (!atStart) return new Result(raw, false);

        String id = viewId == null ? "" : viewId.toLowerCase(java.util.Locale.US);
        String pkg = packageName == null ? "" : packageName.toLowerCase(java.util.Locale.US);
        String value = trimmed.toLowerCase(java.util.Locale.US);

        if ((id.contains("search") || value.startsWith("search "))
                && same(trimmed, description)) {
            return new Result("", true);
        }
        if (id.contains("search")
                && (value.equals("search") || value.startsWith("search ")
                || value.equals("ask google") || value.equals("search or type url"))) {
            return new Result("", true);
        }
        boolean noteApp = pkg.contains("notes") || pkg.contains("notepad")
                || pkg.contains("memo") || pkg.contains("keep");
        if ((id.contains("title") || noteApp)
                && (value.equals("note") || value.equals("title") || value.equals("add title")
                || value.equals("untitled") || value.equals("add a note")
                || value.equals("take a note"))) {
            return new Result("", true);
        }
        return new Result(raw, false);
    }

    private static boolean same(String value, String other) {
        if (other == null) return false;
        return value.equalsIgnoreCase(other.trim());
    }
}
