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

/** Append-only local history with an explicit retention policy. */
public final class HistoryStore {
    private static final int MAX = 300;
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;

    private final File file;
    private final File audioDir;
    private final String retention;

    public HistoryStore(Context context) {
        this.file = new File(context.getFilesDir(), "history.jsonl");
        this.audioDir = new File(context.getFilesDir(), "audio");
        this.retention = new Settings(context).retention();
    }

    public synchronized void append(HistoryEntry e) {
        if (Settings.RETENTION_NEVER.equals(retention)) {
            purgeAll();
            return;
        }
        pruneForPolicy();
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            out.write((e.toJson().toString() + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
        trim();
    }

    public synchronized List<HistoryEntry> list() {
        if (Settings.RETENTION_NEVER.equals(retention)) {
            purgeAll();
            return new ArrayList<>();
        }
        pruneForPolicy();
        List<HistoryEntry> out = readAll();
        Collections.reverse(out);
        return out;
    }

    public synchronized void clear() {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        deleteAudio(0);
    }

    public synchronized void remove(String id) {
        List<HistoryEntry> all = readAll();
        List<HistoryEntry> keep = new ArrayList<>();
        for (HistoryEntry e : all) {
            if (e.id == null || !e.id.equals(id)) keep.add(e);
        }
        writeAll(keep);
        if (id != null) {
            //noinspection ResultOfMethodCallIgnored
            new File(audioDir, id + ".wav").delete();
        }
    }

    public static String formatTime(long ms) {
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    private void trim() {
        List<HistoryEntry> all = readAll();
        if (all.size() <= MAX) return;
        writeAll(all.subList(all.size() - MAX, all.size()));
    }

    private void pruneForPolicy() {
        if (Settings.RETENTION_NEVER.equals(retention)) {
            purgeAll();
            return;
        }
        if (!Settings.RETENTION_24H.equals(retention)) return;
        long cutoff = System.currentTimeMillis() - DAY_MS;
        List<HistoryEntry> all = readAll();
        if (all.isEmpty()) return;
        List<HistoryEntry> keep = new ArrayList<>();
        for (HistoryEntry e : all) {
            if (e.createdAt >= cutoff) keep.add(e);
        }
        if (keep.size() != all.size()) writeAll(keep);
        deleteAudio(cutoff);
    }

    private List<HistoryEntry> readAll() {
        List<HistoryEntry> out = new ArrayList<>();
        if (!file.isFile()) return out;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                try { out.add(HistoryEntry.fromJson(new JSONObject(line))); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void writeAll(List<HistoryEntry> entries) {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            for (HistoryEntry e : entries) {
                out.write((e.toJson().toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    private void purgeAll() {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        deleteAudio(0);
    }

    private void deleteAudio(long cutoff) {
        File[] files = audioDir.listFiles();
        if (files == null) return;
        for (File audio : files) {
            if (cutoff <= 0 || audio.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                audio.delete();
            }
        }
    }
}
