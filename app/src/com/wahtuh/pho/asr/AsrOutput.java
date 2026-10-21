package com.wahtuh.pho.asr;

public final class AsrOutput {
    public final String text;
    public final String language;
    public final String engine;
    public final long latencyMs;

    public AsrOutput(String text, String language, String engine, long latencyMs) {
        this.text = text == null ? "" : text.trim();
        this.language = language == null ? "" : language;
        this.engine = engine;
        this.latencyMs = latencyMs;
    }

    public boolean empty() { return text.isEmpty(); }
}
