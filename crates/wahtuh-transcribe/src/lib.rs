//! Transcription engine abstraction for Wahtuh Pho.
//!
//! [`Engine`] is the single seam between orchestration (`wahtuh-core`) and
//! whatever does the speech recognition. Two implementations ship here:
//!
//! - [`MockEngine`]: deterministic, dependency-free, for unit/integration
//!   tests. It derives canned text from a hash of the input samples so tests
//!   are reproducible without a model.
//! - [`LocalEngine`]: the wiring point for the real on-device engine. It is a
//!   stub today and fails loudly (never silently) until an FFI backend is
//!   linked — see the plug-in notes on the struct.
//!
//! The FFI backend decision (whisper.cpp vs sherpa-onnx vs Vosk) is recorded
//! in `docs/ENGINE_EVALUATION.md`. Whichever wins, it plugs in behind this
//! trait; `wahtuh-core` never changes.

use std::fmt;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

/// Cooperative cancellation token shared between the session and the engine.
///
/// The UI thread calls [`CancellationToken::cancel`]; a long-running
/// `transcribe_chunk` polls [`CancellationToken::is_cancelled`] between
/// inference steps and aborts with [`EngineError::Cancelled`].
#[derive(Debug, Clone)]
pub struct CancellationToken {
    flag: Arc<AtomicBool>,
}

impl CancellationToken {
    pub fn new() -> Self {
        Self {
            flag: Arc::new(AtomicBool::new(false)),
        }
    }

    /// Request cancellation. Idempotent.
    pub fn cancel(&self) {
        self.flag.store(true, Ordering::SeqCst);
    }

    /// Returns true once [`CancellationToken::cancel`] has been called.
    pub fn is_cancelled(&self) -> bool {
        self.flag.load(Ordering::SeqCst)
    }

    /// Reset to the non-cancelled state so the token can be reused.
    pub fn reset(&self) {
        self.flag.store(false, Ordering::SeqCst);
    }
}

impl Default for CancellationToken {
    fn default() -> Self {
        Self::new()
    }
}

/// Best-effort script/language guess for a transcript. Used by the
/// normalizer to pick per-language rules; never used to *discard* text.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Default)]
pub enum LanguageHint {
    English,
    Telugu,
    /// Mixed Telugu/English ("Tenglish") within one utterance.
    CodeSwitch,
    #[default]
    Unknown,
}

/// One time-stamped span of the transcript.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Segment {
    /// Start offset in milliseconds from the beginning of the session.
    pub start_ms: u64,
    /// End offset in milliseconds.
    pub end_ms: u64,
    pub text: String,
}

/// Engine output for one chunk (or a whole session, for short dictations).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Transcript {
    /// Full text of this transcript.
    pub text: String,
    /// Word/phrase-level segments with timestamps. May be empty when the
    /// engine cannot provide them.
    pub segments: Vec<Segment>,
    pub language_hint: LanguageHint,
}

impl Transcript {
    pub fn new(text: impl Into<String>, language_hint: LanguageHint) -> Self {
        Self {
            text: text.into(),
            segments: Vec::new(),
            language_hint,
        }
    }

    /// Rebuild `text` from segments. Useful when an engine returns only
    /// segments, or when a caller edited segments and wants consistency.
    pub fn text_from_segments(&self) -> String {
        self.segments
            .iter()
            .map(|s| s.text.as_str())
            .collect::<Vec<_>>()
            .join(" ")
    }
}

/// Failures an engine can report.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum EngineError {
    /// `transcribe_chunk` called before [`Engine::init`] succeeded.
    NotInitialized,
    /// The native model/backend is not bundled with this build. The message
    /// names what is missing and where to get it. Never a silent empty string.
    ModelNotBundled(String),
    /// Inference aborted via [`CancellationToken`].
    Cancelled,
    /// Input audio was unusable (empty, wrong rate, NaN after resample, ...).
    InvalidAudio(String),
    /// The backend ran but failed.
    InferenceFailed(String),
}

