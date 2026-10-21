# Wispr Flow — Public Research Notes (Clean Room)

**Purpose:** Feature/behavior reference for building Wahtuh Pho (open-source, offline-first dictation). Clean-room research only: everything below is paraphrased from publicly accessible sources. No APK downloaded, no proprietary source copied, no branding reused.

**Research date:** 2026-10-19 (all pages fetched/searched on this date unless noted).

---

## 1. What Wispr Flow is

An AI-powered voice dictation tool from Wispr AI, Inc. The core loop: activate with a hotkey (desktop) or floating bubble (Android), speak naturally, release — and cleaned-up, punctuated text is inserted into whatever text field the cursor is in. Beyond raw transcription it applies an AI post-processing layer: removing filler words, applying punctuation/capitalization/formatting, resolving self-corrections ("5 pm, no actually 6 pm" → "6 pm"), and adjusting tone based on which app is being dictated into (formal in email, casual in messaging).

Sources: https://wisprflow.ai/features · https://www.gladia.io/blog/best-wispr-flow-alternatives · https://willowvoice.com/blog/wispr-flow-vs-willow-voice-dictation

Company background (third-party reported): founded 2021 in San Francisco by Tanay Kothari and Sahaj Garg, described as ex-Apple and ex-Meta engineers; reported total funding of ~$81M.
Source: https://sidsaladi.substack.com/p/wispr-flow-101-the-complete-guide

---

## 2. Platform support

| Platform | Status (public info) | Source |
|---|---|---|
| Mac | Available (reported as first launch, Oct 2024) | https://max-productive.ai/ai-tools/wispr-flow/ |
| Windows | Available (announced by CEO on Product Hunt, "finally available on Windows") | https://www.producthunt.com/p/wisprflow/wispr-flow-for-windows |
| iPhone / iOS | Available (third-party keyboard app) | https://docs.wisprflow.ai/articles/7453988911-set-up-the-flow-keyboard-on-iphone |
| Android | Available since Feb 2026; requires Android 13+; ~1.5M installs, ~4.5★ from ~10k ratings (AppBrain data, updated 2026-10-09) | https://docs.wisprflow.ai/articles/2809924024-android-download-installation-guide · https://www.appbrain.com/app/wispr-flow-ai-voice-to-text/com.wispr.flowapp |

The official features page lists exactly four platforms: "Mac, Windows, iPhone, Android."
Source: https://wisprflow.ai/features

Official marketing claim on speed: "4× faster than typing" (no methodology given — treat as marketing).
Source: https://wisprflow.ai/features

---

## 3. Dictation flow (observed behavior)

**Desktop (Mac/Windows):** Hold a hotkey (e.g. Fn on Mac), speak, release; cleaned text appears at the cursor. A hands-free mode (double-tap to start/stop) is also described by reviewers. On desktop it works in "any app with a text field" — named examples on the official site: Notion, Gmail, Google Docs, WhatsApp, Cursor.
Sources: https://wisprflow.ai/features · https://sidsaladi.substack.com/p/wispr-flow-101-the-complete-guide

**Android:** A floating "Flow Bubble" appears over other apps; tap and hold to dictate; works "with the keyboard you already use" (i.e. it is NOT a replacement keyboard/IME — insertion happens via an Accessibility service). Tapping the bubble requests mic permission ("While using the app" or "Only this time").
Source: https://docs.wisprflow.ai/articles/8858845757-setup-wispr-flow-on-android-android-settings

**iPhone:** Dictation goes through a third-party custom keyboard ("Wispr Flow" keyboard added in Settings → General → Keyboard). Requires "Allow Full Access". Without internet, "No Network" replaces the dictation controls.
Source: https://docs.wisprflow.ai/articles/7453988911-set-up-the-flow-keyboard-on-iphone

**Session limits (official docs):** Mac/Windows warn at 19 minutes and stop at 20 minutes per session; Android warns at ~3:59 and stops/submits at ~4:59.
Source: https://docs.wisprflow.ai/articles/6409258247-starting-your-first-dictation

