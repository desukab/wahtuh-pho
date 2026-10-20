# Wahtuh Pho — Strategy: The Best Calls

**Date:** 2026-10-20 · **Status:** decision document. This is not a menu of options — it records the decisive calls the research supports. Every major decision carries 1–2 lines of evidence. Revisit a call only when new measured evidence contradicts it.

**Mission:** make wahtuh-pho the best Wispr Flow alternative one human + Muse can build — Telugu-first, local-first, open source, zero budget. "It's not a feature. It's the basics we need to achieve."

**Non-negotiables (apply everywhere):** never claim 100% accuracy, in-app or in marketing · never ship broken Telugu as if it works · local-first must be verifiable, not a slogan · the zero-budget constraint is absolute.

## 0. Verified baseline this strategy builds on

- Real local engine: whisper.cpp v1.8.3 via whisper-rs; 93 measured runs on FLEURS (15 EN / 15 TE clips). **EN small: 7.5% WER. Telugu: 117–128% WER on tiny/base/small — unusable.** Code-switching unevaluated. (`docs/EVALUATION.md`)
- Competitor teardown: Wispr Flow Android is a **cloud-native thin client** — zero on-device ML, mic → gRPC bidi streaming → cloud, insertion via accessibility service + own IME fallback, foreground-service keepalive stack. Zero Telugu/Indic strings in the app. (`docs/COMPETITOR_ARCHITECTURE.md`)
- Competitor pricing (verified Oct 2026): Free = 2,000 words/week desktop; **Pro $15/mo ($144/yr)**; no lifetime tier; India ₹400/mo; cloud-only, no offline. (`wisprflow.ai/india`, pricing roundups)
- Competitor weaknesses (verified): privacy scandal (screenshot capture, banned Reddit user, Trustpilot 2.7/5); Android insertion falls back to **clipboard** when it can't insert; cloud-only; price; Telugu exists at best in a language list with **zero published accuracy numbers** — regional Telugu support is publicly described as *planned*.
- Deepgram adapter (`nova-3`, `te`/`en`/`multi`) implemented; **API key blocked: keys #1, #2, #3 all rejected with 401 INVALID_AUTH as of 2026-10-20 ~00:29 IST** — plumbing proven, key values invalid; blind retries paused, user verifying in Deepgram console (key active + under the project holding the credits) before a 4th attempt. Cloud benchmark on own Telugu clips still not run.

---

## 1. Engine calls (the load-bearing decisions)

### DECISION 1 — Offline tier: Adalat AI Koyal for Telugu, whisper small for English
**Call:** Pilot-export **Koyal** (`koyal-te-120m-1.0` Telugu, ~0.5 GB / ~120 MB int8; or the 600M streaming Indic variant) to ONNX and run it under ONNX Runtime / sherpa-onnx on Android. Keep measured whisper-small (7.5% EN WER) as the offline English path. Vosk Telugu (~50 MB, Android bindings) is the interim fallback only — disclose it as lower quality.

**Evidence:**
- Koyal is the only open model with competitive published Telugu numbers: **17.81% WER_SCRIBE on FLEURS-RO** (rich-orthography metric), Indic-specific 279k-hour training, streaming-capable variant — everything else open is research-grade (AI4Bharat XLSR fine-tunes ~23–35% WER, no Android path).
- whisper large-v3-turbo is **not shippable** on general Android: RTF 1.386 on a laptop-class CPU (slower than realtime; phones are 2–5× slower), 1.6 GB download, ~2.3 GB RAM; NPU path is flagships-only. NeMo Parakeet has **no Telugu** (European langs only); sherpa-onnx zoo has no Telugu streaming model.

**Gate:** Koyal must (a) pass the Telugu script-integrity gate (no mixed-script corruption, conjuncts hold) and (b) beat whisper-small's garbage on the project's own FLEURS Telugu clips, on a Redmi-class device, before v1.0 ships Telugu offline.

