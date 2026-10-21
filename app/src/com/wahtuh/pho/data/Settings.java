package com.wahtuh.pho.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class Settings {
    public static final String LOCAL = "local";
    public static final String GROQ = "groq";
    public static final String OPENAI = "openai";
    public static final String GEMINI = "gemini";
    public static final String DEEPGRAM = "deepgram";

    public static final String LANG_AUTO = "auto";
    public static final String LANG_EN = "en";
    public static final String LANG_DE = "de";
    public static final String LANG_FR = "fr";
    public static final String LANG_ES = "es";
    public static final String LANG_IT = "it";
    public static final String LANG_NL = "nl";
    public static final String LANG_PL = "pl";

    public static final String[] LANGUAGES = {
            LANG_AUTO, LANG_EN, LANG_DE, LANG_FR, LANG_ES, LANG_IT, LANG_NL, LANG_PL};

    public static final String STYLE_DEFAULT = "default";
    public static final String STYLE_FORMAL = "formal";
    public static final String STYLE_CASUAL = "casual";
    public static final String STYLE_VERY_CASUAL = "very_casual";
    public static final String STYLE_EXCITED = "excited";

    public static final String RETENTION_KEEP = "keep";
    public static final String RETENTION_24H = "24h";
    public static final String RETENTION_NEVER = "never";

    private static final String PREF = "wahtuh_settings";

    private final SharedPreferences p;

    public Settings(Context context) {
        this.p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public boolean onboarded() { return p.getBoolean("onboarded", false); }
    public void setOnboarded(boolean v) { p.edit().putBoolean("onboarded", v).apply(); }

    /** True while the user has deliberately started the floating bubble. */
    public boolean bubbleEnabled() { return p.getBoolean("bubble_enabled", false); }
    public void setBubbleEnabled(boolean v) { p.edit().putBoolean("bubble_enabled", v).apply(); }

    /** Default is strictly local. Cloud is opt-in, never a fallback. */
    public boolean localOnly() { return p.getBoolean("local_only", true); }
    public void setLocalOnly(boolean v) { p.edit().putBoolean("local_only", v).apply(); }

    public String provider() { return p.getString("provider", LOCAL); }
    public void setProvider(String v) { p.edit().putString("provider", v).apply(); }

    public String language() { return p.getString("language", LANG_AUTO); }
    public void setLanguage(String v) { p.edit().putString("language", v).apply(); }

    public String style() { return p.getString("style", STYLE_DEFAULT); }
    public void setStyle(String v) { p.edit().putString("style", v).apply(); }

    public boolean cleanup() { return p.getBoolean("cleanup", false); }
    public void setCleanup(boolean v) { p.edit().putBoolean("cleanup", v).apply(); }

    public boolean appTone() { return p.getBoolean("app_tone", true); }
    public void setAppTone(boolean v) { p.edit().putBoolean("app_tone", v).apply(); }

    /** Opt-in: share a short term list from the focused field with AI polish. */
    public boolean fieldContext() { return p.getBoolean("field_context", false); }
    public void setFieldContext(boolean v) { p.edit().putBoolean("field_context", v).apply(); }

    /** Hold with text selected to rewrite that selection with a spoken command. */
    public boolean commandMode() { return p.getBoolean("command_mode", true); }
    public void setCommandMode(boolean v) { p.edit().putBoolean("command_mode", v).apply(); }

    public boolean punctuation() { return p.getBoolean("punctuation", true); }
    public void setPunctuation(boolean v) { p.edit().putBoolean("punctuation", v).apply(); }

    public boolean fillers() { return p.getBoolean("fillers", true); }
    public void setFillers(boolean v) { p.edit().putBoolean("fillers", v).apply(); }

    public boolean dictionary() { return p.getBoolean("dictionary", true); }
    public void setDictionary(boolean v) { p.edit().putBoolean("dictionary", v).apply(); }


    /** Local transcript retention: keep, auto-delete after 24h, or never save. */
    public String retention() { return p.getString("retention", RETENTION_KEEP); }
    public void setRetention(String v) { p.edit().putString("retention", v).apply(); }
    public boolean retainsHistory() { return !RETENTION_NEVER.equals(retention()); }

    public static String retentionLabel(String value) {
        if (RETENTION_24H.equals(value)) return "24 HOURS";
        if (RETENTION_NEVER.equals(value)) return "NEVER";
        return "KEEP";
    }

    /** 0 = small, 1 = normal, 2 = large. */
    public int bubbleSize() { return p.getInt("bubble_size", 1); }
    public void setBubbleSize(int v) { p.edit().putInt("bubble_size", v).apply(); }

    /** Bubble opacity percentage, 40-100. */
    public int bubbleAlpha() { return p.getInt("bubble_alpha", 100); }
    public void setBubbleAlpha(int v) { p.edit().putInt("bubble_alpha", v).apply(); }

    public String userDictionary() { return p.getString("user_dictionary", ""); }
    public void setUserDictionary(String v) { p.edit().putString("user_dictionary", v).apply(); }

    public String snippets() { return p.getString("snippets", ""); }
    public void setSnippets(String v) { p.edit().putString("snippets", v).apply(); }

    public String cloudLanguage() {
        String l = language();
        return LANG_AUTO.equals(l) ? "" : l;
    }

    public static String languageLabel(String code) {
        if (LANG_EN.equals(code)) return "English";
        if (LANG_DE.equals(code)) return "German";
        if (LANG_FR.equals(code)) return "French";
        if (LANG_ES.equals(code)) return "Spanish";
        if (LANG_IT.equals(code)) return "Italian";
        if (LANG_NL.equals(code)) return "Dutch";
        if (LANG_PL.equals(code)) return "Polish";
        return "Auto-detect";
    }

    public String keyName(String provider) { return "key_" + provider; }

    public String providerLabel() {
        switch (provider()) {
            case GROQ: return "Groq";
            case OPENAI: return "OpenAI";
            case GEMINI: return "Gemini";
            case DEEPGRAM: return "Deepgram";
            default: return "On-device";
        }
    }

    public static String providerName(String provider) {
        switch (provider) {
            case GROQ: return "Groq";
            case OPENAI: return "OpenAI";
            case GEMINI: return "Gemini";
            case DEEPGRAM: return "Deepgram";
            default: return "On-device";
        }
    }
}
