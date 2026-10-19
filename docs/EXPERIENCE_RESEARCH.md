# Wahtuh Pho — Experience Research: making dictation feel great

**Date:** 2026-10-20 · **Status:** local research document for product review. **DO NOT PUSH to GitHub without explicit approval.**

**Product principle (non-negotiable):** the customer never needs to know about the backend. Every technical complexity must have an invisible UX answer. A user should never encounter the words *model, engine, endpoint, quota (as a technical term), API,* or *latency*.

**How to use this doc:** Track A is the arXiv evidence base for streaming-text UX decisions. Track B is the patent design-around map (read before finalizing correction UX and the own-IME insertion path; it is a landscape scan, **not** a freedom-to-operate opinion — a patent attorney should review the final design). Track C is the design checklist: every row must answer YES before a release ships.

**Research boundary (honored throughout):** we research to understand the space and design around it — never to copy. Same rule as the competitor teardown: understand, never steal.

Related docs: `docs/ANDROID_UX_TEARDOWN.md` (competitor UX patterns), `docs/UI_CONCEPT_8BIT.md` (our 8-bit identity: onboarding → "New Game", bubble → "Battle HUD", history → "Quest Log"), `docs/STRATEGY.md` (decisions), `docs/EVALUATION.md` (measured numbers).

---

## TRACK A — arXiv (streaming ASR & dictation experience)

Scope: arXiv, 2020–2026. All IDs, titles, and numbers below were verified from the papers' own arxiv.org abstract pages (checked 2026-10-20). Claims are cited per-paper; no invented figures.

### Endpointing — when to stop listening

**1. arXiv:2505.17070 — "Improving endpoint detection in end-to-end streaming ASR for conversational speech"**
Transducer ASR delays token emission, and inaccurate endpointing either cuts users off mid-speech (incomplete transcript) or adds perceived latency — both degrade experience. The authors fix this with an end-of-word token plus delay penalty, and frame-level speech-activity detection via an auxiliary network, evaluated on Switchboard. UX takeaway: endpointing is a cutoff-vs-latency trade-off, not a tuning detail — aggressive endpointing truncates dictation, conservative endpointing makes the app feel sluggish. (arXiv:2505.17070)

**2. arXiv:2211.00786 — "Unified End-to-End Speech Recognition and Endpointing for Fast and Efficient Speech Systems" (Google)**
Jointly training ASR and endpointer as one multitask model (a "switch" connection letting the endpointer consume ASR encoder states) cut median end-of-query latency by 120 ms (30.8%) and P90 by 170 ms (23.0%) on voice search, with no WER regression and a 10.6% relative WER improvement on continuous recognition. UX takeaway: an endpointer that "listens" to what the ASR itself knows finishes sentences faster without cutting users off — the silence-timeout hack is not the ceiling. (arXiv:2211.00786)

**3. arXiv:2506.07081 — "Streaming Endpointer for Spoken Dialogue using Neural Audio Codecs and Label-Delayed Training"**
Using streaming, low-bitrate neural-audio-codec features plus a label-delay training scheme cut cutoff errors by 42.7% (single-stream) and 37.5% (two-stream) at a fixed 160 ms median latency; plugged into a codec-based speech LLM, it cut median response time by 1200 ms and cutoffs by 35%. UX takeaway: in two-party/dictation flows, the endpointer should know whose turn it is (two-stream); cutting the user off is a first-class error metric, not just latency. (arXiv:2506.07081)

**4. arXiv:2305.18596 — "Building Accurate Low Latency ASR for Streaming Voice Search" (Flipkart)**
For Hinglish voice search, joint CTC-based end-of-speech detection cut search latency by ~1300 ms (46.64%) versus an independent VAD model, at 4.78% WER with EOS. UX takeaway: for the Hindi/English code-switching audience specifically, a single jointly-trained model beats a bolt-on VAD — and the benchmark is real Hinglish at 6M queries/day, not clean lab speech. (arXiv:2305.18596)

### Partial hypotheses — how streaming text stabilizes on screen

