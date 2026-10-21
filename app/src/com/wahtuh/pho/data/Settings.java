package com.wahtuh.pho.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class Settings {
    public static final String LOCAL = "local";
    public static final String GROQ = "groq";
    public static final String OPENAI = "openai";
    public static final String GEMINI = "gemini";

    public static final String LANG_AUTO = "auto";
    public static final String LANG_EN = "en";
    public static final String LANG_TE = "te";

    public static final String ENGINE_HYBRID = "hybrid";
    public static final String ENGINE_WHISPER = "whisper";
    public static final String ENGINE_VOSK = "vosk";

    private static final String PREF = "wahtuh_settings";

    private final SharedPreferences p;

    public Settings(Context context) {
        this.p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** Default is strictly local. Cloud is opt-in, never a fallback. */
    public boolean localOnly() { return p.getBoolean("local_only", true); }
    public void setLocalOnly(boolean v) { p.edit().putBoolean("local_only", v).apply(); }

    public String provider() { return p.getString("provider", LOCAL); }
    public void setProvider(String v) { p.edit().putString("provider", v).apply(); }

    public String language() { return p.getString("language", LANG_AUTO); }
    public void setLanguage(String v) { p.edit().putString("language", v).apply(); }

    public String engine() { return p.getString("engine", ENGINE_HYBRID); }
    public void setEngine(String v) { p.edit().putString("engine", v).apply(); }

    public boolean cleanup() { return p.getBoolean("cleanup", false); }
    public void setCleanup(boolean v) { p.edit().putBoolean("cleanup", v).apply(); }

    public boolean punctuation() { return p.getBoolean("punctuation", true); }
    public void setPunctuation(boolean v) { p.edit().putBoolean("punctuation", v).apply(); }

    public boolean fillers() { return p.getBoolean("fillers", true); }
    public void setFillers(boolean v) { p.edit().putBoolean("fillers", v).apply(); }

    public boolean dictionary() { return p.getBoolean("dictionary", true); }
    public void setDictionary(boolean v) { p.edit().putBoolean("dictionary", v).apply(); }

    public boolean keepAudio() { return p.getBoolean("keep_audio", false); }
    public void setKeepAudio(boolean v) { p.edit().putBoolean("keep_audio", v).apply(); }

    public String userDictionary() { return p.getString("user_dictionary", ""); }
    public void setUserDictionary(String v) { p.edit().putString("user_dictionary", v).apply(); }

    public String cloudLanguage() {
        String l = language();
        return LANG_AUTO.equals(l) ? "" : l;
    }

    public String keyName(String provider) { return "key_" + provider; }

    public String providerLabel() {
        switch (provider()) {
            case GROQ: return "Groq Whisper";
            case OPENAI: return "OpenAI transcription";
            case GEMINI: return "Google Gemini";
            default: return "On-device";
        }
    }
}