### DECISION 2 — Cloud/premium tier: Deepgram nova-3 primary, Sarvam Saaras V4 as challenger to A/B
**Call:** Deepgram nova-3 is the primary cloud engine (adapter already implemented, WebSocket streaming ~250–450 ms, **Telugu officially supported as monolingual model since Jan 2026** with Indic improvements Mar 2026, per-second billing, $200 credits in hand ≈ **~40,000 minutes** at the $0.0048/min promo rate). **Sarvam Saaras V4 gets a parallel eval** — it's the only vendor explicitly built for Telugu+English code-mixing (`codemix` mode), INR-priced (~$0.0056/min), Indian-built.

**Evidence / disqualifications:**
- **AssemblyAI is rejected for realtime Telugu** — its streaming models cover only EN/ES/FR/DE/IT/PT; no Telugu streaming at all.
- Speechmatics Telugu support could not be confirmed despite 55-language marketing — too uncertain.
- Google Chirp is the published Telugu accuracy ceiling (**11.8% Telugu WER** in a dialect-aware-ASR paper) but costs 2–3× Deepgram — use as the benchmark target, not the production tier.
- Azure MAI batch at $0.0017/min is the cheapest credible backfill/re-transcription option, but it's **preview, no SLA** — eval/dev only today.

**Mandatory next step (blocked today):** user verifies a key in the Deepgram console (active + attached to the credit-holding project) → run the head-to-head benchmark on the project's own 31 FLEURS clips (`te`/`en`/`multi`) → record WER/CER/latency/cost. No Telugu cloud claim until measured. Sarvam `codemix` eval runs on real Tanglish audio. **If Deepgram keys keep failing, Sarvam and Google's free tier ($300 credits, 60 min/mo free) become the primary cloud path, not the backup.**

### DECISION 3 — Code-switching: route, don't pretend
**Call:** No vendor publishes Telugu+English code-switch numbers — treat all claims as unknown. Interim: per-utterance language ID, route to `te` or `en` model rather than one fake "handles everything" model. Deepgram's `multi` code-switch set **excludes Telugu** — don't rely on it. Eval Sarvam `codemix` as the designed-for-it alternative. Never "correct" a word's script across the switch.

### DECISION 4 — Insertion: two paths, shims as data, no silent clipboard
**Call:** Match the incumbent structurally — **accessibility-service path** (probe focused node, `ACTION_SET_TEXT`/`ACTION_PASTE`) **+ own-IME fallback** (`commitText`/`setComposingText`), auto-selected, never a settings maze. But beat their failure mode: **never silently fall back to clipboard** — their own docs admit "if Flow cannot safely insert your text, it copies it to the clipboard instead," and Play Store users hate it. If insertion can't succeed, say so explicitly and offer the fix.

**Evidence:** insertion reliability is their #2 Android complaint; per-app shims (Chromium/WebView, Flutter, rich-text editors) are a durable moat — build the shim layer as **versioned per-package data with opt-in failure telemetry** from day one, not scattered `if`s.

### DECISION 5 — Latency budget (release gates, not aspirations)
**Call:** first partial **<400 ms** from speech onset; final text **<300 ms** after endpointer silence (~300 ms); end-to-end mouth-to-caption **<600 ms**. Streaming partials at ~100–200 ms cadence, always — never batch-only.

**Evidence:** real-time speech pipeline practice (forasoft 2026 reference); the incumbent's whole product is "text appears while you speak" — the felt experience must match even though our inference is on-device. **Gate on P95 measured on the cheapest supported device** (Redmi-class, not the dev phone).

### DECISION 6 — Audio pipeline + session ownership
**Call:** 16 kHz mono; debounced VAD (min speech ~150 ms, min silence ~300 ms, **300 ms pre-roll** so the first syllable is never clipped); light noise suppression (<15 ms/frame); honor Bluetooth SCO / wired / phone-mic routing with mid-session route changes; single session state machine owned by the **Rust core** (`idle → listening → processing → done|error`), cancel <200 ms with zero partial leaks.