impl fmt::Display for EngineError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            EngineError::NotInitialized => write!(f, "engine used before init()"),
            EngineError::ModelNotBundled(what) => write!(f, "model not bundled: {what}"),
            EngineError::Cancelled => write!(f, "transcription cancelled"),
            EngineError::InvalidAudio(why) => write!(f, "invalid audio: {why}"),
            EngineError::InferenceFailed(why) => write!(f, "inference failed: {why}"),
        }
    }
}

impl std::error::Error for EngineError {}

/// The transcription seam. Implementors must be `Send` because sessions run
/// inference on a worker thread while the UI thread owns the session handle.
///
/// Contract:
/// - `init()` loads the model; it is an error to call `transcribe_chunk`
///   before a successful `init()`.
/// - `transcribe_chunk` must poll `cancel` and return
///   [`EngineError::Cancelled`] promptly; it must not swallow the flag.
/// - `partial_transcript` is a best-effort live preview; the default returns
///   `None` (engine does not support streaming partials).
pub trait Engine: Send {
    /// Stable machine name, e.g. `"mock"`, `"whisper-cpp"`.
    fn name(&self) -> &'static str;

    /// Load the model / backend. Idempotent: calling twice is fine.
    fn init(&mut self) -> Result<(), EngineError>;

    fn is_initialized(&self) -> bool;

    /// Transcribe one chunk of `i16` PCM (16 kHz mono preferred).
    fn transcribe_chunk(
        &self,
        samples: &[i16],
        cancel: &CancellationToken,
    ) -> Result<Transcript, EngineError>;

    /// Best-effort streaming preview for the in-progress chunk.
    fn partial_transcript(&self, _samples: &[i16]) -> Option<String> {
        None
    }
}

/// FNV-1a 32-bit hash. Used by [`MockEngine`] to derive deterministic output
/// from input audio, and a fine default wherever a cheap stable hash of PCM
/// is needed (dedupe, cache keys). Not cryptographic.
pub fn fnv1a_32(data: &[u8]) -> u32 {
    const OFFSET: u32 = 0x811c_9dc5;
    const PRIME: u32 = 0x0100_0193;
    let mut hash = OFFSET;
    for &b in data {
        hash ^= b as u32;
        hash = hash.wrapping_mul(PRIME);
    }
    hash
}

/// Deterministic test double for [`Engine`].
///
/// `transcribe_chunk` hashes the input samples and picks one of three canned
/// utterances (English, Telugu, code-switched), so tests get reproducible,
/// script-diverse transcripts with zero model and zero network. Segment
/// timestamps are synthetic but monotonic and non-overlapping.
pub struct MockEngine {
    initialized: bool,
}

/// (text, language hint) pairs the mock cycles through by input hash.
const MOCK_UTTERANCES: [(&str, LanguageHint); 3] = [
    ("hello world this is a test", LanguageHint::English),
    ("నమస్కారం ఇది ఒక పరీక్ష", LanguageHint::Telugu),
    (
        "hello నమస్కారం this is మిక్స్డ్",
        LanguageHint::CodeSwitch,
    ),
];

impl MockEngine {
    pub fn new() -> Self {
        Self { initialized: false }
    }
}

impl Default for MockEngine {
    fn default() -> Self {
        Self::new()
    }
}

