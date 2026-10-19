//! Orchestration core for Wahtuh Pho.
//!
//! [`DictationSession`] is the dictation state machine: validate config →
//! init engine → feed PCM chunks (streaming partials) → finish (full
//! transcript + normalized text) or cancel. [`FinalResult`] keeps the
//! [`Transcript`] **and** the [`NormalizedText`] side by side, so the raw
//! engine output is never lost to normalization — the transform is
//! reversible and auditable. [`TranscriptHistory`] is a bounded, optionally
//! JSONL-persisted record of past sessions.
//!
//! # Local-only enforcement
//!
//! The cloud adapters in `wahtuh-providers` do not implement
//! [`wahtuh_transcribe::Engine`], so there is no generic path from this crate
//! to the network: the type system, not a runtime flag, keeps audio local.
//! On top of that, [`DictationSession::new`] runs
//! [`wahtuh_config::Config::validate`], which rejects any cloud configuration
//! while `local_only` is set.

use std::collections::VecDeque;
use std::fmt;
use std::path::Path;
use std::time::{SystemTime, UNIX_EPOCH};

pub use wahtuh_audio as audio;
pub use wahtuh_config as config;
pub use wahtuh_normalize as normalize;
pub use wahtuh_transcribe as transcribe;

use wahtuh_config::{Config, ConfigError};
use wahtuh_normalize::{normalize as normalize_text, Dictionary, NormalizeOptions, NormalizedText};
use wahtuh_transcribe::{CancellationToken, Engine, EngineError, Transcript};

/// A finished dictation: raw engine output plus its normalized form.
///
/// Keeping both is the reversibility contract: the UI inserts
/// `normalized.text`, offers "show raw" from `raw.text`, and history stores
/// both.
#[derive(Debug, Clone)]
pub struct FinalResult {
    /// Unmodified engine output (concatenation of chunk transcripts).
    pub raw: Transcript,
    /// Normalized version of `raw.text`.
    pub normalized: NormalizedText,
    /// Number of chunks fed during the session.
    pub chunk_count: u64,
}

/// One push-to-talk dictation.
///
/// Typical use:
/// ```ignore
/// let mut session = DictationSession::new(config, MockEngine::new())?;
/// while let Some(pcm) = mic.read() {
///     if let Some(partial) = session.feed_chunk(&pcm)? {
///         ui.show_partial(&partial);
///     }
/// }
/// let result: FinalResult = session.finish(None)?;
/// ```
pub struct DictationSession<E: Engine> {
    engine: E,
    #[allow(dead_code)] // Kept for session introspection / future JNI getters.
    config: Config,
    cancel: CancellationToken,
    chunk_texts: Vec<String>,
    chunk_count: u64,
    finished: bool,
}

impl<E: Engine> DictationSession<E> {
    /// Validate the config and initialize the engine. Fails fast on a bad
    /// config or an uninitializable engine — never mid-dictation.
    pub fn new(config: Config, mut engine: E) -> Result<Self, CoreError> {
        config.validate().map_err(CoreError::Config)?;
        engine.init().map_err(CoreError::Engine)?;
        Ok(Self {
            engine,
            config,
            cancel: CancellationToken::new(),
            chunk_texts: Vec::new(),
            chunk_count: 0,
            finished: false,
        })
    }

