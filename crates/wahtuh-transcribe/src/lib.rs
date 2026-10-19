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
}
