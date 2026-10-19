# Wahtuh Pho — Project Status

**Mission:** open-source, Rust-powered, offline-first voice dictation
(Wispr Flow alternative). English, Telugu, and Telugu–English code-switching.
**Target repo:** https://github.com/desukab/wahtuh-pho (published 2026-10-19) · **Last updated:** 2026-10-19 (IST, real engine + measured benchmarks)

> Honesty rule: only measured numbers appear here. Anything not yet run is
> marked **not run** — no accuracy, latency, or benchmark figures exist.

## Completed milestones

| Date | Milestone | Evidence |
|---|---|---|
| 2026-10-19 | Environment recon | `docs/ENVIRONMENT.md` — actual observed outputs |
| 2026-10-19 | REA inspection | `docs/REA_NOTES.md` — REA 6.7.0 shallow-cloned to `/tmp/rea` (reference only) |
| 2026-10-19 | Wispr Flow clean-room research | `docs/WISPR_FLOW_RESEARCH.md` — public sources only, paraphrased; no APK downloaded, nothing proprietary copied |
| 2026-10-19 | Feature parity matrix | `docs/FEATURE_PARITY.md` — all entries `planned` |
| 2026-10-19 | Android design (thin Kotlin shell + JNI) | `docs/ANDROID.md` — design documentation only, no compiling code |
| 2026-10-19 | Engine evaluation (whisper.cpp vs sherpa-onnx vs Vosk) | `docs/ENGINE_EVALUATION.md` — paper comparison, decision pending on-device benchmark; **no benchmarks run** |
| 2026-10-19 | Rust workspace scaffold | `Cargo.toml` + 6 crates (`wahtuh-audio`, `wahtuh-transcribe`, `wahtuh-normalize`, `wahtuh-providers`, `wahtuh-config`, `wahtuh-core`); `docs/ARCHITECTURE.md`; baseline `cargo test --workspace` = **49 tests, all green** |
| 2026-10-19 | Phase C1: MVP hardening | `crates/wahtuh-core/tests/end_to_end.rs` (6 integration tests: full pipeline → persisted history, mid-session cancellation, buffer-limit error path, `local_only` blocking cloud selection, Telugu/code-switch through normalization); strengthened unit tests (chunker exact-boundary overlap, dictionary migration gate, history eviction + disk rewrite, config save refusing credentials); `docs/EVALUATION.md` (plan) |
| 2026-10-19 | Phase F: publish prep | `LICENSE` (Apache-2.0), `README.md` (honest implemented-vs-planned table), `docs/PRIVACY.md`, `.github/workflows/ci.yml`, `.gitignore`; local git repo initialized (`git init -b main`, 5 meaningful commits, builder identity); `cargo test --workspace` = **71 passed, 0 failed, 1 ignored (doc-test)**. See [License rationale](#license-rationale) and [Publish blockers](#publish-blockers) |
| 2026-10-19 | Published to GitHub | `desukab/wahtuh-pho` created by the user; 29/30 files pushed via the GitHub API (`push_files`, 4 commits — raw SSH is blocked by the VM proxy). `.github/workflows/ci.yml` NOT published: GitHub App lacks the `workflows` permission (403) — must be added manually via web UI |
| 2026-10-19 | Real local transcription (no stubs) | `WhisperCppEngine` in `wahtuh-transcribe` via **whisper-rs 0.16.0** (bundled whisper.cpp v1.8.3); model path from `$WAHTUH_MODEL_PATH` or `~/.cache/wahtuh/models/`; mid-inference cancellation via abort callback; `cargo test --workspace` = **74 passed, 0 failed, 1 ignored** |
| 2026-10-19 | Model weights downloaded + verified | Multilingual `ggml-tiny.bin` (74 MB), `ggml-base.bin` (141 MB), `ggml-small.bin` (465 MB) from Hugging Face `ggerganov/whisper.cpp`; SHA256 recorded; ggml magic verified. Stored at `~/workspace/models/` (never in git) |
| 2026-10-19 | Evaluation corpus (licensed audio) | 31 clips, all 16 kHz mono: `jfk.wav` + 15 EN + 15 TE from Google FLEURS (**CC-BY-4.0**, cite arXiv:2206.12139). Common Voice was unreachable (Mozilla removed the files from Hugging Face in Oct 2025; Data Collective needs interactive sign-in). Manifest: `docs/eval_manifest.json` |
| 2026-10-19 | Measured benchmarks (93 runs) | 3 models × 31 clips on whisper.cpp 1.8.3, 2-vCPU x86_64, no GPU. **EN mean WER: tiny 13.9% / base 11.8% / small 7.5%.** TE: all three models fail (117–128% mean WER). Scorer: jiwer 4.0.0. Full table in `docs/EVALUATION.md`; raw runs in `docs/bench_runs_{tiny,base,small}.jsonl` |

## License rationale

**Apache-2.0.** It is a permissive license that allows commercial use and
modification while keeping the ecosystem open for contributions; its explicit
patent grant protects both contributors and users against patent litigation;
and it is compatible with the project's MIT-licensed dependencies and planned
integrations (whisper.cpp is MIT, whisper-rs is MIT/Apache-2.0). That
combination makes it the right fit for an open-source Wispr Flow alternative
that should stay open whether it is built on by hobbyists or companies.

## Publish blockers

1. **GitHub repo creation failed, then resolved manually (2026-10-19):** `create_repository` for
   `desukab/wahtuh-pho` returned `403 Resource not accessible by integration`
   from `POST https://api.github.com/user/repos`. The user created the repo
   manually and added the VM's SSH key; raw SSH is blocked by the VM proxy,
   so the 29 publishable files were pushed via the GitHub API (`push_files`,
   4 commits, each user-approved). Verified with `list_commits`.
   → **Still needs user:** `.github/workflows/ci.yml` could NOT be pushed —
   the GitHub App lacks the `workflows` permission (403 on any tree
   containing it). Add it manually: repo → Add file → Upload files →
   `.github/workflows/ci.yml` (content is in the local repo).
2. Toolchain note: actual measured toolchain is `rustc 1.99.1` / `cargo
   1.99.1` (via rustup). `whisper-rs-sys` needs `libclang` for bindgen
   (installed via `apt-get install libclang-dev` on this VM); fresh
   environments need it before building.

## Current status per workstream

| Workstream | Status |
|---|---|
| `wahtuh-audio` (PCM, chunker, bounded buffer) | Scaffolded + tested (12 unit tests) |
| `wahtuh-transcribe` (`Engine` trait, `MockEngine`, **`WhisperCppEngine`**) | Real local engine via whisper-rs 0.16.0 (whisper.cpp v1.8.3 bundled); `$WAHTUH_MODEL_PATH` / `~/.cache/wahtuh/models/`; abort-callback cancellation; zero network in local path. 10 unit tests (3 whisper tests skip cleanly without a model). `LocalEngine` stub retained for its loud-failure contract |
| `wahtuh-normalize` (cleanup, Telugu preservation, dictionary) | Scaffolded + tested (11 unit tests) |
| `wahtuh-providers` (Groq/Gemini/OpenAI adapters) | Scaffolded + tested (16 unit tests). No HTTP transport (std-only); unreachable when `local_only` |
| `wahtuh-config` (config + local-only guarantee) | Scaffolded + tested (9 unit tests) |
| `wahtuh-core` (session, history) | Scaffolded + tested (10 unit + 6 integration tests) |
| `wahtuh-jni` Android bridge | Not started (design in `docs/ANDROID.md` only) |
| Android app shell | Not started |
| Evaluation / benchmarks | **Measured 2026-10-19:** 93 runs (3 models × 31 clips). EN WER tiny 13.9% / base 11.8% / small 7.5%; TE 117–128% WER (all models fail Telugu — see Blockers). `docs/EVALUATION.md` + raw `docs/bench_runs_{tiny,base,small}.jsonl` |
| GitHub repo `desukab/wahtuh-pho` | **Published** 2026-10-19 (4 API commits). `.github/workflows/ci.yml` still missing — needs manual upload (App lacks `workflows` permission) |
| Competitor teardown (user's licensed APK, static only) | **Done 2026-10-19:** `docs/ANDROID_UX_TEARDOWN.md` (screen inventory, onboarding state machine, bubble states, permission patterns), `docs/COMPETITOR_ARCHITECTURE.md` (gRPC streaming client, a11y+IME insertion engine, FGS/watchdog keepalive, SDK inventory), `docs/FEATURE_PARITY.md` corrected with app-grounded evidence. Raw decompiled output quarantined at `~/workspace/analysis/wisprflow/` (never in repo). |
| UI direction | **Decided 2026-10-19:** retro 8-bit games vibe for UI/UX chrome and feedback; typeface Fredoka (Latin) + Baloo Tammudu 2 (Telugu companion) — NOT pixel fonts. Concept: `docs/UI_CONCEPT_8BIT.md` |

## Blockers

1. **Telugu transcription is effectively unusable with whisper tiny/base/small** (measured 117–128% mean WER on the FLEURS Telugu subset; hypotheses are script gibberish/hallucinations). Do NOT ship or claim Telugu support on these models. Open: try `large` variants, or evaluate sherpa-onnx/Vosk for Telugu.
2. **No ARM64 Android device** — the only latency/RSS numbers that matter for the ship decision cannot be measured yet.
3. **No Android SDK/NDK** in this environment — required before the JNI bridge or any Android build.
4. **Code-switching not evaluated** — no Telugu-English code-switch clips in the corpus; nothing was invented to fill the gap.
5. `.github/workflows/ci.yml` not on GitHub yet — 403, the GitHub App lacks the `workflows` permission. Manual upload needed (see Publish blockers).
6. Common Voice is no longer on Hugging Face (files removed Oct 2025); a Mozilla Data Collective account (interactive sign-in) would be needed for CC0 clips.

## Next steps (in order)

1. Upload `.github/workflows/ci.yml` manually via the GitHub web UI (one file, content in local repo).
2. Investigate Telugu: benchmark whisper `large` (or `large-v3-turbo` when available in ggml) and/or sherpa-onnx models on the Telugu subset before any Telugu claim.
3. Install Android SDK/NDK; build the `wahtuh-jni` bridge; measure on an ARM64 device.
4. Then: Android app shell, text-insertion spike, provider opt-in transport.
