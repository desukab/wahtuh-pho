# Feature Parity — Wispr Flow vs Wahtuh Pho (Android)

**Updated:** 2026-10-22 · **Basis:** clean-room public research in
[WISPR_FLOW_RESEARCH.md](WISPR_FLOW_RESEARCH.md), the static Android teardown in
[ANDROID_UX_TEARDOWN.md](ANDROID_UX_TEARDOWN.md), static inspection of the
supplied Wispr Flow 2.6.2 APK (`com.wispr.flowapp`, versionCode 170), and the
shipped app source.
Wispr Flow behavior is paraphrased from public documentation and observed app
structure. No code, assets, or copy were taken.

**Status values:** `done` · `partial` · `gap` · `deliberate`

## Current matrix

| Capability | Wispr Flow (public/observed) | Wahtuh Pho 0.4.0 | Status |
|---|---|---|---|
| On-device transcription | Never. Cloud only. | Whistle 16.9 MB CPU model, offline by default, cannot silently fall back | **done (and beyond)** |
| Voice activity detection | Server/streaming VAD with phrase boundaries | Silero VAD v5 (2.3 MB, ONNX Runtime) on-device with adaptive RMS/ZCR fallback; 20 ms frame mapping and phrase splitting | **done (device verification pending)** |
| Cloud transcription | Proprietary cloud only | Groq, OpenAI, Gemini, Deepgram, AssemblyAI with user keys, opt-in | **done** |
| Push-to-talk | Hold bubble/hotkey | Hold the floating bubble; 380 ms threshold; release transcribes and inserts | **done** |
| Hands-free toggle | Tap-toggle mode | Tap the bubble to start; tap again to stop | **done** |
| Live waveform / state UI | Waveform, processing, error, repair, snoozed | Waveform, timer, working/error states, snooze | **done** |
| Auto punctuation / capitalization | Yes | Yes, plus terminal punctuation and style rules | **done** |
| Filler removal / backtracking | Yes | Fillers and false-start debounce | **done** |
| Self-corrections | Real-time understanding | Local numbers, names, noun phrases, clauses, sentences | **done** |
| Numbered lists | Yes | Spoken `first/second/third` and `make that a numbered list` | **done** |
| Bullet lists | Yes | `make that a bullet list` and spoken bullet punctuation | **done** |
| Voice edit commands | Inline commands + Command Mode (Pro) | Local: delete last sentence/word, bullets, numbers, case. Selected-text Command Mode: local subset or cloud freeform | **done** |
| Developer dictation | Syntax, file names, code formatting | `camelCase`, `snake_case`, `PascalCase`, `kebab-case`, `dot.case`, `@file.py` | **done** |
| Tone / styles | Styles and per-category personalization | 5 styles plus email/workplace/chat/other app-aware presets and per-category overrides from the foreground package only | **done** |
| Context awareness | Reads limited on-screen text | Opt-in Field Context extracts names/code terms only; never passwords | **partial / privacy-first** |
| Personal dictionary | Manual + auto-learn, cross-device sync | Manual dictionary with search, edit, duplicate/length validation; local only; no auto-learn or sync | **partial** |
| Snippets | Voice shortcuts, shared on Teams | Local voice-trigger snippets with search, edit, and validation | **done** |
| History | Local + cloud history, retention controls | Local-only history with KEEP / 24 HOURS / NEVER and audio purge | **done** |
| Run Inspector / audit trail | History list without per-run edit provenance | Raw transcript, cleaned output, exact deterministic edits, engine/provider, duration, latency, and audio-route receipt for every run | **done (and beyond)** |
| Undo | Correction flow | Notification undo restores the field only if unchanged | **done** |
| Command Mode | Rewrite highlighted text with a spoken instruction | Hold bubble with text selected; local edits offline, cloud LLM for freeform rewrites | **done** |
| Languages | 100+, auto-detect, Hinglish | 7 local languages + provider auto-detect; no Hinglish/Telugu locally | **gap (local breadth)** |
| Streaming partials | gRPC stream with word alignments | English on-device zipformer composes in the IME and previews in the bubble/notification; final transcript is batch Whistle; no word alignments or non-English stream | **partial** |
| Offline mode | None | Full offline dictation path | **done (and beyond)** |
| Account / sync | Required account; cross-device sync | No account, no sync | **deliberate** |
| Paywall / word caps | Free caps, Pro/Teams | Free, open source, no caps | **deliberate** |
| Insertion | Accessibility + own IME fallback | Accessibility paste-first verify + own IME + clipboard fallback | **done** |
| Field character limits | Not documented | Fits text to the field limit; leftover words copied and shown in a notification | **done** |
| Password protection | Refuses password/card fields | Refuses password fields and its own fields | **done** |
| Banking-app stand-down | Present | Package guard for banking, payment, wallet, authenticator, and password-manager apps in accessibility and IME; password fields always refused; user can relax the package guard | **done** |
| Per-app / web-editor shims | Present | Generic paste/`ACTION_SET_TEXT` fallbacks; package-category styles are limited to email/chat | **gap** |
| Bubble size / opacity | Settings | Small / Normal / Large and 60 / 80 / 100% opacity | **done** |
| Boot / service recovery | Watchdog, JobService, boot + unlock receivers | Boot, package-replace, and unlock receivers; 15-second accessibility watchdog with FIX notification; no JobScheduler backoff | **partial** |
| Session limits | Warn ~4 min, stop ~5 min on Android | Warns at 4 min, auto-transcribes at 5 min | **done** |
| Wake word | Reportedly Pro; unconfirmed on Android | Not implemented | **deliberate** |
| Meeting notetaker | Present on some platforms | Not implemented | **deliberate** |
| LinkedIn integration | Present in observed APK | Not applicable | **deliberate** |
| Privacy controls | Cloud storage, model training, context toggles | Local-only, no telemetry, no screen capture, encrypted keys, retention | **done (and beyond)** |

## Gaps that remain and why

1. **Local language breadth.** Whistle covers seven languages. Cloud auto-detect
   can cover more, but on-device Hinglish/Telugu needs a different model. We will
   not fake language support.
2. **Streaming partials.** English now streams words on-device through a small
   zipformer. The IME treats them as composing text; the floating bubble only
   previews them. The final transcript is batch Whistle, there are no word
   alignments, non-English languages have no stream, and word-diffed
   commit/rollback is still future work.
3. **Dictionary auto-learning and sync.** Auto-learn needs reliable correction
   signals; sync needs an account, which is a deliberate non-goal.
4. **Web-editor shims.** The sensitive-app guard is behavioral and testable
   without screen capture, but per-editor shims need real-device coverage.
5. **Deep device verification.** The v0.4.0 build still needs a physical pass
   for the newest features: Command Mode, retention, boot restore, battery, and
   character-limit overflow reporting.

## Deliberate non-goals

- Cloud-only architecture, required accounts, subscriptions, word caps.
- Broad screen capture or on-screen prose reading.
- Wake word, meeting notetaker, LinkedIn integration in this Android app.

## Bottom line

Wahtuh Pho leads on ownership, offline operation, privacy, and inspectable
cleanup. It now runs Silero VAD locally and has first-class sensitive-app
stand-down and insertion recovery. Wispr Flow still leads on local language
breadth, streaming feedback, account sync, and per-editor hardening; those gaps
are documented rather than hidden.