**5. arXiv:2609.08672 — "X2Streaming-ASR: wait when uncertain, emit when ready for streaming ASR"** *(preprint, Sep 2026)*
Reframes streaming as "when to commit vs. what to commit" under a hard-commit (no-rewrite) constraint, training a commit policy with RL on accuracy + latency. Claims mean commit latency of 32–109 ms per Chinese character and 12–85 ms per English word at comparable accuracy. UX takeaway: this is the architectural direction behind "words appear the moment you're sure" rather than a flickering draft; the hard-commit design means once text lands, it never rewrites — exactly the dictation-feel constraint. Caveat: extremely new preprint, not peer-reviewed; treat the latency numbers as the authors' claim. (arXiv:2609.08672)

**6. arXiv:2312.09463 — "Partial Rewriting for Multi-Stage ASR" (Google)**
In two-stage (small streaming model + big final model) systems, a text-merge algorithm that folds the big model's quality into the streaming outputs improved partial-hypothesis quality ~10% with zero added latency and reduced flickering — no retraining needed. UX takeaway: the visible flicker between "live draft" and "final transcript" can be damped algorithmically; users should see partial text get quietly *better*, never flash-rewritten. (arXiv:2312.09463)

### Whisper specifically (experience of making it streaming)

**7. arXiv:2307.14743 — "Turning Whisper into Real-Time Transcription System" (Whisper-Streaming, Charles University)**
Wraps an unmodified Whisper with a "local agreement" policy — only emitting text that two consecutive chunk decodings agree on (n=2 chosen as most effective) with self-adaptive latency — reaching high quality at 3.3 s latency on unsegmented long-form speech, deployed at a live multilingual conference. UX takeaway: vanilla Whisper can't stream; bolt-on stabilization policies give you long-form transcription but at multi-second latency — fine for conference captions, too slow for "typing as you speak" dictation feel. (arXiv:2307.14743)

**8. arXiv:2406.10052 — "Simul-Whisper: Attention-Guided Streaming Whisper with Truncation Detection"**
Uses Whisper's own cross-attention alignment to decode chunk-by-chunk without fine-tuning, and — key for UX — detects truncated words at chunk boundaries via integrate-and-fire and withholds them until the next chunk, costing only 1.46% absolute WER at 1 s chunks. UX takeaway: the single most visible streaming artifact is the half-word at a boundary; deleting the unreliable last word instead of flashing it is a cheap, model-agnostic fix. (arXiv:2406.10052)

**9. arXiv:2508.12301 — "WhisperRT — Turning Whisper into a Causal Streaming Model"**
Fine-tunes both Whisper encoder (made causal) and decoder so tokens emit as soon as sufficient acoustic evidence arrives, working with <300 ms chunks and outperforming non-fine-tuned streaming hacks. UX takeaway: real sub-second streaming Whisper needs model surgery (causal encoder + alignment fine-tuning), not wrapper tricks — the "invisible backend" cost of Whisper is real engineering, not just a policy on top. (arXiv:2508.12301)

### Code-switching UX

**10. arXiv:2206.09765 — "Bilingual by default: Voice Assistants and the role of code-switching in creating a bilingual user experience" (provocation paper)**
Argues voice assistants are monolingual by default, and that supporting code-switching (multi-language recognition, sensitivity to switching preferences in output) is an inclusion issue: bilinguals switch partly because retrieving a word in one language is effortful. A companion chatbot study found users enjoyed code-switching as long as it was predictable; random/ungrammatical switching eroded trust and task success. UX takeaway: for Telugu/English users, "just pick a language" is a failure mode — and once you support mixing, the mixing itself must be predictable, not glitchy. (arXiv:2206.09765)

### Punctuation & formatting expectations (the dictation-specific pain)

**11. arXiv:2301.03819 — "Streaming Punctuation: A Novel Punctuation Technique Leveraging Bidirectional Context for Continuous Speech Recognition"**
Voice-typing and meeting transcription suffer from over-segmentation because dictating users pause irregularly; prosody-based punctuation gets fooled by those pauses, so a text-only streaming re-punctuator (dynamic decoding windows, never rewriting the transcript) cut over-segmentation (13.9% F0.5 gain) and worked under real-time constraints. UX takeaway: do not infer sentence breaks from pauses in dictation — users pause to *think*, and the app must re-punctuate the text stream, not punish them with fragments. (arXiv:2301.03819)

### Gaps honestly unfilled by this sweep

