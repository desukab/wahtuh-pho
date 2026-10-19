# Competitor Architecture (Static Observation)

**Date:** 2026-10-19 · **Source:** static analysis of `com.wispr.flowapp`
v2.6.2 APK (apktool + class/manifest/resource inventory, ~46k classes across
5 dex files). No code copied, no secrets recorded, no execution, no network
probing. This describes *observable structure* in our own words.

**Integrity note:** the APK carries an APK-Signature-Scheme v2 block whose
signer certificate is **not** the Android debug key — its subject matches the
Google Play App Signing pattern, consistent with a genuine Play Store install.
No v1 (JAR) signature present. Package name verified as `com.wispr.flowapp`.

## Headline: cloud-native thin client

The single most important architectural fact: **there is no on-device ML.**
Native libraries in the APK are only Sentry (crash reporting) and an AndroidX
graphics helper. No TFLite, no ONNX Runtime, no Whisper, no audio-codec
libraries. The transcription pipeline is:

**Mic → audio batching → gRPC bidirectional stream → cloud → streamed
results with word alignments → accessibility-service text insertion**

## Client structure (by module, class counts)

| Module | Classes | Role (inferred from names) |
|---|---|---|
| ui | ~1,760 | All Compose screens (onboarding, bubble, home, settings, dictionary, snippets, payment, auth, LinkedIn, history) |
| flow | ~1,330 | Generated OpenAPI client for the management REST API |
| flowapp | ~920 | App shell: services, receivers, activities, DI setup |
| data | ~410 | Repositories: dictionary, snippets, preferences, privacy, styles, experiments (A/B), retention, Room DB, auth, enterprise, attribution |
| shared | ~225 | Shared utilities |
| payment | ~200 | Subscription logic over RevenueCat |
| transcription | ~150 | Dictation orchestration (see below) |
| auth | ~130 | WorkOS auth (Apple/Google/Microsoft/Email/SSO) |
| design | ~130 | Design system |
| grpc / network | ~35 | gRPC channel setup, dynamic endpoint resolution |
| history, connectivity, context, review, support, linkedin | small | Feature slices |

## Transcription pipeline (static component inventory)

- **Transport:** gRPC bidirectional streaming. Generated stubs live in
  `flow_api/transcribe/v1`: request = init + audio payload chunks; response =
  candidate text **with word-level alignments** (streamed partials).
  A second generated surface (`flow_api/v1`, `flow_api/format`) and a
  1,300-class OpenAPI client cover the non-streaming management API.
- **Endpoint:** not hardcoded — a "get gRPC URL" component resolves it
  dynamically, and request builder assembles init payload + metadata
  (auth, device, experiment flags).
- **Audio:** Media3-based capture; an audio batcher chunks mic input; a
  head-silence tracker trims leading silence; audio-route events
  (device change/removal) are handled; local audio file storage exists
  (subject to the retention setting).
- **Session state machine:** Idle → Initializing → Listening → Processing →
  (Error | Dismissed), managed by a dedicated dictation state manager with
  init/processing timeouts and auto-reset.
- **Reliability:** a dictation reliability gate, a heartbeat monitor on the
  stream, and per-stage timeouts. The client clearly assumes the network can
  fail mid-dictation.

## Text insertion engine (the moat)

Two complementary mechanisms:

1. **Accessibility service** (`FlowAccessibilityService` + a bridge service).
   Minimal event subscription (window changes only), 10 ms timeout, gesture
   support. Insertion stack: focused-node finder → surrounding-text probe →
   node write composer, with an "insertion arm" abstraction and keep-alive
   strategies for the first text insertion.
2. **Their own IME** (`WisprInputMethod`) — a fallback input method for
   targets the accessibility path can't reach.

Notable hardening (all from class names):
- **Per-app compatibility shims** — known lists of apps with null surrounding
  text and apps needing Compose-specific hints
- **Banking-app detector** — the service stands down in banking apps
- **Web-editor policy** — special handling for web text fields
- **Context-awareness manager** — reads the foreground app to adapt behavior
- **Crash guards and health monitors** around the accessibility framework
- **Shake detector** wired into the accessibility layer

## Background execution (keeping the bubble alive)

- `FlowService`: foreground service, types microphone + specialUse
- `MainServiceWatchdog`: monitors the main service
- `FlowRecoveryJobService`: JobService-based resurrection after process death
- `BatteryOptimizationMonitorService`: watches OEM battery-optimization state
- `BootCompletedReceiver` + `ScreenUnlockReceiver`: restart triggers
- Permission `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + full OEM battery-guidance
  screens in onboarding — they fight the OEM task killers as a product feature

## Monetization & telemetry SDK inventory (from package names)

- **RevenueCat** (~5,100 classes — the largest third-party SDK): paywall UI,
  customer center, checkout workflows
- **PostHog, Segment, AppsFlyer, Firebase** (Analytics/Messaging/Installations):
  product analytics, attribution, push
- **Sentry** (incl. session replay + native libs): crash/error monitoring
- **Customer.io**: push messaging
- **Supabase**: small footprint (16 classes)
- **PairIP**: license/anti-tamper check
- Bundled fonts: EB Garamond + Figtree (variable), Roboto Medium for numerals

## What we could NOT determine (obfuscation / static limits)

- API endpoint URLs, auth token mechanics, audio codec/bitrate, retry
  backoff values, exact gRPC service/method names (proto stubs are present
  but method descriptors are stringly-typed at runtime)
- Any secret, key, or certificate value (not recorded, per policy)
- Server-side model identity or pipeline — nothing about it ships in the
  client by design
- Exact onboarding step *order* (the state machine's transition map is
  data-driven; the step inventory is confirmed, sequencing is inferred)

## Implications for wahtuh-pho (design input, not copied design)

1. Our local-first architecture is the inverse of theirs — the interesting
   takeaway is *where the complexity lives*: for them it's stream reliability
   and insertion compatibility; for us it will be on-device inference
   performance and the same insertion-compatibility problem.
2. The insertion engine's hardening (per-app shims, banking-app stand-down,
   web-editor policy) is domain knowledge worth re-deriving independently —
   any serious Android dictation app hits these same walls.
3. Their onboarding state machine (education → try-it-yourself → tutorial →
   permissions with education screens) is a *pattern* we can learn from
   structurally while designing our own flow and visuals from scratch.
