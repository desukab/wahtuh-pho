package com.wahtuh.pho.cloud;

import com.wahtuh.pho.data.Settings;

/**
 * The single privacy chokepoint. If local-only is on, no cloud call can happen
 * and there is no fallback: the session fails visibly instead.
 */
public final class TranscriptionRouter {
    public static final class PrivacyViolation extends RuntimeException {
        public PrivacyViolation(String message) { super(message); }
    }

    private TranscriptionRouter() {}

    public static boolean isCloud(Settings settings) {
        return !Settings.LOCAL.equals(settings.provider());
    }

    public static void assertCloudAllowed(Settings settings) {
        if (settings.localOnly() && isCloud(settings)) {
            throw new PrivacyViolation(
                    "Local-only mode is on. Cloud transcription is blocked and will not be used as a fallback.");
        }
    }
}