**Evidence:** Bluetooth is the #1 audio pitfall (8 kHz SCO, OEMs lying about route state) and the audience is budget phones + cheap earbuds; mic-lifecycle concurrency bugs (double-start, rotate-mid-session) are the classic solo-dev sink — one owner kills them.

### DECISION 7 — Secure-field stand-down as a trust feature
**Call:** detect password/secure input within ~<100 ms of focus and **refuse to dictate**, explicitly, with UI; such sessions never enter history. Conservative on banking/finance apps (over-block, audit with hostile test apps).

**Evidence:** an Indian reviewer uninstalled the competitor because their **banking app refused to open** with its accessibility service present. Our stand-down is *stronger* because it's backed by the local-only guarantee — turn it into a selling point, not an apology.

### DECISION 8 — Punctuation & commands: precision over vocabulary
**Call:** auto-punctuation post-processing (<30 ms added); a **small fixed command vocabulary** ("comma", "full stop", "question mark", "new line", "delete word", "undo") at ≥95% precision — precision over vocabulary size; deterministic user dictionary (local, editable) before snippets.

**Evidence:** a misfired "delete" destroys more trust than a missing command; command-vs-dictation ambiguity needs a documented disambiguation rule taught at onboarding.

### DECISION 9 — Keepalive is a layered system, and honest about OEMs
**Call:** typed `microphone` foreground service + guided battery-exemption flow + per-OEM whitelist screens (dontkillmyapp.com canon) + `START_STICKY`/JobScheduler as recovery net + self-diagnostics ("killed 3 times this week — tap to fix").

**Evidence:** no code fully defeats Xiaomi/Samsung/OnePlus task killers — the fix is UX, and the onboarding is where it must live. Idle drain target: <1–2%/night armed.

### DECISION 10 — Privacy: verifiable, SDK-audited
**Call:** `local_only` enforced **at the network boundary** (zero dictation network calls — document how users verify with a firewall log); retention controls (history on/off, auto-expire, clear-all); audio never retained by default; **audit every dependency's network behavior** (crash/analytics SDKs are where guarantees die); cloud tier promises in writing: TLS, no training without opt-in, defined retention.

**Evidence:** the competitor's single most quotable scandal is screenshot capture + account ban of the user who reported it. "Never sends your voice to a server" is the cleanest counter-position we own.

---

## 2. "The basics done perfectly" — v1.0 checklist (priority order)