impl Engine for MockEngine {
    fn name(&self) -> &'static str {
        "mock"
    }

    fn init(&mut self) -> Result<(), EngineError> {
        self.initialized = true;
        Ok(())
    }

    fn is_initialized(&self) -> bool {
        self.initialized
    }

    fn transcribe_chunk(
        &self,
        samples: &[i16],
        cancel: &CancellationToken,
    ) -> Result<Transcript, EngineError> {
        if !self.initialized {
            return Err(EngineError::NotInitialized);
        }
        if cancel.is_cancelled() {
            return Err(EngineError::Cancelled);
        }
        if samples.is_empty() {
            return Err(EngineError::InvalidAudio("empty chunk".to_string()));
        }
        // Hash the raw bytes of the samples so the output is a pure function
        // of the input.
        let bytes: Vec<u8> = samples.iter().flat_map(|s| s.to_le_bytes()).collect();
        let idx = fnv1a_32(&bytes) as usize % MOCK_UTTERANCES.len();
        let (text, hint) = MOCK_UTTERANCES[idx];

        // Synthetic word-level segments: 300 ms per word, back to back.
        let mut segments = Vec::new();
        let mut t: u64 = 0;
        for word in text.split_whitespace() {
            segments.push(Segment {
                start_ms: t,
                end_ms: t + 300,
                text: word.to_string(),
            });
            t += 300;
        }

        Ok(Transcript {
            text: text.to_string(),
            segments,
            language_hint: hint,
        })
    }

    fn partial_transcript(&self, samples: &[i16]) -> Option<String> {
        if !self.initialized || samples.is_empty() {
            return None;
        }
        // Preview = first word of whatever the full result would be.
        let bytes: Vec<u8> = samples.iter().flat_map(|s| s.to_le_bytes()).collect();
        let idx = fnv1a_32(&bytes) as usize % MOCK_UTTERANCES.len();
        MOCK_UTTERANCES[idx]
            .0
            .split_whitespace()
            .next()
            .map(|w| format!("{w}…"))
    }
}

/// Wiring point for the real on-device engine.
///
/// # FFI plug-in point
///
/// This struct is intentionally thin: the production backend (whisper.cpp or
/// sherpa-onnx, see `docs/ENGINE_EVALUATION.md`) will be linked here through
/// a small `-sys` crate. The integration contract, to be implemented when the
/// backend is chosen:
///
/// 1. A `whisper-sys`-style crate exposes the C API (`whisper_init_from_file`,
///    `whisper_full`, …) with `extern "C"` declarations and `// SAFETY:`
///    comments on every call.
/// 2. `LocalEngine::init` calls it with `model_path`, converting failures to
///    [`EngineError::InferenceFailed`] — panics must never cross the FFI
///    boundary (`catch_unwind` at the seam, see `docs/ARCHITECTURE.md`).
/// 3. `transcribe_chunk` converts `&[i16]` → `&[f32]` in `[-1.0, 1.0]`,
///    polls `cancel` between C calls, and maps segments back to [`Segment`].
/// 4. `Drop` frees the native context exactly once (`Box::into_raw` /
///    `Box::from_raw` ownership discipline).
///
/// Until then, every method fails with [`EngineError::ModelNotBundled`]
/// carrying an actionable message. This is deliberate: a missing model must
/// be loud, never an empty transcript.
pub struct LocalEngine {
    /// Filesystem path of the `.bin`/`.onnx` model file, when configured.
    pub model_path: Option<std::path::PathBuf>,
    initialized: bool,
}

impl LocalEngine {
    pub fn new() -> Self {
        Self {
            model_path: None,
            initialized: false,
        }
    }

    pub fn with_model(path: impl Into<std::path::PathBuf>) -> Self {
        Self {
            model_path: Some(path.into()),
            initialized: false,
        }
    }
}

impl Default for LocalEngine {
    fn default() -> Self {
        Self::new()
    }
}

impl Engine for LocalEngine {
    fn name(&self) -> &'static str {
        "local"
    }

    fn init(&mut self) -> Result<(), EngineError> {
        Err(EngineError::ModelNotBundled(
            "no native backend linked yet; see docs/ENGINE_EVALUATION.md for the \
             whisper.cpp / sherpa-onnx decision and the FFI plug-in notes on LocalEngine"
                .to_string(),
        ))
    }

    fn is_initialized(&self) -> bool {
        self.initialized
    }

    fn transcribe_chunk(
        &self,
        _samples: &[i16],
        _cancel: &CancellationToken,
    ) -> Result<Transcript, EngineError> {
        Err(EngineError::ModelNotBundled(
            "LocalEngine has no native backend linked; call init() first, which \
             currently always reports the backend as not bundled"
                .to_string(),
        ))
    }
}

