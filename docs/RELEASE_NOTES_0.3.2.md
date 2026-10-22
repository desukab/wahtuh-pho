# Wahtuh Pho 0.3.2

Draft release notes for the Run Inspector and the audit fixes behind it. This
build has not been installed on a physical device yet; the device evidence in
this repository still comes from the v0.3.0 build.

## What changed in 0.3.2

- **Run Inspector.** Tap any transcript to see the cleaned output, the raw
  transcript, every deterministic edit, the engine and provider, speaking time,
  latency, and the language. The receipt names where audio went: device,
  provider, or device audio with a cloud text rewrite. Copy raw or clean, and
  delete the run from the same panel.
- **Transparent history card.** Cards now mark cleaned runs and expose an
  `INSPECT` action; tapping the card opens the same panel.
- **Capture hardening.** Audio is stored in chunks and joined once at stop, so
  long sessions no longer repeatedly copy the whole buffer. Microphone errors
  and the ten-minute limit are reported instead of surfacing as empty speech.
  When capture failed, the controller now shows the microphone error.
- **History integrity.** History writes use a synced temporary file and rename,
  with a copy fallback. The service and UI share one lock, and retention is read
  at use time rather than at construction, so a policy change takes effect
  immediately. The 24-hour policy also purges old legacy audio when the
  transcript file is already empty.
- **Dictionary canonicalization.** A single dictionary word now supplies its
  canonical spelling and is included in the cloud ASR vocabulary prompt. No-op
  rewrites are no longer recorded as edits.
- **Multiline snippets.** Snippet expansions round-trip embedded newlines and
  backslashes instead of being flattened to one line.
- **IME Enter.** Enter now uses the editor's actual action and falls back to a
  newline when the field asks for no enter action.
- **Cancel correctness.** Cancelling during transcription no longer writes the
  cancelled result into history or delivers it to the field.
- **Sensitive-app stand-down.** Banking, payment, wallet, authenticator, and
  password-manager packages are refused by default in accessibility insertion
  and the IME. Password fields are always refused. Settings can relax the
  package guard, but never the password guard.
- **Insertion recovery.** Boot, package-replace, and screen-unlock receivers
  restore a deliberately-running bubble. A 15-second watchdog detects when
  Android turns off accessibility insertion, hides the bubble, and posts a FIX
  notification.
- **Dictionary and snippet management.** Both pages now support search, edit,
  duplicate rejection, and length validation. Snippets still round-trip
  multiline expansions.
- **Per-category tone.** Email, workplace, chat, and other apps can each keep
  AUTO, force a style, or fall back to the global style. AUTO makes email
  formal and chat casual. It reads the foreground package name only.
- **Silero VAD v5.** The audio path now runs the 2.3 MB Silero model locally
  through ONNX Runtime, with the adaptive RMS/ZCR detector as a fallback. It
  improves speech/no-speech and phrase-boundary decisions without sending audio
  anywhere. On-device latency and battery cost still need measurement.
- **On-device live words.** A 43 MB int8 streaming zipformer runs through
  sherpa-onnx and streams English words while recording. The IME places them in
  the composing region; the bubble and notification show a preview. The final
  transcript still comes from Whistle. The Settings switch can turn the stream
  off.

## Verification

- Text pipeline: **65/65** checks.
- VAD/audio: **14/14** checks.
- Character-fit: **18/18** checks.
- Rust workspace: **78** tests passed, 1 ignored.
- ASR scoring and Telugu normalization: 4 tests passed.
- Gradle-free APK build completed with Android 35 `aapt2`, `javac`, D8,
  `zipalign`, and `apksigner`: `build/wahtuh-pho-0.3.2.apk`, 72 MB with ONNX Runtime and streaming sherpa-onnx.
- `apksigner verify` passed. `zipalign -c -v 4` passed.
- The streaming zipformer was exercised on x86_64 with a 6.5 s clip: first
  partial at ~2.5 s and full decode in ~229 ms. Android device latency and
  battery cost still need measurement.
- No release SHA-256 is recorded here because this is an uncommitted local
  build with the debug signing key, not a published release artifact.

## Known gaps

Still needed: a physical-device pass for voice, Silero VAD, Command Mode,
retention, boot restore, Field Context, and the Run Inspector; live-key retests
for all four cloud providers; and measured in-app Whistle/Silero latency. See
[PROJECT_STATUS.md](PROJECT_STATUS.md) and
[FEATURE_PARITY.md](FEATURE_PARITY.md).

## Install

```sh
./scripts/fetch-deps.sh
./scripts/fetch-models.sh
./scripts/build.sh
adb install -r build/wahtuh-pho-0.3.2.apk
```