**Account required:** Sign-in with email and password is required before use; account-based (sign-in methods can't be switched later).
Source: https://docs.wisprflow.ai/articles/2809924024-android-download-installation-guide

---

## 4. Auto-formatting / AI editing

Officially described editing behaviors (all from the official features page):

- Filler word removal ("um", "uh", repeated words) — labels: "Remove fillers", "Backtrack"
- Automatic punctuation, capitalization, paragraph/list formatting — "Auto punctuation", "Numbered lists"
- Real-time correction understanding: "cleaning up filler words, formatting lists, catching punctuation, and understanding corrections in real time"
- Spells uncommon names right using surrounding context
- Whisper mode: works when the user whispers, for quiet/shared spaces
- Styles (tone adaptation per app): "Let Flow shape your tone based on where you're writing… Go from formal in docs, to casual in messages, to enthusiastic in emails." Note: **"Only available in English and on desktop."**
- Developer features: "Flow understands syntax, file names, and code formatting" — file tagging, syntax awareness, dev jargon

Source: https://wisprflow.ai/features

Reviewers additionally describe: course correction handling ("let's meet Tuesday, wait no, Friday" → "Let's meet Friday"); camelCase/snake_case dictation ("camel case user name" → "userName"); app-aware formatting (Gmail/Outlook → professional; Slack → casual; VS Code/Cursor → code-aware).
Sources: https://sidsaladi.substack.com/p/wispr-flow-101-the-complete-guide · https://pasqualepillitteri.it/en/news/556/ai-voice-dictation-wispr-flow-superwhisper-compared

---

## 5. Voice commands and editing

Two layers, per public documentation:

**a) Inline commands while dictating:** Users can say things like "make that a bullet list" or "start a new paragraph" mid-dictation without touching the keyboard.
Source: https://www.boostwithai.se/voice-to-text-ai-wispr-flow/

**b) Command Mode (Pro-only):** Highlight text, activate Command Mode (desktop shortcut, e.g. hold Fn+Ctrl on Mac per one review), speak an instruction — "make this shorter", "turn this into bullet points", "delete the last sentence", "make this more formal" — and the AI rewrites the selection. Requires a paid plan or active trial.
Sources: https://tldv.io/blog/wisprflow/ · https://max-productive.ai/ai-tools/wispr-flow/ · https://droidcrunch.com/wispr-flow-review/

**Snippets:** Voice-activated shortcuts that expand into pre-written text blocks (e.g. say "Calendar" and a Calendly link is inserted). Personal snippets sync across devices; Teams plans get shared snippets.
Source: https://wisprflow.ai/features

**"Hey Flow" wake word:** Reported by multiple reviews as a hands-free trigger (Pro tier). Not confirmed on any official page found — treat as third-party-reported.
Sources: https://max-productive.ai/ai-tools/wispr-flow/ · https://www.gladia.io/blog/best-wispr-flow-alternatives

---

## 6. Transcript history

- Past dictations appear in "Recent activity" or "History" when storage settings allow (official docs).
- Official privacy controls distinguish: (i) local "dictation history" saved on the device (keep / auto-delete after 24h / never save — desktop), (ii) "Dictation cloud storage" (transcripts, audio and history stored on Wispr servers so data is available on all devices), (iii) mobile history managed separately.
- Desktop recordings older than 14 days have audio removed but transcripts retained, when stored normally.

Sources: https://docs.wisprflow.ai/articles/6409258247-starting-your-first-dictation · https://wisprflow.ai/privacy

One forensic third-party write-up of the Mac client reported a local `History` table with thousands of entries and embedded audio — included here as a caution about what "history" can mean in practice; it is not an official feature description.
Source: https://www.getvoibe.com/resources/is-wispr-flow-safe/

---

## 7. Permissions and keyboard/IME integration

