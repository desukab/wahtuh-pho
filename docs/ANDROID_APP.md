# Android app guide

## Screens

- **Onboarding** — arcade attract screen, cloud-key choice, provider, language, permission checklist, finish.
- **Home** — word count, privacy-mode switch, setup warnings, transcripts grouped by day with copy/delete.
- **Words** — user dictionary; `from=to` or a standalone word; a switch disables all replacements.
- **Snips** — `trigger=full text` expansions applied on word boundaries.
- **Style** — default, formal, casual, very casual, excited.
- **Settings** — provider cards, encrypted API keys, language chips, permission fixes, AI polish, Command Mode, bubble size/opacity, history retention, transcript clearing.
- **Bubble** — dark pixel pill with waveform, small/normal/large and 60/80/100% opacity; expands to a timer while recording and warns at four minutes, then auto-transcribes at five.
- **Keyboard** — QWERTY/numbers with Dictate and Setup.

## Languages

Whistle supports **English, German, French, Spanish, Italian, Dutch, and Polish**, plus auto-detect. Telugu is deliberately excluded.

Cloud providers expose the same language list. Deepgram auto mode uses `language=multi`, which supports all seven listed languages except Polish; choose Polish explicitly for Deepgram. AssemblyAI auto mode uses language detection and sends Universal-3.5 Pro plus Universal-2, so Polish falls back to Universal-2 automatically.

## Providers

| Provider | Path | Key |
|---|---|---|
| On-device | Whistle JNI, 16 kHz | none |
| Groq | `whisper-large-v3-turbo` | user key |
| Gemini | `gemini-3.5-transcribe` | user key |
| Deepgram | `nova-3` | user key |
| AssemblyAI | `universal-3-5-pro` → `universal-2` | user key |
| OpenAI | `gpt-transcribe` | user key |

Cloud is never used as a fallback. Local-only mode blocks every cloud call. Keys are encrypted with an Android Keystore AES/GCM key and stored only as ciphertext.

## Text policy

1. Apply local inline edit commands (delete last sentence/word, bullet/numbered list, case changes).
2. Normalize whitespace.
3. Expand spoken punctuation.
4. Remove fillers if enabled.
5. Resolve self-corrections.
6. Apply the dictionary and snippets, recording every replacement.
7. Tidy spacing, sentence-case Latin starts, add terminal punctuation.
8. Optionally apply the selected style with AI cleanup (cloud only).
9. Store raw text, final text, language, engine, provider, latency, and edits.

No translation between languages and no invented words. Every change is reversible via the raw transcript.

## Insertion behavior

`InsertAccessibilityService` re-reads the focused editable node at insertion time and refuses password and own-package fields. It pastes at the cursor first, waits for the field to update, and verifies the resulting text. If paste is rejected it falls back to `ACTION_SET_TEXT`, then verifies again. It adds a separating space when joining existing words and places the cursor after the insertion. The notification Undo action calls `undoLast()`, which restores the exact prior text only when the field has not changed. With text selected, holding the bubble starts Command Mode. Case, list, and delete instructions run on-device; freeform instructions use the configured Groq, OpenAI, or Gemini key. `replaceSelection()` rewrites only that selection after verifying it still matches. The clipboard is overwritten by design. If every path is refused, the transcript is copied and reported. When the focused field reports a character limit, only the fitting part is inserted; the remaining dictated words are copied to the clipboard and shown in a notification instead of being dropped silently.

## On-device model

- Bundled asset: `app/assets/models/whistle.cact` (16,919,407 bytes).
- Copied once to `files/models/whistle.cact` with a `.ready` marker.
- Loaded process-wide through `native/whistle_jni.cpp` against `libneedle.a`.
- The arm64 runtime libraries are in `app/jniLibs/arm64-v8a/`.
- Legacy Whisper/Vosk model folders are deleted on upgrade.

## Build

```bash
export ANDROID_JAR=$HOME/asdk/android-35/android.jar
./scripts/fetch-deps.sh
./scripts/fetch-models.sh
./scripts/build.sh
```

`scripts/build-native.sh` is the optional Termux arm64 rebuild path for `libwhistle_jni.so` and the bundled `libc++_shared.so`. The app is built without Gradle.

## Storage

- `files/models/` — unpacked Whistle model and version marker
- `files/history.jsonl` — JSONL, max 300 entries, subject to KEEP / 24 HOURS / NEVER
- `files/audio/` — legacy only; no new recordings are written and retention deletes leftovers
- `shared_prefs/` — settings; API key ciphertext only, with the key in Keystore
