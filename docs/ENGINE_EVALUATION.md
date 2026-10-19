# Engine Evaluation — on-device ASR for Android ARM64

**Date:** 2026-10-19 · **Basis:** public documentation read on this date (URLs
+ access dates cited per claim). No benchmarks were run; anywhere we lack
data it says **not measured** — no numbers are invented.

**Context:** Wahtuh Pho needs offline transcription on Android ARM64 with
English, Telugu, and Telugu–English code-switching. The engine plugs into
`wahtuh-transcribe::LocalEngine` behind the `Engine` trait
(see `ARCHITECTURE.md` §8).

---

## Candidates

| | whisper.cpp | sherpa-onnx | Vosk |
|---|---|---|---|
| Repo | [ggml-org/whisper.cpp](https://github.com/ggml-org/whisper.cpp) | [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | [alphacep/vosk-api](https://github.com/alphacep/vosk-api) |
| License | **MIT** | **Apache-2.0** | **Apache-2.0** |
| ARM64/NEON | README: "Apple Silicon first-class citizen — optimized via ARM NEON"; Android listed as supported platform | Platform table: arm64 ✓ Android ✓; ships `build-android-arm64-v8a.sh`; NPU backends (QNN/RKNN) | `android/` dir in repo; small models "ideal for mobile" |
| Model sizes | tiny 75 MB / ~273 MB RAM; base 142 MB / ~388 MB; small 466 MB / ~852 MB; medium 1.5 GB; large 2.9 GB (README memory table) | Varies by arch; streaming zipformer EN ~tens of MB class | Small models ~50 MB, ~300 MB runtime (site) |
| Streaming | `whisper-stream` example: mic sampled every 0.5 s, sliding-window re-decode ("naive example"); built-in Silero-VAD support | **True streaming** (OnlineRecognizer, transducer/zipformer) | **True streaming** ("zero-latency streaming API") |
| Telugu | Multilingual models (tiny/base/small/medium/large-v3) cover Telugu per upstream Whisper's 99-language training | No Telugu model in official streaming/non-streaming lists found; Telugu only via its Whisper backend or a custom ONNX model | `vosk-model-small-te-0.42` (58 MB, Apache-2.0) exists — but published WER **87.9%** (Fleurs), i.e. unusable |
| Rust bindings | **[tazz4843/whisper-rs](https://codeberg.org/tazz4843/whisper-rs)** — listed in README bindings; v0.16.0 on docs.rs; maintained ("no plans to discontinue"); safe two-crate design (`whisper-rs` + `whisper-rs-sys` w/ bindgen) | **[sherpa-rs](https://docs.rs/sherpa-rs/0.6.5/i686-pc-windows-msvc/sherpa_rs/index.html)** v0.6.8 — safe bindings, `OfflineRecognizer`/`OnlineRecognizer`/`VoiceActivityDetector`; `download-binaries` feature fetches prebuilt libs | `rust/` dir in repo; README claims Rust bindings; maturity/maintenance not verified |
| Inference style | 30 s native window, chunked | Streaming or chunked | Streaming |

Sources (all read 2026-10-19): whisper.cpp README (license, NEON, Android,
memory table, stream example, VAD, bindings incl. `GiviMAD/whisper-jni`
precedent); sherpa-onnx README (license, platform table, language list,
`rust-api-examples/`); Vosk README + [model list](https://alphacephei.com/vosk/models)
(license, rust dir, android dir, Telugu model row with 87.9 WER);
docs.rs pages for `whisper-rs` 0.16.0 and `sherpa-rs` 0.6.8.

---

## Criterion-by-criterion notes

**License compatibility with Apache-2.0.** All three are fine at the code
level: sherpa-onnx and Vosk are Apache-2.0; whisper.cpp is MIT, which is
compatible with Apache-2.0 redistribution (keep the attribution). Two
caveats: (a) Vosk's *model catalog* mixes licenses — several models are
AGPL/GPL/CC-BY-NC-SA, so we must ship only Apache-2.0 models; (b) whisper
model *weights* are distributed via HuggingFace under the upstream Whisper
release terms — verify the exact grant before bundling.

**ARM64/NEON maturity.** whisper.cpp documents NEON as first-class and
Android as a supported platform. sherpa-onnx is arguably the most
mobile-explicit (dedicated Android build scripts, prebuilt APKs, NPU
backends). Vosk ships an Android demo. All three clear the bar on paper;
on-device RTF is **not measured** for any of them.

**Model sizes.** For a phone, realistic choices are whisper tiny/base
(75–142 MB, quantized variants smaller), a streaming zipformer, or a Vosk
small model (~40–58 MB). whisper small (466 MB) is flagship-only. None of
this is measured on our target device.

**Streaming.** True streaming (sherpa-onnx, Vosk) is nicer for live partials,
but our product is **push-to-talk dictation**, not live captioning: the
`Chunker` already emits 30 s windows, which is whisper's native context size —
an unusually clean architectural fit. Partial previews come from
`Engine::partial_transcript`, which can be served per-chunk. Streaming is
therefore a nice-to-have, not a decider.

**Telugu — the decider.** This is where the field separates honestly:
- whisper.cpp: Telugu is inside the multilingual Whisper models (the `.en`
  models are English-only — we must ship multilingual `tiny`/`base`/…).
  Quality on-device: **not measured**.
- sherpa-onnx: no official Telugu streaming model found in their model
  lists (streaming coverage is Chinese/English/Korean/French/…). Telugu
  would ride on its Whisper backend (non-streaming) — which concedes
  sherpa-onnx's main advantage (true streaming) for our headline language —
  or on a third-party ONNX model (e.g. AI4Bharat-derived), which is extra
  integration and licensing diligence.
- Vosk: an official Telugu model exists, but their own published number is
  87.9% WER — not a viable starting point.

**Rust binding quality.** whisper-rs (0.16.0, maintained, safe API over a
`-sys` bindgen crate) and sherpa-rs (0.6.8, safe API, prebuilt-binary
download feature) are both credible. Vosk's Rust binding is unverified.
Neither whisper-rs nor sherpa-rs has been verified to cross-compile for
`aarch64-linux-android` via cargo-ndk in *our* build — that is part of the
deciding experiment below.

---

## Recommendation

**Recommend: whisper.cpp, integrated via `whisper-rs` (safe bindings), with
the multilingual `base` model as the default and `tiny` as the low-end
fallback.**

Rationale: it is the only candidate with a credible Telugu story (Whisper's
multilingual training covers Telugu; Vosk's own published Telugu WER is
87.9% and sherpa-onnx has no official Telugu streaming model), its license
(MIT) is Apache-2.0-compatible, ARM NEON is documented first-class with
Android as a supported platform, its native 30 s window matches our
`Chunker` design exactly, and `whisper-rs` is a maintained, safe,
two-crate binding (v0.16.0) with a JNI precedent in the ecosystem
(`whisper-jni` is listed in the same README bindings section). The honest
weaknesses: on-device Telugu/code-switch quality and ARM64 latency are
**not measured**, and whisper is chunked rather than truly streaming —
acceptable for push-to-talk dictation, but it must be validated, not assumed.

## Deciding experiment (before committing)

1. Build a small Rust harness on `whisper-rs` (pinned version) that feeds a
   Telugu + Tenglish test corpus (recorded, ~50 utterances, with reference
   transcripts) through multilingual `tiny` and `base` (and a quantized
   variant), measuring WER/CER per language and end-to-end latency per 30 s
   chunk on x86_64 first.
2. Repeat on a real ARM64 Android device via `cargo-ndk`, verifying that
   `whisper-rs-sys` cross-compiles for `aarch64-linux-android` and measuring
   real-time factor and peak RSS against the README's memory figures.
3. As a control, run the same corpus through sherpa-onnx's Whisper backend
   (non-streaming) to confirm the choice isn't leaving quality on the table.
4. Verify the model-weights redistribution terms for bundling before shipping.

If whisper base misses the quality bar on Telugu, revisit: sherpa-onnx with
a custom Telugu ONNX model, or a larger whisper model for high-end devices.