// ---------------------------------------------------------------------------
// WhisperCppEngine: real on-device transcription via whisper.cpp (whisper-rs)
// ---------------------------------------------------------------------------

use std::path::{Path, PathBuf};

/// Environment variable overriding the model path used by
/// [`WhisperCppEngine::from_env_or_default`].
pub const WHISPER_MODEL_PATH_ENV: &str = "WAHTUH_MODEL_PATH";

/// Subdirectory of `$HOME` searched for `*.bin` model files.
const MODEL_CACHE_SUBDIR: &str = ".cache/wahtuh/models";

/// Filename used as the last-resort default model path.
const DEFAULT_MODEL_FILENAME: &str = "ggml-base.bin";

/// Unicode block for the Telugu script, used for auto language detection.
const TELUGU_RANGE: std::ops::RangeInclusive<char> = '\u{0C00}'..='\u{0C7F}';

/// Real on-device transcription backed by whisper.cpp through the
/// [`whisper_rs`](https://docs.rs/whisper-rs) safe bindings (v0.16).
///
/// This is the production implementation of the [`Engine`] trait: model and
/// inference both run locally, there are no stubs, and every failure mode
/// maps to an [`EngineError`].
///
/// # Model resolution
///
/// [`WhisperCppEngine::from_env_or_default`] picks the model file in this
/// order:
/// 1. the `$WAHTUH_MODEL_PATH` environment variable, when set;
/// 2. the first `*.bin` file (lexicographic order) in `~/.cache/wahtuh/models/`;
/// 3. `~/.cache/wahtuh/models/ggml-base.bin` as a last-resort default path.
///    If nothing exists there, [`Engine::init`] fails with an actionable
///    [`EngineError::ModelNotBundled`] message naming the path and where to
///    get a model.
///
/// # Local-only guarantee
///
/// The model is loaded from disk with `WhisperContext::new_with_params` and
/// inference is pure local compute. This engine's code path performs **zero
/// network calls** — audio and model bytes never leave the machine.
///
/// # Threading and `Send`
///
/// `whisper_rs::WhisperContext` is `Send + Sync` upstream: it is a thin
/// `Arc` wrapper around `WhisperInnerContext`, which carries explicit
/// `unsafe impl Send` / `unsafe impl Sync`. Each `transcribe_chunk` call
/// builds its own `WhisperState` (also `Send + Sync` upstream) via
/// `create_state()`, so no locking is needed and `Engine: Send` holds by
/// construction — the `impl Engine for WhisperCppEngine` below would not
/// compile otherwise.
///
/// # Cancellation
///
/// The [`CancellationToken`] is polled before and after inference, and is
/// additionally wired into whisper.cpp's abort callback
/// (`FullParams::set_abort_callback_safe`), so a long chunk aborts promptly
/// *mid-inference* and surfaces as [`EngineError::Cancelled`] rather than
/// running to completion.
pub struct WhisperCppEngine {
    model_path: PathBuf,
    language: Option<String>,
    n_threads: usize,
    ctx: Option<whisper_rs::WhisperContext>,
}

impl WhisperCppEngine {
    /// Build an engine for the model at `path`, with automatic language
    /// detection (Telugu script sniffing, see [`LanguageHint`]).
    pub fn with_model(path: impl Into<PathBuf>) -> Self {
        Self::with_model_and_language(path, None)
    }

    /// Build an engine for the model at `path`, forcing whisper's language
    /// (e.g. `Some("en")`, `Some("te")`). `None` means auto-detect.
    pub fn with_model_and_language(path: impl Into<PathBuf>, language: Option<String>) -> Self {
        Self {
            model_path: path.into(),
            language,
            n_threads: default_threads(),
            ctx: None,
        }
    }

