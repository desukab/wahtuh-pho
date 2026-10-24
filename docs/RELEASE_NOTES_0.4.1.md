# Wahtuh Pho 0.4.1

Polish and retention release on top of 0.4.0. AssemblyAI can now polish with
the same key, and final ASR keeps more of what you actually said. Core screens
are unchanged, so the screenshots remain from the 0.3.0 build on a vivo V2251
(1260 × 2800); Settings gains a Fast Cloud row for cloud providers.

## Screenshots

- [Home](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/home.png)
- [Dictionary](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/words.png)
- [Settings](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/settings.png)
- [Offline arcade](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/arcade.png)
- [Snippets](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/snips.png) · [Styles](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/style.png)

## What changed in 0.4.1

- **AssemblyAI polish with the same key.** Optional AI polish now uses the
  AssemblyAI LLM Gateway (`https://llm-gateway.assemblyai.com/v1/chat/completions`)
  with the user's existing AssemblyAI key and `qwen3.5-4b-32k-fast`. Only the
  final text leaves the device for this step. No separate Groq, OpenAI, or
  Gemini key is required.
- **AssemblyAI Command Mode.** Freeform Command Mode can use the same
  AssemblyAI key through the LLM Gateway. Offline case/list/delete commands
  are unchanged.
- **Deepgram stays explicit.** Deepgram has no text-edit model, so its polish
  still needs a configured Groq, OpenAI, or Gemini key. The toggle explains
  the missing key instead of silently sending text elsewhere.
- **Full audio reaches final ASR.** Short recordings are normalized and sent
  whole instead of being VAD-trimmed first, removing a source of dropped
  words at the start and end of a clip.
- **VAD sanity fallback.** If Silero finds little or no speech but the
  adaptive energy gate finds substantially more, the adaptive plan is used.
- **Cleaner long-recording splits.** Split segments are normalized without a
  second VAD trim, and 28-second hard cuts land on the quietest nearby frame.
  Segments no longer overlap at hard cuts, which removes duplicated boundary
  words.
- **Lower cloud latency.** AssemblyAI polling starts at 250 ms and backs off to
  1 s after five seconds. Transcript deletion now runs off the response path,
  so cleanup no longer delays the returned text. Silero VAD is cached for the
  process, warmed while recording, and skipped entirely for clips under 15 s.
  Capture stop waits at most 600 ms for the audio thread.
- **Fast Cloud.** A new setting, on by default for cloud providers, inserts the
  deterministic transcript as soon as transcription returns and skips the
  extra AI polish call. Command Mode still performs its requested rewrite. Turn
  Fast Cloud off to wait for AI polish again.
- **Automatic GitHub update prompt.** On launch, the app checks the public
  latest-release endpoint at most once every six hours. When a newer tag is
  available it shows a DOWNLOAD / NOTES / LATER dialog. It sends no account,
  key, transcript, or device identifier. The drawer also has a manual
  CHECK FOR UPDATES action.
- **Version bump:** 0.4.1, versionCode 7.

## Verification

- Text pipeline: **65/65** checks.
- VAD/audio: **19/19** checks.
- Character-fit: **18/18** checks.
- Update/version comparison: **6/6** checks.
- Final APK: `wahtuh-pho-0.4.1.apk`, 72 MB, arm64-v8a, minSdk 26, targetSdk 35,
  versionCode 7.
- `zipalign -c -v 4` passed.
- `apksigner verify` passed. Project signing certificate SHA-256:
  `4a8cf0a7bfaee36ce7a51ebc954e74ae16753a55d14640f2c625ba10c717f0a4`.
- SHA-256: `7be01f0f4ccd06a95863982de62352bf0e5ea808dad42bb4a363a78abe3d201a`
- AssemblyAI transcription and LLM Gateway polish have **not** had a live
  call: no key was available. The adapters follow the documented API shapes,
  but live transcripts, polish quality, error handling, and latency remain
  unverified.
- No 0.4.1 APK has been installed on a physical device. The retention fix is
  covered by synthetic VAD/audio checks, not a field recording.
- The **1.5-second cloud end-to-end target is not verified and is not
  guaranteed**. Fast Cloud removes the polish call, but AssemblyAI
  transcription is still asynchronous. Measure on-device before treating
  1.5 s as an SLA.

## Known gaps

Still needed: a physical-device pass for voice, Silero VAD, streaming words,
Command Mode, retention, insertion recovery, Field Context, and the Run
Inspector; live-key retests for all five cloud providers, including AssemblyAI
transcription and LLM Gateway polish; and measured in-app Whistle/Silero
latency. Streaming insertion with rollback, local Hinglish/Telugu breadth,
dictionary auto-learn/sync, and per-app web-editor shims remain out of scope.
See [FEATURE_PARITY.md](https://github.com/desukab/wahtuh-pho/blob/main/docs/FEATURE_PARITY.md)
and [PROJECT_STATUS.md](https://github.com/desukab/wahtuh-pho/blob/main/docs/PROJECT_STATUS.md).

## Install

```sh
adb install -r wahtuh-pho-0.4.1.apk
```

Or open the APK on the phone. Grant microphone, overlay, accessibility, and
notification permissions in Settings, then start the bubble.
