# Wahtuh Pho — Project Status

**Mission:** open-source, offline-first voice dictation for Android, with an arcade-inspired UI.
**Repo:** https://github.com/desukab/wahtuh-pho · **Updated:** 2026-10-22

> Honesty rule: only measured or directly observed results appear here. The v0.3.0 APK compiles, passes local tests, installs over the previous build, and its Home, tabs, Settings, drawer, bubble visibility, and all three arcade games were exercised on the vivo V2251. The refreshed screenshots in `docs/screenshots/` were captured from build `e274e26b…` on that device. Voice, Command Mode, retention, boot recovery, Field Context, and selected-text replacement still need a broader field pass.

## Android app — v0.3.0

- Local ASR: Cactus Compute Whistle, 16.9 MB, CPU.
- Languages: English, German, French, Spanish, Italian, Dutch, Polish, plus auto-detect.
- UI: 90s arcade theme; Home, Words, Snips, Style tabs; onboarding; dark floating bubble.
- Insertion: accessibility paste-first with verification, fallback, cursor placement, undo. Search/note placeholders such as “Search YouTube”, “Ask Google”, and “Note” are treated as empty fields instead of existing text.
- Cloud: Groq, Gemini, Deepgram, OpenAI with user keys encrypted by Android Keystore.
- Text: punctuation, filler removal, dictionary, snippets, five styles, email dictation, optional text-only cloud cleanup.
- Precision helpers: local self-corrections (`actually`, `no wait`, `I mean`, `scratch that`), spoken developer identifiers (`camelCase`, `snake_case`, `PascalCase`, `kebab-case`, `dot.case`), file tags (`@main.py`), numbered lists, and local inline edits (delete last sentence/word, bullet/numbered lists, case changes). Tests: **51/51** text, **11/11** VAD.
- Command Mode: select text, hold the bubble, and speak an edit. On-device handles case, list, and delete commands; Groq, OpenAI, or Gemini adds freeform rewrites. Replaces the selection only if it still matches, with notification undo. Sessions warn at 4 minutes and auto-submit at 5.
- History retention: KEEP / 24 HOURS / NEVER; 24-hour mode prunes old transcripts and legacy audio; NEVER purges both and stops saving. New dictation audio is never written to disk.
- Resilience: boot receiver restores a deliberately-running bubble; Settings exposes the battery-optimization exemption. Bubble size (small/normal/large) and opacity (60/80/100%) are configurable.
- Context: app-aware tone uses the package name only. An opt-in **Field Context** switch extracts names/code terms from the focused field for AI polish; passwords and the app's own fields are refused.
- Tone: app-aware preset from the foreground package name only (chat = casual, email = formal); no screen reading.
- Bubble gating: accessibility watches keyboard/window changes and shows the bubble only for an editable, non-password field in another app. Drag it onto a 5 MIN or 10 MIN X target to snooze.
- Cleanup routing: if transcription uses Deepgram, polish can use the first configured Groq, OpenAI, or Gemini key; the audio still goes only to Deepgram.
- Build: Gradle-free, `aapt2` + `javac` + D8 + `apksigner`; 18 MB APK.
- JNI: `libwhistle_jni.so` links `libneedle.a`; bundled with `libc++_shared.so`.

## Measured locally (CLI, vivo V2251 / Android 15)

- `0.wav` 3.65 s — exact transcript; 1.3–3.2 s warm with 2 threads; ~8.9 s cold.
- `en1.wav` — one substitution and spacing errors (“shed” for “schedule”, “to morrow”).
- Telugu — romanised garbage; unsupported, removed from the app.

The in-app JNI latency has not been measured yet. See [BENCHMARKS.md](BENCHMARKS.md).

## Current gaps

1. Broader field pass for voice, Command Mode selected-text replacement, inline edits, retention (KEEP/24H/NEVER), boot restore, battery exemption, text-field gating, snooze, self-correction/dev-text, numbered lists, and Field Context, including password fields, split screen, and keyboard close/open.
2. Re-test all four cloud providers with live keys, including Command Mode on Groq, OpenAI, and Gemini. Gemini response parsing reads `audioTranscription.text`; OpenAI retries `gpt-4o-transcribe` with legacy fields on a 400/404/415/422.
3. Measure Whistle quality and latency inside the app across the seven languages.
4. Field-test accented English and noisy environments; the current sample is tiny.
5. Keep the v0.3.0 APK, release notes, and screenshots in sync with the tested commit.
6. Remaining parity gaps that need engineering, not just testing: streaming partials, local Hinglish/Telugu breadth, dictionary auto-learn/sync, banking-app stand-down, and per-app web-editor shims. See [FEATURE_PARITY.md](FEATURE_PARITY.md).

## Rust core

The `crates/` workspace is independent of the shipped APK: audio chunking, engine trait with a whisper.cpp backend, normalization/dictionary, cloud request builders, local-only config, session/history. Last recorded run: 74 tests passed, 1 ignored. It still targets Whisper/Telugu research and is not wired to the app.

## Historical notes

- v0.1.0 shipped Whisper tiny + Vosk Telugu.
- v0.2.0 moved to Whisper base; Telugu remained unusable locally.
- v0.3.0 replaced both with Whistle and the arcade UI, dropping Telugu and all sherpa/Vosk/Kotlin/JNA dependencies.
- v0.3.0 added adaptive VAD, offline arcade games, editable-field bubble gating, drag-to-snooze, and provider-independent text cleanup routing.
- Prior research artifacts: `docs/WISPR_FLOW_RESEARCH.md`, `docs/ENGINE_EVALUATION.md`, `docs/EVALUATION.md`, `docs/REA_NOTES.md`.
