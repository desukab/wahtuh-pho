# Android UX Teardown — Competitor Dictation App

**Date:** 2026-10-19 · **Source:** static analysis of the competitor's Android APK
(package `com.wispr.flowapp`, version 2.6.2 / versionCode 170, extracted from a
licensed install on the user's own device). **Method:** apktool decode +
class/manifest/resource inventory. No code, artwork, or text was copied — this
document describes structure and patterns in our own words to inform
wahtuh-pho's original design.

**Obfuscation note:** this is a release build. Class and method names inside
the app's own packages are partially obfuscated, so screen-level detail below
comes from module/class *names* that survived (UI composable names, onboarding
step names, manifest components, resource keys) — not from reading screen
logic. Anything inferred is marked as such.

## Screen inventory (from UI module structure)

The app is 100% Jetpack Compose (Material3). Recognizable screen areas:

| Area | What it appears to be |
|---|---|
| Onboarding (~290 classes, largest area) | Multi-step first-run flow (see below) |
| Payment (~180) | Paywall, plan selection, upgrade prompts |
| Accessibility setup (~160) | Guides for enabling the accessibility service |
| Home (~155) | Main screen: education cards, insights pager, transcript log card, progress card, feedback prompt, review nudge |
| Auth (~125) | Sign-in (email, Google, Apple, Microsoft, SSO via WorkOS) |
| Bubble (~115) | Floating overlay bubble UI and its states |
| Settings (~65) | Bubble opacity/size, languages, local data storage, general settings |
| Dictionary (~60) | Custom vocabulary management (personal + team-shared filters) |
| Snippets (~60) | Voice text-expansion shortcuts |
| Dashboard (~55) | Usage stats/insights |
| LinkedIn (~47) | LinkedIn integration (OAuth via deep link) |
| Account (~40) | Account/plan management |
| History (~10) | Transcript history list |
| Support / legal / feedback | Help, legal screens, feedback + transcription-issue reporting |

## Key user flows

### 1. Onboarding — a state machine, not a slideshow
Onboarding is implemented as an explicit step state machine (first step +
transition map + progress map) with three step-order variants (default,
latching, pre-login intro). Reconstructed step sequence:

1. **Intro education** — what the product is, bubble intro, visibility explainer
2. **Try-it-yourself value demo** — the user dictates a *real email* and a
   *real note* during onboarding, with per-attempt states
   (tap → recording → processing → completed / failed / skippable)
3. **Push-to-talk tutorial** — notice → bar-ready → recording → processing →
   completed, with dedicated error/failed states
4. **Privacy notice** — shown *before* any permission request
5. **Language setup** — pick dictation languages
6. **Permission education + requests**, each with its own education screen:
   - Accessibility service (with a legal notice screen and a picture-in-picture
     video guide for finding the setting)
   - Draw-over-apps ("display on screen") permission
   - Background execution / battery-optimization exemption (with OEM-specific
     battery guidance screens)
7. **Wrap-up** — notification opt-in, attribution screens, completed notice

Pattern worth learning: **permissions are never asked cold** — every system
permission gets an education screen first, and the trickiest one
(accessibility) gets a legal notice plus a video guide. The try-it-yourself
dictation inside onboarding is the strongest activation pattern: the user
experiences the core value before seeing a paywall.

### 2. Dictation overlay (the bubble)
The floating bubble (requires draw-over-apps permission) is the primary
dictation surface:

- **Modes:** push-to-talk bubble and a tap-toggle variant
- **States:** idle, listening (live waveform visualization), processing
  (animated spinner), error, error-with-retry, repair, snoozed
- **Behavior:** draggable to any screen position (persisted horizontal/vertical
  placement), haptic feedback on interactions, auto-reveal logic, a watchdog
  that monitors bubble visibility, snooze option
- Dictation itself runs as a foreground service (microphone + special-use
  types) with a watchdog service, a recovery job service, and
  boot/unlock receivers to resurrect it

### 3. Transcript editing & history
- Transcript log card on home; dedicated history screen
- Issue-reporting flow for bad transcriptions (feedback loop into their models)
- Local data-storage setting (keep / auto-delete choices)

### 4. Settings
Bubble opacity and size, dictation languages, local data retention, account,
support, legal. Notably *pragmatic*: the two most prominent settings are
about the overlay bubble itself, not the account.

## Permission usage (from manifest)

| Permission | Why (inferred from structure) |
|---|---|
| RECORD_AUDIO | Dictation capture |
| FOREGROUND_SERVICE + MICROPHONE + SPECIAL_USE | Foreground dictation service (Android 14 types) |
| SYSTEM_ALERT_WINDOW | Floating bubble overlay |
| POST_NOTIFICATIONS | Dictation status/limit notifications |
| RECEIVE_BOOT_COMPLETED | Resurrect service after reboot (plus a screen-unlock receiver) |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Battery whitelist; paired with OEM battery-guidance screens |
| VIBRATE | Haptic feedback on bubble interactions |
| BIND_ACCESSIBILITY_SERVICE (service-level) | Text insertion engine |
| BILLING / CHECK_LICENSE | Play Billing via RevenueCat + license check SDK |

Accessibility service config is *minimal and deliberate*: it listens only to
window-change events, with flags for view IDs, key-event filtering,
interactive-window retrieval, and IME interaction; gesture support enabled;
marked as an accessibility tool; 10 ms notification timeout (very responsive).

## Notable UX patterns (structure-level)

1. **Education before permission, always.** No system permission is requested
   without a preceding explainer screen.
2. **Activation inside onboarding.** Real dictation (email + note) happens
   before the user reaches the home screen.
3. **Graceful degradation everywhere.** Every async surface (tutorial,
   dictation, dictionary, snippets) has explicit error / retry / skipped
   states — not just happy paths.
4. **The overlay is a first-class citizen.** Bubble size/opacity are top-level
   settings; the bubble has snooze, repair, and visibility-watchdog behavior.
5. **Trust signals for a sensitive permission.** Legal notice for
   accessibility access, self-disabling in banking apps, minimal event types.
6. **Paywall entry points are numerous** (plan page entry activity + launcher
   alias, upgrade prompts, word-limit sheet) but onboarding leads with value,
   not price.
