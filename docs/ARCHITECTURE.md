# Wahtuh Pho — Architecture

**Date:** 2026-10-19 · **Status:** Phase B scaffold (Rust workspace compiles, `cargo test` green; no FFI backend linked yet)

This document describes the Rust workspace as built. It is the contract that
future work (the `wahtuh-jni` Android bridge, the FFI engine backend) must
honor.

---

## 1. Workspace diagram

```
wahtuh-pho/
├── Cargo.toml                      # workspace, resolver = "2", std-only (no external deps)
├── crates/
│   ├── wahtuh-audio/               # PCM plumbing: capture → chunks
│   │   └── src/lib.rs              # PcmSample=i16, AudioConfig, Chunker (30s+overlap),
│   │                               # RingBuffer, BoundedAudioBuffer (hard memory ceiling)
│   ├── wahtuh-transcribe/          # engine seam
│   │   └── src/lib.rs              # Engine trait, Transcript/Segment, CancellationToken,
│   │                               # MockEngine (deterministic tests), LocalEngine (FFI stub)
│   ├── wahtuh-normalize/           # text cleanup
│   │   └── src/lib.rs              # NFC-lite, punctuation/capitalization, Telugu preservation,
│   │                               # versioned Dictionary
│   ├── wahtuh-providers/           # OPT-IN cloud adapters (Groq/Gemini/OpenAI stubs)
│   │   └── src/lib.rs              # Provider trait; keys runtime-only; no HTTP transport yet
│   ├── wahtuh-config/              # Config + local-only guarantee
│   │   └── src/lib.rs              # Config, EngineSelection, Language, ProviderCredentials
│   └── wahtuh-core/                # orchestration
│       └── src/lib.rs              # DictationSession, FinalResult, TranscriptHistory
└── docs/
    ├── ARCHITECTURE.md             # this file
    └── ENGINE_EVALUATION.md        # whisper.cpp vs sherpa-onnx vs Vosk
```

**Future (not yet built):** `crates/wahtuh-jni/` — the Android JNI bridge
(`cdylib`, `jni` crate). It will sit *beside* these crates and call into
`wahtuh-core`; it must never contain business logic, only type translation.

### Crate responsibilities

| Crate | Owns | Must never |
|---|---|---|
| `wahtuh-audio` | PCM types, chunking, buffering, memory ceiling | know about engines, text, or network |
| `wahtuh-transcribe` | `Engine` trait, transcript types, cancellation | pick a backend; `LocalEngine` fails loudly until one is linked |
| `wahtuh-normalize` | text cleanup, dictionaries | drop or transliterate non-Latin scripts |
| `wahtuh-providers` | cloud adapter types (opt-in) | be reachable when `local_only` is set; hold keys |
| `wahtuh-config` | config load/save/validate, local-only gate | persist secrets |
| `wahtuh-core` | session state machine, history | touch the network; depend on providers |

Dependency direction: `wahtuh-core` → `{audio, transcribe, normalize, config}`.
`wahtuh-providers` is depended on by **nothing** in the workspace today — that
is deliberate (see §7).

---

## 2. Data flow

```
┌──────────┐   i16 PCM     ┌──────────────┐  30s windows   ┌────────────────┐
│ Android  │ ────────────▶ │ BoundedAudio │ ─────────────▶ │    Chunker     │
│ mic      │  AudioRecord  │    Buffer    │  (hard cap,    │ (30s, 5s overlap)│
│ (JNI)    │  via wahtuh-  │ (error, not  │   error on     └───────┬────────┘
│          │  jni, future) │  silent drop)│   overflow)            │
└──────────┘               └──────────────┘                        ▼
                                                              ┌────────────────┐
                                                              │ Engine::        │
                                                              │ transcribe_chunk│ ◀── CancellationToken
                                                              │ (worker thread) │
                                                              └───────┬────────┘
                                                                      │ Transcript (raw)
                                                                      ▼
                                                              ┌────────────────┐
                                                              │  wahtuh-       │
                                                              │  normalize     │──▶ Dictionary
                                                              └───────┬────────┘
                                                                      │ NormalizedText { text, original }
                                                                      ▼
                                              ┌───────────────────────────────────┐
                                              │ FinalResult { raw, normalized }   │
                                              │   → TranscriptHistory (bounded)   │
                                              │   → insertion via wahtuh-jni      │
                                              └───────────────────────────────────┘
```