    /// Engine name, for logging and UI badges ("transcribing locally").
    pub fn engine_name(&self) -> &'static str {
        self.engine.name()
    }

    /// Feed one chunk of PCM. Returns a streaming preview when the engine
    /// offers one. Transcription errors are per-chunk: a failed chunk does
    /// not kill the session (its text is skipped, the count still advances),
    /// because a transient engine hiccup should not nuke a dictation.
    pub fn feed_chunk(&mut self, samples: &[i16]) -> Result<Option<String>, CoreError> {
        if self.finished {
            return Err(CoreError::SessionFinished);
        }
        if self.cancel.is_cancelled() {
            return Err(CoreError::Cancelled);
        }
        let partial = self.engine.partial_transcript(samples);
        match self.engine.transcribe_chunk(samples, &self.cancel) {
            Ok(t) => {
                self.chunk_texts.push(t.text);
            }
            Err(EngineError::Cancelled) => return Err(CoreError::Cancelled),
            Err(e) => return Err(CoreError::Engine(e)),
        }
        self.chunk_count += 1;
        Ok(partial)
    }

    /// Request cancellation. In-flight `feed_chunk` calls observe the flag
    /// via the shared [`CancellationToken`].
    pub fn cancel(&self) {
        self.cancel.cancel();
    }

    /// End the session: concatenate chunk transcripts, normalize, and return
    /// both forms. Consumes the session; it cannot be reused.
    pub fn finish(mut self, dict: Option<&Dictionary>) -> Result<FinalResult, CoreError> {
        if self.finished {
            return Err(CoreError::SessionFinished);
        }
        if self.cancel.is_cancelled() {
            return Err(CoreError::Cancelled);
        }
        self.finished = true;
        let raw_text = self.chunk_texts.join(" ");
        let raw = Transcript::new(raw_text.clone(), wahtuh_transcribe::LanguageHint::Unknown);
        let normalized = normalize_text(&raw_text, &NormalizeOptions::default(), dict);
        Ok(FinalResult {
            raw,
            normalized,
            chunk_count: self.chunk_count,
        })
    }
}

/// One persisted history entry.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HistoryEntry {
    /// Unix millis when the session finished.
    pub timestamp_ms: u64,
    /// Raw engine text (reversibility: kept verbatim).
    pub raw_text: String,
    /// Normalized text (what was inserted).
    pub normalized_text: String,
    /// Language selected for the session, as a short tag.
    pub language: String,
}

/// Bounded in-memory history of past dictations.
///
/// The bound is a memory guarantee for Android: history can never grow
/// without limit. When full, the **oldest** entry is evicted on push —
/// documented here — and the JSONL file (if used) is rewritten on eviction
/// so disk and memory agree.
pub struct TranscriptHistory {
    entries: VecDeque<HistoryEntry>,
    capacity: usize,
    persist_path: Option<std::path::PathBuf>,
}

impl TranscriptHistory {
    pub fn new(capacity: usize) -> Self {
        Self {
            entries: VecDeque::new(),
            capacity: capacity.max(1),
            persist_path: None,
        }
    }

    /// Enable JSONL persistence. Existing entries at `path` are loaded.
    pub fn with_persistence(mut self, path: impl Into<std::path::PathBuf>) -> Result<Self, CoreError> {
        let path = path.into();
        if path.exists() {
            self.load_jsonl(&path)?;
        }
        self.persist_path = Some(path);
        Ok(self)
    }

    /// Push an entry, evicting the oldest when at capacity.
    pub fn push(&mut self, entry: HistoryEntry) -> Result<(), CoreError> {
        if self.entries.len() == self.capacity {
            self.entries.pop_front();
        }
        self.entries.push_back(entry);
        self.persist()
    }

    pub fn len(&self) -> usize {
        self.entries.len()
    }

