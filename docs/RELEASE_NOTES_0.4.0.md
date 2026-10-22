# Wahtuh Pho 0.4.0

Provider release on top of 0.3.2. Adds AssemblyAI as a fifth opt-in cloud
transcriber. The UI is unchanged, so the screenshots below are still from the
0.3.0 build on a vivo V2251 (1260 × 2800).

## Screenshots

- [Home](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/home.png)
- [Dictionary](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/words.png)
- [Settings](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/settings.png)
- [Offline arcade](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/arcade.png)
- [Snippets](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/snips.png) · [Styles](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/style.png)

## What changed in 0.4.0

- **AssemblyAI provider.** Opt-in transcription through AssemblyAI's
  pre-recorded API: raw WAV upload, asynchronous transcript job, then polling.
  The request uses Universal-3.5 Pro with Universal-2 as fallback. Auto mode
  uses language detection; explicit Polish sends Universal-2 directly. The
  user dictionary is sent as `keyterms_prompt`.
- **Best-effort transcript deletion.** After the text is read, the app asks
  AssemblyAI to delete the transcript. Uploaded-audio retention is still
  governed by AssemblyAI's terms.
- **Cleanup routing.** AssemblyAI is transcription-only, like Deepgram. AI
  polish continues to use a separate configured Groq, OpenAI, or Gemini key
  and sends only the final text.
- **Version bump only otherwise.** The 0.3.2 Run Inspector, Silero VAD,
  streaming English preview, sensitive-app guard, and recovery behavior are
  unchanged.

## Verification

- Text pipeline: **65/65** checks.
- VAD/audio: **14/14** checks.
- Character-fit: **18/18** checks.
- Final APK: `wahtuh-pho-0.4.0.apk`, 72 MB, arm64-v8a, minSdk 26, targetSdk 35,
  versionCode 6.
- `zipalign -c -v 4` passed.
- `apksigner verify` passed. Project signing certificate SHA-256:
  `4a8cf0a7bfaee36ce7a51ebc954e74ae16753a55d14640f2c625ba10c717f0a4`.
- SHA-256: `e6378698d1757d823e5f29e3afc92236c97441b567cde1d50a36879aad722963`
- AssemblyAI has **not** had a live call: no key was available. The adapter
  follows the documented upload/submit/poll shape, but live transcripts,
  error handling, and latency remain unverified.
- No 0.4.0 APK has been installed on a physical device.

## Known gaps

Still needed: a physical-device pass for voice, Silero VAD, streaming words,
Command Mode, retention, insertion recovery, Field Context, and the Run
Inspector; live-key retests for all five cloud providers, including AssemblyAI;
and measured in-app Whistle/Silero latency. Streaming insertion with rollback,
local Hinglish/Telugu breadth, dictionary auto-learn/sync, and per-app
web-editor shims remain out of scope. See
[FEATURE_PARITY.md](https://github.com/desukab/wahtuh-pho/blob/main/docs/FEATURE_PARITY.md)
and [PROJECT_STATUS.md](https://github.com/desukab/wahtuh-pho/blob/main/docs/PROJECT_STATUS.md).

## Install

```sh
adb install -r wahtuh-pho-0.4.0.apk
```

Or open the APK on the phone. Grant microphone, overlay, accessibility, and
notification permissions in Settings, then start the bubble.