- **PTT vs. continuous-listening UX:** no arXiv paper in the 2020–2026 window directly compares push-to-talk against continuous/VAD-driven dictation UX. That question is interaction-design territory (CHI/HCI literature), not arXiv.
- **Perceived-latency thresholds for dictation specifically:** the latency numbers above (120–1300 ms improvements) are engineering metrics on voice-search/dialogue corpora, not "what dictation users perceive as acceptable."
- **User studies of error correction / dictation behavior:** arXiv has almost no first-party dictation UX studies in this window; that literature lives in CHI/IUI (e.g., Kumar, Paek & Lee's "Voice Typing" CHI 2012, Ruan et al.'s speech-vs-typing CHI 2016).

---

## TRACK B — Patent landscape (design-around map)

*Scope note: this scan maps what is patented at a high level so wahtuh-pho can be designed around it. No claim text, methods, or designs are reproduced. Nothing here is implementation guidance. This is a landscape scan, not a legal opinion or freedom-to-operate analysis — a patent attorney should review the final design.*

### Patents found (verified numbers only)

**Inline correction UX**
- **US11093710B2 — Google LLC** — *"Allowing spelling of arbitrary words."* Claims cover letting a user select a misrecognized term in a displayed transcript and speak a spelled-out correction, then merging it into an updated transcription via a letter-oriented language model.
- **US11721322B2 — Rovi Guides, Inc.** — *"Automated word correction in speech recognition systems."* Claims cover detecting that a follow-up utterance is a correction of earlier dictated text (e.g., via acoustic emphasis properties or a misrecognition word database) and replacing the targeted portion.
- **US8719014B2 — Apple Inc.** — *"Electronic device with text error correction based on voice recognition data."* Claims cover ranking suggested replacement words for mistyped words using recently recognized spoken-word data, with fully-automatic and semi-automatic correction modes.
- **US8280733** (assignee unverified) — *"Automatic speech recognition learning using categorization and selective incorporation of user-initiated corrections."* Claims cover inferring whether a user's text edit is a *correction* vs. a change-of-mind (using alternate-list selection, acoustic similarity, edit timing/scale) and selectively retraining from corrections only.

**Dictation rendering and editing UX**
- **US9361883B2 — Microsoft Technology Licensing, LLC** — *"Dictation with incremental recognition of speech."* Claims cover portion-by-portion incremental dictation with rendered text delivered as speech becomes available, uncertainty-modulated delivery rate, an n-best alternative-word correction menu, a punctuation-addition menu, and re-speaking a word as a replacement command.
- **US9865280B2 — Apple Inc.** — *"Structured dictation using intelligent automated assistants."* Claims cover structured dictation through an intelligent automated assistant (per the published abstract: systems/processes for producing structured content from dictated speech via an assistant).
- **US20120166192** (application, granted status not confirmed) — **Apple Inc.** — *"Providing text input using speech data and non-speech data."* Covers an editor combining speech-derived text and non-speech input with per-word sequence information.

**Automatic punctuation / capitalization**
- **US11645460B2 — Genesys Cloud Services Inc.** — *"Punctuation and capitalization of speech recognition transcripts."* Claims cover a two-stage trained model approach: preliminary training on a normalized corpus, then domain-specific training on conversational transcripts annotated with punctuation/capitalization, to predict both per word.

**Mic UX: PTT vs tap-toggle**
- **US20030028382A1** (application, not confirmed granted; assignee unverified — background cites Dragon NaturallySpeaking) — *"System and method for voice dictation and command input modes."* Covers single-button control of speech recognition: tap vs. press-and-hold vs. multi-state button depression to switch dictation/command modes and to activate/deactivate the microphone.

**Text insertion into other apps**
- **KR102713167B1 — Google LLC** — *"Voice-controlled content input into graphical user interfaces."* Claims cover a voice assistant invoked while a keyboard interface is displayed (e.g., inside a third-party app), deciding whether an utterance is dictation or alternative content, and issuing a command to the keyboard application to insert it into the input field via the keyboard-application or OS API. **This is the closest patent to wahtuh-pho's own-IME insertion path** — the claims hinge on the *assistant → keyboard-app → insert into another app's field* command flow.
- **US20090112572A1** (application, not confirmed granted; assignee unverified) — *"System and method for input of text to an application operating on a device."* Covers clipboard-mediated insertion: selecting/copying rendered text and pasting it into an active application — a generic OS clipboard flow.