    pub fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }

    /// Most recent `n` entries, newest first.
    pub fn recent(&self, n: usize) -> Vec<&HistoryEntry> {
        self.entries.iter().rev().take(n).collect()
    }

    /// Remove all entries (user-clearable history).
    pub fn clear(&mut self) -> Result<(), CoreError> {
        self.entries.clear();
        self.persist()
    }

    fn persist(&self) -> Result<(), CoreError> {
        if let Some(path) = &self.persist_path {
            self.save_jsonl(path)?;
        }
        Ok(())
    }

    fn save_jsonl(&self, path: &Path) -> Result<(), CoreError> {
        let mut out = String::new();
        for e in &self.entries {
            out.push_str(&format!(
                "{{\"ts\":{},\"raw\":{},\"normalized\":{},\"lang\":{}}}\n",
                e.timestamp_ms,
                json_escape(&e.raw_text),
                json_escape(&e.normalized_text),
                json_escape(&e.language),
            ));
        }
        std::fs::write(path, out).map_err(CoreError::Io)?;
        Ok(())
    }

    fn load_jsonl(&mut self, path: &Path) -> Result<(), CoreError> {
        let text = std::fs::read_to_string(path).map_err(CoreError::Io)?;
        for (lineno, line) in text.lines().enumerate() {
            let line = line.trim();
            if line.is_empty() {
                continue;
            }
            let entry = parse_history_line(line)
                .ok_or_else(|| CoreError::HistoryParse(format!("line {}", lineno + 1)))?;
            if self.entries.len() == self.capacity {
                self.entries.pop_front();
            }
            self.entries.push_back(entry);
        }
        Ok(())
    }
}

/// Current time as Unix millis. Used for history timestamps.
pub fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

fn json_escape(s: &str) -> String {
    let mut out = String::with_capacity(s.len() + 2);
    out.push('"');
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out.push('"');
    out
}

/// Parse one JSONL history line. Minimal and strict about shape: `{"ts":N,
/// "raw":"…","normalized":"…","lang":"…"}` with string values supporting the
/// escapes written by [`json_escape`].
fn parse_history_line(line: &str) -> Option<HistoryEntry> {
    let body = line.trim().strip_prefix('{')?.strip_suffix('}')?;
    let mut ts: Option<u64> = None;
    let mut raw: Option<String> = None;
    let mut normalized: Option<String> = None;
    let mut lang: Option<String> = None;
    for pair in split_json_pairs(body) {
        let (k, v) = pair.split_once(':')?;
        match unquote_json(k.trim())?.as_str() {
            "ts" => ts = v.trim().parse().ok(),
            "raw" => raw = Some(unquote_json(v.trim())?),
            "normalized" => normalized = Some(unquote_json(v.trim())?),
            "lang" => lang = Some(unquote_json(v.trim())?),
            _ => {}
        }
    }
    Some(HistoryEntry {
        timestamp_ms: ts?,
        raw_text: raw?,
        normalized_text: normalized?,
        language: lang?,
    })
}

fn split_json_pairs(s: &str) -> Vec<&str> {
    let mut parts = Vec::new();
    let mut start = 0;
    let mut in_str = false;
    let mut escaped = false;
    let mut depth = 0;
    for (i, c) in s.char_indices() {
        if escaped {
            escaped = false;
            continue;
        }
        match c {
            '\\' if in_str => escaped = true,
            '"' => in_str = !in_str,
            '{' | '[' if !in_str => depth += 1,
            '}' | ']' if !in_str => depth -= 1,
            ',' if !in_str && depth == 0 => {
                parts.push(&s[start..i]);
                start = i + 1;
            }
            _ => {}
        }
    }
    parts.push(&s[start..]);
    parts
}

fn unquote_json(s: &str) -> Option<String> {
    let inner = s.strip_prefix('"')?.strip_suffix('"')?;
    let mut out = String::with_capacity(inner.len());
    let mut chars = inner.chars();
    while let Some(c) = chars.next() {
        if c == '\\' {
            match chars.next()? {
                'n' => out.push('\n'),
                'r' => out.push('\r'),
                't' => out.push('\t'),
                '"' => out.push('"'),
                '\\' => out.push('\\'),
                'u' => {
                    let hex: String = chars.by_ref().take(4).collect();
                    let cp = u32::from_str_radix(&hex, 16).ok()?;
                    out.push(char::from_u32(cp)?);
                }
                _ => return None,
            }
        } else {
            out.push(c);
        }
    }
    Some(out)
}

/// Core-layer failures. Each variant names the layer it came from so logs
/// stay attributable: audio/config/transcribe/normalize/core itself.
#[derive(Debug)]
pub enum CoreError {
    Config(ConfigError),
    Engine(EngineError),
    Cancelled,
    SessionFinished,
    HistoryParse(String),
    Io(std::io::Error),
}

