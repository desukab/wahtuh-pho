# Wahtuh Pho — Project Status

**Mission:** open-source, Rust-powered, offline-first voice dictation
(Wispr Flow alternative). English, Telugu, and Telugu–English code-switching.
**Target repo:** `desukab/wahtuh-pho` (not created — blocked, see Publish blockers) · **Last updated:** 2026-10-19 (IST, publish prep)

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

## License rationale

**Apache-2.0.** It is a permissive license that allows commercial use and
modification while keeping the ecosystem open for contributions; its explicit
patent grant protects both contributors and users against patent litigation;
and it is compatible with the project's MIT-licensed dependencies and planned
integrations (whisper.cpp is MIT, whisper-rs is MIT/Apache-2.0). That
combination makes it the right fit for an open-source Wispr Flow alternative
that should stay open whether it is built on by hobbyists or companies.

## Publish blockers

1. **GitHub repo creation failed (2026-10-19):** `create_repository` for
   `desukab/wahtuh-pho` returned `403 Resource not accessible by integration`
   from `POST https://api.github.com/user/repos`. User approval was granted
   for the attempt, so this is not an approval problem — the connected
   GitHub App (OAuth login `desukab`) lacks the repository-creation
   permission/scope. No retry loop was attempted.
   → **Needs user:** create `desukab/wahtuh-pho` manually (or grant the
   integration repo-creation scope), then run
   `git remote add origin https://github.com/desukab/wahtuh-pho.git` and
   `git push -u origin main` from `~/workspace/wahtuh-pho`.
2. **Push not attempted** — there is no remote to push to until blocker 1 is
   resolved. Git-over-HTTPS credentials for push have not been verified; push
   may need the user's credential setup.
3. Toolchain note: actual measured toolchain is `rustc 1.99.1` / `cargo
   1.99.1` (via rustup), not 1.90.1 as recorded in an earlier handoff.

## Current status per workstream

| Workstream | Status |
|---|---|
| `wahtuh-audio` (PCM, chunker, bounded buffer) | Scaffolded + tested (12 unit tests) |
| `wahtuh-transcribe` (`Engine` trait, `MockEngine`) | Scaffolded + tested (7 unit tests). `LocalEngine` is a **stub** (`ModelNotBundled`) — no native backend linked |
| `wahtuh-normalize` (cleanup, Telugu preservation, dictionary) | Scaffolded + tested (11 unit tests) |
| `wahtuh-providers` (Groq/Gemini/OpenAI adapters) | Scaffolded + tested (16 unit tests). No HTTP transport (std-only); unreachable when `local_only` |
| `wahtuh-config` (config + local-only guarantee) | Scaffolded + tested (9 unit tests) |
| `wahtuh-core` (session, history) | Scaffolded + tested (10 unit + 6 integration tests) |
| `wahtuh-jni` Android bridge | Not started (design in `docs/ANDROID.md` only) |
| Android app shell | Not started |
| Evaluation / benchmarks | **Not run.** Plan written (`docs/EVALUATION.md`); no corpus, no scorer, no results |
| GitHub repo `desukab/wahtuh-pho` | Not created yet (GitHub login `desukab` connected via OAuth 2026-10-19) |

## Blockers

1. **No model weights** — nothing to run inference with; `LocalEngine` stub until whisper.cpp weights are downloaded (license terms to verify) and `whisper-rs` is integrated.
2. **No ARM64 Android device** — the only latency/RSS numbers that matter for the ship decision cannot be measured yet.
3. **No Android SDK/NDK** in this environment — required before the JNI bridge or any Android build; disk space is limited.
4. **No evaluation corpus** — Common Voice subsets not downloaded; code-switch samples not recorded.
5. GitHub repo not created/published yet — repo-creation API call failed with `403 Resource not accessible by integration` (the connected GitHub App lacks repo-creation scope). Details + manual workaround in [Publish blockers](#publish-blockers).

## Next steps (in order)

1. Publish: `desukab/wahtuh-pho` repo creation on GitHub is **blocked** (GitHub App lacks repo-creation scope — see [Publish blockers](#publish-blockers)); license, README, CI, and local git history are done. User needs to create the repo (or grant scope), then `git remote add origin` + `git push -u origin main`.
2. Download multilingual whisper `tiny`/`base` weights (verify license terms); integrate `whisper-rs` behind the `Engine` trait per `docs/ARCHITECTURE.md` §8.
3. Collect the evaluation corpus per `docs/EVALUATION.md` §2; write the scorer; record the first `eval/runs/` results on x86_64.
4. Install Android SDK/NDK; build the `wahtuh-jni` bridge; measure on an ARM64 device.
5. Then: Android app shell, text-insertion spike, provider opt-in transport.