**Error recovery**
- **US20150187350A1** (application, not confirmed granted; assignee unverified) — *"Apparatus and method for reporting speech recognition failures."* Covers analyzing failure causes (noise, volume, speech rate, transmission error) and reporting the failure reason to the user.

**Wake-word / hands-free UX**
- **US9928840B2 — Google LLC** — *"Hotword recognition."* Claims cover always-on hotword recognition comparing audio features of input speech to known hotword utterances without full speech recognition, combined with speaker verification and replay-attack protection.
- **US9361885B2 — Nuance Communications, Inc.** — *"Methods and apparatus for detecting a voice command."* Claims cover detection of voice commands (per title; detail limited in sources reviewed).

### Design-around map

**CROWDED — design around carefully (independent invention is risky):**
1. **Inline correction flows.** The densest area: spelled-out voice corrections (US11093710B2), correction-intent detection from a follow-up utterance (US11721322B2), n-best alternate-word menus and punctuation menus (US9361883B2), and correction learning (US8280733). Keep wahtuh-pho's correction UX mechanically distinct — plain text selection + re-dictation or keyboard editing is the generic, unclaimed baseline; avoid building menus/pipelines that mirror these claim topics (especially n-best alternates lists and "re-speak the word to replace it" semantics).
2. **Automatic punctuation/capitalization pipelines.** US11645460B2 covers a specific two-stage training approach; the *function* of auto-punctuation is standard, but design around the claimed training regimen and per-word punctuation/capitalization prediction structure.
3. **Assistant → IME → third-party-field insertion command flows.** KR102713167B1 sits closest to wahtuh-pho's own-IME insertion. It covers a *voice assistant commanding a keyboard app* to insert dictation into another app's field. The planned architecture should be reviewed against this claim topic specifically — the claims pivot on the assistant→keyboard-app command relationship, not on dictation generally.

**THINNER / OPEN — lower patent density found:**
1. **Text insertion via accessibility service.** No verified patent found covering Android AccessibilityService-based text insertion for dictation apps specifically (only the generic clipboard flow, US20090112572A1, and the Google assistant→keyboard command patent above). The platform-provided a11y APIs are unpatented surface area; this appears to be the safest insertion route.
2. **Mic toggle UX mechanics.** The PTT/button-mode space (US20030028382A1) is an old, ungranted application; a simple tap-to-toggle microphone with standard visual states has deep prior art and appears open.
3. **Error-recovery UX.** Only one application found (US20150187350A1, failure-reason reporting); generic recovery flows (retry prompts, "didn't catch that" states, fallback to offline model) appear sparsely claimed.
4. **Wake-word detection.** US9928840B2 covers Google's specific always-on hotword + speaker-verification + replay-protection combination, and US9361885B2 (Nuance) covers voice-command detection generally. A wake word is available territory if built without mirroring Google's combined detection+verification+replay-attack structure. (wahtuh-pho is currently not planned to be wake-word activated, which sidesteps this entirely.)

**Caveats:** claim topics were summarized, claim scope/breadth was not evaluated, and two listed applications are not confirmed granted. Flag for attorney review: the own-IME insertion architecture against KR102713167B1, and correction UX against US9361883B2 / US11093710B2 / US11721322B2.

---

## TRACK C — Invisible backend checklist (UX answers for every backend complexity)

**Principle:** the customer never needs to know about the backend. Every technical complexity below gets an invisible UX answer: the user sees a game moment ("New Game", "Battle HUD", "Quest Log"), never the plumbing. Banned vocabulary in anything the user can read: *model, engine, endpoint, quota, API, latency, model size, offline/online mode, transcription provider*.

Grounded in two sources: the competitor teardown (`docs/ANDROID_UX_TEARDOWN.md` — education-before-permission, try-it-yourself activation, graceful error/retry states everywhere, bubble as first-class surface) and our 8-bit identity (`docs/UI_CONCEPT_8BIT.md` — Fredoka/Baloo Tammudu 2 type, stepped chrome, XP meters, achievement toasts).

### 1. Offline vs cloud engine routing

