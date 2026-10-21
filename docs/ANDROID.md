# Android Layer — Design Documentation

**Mission:** Wahtuh Pho — open-source, Rust-powered offline voice dictation (Wispr Flow alternative).
**Scope of this document:** everything Android-specific. Core logic (audio pipeline,
transcription orchestration, normalization, providers, config) lives in Rust; this
document describes only the thin Kotlin/Jetpack Compose shell that talks to the
Rust core through a JNI bridge (`wahtuh-jni`).

> **Phase C2 status:** design documentation only. No compiling code was produced —
> neither the Rust toolchain nor the Android NDK is present in this environment
> (see **Toolchain Blocker** at the end).

---

## 1. Guiding principle: Kotlin stays thin

Kotlin is glue, not logic. The rule of thumb: **if a line of code would also be
needed on desktop Linux or iOS, it belongs in Rust, not Kotlin.** Kotlin exists
only where the Android OS forces our hand:

| Stays in Kotlin (Android-only concerns) | Lives in Rust (shared core) |
|---|---|
| `AudioRecord` setup, buffer sizing, mic lifecycle | Audio DSP: resampling, VAD (voice activity detection), ring buffers |
| Runtime permission flows (`RECORD_AUDIO`, `POST_NOTIFICATIONS`) | Engine state machine: init → listening → transcribing → done |
| Foreground service + persistent notification lifecycle | Speech-to-text model inference (offline, local) |
| Text insertion via `InputConnection` (IME) or accessibility | Text normalization, punctuation restoration, code-switch handling |
| Floating bubble / overlay lifecycle (`SYSTEM_ALERT_WINDOW`) | Provider abstraction + cloud fallbacks |
| Android Keystore-backed storage of user API keys | Config parsing, profile management |
| JNI thread attachment bookkeeping, `ByteArray` ↔ slice copies | All error types (`thiserror`), all heavy threads |
| Compose UI for settings/onboarding screens | — |

Nothing in the Kotlin layer should contain speech-model logic, language
heuristics, or network code. When in doubt, expose a JNI function and implement
it in Rust.

---

## 2. Microphone capture

### 2.1 API choice: `AudioRecord` (not `MediaRecorder`)

Use **`android.media.AudioRecord`** directly, not `MediaRecorder` or the
`MediaRecorder`-backed `MediaStore` path. Reasons:

- `MediaRecorder` writes compressed output (AAC/AMR) to a file — the Rust core
  wants **raw PCM** to feed the transcription model frame-by-frame.
- `AudioRecord` gives us a pull-based byte stream (`read()`), which maps cleanly
  onto the JNI boundary: Kotlin pulls PCM frames and calls
  `nativeFeedPcm(short[]/byte[], length)` on a background thread.
- Per-chunk latency is controllable via the buffer size we choose.

### 2.2 Sample rate / channel config

- **16 kHz, mono, 16-bit PCM** (`ENCODING_PCM_16BIT`). This is the standard
  input format for speech models (e.g. Whisper-family models natively expect
  16 kHz mono). Capturing at 16 kHz avoids shipping a resampler on the Android
  side; if the device's native path only supports 44.1/48 kHz, downsampling
  happens **in Rust** (one resampler implementation, shared across platforms).
- Use `AudioRecord.getMinBufferSize(16000, CHANNEL_IN_MONO, ENCODING_PCM_16BIT)`
  and multiply by a small factor (e.g. ×4–×8) for the actual buffer to absorb
  GC/JNI jitter. Read in chunks of ~100–200 ms worth of samples.
- `AudioSource`: `VOICE_RECOGNITION` (preferred — applies device-tuned
  speech-processing such as noise suppression) with `MIC` as fallback if
  `VOICE_RECOGNITION` is unavailable.
- Always handle `AudioRecord` state transitions: `STATE_INITIALIZED` check after
  construction, `startRecording()` / `stop()` / `release()` paired with the
  foreground-service lifecycle (section 2.4).

### 2.3 Threading on the Kotlin side

The capture loop runs on a dedicated `HandlerThread` (or a coroutine on
`Dispatchers.IO`) — **never the main thread**. The loop is minimal:

```
while (recording) {
    n = audioRecord.read(pcmShortArray, 0, chunkSize)
    if (n > 0) wahtuhJni.feedPcm(pcmShortArray, n)
}
```

