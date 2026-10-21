# Wahtuh Pho — Privacy

**Date:** 2026-10-19 · **Status:** describes the design as implemented in the
Rust core; the Android app does not exist yet, so platform-layer behaviors
are marked *design*.

**One line:** in the default mode, your voice never leaves your device and
the project collects nothing. Cloud transcription exists only as an explicit
opt-in, and using it means your audio is uploaded to that provider.

## Data-flow diagram (in words)

**Local-only mode (default):**

```
microphone → platform audio capture (Android AudioRecord, future)
         → wahtuh-audio: i16 PCM chunks (30 s windows, bounded memory buffer)
         → wahtuh-transcribe: on-device inference (Engine trait; LocalEngine backend, future)
         → wahtuh-normalize: text cleanup on-device
         → wahtuh-core: session result + local transcript history (JSONL on device)
```

No step in this path can reach the network: the workspace builds **std-only
with no HTTP transport**, `wahtuh-providers` is depended on by no other crate,
and `wahtuh-config` rejects any cloud configuration while `local_only = true`
(the default). The local-only guarantee is structural (no code path), not just
a flag.

**Opt-in cloud mode (user explicitly disables local-only and supplies a key):**

```
microphone → chunks → provider adapter builds a typed request
         → app layer uploads audio over HTTPS to the chosen provider
         → provider transcribes on its servers → transcript returned
         → local normalization + history (same as above)
```

Once you opt in, the audio that goes to the provider is governed by **that
provider's privacy policy and data-retention terms**, not by this document.

## What is collected

- **By Wahtuh Pho itself, in local-only mode: nothing.** No accounts, no
  sign-in, no telemetry, no analytics, no crash reporting. There is no server
  to send anything to.
- **Transcript history:** dictation results are stored **on the device** as
  JSONL (see `TranscriptHistory` in `wahtuh-core`), bounded in size, and
  **user-deletable** at any time. Both the raw transcript and the normalized
  text are kept so normalization stays auditable; nothing is synced anywhere.
- **In opt-in cloud mode:** the audio you send to a provider and the
  transcript it returns are handled per that provider's terms. Wahtuh Pho
  itself does not retain a cloud-side copy.

## Local-only guarantee — how it is enforced

1. `Config::default()` sets `local_only = true`.
2. `Config::validate()` rejects `local_only` combined with provider
   credentials or a cloud engine selection *before any audio flows*.
3. The app layer must call the deliberate, greppable `Config::allow_cloud()`
   *and* supply credentials before any provider code can run — cloud cannot
   be enabled by accident or by a silent fallback.
4. Cloud adapters don't implement the local `Engine` trait, so `wahtuh-core`
   cannot route audio to them.
5. `Config::save` refuses to persist a config that carries credentials —
   the on-disk schema has no key fields at all.

## Key storage

API keys are the user's own credentials, created in the provider's console:

- **Android (design):** stored in the **Android Keystore** (hardware-backed
  StrongBox where available) — never in `SharedPreferences`, never in the
  config file, never in logs. `HttpRequest`'s `Debug` impl redacts key-bearing
  headers.
- **Desktop / CI convention:** environment variables
  `WAHTUH_<PROVIDER>_API_KEY` (e.g. `WAHTUH_GROQ_API_KEY`), read at runtime
  via `Config::credentials_from_env`.
- **Never committed.** `.gitignore` excludes `.env`, keystores, and key files;
  CI does not use real keys. (No credentials exist in this repository —
  scanned before publish.)

## Third-party disclosure (when you opt into cloud)

Using a provider means uploading your audio to that provider over HTTPS for
server-side transcription. Providers currently supported by adapters (model
names verified against official docs 2026-10-19; see
[docs/PROVIDERS.md](PROVIDERS.md)):

| Provider | What leaves the device | Their terms |
|---|---|---|
| Groq | Audio chunks + transcript request (`api.groq.com`) | Groq privacy policy / API terms |
| OpenAI | Audio chunks + transcript request (`api.openai.com`) | OpenAI API data-usage policy |
| Gemini | Audio chunks (inline data) + prompt (`generativelanguage.googleapis.com`) | Google API data practices |

Optional AI cleanup/post-processing (punctuation restoration, reformatting) is
a **separate opt-in step**, never applied silently. Local transcription
itself involves **zero third parties**.

## Permissions rationale (Android, design)

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Required to capture dictation from the microphone. Without it, there is no product. |
| `FOREGROUND_SERVICE_MICROPHONE` (or equivalent) | Dictation sessions run as a foreground service so recording isn't killed; the persistent notification tells you when the mic is live. |
| `POST_NOTIFICATIONS` | Session status / the foreground-service notification channel. |
| `BIND_ACCESSIBILITY_SERVICE` *(future)* | Text insertion into other apps via Accessibility, matching the Wispr Flow approach — only to deliver the transcript you dictated; no screen-reading beyond insertion mechanics. |

No contacts, location, camera, or storage permissions are part of the design.
Recording is push-to-talk / session-scoped: the mic is not hot when you
aren't dictating, and the app must show a visible indicator while recording.

## Your rights and controls

- Delete transcript history locally at any time (per-session or wipe-all).
- Revoke/replace provider keys in the Keystore; switching back to local-only
  stops all uploads immediately.
- No account means there is nothing to "export" or "close" on a server —
  uninstalling removes all local data.

---

# Android app privacy (implemented)

The shipped app defaults to **On-device (private)** with local-only mode **on**.

## Stays on device

- Microphone audio and both raw and cleaned transcripts.
- `files/history.jsonl`, capped at 300 entries.
- Optional WAVs in `files/audio/<id>.wav` when **Save each recording** is enabled.
- Dictionary text, text settings, and provider choice.
- API keys encrypted with an AES/GCM key in the Android Keystore.

## Can leave the device

Only when you pick a cloud provider **and** turn local-only off:

| Provider | Audio endpoint | Optional cleanup |
|---|---|---|
| Groq | `api.groq.com` transcription | final text only |
| OpenAI | `api.openai.com` transcription | final text only |
| Gemini | `generativelanguage.googleapis.com` | final text only |

Requests include audio, model, language hint, and a short vocabulary prompt from your user dictionary. No silent fallback: local-only is enforced in `TranscriptionRouter` before any network call.

## Controls

- **Clear transcript history** deletes `history.jsonl`, not WAV files.
- Recordings can be deleted by clearing app storage or uninstalling.
- Remove the cloud key at any time; transcripts remain.
- No analytics, ads, crash reporting, or telemetry SDKs. `INTERNET` exists only for opt-in cloud calls.
