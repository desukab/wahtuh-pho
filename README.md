# Wahtuh Pho

**Speak. It types.** Private, on-device dictation for Android, with English and Telugu.

Wahtuh Pho is an independent, local-first voice keyboard and floating dictation bubble. It transcribes on your phone by default, inserts text into other apps through an accessibility service, and keeps every raw transcript next to the cleaned result.

> Not affiliated with Wispr Flow. The interaction model (floating bubble, keyboard, transcript home) is inspired by modern dictation apps; all code, copy, and artwork here are original.

![Home screen](docs/screenshots/home.png)

## What works

- **On-device hybrid ASR** — sherpa-onnx Whisper tiny int8 for English, Vosk `vosk-model-small-te-0.42` for Telugu, automatic routing per utterance.
- **Floating bubble** — draggable cream pill with an animated waveform, timer, and cross-state visuals. Tap to start/stop, drag to move.
- **Voice keyboard** — a full QWERTY/number keyboard with a Dictate button, built as an Android IME.
- **Cross-app insertion** — accessibility service inserts into the focused text field, with clipboard fallback; password fields and Wahtuh's own package are refused.
- **Reversible text cleanup** — deterministic punctuation, filler removal, and user dictionary. Raw ASR text is stored and never overwritten.
- **Local history & stats** — Home shows word/session counts and recent dictations grouped by day; full history is one tap away.
- **Cloud is opt-in** — Groq, OpenAI, or Gemini keys can be stored encrypted with the Android Keystore. Local-only mode blocks every cloud call. No silent fallback.
- **Extras** — diagnostics screen with 10 self-tests, on-device WER/CER benchmarks, and a real microphone smoke test.

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
- **Keyboard**: long-press the globe/space to switch to Wahtuh, or choose it in Settings → System → Languages & input. Tap **Dictate**.
- **Dictionary**: one entry per line, either `Name=Replacement` or a single word. Telugu and English entries are supported.

## Measured on device

vivo V2251, Android 15 (SDK 35), arm64-v8a, 16 kHz mono:

| Clip | Engine | Audio | WER | CER | RTF |
|---|---|---:|---:|---:|---:|
| English LibriSpeech sample | Whisper tiny int8 | 6.63 s | 0.0% | 0.0% | 0.381 |
| Telugu TTS sample | Vosk small te 0.42 | 4.73 s | 0.0% | 0.0% | 0.197 warm / 0.439 cold |
| Telugu TTS sample | Hybrid (auto) | 4.73 s | 0.0% | 0.0% | 0.663 |

Diagnostics: **10/10 checks passed**. See [docs/BENCHMARKS.md](docs/BENCHMARKS.md) for methodology and caveats. The Telugu clip is synthetic Google TTS, so 0% WER is not evidence of real-world Telugu accuracy. Field testing with human speech is still needed.

## Build from source

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

## Privacy

- On-device mode never opens a network connection for transcription.
- Cloud providers are only used when you explicitly select one and turn local-only off.
- Cloud audio is sent as a standard multipart/JSON transcription request; optional cleanup sends final text only.
- API keys are encrypted with an Android Keystore AES/GCM key and never logged.
- History and optional recordings live in app-private storage. Clear history from Settings; recordings are not deleted by that action.

Full details: [docs/PRIVACY.md](docs/PRIVACY.md).

## Known limitations

- Telugu accuracy is validated only on synthetic speech; code-switching inside one utterance is not solved by per-utterance routing.
- Whisper tiny trades accuracy for size/speed; Whisper small was not benchmarked.
- Accessibility insertion replaces the whole field via `ACTION_SET_TEXT` when available; clipboard fallback overwrites the clipboard.
- This is a sideloaded v0.1.0, not a Play Store release. No account, sync, snippets, or styles yet.

## License

Apache-2.0. Third-party components and model licenses are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