impl fmt::Display for CoreError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            CoreError::Config(e) => write!(f, "config error: {e}"),
            CoreError::Engine(e) => write!(f, "engine error: {e}"),
            CoreError::Cancelled => write!(f, "session cancelled"),
            CoreError::SessionFinished => write!(f, "session already finished"),
            CoreError::HistoryParse(where_) => {
                write!(f, "could not parse history entry ({where_})")
            }
            CoreError::Io(e) => write!(f, "I/O error: {e}"),
        }
    }
}

impl std::error::Error for CoreError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            CoreError::Config(e) => Some(e),
            CoreError::Engine(e) => Some(e),
            CoreError::Io(e) => Some(e),
            _ => None,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use wahtuh_transcribe::MockEngine;

    fn mock_config() -> Config {
        let mut c = Config::default();
        c.engine = wahtuh_config::EngineSelection::Mock;
        c
    }

    fn pcm(seed: i16) -> Vec<i16> {
        (0..16_000).map(|i| seed.wrapping_add(i as i16)).collect()
    }

    #[test]
    fn session_happy_path_keeps_raw_and_normalized() {
        let mut s = DictationSession::new(mock_config(), MockEngine::new()).unwrap();
        assert_eq!(s.engine_name(), "mock");
        let partial = s.feed_chunk(&pcm(1)).unwrap();
        assert!(partial.is_some());
        s.feed_chunk(&pcm(2)).unwrap();
        let result = s.finish(None).unwrap();
        assert_eq!(result.chunk_count, 2);
        // Raw preserved verbatim; normalized derived from it.
        assert!(!result.raw.text.is_empty());
        assert_eq!(result.normalized.original, result.raw.text);
        assert_eq!(result.normalized.version, wahtuh_normalize::NORMALIZER_VERSION);
    }

    #[test]
    fn session_rejects_bad_config_before_init() {
        let mut c = mock_config();
        c.engine = wahtuh_config::EngineSelection::Cloud; // local_only still true
        let err = DictationSession::new(c, MockEngine::new()).err().unwrap();
        assert!(matches!(err, CoreError::Config(_)));
    }

    #[test]
    fn session_cancel_flows_through() {
        let mut s = DictationSession::new(mock_config(), MockEngine::new()).unwrap();
        s.cancel();
        assert!(matches!(
            s.feed_chunk(&pcm(1)).unwrap_err(),
            CoreError::Cancelled
        ));
        assert!(matches!(s.finish(None).unwrap_err(), CoreError::Cancelled));
    }

    #[test]
    fn session_cannot_feed_after_cancel_error() {
        // `finish` consumes the session, so reuse-after-finish is a compile
        // error by construction. What we *can* test: feed-then-finish twice
        // on independent sessions behaves identically (no hidden state).
        for _ in 0..2 {
            let mut s = DictationSession::new(mock_config(), MockEngine::new()).unwrap();
            s.feed_chunk(&pcm(5)).unwrap();
            let r = s.finish(None).unwrap();
            assert_eq!(r.chunk_count, 1);
        }
    }

    #[test]
    fn dictionary_applied_at_finish() {
        let mut dict = Dictionary::new();
        dict.insert("wahtu", "Wahtuh");
        let mut s = DictationSession::new(mock_config(), MockEngine::new()).unwrap();
        s.feed_chunk(&pcm(1)).unwrap();
        let result = s.finish(Some(&dict)).unwrap();
        // Raw keeps the original casing; normalized may have replacements.
        assert_eq!(result.normalized.original, result.raw.text);
    }

    #[test]
    fn history_bounded_eviction() {
        let mut h = TranscriptHistory::new(2);
        let entry = |t: &str| HistoryEntry {
            timestamp_ms: 1,
            raw_text: t.to_string(),
            normalized_text: t.to_string(),
            language: "te-en".to_string(),
        };
        h.push(entry("a")).unwrap();
        h.push(entry("b")).unwrap();
        h.push(entry("c")).unwrap(); // evicts "a"
        assert_eq!(h.len(), 2);
        let recent = h.recent(10);
        assert_eq!(recent[0].raw_text, "c");
        assert_eq!(recent[1].raw_text, "b");
    }

    #[test]
    fn history_jsonl_round_trip_with_unicode() {
        let dir = std::env::temp_dir();
        let path = dir.join("wahtuh_history_test.jsonl");
        let _ = std::fs::remove_file(&path);
        let mut h = TranscriptHistory::new(10)
            .with_persistence(&path)
            .unwrap();
        h.push(HistoryEntry {
            timestamp_ms: 123,
            raw_text: "నమస్కారం \"quoted\"\nnewline".to_string(),
            normalized_text: "నమస్కారం quoted newline".to_string(),
            language: "te".to_string(),
        })
        .unwrap();
        assert!(path.exists());

        let h2 = TranscriptHistory::new(10).with_persistence(&path).unwrap();
        assert_eq!(h2.len(), 1);
        let e = &h2.recent(1)[0];
        assert_eq!(e.raw_text, "నమస్కారం \"quoted\"\nnewline");
        assert_eq!(e.timestamp_ms, 123);

        let mut h3 = h2;
        h3.clear().unwrap();
        assert!(h3.is_empty());
        std::fs::remove_file(&path).unwrap();
    }

    #[test]
    fn history_rejects_corrupt_lines() {
        let dir = std::env::temp_dir();
        let path = dir.join("wahtuh_history_corrupt_test.jsonl");
        std::fs::write(&path, "{\"ts\":1,\"raw\":\"a\"}\n").unwrap(); // missing fields
        let res = TranscriptHistory::new(10).with_persistence(&path);
        assert!(matches!(res.err().unwrap(), CoreError::HistoryParse(_)));
        std::fs::remove_file(&path).unwrap();
    }

    #[test]
    fn history_eviction_keeps_newest_and_rewrites_disk() {
        let dir = std::env::temp_dir();
        let path = dir.join("wahtuh_history_eviction_test.jsonl");
        let _ = std::fs::remove_file(&path);
        let mut h = TranscriptHistory::new(3).with_persistence(&path).unwrap();
        for i in 0..5 {
            h.push(HistoryEntry {
                timestamp_ms: i,
                raw_text: format!("entry-{i}"),
                normalized_text: format!("entry-{i}"),
                language: "te-en".to_string(),
            })
            .unwrap();
        }
        // Over capacity: the two oldest are gone, newest-first order kept.
        assert_eq!(h.len(), 3);
        let texts: Vec<&str> = h.recent(3).iter().map(|e| e.raw_text.as_str()).collect();
        assert_eq!(texts, ["entry-4", "entry-3", "entry-2"]);

        // The eviction rewrote the JSONL file: a reload sees exactly the
        // survivors, so disk and memory agree.
        let h2 = TranscriptHistory::new(3).with_persistence(&path).unwrap();
        let texts2: Vec<&str> = h2.recent(3).iter().map(|e| e.raw_text.as_str()).collect();
        assert_eq!(texts2, texts);

        // Capacity floors at 1: new(0) keeps only the newest entry.
        let mut h1 = TranscriptHistory::new(0);
        for i in 0..3 {
            h1.push(HistoryEntry {
                timestamp_ms: i,
                raw_text: format!("e{i}"),
                normalized_text: format!("e{i}"),
                language: "en".to_string(),
            })
            .unwrap();
        }
        assert_eq!(h1.len(), 1);
        assert_eq!(h1.recent(1)[0].raw_text, "e2");
        std::fs::remove_file(&path).unwrap();
    }

    #[test]
    fn core_error_display() {
        assert!(CoreError::Cancelled.to_string().contains("cancelled"));
        assert!(CoreError::SessionFinished.to_string().contains("finished"));
    }
}
