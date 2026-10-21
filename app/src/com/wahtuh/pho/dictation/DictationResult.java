package com.wahtuh.pho.dictation;

import com.wahtuh.pho.text.Edit;

import java.util.Collections;
import java.util.List;

public final class DictationResult {
    public final String raw;
    public final String text;
    public final List<Edit> edits;
    public final String language;
    public final String engine;
    public final String provider;
    public final long durationMs;
    public final long latencyMs;
    public final boolean cleaned;

    public DictationResult(String raw, String text, List<Edit> edits, String language,
                           String engine, String provider, long durationMs, long latencyMs,
                           boolean cleaned) {
        this.raw = raw == null ? "" : raw;
        this.text = text == null ? "" : text;
        this.edits = Collections.unmodifiableList(edits);
        this.language = language;
        this.engine = engine;
        this.provider = provider;
        this.durationMs = durationMs;
        this.latencyMs = latencyMs;
        this.cleaned = cleaned;
    }

    public boolean empty() { return text.trim().isEmpty(); }
}
