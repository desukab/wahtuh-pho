# Wahtuh Pho

**Wahtuh Pho — offline-first, Rust-powered open-source voice dictation; English / Telugu / code-switch.**

An open-source alternative to Wispr Flow. The core differentiator: **transcription happens on-device by default** — audio never leaves your phone unless you explicitly opt into a cloud provider.

> **Status: early scaffold (2026-10-19).** The Rust core compiles and its 71
> tests pass, but there is no working app yet: the local inference engine is a
> stub (no model weights, no native backend linked), there is no Android app
> shell, and no evaluation benchmarks have been run. See
> [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) for the precise state of
> every workstream. Nothing in this README claims more than that.

## Features — implemented vs planned

| Feature | Status |
|---|---|
| Rust core: PCM audio types, chunker (30 s windows + 5 s overlap), bounded memory buffer | ✅ implemented (`wahtuh-audio`) |
| `Engine` trait + deterministic `MockEngine` for testing; cancellation | ✅ implemented (`wahtuh-transcribe`) |
| Text cleanup: NFC-lite, punctuation/capitalization, Telugu-script preservation, versioned personal dictionary | ✅ implemented (`wahtuh-normalize`) |
| Opt-in cloud adapters (Groq / Gemini / OpenAI): typed request builders, model allow-lists verified against official docs 2026-10-19; **no HTTP transport yet — they cannot send anything** | ✅ implemented (`wahtuh-providers`) |
| Config + local-only guarantee (default `local_only = true`; validation rejects cloud settings in local mode; credentials can never be persisted) | ✅ implemented (`wahtuh-config`) |
| Dictation session state machine, bounded transcript history (local JSONL) | ✅ implemented (`wahtuh-core`) |
| On-device inference (`LocalEngine` backend) | ⏳ planned — stub returns `ModelNotBundled` |
| Android JNI bridge + app shell (floating bubble, mic capture, text insertion) | ⏳ planned — design only, see [docs/ANDROID.md](docs/ANDROID.md) |
| English / Telugu / Telugu–English code-switch transcription | ⏳ planned — pipeline support exists (normalizer preserves Telugu script); model selection undecided, see [docs/ENGINE_EVALUATION.md](docs/ENGINE_EVALUATION.md) |
| Hands-free mode, voice editing commands, tone styles | ⏳ planned — see [docs/FEATURE_PARITY.md](docs/FEATURE_PARITY.md) |
| iOS / desktop apps | ❌ not planned — Android first |

## Architecture

Six std-only crates (no external dependencies) arranged so audio, inference,
text, config, and cloud concerns can never leak into each other:

```
mic → wahtuh-audio (PCM chunks, bounded buffer) → wahtuh-transcribe (Engine trait)
    → wahtuh-normalize (cleanup, Telugu preserved) → wahtuh-core (session, history)
              ↑ local-only default enforced by wahtuh-config
              ╳ wahtuh-providers (opt-in cloud) is depended on by nothing — no silent fallback
```