Rust performs VAD, inference, and all heavy work on **its own threads** spawned
inside the JNI crate. Kotlin must not spawn extra threads per chunk; the JNI
call should be non-blocking (Rust copies the samples into its internal ring
buffer and returns immediately).

### 2.4 Foreground service requirement

Since Android 9+, background microphone access requires a **foreground service**
with a persistent notification (`microphone` foreground-service type, declared
in the manifest). Key points:

- Declare `<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>`
  and `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>`
  (API 34+).
- Service type `microphone` in the `<service>` declaration (API 30+):
  `android:foregroundServiceType="microphone"`.
- The service must call `startForeground()` with a notification **within the
  timeout** after `startForegroundService()` (ANR otherwise), and must be
  started while the app is in the foreground or via an explicit user gesture
  (bubble tap).
- While backgrounded, the service shows the persistent notification (mic icon,
  "Listening…", stop action) — this is also the privacy surface users expect.
- On `onDestroy()` / stop action: stop `AudioRecord`, call
  `nativeCancel()`/`nativeFinalize()`, release Rust engine resources.

### 2.5 Audio focus handling

Dictation competes with media playback. The correct behavior:

- Request **transient** audio focus (`AUDIOFOCUS_GAIN_TRANSIENT`) on dictation
  start via `AudioManager.requestAudioFocus()`; abandon on stop.
- Do **not** request permanent focus and do not duck: dictation is short-lived
  and the user explicitly started it, so pausing other audio for its duration is
  acceptable and expected. If focus is denied, still allow dictation to run
  (degraded: note possible interference) rather than failing silently.
- React to `AUDIOFOCUS_LOSS`: treat like a user stop (finalize current
  utterance, keep the session alive) rather than discarding audio.

---

## 3. Permissions

### 3.1 `RECORD_AUDIO` — runtime permission

- Manifest: `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
- Flow: request at first dictation attempt (never at app launch), using
  `ActivityResultContracts.RequestPermission()` or the `PermissionX`/Accompanist
  helper of choice. Explain *why* in the rationale UI ("Wahtuh Pho records your
  voice to transcribe it locally on your device").
- If denied: the dictation entry point shows a disabled state with a
  "Grant permission" button that re-opens the rationale; if permanently denied
  (`shouldShowRequestPermissionRationale() == false` after denial), deep-link to
  app settings. Never crash, never silently no-op.
- Dictation must be impossible without this permission — the JNI engine should
  refuse to start capture if the Kotlin side hasn't confirmed the grant.

### 3.2 `POST_NOTIFICATIONS` — runtime permission (API 33+)

- Manifest: `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`
- Needed because the foreground service's persistent notification is the
  privacy indicator while the mic is live.
- Request together with `RECORD_AUDIO` on the first dictation attempt. If the
  user denies `POST_NOTIFICATIONS` but grants `RECORD_AUDIO`, the foreground
  service still runs (the notification is required by the OS regardless —
  denial of `POST_NOTIFICATIONS` only hides it from the shade on API 33+; the
  foreground-service notification itself still posts). Handle both states
  gracefully and don't block dictation over it.

### 3.3 Full manifest entries

```xml
<!-- Required -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Only if the floating-bubble route is chosen (see §5) -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- Do NOT request: INTERNET for core dictation (offline-first is the point).
     INTERNET is requested only when the user explicitly enables a cloud
     provider, and its use is isolated in the Rust provider module. -->

<application ...>
    <service
        android:name=".dictation.DictationService"
        android:foregroundServiceType="microphone"
        android:exported="false" />
