package com.wahtuh.pho.text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** Word and character error rates with Unicode-aware, script-preserving tokens. */
public final class Metrics {
    public static final class Score {
        public final double wer;
        public final double cer;
        public final int refWords;
        public final int substitutions;
        public final int deletions;
        public final int insertions;

        Score(double wer, double cer, int refWords, int s, int d, int i) {
            this.wer = wer;
            this.cer = cer;
            this.refWords = refWords;
            this.substitutions = s;
            this.deletions = d;
            this.insertions = i;
        }

        public String summary() {
            return String.format(java.util.Locale.US,
                    "WER %.1f%%  CER %.1f%%  (S=%d D=%d I=%d, ref words=%d)",
                    wer * 100.0, cer * 100.0, substitutions, deletions, insertions, refWords);
        }
    }

    private Metrics() {}

    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFC)
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return n;
    }

    public static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        for (String t : normalize(s).split(" ")) if (!t.isEmpty()) out.add(t);
        return out;
    }

    public static Score score(String reference, String hypothesis) {
        List<String> r = words(reference);
        List<String> h = words(hypothesis);
        int[][] d = new int[r.size() + 1][h.size() + 1];
        int[][] op = new int[r.size() + 1][h.size() + 1];
        for (int i = 0; i <= r.size(); i++) { d[i][0] = i; op[i][0] = 2; }
        for (int j = 0; j <= h.size(); j++) { d[0][j] = j; op[0][j] = 3; }
        for (int i = 1; i <= r.size(); i++) {
            for (int j = 1; j <= h.size(); j++) {
                int sub = d[i - 1][j - 1] + (r.get(i - 1).equals(h.get(j - 1)) ? 0 : 1);
                int del = d[i - 1][j] + 1;
                int ins = d[i][j - 1] + 1;
                int best = sub;
                int o = 1;
                if (del < best) { best = del; o = 2; }
                if (ins < best) { best = ins; o = 3; }
                d[i][j] = best;
                op[i][j] = o;
            }
        }
        int s = 0, del = 0, ins = 0;
        int i = r.size(), j = h.size();
        while (i > 0 || j > 0) {
            int o = op[i][j];
            if (i > 0 && j > 0 && o == 1) {
                if (!r.get(i - 1).equals(h.get(j - 1))) s++;
                i--; j--;
            } else if (i > 0 && (j == 0 || o == 2)) {
                del++; i--;
            } else {
                ins++; j--;
            }
        }
        double wer = r.isEmpty() ? (h.isEmpty() ? 0 : 1) : (double) d[r.size()][h.size()] / r.size();
        double cer = charError(reference, hypothesis);
        return new Score(wer, cer, r.size(), s, del, ins);
    }

    public static double charError(String reference, String hypothesis) {
        String r = normalize(reference).replace(" ", "");
        String h = normalize(hypothesis).replace(" ", "");
        int[] prev = new int[h.length() + 1];
        int[] cur = new int[h.length() + 1];
        for (int j = 0; j <= h.length(); j++) prev[j] = j;
        for (int i = 1; i <= r.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= h.length(); j++) {
                int sub = prev[j - 1] + (r.charAt(i - 1) == h.charAt(j - 1) ? 0 : 1);
                cur[j] = Math.min(sub, Math.min(prev[j] + 1, cur[j - 1] + 1));
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return r.isEmpty() ? (h.isEmpty() ? 0 : 1) : (double) prev[h.length()] / r.length();
    }
}
