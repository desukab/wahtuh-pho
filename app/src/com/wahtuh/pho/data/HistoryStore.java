package com.wahtuh.pho.data;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Append-only local history. Raw text is always retained for reversibility. */
public final class HistoryStore {
    private static final int MAX = 300;
    private final File file;

    public HistoryStore(Context context) {
        this.file = new File(context.getFilesDir(), "history.jsonl");
    }

    public synchronized void append(HistoryEntry e) {
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            out.write((e.toJson().toString() + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
        trim();
    }

    public synchronized List<HistoryEntry> list() {
        List<HistoryEntry> out = new ArrayList<>();
        if (!file.isFile()) return out;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                try { out.add(HistoryEntry.fromJson(new JSONObject(line))); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        Collections.reverse(out);
        return out;
    }

    public synchronized void clear() {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    public static String formatTime(long ms) {
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    private void trim() {
        List<HistoryEntry> all = list();
        if (all.size() <= MAX) return;
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            for (int i = MAX - 1; i >= 0; i--) {
                out.write((all.get(i).toJson().toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }
}
