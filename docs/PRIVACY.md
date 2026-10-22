# Wahtuh Pho — Privacy

**Date:** 2026-10-22 · **Status:** the first half describes the Rust core
design; the Android section at the end describes the v0.4.0 app.

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

No step in this path can reach the network in the shipped app: the local
engine path never constructs a cloud request, `wahtuh-providers` has no HTTP
transport, and the app's single routing chokepoint refuses a cloud provider
while local-only is on (the default). The research workspace uses
`whisper-rs` for local inference; that is still an on-device engine, not a
network path. The local-only guarantee is structural, not just a flag.

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
| Groq | Audio + transcript request (`api.groq.com`) | Groq privacy policy / API terms |
| OpenAI | Audio + transcript request (`api.openai.com`) | OpenAI API data-usage policy |
| Gemini | Audio (inline data) + transcription config (`generativelanguage.googleapis.com`) | Google API data practices |
| Deepgram | Audio + transcript request (`api.deepgram.com`) | Deepgram privacy policy / API terms |
| AssemblyAI | Audio upload (`api.assemblyai.com/v2/upload`), transcript job/poll (`/v2/transcript`); best-effort transcript delete after reading | AssemblyAI privacy policy / API terms |

Optional AI cleanup/post-processing (punctuation restoration, reformatting) is
a **separate opt-in step**, never applied silently. Local transcription
itself involves **zero third parties**.

## Permissions rationale (Android, design)

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Required to capture dictation from the microphone. Without it, there is no product. |
| `FOREGROUND_SERVICE_MICROPHONE` (or equivalent) | Dictation sessions run as a foreground service so recording isn't killed; the persistent notification tells you when the mic is live. |
| `POST_NOTIFICATIONS` | Session status / the foreground-service notification channel. |
| `BIND_ACCESSIBILITY_SERVICE` | Text insertion into other apps and bubble visibility. The service checks whether the focused field is editable and not a password field; it reads field text at insertion, at the start of Command Mode, or when opt-in Field Context is enabled, and never reads password fields. No screen capture and no background reading. |
| `RECEIVE_BOOT_COMPLETED` | Restores the floating bubble after reboot only if you had deliberately started it. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Opens the system exemption prompt so Android does not kill a long dictation. The app never enables the exemption without your confirmation. |

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

- Microphone audio and both raw and cleaned transcripts. Local ASR is Cactus Whistle, local VAD is Silero v5 through ONNX Runtime, and the English live-preview stream runs locally through sherpa-onnx; no audio leaves the device in on-device mode.
- `files/history.jsonl`, capped at 300 entries and subject to the KEEP / 24 HOURS / NEVER policy.
- No new audio files are written. Any legacy `files/audio/<id>.wav` files are deleted by Clear, by NEVER, and by the 24-hour policy.
- Dictionary text, text settings, and provider choice.
- API keys encrypted with an AES/GCM key in the Android Keystore.

## Can leave the device

Only when you pick a cloud provider **and** turn local-only off:

| Provider | Audio endpoint | Optional cleanup |
|---|---|---|
| Groq | `api.groq.com` transcription | final text only |
| OpenAI | `api.openai.com` transcription | final text only |
| Gemini | `generativelanguage.googleapis.com` | final text only |
| Deepgram | `api.deepgram.com` transcription | final text only, routed to your configured Groq/OpenAI/Gemini key; no cleanup if none is configured |
| AssemblyAI | `api.assemblyai.com` upload + async transcription | final text only, routed to your configured Groq/OpenAI/Gemini key; no cleanup if none is configured |

When **Command Mode** is used, local case/list/delete edits stay on device.
Freeform rewrites send the selected text and your spoken instruction to the
configured Groq, OpenAI, or Gemini key. The selected text is untrusted content
and is not sent anywhere unless you hold the bubble with a selection, Command
Mode is on, and the instruction is not one of the local commands.

If **Field Context** is enabled (off by default), AI polish also receives a
short list of names/code-like terms extracted from the focused field. The
app does not send the surrounding sentences, never reads password fields,
and never reads its own fields.

Requests include audio, model, language hint, and short vocabulary hints from your user dictionary. No silent fallback: local-only is enforced in `TranscriptionRouter` before any network call.

## Controls

- **App-aware tone** reads the foreground package name only (for example, a chat or email app) to pick an email, workplace, chat, or other preset. It never reads the field contents or screen.
- **Sensitive-app stand-down** uses the foreground package name only. Banking, payment, wallet, authenticator, and password-manager packages are refused by default in accessibility insertion and the IME. Password fields are always refused. Turning the package guard off does not relax the password guard.
- **Insertion recovery** checks whether Android still has accessibility insertion enabled. If it is off, the app posts a local FIX notification; no data is collected or sent.
- **Field Context** is a separate, off-by-default switch. When enabled, the app reads the focused field at stop time and sends only extracted names/code terms to AI polish. It is blocked unless AI polish is on, and refuses passwords and Wahtuh Pho's own fields.
- **History retention** is KEEP, 24 HOURS, or NEVER. NEVER deletes `history.jsonl` and legacy audio and stops saving new entries.
- **Clear transcript history** deletes `history.jsonl` and any legacy audio files.
- Recordings are never saved; clearing app storage or uninstalling removes all local data.
- Remove the cloud key at any time; transcripts remain.
- No analytics, ads, crash reporting, or telemetry SDKs. `INTERNET` exists only for opt-in cloud calls.