</application>
```

---

## 4. Text insertion: IME vs Accessibility

Once Rust returns transcribed text, it must be inserted wherever the user's
cursor is — system-wide, in any app. There are two realistic routes.

### 4.1 Route (a): Custom IME (`InputMethodService`)

Build a minimal **Input Method Editor**: the user enables "Wahtuh Pho keyboard"
in system Settings → the IME gets a microphone key → pressing it starts
dictation → final text is committed through `InputConnection.commitText()`.

**Pros**
- First-class text insertion: `InputConnection` gives cursor position, selected
  text, surrounding text — enables smart behaviors (replace selection,
  insert at cursor, capitalize after period) with no hacks.
- Respected by virtually all apps; no per-app breakage.
- Standard, documented, stable API surface.

**Cons**
- The IME must be a *complete keyboard* or at least a functional one while
  active: Android requires the IME to handle key events and show an input view.
  The MVP shortcut is a minimal keyboard layout (or a "voice-only" IME that
  immediately shows the dictation UI), but users must still switch input methods
  in Settings — a meaningful onboarding friction step.
- The IME runs in a restricted process with lifecycle quirks (e.g. `onStartInput`
  timing); debugging is harder than a normal Activity.
- Switching keyboards mid-flow annoys some users.

### 4.2 Route (b): Accessibility service

An `AccessibilityService` listens for the dictation trigger (bubble/notification),
then inserts text into the currently focused node via
`AccessibilityNodeInfo.ACTION_SET_TEXT` (or clipboard + paste fallback).

**Pros**
- Works from a floating bubble without making the user switch keyboards —
  lower onboarding friction for the "hold-to-dictate anywhere" flow that
  mirrors Wispr Flow's UX.
- No keyboard UI to build.

**Cons**
- `ACTION_SET_TEXT` support is **inconsistent across apps** (notably poor in
  WebViews, some custom editors, and password fields — where it's blocked for
  good reason). Fallbacks (clipboard paste) are racy and clobber the user's
  clipboard.
- Requires the user to enable an accessibility service in Settings — a scary
  system dialog ("can read everything on screen") that erodes trust, and is
  exactly the kind of permission an offline-privacy-first app should minimize.
- Google Play policies around accessibility-service usage are strict; a
  dictation app is a legitimate use, but reviews are slower and riskier.
- No reliable cursor context: inserting mid-sentence or replacing a selection is
  guesswork.

### 4.3 Recommendation for MVP: **Route (a) — the custom IME**

Honest tradeoff: the IME demands more upfront work (a minimal keyboard UI and
a Settings onboarding step), but it is the only route that inserts text
**reliably, with cursor context, in every app**. The accessibility route
optimizes for demo-day smoothness and then breaks in the apps people actually
dictate into (browsers, chat apps with custom editors, code editors) — exactly
where reliability matters for a Wispr Flow replacement.

Additional rationale:

1. **Trust story.** Our headline feature is "offline, your voice never leaves
   the device." Asking for full accessibility access ("read all screen content")
   contradicts that story; enabling a keyboard is a familiar, bounded grant.
2. **Correctness is the product.** Dictation that misplaces text or eats the
   clipboard is worse than dictation with a one-time keyboard setup.
3. **Wispr Flow itself ships a keyboard/IME path** on mobile for the same
   reason — following the proven UX is the right MVP call.
4. **Phased plan:** ship the IME first; add an optional accessibility-service
   "quick insert" as a *later* enhancement only if user feedback demands the
   bubble-without-keyboard-switch flow. The Rust core doesn't care which route
   delivers the insertion trigger — the JNI boundary stays identical.

---

## 5. Floating UI: overlay bubble vs notification

| | Overlay bubble (`SYSTEM_ALERT_WINDOW`) | Notification (+ foreground service) |
|---|---|---|
| Trigger surface | Floating mic bubble visible over any app; tap/hold to dictate | Persistent notification with "Dictate" action |
| Permission friction | `SYSTEM_ALERT_WINDOW` requires a special Settings grant ("Display over other apps") — high friction, scary dialog | `POST_NOTIFICATIONS` is a normal runtime permission; foreground-service notification is expected for mic use |
| Lifecycle | Tied to a `Service`; must handle window attach/detach across configuration changes and process death | Tied to the same `DictationService`; lifecycle is simpler — start/stop with the service |
| UX fit | Closest to Wispr Flow's hold-to-talk-anywhere feel | One extra tap; less "magical" |
| Reliability | Overlays get killed/buggy on some OEM skins (Xiaomi, Samsung aggressive battery management) | Notifications are the most reliable background surface on Android |

**Recommendation for MVP: notification-first, bubble as opt-in later.**

The foreground service *already requires* a persistent notification while the
mic is live (privacy + OS requirement) — so the notification action ("🎙
Dictate") is free. The bubble adds a second scary permission and OEM-specific
flakiness for a UX gain that the IME route (section 4.3) already covers: with a
custom IME, the mic key is *in the keyboard*, one tap away, in every app. Add
the overlay bubble only after the core dictation loop is proven, and gate it
behind an explicit "Floating bubble (experimental)" settings toggle.

**Lifecycle note (both routes):** UI state (idle / listening / transcribing /
error) is driven by callbacks from the JNI layer into Kotlin (Rust → JNI →
Kotlin listener on the main thread via `Handler(Looper.getMainLooper())`), not
by an Activity/Fragment. No dictation state lives in an Activity — the service
owns it, so rotation and process recreation can't strand the mic open.

---

## 6. JNI boundary design (`wahtuh-jni`)

The `wahtuh-jni` crate is a thin translation layer: JNI signatures on the
outside, safe Rust on the inside. It must contain **no** audio DSP, model, or
provider logic — it calls into the core crates.

### 6.1 Exposed functions (Kotlin → Rust)

| JNI function | Signature (Kotlin view) | Behavior |
|---|---|---|
| `nativeInit` | `(configJson: String): Long` | Parses config, loads engine, returns an opaque engine handle (see §6.4). Heavy work (model load) happens on a Rust-spawned thread; returns immediately. |
| `nativeStartSession` | `(handle: Long)` | Resets engine state for a new utterance. |
| `nativeFeedPcm` | `(handle: Long, pcm: ShortArray, len: Int)` | **Non-blocking.** Copies samples into Rust's ring buffer; VAD + inference run on Rust worker threads. Called ~5–10×/s from the Kotlin capture loop. |
| `nativeFinalize` | `(handle: Long): String` | Signals end-of-utterance; blocks *briefly* only to flush remaining audio (with a timeout), returns final transcribed text. |
| `nativeCancel` | `(handle: Long)` | Discards the current utterance, resets state. |
| `nativeRelease` | `(handle: Long)` | Destroys the engine, frees the handle. Must be called from `DictationService.onDestroy()`; also guarded by a `Cleaner`/finalizer fallback. |

Rust → Kotlin callbacks (engine events: `onPartialText`, `onStateChanged`,
`onError`) go through a global `JWeak` reference to a Kotlin listener,
invoked on the main thread via a `Handler`. Rust never touches Android UI
objects directly.

### 6.2 Threading model

- **Never block the UI thread.** Every JNI entry point returns quickly.
  `nativeInit` kicks off model loading on a Rust thread and reports readiness
  via `onStateChanged(READY)`; `nativeFinalize` has a bounded wait (e.g. 2 s)
  and returns whatever partial result exists on timeout.
- **Rust owns its threads.** The JNI crate spawns worker threads with
  `std::thread`; any thread that calls back into the JVM attaches with
  `JavaVM.attach_current_thread()` and detaches on exit (the `jni` crate's
  `attach_current_thread` guard handles this).
- **Kotlin owns the capture thread** (section 2.3). The two thread domains meet
  only at `nativeFeedPcm`, which does a bounded memcpy and returns.
- JNI local references created in callbacks are explicitly deleted or scoped;
  global/weak-global refs are released in `nativeRelease`.

### 6.3 Error propagation

- Rust `Result<T, WahtuhError>` at the boundary becomes one of:
  - a **thrown Java exception** for programmer errors (bad handle, null args,
    called out of order) — `env.throw_new("java/lang/IllegalStateException", …)`;
  - an **error code / `onError(ErrorKind, message)` callback** for runtime
    failures (model load failed, audio underrun, inference timeout) so the
    Kotlin UI can show a human-readable state and offer retry.
- Never let a Rust panic cross the FFI boundary: every `extern "C"` JNI
  function wraps its body in `std::panic::catch_unwind` and converts a panic
  into an `IllegalStateException`. A panic crossing JNI is undefined behavior
  and will crash the process.

### 6.4 Memory safety notes

- The `Long` handle is an opaque pointer (`*mut Engine`) created with
  `Box::into_raw` and reclaimed exactly once in `nativeRelease` via
  `Box::from_raw`. **Rules:** null/invalid handles are rejected; double-release
  is prevented by nulling the Kotlin-side field after release; every JNI
  function validates the handle before dereferencing.
- **No raw pointer sharing without clear ownership.** PCM data crosses the
  boundary by *copy* (`GetShortArrayRegion` into a Rust `Vec<i16>`), never by
  pinning a JVM array and holding the pointer across calls — the GC may move
  it, and holding a `GetPrimitiveArrayCritical` pin across a blocking call is a
  deadlock risk.
- Strings cross as copies: `JString` → Rust `String` via `env.get_string()`.
- All `unsafe` blocks in `wahtuh-jni` carry a `// SAFETY:` comment stating the
  invariant (valid handle, array bounds checked, attached thread, local ref
  scope). `cargo geiger`-style review: the crate's `unsafe` surface should be
  auditable in one sitting — if it isn't, the design is wrong.

