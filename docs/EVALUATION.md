# Wahtuh Pho — Evaluation Plan & First Measured Results

**Date:** 2026-10-19 · **Status:** FIRST MEASURED RUN COMPLETE (x86_64 VM).
§6 below holds real measured numbers from 93 transcription runs
(3 models × 31 clips). Method (§1–§5) is unchanged in spirit; the corpus
section now describes the data actually used.

**Rule carried over from the plan:** any number in a results table must be
traceable to a recorded run. Raw per-run output (hypothesis text, load_ms,
latency_ms, peak RSS, exit codes) is in
`docs/bench_runs_tiny.jsonl`, `docs/bench_runs_base.jsonl`, and
`docs/bench_runs_small.jsonl` (one JSON object per line, split per model for
size).

---

## 1. Why evaluate

Wahtuh Pho is push-to-talk dictation, not live captioning. What matters for
the product decision (engine choice, model size, ship/no-ship) is:

1. **Accuracy** on English, Telugu, and Telugu–English code-switching
   ("Tenglish") — the headline language story.
2. **Latency** from end-of-speech to inserted text (the Wispr Flow "feels
   instant" bar).
3. **Resource footprint** on a phone: peak RSS during inference, model file
   size, load time.

## 2. Corpus

### 2.1 Corpus actually used (2026-10-19 run)

Manifest: `docs/eval_manifest.json` — 31 entries, each `{id, file, lang,
reference, license, source_url}`. All clips verified as 16-bit PCM mono
16 kHz WAV (checked per file with Python `wave`: channels=1, sampwidth=2,
framerate=16000).

| Subset | Clips | Total audio | License / source |
|---|---|---|---|
| `jfk.wav` (en) | 1 | 11.0 s | whisper.cpp project sample (public-domain speech excerpt); https://raw.githubusercontent.com/ggml-org/whisper.cpp/master/samples/jfk.wav |
| `en_fleurs_1..15.wav` (en) | 15 | 158.2 s (4.8–16.8 s/clip) | **CC-BY-4.0** (Google FLEURS) |
| `te_fleurs_1..15.wav` (te) | 15 | 150.3 s (4.8–14.4 s/clip) | **CC-BY-4.0** (Google FLEURS) |

FLEURS citation (use wherever the corpus is described): Conneau et al.,
"FLEURS: Few-shot Learning Evaluation of Universal Representations of
Speech", arXiv:2206.12139. Source: https://huggingface.co/datasets/google/fleurs.

Layout:

```
~/workspace/corpus/audio/        # 31 *.wav (16 kHz mono s16)
~/workspace/wahtuh-pho/docs/eval_manifest.json       # ids, refs, licenses
~/workspace/wahtuh-pho/docs/bench_runs_{tiny,base,small}.jsonl  # raw runs, per model
```

**Hard rule (kept):** only consented recordings or appropriately licensed
data. No scraped audio, no audio of non-consenting speakers, no PII in
reference transcripts.

### 2.2 Not yet collected (targets, not claims)

- ≥ 100 Telugu clips, ≥ 100 English clips (currently 15 + 16 incl. jfk).
- **Telugu–English code-switch clips: none exist — code-switching not
  evaluated** (no code-switch clips in corpus; nothing was invented).
- Noisy-condition samples: none yet.

## 3. Metrics

### 3.1 Accuracy

- **WER (word error rate):** jiwer 4.0.0 (`jiwer.wer`) on explicitly
  normalized text (normalization documented in §6.2 — jiwer's own defaults do
  no case folding or punctuation removal). WER = (S + D + I) / N; values
  above 1.0 are possible and reported as-is.
- **CER (character error rate):** `jiwer.cer` on the same normalized text;
  primary metric for Telugu, where word boundaries are less stable.
- **Code-switch accuracy:** not computed — no code-switch clips (§2.2).

### 3.2 Latency

- `latency_ms` from the bench harness: inference only (model load reported
  separately as `load_ms`). Report **p50 / p95** per (model, language);
  percentiles by linear interpolation on sorted values
  (rank = (n−1)·p/100). n = 15–16 clips per cell in this run — below the
  30-clip target for stable percentiles; treat p95 as rough.
- One run per (model, clip) in this run (no repetitions yet).

### 3.3 Footprint

- **Peak RSS** per transcription process (method in §6.2), model load
  included.
- **Model file size** on disk and **load time** (`init()` wall-clock,
  `load_ms`).

## 4. Procedure (as actually run, 2026-10-19)

1. Built once: `cargo build --release --example whisper_bench`
   (cargo 1.99.1, rustc 1.99.1) in `crates/wahtuh-transcribe`.
   Engine: whisper.cpp **1.8.3** via whisper-rs 0.16.0 / whisper-rs-sys
   0.15.0 (version read from the vendored `whisper.cpp/CMakeLists.txt`:
   `project("whisper.cpp" VERSION 1.8.3)`).
2. For each model in {tiny, base, small} × each of the 31 manifest clips,
   ran `whisper_bench <model.bin> <wav> <lang> 2` — lang `en`/`te` from the
   manifest, 2 inference threads — capturing its single JSON stdout line
   (`text`, `load_ms`, `latency_ms`, `segments`) and the process peak RSS.
3. Scored all 93 hypotheses against manifest references with jiwer 4.0.0
   under the §6.2 normalization; aggregated per (model, language).

## 5. Hardware matrix

| Machine | Arch | Role | Status |
|---|---|---|---|
| This Linux VM: **AMD EPYC 9D25, 2 vCPUs, 7.7 GB RAM, x86_64, no GPU** | x86_64 | Initial bring-up, correctness, rough latency | **Measured 2026-10-19** (§6) |
| ARM64 Android device | ARM64 (arm64-v8a) | Real target: on-device latency, RSS, thermal behavior | **No ARM64 numbers — no device available** |

Apple Silicon is not a target.

## 6. Benchmark results — 2026-10-19 (measured, x86_64 VM)

93/93 runs succeeded (exit 0, valid JSON). Threads: 2. One run per cell.

| Model | Lang | Clips | Mean WER | Mean CER | p50 latency | p95 latency | Mean load | Peak RSS | Model file |
|---|---|---|---|---|---|---|---|---|---|
| tiny | en | 16 | 0.139 | 0.068 | 1,824 ms | 2,366 ms | 103 ms | 214 MiB | 77,691,713 B |
| tiny | te | 15 | 1.182 | 1.045 | 7,516 ms | 10,921 ms | 129 ms | 214 MiB | 77,691,713 B |
| base | en | 16 | 0.118 | 0.051 | 3,675 ms | 4,309 ms | 165 ms | 327 MiB | 147,951,465 B |
| base | te | 15 | 1.275 | 1.000 | 8,557 ms | 16,385 ms | 271 ms | 327 MiB | 147,951,465 B |
| small | en | 16 | 0.075 | 0.038 | 14,071 ms | 17,273 ms | 1,205 ms | 765 MiB | 487,601,967 B |
| small | te | 15 | 1.169 | 1.024 | 33,076 ms | 41,001 ms | 1,159 ms | 765 MiB | 487,601,967 B |

Reading notes (no claims beyond the table):

- Latency is inference only; load time is the separate `load_ms` column
  (model `init()` wall-clock).
- Telugu: all three models land at 117–128% mean WER / ~100–104% mean CER.
  Raw hypotheses (in `docs/bench_runs_*.jsonl`) show why: Telugu-script
  gibberish, mixed-script hallucinations (Latin/CJK fragments inside
  Telugu-script output), and near-empty outputs (e.g. a single character).
  English improves monotonically tiny → base → small, as expected.
- **Code-switching not evaluated — no code-switch clips in corpus.**
- **No ARM64 numbers — no device available.**

### 6.1 Models

Multilingual ggml builds: `ggml-tiny.bin` (74.1 MiB), `ggml-base.bin`
(141.1 MiB), `ggml-small.bin` (465.1 MiB), in `~/workspace/models/`.

### 6.2 Scoring & measurement methods (exact)

- **jiwer 4.0.0** (`pip install jiwer`; version via
  `importlib.metadata.version("jiwer")`). `jiwer.wer` / `jiwer.cer` called
  with defaults. Verified on the installed build, jiwer 4.0.0's defaults
  are: wer → `RemoveMultipleSpaces → Strip → ReduceToListOfListOfWords`
  (split on space); cer → `Strip → ReduceToListOfListOfChars`. jiwer itself
  does **no** lowercasing and **no** punctuation removal, so all
  normalization below was applied by the scoring script first.
- **Text normalization** (identical for reference and hypothesis):
  1. `unicodedata.normalize("NFC", text)`
  2. `.lower()` (Telugu has no case; affects Latin only)
  3. delete every character whose Unicode category starts with "P"
     (punctuation) or "S" (symbol); letters, **marks** (including Telugu
     dependent vowel signs / virama, category M\*), numbers and whitespace
     are kept
  4. collapse whitespace runs to one space, strip ends.
  (An earlier draft used regex `[^\\w\\s]`, which wrongly deleted M\*
  marks and mangled Telugu; it was replaced before any reported number
  was produced.)
- **Peak RSS:** `/usr/bin/time` is not installed on this VM, so the
  per-process peak was measured with `os.wait4()` on the bench child:
  `rusage.ru_maxrss` (kilobytes on Linux) — the same kernel-tracked
  maximum-RSS figure `/usr/bin/time -v` reports. Reported per
  (model, language) as the max over that cell's runs (min–max spread was
  <1% within each model). Method validated against a child allocating a
  known 200 MiB (reported 214,904 KiB ≈ 210 MiB including interpreter).
- **Percentiles:** linear interpolation on sorted per-clip latencies
  (rank = (n−1)·p/100, numpy-`linear` style).
- No repetitions: each (model, clip) ran once.

### 6.3 What remains blocked

1. **ARM64 Android device** — for the only latency/RSS numbers that matter
   for the ship decision.
2. **Corpus growth** — code-switch clips, noisy-condition clips, and larger
   en/te sets per §2.2.
3. **Repeated runs** — 3 repetitions per cell for mean ± range.