    /// Resolve the model path from the environment / model cache and build
    /// an engine for it. See the [struct-level docs](WhisperCppEngine#model-resolution)
    /// for the exact resolution order.
    pub fn from_env_or_default() -> Self {
        let path = std::env::var(WHISPER_MODEL_PATH_ENV)
            .ok()
            .map(PathBuf::from)
            .or_else(first_cached_model)
            .unwrap_or_else(|| model_cache_dir().join(DEFAULT_MODEL_FILENAME));
        Self::with_model(path)
    }

    /// Override the inference thread count (default: logical CPU count).
    /// Must be called before [`Engine::init`] has no effect on thread use —
    /// it is read fresh on every `transcribe_chunk` call.
    pub fn set_threads(&mut self, n_threads: usize) {
        self.n_threads = n_threads.max(1);
    }

    /// Builder-style thread-count override.
    pub fn with_threads(mut self, n_threads: usize) -> Self {
        self.set_threads(n_threads);
        self
    }

    /// The model file this engine will load on [`Engine::init`].
    pub fn model_path(&self) -> &Path {
        &self.model_path
    }

    /// The forced language, if any (`None` = auto-detect).
    pub fn language(&self) -> Option<&str> {
        self.language.as_deref()
    }

    /// Map transcript text to a [`LanguageHint`]: the configured language
    /// wins when set, otherwise Telugu script (U+0C00–U+0C7F) is sniffed —
    /// mixed Telugu/Latin text reports [`LanguageHint::CodeSwitch`].
    fn detect_language(&self, text: &str) -> LanguageHint {
        if let Some(lang) = self.language.as_deref() {
            return match lang {
                "en" => LanguageHint::English,
                "te" => LanguageHint::Telugu,
                _ => LanguageHint::Unknown,
            };
        }
        let has_telugu = text.chars().any(|c| TELUGU_RANGE.contains(&c));
        let has_latin = text.chars().any(|c| c.is_ascii_alphabetic());
        match (has_telugu, has_latin) {
            (true, true) => LanguageHint::CodeSwitch,
            (true, false) => LanguageHint::Telugu,
            (false, true) => LanguageHint::English,
            (false, false) => LanguageHint::Unknown,
        }
    }
}

