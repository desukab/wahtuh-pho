package com.wahtuh.pho.update;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Reads the latest public GitHub release without sending any user data. */
public final class UpdateChecker {
    private static final String LATEST_URL =
            "https://api.github.com/repos/desukab/wahtuh-pho/releases/latest";

    public static final class Release {
        public final String version;
        public final String pageUrl;
        public final String apkUrl;
        public final boolean newer;

        Release(String version, String pageUrl, String apkUrl, boolean newer) {
            this.version = version;
            this.pageUrl = pageUrl;
            this.apkUrl = apkUrl;
            this.newer = newer;
        }
    }

    private UpdateChecker() {}

    public static Release latest(String currentVersion) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(LATEST_URL).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        c.setUseCaches(false);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        c.setRequestProperty("User-Agent", "Wahtuh-Pho-Android");
        try {
            int code = c.getResponseCode();
            String body = read(c);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("update check HTTP " + code);
            }
            JSONObject json = new JSONObject(body);
            String tag = json.optString("tag_name", "").trim();
            String version = cleanVersion(tag);
            if (version.isEmpty()) throw new IllegalStateException("update check had no tag");
            String page = json.optString("html_url", "");
            String apk = "";
            JSONArray assets = json.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.optString("name", "");
                    if (name.toLowerCase(java.util.Locale.US).endsWith(".apk")) {
                        apk = asset.optString("browser_download_url", "");
                        if (!apk.isEmpty()) break;
                    }
                }
            }
            return new Release(version, page, apk, isNewer(version, currentVersion));
        } finally {
            c.disconnect();
        }
    }

    public static boolean isNewer(String candidate, String current) {
        return compare(cleanVersion(candidate), cleanVersion(current)) > 0;
    }

    public static int compare(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = number(i < a.length ? a[i] : "");
            int y = number(i < b.length ? b[i] : "");
            if (x != y) return x < y ? -1 : 1;
        }
        return 0;
    }

    private static String cleanVersion(String value) {
        String v = value == null ? "" : value.trim();
        while (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        return v;
    }

    private static int number(String value) {
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) end++;
        if (end == 0) return 0;
        try {
            return Integer.parseInt(value.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String read(HttpURLConnection c) throws Exception {
        InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) return "";
        try (InputStream input = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