**Android (official help center):**
- Microphone — "While using the app" or "Only this time"
- Display over other apps — required, cannot be skipped (shows the floating bubble)
- Accessibility service — required, cannot be skipped; used to insert dictated text. The accessibility disclosure lists access to: on-screen text, text-field layout, app/site name or URL, limited text-box contents, input-field types. Flow does NOT insert into password or credit-card fields.
- Battery optimization exemption recommended on all manufacturers to keep the background service alive
- Notifications — optional (status alerts only; does not read other apps' notifications)
- Internet connection required at all times; offline shows "No internet. Try again later."

Sources: https://docs.wisprflow.ai/articles/8858845757-setup-wispr-flow-on-android-android-settings · https://docs.wisprflow.ai/articles/6344532666-android-system-requirements

**iOS:** Microphone + keyboard "Full Access" (required for transcription; lets the keyboard work across apps).
Source: https://docs.wisprflow.ai/articles/7453988911-set-up-the-flow-keyboard-on-iphone

**Desktop (third-party summarized):** Mac needs Microphone + Accessibility (+ Screen Recording for screen-reading features); Windows needs the microphone only.
Source: https://spokenly.app/blog/wispr-flow-review

**Keyboard/IME approach summary (Android):** Wispr Flow is deliberately NOT an input-method editor on Android — the user keeps their existing keyboard; insertion is done by the floating bubble plus the Accessibility service. On iPhone it IS a custom keyboard.

---

## 8. Transcription: on-device vs cloud, languages, latency

**On-device vs cloud:** Cloud-only. Official data-controls page states verbatim: "Transcription always occurs on the cloud. This is the best way for us to provide accurate, low latency transcription." There is no on-device/offline mode at any price. Every dictation requires an internet connection.
Source: https://wisprflow.ai/data-controls (Last Updated: August 18, 2026)

**Accuracy claims:** The Play Store listing calls it "World's most accurate voice-to-text" (marketing claim, no methodology). Independent reviewers report roughly 95–97% accuracy in quiet conditions with a good mic, lower with laptop mics or noisy environments. These are third-party measurements, not official benchmarks.
Sources: https://play.google.com/web/store/apps/details?id=com.wispr.flowapp&hl=en_US · https://max-productive.ai/ai-tools/wispr-flow/

**Languages:** Officially "100+ languages". The Play Store listing names Español, Français, 中文, ไทย, हिन्दी, and **Hinglish** (Hindi-English mix). Android setup lets users select multiple languages; auto-detect of spoken language is described by reviewers.
Sources: https://wisprflow.ai/features · https://play.google.com/web/store/apps/details?id=com.wispr.flowapp&hl=en_US · https://docs.wisprflow.ai/articles/2809924024-android-download-installation-guide

**Telugu:** No official page found names Telugu specifically. One independent reviewer lists Telugu among available regional Indian languages (alongside Hindi, Bengali, Assamese, Tamil); a separate article about Wispr's India push describes Hinglish code-switching support as shipped and says the company is "planning deeper localization, including support for regional languages like Tamil, Telugu, Marathi, and Bengali" — implying full Telugu support was still planned at that article's date. Status: **partially verified / conflicting third-party reports; no official confirmation found.**
Sources: https://kripeshadwani.com/wispr-flow-review/ · https://theempiremagazine.com/voice-ai-in-india-wispr-flow/

**Code-switching:** Officially confirmed only for Hinglish (named on the Play Store listing). No official mention found of Telugu–English (Tenglish) code-switching. Status: **unverified**.
Source: https://play.google.com/web/store/apps/details?id=com.wispr.flowapp&hl=en_US

**Latency:** No official numeric latency claim found. The official line is "low latency transcription" (qualitative). A competitor's comparison page claims ~700ms processing latency for Wispr Flow — competitor-sourced, treat as **unverified**. Reviewers describe delays only on poor connections.
Sources: https://wisprflow.ai/data-controls · https://willowvoice.com/blog/wispr-flow-vs-willow-voice-dictation (competitor claim)

---

## 9. Pricing / plan structure

Prices below are from multiple third-party reviews; one source (getvoibe, May 2026) states they were verified against wisprflow.ai/pricing. The official pricing page was not separately fetched — treat figures as third-party-reported but consistent across 5+ sources.

| Plan | Price | Limits / features |
|---|---|---|
| Flow Basic (free) | $0 | 2,000 words/week on Mac & Windows; 1,000 words/week on iPhone; unlimited on Android (described as limited-time). Personal dictionary, snippets, 100+ languages, privacy controls. |
| Flow Pro | $15/mo monthly, or $12/mo billed annually ($144/yr) | Unlimited dictation all platforms, Command Mode, cross-device sync, "Hey Flow" wake word, priority support, early access |
| Flow Teams | $12/user/mo monthly, $10/user/mo annual, min 3 seats | Everything in Pro + shared dictionary & snippets, centralized billing, admin controls |
| Enterprise | Custom | SSO/SAML, dedicated support, usage dashboards, enforced data controls |

Additional details (third-party): 14-day Pro trial on every new account, no credit card required; free word cap resets Sunday at midnight PT; student discount (~50% off); HIPAA BAA signing available on all plans at no extra cost.

Sources: https://www.getvoibe.com/resources/wisprtype-vs-wispr-flow/ · https://max-productive.ai/ai-tools/wispr-flow/ · https://www.toolworthy.ai/tool/wisprflow-ai · https://iconpolls.com/blogs/wispr-flow-review-2026-ai-voice-dictation-app-login-pricing-and-user-experience-and-faqs · https://tldv.io/blog/wisprflow/

---

## 10. Privacy posture (exact claims from official sources)

**Processing location:** Audio is captured on the device, sent to Wispr servers over TLS 1.2+, transcribed in real time in the cloud, then deleted as soon as it's transcribed — unless "Dictation cloud storage" is on. There is no option for on-device transcription.
Source: https://wisprflow.ai/privacy · https://wisprflow.ai/data-controls

**User controls (official, from wisprflow.ai/privacy):**
- *Improve the model for everyone* — lets Wispr use audio, transcripts and edits to train models. "Turn it off and nothing you say is ever used to train our models." (On by default for individual Free/Pro accounts per third-party reviews and the Android setup docs, where it is "pre-selected for new users"; off and locked on Enterprise.)
- *Dictation cloud storage* — "Stores your dictation transcripts, audio, and history on Wispr servers… Turn it off and your dictation stays on your device. Audio is discarded once it's transcribed." (A separate toggle from model training — turning off training does not turn off storage.)
- *Context awareness* — "Reads limited text from the app you're dictating into, so names and jargon come out right. Turn it off and Wispr Flow transcribes without reading anything on your screen."
- *Local data storage* — dictation history on the device: keep, auto-delete after 24h, or never save.
- Enterprise/Growth: model training off by default and locked.

Sources: https://wisprflow.ai/privacy · https://wisprflow.ai/data-controls · https://docs.wisprflow.ai/articles/8858845757-setup-wispr-flow-on-android-android-settings

**Data collected (legal Privacy Policy, https://wisprflow.ai/privacy-policy):**
- Personal Data: email address, first/last name, phone number (if provided), cookies, Usage Data
- Usage Data: IP address, device type, browser, pages visited, timestamps, device language, device identifiers, system logs, performance metrics, "the application used for dictation"; on mobile: device type, unique ID, mobile IP, OS, browser type
- Customer Content: audio inputs (which may contain personal info spoken aloud), meeting data, contextual on-screen content (only with Context Awareness enabled; opt-out anytime)
- Model training: only on pseudonymized text/transcripts/outputs/corrections "if you opt to share your content with us for model training"
- Meeting audio: "not used for model training unless expressly disclosed and enabled"; Google Calendar/Gmail data "never included in any user-enabled model training program"
- Payments: processed by Stripe
- Also states use of data for digital advertising/marketing, and third-party analytics (e.g. Google Analytics) on the website

**Compliance:** SOC 2 Type II and ISO 27001:2022 certifications claimed by Wispr (referenced by Wispr materials and reviews; one review notes the SOC 2 report was "in progress" in an August 2026 trust-center FAQ). HIPAA BAA signing offered on all plans. Note: some third-party write-ups (getvoibe, iconpolls) allege past incidents around data practices and recommend reading the current policies directly — included for completeness, not as verified fact.
Sources: https://wisprflow.ai/privacy (badges) · https://spokenly.app/blog/wispr-flow-review · https://iconpolls.com/blogs/wispr-flow-review-2026-ai-voice-dictation-app-login-pricing-and-user-experience-and-faqs

**Key privacy takeaway for parity planning:** Wispr Flow offers *zero-retention configuration* but is architecturally cloud-only — audio always leaves the device. The differentiator for Wahtuh Pho is transcription that never leaves the device at all.

---

## 11. Android specifics (for the Wahtuh Pho MVP)

- Install via Google Play; developer listed as Wispr Inc / Wispr AI, Inc.; genuine package id `com.wispr.flowapp` (Wispr has warned about fake listings impersonating Flow — worth knowing for brand-safety, not for copying).
- Source: https://whisperai.com/blog/wispr-flow · https://www.appbrain.com/app/wispr-flow-ai-voice-to-text/com.wispr.flowapp
- Play Store description pitches: accessibility use cases (motor impairments, Parkinson's, arthritis, RSI, dyslexia, ADHD, stuttering, visual impairments), "maintains a lightweight background service purely to ensure it's instantly available", works inside ChatGPT, WhatsApp, Instagram, Slack, Gmail.
- Source: https://play.google.com/web/store/apps/details?id=com.wispr.flowapp&hl=en_US
- Review coverage: Fast Company called it "the dictation upgrade Android users deserve"; minor UX complaints about the floating bubble occasionally covering screen content.
- Source: https://www.fastcompany.com/91499738/wispr-flow-dictation-android-gboard-alternatives
- ARM64 support: not explicitly claimed anywhere found; the app is distributed via Google Play (which universally ships arm64-v8a builds). Status: **unverified / inferred only.**

---

## 12. Items NOT verified

1. Exact official pricing numbers — official pricing page not fetched; figures above are from 5+ consistent third-party sources (one claims verification against wisprflow.ai/pricing, May 2026).
2. Any numeric latency/SLA claim — none found officially.
3. "Hey Flow" wake word — reported only by third-party reviews, not found on official pages.
4. Telugu language availability — conflicting third-party reports; not on official pages.
5. Telugu–English code-switching — no evidence found anywhere.
6. ARM64 Android build specifics — inferred from Play Store distribution only.
7. Whether advanced features (Dictionary, Snippets, Styles) work on Android — one review says they are "desktop-first" and "still rolling out" on Android.
8. Past privacy incidents/controversies described by third-party sites — reported, not independently verified; excluded from factual claims above.
9. Current SOC 2 audit status — Wispr materials claim SOC 2 Type II; one third-party review (Aug 2026) said the trust center listed the report as "in progress". **Unresolved.**

---

## 13. Source log

All checked 2026-10-19.

Official:
- https://wisprflow.ai/features (fetched full text)
- https://wisprflow.ai/privacy (fetched via search excerpt)
- https://wisprflow.ai/data-controls (fetched via search excerpt; last updated Aug 18, 2026 per page)
- https://wisprflow.ai/privacy-policy (fetched, lines 0–174)
- https://docs.wisprflow.ai/articles/2809924024-android-download-installation-guide
- https://docs.wisprflow.ai/articles/8858845757-setup-wispr-flow-on-android-android-settings
- https://docs.wisprflow.ai/articles/6344532666-android-system-requirements
- https://docs.wisprflow.ai/articles/6409258247-starting-your-first-dictation
- https://docs.wisprflow.ai/articles/7453988911-set-up-the-flow-keyboard-on-iphone
- https://play.google.com/web/store/apps/details?id=com.wispr.flowapp&hl=en_US (fetched via search excerpt)
- https://www.producthunt.com/p/wisprflow/wispr-flow-for-windows (CEO announcement)

Press / independent:
- https://www.fastcompany.com/91499738/wispr-flow-dictation-android-gboard-alternatives
- https://www.appbrain.com/app/wispr-flow-ai-voice-to-text/com.wispr.flowapp (stats, updated 2026-10-09)

Reviews / comparisons (third-party claims, attributed as such):
- https://www.gladia.io/blog/best-wispr-flow-alternatives
- https://willowvoice.com/blog/wispr-flow-vs-willow-voice-dictation (competitor; latency claim unverified)
- https://max-productive.ai/ai-tools/wispr-flow/
- https://www.toolworthy.ai/tool/wisprflow-ai
- https://www.getvoibe.com/resources/wisprtype-vs-wispr-flow/ (pricing verified May 2026 vs wisprflow.ai/pricing)
- https://www.getvoibe.com/resources/is-wispr-flow-safe/ (forensic write-up)
- https://spokenly.app/blog/wispr-flow-review
- https://solobrief.co/reviews/wispr-flow-review-freelance-writers-2026/
- https://tldv.io/blog/wisprflow/
- https://droidcrunch.com/wispr-flow-review/
- https://awesome.tools/apps/wispr-flow (pricing checked Sep 8, 2026)
- https://iconpolls.com/blogs/wispr-flow-review-2026-ai-voice-dictation-app-login-pricing-and-user-experience-and-faqs (pricing verified Apr 2026)
- https://whisperai.com/blog/wispr-flow (fake-listing warning; pricing)
- https://kripeshadwani.com/wispr-flow-review/ (Telugu/Hinglish mention)
- https://theempiremagazine.com/voice-ai-in-india-wispr-flow/ (Hinglish shipped; Telugu localization "planned")
- https://sidsaladi.substack.com/p/wispr-flow-101-the-complete-guide (company background; feature walkthrough)
- https://www.boostwithai.se/voice-to-text-ai-wispr-flow/
- https://pasqualepillitteri.it/en/news/556/ai-voice-dictation-wispr-flow-superwhisper-compared
- https://ai-productivity.pages.dev/guides/wispr-flow-voice-dictation-guide/ (Command Mode examples)
- https://hustlyst.substack.com/p/wisprflow
- https://mundobytes.com/en/How-to-use-Wispr-Flow-to-turn-your-voice-into-real-productivity/
- https://maketecheasier.com/wispr-flow-ai-voice-dictation-tool/
- https://readwise.io/reader/shared/01jyf1ayjz6wkwfy13c9c9kseq/
- https://iseoai.com/wispr-flow/
- https://www.sales-hacking.com/en/outils/wispr-flow
- https://www.productcool.com/product/wispr-flow-for-android

---

## 14. Product takeaway for Wahtuh Pho

### Why people pay for Flow

The paid value is not raw transcription. It is the removal of the editing loop:

1. **Zero-friction capture** — one bubble/hotkey, works in the field you are already in, no copy/paste or app switching.
2. **Send-ready output** — punctuation, capitalization, filler removal, backtracking, lists, and names handled automatically.
3. **Destination-aware tone** — formal in email, casual in chat, code-aware in editors (desktop/English only today).
4. **Personal vocabulary and snippets** — the tool learns how you write and speak.
5. **Reliability and speed** — the cloud model is consistently good and the interaction is polished.
6. **A mental shortcut** — users stop thinking of it as “speech recognition” and start thinking of it as “typing, but faster.”

### What we adopt

- Bubble only when an editable field is focused and the keyboard is open, so it stops covering content when it is not useful.
- VAD-driven capture: no-speech rejection, pre/post roll, pause segmentation, and a debounced start.
- Deterministic cleanup that works offline: fillers, punctuation, dictionary, snippets, email dictation, styles.
- Optional text-only AI polish, with the raw and cleaned result both preserved.
- Fast correction paths: verified insertion, clipboard fallback, notification undo.
- Personality and offline play: the arcade is a real product surface, not a settings page.

### What we deliberately do not copy

- Cloud-only transcription. Local-only is the default and cannot silently fall back.
- Screen capture or broad on-screen context reading. Future app-aware tone must use only the foreground package name.
- A mandatory account, subscription, or word quota.
- A proprietary pipeline that hides what changed. Cleanup should be inspectable and reversible.

### The defensible position

**“The voice keyboard you own.”**

Private by architecture, not by promise: on-device ASR, bring-your-own-key cloud, text-only cleanup, no screen capture, no account, no subscription. The arcade shell makes the privacy-first choice feel intentional and fun rather than like a compromise.

**App-aware tone without screen capture** is now implemented: the app uses only the foreground package name to choose a tone preset (chat vs. email vs. notes), never the field contents. That captures part of Flow's context advantage while keeping the privacy story honest. An off-by-default **Field Context** switch can additionally extract names/code terms from the focused field for AI polish; it never sends surrounding prose and never reads passwords.
