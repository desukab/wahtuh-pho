package com.wahtuh.pho.text;

import java.util.Collections;
import java.util.List;

public final class ProcessedResult {
    public final String raw;
    public final String text;
    public final List<Edit> edits;
    public final String language;

    public ProcessedResult(String raw, String text, List<Edit> edits, String language) {
        this.raw = raw;
        this.text = text;
        this.edits = Collections.unmodifiableList(edits);
        this.language = language;
    }

    public boolean changed() {
        return !raw.equals(text);
    }
}