1. **Insertion reliability** — verbatim ≥99% on a ~20-app test matrix; commit <100 ms after final; two auto-selected paths; shims as versioned data.
2. **Latency** — first partial <400 ms; final <300 ms post-endpointer; end-to-end <600 ms; **P95 on cheapest supported device as release gate**.
3. **Streaming partials + commit pipeline** — word-diffed partials in a provisional region; only finals commit; **one serialized commit log with sequence numbers** (the "two writers" race is the #1 "this app is broken" moment).
4. **Audio pipeline** — VAD debounce (150/300 ms + 300 ms pre-roll), <15 ms/frame NS, Bluetooth SCO honored, route changes don't kill sessions.
5. **Session state machine** — Rust-core-owned `idle → listening → processing → done|error`; cancel <200 ms; mic indicator never lies; genuine offline-by-default.
6. **Secure-field stand-down** — <100 ms detection, explicit refusal UI, nothing retained, banking apps conservative.
7. **Punctuation & commands** — auto-punct <30 ms; small ≥95%-precision command vocab with disambiguation rule; local dictionary.
8. **Battery/background** — typed FGS, battery-exemption onboarding, per-OEM guidance, self-diagnostics; <1–2%/night idle drain.
9. **Privacy** — `local_only` network-boundary enforcement, retention controls, dependency audit, written cloud promises.
10. **Telugu script gate** — correct Unicode, no mixed-script corruption, conjuncts (క్ష జ్ఞ త్ర స్త్ర) hold across apps, Western punctuation per real usage, code-switch faithful per script. **If a model can't hold script integrity, it doesn't ship for Telugu** — English-first with Telugu experimental is a defensible v1.0 if the gate isn't passed.
11. **Offline multilingual gate (premium markets)** — the paying users are US/UK/Germany: offline English must hold across accents (US, UK, Indian English at minimum), plus German, French, Spanish at shippable model sizes. A model that is "good" only as a 500 MB download doesn't count — the gate is **quality at a size budget phones can carry** (tiny/base class, ~75–150 MB). German/French/Spanish get the same FLEURS treatment as en/te before v1.0: measured WER per language per model, no marketing claims. **First measurement (2026-10-20, docs/EVALUATION.md §8): German tiny 31.6% / base 21.8% / small 8.8% WER — only small clears "really good", at 488 MB (3× over the size gate).** German joins Telugu on the cloud-tier-at-launch list unless a German-specialized ~100–150 MB model is found and measured.

Full detail: `~/workspace/wahtuh-pho-research/dictation-basics-checklist.md` (research working file, not in repo).

---

## 3. Monetization — ads carry the free tier, limits sell the premium

**The call (user, 2026-10-20):** the Telugu/Hindi audience won't pay subscriptions — monetize on **ads + better limits on premium tiers, that's it.** Subscription conversion in India is near-zero; ad-supported free is the only mass-market model. The paid tiers target Western users (the undercut-vs-Wispr positioning holds there); India revenue is ads.

| Tier | Price | What's inside |
|---|---|---|
| **Free "Nubcat"** | $0, ad-supported | Unlimited on-device dictation (marginal cost $0). Small monthly cloud-minute quota for Telugu cloud, ad-funded. |
| **Premium "Gulabipilli"** | **$4.99/mo or $39/yr** | No ads. Large cloud-minute bucket (6 hrs/mo fair-use) for Telugu + English cloud. |
| **Premium Max "OG Nubcat"** | **$9.99/mo or $79/yr** | No ads. Highest cloud limits + early features + supporter status. |
| **Lifetime Local** | **$99 one-time** | Everything local forever + no ads. No recurring cloud minutes — that's what makes lifetime sustainable. |
| **BYOK add-on** | Free | Plug in your own Deepgram/Sarvam key (~$0.26–0.46/hr direct). Zero markup, zero trust required. |

**Unit-economics guardrail (the math that keeps free alive):** offline dictation costs $0 → unlimited free is safe forever. Cloud costs ~$0.0043–0.0052/min, so the free cloud quota must satisfy **ad revenue per user/mo > quota × cost/min**. Start conservative (15–30 min/mo ≈ $0.07–0.16 cost) and tune the quota from measured ad ARPU — never from hope. Premium $4.99 with 6 hrs/mo costs ~$1.55–1.87 → healthy margin. The $200 Deepgram credits (~40,000 min) fund the beta; the free quota must be ad-sustainable before they expire (~1 yr out).

**Ad placement rule (product constraint, non-negotiable):** ads NEVER in the dictation path — nothing in the bubble overlay, no interstitial between mic-tap and committed text. Ads live only in the app's own screens (Quest Log/history, settings, onboarding recap). An ad that delays or interrupts dictation kills the product; the dictation flow is sacred.

**One-liner:** free forever with ads vs their 2,000 words/week paywall; premium 67% cheaper than Wispr Pro ($4.99 vs $15), sold on limits not promises; lifetime exists vs theirs doesn't.

**Honesty guardrails (on the pricing page itself):** free cloud quota stated up front with what happens at overage (auto-downgrade to offline, never a surprise bill or mid-sentence cutoff); ads disclosed; Telugu runs on cloud at launch — local Telugu is roadmap, never promised (small models measured 117–128% WER); never claim 100% accuracy.

---

## 4. USP vs Wispr Flow (proof-pointed)

1. **Open source + local-first** — Apache-2.0 published repo, real on-device engine vs their closed cloud-only thin client.
2. **Telugu-first** — zero Telugu/Indic strings in their Android app; we ship Telugu UI, dictation, and community day one. Their Telugu is *planned*; ours is the product.
3. **Radically cheaper** — 67–73% under their Pro; free tier genuinely unlimited; lifetime exists.
4. **Works offline, no account** — zero-connectivity dictation vs their "no internet = no product."
5. **BYOK cloud** — your key, your audio straight to the provider, zero trust in us required.
6. **Privacy you can verify** — local-only enforced at the network boundary, against their screenshot-capture scandal.
7. **It's a game, not SaaS** — 8-bit identity, quests/XP/streaks, a Telugu meme channel vs their enterprise aesthetic.

---

## 5. Prioritized build order

1. **Unblock Telugu evidence (now).** Working Deepgram key → run bench on own FLEURS clips (te/en/multi) → record WER/CER/latency/cost in `docs/EVALUATION.md`. Parallel: Sarvam Saaras V4 `codemix` eval on real Tanglish audio. Parallel: Koyal → ONNX pilot export + Android feasibility check.
2. **Insertion spike.** a11y service + own-IME fallback paths, 20-app test matrix (weight web editors), shim layer as versioned data. This is the moat; do it before the pretty UI.
3. **Audio pipeline + session core in Rust.** VAD, noise suppression, 300 ms pre-roll, Rust-core state machine, <200 ms cancel. Bind through `wahtuh-jni` design (already in `docs/ANDROID.md`).
4. **Streaming partials + commit pipeline.** Sequence-numbered serialized commits; composing-region rendering; never silently rewrite a committed word.
5. **Android shell: bubble + onboarding.** Try-it-yourself first dictation as activation (their structural pattern), permission education before system dialogs, battery-exemption UX, 8-bit skin per `docs/UI_CONCEPT_8BIT.md`.
6. **Punctuation/commands/dictionary.** Auto-punct, fixed command vocab with disambiguation, local dictionary UI.
7. **History + "Quest Log" + privacy controls.** Retention, auto-expire, clear-all, `local_only` enforcement tests.
8. **Monetization plumbing.** Ad SDK (AdMob) in non-dictation screens only, cloud-minute accounting + quota enforcement, BYOK, payment (Play billing), pricing page with honesty guardrails.
9. **Beta via the meme channel.** Play listing with Telugu ASO (వాయిస్ టైపింగ్, తెలుగు డిక్టేషన్), 100 testers from the audience, 90-day organic GTM (demo memes, creator DMs, Show HN, r/telugu, Product Hunt).

## 6. Biggest risks

1. **Telugu is still unproven on both tiers.** Locally: whisper small-class models are measured garbage (117–128% WER); Koyal's 17.81% is the best open number but the ONNX-on-Android pilot hasn't run. Cloud: Deepgram `te` has official support but **no published Telugu WER**, and our own benchmark is blocked — three API keys have returned 401 with blind retries now paused. If a verified key still fails, Sarvam/Google free tiers become the primary cloud path, not the backup. If both underdeliver, the Telugu-first USP — the entire positioning — has no product behind it. *Mitigation: run all three Telugu probes (Deepgram bench, Sarvam eval, Koyal export) before committing to any v1.0 Telugu promise; ship English-first + Telugu-experimental if the gates fail.*
2. **Insertion matrix explosion.** The shim-per-app moat is real but unbounded for one dev — the telemetry-driven shim layer is the only scalable answer, and it needs users before it has data. Cold-start it with the top-20-app matrix and WhatsApp/Chrome/Docs weighted heaviest (the audience's apps).
3. **OEM battery killers vs the bubble.** Works on a Pixel, dies on a Xiaomi — the audience's phones. This is UX work, not code, and it never fully ends.

---

*Research basis: four parallel research tracks (2026-10-20) — competitor intel, STT engine landscape, dictation basics, pricing/positioning — built on the verified repo docs (`PROJECT_STATUS.md`, `COMPETITOR_ARCHITECTURE.md`, `ANDROID_UX_TEARDOWN.md`, `UI_CONCEPT_8BIT.md`, `EVALUATION.md`). This doc is written locally for review; push to GitHub only after approval.*