**User sees:** Nothing at the moment of dictation. In the Options Menu there is one DIP-switch row: **"✈ Airplane-Friendly Mode — dictation works with zero network (uses phone brain only)"** vs default **"🌐 Smart Mode — best quality, works everywhere"** (teardown ref: their settings stay pragmatic, top-level bubble/overlay controls, not account plumbing). No per-utterance indication of which engine ran.

**User never sees:** That Smart Mode routes each utterance between local whisper.cpp and Deepgram cloud, or that a decision was made at all.

### 2. Per-utterance language routing (Telugu vs English)

**User sees:** In the New Game tutorial level, a single choice framed as character selection: **"Pick your voice hero: 🅔 English / 🅣 తెలుగు / 🅔+🅣 Both (I mix!)"** rendered as flag-sprite chips (`docs/UI_CONCEPT_8BIT.md`: language selection). After that, language just works — including mixed Telugu+English sentences, with no language-switch tap needed. First Telugu dictation earns the **"BILINGUAL BARD — achievement unlocked"** toast.

**User never sees:** Per-utterance language detection, confidence scores, or which language the utterance was routed as.

### 3. Model fallback (primary engine fails → backup)

**User sees:** The Battle HUD sprite does one quick spin (processing) slightly longer than usual, then delivers the text. If the fallback is slow, the bubble shows the stepped-waveform "still listening" pulse — the same sprite as a long utterance. On rare multi-second delays: **"One sec… polishing your words ✨"** — a patience line, not an error.

**User never sees:** That engine A failed and engine B took over; no "switching to backup" notice, no error badge.

### 4. Insertion path selection (accessibility service vs own IME vs nothing works)

**User sees:** During New Game, the accessibility power-up unlock screen says: **"UNLOCK: AUTO-TYPE 🖐 — lets your words land in any app. No peeking at your screen, ever."** (teardown ref: their legal-notice + minimal-event-types pattern for this sensitive permission; honest-notes rule: playful skin must not obscure serious education). If accessibility isn't granted, the Battle HUD shows a one-tap prompt: **"Tap here to become the keyboard ⌨"** — switching to our own IME, framed as a power-up, not a downgrade. If neither works, dictated text lands in the **Quest Log** with a chip: **"Copied! Long-press to paste 📋"** — and the user can paste manually.

**User never sees:** The words "accessibility service", "IME", "input method", or any explanation of insertion plumbing.

### 5. Cloud quota limits (free tier minutes running out / exhausted)

**User sees:** Cloud minutes are framed as an in-game resource, never a technical quota: a small **"☁ Cloud Juice"** meter in the Options Menu (styled as an MP/magic bar). At 25% remaining, the Quest Log shows a cheerful heads-up card: **"Running low on Cloud Juice! Local dictation stays free forever — top up or keep playing offline."** At zero, Smart Mode silently becomes local-only; the Battle HUD sprite winks and a toast reads: **"Cloud Juice empty — phone brain takes over 🧠 No charge, no interruption."** Topping up is **"Refill Cloud Juice"**, not "buy quota".

**User never sees:** The words quota, tier limits, minutes-as-billing, or any API/billing concept.

### 6. Network loss mid-dictation

**User sees:** The Battle HUD keeps the stepped-waveform "listening" sprite; the recording never stops and nothing is lost. When the words are ready (local fallback transcribes on-device), a tiny toast: **"Saved offline — sent it through anyway! 📼"** If the user was mid-utterance when signal dropped, dictation continues uninterrupted and the text arrives as normal.

**User never sees:** Connection errors, retry counters, "network unavailable" dialogs, or any mention of offline failover.

### 7. Latency (waiting for first words / final text)

**User sees:** The Battle HUD always answers *instantly* with motion: tap → sprite bounces + haptic tick (listening state, teardown ref: haptics on interactions). First words stream into the target app character-by-character as they're ready (their PTT tutorial's recording → processing → completed chain, but ours shows partial text live). If final polish takes >1s, the sprite's spinning-star "polishing" state appears with rotating one-liners: **"Polishing…", "Dotting the i's…", "Teaching the blob to spell…"** — never a spinner with a percentage.

**User never sees:** Latency numbers, "processing" spinners with technical states, buffering indicators, or partial-vs-final distinctions.