---

## 7. Secure storage: Android Keystore for cloud provider API keys

Wahtuh Pho is offline-first, but the Rust core supports optional cloud
providers — and those need user-supplied API keys. Rules:

- **Never hardcode keys.** No API keys in source, resources, `BuildConfig`, or
  the Rust core. Keys exist only as user input, stored on-device.
- Storage: **Android Keystore** (`AndroidKeyStore` provider) holding an AES-256
  key used to encrypt the API key, with the ciphertext in `EncryptedSharedPreferences`
  (Jetpack Security) or `DataStore`. The Keystore key is hardware-backed where
  available (`setIsStrongBoxBacked(true)` attempted, software fallback allowed)
  and marked non-exportable.
- The Kotlin layer exposes a single JNI-adjacent function,
  e.g. `getProviderKey(providerId: String): String?`, which decrypts on demand
  and passes the key **in memory only** to `nativeInit`'s config JSON. The key
  is zeroed in Rust after the provider client is constructed.
- Key rotation/deletion: settings screen offers "Remove key"; deletion wipes
  the ciphertext and the Keystore entry.
- Threat model note: this protects against casual extraction (backup theft,
  rooted-app snooping of plain prefs), not against a determined attacker with
  a rooted device and the user's lock screen bypassed. Document that honestly
  in user-facing copy.