Notes:

- **Chunking matches the engine.** Whisper-class models natively consume 30 s
  windows; `Chunker::new(cfg, 30, 5)` produces exactly that, with 5 s overlap
  so words on window edges keep full context. The tail (< 1 s) is dropped by
  documented policy; a ≥ 1 s tail is emitted with its overlap region intact.
- **Partials are previews, not results.** `Engine::partial_transcript` feeds
  the UI's live preview; only `transcribe_chunk` output enters the transcript.
- **Raw is always preserved.** `FinalResult` carries both `raw: Transcript`
  and `normalized: NormalizedText` (which itself keeps `original`). The UI
  inserts `normalized.text` and can show "raw transcript" on demand; history
  stores both. Normalization is thereby reversible and auditable.

---

## 3. Threading model

- **Mic/capture thread** (platform side, e.g. Android `AudioRecord` callback):
  pushes `&[i16]` into `BoundedAudioBuffer` / `Chunker`. These types are
  `!Sync` by default — they live on one thread; chunks are *moved* across the
  boundary, never shared.
- **Inference worker thread:** owns the `Engine` (`Engine: Send` is a
  supertrait bound). `DictationSession::feed_chunk` is called here or on a
  session thread; a long `transcribe_chunk` polls the shared
  `CancellationToken` (`Arc<AtomicBool>`, `SeqCst`).
- **UI thread:** owns the `DictationSession` handle conceptually, calls
  `cancel()` (lock-free, via the token), receives partials/final results
  through the JNI callback or channel.
- **Rule: no locks around inference.** The token is the only cross-thread
  primitive on the hot path. History writes happen on session finish, off the
  audio path.

---

## 4. FFI safety requirements (for future `wahtuh-jni`)

The JNI bridge does not exist yet. When it is written, these rules are
non-negotiable:

1. **`catch_unwind` at every boundary.** Every `extern "C"` / JNI entry point
   wraps its body in `std::panic::catch_unwind`. A Rust panic crossing into
   the JVM is undefined behavior; convert panics to a Java exception or an
   error code.
2. **No panics across FFI, ever.** Inside the bridge: no `unwrap()`/`expect()`
   on fallible paths — map to `CoreError`/`EngineError` and return them.
3. **Ownership discipline.** Native handles cross as opaque `jlong`s via
   `Box::into_raw`; each is reclaimed exactly once with `Box::from_raw` in a
   paired `close`/`destroy` function. Document the pairing on both sides.
4. **`Send`/`Sync` constraints.** Anything stored in a `static` or shared
   across JNI calls must be `Send + Sync` or confined to one thread with a
   clear owner. `Engine: Send` already holds; the session object must not be
   `Sync` (keep `!Sync` so misuse fails to compile).
5. **`// SAFETY:` comments.** Every `unsafe` block carries a `// SAFETY:`
   comment stating the invariant the caller upholds (valid pointer, correct
   lifetime, called once, etc.).
6. **String/array conversion.** `jstring` ↔ `String` conversions validate
   UTF-8 (`String::from_utf8`, never `from_utf8_unchecked` on JNI input);
   PCM arrays cross as `jshortArray` with `GetShortArrayRegion` (copying is
   fine at 16 kHz mono — 32 KB/s).
7. **Precedent:** the whisper.cpp project documents community JNI bindings
   (`GiviMAD/whisper-jni`, listed in its README bindings section) — the same
   shape (C API → JNI → app) is proven for this engine family.

