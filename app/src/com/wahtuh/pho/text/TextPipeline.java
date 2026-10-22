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
        public Snippets snippets;
        public String language = "auto";
        public String style = "default";

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
            "(?i)(?<![\\p{L}\\p{N}])(um+|umm+|uh+|uhh+|uhm+|erm+|hmm+|hm+|ah+|eh+)"
                    + "(?![\\p{L}\\p{N}])[,]?\\s*");
    private static final Pattern DEBOUNCE = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])"
                    + "(i|a|an|the|and|but|to|of|in|it|is|we|you|that|like)"
                    + "(?:\\s+\\1(?![\\p{L}\\p{N}]))+");
    private static final Pattern STANDALONE_I = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])i(?![\\p{L}\\p{N}])");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}._%+-])"
                    + "([\\p{L}\\p{N}._%+-]+(?:\\s+dot\\s+[\\p{L}\\p{N}._%+-]+)*)"
                    + "\\s+at\\s+"
                    + "([\\p{L}\\p{N}-]+(?:\\s+dot\\s+[\\p{L}\\p{N}-]+)+)");
    private static final Pattern CORRECTION = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])"
                    + "(scratch that|actually|no wait|wait no|i mean|make that|correction)"
                    + "(?![\\p{L}\\p{N}])[\\s,]*");
    private static final Pattern NUMBER_ANY = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])"
                    + "(?:\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|"
                    + "one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)"
                    + "(?![\\p{L}\\p{N}])");
    private static final Pattern ENTITY_END = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])"
                    + "(?:\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|"
                    + "one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|"
                    + "noon|midnight|monday|tuesday|wednesday|thursday|friday|saturday|sunday|"
                    + "january|february|march|april|may|june|july|august|september|october|november|december)"
                    + "[\\s.!?,]*$");
    private static final Pattern LAST_WORD = Pattern.compile(
            "(?<![\\p{L}\\p{N}])([\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*)[\\s.!?,]*$");
    private static final Pattern ARTICLE_PHRASE = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])(?:the|a|an|this|that|these|those)\\s+"
                    + "[\\p{L}\\p{N}]+(?:\\s+[\\p{L}\\p{N}]+){0,2}[\\s.!?,]*$");
    private static final Pattern CLAUSE_STARTER = Pattern.compile(
            "(?i)^(i|we|you|he|she|they|it|let's|let us|the|this|that|these|those|my|our|"
                    + "there|please|can|could|would|should|will|is|are|was|were|do|does|did|"
                    + "i'm|we're|it's|i'll|we'll)\\b");
    private static final Pattern CASE_PHRASE = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])"
                    + "(camel case|lower camel case|pascal case|upper camel case|snake case|"
                    + "kebab case|constant case|screaming snake case|dot case)"
                    + "\\s+([\\p{L}\\p{N}]+(?:\\s+[\\p{L}\\p{N}]+){0,4})");
    private static final Pattern FILE_TAG = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])"
                    + "(?:file tag|file name|filename|at file|tag)"
                    + "\\s+([\\p{L}\\p{N}_-]+(?:\\s+dot\\s+[\\p{L}\\p{N}_-]+)+)");
    private static final Pattern NUMBERED_LIST = Pattern.compile(
            "(?i)^\\s*(?:first|firstly)\\b[\\s,:;]*(.+?)\\s+"
                    + "(?:second|secondly)\\b[\\s,:;]*(.+?)"
                    + "(?:\\s+(?:third|thirdly)\\b[\\s,:;]*(.+))?$");
    private static final Pattern DELETE_WORD_COMMAND = Pattern.compile(
            "(?i)(?:^|[.!?\\n])\\s*(?:delete|remove|drop)\\s+"
                    + "(?:the\\s+last\\s+word|last\\s+word)(?:\\s+please)?[\\s.!?]*$");
    private static final Pattern DELETE_SENTENCE_COMMAND = Pattern.compile(
            "(?i)(?:^|[.!?\\n])\\s*(?:delete|remove|drop)\\s+"
                    + "(?:that|this|it|the\\s+last\\s+sentence|last\\s+sentence)"
                    + "(?:\\s+please)?[\\s.!?]*$");
    private static final Pattern BULLET_COMMAND = Pattern.compile(
            "(?i)(?:^|[.!?\\n])\\s*(?:make|turn|format|convert)\\s+(?:that|this|it)"
                    + "(?:\\s+(?:in)?to)?\\s+(?:a\\s+)?(?:bullet(?:ed)?\\s+list|bullets)"
                    + "(?:\\s+please)?[\\s.!?]*$");
    private static final Pattern NUMBER_COMMAND = Pattern.compile(
            "(?i)(?:^|[.!?\\n])\\s*(?:make|turn|format|convert)\\s+(?:that|this|it)"
                    + "(?:\\s+(?:in)?to)?\\s+(?:a\\s+)?(?:numbered\\s+list|number\\s+list)"
                    + "(?:\\s+please)?[\\s.!?]*$");
    private static final Pattern UPPER_COMMAND = Pattern.compile(
            "(?i)(?:^|[.!?\\n])\\s*(?:make|turn|set)\\s+(?:that|this|it)\\s+"
                    + "(?:all\\s+)?(?:uppercase|upper\\s+case|all\\s+caps)"
                    + "(?:\\s+please)?[\\s.!?]*$");
    private static final Pattern LOWER_COMMAND = Pattern.compile(
            "(?i)(?:^|[.!?\\n])\\s*(?:make|turn|set)\\s+(?:that|this|it)\\s+"
                    + "(?:all\\s+)?(?:lowercase|lower\\s+case)"
                    + "(?:\\s+please)?[\\s.!?]*$");
    private static final Pattern CAPITALIZE_COMMAND = Pattern.compile(
            "(?i)(?:^|[.!?\\n])\\s*(?:capitalize|title\\s+case)\\s+(?:that|this|it)"
                    + "(?:\\s+please)?[\\s.!?]*$");
    private static final java.util.Set<String> CASE_STOP = new java.util.HashSet<>(
            java.util.Arrays.asList("is", "are", "was", "were", "and", "or", "to", "for",
                    "with", "in", "on", "at", "the", "a", "an", "that", "this", "then",
                    "please", "should", "could", "would", "can", "will", "from", "by",
                    "of", "as", "but", "if", "so"));
    private static final java.util.Set<String> CONTEXT_STOP = new java.util.HashSet<>(
            java.util.Arrays.asList("the", "and", "this", "that", "with", "from", "your",
                    "you", "for", "are", "was", "were", "have", "has", "will", "can",
                    "but", "not", "all", "any", "our", "out", "get", "set", "use", "add",
                    "new", "now", "please", "thanks", "hello", "hi", "dear", "meet",
                    "send", "make", "just", "here", "there", "what", "when", "where",
                    "who", "how", "why", "yes", "okay", "ok"));
    private static final java.util.Set<String> CODE_LEAD = new java.util.HashSet<>(
            java.util.Arrays.asList("for", "if", "while", "return", "new", "class", "const",
                    "let", "var", "function", "async", "await", "static", "public",
                    "private", "import", "export", "default"));

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
            {"ampersand", "&"},
            {"at sign", "@"},
            {"hashtag", "#"},
            {"hash sign", "#"},
            {"hyphen", "-"},
            {"em dash", "—"},
            {"underscore", "_"},
            {"slash", "/"},
            {"backslash", "\\"},
            {"percent sign", "%"},
            {"dollar sign", "$"},
            {"euro sign", "€"},
            {"rupee sign", "₹"},
            {"plus sign", "+"},
            {"equals sign", "="},
            {"open parenthesis", "("},
            {"close parenthesis", ")"},
            {"open bracket", "["},
            {"close bracket", "]"},
            {"open brace", "{"},
            {"close brace", "}"},
            {"bullet point", "\n• "},
            {"new bullet", "\n• "},
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

        text = DEBOUNCE.matcher(text).replaceAll("$1");

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

        text = inlineCommands(text, edits);
        boolean inlineApplied = hasRule(edits, "inline_command");
        text = selfCorrections(text, edits);

        if (opt.dictionary && opt.dict != null) {
            Dictionary.Applied applied = opt.dict.apply(text);
            text = applied.text;
            edits.addAll(applied.edits);
        }

        if (opt.snippets != null) {
            Snippets.Applied applied = opt.snippets.apply(text);
            text = applied.text;
            edits.addAll(applied.edits);
        }

        text = tidySpacing(text);
        text = numberedList(text, edits);
        if (!inlineApplied) {
            text = STANDALONE_I.matcher(text).replaceAll("I");
            text = sentenceCase(text);
        }
        text = emailDictation(text, edits);
        text = developerText(text, edits);

        if (opt.punctuation) {
            text = terminalPunctuation(text);
        }

        text = applyStyle(text, opt.style, opt.punctuation);

        return new ProcessedResult(raw == null ? "" : raw, text.trim(), edits, guessLanguage(text));
    }

    /**
     * Resolves explicit spoken corrections. "Meet at five, actually six" keeps
     * the corrected number; "send it to John, I mean Jane" keeps the last name.
     * Ambiguous rewrites are left to the optional AI polish pass.
     */
    private static String selfCorrections(String text, List<Edit> edits) {
        int pass = 0;
        int from = 0;
        while (pass < 5 && from < text.length()) {
            Matcher m = CORRECTION.matcher(text);
            if (!m.find(from)) break;
            String cue = m.group(1).toLowerCase(java.util.Locale.US);
            String prefix = text.substring(0, m.start());
            String suffix = text.substring(m.end()).trim();
            if (!plausibleCorrection(cue, prefix, suffix)) {
                from = m.end();
                continue;
            }
            String replacement;
            if ("scratch that".equals(cue)) {
                int cut = lastSentenceStart(prefix);
                replacement = joinCorrection(prefix.substring(0, cut), suffix);
            } else if (suffix.isEmpty()) {
                replacement = prefix.trim();
            } else if (needsClauseReplace(suffix)
                    && !(startsWithArticle(suffix) && hasArticlePhrase(prefix))) {
                replacement = joinCorrection(clauseHead(prefix), suffix);
            } else {
                replacement = replaceEntity(prefix, suffix);
            }
            edits.add(new Edit("self_correction", m.group().trim(), replacement, m.start()));
            text = replacement;
            from = 0;
            pass++;
            if (text.isEmpty()) break;
        }
        return text;
    }

    /** "Actually" is also a normal adverb, so only act on a clear correction. */
    private static boolean plausibleCorrection(String cue, String prefix, String suffix) {
        if (!"actually".equals(cue)) return true;
        String head = prefix.trim();
        if (head.isEmpty() || suffix.isEmpty()) return false;
        if (head.endsWith(",")) return true;
        if (startsWithNumber(suffix)) {
            Matcher numbers = NUMBER_ANY.matcher(prefix);
            return numbers.find();
        }
        if (needsClauseReplace(suffix)) {
            return head.split("\\s+").length >= 2;
        }
        return false;
    }

    private static boolean needsClauseReplace(String suffix) {
        return CLAUSE_STARTER.matcher(suffix).find();
    }

    private static String replaceEntity(String prefix, String suffix) {
        if (startsWithNumber(suffix)) {
            Matcher numbers = NUMBER_ANY.matcher(prefix);
            int start = -1;
            int end = -1;
            while (numbers.find()) {
                start = numbers.start();
                end = numbers.end();
            }
            if (start >= 0) {
                String tail = prefix.substring(end).replaceAll("^[\\s,.;:]+", "").trim();
                String joined = joinCorrection(prefix.substring(0, start), suffix);
                return tail.isEmpty() ? joined : joined + " " + tail;
            }
        }
        Matcher phrase = ARTICLE_PHRASE.matcher(prefix);
        if (phrase.find()) {
            return joinCorrection(prefix.substring(0, phrase.start()), suffix);
        }
        Matcher entity = ENTITY_END.matcher(prefix);
        if (entity.find()) {
            return joinCorrection(prefix.substring(0, entity.start()), suffix);
        }
        Matcher word = LAST_WORD.matcher(prefix);
        if (word.find()) {
            return joinCorrection(prefix.substring(0, word.start(1)), suffix);
        }
        return joinCorrection(prefix, suffix);
    }

    private static boolean startsWithArticle(String text) {
        return text.matches("(?i)^(the|a|an|this|that|these|those)\\b.*");
    }

    private static boolean hasArticlePhrase(String prefix) {
        return ARTICLE_PHRASE.matcher(prefix).find();
    }

    private static boolean startsWithNumber(String text) {
        if (text.isEmpty()) return false;
        char c = text.charAt(0);
        if (Character.isDigit(c)) return true;
        String lower = text.toLowerCase(java.util.Locale.US);
        for (String n : new String[] {"one", "two", "three", "four", "five", "six",
                "seven", "eight", "nine", "ten", "eleven", "twelve", "noon", "midnight"}) {
            if (lower.equals(n) || lower.startsWith(n + " ")) return true;
        }
        return false;
    }

    private static String clauseHead(String prefix) {
        String trimmed = prefix.trim();
        if (trimmed.isEmpty()) return "";
        for (int i = trimmed.length() - 1; i >= 0; i--) {
            char c = trimmed.charAt(i);
            if ((c == '.' || c == '!' || c == '?' || c == '\n')
                    && (i + 1 >= trimmed.length()
                    || Character.isWhitespace(trimmed.charAt(i + 1)))) {
                return trimmed.substring(0, i + 1).trim();
            }
        }
        return "";
    }

    private static int lastSentenceStart(String prefix) {
        int end = prefix.length();
        while (end > 0) {
            char c = prefix.charAt(end - 1);
            if (Character.isWhitespace(c) || c == '.' || c == '!' || c == '?' || c == ',') end--;
            else break;
        }
        for (int i = end - 1; i >= 0; i--) {
            char c = prefix.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') return i + 1;
        }
        return 0;
    }

    private static String joinCorrection(String head, String suffix) {
        String h = head == null ? "" : head.trim();
        String s = suffix == null ? "" : suffix.trim();
        if (h.isEmpty()) return s;
        if (s.isEmpty()) return h;
        return h + " " + s;
    }

    private static String numberedList(String text, List<Edit> edits) {
        Matcher m = NUMBERED_LIST.matcher(text);
        if (!m.matches()) return text;
        String first = listItem(m.group(1));
        String second = listItem(m.group(2));
        String third = m.group(3) == null ? "" : listItem(m.group(3));
        if (first.isEmpty() || second.isEmpty()) return text;
        StringBuilder out = new StringBuilder();
        out.append("1. ").append(first).append('\n');
        out.append("2. ").append(second);
        if (!third.isEmpty()) out.append('\n').append("3. ").append(third);
        edits.add(new Edit("numbered_list", m.group(), out.toString(), m.start()));
        return out.toString();
    }

    private static String listItem(String value) {
        if (value == null) return "";
        return value.replaceAll("(?i)\\s+(?:and|then|also)$", "")
                .replaceAll("[\\s,;:.]+$", "").trim();
    }

    /**
     * Local editing commands that act on text already in the current utterance.
     * They run before punctuation, need no cloud key, and every change is
     * recorded as an Edit.
     */
    private static String inlineCommands(String text, List<Edit> edits) {
        Matcher m = DELETE_WORD_COMMAND.matcher(text);
        if (m.find()) {
            String prefix = text.substring(0, m.start()).trim();
            String out = sentenceCase(removeLastWord(prefix));
            edits.add(new Edit("inline_command", m.group().trim(), out, m.start()));
            return out;
        }
        m = DELETE_SENTENCE_COMMAND.matcher(text);
        if (m.find()) {
            String prefix = text.substring(0, m.start()).trim();
            String out = sentenceCase(removeLastSentence(prefix));
            edits.add(new Edit("inline_command", m.group().trim(), out, m.start()));
            return out;
        }
        m = BULLET_COMMAND.matcher(text);
        if (m.find()) {
            String prefix = text.substring(0, m.start()).trim();
            if (!prefix.isEmpty()) {
                String out = listify(prefix, false);
                edits.add(new Edit("inline_command", m.group().trim(), out, m.start()));
                return out;
            }
        }
        m = NUMBER_COMMAND.matcher(text);
        if (m.find()) {
            String prefix = text.substring(0, m.start()).trim();
            if (!prefix.isEmpty()) {
                String out = listify(prefix, true);
                edits.add(new Edit("inline_command", m.group().trim(), out, m.start()));
                return out;
            }
        }
        m = UPPER_COMMAND.matcher(text);
        if (m.find()) {
            String prefix = text.substring(0, m.start()).trim();
            if (!prefix.isEmpty()) {
                String out = prefix.toUpperCase(java.util.Locale.US);
                edits.add(new Edit("inline_command", m.group().trim(), out, m.start()));
                return out;
            }
        }
        m = LOWER_COMMAND.matcher(text);
        if (m.find()) {
            String prefix = text.substring(0, m.start()).trim();
            if (!prefix.isEmpty()) {
                String out = prefix.toLowerCase(java.util.Locale.US);
                edits.add(new Edit("inline_command", m.group().trim(), out, m.start()));
                return out;
            }
        }
        m = CAPITALIZE_COMMAND.matcher(text);
        if (m.find()) {
            String prefix = text.substring(0, m.start()).trim();
            if (!prefix.isEmpty()) {
                String out = sentenceCase(prefix);
                edits.add(new Edit("inline_command", m.group().trim(), out, m.start()));
                return out;
            }
        }
        return text;
    }

    /**
     * Applies a recognised spoken edit to an existing selection, without cloud.
     * Returns null when the instruction is not one of the local commands; an
     * empty string is a valid result (for example, deleting the only word).
     */
    public static String selectionCommand(String selected, String instruction) {
        if (selected == null || selected.trim().isEmpty()
                || instruction == null || instruction.trim().isEmpty()) return null;
        String value = selected;
        String cmd = instruction.trim();
        Matcher m = DELETE_WORD_COMMAND.matcher(cmd);
        if (m.find()) return sentenceCase(removeLastWord(value));
        m = DELETE_SENTENCE_COMMAND.matcher(cmd);
        if (m.find()) return sentenceCase(removeLastSentence(value));
        m = BULLET_COMMAND.matcher(cmd);
        if (m.find()) return listify(value, false);
        m = NUMBER_COMMAND.matcher(cmd);
        if (m.find()) return listify(value, true);
        m = UPPER_COMMAND.matcher(cmd);
        if (m.find()) return value.toUpperCase(java.util.Locale.US);
        m = LOWER_COMMAND.matcher(cmd);
        if (m.find()) return value.toLowerCase(java.util.Locale.US);
        m = CAPITALIZE_COMMAND.matcher(cmd);
        if (m.find()) return sentenceCase(value);
        return null;
    }

    private static String removeLastWord(String text) {
        String value = text == null ? "" : text.trim();
        int end = value.length();
        while (end > 0 && " \t\n.!?,".indexOf(value.charAt(end - 1)) >= 0) end--;
        int start = end;
        while (start > 0 && !Character.isWhitespace(value.charAt(start - 1))) start--;
        return value.substring(0, start).trim();
    }

    private static String removeLastSentence(String text) {
        String value = text == null ? "" : text.trim();
        int end = value.length();
        while (end > 0 && " \t\n.!?".indexOf(value.charAt(end - 1)) >= 0) end--;
        int start = end;
        while (start > 0 && ".!?\n".indexOf(value.charAt(start - 1)) < 0) start--;
        return value.substring(0, start).trim();
    }

    private static String listify(String text, boolean numbered) {
        String value = text.replaceAll("\\s+", " ").trim();
        value = value.replaceAll("[.!?]+$", "");
        String[] parts = value.split("(?i)\\s*,\\s*|\\s*;\\s*|\\s+and\\s+");
        StringBuilder out = new StringBuilder();
        int number = 1;
        for (String part : parts) {
            String item = capitalizeItem(part.replaceAll("(?i)^(?:and|then|also)\\s+", "")
                    .replaceAll("[\\s,;:.]+$", "").trim());
            if (item.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(numbered ? (number++ + ". ") : "• ").append(item);
        }
        return out.length() == 0 ? text : out.toString();
    }

    private static String capitalizeItem(String value) {
        if (value.isEmpty()) return value;
        char first = value.charAt(0);
        if (Character.isLowerCase(first)) {
            return Character.toUpperCase(first) + value.substring(1);
        }
        return value;
    }

    private static boolean hasRule(List<Edit> edits, String rule) {
        for (Edit edit : edits) {
            if (rule.equals(edit.rule)) return true;
        }
        return false;
    }

    /** Spoken developer identifiers and file tags, handled locally. */
    private static String developerText(String text, List<Edit> edits) {
        Matcher cases = CASE_PHRASE.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (cases.find()) {
            String style = cases.group(1).toLowerCase(java.util.Locale.US);
            String[] words = cases.group(2).trim().split("\\s+");
            int begin = 0;
            while (begin < words.length) {
                String lead = words[begin].toLowerCase(java.util.Locale.US);
                if ("the".equals(lead) || "a".equals(lead) || "an".equals(lead)) begin++;
                else break;
            }
            if (begin >= words.length) {
                cases.appendReplacement(sb, Matcher.quoteReplacement(cases.group()));
                continue;
            }
            String lead = words[begin].toLowerCase(java.util.Locale.US);
            if (CASE_STOP.contains(lead) && !CODE_LEAD.contains(lead)) {
                cases.appendReplacement(sb, Matcher.quoteReplacement(cases.group()));
                continue;
            }
            int count = begin + 1;
            while (count < words.length && !CASE_STOP.contains(
                    words[count].toLowerCase(java.util.Locale.US))) {
                count++;
            }
            String identifier = caseConvert(
                    java.util.Arrays.copyOfRange(words, begin, count), style);
            if (identifier.isEmpty()) {
                cases.appendReplacement(sb, Matcher.quoteReplacement(cases.group()));
                continue;
            }
            String keep = count < words.length
                    ? " " + String.join(" ", java.util.Arrays.copyOfRange(
                            words, count, words.length))
                    : "";
            String replacement = identifier + keep;
            edits.add(new Edit("developer_case", cases.group(), replacement, cases.start()));
            cases.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        cases.appendTail(sb);
        text = sb.toString();

        Matcher files = FILE_TAG.matcher(text);
        sb = new StringBuffer();
        while (files.find()) {
            String name = files.group(1).replaceAll("(?i)\\s+dot\\s+", ".");
            String replacement = "@" + name;
            edits.add(new Edit("file_tag", files.group(), replacement, files.start()));
            files.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        files.appendTail(sb);
        return sb.toString();
    }

    private static String caseConvert(String[] words, String style) {
        List<String> clean = new ArrayList<>();
        for (String word : words) {
            String value = word.replaceAll("[^\\p{L}\\p{N}]", "");
            if (!value.isEmpty()) clean.add(value);
        }
        if (clean.isEmpty()) return "";
        boolean upper = style.contains("constant") || style.contains("screaming");
        if (style.contains("snake") || upper || style.contains("kebab") || style.contains("dot")) {
            String separator = style.contains("kebab") ? "-" : style.contains("dot") ? "." : "_";
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < clean.size(); i++) {
                if (i > 0) out.append(separator);
                String value = clean.get(i);
                out.append(upper ? value.toUpperCase(java.util.Locale.US)
                        : value.toLowerCase(java.util.Locale.US));
            }
            return out.toString();
        }
        boolean pascal = style.contains("pascal") || style.contains("upper camel");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < clean.size(); i++) {
            String value = clean.get(i).toLowerCase(java.util.Locale.US);
            if (pascal || i > 0) value = Character.toUpperCase(value.charAt(0)) + value.substring(1);
            out.append(value);
        }
        return out.toString();
    }

    /**
     * Pulls only name/code-like terms from nearby field text. This gives AI
     * polish spelling context without uploading the surrounding sentences.
     */
    public static String contextTerms(String text, int maxTerms) {
        if (text == null || text.trim().isEmpty()) return "";
        Matcher m = Pattern.compile("[\\p{L}][\\p{L}\\p{N}_@./+-]{2,}").matcher(text);
        java.util.LinkedHashSet<String> terms = new java.util.LinkedHashSet<>();
        while (m.find() && terms.size() < maxTerms) {
            String term = m.group().replaceAll("[.,;:!?]+$", "");
            if (term.length() < 3) continue;
            boolean codeish = term.indexOf('_') >= 0 || term.indexOf('@') >= 0
                    || term.indexOf('.') >= 0 || term.indexOf('/') >= 0
                    || term.indexOf('+') >= 0;
            boolean mixed = false;
            boolean hasLower = false;
            boolean hasUpper = false;
            for (int i = 1; i < term.length(); i++) {
                char c = term.charAt(i);
                if (Character.isUpperCase(c)) hasUpper = true;
                if (Character.isLowerCase(c)) hasLower = true;
                if (Character.isDigit(c)) mixed = true;
            }
            if (CONTEXT_STOP.contains(term.toLowerCase(java.util.Locale.US))) continue;
            if (codeish || mixed || (hasUpper && hasLower) || term.toUpperCase(
                    java.util.Locale.US).equals(term)
                    || Character.isUpperCase(term.charAt(0))) {
                terms.add(term);
            }
        }
        return String.join(", ", terms);
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
                edits.add(new Edit("spoken_punctuation", m.group(), symbol, m.start()));
                m.appendReplacement(sb, Matcher.quoteReplacement(symbol));
            }
            m.appendTail(sb);
            text = sb.toString();
        }
        return text;
    }

    private static String emailDictation(String text, List<Edit> edits) {
        Matcher m = EMAIL.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String user = m.group(1).replaceAll("(?i)\\s+dot\\s+", ".");
            String domain = m.group(2).replaceAll("(?i)\\s+dot\\s+", ".");
            String replacement = user + "@" + domain;
            edits.add(new Edit("email", m.group(), replacement, m.start()));
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String tidySpacing(String text) {
        text = text.replaceAll(" +([,.;:!?])", "$1");
        text = text.replaceAll("(?<=\\D)([,;:])(?=\\S)", "$1 ");
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
            if (c == '!' || c == '?' || c == '\n') {
                start = true;
            } else if (c == '.') {
                start = i + 1 >= text.length() || Character.isWhitespace(text.charAt(i + 1));
            } else if (!Character.isWhitespace(c) && c != '"' && c != '\'' && c != '(') {
                start = false;
            }
        }
        return out.toString();
    }

    private static String applyStyle(String text, String style, boolean punctuation) {
        if (style == null || "default".equals(style)) return text;
        if ("formal".equals(style)) {
            return punctuation ? terminalPunctuation(sentenceCase(text)) : text;
        }
        if ("casual".equals(style)) {
            if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
            return text;
        }
        if ("very_casual".equals(style)) {
            if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
            if (!text.isEmpty() && Character.isUpperCase(text.charAt(0))) {
                text = Character.toLowerCase(text.charAt(0)) + text.substring(1);
            }
            return text;
        }
        if ("excited".equals(style) && punctuation) {
            while (text.endsWith(".")) text = text.substring(0, text.length() - 1);
            if (!text.isEmpty() && !text.endsWith("!") && !text.endsWith("?")) text = text + "!";
            return text;
        }
        return text;
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

    /** App-aware tone uses only the package name, never screen contents. */
    public static String categoryForPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return "other";
        String p = packageName.toLowerCase(java.util.Locale.US);
        if (p.equals("com.google.android.gm")
                || p.contains("outlook") || p.contains("protonmail")
                || p.contains("thunderbird") || p.contains("email")) {
            return "email";
        }
        if (p.contains("slack") || p.contains("teams") || p.contains("dynamite")
                || p.contains("webex") || p.contains("zoom") || p.contains("workplace")) {
            return "work";
        }
        if (p.contains("whatsapp") || p.contains("telegram") || p.contains("messenger")
                || p.contains("discord") || p.contains("instagram")
                || p.contains("messaging") || p.contains("mms") || p.contains("messages")
                || p.contains("signal") || p.contains("snapchat")
                || p.contains("viber") || p.contains("wechat") || p.contains("kakao")) {
            return "chat";
        }
        return "other";
    }

    public static String defaultStyleForCategory(String category) {
        if ("email".equals(category)) return "formal";
        if ("chat".equals(category)) return "casual";
        return "default";
    }

    /** Backwards-compatible preset used by callers that only need a style. */
    public static String styleForPackage(String packageName) {
        return defaultStyleForCategory(categoryForPackage(packageName));
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