---

## 8. Build & packaging notes

- The Rust core compiles to a shared library per ABI (`arm64-v8a` primary,
  `armeabi-v7a` if low-end support is wanted, `x86_64` for the emulator) via
  the Android NDK's Clang toolchain; the `.so` files ship under
  `app/src/main/jniLibs/<abi>/`.
- `minSdk`: 29 (Android 10) is a sane floor — `AudioRecord` behavior,
  foreground-service types, and Keystore APIs are all stable there; going lower
  buys device coverage at the cost of permission-model complexity.
- `targetSdk`: latest stable (per current AGP). Foreground-service-type
  declarations (`microphone`) are mandatory from API 34.

---

## 9. Toolchain Blocker — JNI skeleton not written

The Phase C2 task called for a minimal compiling `wahtuh-jni` skeleton **only
if both** the Rust toolchain and the Android NDK were present. Environment
check (2026-10-19, this VM):

| Requirement | Result |
|---|---|
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` / `ANDROID_NDK_HOME` / `ANDROID_NDK_ROOT` | **All unset** |
| `~/Android`, `/opt/android*`, `/usr/local/android*` | **Do not exist** |
| `sdkmanager`, `ndk-build`, `adb` | **Not on PATH** |
| `rustc` / `cargo` | **Not installed** (`rustc: command not found`) |
| `~/.cargo/bin` | **Does not exist** |
| `cargo-ndk` | **Not installed** (no cargo to host it) |

**Both prerequisites are missing**, so no skeleton crate was written — writing
one I cannot compile would violate the mission's no-fabrication rule (an
uncompilable "skeleton" is worse than none: it pretends at progress).

**Unblock checklist** (for whoever provisions the build environment):

1. Install Rust: `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`,
   then `rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android`.
2. Install Android SDK cmdline-tools + NDK (e.g. via Android Studio or
   `sdkmanager "ndk;<version>" "platform-tools"`); export `ANDROID_HOME` and
   `ANDROID_NDK_HOME`.
3. `cargo install cargo-ndk`; add `jni = "0.21"` to `wahtuh-jni`'s
   `Cargo.toml`; set `[lib] crate-type = ["cdylib"]`.
4. Build: `cargo ndk -t arm64-v8a -o app/src/main/jniLibs build -p wahtuh-jni`.
5. The skeleton's 2–3 wrapper functions should be: `nativeInit`,
   `nativeFeedPcm`, `nativeFinalize` — each with `// SAFETY:` comments per §6.4
   and `catch_unwind` per §6.3 — then grow the surface per the table in §6.1.

---

*Document status: design-complete, code-pending-toolchain. Last updated 2026-10-19.*