impl Engine for WhisperCppEngine {
    fn name(&self) -> &'static str {
        "whisper-cpp"
    }

    fn init(&mut self) -> Result<(), EngineError> {
        if self.ctx.is_some() {
            return Ok(()); // idempotent
        }
        if !self.model_path.is_file() {
            return Err(EngineError::ModelNotBundled(format!(
                "whisper model not found at '{}'. Download a multilingual ggml model \
                 (e.g. ggml-base.bin from https://huggingface.co/ggerganov/whisper.cpp) \
                 into ~/.cache/wahtuh/models/, or set ${} to the model path.",
                self.model_path.display(),
                WHISPER_MODEL_PATH_ENV
            )));
        }
        // CPU-only context params (no GPU features enabled); loads from disk only.
        let ctx_params = whisper_rs::WhisperContextParameters::new();
        let ctx =
            whisper_rs::WhisperContext::new_with_params(&self.model_path, ctx_params).map_err(
                |e| {
                    EngineError::InferenceFailed(format!(
                        "failed to load whisper model '{}': {e}",
                        self.model_path.display()
                    ))
                },
            )?;
        self.ctx = Some(ctx);
        Ok(())
    }

    fn is_initialized(&self) -> bool {
        self.ctx.is_some()
    }

    fn transcribe_chunk(
        &self,
        samples: &[i16],
        cancel: &CancellationToken,
    ) -> Result<Transcript, EngineError> {
        let ctx = self.ctx.as_ref().ok_or(EngineError::NotInitialized)?;
        if samples.is_empty() {
            return Err(EngineError::InvalidAudio("empty chunk".to_string()));
        }
        if cancel.is_cancelled() {
            return Err(EngineError::Cancelled);
        }

        // i16 PCM -> f32 in [-1.0, 1.0], as whisper.cpp expects.
        let pcm: Vec<f32> = samples.iter().map(|s| *s as f32 / 32768.0).collect();

        let mut state = ctx.create_state().map_err(|e| {
            EngineError::InferenceFailed(format!("whisper failed to create state: {e}"))
        })?;

        let mut params = whisper_rs::FullParams::new(whisper_rs::SamplingStrategy::Greedy {
            best_of: 1,
        });
        params.set_n_threads(self.n_threads as i32);
        params.set_translate(false);
        params.set_language(self.language.as_deref());
        params.set_print_special(false);
        params.set_print_progress(false);
        params.set_print_realtime(false);
        params.set_print_timestamps(false);
        // Abort mid-inference when the token fires: whisper.cpp polls this
        // callback during encode/decode.
        let abort_token = cancel.clone();
        params.set_abort_callback_safe(move || abort_token.is_cancelled());

        state.full(params, &pcm).map_err(|e| {
            if cancel.is_cancelled() {
                EngineError::Cancelled
            } else {
                EngineError::InferenceFailed(format!("whisper inference failed: {e}"))
            }
        })?;
        if cancel.is_cancelled() {
            return Err(EngineError::Cancelled);
        }

        // whisper.cpp reports segment times in centiseconds; the Engine
        // contract uses milliseconds.
        let n = state.full_n_segments();
        let mut segments = Vec::new();
        let mut text_parts = Vec::new();
        for i in 0..n {
            if cancel.is_cancelled() {
                return Err(EngineError::Cancelled);
            }
            let Some(seg) = state.get_segment(i) else {
                continue;
            };
            let text = seg
                .to_str_lossy()
                .map(|s| s.into_owned())
                .unwrap_or_default();
            let text = text.trim().to_string();
            if text.is_empty() {
                continue;
            }
            segments.push(Segment {
                start_ms: seg.start_timestamp().max(0) as u64 * 10,
                end_ms: seg.end_timestamp().max(0) as u64 * 10,
                text: text.clone(),
            });
            text_parts.push(text);
        }

        let text = text_parts.join(" ");
        let language_hint = self.detect_language(&text);
        Ok(Transcript {
            text,
            segments,
            language_hint,
        })
    }
}

/// Logical CPU count, falling back to 4, always at least 1.
fn default_threads() -> usize {
    std::thread::available_parallelism()
        .map(|n| n.get())
        .unwrap_or(4)
        .max(1)
}

/// `$HOME/.cache/wahtuh/models` (`"."`-relative fallback when `$HOME` is unset).
fn model_cache_dir() -> PathBuf {
    std::env::var("HOME")
        .map(PathBuf::from)
        .unwrap_or_else(|_| PathBuf::from("."))
        .join(MODEL_CACHE_SUBDIR)
}