---

## 5. Error taxonomy

Errors are typed per layer; `wahtuh-core::CoreError` wraps them so logs stay
attributable. Convention: **fail loudly, never silently.**

| Layer | Type | Key variants |
|---|---|---|
| audio | `AudioError` | `InvalidSampleRate`, `InvalidChannels`, `InvalidWindow`, `BufferLimitExceeded { limit_seconds, attempted_samples }` |
| transcribe | `EngineError` | `NotInitialized`, `ModelNotBundled(msg)`, `Cancelled`, `InvalidAudio`, `InferenceFailed` |
| normalize | `DictionaryError` | `UnsupportedVersion` (unknown versions are rejected, not guessed) |
| providers | `ProviderError` | `MissingApiKey`, `TransportNotConfigured`, `Api`, `UnsupportedModel` |
| config | `ConfigError` | `CredentialsWithLocalOnly`, `CloudEngineWithLocalOnly`, `SecretsNotPersisted`, `MissingEnvCredentials` |
| core | `CoreError` | `Config(_)`, `Engine(_)`, `Cancelled`, `SessionFinished`, `HistoryParse`, `Io` |

Notable policies:

- `BufferLimitExceeded` **errors instead of dropping audio.** Dictation must
  never quietly lose speech; the caller decides recovery (warn user, end
  session, spill to disk).
- `ModelNotBundled` is the `LocalEngine` stub's only behavior today. A
  missing model is an explicit error with an actionable message, never an
  empty transcript.
- `SecretsNotPersisted`: `Config::save` refuses to write when credentials
  are attached — it does not silently strip them.

---

## 6. Memory-limit strategy

Android phones are the primary target; every unbounded growth is a bug.

| Resource | Bound | Enforcement |
|---|---|---|
| Live audio buffer | `max_seconds` (default 300 s ≈ 9.6 MB @16 kHz mono) | `BoundedAudioBuffer::push_samples` → `Err(BufferLimitExceeded)` |
| Chunk windows | 30 s + 5 s overlap, streamed | `Chunker` holds ≤ 1 window + overlap |
| Transcript history | `capacity` entries (default 50) | `TranscriptHistory` evicts oldest; JSONL rewritten on eviction |
| Engine working set | model-dependent | chosen at engine selection; base ≈ 388 MB, tiny ≈ 273 MB (whisper.cpp README figures) |
| Dictionary | user-bounded (hundreds of entries) | `HashMap`; no bound needed at this scale |

Session caps (from the Wispr Flow research: 20 min desktop / ~5 min Android
per session) map directly onto `BoundedAudioBuffer::new(cfg, max_seconds)`.

---

## 7. The local-only guarantee

`local_only = true` (the default) makes provider code paths **unreachable**,
not merely disabled. Three layers:

1. **Type system.** Cloud adapters (`GroqAdapter`, `GeminiAdapter`,
   `OpenAiAdapter`) implement `Provider`, **not** `Engine`. `DictationSession`
   is generic over `E: Engine`, so there is no generic path from a session to
   a provider — the compiler rejects it. Nothing in the workspace depends on
   `wahtuh-providers`.
2. **Config gate.** `Config::validate()` (run by `DictationSession::new`
   before any audio flows) rejects `local_only + credentials` and
   `local_only + EngineSelection::Cloud`. Opting out requires the explicit,
   greppable `Config::allow_cloud()` call — not a boolean flip in a file.
3. **Secret hygiene.** `ProviderCredentials` has no serialization path
   (std-only crate, no derive to forget); `Config::save` refuses configs that
   carry credentials; the on-disk JSON schema has no credential fields (a test
   asserts this); keys enter only at runtime via `credentials_from_env` or
   the OS keychain.

To audit: `grep -rn wahtuh_providers crates/` should show zero non-test
dependents, and `grep -rn allow_cloud` should show exactly the app-layer
call sites.

