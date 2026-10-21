# Wahtuh Pho 0.3.0

Offline-first Android dictation with a floating arcade bubble. No account, no
subscription, no telemetry, and no silent cloud fallback.

## Highlights

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
  transcripts and audio and stops saving new ones.
- **Boot and battery resilience.** A deliberately-running bubble is restored
  after reboot, and Settings links to the battery-optimization exemption.
- **Bubble controls.** Small, normal, or large bubble at 60%, 80%, or 100%
  opacity.
- **Session limits.** A session warns at four minutes and auto-transcribes at
  five minutes, matching Wispr Flow Android.
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
- APK builds with `aapt2` + `javac` + D8 + `apksigner`.
- SHA-256: `583a301522fe31cb64532a410ace33f6f144e8c18105435bccb4ab69737ccbb8`

## Known gaps

Streaming partials, local Hinglish/Telugu breadth, dictionary auto-learn/sync,
banking-app stand-down, per-app web-editor shims, configurable bubble
size/opacity, and hard session caps. See
[FEATURE_PARITY.md](FEATURE_PARITY.md) for the comparison and
[PROJECT_STATUS.md](PROJECT_STATUS.md) for the device-test checklist.

## Install

```sh
adb install -r wahtuh-pho-0.3.0.apk
```

Or open the APK on the phone. Grant microphone, overlay, accessibility, and
notification permissions in Settings, then start the bubble.
