# Feature Parity Matrix — Wispr Flow vs Wahtuh Pho

**Date:** 2026-10-19 · **Basis:** [WISPR_FLOW_RESEARCH.md](WISPR_FLOW_RESEARCH.md) (clean-room research; Wispr Flow column reflects publicly documented behavior, paraphrased, not copied).

**Status values:** `planned` · `in-progress` · `done` · `not-planned`

**Honesty note:** the Wahtuh Pho MVP is not built yet. As of 2026-10-19 the
workspace is a Rust scaffold: the six core crates compile and their 71 tests
pass, but `LocalEngine` is a stub (no model weights, no native backend), there
is no Android app shell, and no evaluation benchmarks have been run. Nothing
below is marked `done` — that would be a fabrication. Nothing is `in-progress`
either, since implementation phases beyond the scaffold have not started.

| Feature | Wispr Flow (public info) | Wahtuh Pho MVP target | Status |
|---|---|---|---|
| Offline local transcription | None — cloud-only. Official data-controls page: "Transcription always occurs on the cloud." Internet required for every dictation. | Core differentiator: on-device transcription (Rust core + local model) with zero network requirement | planned |
| Mic recording | Push-to-talk (hold hotkey / hold floating bubble), plus hands-free double-tap mode reported by reviewers; whisper mode for quiet spaces (official) | Push-to-talk recording via Android mic; hands-free toggle as stretch goal | planned |
| Text insertion into apps | Any app with a text field. Desktop: hotkey-driven insertion at cursor. Android: floating bubble + Accessibility service (keeps user's own keyboard; does not insert into password/credit-card fields). iOS: custom keyboard with Full Access. | Android-first: floating bubble + Accessibility service (same approach), with an IME/keyboard option evaluated later | planned |
| Auto-punctuation / formatting | Auto-punctuation, capitalization, paragraphing, numbered lists, name spelling from context; code-syntax awareness (official) | Auto-punctuation and capitalization; lists/formatting as follow-up | planned |
| Voice editing commands | Inline commands while dictating ("make that a bullet list"); Command Mode (Pro-only): highlight text, speak rewrite instruction ("make this shorter", "delete the last sentence") | Basic inline editing commands post-MVP (e.g. "new paragraph", "delete that"); full Command-Mode-style rewrite needs cloud LLM — deferred | planned |
| Transcript history | "Recent activity"/"History" when storage settings allow; local device history (keep / auto-delete 24h / never), separate cloud history; per-session caps (20 min desktop, ~5 min Android) | Local-only transcript history on device, user-clearable, no cloud | planned |
| English support | Full; tone Styles limited to "English and desktop only" (official) | Full English support | planned |
| Telugu support | No official confirmation found. One independent reviewer lists Telugu among available regional languages; one article describes Telugu localization as planned. Conflicting third-party reports only. | Native Telugu transcription | planned |
| Telugu–English code-switching | No evidence found anywhere (officially supported code-switching is Hinglish only, per Play Store listing) | Tenglish (Telugu↔English mix) code-switching as a headline feature | planned |
| Custom vocabulary | Personal dictionary (auto-learns corrections, manual adds), snippets (voice shortcuts expanding to text blocks), cross-device sync; shared dictionary/snippets on Teams plan | Custom vocabulary / personal dictionary, local-only | planned |
| Cloud provider options | None — proprietary cloud only, no BYOK, no provider choice | Optional cloud provider fallback (opt-in, user-configured) for heavy tasks; local remains default | planned |
| Privacy (local-only mode) | No local-only mode. Best case is "zero retention" config: audio discarded after cloud transcription, model training off, cloud storage off — but audio still leaves the device every dictation | Local-only by default: audio never leaves the device; no account, no telemetry | planned |
| ARM64 Android support | Android 13+ via Google Play (package `com.wispr.flowapp`); ARM64 build not explicitly claimed — inferred from Play distribution | Native ARM64 Android target (primary MVP platform) | planned |
| iOS / desktop apps | Mac, Windows, iPhone (all officially supported) | Not in MVP scope — Android first | not-planned |
| Subscription / paywall | Freemium: free word caps (2,000/wk desktop, 1,000/wk iPhone, unlimited Android limited-time); Pro $15/mo or $144/yr; Teams $10–12/user/mo; Enterprise custom | Free and open-source; no accounts, no word caps, no paywall | not-planned |
| Wake word ("Hey Flow") | Reported by third-party reviews as Pro feature; not confirmed on official pages | Not in MVP | not-planned |
| Meeting notetaker | Notetaker feature (meeting transcripts/summaries; reviewers note Mac-only) | Not in MVP | not-planned |
| Usage dashboards / stats | Team usage dashboards (official, Teams); per-user word stats reported by reviewers | Basic local usage stats (stretch goal) | not-planned |

## Deliberate gaps (what we are NOT copying)

1. **No cloud-dependent architecture.** Wispr Flow's entire pipeline requires connectivity; Wahtuh Pho's reason to exist is the opposite.
2. **No account system.** Wispr Flow requires email/password sign-in before first use; the MVP needs no account.
3. **No tone-adaptation-by-app surveillance.** Wispr Flow's Styles/Context Awareness reads on-screen text of the target app; we will not replicate screen-reading beyond what text insertion mechanically requires.
4. **No word caps or tiers.** Parity here means matching the *user-facing capability* (dictation, cleanup, dictionary, history), not the business model.
