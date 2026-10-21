package com.wahtuh.pho.asr;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Models are bundled in the APK and unpacked once into app-private storage,
 * because the native runtimes need real file paths. No network is involved.
 */
public final class ModelInstaller {
    private static final String VERSION = "1";

    private ModelInstaller() {}

    public static File modelsDir(Context context) {
        return new File(context.getFilesDir(), "models");
    }

    public static File voskDir(Context context) {
        return new File(modelsDir(context), "vosk-te");
    }

    public static File whisperDir(Context context) {
        return new File(modelsDir(context), "whisper-tiny");
    }

    public static boolean ready(Context context) {
        File marker = new File(modelsDir(context), ".ready");
        return marker.isFile()
                && new File(voskDir(context), "conf/model.conf").isFile()
                && new File(whisperDir(context), "tiny-decoder.int8.onnx").isFile();
    }

    public static synchronized void install(Context context) throws Exception {
        File dir = modelsDir(context);
        File marker = new File(dir, ".ready");
        if (marker.isFile() && VERSION.equals(readText(marker))
                && new File(voskDir(context), "conf/model.conf").isFile()
                && new File(whisperDir(context), "tiny-decoder.int8.onnx").isFile()) {
            return;
        }
        dir.mkdirs();
        AssetManager assets = context.getAssets();
        copyTree(assets, "models/vosk-te", voskDir(context));
        copyTree(assets, "models/whisper-tiny", whisperDir(context));
        try (FileOutputStream out = new FileOutputStream(marker)) {
            out.write(VERSION.getBytes("UTF-8"));
        }
    }

    public static long installedBytes(Context context) {
        return size(modelsDir(context));
    }

    private static void copyTree(AssetManager assets, String assetPath, File target) throws Exception {
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            target.getParentFile().mkdirs();
            try (InputStream in = assets.open(assetPath);
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return;
        }
        target.mkdirs();
        for (String child : children) {
            copyTree(assets, assetPath + "/" + child, new File(target, child));
        }
    }

    private static long size(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long n = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) n += size(k);
        return n;
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