Full contract (threading model, data flow, dependency rules): [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Quickstart

```bash
# 1. Install Rust (stable)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# 2. Build
cargo build --workspace

# 3. Test
cargo test --workspace
```

Current baseline: **71 tests passed, 0 failed** (`cargo test --workspace`).

## Repo layout

```
wahtuh-pho/
├── Cargo.toml              # workspace (resolver 2, std-only, no deps)
├── crates/
│   ├── wahtuh-audio/       # PCM types, chunker, ring/bounded buffers
│   ├── wahtuh-transcribe/  # Engine trait, transcript types, cancellation, MockEngine, LocalEngine stub
│   ├── wahtuh-normalize/   # text cleanup, Telugu preservation, versioned dictionary
│   ├── wahtuh-providers/   # opt-in Groq/Gemini/OpenAI adapters (no transport yet)
│   ├── wahtuh-config/      # config load/save/validate, local-only gate
│   └── wahtuh-core/        # DictationSession, TranscriptHistory (+ end-to-end tests)
├── docs/
│   ├── PROJECT_STATUS.md   # honest per-workstream status + milestones
│   ├── ARCHITECTURE.md     # crate contracts, data flow, threading
│   ├── FEATURE_PARITY.md   # Wispr Flow parity matrix (clean-room research)
│   ├── WISPR_FLOW_RESEARCH.md
│   ├── ENGINE_EVALUATION.md
│   ├── ANDROID.md          # thin-Kotlin-shell + JNI design
│   ├── PROVIDERS.md        # cloud opt-in design, upload disclosure, key storage
│   ├── EVALUATION.md       # benchmark plan (not run yet)
│   ├── PRIVACY.md          # data flows, collection, permissions
│   ├── ENVIRONMENT.md
│   └── REA_NOTES.md
├── .github/workflows/ci.yml
└── LICENSE
```

## Docs index

| Doc | What it is |
|---|---|
| [PROJECT_STATUS.md](docs/PROJECT_STATUS.md) | Milestones, per-workstream status, blockers, next steps — the source of truth |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Workspace contract: crates, data flow, threading, dependency rules |
| [FEATURE_PARITY.md](docs/FEATURE_PARITY.md) | Feature-by-feature comparison with Wispr Flow (public sources only) |
| [WISPR_FLOW_RESEARCH.md](docs/WISPR_FLOW_RESEARCH.md) | Clean-room research notes on Wispr Flow's public behavior |
| [ENGINE_EVALUATION.md](docs/ENGINE_EVALUATION.md) | whisper.cpp vs sherpa-onnx vs Vosk paper comparison; no benchmarks run |
| [ANDROID.md](docs/ANDROID.md) | Android design: thin Kotlin shell + JNI bridge |
| [PROVIDERS.md](docs/PROVIDERS.md) | Opt-in cloud providers: verified models, upload disclosure, key storage design |
| [EVALUATION.md](docs/EVALUATION.md) | Benchmark plan (corpus, scorer) — not executed |
| [PRIVACY.md](docs/PRIVACY.md) | Data-flow diagram in words, collection, permissions, key storage |
| [ENVIRONMENT.md](docs/ENVIRONMENT.md) | Toolchain/environment recon outputs |
| [REA_NOTES.md](docs/REA_NOTES.md) | Notes on the REA 6.7.0 reference app (shallow-cloned, reference only) |

## Privacy

Local-first by design. Microphone audio is chunked and transcribed **on-device**;
in the default local-only mode the codebase contains no HTTP transport, so no
network call can occur. Transcripts are stored locally as JSONL history that
you can delete; there are no accounts, no telemetry, no analytics. Cloud
providers exist only as an explicit opt-in (you supply the key; audio is then
uploaded to that provider per its terms). Full details:
[docs/PRIVACY.md](docs/PRIVACY.md).

## Providers & keys

- Cloud use is **opt-in, never default**: `Config::default()` sets
  `local_only = true`, and validation rejects any cloud configuration while
  that flag is set.
- Supported adapters (model names verified against official provider docs on
  2026-10-19; the adapters build requests but **cannot send them** — no HTTP
  transport is linked): Groq (`whisper-large-v3-turbo`, `whisper-large-v3`),
  OpenAI (`gpt-transcribe`, `gpt-4o-transcribe`, …), Gemini
  (`gemini-3.8-flash`).
- API keys are the user's own credentials: stored in the Android Keystore
  (design), never persisted by `wahtuh-config` (it refuses to save configs
  carrying secrets), never committed. See
  [docs/PROVIDERS.md](docs/PROVIDERS.md#key-storage-design-android-keystore).

## Contributing

This project is pre-release: the core is a scaffold, the app doesn't exist
yet. Contributions welcome — start with
[docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) "Next steps" and
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) (the crate contracts are
binding: audio must never know about engines, core must never touch the
network). All contributions are accepted under the Apache-2.0 license.

## License

Apache License 2.0 — see [LICENSE](LICENSE). **Why Apache-2.0:** it is a
permissive license that allows commercial use and modification while keeping
the ecosystem open for contributions; its explicit patent grant protects
contributors and users; and it is compatible with the project's MIT-licensed
dependencies and planned integrations (whisper.cpp is MIT, whisper-rs is
MIT/Apache-2.0). That combination makes it the right fit for an open-source
Wispr Flow alternative that should stay open whether built on by hobbyists or
companies.
