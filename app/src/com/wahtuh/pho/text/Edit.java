package com.wahtuh.pho.text;

import org.json.JSONException;
import org.json.JSONObject;

/** One reversible change made to a raw transcript. */
public final class Edit {
    public final String rule;
    public final String from;
    public final String to;
    public final int index;

    public Edit(String rule, String from, String to, int index) {
        this.rule = rule;
        this.from = from;
        this.to = to;
        this.index = index;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("rule", rule);
        o.put("from", from);
        o.put("to", to);
        o.put("index", index);
        return o;
    }

    @Override
    public String toString() {
        return rule + " @ " + index + ": " + from + " -> " + to;
    }
}
