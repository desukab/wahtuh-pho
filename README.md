# Wahtuh Pho

**Speak. It types.** Offline-first dictation for Android, with English and Telugu.

Wahtuh Pho is an independent, local-first voice keyboard and floating dictation bubble. It transcribes on your phone by default, inserts text into other apps through an accessibility service, and keeps every raw transcript next to the cleaned result. A separate Rust core explores a portable engine/pipeline for future platforms.

> Not affiliated with Wispr Flow. The interaction model (floating bubble, keyboard, transcript home) is inspired by modern dictation apps; all app code, copy, and artwork here are original.

![Home screen](docs/screenshots/home.png)

## Status

- **Android app: working v0.1.0.** Built, installed, and measured on a vivo V2251 running Android 15. Diagnostics pass 10/10.
- **Rust core: scaffold + real local Whisper engine.** 74 workspace tests pass; no Android JNI shell yet. See [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md).
- The shipped APK uses sherpa-onnx + Vosk directly on Android; it does **not** link the Rust crates yet.

## What works

- **On-device hybrid ASR** — sherpa-onnx Whisper tiny int8 for English, Vosk `vosk-model-small-te-0.42` for Telugu, automatic routing per utterance.
- **Floating bubble** — draggable cream pill with an animated waveform, live timer, and distinct recording/transcribing/error states.
- **Voice keyboard** — full QWERTY/number IME with a Dictate button.
- **Cross-app insertion** — accessibility service inserts into the focused field, with clipboard fallback; password fields and Wahtuh's own package are refused.
- **Reversible text cleanup** — punctuation, filler removal, and a user dictionary. Raw ASR text is stored and never overwritten.
- **Home dashboard** — serif hero, one primary dictation card, setup status, word/session stats, recent transcripts grouped by day, and a local-only toggle.
- **Dictionary and Settings tabs** — configuration is no longer dumped on the home screen.
- **Cloud is opt-in** — Groq, OpenAI, or Gemini keys stored encrypted with the Android Keystore. Local-only mode blocks every cloud call. No silent fallback.
- **Extras** — diagnostics with 10 self-tests, WER/CER benchmarks, and a live microphone smoke test.

## Install

Requirements: Android 8.0 (API 26) or newer, arm64 device.

1. Download `wahtuh-pho-0.1.0.apk` from the GitHub Releases page.
2. `adb install -r wahtuh-pho-0.1.0.apk` (or open the file on the phone).
3. Open the app and follow **Get set up**:
   - Enable the voice keyboard, then choose it as the current keyboard.
   - Enable text insertion (accessibility).
   - Allow display over other apps and start the floating bubble.
4. Tap **Start dictation**, or tap the bubble in any text field.

The APK bundles ~222 MB of models, so the first launch unpacks them once into app-private storage. Expect a ~131 MB download and ~350 MB of installed storage.

## Use

- **Home**: tap the mic card to dictate. Copy, clear, and review recent transcripts. Toggle local-only.
- **Bubble**: appears after you start it. Drag to move. Tap to begin; tap again to stop and insert. Recording shows a live timer and waveform.
- **Keyboard**: switch to Wahtuh in the keyboard picker, then tap **Dictate**.
- **Dictionary**: one entry per line, either `Name=Replacement` or a single word. Telugu and English entries are supported.

## Measured on device

vivo V2251, Android 15 (SDK 35), arm64-v8a, 16 kHz mono:

| Clip | Engine | Audio | WER | CER | RTF |
|---|---|---:|---:|---:|---:|
| English LibriSpeech sample | Whisper tiny int8 | 6.63 s | 0.0% | 0.0% | 0.381 |
| Telugu TTS sample | Vosk small te 0.42 | 4.73 s | 0.0% | 0.0% | 0.197 warm / 0.439 cold |
| Telugu TTS sample | Hybrid (auto) | 4.73 s | 0.0% | 0.0% | 0.663 |

Diagnostics: **10/10 checks passed**. See [docs/BENCHMARKS.md](docs/BENCHMARKS.md). The Telugu clip is synthetic Google TTS, so 0% WER is not evidence of real-world Telugu accuracy. Human field testing is still needed.

## Build the Android app

Termux/Linux, Java 17+, Android SDK platform 35, no Gradle required.

```bash
export ANDROID_JAR=$HOME/asdk/android-35/android.jar
./scripts/fetch-deps.sh       # pinned AARs, Kotlin stdlib, D8
./scripts/fetch-models.sh     # Whisper tiny int8 + Vosk Telugu + checksums
./scripts/build.sh            # aapt2, javac, D8, zipalign, apksigner
```

Output: `build/wahtuh-pho-0.1.0.apk`. Override signing with
`WAHTUH_KEYSTORE`, `WAHTUH_KEY_ALIAS`, `WAHTUH_STORE_PASS`, `WAHTUH_KEY_PASS`.
Without them a local debug keystore is generated for testing only.

The build uses **D8** from build-tools r35. Legacy `dx` does not desugar lambdas and crashes on Android 15 with a blocked `LambdaMetafactory` hidden API.

Run the dependency-free text tests with `./scripts/test.sh` (needs `ANDROID_JAR` only for compilation).

## Rust core

The `crates/` workspace contains the portable pipeline that predates the Android shell:
audio chunking, engine trait with a real whisper.cpp backend, normalization/dictionary,
provider request builders, config with a local-only guarantee, and a session/history core.
It is independent of the shipped APK today.

```bash
cargo test --workspace
```

Research and design notes live under [docs/](docs/): [architecture](docs/ARCHITECTURE.md),
[project status](docs/PROJECT_STATUS.md), [engine evaluation](docs/ENGINE_EVALUATION.md),
[REA notes](docs/REA_NOTES.md), and [Wispr Flow research](docs/WISPR_FLOW_RESEARCH.md).

## Privacy

- On-device mode never opens a network connection for transcription.
- Cloud providers are only used when you explicitly select one and turn local-only off.
- Cloud audio is sent as a standard multipart/JSON transcription request; optional cleanup sends final text only.
- API keys are encrypted with an Android Keystore AES/GCM key and never logged.
- History and optional recordings live in app-private storage. Clear history from Settings; recordings are not deleted by that action.

Full details: [docs/PRIVACY.md](docs/PRIVACY.md) and [docs/ANDROID_APP.md](docs/ANDROID_APP.md).

## Known limitations

- Telugu accuracy is validated only on synthetic speech; code-switching inside one utterance is not solved by per-utterance routing.
- Whisper tiny trades accuracy for size/speed; Whisper small was not benchmarked.
- Accessibility insertion replaces the whole field via `ACTION_SET_TEXT` when available; clipboard fallback overwrites the clipboard.
- Sideloaded v0.1.0, not a Play Store release. No account, sync, snippets, styles, or JNI link to the Rust core yet.

## License

Apache-2.0. Third-party components and model licenses are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
