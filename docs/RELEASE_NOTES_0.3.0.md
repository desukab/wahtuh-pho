# Wahtuh Pho 0.3.0

Offline-first Android dictation with a floating arcade bubble. No account, no
subscription, no telemetry, and no silent cloud fallback.

This release is the one that actually ships the arcade UI. Earlier public
artifacts were still v0.1.0; the source tree had moved on, but the screenshot and
APK on the Releases page had not.

## Screenshots

Captured from this APK on a vivo V2251 (1260 × 2800):

- [Home](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/home.png)
- [Dictionary](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/words.png)
- [Settings](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/settings.png)
- [Offline arcade](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/arcade.png)
- [Snippets](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/snips.png) · [Styles](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/style.png)

## Highlights

- **Arcade UI, everywhere.** Dark indigo cabinet, neon lime/cyan/magenta,
  Press Start 2P headings, VT323 body copy, sharp panels, bevel buttons, a
  starfield/scanline backdrop, and an 8-bit launcher icon. Home, Words, Snips,
  Style, Settings, the drawer, onboarding, dialogs, and the floating bubble all
  share the same system.
- **Hold to dictate, tap for hands-free.** Hold the bubble for 380 ms to record;
  release to transcribe. A short tap still toggles hands-free.
- **Command Mode.** Select text in another app, hold the bubble, and speak an
  edit. On-device it handles case changes, bullet/numbered lists, and deletes.
  Groq, OpenAI, or Gemini adds freeform rewrites such as "make this shorter".
  Only the selection changes; the replacement is verified and can be undone
  from the notification.
- **Inline edit commands.** Delete the last sentence or word, turn the last
  sentence into a bullet or numbered list, and change case — entirely on-device.
- **History retention.** KEEP, 24 HOURS, or NEVER. NEVER deletes saved
  transcripts and legacy audio and stops saving new history. New dictation
  audio is never written to disk.
- **Boot and battery resilience.** A deliberately-running bubble is restored
  after reboot, and Settings links to the battery-optimization exemption.
- **Bubble controls.** Small, normal, or large bubble at 60%, 80%, or 100%
  opacity.
- **Session limits.** A session warns at four minutes and auto-transcribes at
  five minutes.
- **Notification undo.** Undo restores the field only if it still matches the
  inserted text.

## Existing capabilities

- Whistle 16.9 MB on-device ASR; English, German, French, Spanish, Italian,
  Dutch, Polish, plus auto-detect.
- Optional BYOK Groq, OpenAI, Gemini, and Deepgram transcription.
- Verified accessibility insertion, built-in IME fallback, and clipboard
  fallback.
- Self-corrections, developer identifiers and file tags, lists, snippets,
  personal dictionary, five writing styles, app-aware tone, and opt-in Field
  Context.
- Snooze, waveform, timer, password-field refusal, and encrypted API keys.

## Verification

- Text pipeline: **51/51** checks.
- VAD/audio: **11/11** checks.
- ASR scoring/normalization: **4/4** unit tests.
- Final APK: `wahtuh-pho-0.3.0.apk`, 18 MB, arm64-v8a, minSdk 26, targetSdk 35.
- Installed over the previous build with `adb install -r`; Home, Words, Snips,
  Style, Settings, the drawer, the live bubble, and all three arcade games were
  exercised on a vivo V2251 / Android 15.
- `zipalign -c -v 4` passed.
- `apksigner verify` passed. Project signing certificate SHA-256:
  `4a8cf0a7bfaee36ce7a51ebc954e74ae16753a55d14640f2c625ba10c717f0a4`.
- SHA-256: `e274e26b3bfded4927afc4bd0afb90e31d7138fd8468727a008a03607b622460`

## Known gaps

Streaming partials, local Hinglish/Telugu breadth, dictionary auto-learn/sync,
banking-app stand-down, and per-app web-editor shims are not in this build. Voice,
Command Mode, retention, boot recovery, Field Context, and selected-text
replacement still need a broader field pass. No Play Store distribution; install
the APK directly. See [FEATURE_PARITY.md](FEATURE_PARITY.md) for the comparison
and [PROJECT_STATUS.md](PROJECT_STATUS.md) for the device-test checklist.

## Install

```sh
adb install -r wahtuh-pho-0.3.0.apk
```

Or open the APK on the phone. Grant microphone, overlay, accessibility, and
notification permissions in Settings, then start the bubble.