### 8. Secure fields (password inputs — refusal)

**User sees:** When the Battle HUD is summoned over a password/PIN field, the sprite covers its eyes with a **"🙈 Nope! I don't do passwords."** bubble, auto-dismisses, and nothing is recorded. Framed as the companion's personality quirk — a trust moment, not a limitation. (Teardown ref: their self-disabling in banking apps; ours makes it visible and charming.)

**User never sees:** Any explanation involving secure-window flags, input-type detection, or accessibility event filtering.

### 9. First-run permissions (mic, accessibility, battery exemption)

**User sees:** The New Game flow: each permission is a **power-up unlock** with a one-line cutscene card *before* the system dialog (teardown ref: education-before-permission, always — their strongest pattern, kept and reskinned). Mic: **"🎤 UNLOCK: EARS — I need to hear you to write for you."** Draw-over-apps: **"🫧 UNLOCK: FLOAT — lets your buddy hover over any app."** Battery exemption: **"🔋 UNLOCK: STAY AWAKE — stops your phone from napping your buddy mid-dictation."** (teardown ref: their OEM-specific battery guidance screens — ours keeps the guidance, loses the jargon). The tutorial-level dictation (their try-it-yourself activation pattern) happens *before* the trickiest permission, so value lands first.

**User never sees:** Permission rationales written in platform jargon ("foreground service", "battery optimization exemption", "draw over other apps" as a scary system phrase is softened by the preceding card).

### 10. Error recovery (engine crash, empty result, gibberish)

**User sees:** The Battle HUD sprite goes dizzy with **"HUH? I missed that — TRY AGAIN? 🔁"** (their error-with-retry state, reskinned). One tap retries; the previous audio is kept so the user doesn't re-speak. Empty result (silence detected): **"All quiet… tap and give me something to chew on 🎤"**. Gibberish/low-confidence result: text still lands, but shaky words get **HP bars** in the Quest Log editing view — tap a wounded word to "heal" it (`docs/UI_CONCEPT_8BIT.md`: word-level confidence as HP bars). After 2 failed retries: **"My ears are glitchy today — your words are safe in the Quest Log 📼"** and the raw audio is saved for later re-transcription.

**User never sees:** Crash reports, "engine error" codes, "no speech detected" system phrasing, confidence percentages, or any diagnostic text.

---

## The Invisible Backend Checklist

A designer checks each row before a release. Every answer must be **YES** — a single NO is a launch blocker.

| # | Backend complexity | Invisibility test (must answer YES) |
|---|---|---|
| 1 | Offline vs cloud engine routing | Can a user complete a full day of dictation without ever learning there are two engines? |
| 2 | Per-utterance language routing | Can a Telugu-English code-mixer dictate naturally without ever choosing a language per utterance? |
| 3 | Model fallback | If the primary engine fails 10 times in a row, does the user see anything other than slightly slower (but successful) dictation? |
| 4 | Insertion path selection | Can a user get text into every app without ever encountering the words "accessibility service" or "IME"? |
| 5 | Cloud quota limits | Does the word "quota" (or minutes-as-billing) appear nowhere in the UI, with exhaustion feeling like a game resource running out, not a paywall? |
| 6 | Network loss mid-dictation | Can the user dictate through a tunnel with zero error dialogs and zero lost words? |
| 7 | Latency | Does the bubble respond with motion + haptic feedback within one frame of every tap, with no technical "processing" state ever shown? |
| 8 | Secure fields | Does the refusal feel like the companion's personality (a trust moment), not a feature limitation? |
| 9 | First-run permissions | Is every system permission preceded by a one-line game-world reason, with the tutorial-level dictation completed *before* the scariest permission? |
| 10 | Error recovery | Do all three failure modes (crash, silence, gibberish) resolve to at most two taps with zero diagnostic text shown? |
| — | Vocabulary audit | Do the strings `model`, `engine`, `endpoint`, `quota`, `API`, and `latency` return zero hits in a full UI-string search of the app? |

**Global rule (the "hello" test):** a brand-new user who dictates the word "hello" in the tutorial level should experience: tap → bounce + tick → waveform → text appears → XP bar fills → achievement toast. If at any point that user could reasonably ask "what's an engine?", the checklist has failed.
