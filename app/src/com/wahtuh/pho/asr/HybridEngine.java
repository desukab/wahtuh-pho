package com.wahtuh.pho.asr;

import com.wahtuh.pho.audio.SpeechAudio;

import java.util.List;

/**
 * Auto mode runs both engines per utterance and uses Whisper's detected
 * language as the router. English goes to Whisper; Telugu goes to Vosk.
 * A single utterance that mixes both languages inside one breath still has to
 * pick one engine, which is a documented limitation.
 */
public final class HybridEngine implements AsrEngine {
    private final VoskEngine vosk;
    private final SherpaWhisperEngine whisper;

    public HybridEngine(VoskEngine vosk, SherpaWhisperEngine whisper) {
        this.vosk = vosk;
        this.whisper = whisper;
    }

    @Override public String id() { return "hybrid-vosk+whisper"; }

    @Override
    public AsrOutput transcribe(float[] pcm, int sampleRate, String language) throws Exception {
        if ("en".equals(language)) return whisper.transcribe(pcm, sampleRate, "en");
        if ("te".equals(language)) {
            AsrOutput te = vosk.transcribe(pcm, sampleRate, "te");
            if (!te.empty()) return te;
            return whisper.transcribe(pcm, sampleRate, "");
        }
        long t0 = System.currentTimeMillis();
        List<float[]> parts = SpeechAudio.utterances(pcm, sampleRate);
        if (parts.isEmpty()) parts.add(pcm);
        StringBuilder text = new StringBuilder();
        String lastLang = "";
        for (float[] part : parts) {
            AsrOutput w = whisper.transcribe(part, sampleRate, "");
            String chosen;
            if (w.language != null && w.language.startsWith("en")) {
                chosen = w.text;
                lastLang = "en";
            } else {
                AsrOutput v = vosk.transcribe(part, sampleRate, "te");
                if (!v.empty()) {
                    chosen = v.text;
                    lastLang = "te";
                } else {
                    chosen = w.text;
                    lastLang = w.language;
                }
            }
            if (!chosen.isEmpty()) {
                if (text.length() > 0) text.append(' ');
                text.append(chosen);
            }
        }
        return new AsrOutput(text.toString(), lastLang, id(), System.currentTimeMillis() - t0);
    }

    @Override
    public void close() {
        vosk.close();
        whisper.close();
    }
}
