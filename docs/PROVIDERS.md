# Cloud Providers (opt-in)

Wahtuh Pho is **local-first**: on-device transcription is the default and the
recommended path. Cloud providers exist as an explicit, opt-in fallback for
situations where the user wants a different accuracy/cost tradeoff — for
example, noisy audio or a language the local engine handles poorly.

Using a provider means **your audio is uploaded to that provider's servers**
(see [Upload disclosure](#upload-disclosure)). Nothing is uploaded unless
you explicitly opt out of local-only mode, and the app never falls back to a
cloud provider silently.

Status of the Android app: adapters live in
`app/src/com/wahtuh/pho/cloud/CloudTranscriber.java` and use
`HttpURLConnection`. The separate `crates/wahtuh-providers` research workspace
still has no HTTP transport and is not wired into the APK. In the app, every
provider is opt-in; local Whistle transcription stays the default and cloud is
never used as a fallback.

## Model verification log

Every model in `Provider::supported_models` was checked against current
official provider documentation on the date shown. A name that could not be
verified stays **out** of the allow-list — we never invent model names.

| Provider | Verified item | Source | Checked (UTC) | Status |
|---|---|---|---|---|
| Groq | Models `whisper-large-v3-turbo`, `whisper-large-v3`; endpoint `POST https://api.groq.com/openai/v1/audio/transcriptions`; per-hour pricing | Groq Speech-to-Text docs: http://console.groq.com/docs/speech-to-text | 2026-10-19 | ✅ verified |
| OpenAI | Models `gpt-transcribe`, `gpt-4o-transcribe`, `gpt-4o-mini-transcribe`, `gpt-4o-mini-transcribe-2025-12-15`, `whisper-1`, `gpt-4o-transcribe-diarize`; endpoint `POST https://api.openai.com/v1/audio/transcriptions` (multipart, `response_format=json`) | OpenAI API reference (Create transcription): https://developers.openai.com/api/reference/ruby/resources/audio/subresources/transcriptions/methods/create and transcription guide: https://developers.openai.com/api/docs/guides/transcription | 2026-10-19 | ✅ verified |
| Gemini | `gemini-3.8-flash` supports audio input + `generateContent` | Google Cloud model docs: https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/gemini/3-8-flash (Modalities: Audio input; APIs: GenerateContent supported) | 2026-10-19 | ✅ verified |
| Gemini | `gemini-2.5-flash` used for audio transcription via inline data / Files API | Published walkthrough using the official `google-genai` SDK: https://dzone.com/articles/mastering-audio-transcription-with-gemini-apis | 2026-10-19 | ⚠️ verified usage, but **legacy** — see caveat below |
| OpenAI | Per-minute pricing | Not found on official docs during this check | 2026-10-19 | ❌ unverified — check OpenAI's pricing page |
| Deepgram | Model `nova-3`; endpoint `POST https://api.deepgram.com/v1/listen` (raw WAV body, `Content-Type: audio/wav`, `Authorization: Token <key>`); languages `te`, `en`, `multi` (multilingual/code-switching); `smart_format` option | Deepgram pre-recorded docs: https://developers.deepgram.com/docs/pre-recorded-audio ; Telugu: https://deepgram.com/product/speech-to-text/telugu ; pricing via third-party summaries of the official pricing page | 2026-10-19 | ✅ verified (endpoint shape from docs; **live call blocked — stored API key rejected with 401, reconnect needed**) |
| Deepgram | Per-minute pricing: $0.0043/min mono, $0.0052/min multi (pre-recorded Nova-3) | Third-party summaries of Deepgram's pricing page (brasstranscripts.com, anygen.io) | 2026-10-19 | ⚠️ from secondary sources — recheck Deepgram's pricing page before budgeting |
| Gemini | Per-token/per-minute pricing | Not found on official docs during this check | 2026-10-19 | ❌ unverified — check Google's pricing page |
| AssemblyAI | Endpoints `POST /v2/upload` (raw WAV), `POST /v2/transcript`, `GET`/`DELETE /v2/transcript/{id}`; auth header `authorization: <key>`; models `universal-3-5-pro` + `universal-2`; `language_detection` for auto, `language_code` for explicit languages; `keyterms_prompt` for vocabulary | AssemblyAI model selection: https://www.assemblyai.com/docs/pre-recorded-audio/select-the-speech-model ; supported languages: https://www.assemblyai.com/docs/pre-recorded-audio/supported-languages | 2026-10-22 | ✅ verified from official docs; **live call not run — no key available** |
| AssemblyAI | Supported coverage: Universal-3.5 Pro covers 18 languages including `en`, `de`, `fr`, `es`, `it`, `nl` but not Polish; Universal-2 covers 99 including Polish. The app sends Universal-2 alone for explicit Polish. | AssemblyAI supported-languages doc (above) | 2026-10-22 | ✅ verified |
| AssemblyAI | Pricing | Not checked against the official pricing page | 2026-10-22 | ❌ unverified — check AssemblyAI pricing before budgeting |

**Gemini 2.5 caveat (2026-10):** multiple reports indicate Google is
retiring 2.5-series access for *newly created* API keys (`gemini-2.5-flash`
returns 404 "no longer available to new users"; the API's own error messages
suggest newer replacements). New users should therefore prefer
`gemini-3.8-flash`. `gemini-2.5-flash` remains in the allow-list for existing
keys that still have access, but expect it to stop working for new keys.

## Groq

- **Where to get a key:** the Groq console (console.groq.com) → *API Keys*
  section. Keys are free-tier friendly; no credit card is required for the
  free tier (per Groq's docs).
- **Endpoint:** `POST https://api.groq.com/openai/v1/audio/transcriptions`
  (OpenAI-compatible), `Authorization: Bearer <key>`, `multipart/form-data`
  with fields `file` (WAV audio), `model`, `response_format=json`, optional
  `language` (ISO-639-1). A translations endpoint
  (`/audio/translations`) also exists but is not used by this adapter.
- **Supported models** (verified 2026-10-19):
  | Model | Notes | Documented price |
  |---|---|---|
  | `whisper-large-v3` | Highest accuracy, multilingual; supports transcription + translation | $0.111/hour |
  | `whisper-large-v3-turbo` | Fast, multilingual; transcription only | $0.04/hour |

  Prices change; confirm on Groq's pricing page before relying on them.
- **Response:** JSON `{"text": "..."}` (we request `response_format=json`).

## OpenAI

- **Where to get a key:** the OpenAI developer dashboard → *API keys*.
  Requires billing setup.
- **Endpoint:** `POST https://api.openai.com/v1/audio/transcriptions`,
  `Authorization: Bearer <key>`, `multipart/form-data` with fields `file`
  (flac/mp3/mp4/mpeg/mpga/m4a/ogg/wav/webm), `model`,
  `response_format=json`, optional `language`.
- **Supported models** (verified 2026-10-19 against the API reference):
  `gpt-transcribe`, `gpt-4o-transcribe`, `gpt-4o-mini-transcribe`,
  `gpt-4o-mini-transcribe-2025-12-15`, `whisper-1`,
  `gpt-4o-transcribe-diarize`.
  (`whisper-1` is the one that supports word/segment timestamps and the
  translations endpoint; `gpt-4o-transcribe-diarize` adds speaker labels.)
- **Pricing:** per-minute billing; the exact figures were not verified
  against official docs in this check — **see OpenAI's pricing page**
  before estimating cost.

## Gemini

- **Where to get a key:** Google AI Studio (free API key for the Gemini
  Developer API).
- **How transcription works:** Gemini has **no dedicated speech-to-text
  endpoint**. Transcription is done via `generateContent` with the audio
  supplied as input plus a text prompt asking for a verbatim transcript.
  Audio can be sent as inline data (total request ≤ 20 MB) or via the Files
  API for longer recordings. This adapter builds the **inline** form —
  dictation snippets are seconds long, well under the limit — and documents
  the Files API route for anything longer.
- **Endpoint:** `POST
  https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`,
  header `x-goog-api-key: <key>`, JSON body
  `{"contents":[{"parts":[{"text":"<prompt>"},{"inlineData":{"mimeType":"audio/wav","data":"<base64>"}}]}]}`.
- **Supported models** (verified 2026-10-19): `gemini-3.8-flash`,
  `gemini-2.5-flash` (legacy — see the caveat in the verification log).
- **Pricing:** not verified in this check — **see Google's pricing page**.
- **Response:** transcript at
  `candidates[0].content.parts[0].text`.

## Deepgram

- **Where to get a key:** the Deepgram console → *API Keys*. The account
  used for this project carries $200 in credits (user-funded, 2026-10-19).
- **Endpoint:** `POST https://api.deepgram.com/v1/listen`, raw WAV bytes as
  the request body, `Content-Type: audio/wav`, header
  `Authorization: Token <key>` — note this is the **`Token` scheme, not
  `Bearer`**. Query parameters: `model=nova-3`, `language=te|en|multi`,
  `smart_format=true`.
- **Supported models** (verified 2026-10-19): `nova-3`.
- **Supported languages** (verified 2026-10-19): `te` (Telugu), `en`
  (English), `multi` (Deepgram's multilingual mode, which handles
  code-switching). The adapter validates the language against this
  allow-list and rejects anything else with `InvalidLanguage` — this also
  prevents query-string injection, since the language goes in the URL.
- **Response:** transcript at
  `results.channels[0].alternatives[0].transcript`.
- **Pricing** (secondary sources, recheck before budgeting): $0.0043/min
  monolingual, $0.0052/min multilingual (pre-recorded Nova-3). At those
  rates $200 buys roughly 775 hours of monolingual transcription; the
  project's 31-clip benchmark corpus is a few minutes of audio — a few
  cents per full run.
- **Business role:** Deepgram is the *premium cloud tier*. The local
  whisper path stays the default (near-zero marginal cost, offline);
  Deepgram is opt-in for users who want maximum accuracy — particularly
  for Telugu, where local tiny/base/small models measured 117–128% WER.
- **Status 2026-10-19:** adapter implemented and unit-tested
  (`DeepgramAdapter`, 20 provider tests green). **Live benchmark blocked:**
  the stored API key was rejected by Deepgram with 401 `INVALID_AUTH` on
  the first authenticated call; a reconnect with a fresh key is needed
  before any live transcription runs. Connector doc:
  `~/workspace/custom-connectors/custom.deepgram/CONNECTOR.md`.

## AssemblyAI

- **Where to get a key:** the AssemblyAI dashboard → *Workspace → Manage →
  API Keys*. The app links to <https://www.assemblyai.com/dashboard>.
- **Endpoint flow (verified 2026-10-22):** AssemblyAI's pre-recorded API is
  asynchronous.
  1. `POST https://api.assemblyai.com/v2/upload` with the raw WAV bytes,
     `Content-Type: application/octet-stream`, header
     `authorization: <key>` — note **no `Bearer` prefix**. The response
     contains `upload_url`.
  2. `POST https://api.assemblyai.com/v2/transcript` with JSON
     `{"audio_url": "<upload_url>", "speech_models": ["universal-3-5-pro",
     "universal-2"], "punctuate": true, "format_text": true, ...}`. Add
     either `"language_detection": true` or `"language_code": "<code>"`,
     plus optional `keyterms_prompt` (an array of terms).
  3. Poll `GET /v2/transcript/{id}` until `status` is `completed` (read
     `text`) or `error` (read `error`). The app polls every second with a
     120-second deadline.
  4. Best effort: `DELETE /v2/transcript/{id}` after reading the text, so the
     transcript is not left in the account longer than needed. Uploaded audio
     retention is governed by AssemblyAI's terms; the delete call is not a
     guarantee that the uploaded object is removed immediately.
- **Supported models:** `universal-3-5-pro` (highest accuracy, 18 languages)
  and `universal-2` (99 languages). The app sends both, in that order, so a
  language outside the Pro set can fall back to Universal-2.
- **Languages:** Universal-3.5 Pro covers `en`, `de`, `fr`, `es`, `it`, `nl`,
  among others, but not Polish. Universal-2 covers Polish. Auto mode uses
  `language_detection`; English is sent as `en_us`. Explicit Polish sends
  only `universal-2`, so no unsupported-model request is made.
- **Vocabulary:** the user dictionary is sent as `keyterms_prompt` terms,
  which Universal-3.5 Pro and Universal-2 both support. Contextual `prompt`
  is not used.
- **Cleanup:** AssemblyAI is transcription-only here. If AI polish is on, the
  app looks for a configured Groq, OpenAI, or Gemini key and sends only the
  final text there, exactly as it does for Deepgram.
- **Status 2026-10-22:** adapter implemented and compiled; **no live call
  has been made** because no AssemblyAI key was available. Treat live
  behavior and latency as unverified until a key is connected.
- **Pricing:** not verified — check AssemblyAI's pricing page.

## Upload disclosure

When you use a cloud provider, **your audio is uploaded to that provider**
over HTTPS, transcribed on their servers, and the transcript is returned.
There is no way to use a provider without uploading — that is what the
service is.

What we guarantee instead:

1. **Local-only is the default.** `Config::default()` sets
   `local_only = true`, and the local engine handles everything on-device.
2. **No silent fallback.** `Config::validate()` rejects
   `local_only + provider credentials` and `local_only + Cloud engine`
   before any audio flows. Cloud adapters don't implement the local
   `Engine` trait, so `wahtuh-core` *cannot* route audio to them — there is
   no code path, not just a flag check.
3. **Opting out is explicit.** The app layer must call
   `Config::allow_cloud()` (a greppable, deliberate call — not a struct
   field flip) *and* supply credentials before any provider code can run.
4. **Uploads are visible.** Provider use is a per-session, user-visible
   choice in settings; the app must indicate when audio will leave the
   device.

## Key storage design (Android Keystore)

API keys are the user's own credentials. Design for the Android app layer:

- Keys are created by the user in the provider's console and pasted/entered
  once in the app's settings.
- The app stores them in the **Android Keystore** (hardware-backed
  StrongBox where the device offers it), never in `SharedPreferences`,
  never in the config file, never in logs. `Config::save` *refuses* to
  persist a config that carries credentials (`SecretsNotPersisted`) — the
  on-disk schema has no key fields at all.
- At runtime the app reads the key from the Keystore into memory and passes
  it as `&str` to `Provider::build_request` / `TranscriptionRequest::new`.
  This crate never stores the key in a struct beyond that call and redacts
  key-bearing headers in `HttpRequest`'s `Debug` impl.
- Convention for env-var-based setups (desktop/CI): `WAHTUH_<PROVIDER>_API_KEY`
  (e.g. `WAHTUH_GROQ_API_KEY`), read via `Config::credentials_from_env`.

## Android app behavior (2026-10-22)

The shipped Android app implements the providers directly in
`app/src/com/wahtuh/pho/cloud/CloudTranscriber.java`; the Rust adapters
remain a separate research crate.

- **Gemini transcription** uses `audioTranscriptionConfig` on
  `gemini-3.5-transcribe`. The response parser accepts the transcript from
  either `parts[].text` or `parts[].audioTranscription.text`; some
  responses contain only the latter.
- **OpenAI transcription** defaults to `gpt-transcribe` with
  `languages[]` and `keywords[]`. On a 400/404/415/422 response it retries
  once with `gpt-4o-transcribe` and the legacy `language`/`prompt` fields.
- **Deepgram transcription** uses Nova-3 with raw WAV upload.
- **AssemblyAI transcription** uploads the WAV, submits a Universal-3.5
  Pro/Universal-2 job, polls for completion, and best-effort deletes the
  transcript after reading it.
- **Cleanup is vendor-independent.** Transcription and polish may use
  different providers. If Deepgram or AssemblyAI is the transcription
  provider, the app looks for a configured Groq, OpenAI, or Gemini key and
  sends only the final text there. If no such key exists, deterministic
  local cleanup still runs and the AI polish toggle stays off with an
  explanation.

## AI cleanup / post-processing is separate and optional

Transcription returns a **verbatim transcript**. Any AI-powered cleanup —
punctuation restoration, filler-word removal, formatting, summarization —
is a **separate, opt-in step** that the user enables explicitly. It sends
only final text, never audio, and may use a different provider key from the
one used for transcription. The raw transcript and cleaned result are both
kept in history, so the change is inspectable. (This separation also keeps
the privacy story clean: cleanup is a second, distinct data use.) The
off-by-default **Field Context** switch adds a short list of extracted
names/code terms to that text request; it never sends surrounding prose.

## Error taxonomy

`ProviderError` (in `crates/wahtuh-providers`):

| Variant | Meaning |
|---|---|
| `MissingKey` | No API key supplied at runtime |
| `InvalidModel(String)` | Model not in the verified allow-list |
| `InvalidLanguage(String)` | Language code not accepted by this adapter (Deepgram only) |
| `TransportNotConfigured` | No HTTP transport linked in the research crate |
| `HttpError(u16)` | Provider returned an HTTP error status |
| `RateLimited` | HTTP 429 — back off and retry |
| `Cancelled` | User cancelled (app-layer signal) |
| `ProviderMessage(String)` | Provider-supplied error detail / malformed response |

The future transport should map responses with
`ProviderError::from_http_status(status, body)` so all adapters report
failures identically.

## For developers: adding or updating a provider

1. Verify the model names and endpoint against the provider's **current
   official docs**. Record the URL + check date in the verification log
   above.
2. Add the names to that adapter's `supported_models()` — nothing
   unverified goes in.
3. Add/extend the typed request struct (`GroqTranscribeRequest`,
   `GeminiTranscribeRequest`, `OpenAITranscribeRequest`,
   `DeepgramTranscribeRequest`) and its
   `build_http`, keeping keys as `&str` parameters and out of `Debug`
   output.
4. Extend the unit tests: allow-list equality, request shape, and the
   `no_hardcoded_secrets` scan must keep passing.
