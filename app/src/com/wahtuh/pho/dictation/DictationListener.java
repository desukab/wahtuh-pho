package com.wahtuh.pho.dictation;

import com.wahtuh.pho.dictation.DictationResult;

public interface DictationListener {
    void onState(String state, String message);
    void onLevel(float rms);
    void onResult(DictationResult result);
    void onError(String message);
}
