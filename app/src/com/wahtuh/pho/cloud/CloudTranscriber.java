package com.wahtuh.pho.cloud;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Optional cloud transcription. Audio is sent only when the user explicitly
 * selects a cloud provider and turns off local-only mode. This class is never
 * called as a fallback from the on-device path.
 */
public final class CloudTranscriber {
    public static final String GROQ_MODEL = "whisper-large-v3-turbo";
    public static final String OPENAI_MODEL = "gpt-transcribe";
    public static final String GEMINI_MODEL = "gemini-3.5-transcribe";

    public static final String GROQ_TRANSCRIBE = "https://api.groq.com/openai/v1/audio/transcriptions";
    public static final String OPENAI_TRANSCRIBE = "https://api.openai.com/v1/audio/transcriptions";
    public static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    public static final String GROQ_CHAT = "https://api.groq.com/openai/v1/chat/completions";
    public static final String OPENAI_CHAT = "https://api.openai.com/v1/chat/completions";
    public static final String GROQ_CLEAN_MODEL = "llama-3.3-70b-versatile";
    public static final String OPENAI_CLEAN_MODEL = "gpt-4o-mini";
    public static final String GEMINI_CLEAN_MODEL = "gemini-3.8-flash";

    public static final int MAX_INLINE_BYTES = 19 * 1024 * 1024;

    private CloudTranscriber() {}

