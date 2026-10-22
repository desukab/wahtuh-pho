package com.wahtuh.pho.dictation;

import com.wahtuh.pho.dictation.DictationResult;

public interface DictationListener {
    void onState(String state, String message);
    void onLevel(float rms);

    /** Live on-device words while the user is still speaking. */
    default void onPartial(String text) {}

    void onResult(DictationResult result);
    void onError(String message);
}
