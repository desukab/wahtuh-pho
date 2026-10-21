package com.wahtuh.pho.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, local, reversible text cleanup. It never translates between
 * Telugu and English and never invents words. Every step can be disabled.
 */
public final class TextPipeline {
    public static final class Options {
        public boolean punctuation = true;
        public boolean removeFillers = true;
        public boolean dictionary = true;
        public Dictionary dict;
        public String language = "auto";

        public Options() {}

        public Options(Dictionary dict, boolean punctuation, boolean removeFillers, String language) {
            this.dict = dict;
            this.punctuation = punctuation;
            this.removeFillers = removeFillers;
            this.language = language;
        }
    }

    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");
    private static final Pattern FILLER = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])(um+|uh+|erm+|hmm+)(?![\\p{L}\\p{N}])[,]?\\s*");

    private static final String[][] SPOKEN_EN = {
            {"new paragraph", "\n\n"},
            {"new line", "\n"},
            {"question mark", "?"},
            {"exclamation mark", "!"},
            {"exclamation point", "!"},
            {"full stop", "."},
            {"period", "."},
            {"comma", ","},
            {"colon", ":"},
            {"semicolon", ";"},
            {"open quote", "\""},
            {"close quote", "\""},
    };

    private static final String[][] SPOKEN_TE = {
            {"కొత్త పేరా", "\n\n"},
            {"కొత్త లైన్", "\n"},
            {"ప్రశ్న గుర్తు", "?"},
            {"ఆశ్చర్యార్థకం", "!"},
            {"ఫుల్ స్టాప్", "."},
            {"కామా", ","},
    };

    public ProcessedResult process(String raw, Options opt) {
        List<Edit> edits = new ArrayList<>();
        String text = raw == null ? "" : raw;

        text = text.replace('\u00A0', ' ');
        text = MULTI_SPACE.matcher(text).replaceAll(" ");
        text = MULTI_NEWLINE.matcher(text).replaceAll("\n\n");
        text = text.trim();

        text = spokenPunctuation(text, SPOKEN_EN, edits);
        if (hasTelugu(text)) text = spokenPunctuation(text, SPOKEN_TE, edits);

        if (opt.removeFillers) {
            Matcher m = FILLER.matcher(text);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                edits.add(new Edit("filler", m.group(), "", m.start()));
                m.appendReplacement(sb, "");
            }
            m.appendTail(sb);
            text = sb.toString();
        }

        if (opt.dictionary && opt.dict != null) {
            Dictionary.Applied applied = opt.dict.apply(text);
            text = applied.text;
            edits.addAll(applied.edits);
        }

        text = tidySpacing(text);
        text = sentenceCase(text);

        if (opt.punctuation) {
            text = terminalPunctuation(text);
        }

        return new ProcessedResult(raw == null ? "" : raw, text.trim(), edits, guessLanguage(text));
    }

    private static String spokenPunctuation(String text, String[][] rules, List<Edit> edits) {
        for (String[] rule : rules) {
            String phrase = rule[0];
            String symbol = rule[1];
            Pattern p = Pattern.compile("(?i)(?<![\\p{L}\\p{N}])" + Pattern.quote(phrase)
                    + "(?![\\p{L}\\p{N}])");
            Matcher m = p.matcher(text);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String before = text.substring(0, m.start());
                String replacement = symbol;
                if (symbol.equals(",") || symbol.equals(".") || symbol.equals("?")
                        || symbol.equals("!") || symbol.equals(":") || symbol.equals(";")) {
                    replacement = symbol;
                } else if (symbol.equals("\n") || symbol.equals("\n\n")) {
                    replacement = symbol;
                }
                edits.add(new Edit("spoken_punctuation", m.group(), replacement, m.start()));
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            text = sb.toString();
        }
        return text;
    }

    private static String tidySpacing(String text) {
        text = text.replaceAll(" +([,.;:!?])", "$1");
        text = text.replaceAll("([,;:])", "$1 ");
        text = text.replaceAll(" +", " ");
        text = text.replaceAll(" ?\\n ?", "\n");
        text = text.replaceAll("\\.{2,}", ".");
        return text.trim();
    }

    private static String sentenceCase(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean start = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (start && Character.isLowerCase(c) && c < 0x0250) {
                out.append(Character.toUpperCase(c));
                start = false;
                continue;
            }
            out.append(c);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                start = true;
            } else if (!Character.isWhitespace(c) && c != '"' && c != '\'' && c != '(') {
                start = false;
            }
        }
        return out.toString();
    }

    private static String terminalPunctuation(String text) {
        if (text.isEmpty()) return text;
        char last = text.charAt(text.length() - 1);
        if (last == '.' || last == '!' || last == '?' || last == ';' || last == ':'
                || last == '\n' || last == '\u0964' || last == '\u2026') {
            return text;
        }
        if (Character.isLetterOrDigit(last) || isTelugu(last)) {
            return text + ".";
        }
        return text;
    }

    public static boolean hasTelugu(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x0C00 && c <= 0x0C7F) return true;
        }
        return false;
    }

    private static boolean isTelugu(char c) {
        return c >= 0x0C00 && c <= 0x0C7F;
    }

    public static String guessLanguage(String text) {
        if (hasTelugu(text)) return "te";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return "en";
        }
        return "unknown";
    }
}