    public static String transcribe(String provider, String apiKey, byte[] wav, String language,
                                    String vocabularyPrompt) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("No API key configured for " + provider);
        }
        if (wav.length > MAX_INLINE_BYTES) {
            throw new IllegalStateException("Recording is too long for cloud upload (limit 19 MB)");
        }
        switch (provider) {
            case "groq":
                return openAiStyle(GROQ_TRANSCRIBE, apiKey, GROQ_MODEL, wav, language,
                        vocabularyPrompt, "groq");
            case "openai":
                return openAiStyle(OPENAI_TRANSCRIBE, apiKey, OPENAI_MODEL, wav, language,
                        vocabularyPrompt, "openai");
            case "gemini":
                return geminiTranscribe(apiKey, wav, language, vocabularyPrompt);
            default:
                throw new IllegalArgumentException("Unknown cloud provider: " + provider);
        }
    }

    private static String openAiStyle(String endpoint, String key, String model, byte[] wav,
                                      String language, String prompt, String provider) throws Exception {
        String boundary = "wahtuh-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream(wav.length + 4096);
        part(body, boundary, "model", model);
        part(body, boundary, "response_format", "json");
        if (language != null && !language.isEmpty()) part(body, boundary, "language", language);
        if (prompt != null && !prompt.isEmpty()) part(body, boundary, "prompt", prompt);
        part(body, boundary, "temperature", "0");
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n"
                .getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: audio/wav\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(wav);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        c.setFixedLengthStreamingMode(body.size());
        try (OutputStream out = c.getOutputStream()) {
            body.writeTo(out);
        }
        String response = read(c);
        int code = c.getResponseCode();
        c.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException(provider + " HTTP " + code + ": " + safe(response));
        }
        try {
            JSONObject json = new JSONObject(response);
            String text = json.optString("text", "");
            if (text.isEmpty()) throw new IllegalStateException(provider + " returned no text");
            return text;
        } catch (org.json.JSONException e) {
            // Some providers return plain text when response_format=text.
            if (!response.trim().isEmpty() && !response.trim().startsWith("{")) return response.trim();
            throw new IllegalStateException(provider + " returned an unexpected response");
        }
    }

    private static String geminiTranscribe(String key, byte[] wav, String language,
                                           String prompt) throws Exception {
        JSONObject root = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        String langHint = language == null || language.isEmpty()
                ? "Detect the language. This may be English, Telugu, or Telugu-English code-switching."
                : "The expected language is " + language + ".";
        String instruction = "Transcribe the speech exactly as spoken. Preserve the original script "
                + "(Telugu in Telugu script, English in Latin script). Do not translate, summarize, "
                + "or add facts. Return only the transcript. " + langHint
                + (prompt == null || prompt.isEmpty() ? "" : " Vocabulary hints: " + prompt);
        parts.put(new JSONObject().put("text", instruction));
        parts.put(new JSONObject().put("inlineData", new JSONObject()
                .put("mimeType", "audio/wav")
                .put("data", Base64.encodeToString(wav, Base64.NO_WRAP))));
        content.put("parts", parts);
        contents.put(content);
        root.put("contents", contents);
        root.put("generationConfig", new JSONObject().put("temperature", 0));

        String endpoint = GEMINI_BASE + GEMINI_MODEL + ":generateContent";
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("x-goog-api-key", key);
        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
        String response = read(c);
        int code = c.getResponseCode();
        c.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("gemini HTTP " + code + ": " + safe(response));
        }
        JSONObject json = new JSONObject(response);
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new IllegalStateException("gemini returned no candidates");
        }
        JSONObject candidate = candidates.getJSONObject(0);
        JSONObject cContent = candidate.optJSONObject("content");
        if (cContent == null) throw new IllegalStateException("gemini returned no content");
        JSONArray cparts = cContent.optJSONArray("parts");
        if (cparts == null) throw new IllegalStateException("gemini returned no parts");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < cparts.length(); i++) {
            String t = cparts.getJSONObject(i).optString("text", "");
            if (!t.isEmpty()) text.append(t);
        }
        if (text.length() == 0) throw new IllegalStateException("gemini returned no text");
        return text.toString();
    }

    /** Optional AI cleanup, always a separate request containing text only. */
    public static String cleanup(String provider, String key, String text) throws Exception {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalStateException("No API key configured for cleanup");
        }
        String instruction = "Clean up this dictated text. Fix punctuation, capitalization and "
                + "obvious speech-recognition slips. Preserve the speaker's meaning, language mix, "
                + "and the original script (Telugu stays Telugu, English stays English). Do not add "
                + "facts, do not translate, do not answer the text. Return only the cleaned text.\n\n"
                + text;
        switch (provider) {
            case "groq":
                return chat(GROQ_CHAT, key, GROQ_CLEAN_MODEL, instruction, "groq");
            case "openai":
                return chat(OPENAI_CHAT, key, OPENAI_CLEAN_MODEL, instruction, "openai");
            case "gemini":
                return geminiText(key, instruction);
            default:
                throw new IllegalArgumentException("Unknown cleanup provider: " + provider);
        }
    }

    private static String chat(String endpoint, String key, String model, String prompt,
                               String provider) throws Exception {
        JSONObject root = new JSONObject();
        root.put("model", model);
        root.put("temperature", 0);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system")
                .put("content", "You are a careful dictation editor. You never invent content."));
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        root.put("messages", messages);
        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Content-Type", "application/json");
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
        String response = read(c);
        int code = c.getResponseCode();
        c.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException(provider + " cleanup HTTP " + code + ": " + safe(response));
        }
        JSONObject json = new JSONObject(response);
        JSONArray choices = json.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IllegalStateException(provider + " cleanup returned no choices");
        }
        String out = choices.getJSONObject(0).getJSONObject("message").optString("content", "");
        if (out.trim().isEmpty()) throw new IllegalStateException(provider + " cleanup returned no text");
        return out.trim();
    }

    private static String geminiText(String key, String prompt) throws Exception {
        JSONObject root = new JSONObject();
        JSONArray contents = new JSONArray();
        contents.put(new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", prompt))));
        root.put("contents", contents);
        root.put("generationConfig", new JSONObject().put("temperature", 0));
        String endpoint = GEMINI_BASE + GEMINI_CLEAN_MODEL + ":generateContent";
        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("x-goog-api-key", key);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
        String response = read(c);
        int code = c.getResponseCode();
        c.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("gemini cleanup HTTP " + code + ": " + safe(response));
        }
        JSONObject json = new JSONObject(response);
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new IllegalStateException("gemini cleanup returned no candidates");
        }
        JSONArray parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) out.append(parts.getJSONObject(i).optString("text", ""));
        if (out.length() == 0) throw new IllegalStateException("gemini cleanup returned no text");
        return out.toString().trim();
    }

    public static String vocabularyPrompt(String userDictionary) {
        if (userDictionary == null || userDictionary.trim().isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Vocabulary: ");
        int n = 0;
        for (String line : userDictionary.split("\n")) {
            int at = line.indexOf('=');
            if (at <= 0) continue;
            String to = line.substring(at + 1).trim();
            if (to.isEmpty()) continue;
            if (n++ > 24) break;
            sb.append(to).append(", ");
        }
        if (n == 0) return "";
        sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    private static void part(ByteArrayOutputStream out, String boundary, String name, String value)
            throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String read(HttpURLConnection c) throws Exception {
        InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) return "";
        try (InputStream input = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = input.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String safe(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[\\r\\n]+", " ").trim();
        return t.length() > 300 ? t.substring(0, 300) : t;
    }
}
