# Wahtuh Pho — Evaluation Plan

**Date:** 2026-10-19 · **Status:** PLAN ONLY. No benchmarks have been run and
no accuracy/latency numbers exist anywhere in this project. This document
describes *what* will be measured, *how*, and *what is blocking it*. Any
number appearing in a future results table must be traceable to a recorded
run; until then, every results field reads **not measured**.

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

## 2. Corpus plan

### 2.1 What audio is needed

| Subset | Source | License / consent |
|---|---|---|
| Telugu read speech | Mozilla Common Voice, Telugu validated subset | Corpus distributed under **CC0** (verify the license file in the downloaded release before use) |
| English read speech (baseline) | Mozilla Common Voice, English validated subset | CC0, same verification |
| Telugu–English code-switch | Self-recorded utterances (no public corpus covers our Tenglish style) | Written speaker consent per contributor; consent log kept with the corpus |
| Noisy-condition samples | Self-recorded EN + TE in realistic noise (street, office) | Same consent log |

**Hard rule:** only consented recordings or appropriately licensed data. No
scraped audio, no audio of non-consenting speakers, no PII in reference
transcripts. If a clip's license is unclear, it is excluded.

### 2.2 Corpus layout

```
eval/corpus/
├── telugu/clean/        # *.wav (16 kHz mono) + refs/*.txt
├── telugu/noisy/
├── english/clean/
├── english/noisy/
└── codeswitch/clean/    # Tenglish utterances
eval/
├── CONSENT.md           # who recorded what, consent status (no PII)
├── LICENSES.md          # license evidence per subset
└── runs/                # one dir per benchmark run: params + raw outputs
```

- Audio normalized to **16 kHz mono WAV** before scoring (matches
  `AudioConfig` / engine input contract).
- Reference transcripts: one UTF-8 line per clip, verbatim (no
  normalization), reviewed by a second listener for the code-switch subset.

### 2.3 Corpus size targets (to be hit before claiming results)

- ≥ 100 Telugu clips, ≥ 100 English clips, ≥ 50 code-switch clips,
  ≥ 30 noisy clips per language. These are *targets*, not claims — the corpus
  does not exist yet.

## 3. Metrics

### 3.1 Accuracy

- **WER (word error rate):** jiwer-style. Tokenize reference and hypothesis
  into words (Unicode-aware split on whitespace; lowercase Latin only —
  Telugu has no case), compute Levenshtein alignment, then
  **WER = (S + D + I) / N** where S/D/I are substitutions, deletions,
  insertions and N is the reference word count. Report per-subset and
  macro-averaged.
- **CER (character error rate):** same formula at character level; primary
  metric for Telugu, where word boundaries are less stable than in English.
- **Code-switch accuracy:** two numbers per mixed utterance —
  (a) mixed-utterance WER as above, and
  (b) **script-token recall:** Telugu-script tokens correct ÷ Telugu-script
  tokens in the reference. A model that transliterates or drops Telugu words
  fails (b) even if (a) looks passable.

### 3.2 Latency

- **End-of-speech → `FinalResult`:** wall-clock ms from the last audio sample
  to `DictationSession::finish` returning. Report **p50 / p95** over each
  subset (minimum 30 clips per reported percentile).
- **First-partial latency** (informational): time to first
  `partial_transcript` output.
- Decode parameters (threads, beam size, model variant) recorded per run;
  changing them starts a new run.

### 3.3 Footprint

- **Peak RSS** during a session (desktop: `/usr/bin/time -v`; Android:
  `dumpsys meminfo` or Android Studio profiler), model load included.
- **Model file size** on disk and **load time** (`init()` wall-clock).
- Engine working-set expectations from `docs/ENGINE_EVALUATION.md`
  (whisper tiny ≈ 273 MB / base ≈ 388 MB per the whisper.cpp README table)
  are *vendor figures*, not measurements — the plan is to replace them with
  measured numbers.

## 4. Procedure

1. Check out this repo at a tagged commit; record `cargo --version`, rustc
   target, OS, CPU model, RAM.
2. Resample corpus to 16 kHz mono; verify each clip decodes.
3. Run the scorer (to be written: `eval/score` harness driving
   `wahtuh-core` with the real engine behind the `Engine` trait — the same
   `Chunker` 30 s / 5 s windows the product uses).
4. Decode settings frozen for the run; 3 repetitions; report mean ± range.
5. Results go in `eval/runs/<date>-<model>-<device>/RESULTS.md` with the exact
   command lines. No number is quoted anywhere else without a pointer to its
   run dir.

## 5. Hardware matrix

| Machine | Arch | Role | Status |
|---|---|---|---|
| This Linux VM (x86_64) | x86_64 | Initial bring-up, correctness, rough latency | Available |
| ARM64 Android device | ARM64 (arm64-v8a) | Real target: on-device latency, RSS, thermal behavior | **Not available yet** — device TBD |

Apple Silicon is not a target. Cross-compile checks for
`aarch64-linux-android` happen once the NDK is installed (see
`docs/ANDROID.md`).

## 6. Benchmark status

**No benchmarks have been run.** Concretely:

- `wahtuh-transcribe::LocalEngine` is a **stub**: `init()` and
  `transcribe_chunk()` return `EngineError::ModelNotBundled`. There is no
  acoustic model in the repo and no inference code path to measure.
- The `MockEngine` numbers would be meaningless (it hashes audio and returns
  canned strings); no MockEngine-based "accuracy" is or will be reported.
- `eval/` does not exist yet: no corpus, no scorer, no runs.

**Blocked on, in order:**

1. **Model weights download** — multilingual whisper `tiny`/`base` (`.bin`)
   from HuggingFace; verify the upstream Whisper release license terms before
   bundling (flagged in `docs/ENGINE_EVALUATION.md`).
2. **whisper-rs integration** — link `whisper-rs` / `whisper-rs-sys` behind
   the `Engine` trait in `LocalEngine` per the FFI plug-in contract in
   `docs/ARCHITECTURE.md` §8 (the deciding experiment in
   `docs/ENGINE_EVALUATION.md` §6 still needs its on-device data point).
3. **ARM64 Android device** — for the only latency/RSS numbers that matter
   for the ship decision.
4. **Corpus collection** — Common Voice Telugu/English subsets + consented
   self-recorded code-switch and noisy samples per §2.

When unblocked, the first run fills in §6 with a pointer to
`eval/runs/.../RESULTS.md` — and not before.