/// First `*.bin` in the model cache, lexicographic order for determinism.
fn first_cached_model() -> Option<PathBuf> {
    let entries = std::fs::read_dir(model_cache_dir()).ok()?;
    let mut bins: Vec<PathBuf> = entries
        .filter_map(|e| e.ok().map(|e| e.path()))
        .filter(|p| p.extension().and_then(|e| e.to_str()) == Some("bin"))
        .collect();
    bins.sort();
    bins.into_iter().next()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn samples(seed: i16, n: usize) -> Vec<i16> {
        // Deterministic pseudo-audio: ramp with a seed offset.
        (0..n).map(|i| seed.wrapping_add(i as i16)).collect()
    }

    #[test]
    fn mock_is_deterministic_per_input() {
        let mut e = MockEngine::new();
        e.init().unwrap();
        let cancel = CancellationToken::new();
        let a = samples(1, 16_000);
        let t1 = e.transcribe_chunk(&a, &cancel).unwrap();
        let t2 = e.transcribe_chunk(&a, &cancel).unwrap();
        assert_eq!(t1, t2);
    }

    #[test]
    fn mock_varies_with_input_and_covers_scripts() {
        let mut e = MockEngine::new();
        e.init().unwrap();
        let cancel = CancellationToken::new();
        let mut seen = std::collections::HashSet::new();
        for seed in [1i16, 7, 42, 100, 999, 1234] {
            let t = e.transcribe_chunk(&samples(seed, 16_000), &cancel).unwrap();
            seen.insert(t.language_hint);
            // Segments are monotonic, non-overlapping, and join back to text.
            let mut prev_end = 0;
            for s in &t.segments {
                assert!(s.start_ms >= prev_end);
                assert!(s.end_ms > s.start_ms);
                prev_end = s.end_ms;
            }
            assert_eq!(t.text_from_segments(), t.text);
        }
        // Across seeds we should see more than one language hint (hash spread).
        assert!(seen.len() >= 2, "mock should emit diverse hints, saw {seen:?}");
    }

    #[test]
    fn mock_requires_init_and_honours_cancel() {
        let e = MockEngine::new();
        let cancel = CancellationToken::new();
        assert_eq!(
            e.transcribe_chunk(&samples(1, 100), &cancel),
            Err(EngineError::NotInitialized)
        );

        let mut e = MockEngine::new();
        e.init().unwrap();
        assert_eq!(
            e.transcribe_chunk(&[], &cancel),
            Err(EngineError::InvalidAudio("empty chunk".to_string()))
        );
        cancel.cancel();
        assert_eq!(
            e.transcribe_chunk(&samples(1, 100), &cancel),
            Err(EngineError::Cancelled)
        );
        cancel.reset();
        assert!(e.transcribe_chunk(&samples(1, 100), &cancel).is_ok());
    }

    #[test]
    fn mock_partial_preview() {
        let mut e = MockEngine::new();
        assert_eq!(e.partial_transcript(&samples(1, 100)), None); // not init'd
        e.init().unwrap();
        let p = e.partial_transcript(&samples(1, 100)).unwrap();
        assert!(p.ends_with('…'));
    }

    #[test]
    fn local_engine_fails_loudly() {
        let mut e = LocalEngine::with_model("/nonexistent/model.bin");
        let err = e.init().unwrap_err();
        assert!(matches!(err, EngineError::ModelNotBundled(_)));
        assert!(err.to_string().contains("ENGINE_EVALUATION"));

        let cancel = CancellationToken::new();
        let err = e.transcribe_chunk(&samples(1, 100), &cancel).unwrap_err();
        assert!(matches!(err, EngineError::ModelNotBundled(_)));
        assert!(!e.is_initialized());
    }

    #[test]
    fn fnv1a_is_stable() {
        assert_eq!(fnv1a_32(b""), 0x811c_9dc5);
        assert_eq!(fnv1a_32(b"hello"), fnv1a_32(b"hello"));
        assert_ne!(fnv1a_32(b"hello"), fnv1a_32(b"world"));
    }

    #[test]
    fn cancellation_token_clone_shares_flag() {
        let t = CancellationToken::new();
        let t2 = t.clone();
        t.cancel();
        assert!(t2.is_cancelled());
    }

    // --- WhisperCppEngine tests -------------------------------------------

    /// Resolve a model the same way `WhisperCppEngine::from_env_or_default`
    /// does; `None` when no model file exists on this machine.
    fn available_whisper_model() -> Option<PathBuf> {
        let e = WhisperCppEngine::from_env_or_default();
        e.model_path().is_file().then(|| e.model_path().to_path_buf())
    }

    #[test]
    fn whisper_engine_fails_loudly_without_model_file() {
        let mut e = WhisperCppEngine::with_model("/nonexistent/wahtuh-test-model.bin");
        assert_eq!(e.name(), "whisper-cpp");
        assert!(!e.is_initialized());
        assert_eq!(e.language(), None);

        let err = e.init().unwrap_err();
        assert!(
            matches!(err, EngineError::ModelNotBundled(_)),
            "expected ModelNotBundled, got {err:?}"
        );
        let msg = err.to_string();
        assert!(msg.contains("/nonexistent/wahtuh-test-model.bin"), "msg: {msg}");
        assert!(msg.contains("WAHTUH_MODEL_PATH"), "msg: {msg}");
        assert!(!e.is_initialized(), "failed init must not mark initialized");

        // Uninitialized engine reports NotInitialized regardless of input.
        let cancel = CancellationToken::new();
        assert_eq!(
            e.transcribe_chunk(&samples(1, 100), &cancel),
            Err(EngineError::NotInitialized)
        );
        // ... and a cancelled token does not change that ordering.
        cancel.cancel();
        assert_eq!(
            e.transcribe_chunk(&samples(1, 100), &cancel),
            Err(EngineError::NotInitialized)
        );
    }

    #[test]
    fn whisper_model_resolution_prefers_env_var() {
        // Point the env var at a nonexistent path: resolution must still pick
        // it (init then fails loudly naming that path).
        std::env::set_var("WAHTUH_MODEL_PATH", "/tmp/wahtuh-env-override-test.bin");
        let e = WhisperCppEngine::from_env_or_default();
        assert_eq!(e.model_path(), Path::new("/tmp/wahtuh-env-override-test.bin"));
        std::env::remove_var("WAHTUH_MODEL_PATH");
    }

    #[test]
    fn whisper_engine_skips_without_model() {
        let Some(model) = available_whisper_model() else {
            eprintln!("SKIP: no whisper model found");
            return;
        };
        eprintln!("using whisper model: {}", model.display());

        let mut e = WhisperCppEngine::with_model(&model);
        e.init().expect("init() must succeed when the model file exists");
        assert!(e.is_initialized());
        e.init().expect("init() must be idempotent");

        // A cancelled token aborts before any inference runs.
        let cancel = CancellationToken::new();
        cancel.cancel();
        assert_eq!(
            e.transcribe_chunk(&samples(1, 100), &cancel),
            Err(EngineError::Cancelled)
        );
        cancel.reset();

        // Empty input is rejected without touching the backend.
        assert!(matches!(
            e.transcribe_chunk(&[], &cancel),
            Err(EngineError::InvalidAudio(_))
        ));

        // Optional end-to-end check against the licensed jfk.wav corpus clip.
        let wav = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../corpus/audio/jfk.wav");
        if !wav.is_file() {
            eprintln!("SKIP: corpus wav not found at {}", wav.display());
            return;
        }
        let samples = match read_wav_i16_mono_16k(&wav) {
            Ok(s) => s,
            Err(why) => {
                eprintln!("SKIP: cannot parse {}: {why}", wav.display());
                return;
            }
        };
        let t = e
            .transcribe_chunk(&samples, &cancel)
            .expect("transcription of jfk.wav must succeed");
        assert!(
            !t.text.trim().is_empty(),
            "jfk.wav transcript must be non-empty"
        );
        eprintln!("whisper transcript: {:?}", t.text.trim());
        // Segment timestamps must be centiseconds*10 and well-ordered.
        for s in &t.segments {
            assert!(s.end_ms >= s.start_ms, "segment not ordered: {s:?}");
        }
    }

    /// Read 16-bit mono 16 kHz PCM from a WAV file; anything else is an error.
    fn read_wav_i16_mono_16k(path: &Path) -> Result<Vec<i16>, String> {
        let mut reader = hound::WavReader::open(path).map_err(|e| e.to_string())?;
        let spec = reader.spec();
        if spec.channels != 1 || spec.sample_rate != 16_000 || spec.bits_per_sample != 16 {
            return Err(format!("expected 16-bit mono 16kHz PCM, got {spec:?}"));
        }
        reader
            .samples::<i16>()
            .collect::<Result<Vec<_>, _>>()
            .map_err(|e| e.to_string())
    }
}
