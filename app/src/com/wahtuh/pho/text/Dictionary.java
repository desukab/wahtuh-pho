package com.wahtuh.pho.text;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versioned vocabulary and normalization dictionary.
 *
 * Built-in entries live in assets/dictionary.json and are versioned. User
 * entries are additive, one "from=to" per line, and never overwrite history:
 * every application records an {@link Edit} and the raw transcript is kept.
 */
public final class Dictionary {
    public static final String ASSET = "dictionary.json";

    public static final class Entry {
        public final String from;
        public final String to;
        public final boolean caseSensitive;
        public final String lang;

        public Entry(String from, String to, boolean caseSensitive, String lang) {
            this.from = from;
            this.to = to;
            this.caseSensitive = caseSensitive;
            this.lang = lang == null ? "" : lang;
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

    private final int version;
    private final List<Entry> entries;

    public Dictionary(int version, List<Entry> entries) {
        this.version = version;
        this.entries = new ArrayList<>(entries);
        this.entries.sort(Comparator.comparingInt((Entry e) -> e.from.length()).reversed());
    }

    public int version() { return version; }
    public List<Entry> entries() { return new ArrayList<>(entries); }

    public static Dictionary load(Context context) {
        try {
            String json = readAsset(context, ASSET);
            JSONObject root = new JSONObject(json);
            int version = root.optInt("version", 1);
            JSONArray arr = root.optJSONArray("entries");
            List<Entry> list = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Entry e = new Entry(
                            o.getString("from"),
                            o.getString("to"),
                            o.optBoolean("caseSensitive", false),
                            o.optString("lang", ""));
                    if (!e.from.isEmpty() && !e.from.equals(e.to)) list.add(e);
                }
            }
            return new Dictionary(version, list);
        } catch (Exception e) {
            return new Dictionary(1, new ArrayList<>());
        }
    }

    public static Dictionary withUserEntries(Dictionary base, String userText) {
        List<Entry> all = base.entries();
        if (userText != null) {
            for (String line : userText.split("\n")) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                int at = s.indexOf('=');
                if (at <= 0) continue;
                String from = s.substring(0, at).trim();
                String to = s.substring(at + 1).trim();
                if (!from.isEmpty() && !from.equals(to)) {
                    all.add(new Entry(from, to, false, ""));
                }
            }
        }
        return new Dictionary(base.version(), all);
    }

    public Applied apply(String input) {
        String text = input == null ? "" : input;
        List<Edit> edits = new ArrayList<>();
        for (Entry e : entries) {
            if (e.lang.contains("te") && !containsTelugu(text)) continue;
            text = replace(text, e, edits);
        }
        return new Applied(text, edits);
    }

    private String replace(String text, Entry e, List<Edit> edits) {
        if (e.from.isEmpty()) return text;
        boolean latin = e.from.matches(".*[A-Za-z].*");
        if (latin) {
            Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(e.from)
                    + "(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher m = p.matcher(text);
            StringBuilder out = new StringBuilder();
            int last = 0;
            while (m.find()) {
                out.append(text, last, m.start());
                out.append(e.to);
                edits.add(new Edit("dictionary", m.group(), e.to, m.start()));
                last = m.end();
            }
            out.append(text.substring(last));
            return out.toString();
        }
        int idx = 0;
        while (true) {
            int at = text.indexOf(e.from, idx);
            if (at < 0) break;
            text = text.substring(0, at) + e.to + text.substring(at + e.from.length());
            edits.add(new Edit("dictionary", e.from, e.to, at));
            idx = at + e.to.length();
        }
        return text;
    }

    private static boolean containsTelugu(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 0x0C00 && s.charAt(i) <= 0x0C7F) return true;
        }
        return false;
    }

    static String readAsset(Context context, String name) throws Exception {
        try (InputStream in = context.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
