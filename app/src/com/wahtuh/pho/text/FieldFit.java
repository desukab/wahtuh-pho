package com.wahtuh.pho.text;

/**
 * Fits replacement text into a field's character budget and reports the
 * dictated remainder so the caller can show it instead of dropping it.
 */
public final class FieldFit {
    public final String text;
    public final String overflow;

    private FieldFit(String text, String overflow) {
        this.text = text;
        this.overflow = overflow;
    }

    public static FieldFit fit(String replacement, int maxLength, String old,
                               int start, int end) {
        String value = replacement == null ? "" : replacement;
        int length = old == null ? 0 : old.length();
        if (start < 0 || start > length) start = length;
        if (end < start || end > length) end = start;
        if (maxLength <= 0) return new FieldFit(value, "");
        int room = maxLength - (length - (end - start));
        if (room <= 0) return new FieldFit("", value);
        if (value.length() <= room) return new FieldFit(value, "");
        return new FieldFit(value.substring(0, room), value.substring(room));
    }

    /**
     * Best-effort detection for fields that enforce a limit without exposing
     * it. Returns the missing tail when the field kept a prefix of the insert.
     */
    public static String overflowAfterRead(String read, String old, int start,
                                           int end, String inserted) {
        if (read == null || old == null || inserted == null || inserted.isEmpty()) return "";
        if (start < 0 || start > old.length()) return "";
        if (end < start || end > old.length()) end = start;
        String prefix = old.substring(0, start);
        String suffix = old.substring(end);
        if (!read.startsWith(prefix) || !read.endsWith(suffix)) return "";
        int middleEnd = read.length() - suffix.length();
        if (middleEnd < prefix.length()) return "";
        String middle = read.substring(prefix.length(), middleEnd);
        if (middle.isEmpty() || middle.length() >= inserted.length()) return "";
        if (!inserted.startsWith(middle)) return "";
        return inserted.substring(middle.length());
    }
}
