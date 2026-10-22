package com.wahtuh.pho.text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Snippets {
    public static final class Entry {
        public final String trigger;
        public final String expansion;

        public Entry(String trigger, String expansion) {
            this.trigger = trigger;
            this.expansion = expansion;
        }
    }

    public static final class Applied {
        public final String text;
        public final List<Edit> edits;

        Applied(String text, List<Edit> edits) {
            this.text = text;
            this.edits = edits;
        }
    }

    private final List<Entry> entries;

    public Snippets(List<Entry> entries) {
        this.entries = new ArrayList<>(entries);
        this.entries.sort(Comparator.comparingInt((Entry e) -> e.trigger.length()).reversed());
    }

    public static Snippets parse(String text) {
        List<Entry> list = new ArrayList<>();
        if (text != null) {
            for (String line : text.split("\n")) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                int at = s.indexOf('=');
                if (at <= 0) continue;
                String trigger = s.substring(0, at).trim();
                String expansion = unescape(s.substring(at + 1).trim());
                if (!trigger.isEmpty() && !expansion.isEmpty()) {
                    list.add(new Entry(trigger, expansion));
                }
            }
        }
        return new Snippets(list);
    }

    public List<Entry> entries() { return new ArrayList<>(entries); }

    public static String escape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') out.append("\\\\");
            else if (c == '\n') out.append("\\n");
            else out.append(c);
        }
        return out.toString();
    }

    public static String unescape(String value) {
        if (value == null || value.indexOf('\\') < 0) return value == null ? "" : value;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                if (next == 'n') out.append('\n');
                else if (next == '\\') out.append('\\');
                else out.append('\\').append(next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public Applied apply(String input) {
        String text = input == null ? "" : input;
        List<Edit> edits = new ArrayList<>();
        for (Entry e : entries) {
            Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(e.trigger)
                    + "(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher m = p.matcher(text);
            StringBuilder out = new StringBuilder();
            int last = 0;
            while (m.find()) {
                out.append(text, last, m.start());
                out.append(e.expansion);
                edits.add(new Edit("snippet", m.group(), e.expansion, m.start()));
                last = m.end();
            }
            out.append(text.substring(last));
            text = out.toString();
        }
        return new Applied(text, edits);
    }
}
