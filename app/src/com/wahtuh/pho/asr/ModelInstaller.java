package com.wahtuh.pho.asr;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class ModelInstaller {
    private static final String VERSION = "whistle-1";
    private static final String ASSET = "models/whistle.cact";
    private static final long MODEL_BYTES = 16919407L;

    private ModelInstaller() {}

    public static File modelsDir(Context context) {
        return new File(context.getFilesDir(), "models");
    }

    public static File whistleModel(Context context) {
        return new File(modelsDir(context), "whistle.cact");
    }

    public static boolean ready(Context context) {
        File model = whistleModel(context);
        File marker = new File(modelsDir(context), ".ready");
        return model.isFile() && model.length() == MODEL_BYTES && marker.isFile()
                && VERSION.equals(readText(marker));
    }

    public static synchronized void install(Context context) throws Exception {
        deleteLegacy(context);
        if (ready(context)) return;
        File dir = modelsDir(context);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create model directory");
        }
        File model = whistleModel(context);
        File tmp = new File(dir, "whistle.cact.tmp");
        AssetManager assets = context.getAssets();
        try (InputStream in = assets.open(ASSET);
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[1 << 16];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        }
        if (tmp.length() != MODEL_BYTES) {
            tmp.delete();
            throw new IllegalStateException("Bundled speech model is incomplete");
        }
        if (!tmp.renameTo(model)) {
            if (model.isFile()) model.delete();
            if (!tmp.renameTo(model)) {
                tmp.delete();
                throw new IllegalStateException("Could not install speech model");
            }
        }
        try (FileOutputStream out = new FileOutputStream(new File(dir, ".ready"))) {
            out.write(VERSION.getBytes("UTF-8"));
        }
    }

    public static long installedBytes(Context context) {
        File model = whistleModel(context);
        return model.isFile() ? model.length() : 0;
    }

    private static void deleteLegacy(Context context) {
        String[] names = {"vosk-te", "whisper-tiny", "whisper-base"};
        for (String name : names) {
            deleteTree(new File(modelsDir(context), name));
        }
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static String readText(File f) {
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(64, f.length())];
            int n = in.read(b);
            return new String(b, 0, Math.max(0, n), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
