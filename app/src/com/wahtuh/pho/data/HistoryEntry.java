package com.wahtuh.pho.data;

import org.json.JSONObject;

public final class HistoryEntry {
    public final String id;
    public final long createdAt;
    public final String raw;
    public final String text;
    public final String engine;
    public final String provider;
    public final String language;
    public final long durationMs;
    public final long latencyMs;
    public final boolean cleaned;
    public final String editsJson;

    public HistoryEntry(String id, long createdAt, String raw, String text, String engine,
                        String provider, String language, long durationMs, long latencyMs,
                        boolean cleaned, String editsJson) {
        this.id = id;
        this.createdAt = createdAt;
        this.raw = raw;
        this.text = text;
        this.engine = engine;
        this.provider = provider;
        this.language = language;
        this.durationMs = durationMs;
        this.latencyMs = latencyMs;
        this.cleaned = cleaned;
        this.editsJson = editsJson;
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("createdAt", createdAt);
        o.put("raw", raw);
        o.put("text", text);
        o.put("engine", engine);
        o.put("provider", provider);
        o.put("language", language);
        o.put("durationMs", durationMs);
        o.put("latencyMs", latencyMs);
        o.put("cleaned", cleaned);
        o.put("edits", editsJson);
        return o;
    }

    public static HistoryEntry fromJson(JSONObject o) {
        return new HistoryEntry(
                o.optString("id", ""),
                o.optLong("createdAt", 0),
                o.optString("raw", ""),
                o.optString("text", ""),
                o.optString("engine", ""),
                o.optString("provider", ""),
                o.optString("language", ""),
                o.optLong("durationMs", 0),
                o.optLong("latencyMs", 0),
                o.optBoolean("cleaned", false),
                o.optString("edits", "[]"));
    }
}
