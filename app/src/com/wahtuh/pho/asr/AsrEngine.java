package com.wahtuh.pho.asr;

public interface AsrEngine {
    AsrOutput transcribe(float[] pcm, int sampleRate, String language) throws Exception;
    String id();
    void close();
}