---

## 8. FFI plug-in point (engine backend)

`wahtuh-transcribe::LocalEngine` is the documented seam. When the backend
decision in `ENGINE_EVALUATION.md` is implemented:

1. Add a `whisper-sys`-style `-sys` crate with `extern "C"` declarations for
   the C API (`whisper_init_from_file`, `whisper_full`, …), `// SAFETY:`
   comments on every call.
2. `LocalEngine::init` loads `model_path`, mapping failures to
   `EngineError::InferenceFailed` (panics contained via `catch_unwind`).
3. `transcribe_chunk` converts `&[i16]` → `&[f32]` in `[-1.0, 1.0]`, polls the
   `CancellationToken` between C calls, maps segments to `Segment`.
4. `Drop` frees the native context exactly once
   (`Box::into_raw`/`Box::from_raw` discipline).

`wahtuh-core` does not change: the backend plugs in behind the `Engine`
trait. The deciding experiment before committing is specified in
`ENGINE_EVALUATION.md` §6.

---

# Android app (v0.1.0, shipped)

The shipped APK is a separate, Gradle-free Android implementation. It does not link the Rust crates.

## Runtime flow

```
mic / WAV
  └─ AudioCapture (16 kHz mono float)
      └─ SpeechAudio.trim + utterances (silence split)
          └─ DictationController
              ├─ AsrManager (local)
              │   ├─ WhisperEngine (sherpa-onnx OfflineRecognizer)
              │   ├─ VoskEngine (Vosk Recognizer)
              │   └─ HybridEngine (Whisper language detect → Vosk for Telugu)
              ├─ TranscriptionRouter (local-only chokepoint)
              │   └─ CloudTranscriber (Groq / OpenAI / Gemini)
              ├─ TextPipeline (punctuation, fillers, dictionary)
              ├─ HistoryStore (JSONL, raw + final + edits)
              └─ DictationResult → listener (MainActivity, IME, bubble)
```

## Components

| Path | Responsibility |
|---|---|
| `app/src/com/wahtuh/pho/asr/` | Model install, Whisper, Vosk, hybrid routing, `AsrManager` |
| `app/src/com/wahtuh/pho/audio/` | `AudioCapture` (AudioRecord) and `Wav` read/write |
| `app/src/com/wahtuh/pho/speech/` | VAD/trim/utterance splitting over RMS frames |
| `app/src/com/wahtuh/pho/dictation/` | Session state machine and listener contract |
| `app/src/com/wahtuh/pho/cloud/` | Multipart/JSON clients and privacy router |
| `app/src/com/wahtuh/pho/text/` | Deterministic pipeline, dictionary, edits, WER/CER |
| `app/src/com/wahtuh/pho/data/` | Preferences, Keystore keys, JSONL history |
| `app/src/com/wahtuh/pho/service/` | IME keyboard, bubble FGS, accessibility insertion |
| `app/src/com/wahtuh/pho/diag/` | Self-tests, benchmarks, mic smoke test |

## Android state machine

`IDLE → PREPARING → RECORDING → TRANSCRIBING → IDLE`, with `ERROR` on failure. A monotonic session token rejects late results. The controller is a process singleton shared by the activity, IME, and bubble.

## Android build

`scripts/build.sh` extracts AAR classes/JNI, runs `aapt2` with bundled assets, compiles with `javac`, jars app classes, converts with D8 (`--release --min-api 26 --lib android.jar`), adds `classes.dex` + `lib/arm64-v8a`, zipaligns, signs, and verifies. Legacy `dx` is not used because it lacks lambda desugaring.

## Android storage

- `files/models/` — unpacked models with a version marker
- `files/history.jsonl` — append-only JSONL, max 300 entries
- `files/audio/` — optional per-dictation WAVs
- `shared_prefs/` — settings; API key ciphertext only, with the key in Keystore
