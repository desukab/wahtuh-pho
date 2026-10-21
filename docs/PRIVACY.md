# Privacy

Wahtuh Pho is local-first. The default provider is **On-device (private)** and local-only mode is **on**.

## What stays on the device

- Microphone audio, both while recording and after transcription.
- Raw and cleaned transcripts in `files/history.jsonl`.
- Optional WAV recordings in `files/audio/<id>.wav` when **Save each recording** is enabled.
- User dictionary text, text settings, and provider choice in app preferences.
- API keys, encrypted with an AES/GCM key held by the Android Keystore.

## What can leave the device

Only when you select a cloud provider **and** turn local-only off:

| Provider | Audio sent to | Optional cleanup sends |
|---|---|---|
| Groq | `api.groq.com` | final text to Groq chat |
| OpenAI | `api.openai.com` | final text to OpenAI chat |
| Gemini | `generativelanguage.googleapis.com` | final text to Gemini chat |

The request includes the recorded audio, model name, language hint, and a short vocabulary prompt derived from your user dictionary. Cleanup is a separate request containing text only. There is no automatic fallback from local to cloud; if a cloud request fails, the dictation fails visibly.

Local-only mode is enforced in `TranscriptionRouter`, not just in the UI. A cloud provider with local-only on raises a `PrivacyViolation` before any network call.

## API keys

- Stored with `AES/GCM/NoPadding` under `AndroidKeyStore`.
- Not included in history, logs, exports, or error messages.
- Remove the key from Settings at any time. Removing it does not delete transcripts.

## History and recordings

- History is capped at 300 entries, newest first.
- **Clear transcript history** deletes `history.jsonl`; it does not delete WAV files.
- To delete recordings, clear app storage in Android settings or remove the app.
- History is never uploaded by Wahtuh Pho; cloud sync is not implemented.

## Analytics and network

Wahtuh Pho has no analytics, crash reporting, ads, or telemetry SDK. The only network calls are the cloud transcription/cleanup requests you explicitly configure. The app does request the `INTERNET` permission because cloud transcription is optional.

## Accessibility data

The insertion service reads the focused node to verify it is an editable, non-password field and to place text. It refuses password fields and Wahtuh's own package. It does not record screen contents, keylogs, or browse history.

## Contact

Open an issue in the repository for privacy questions or data-handling bugs.
