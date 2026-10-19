//! End-to-end pipeline tests for Wahtuh Pho (Phase C1).
//!
//! These tests exercise the full dictation path across crate boundaries:
//! [`AudioConfig`] validation → [`Chunker`] over synthetic PCM →
//! [`MockEngine`] transcription → [`normalize`] →
//! [`DictationSession::finish`] → [`TranscriptHistory`] persist/reload.
//! The mock engine stands in for the real on-device backend (see
//! `docs/ENGINE_EVALUATION.md`); the pipeline shape is what is under test,
//! not acoustic accuracy.

use std::path::PathBuf;

use wahtuh_core::audio::{AudioConfig, AudioError, BoundedAudioBuffer, Chunker};
use wahtuh_core::config::{Config, ConfigError, EngineSelection, ProviderCredentials};
use wahtuh_core::normalize::{
    is_telugu, normalize, Dictionary, NormalizeOptions, NORMALIZER_VERSION,
};
use wahtuh_core::transcribe::{CancellationToken, Engine, LanguageHint, MockEngine};
use wahtuh_core::{now_ms, CoreError, DictationSession, HistoryEntry, TranscriptHistory};

/// 16 kHz mono, the canonical session format.
fn cfg16k() -> AudioConfig {
    AudioConfig::new(16_000, 1).unwrap()
}

/// Deterministic synthetic "audio": a ramp with a seed offset, so different
/// seeds hash to different [`MockEngine`] utterances.
fn synth_pcm(seed: i16, secs: u64) -> Vec<i16> {
    let n = secs as usize * 16_000;
    (0..n).map(|i| seed.wrapping_add((i % 1000) as i16)).collect()
}

fn mock_session() -> DictationSession<MockEngine> {
    let mut c = Config::default();
    c.engine = EngineSelection::Mock;
    DictationSession::new(c, MockEngine::new()).unwrap()
}

/// Scratch dir unique to this test-binary process and test name.
fn scratch(name: &str) -> PathBuf {
    let d = std::env::temp_dir().join(format!("wahtuh_e2e_{name}_{}", std::process::id()));
    std::fs::create_dir_all(&d).unwrap();
    d
}

/// Find a 1s seed whose [`MockEngine`] output carries `hint`, probing the
/// real engine so the test does not depend on its private hash ordering.
fn seed_for_hint(hint: LanguageHint) -> Vec<i16> {
    let mut engine = MockEngine::new();
    engine.init().unwrap();
    let cancel = CancellationToken::new();
    for seed in 0i16..10_000 {
        let s = synth_pcm(seed, 1);
        let t = engine.transcribe_chunk(&s, &cancel).unwrap();
        if t.language_hint == hint {
            return s;
        }
    }
    panic!("no 1s seed produced language hint {hint:?}");
}

#[test]
fn full_pipeline_audio_to_persisted_history() {
    // 1. AudioConfig validation.
    let cfg = cfg16k();
    assert_eq!(cfg.samples_per_second(), 16_000);
    assert!(AudioConfig::new(11_025, 1).is_err());

    // 2. Chunk 60s of synthetic audio with the production window/overlap,
    //    fed in 10s slices like a streaming mic source.
    let mut chunker = Chunker::new(cfg, 30, 5).unwrap();
    let mut session = mock_session();
    let mut fed = 0u64;
    for seed in 0..6 {
        for chunk in chunker.push(&synth_pcm(seed, 10)) {
            session.feed_chunk(&chunk.samples).unwrap();
            fed += 1;
        }
    }
    // 60s with 30s windows / 5s overlap: [0..30], [25..55], then the tail
    // [50..60] (the overlap region is retained, so nothing is lost).
    if let Some(tail) = chunker.flush() {
        assert_eq!(tail.start_sample, 50 * 16_000);
        assert_eq!(tail.samples.len(), 10 * 16_000);
        session.feed_chunk(&tail.samples).unwrap();
        fed += 1;
    }
    assert_eq!(fed, 3);

    // 3. Finish: raw engine output and normalized text side by side.
    let dict = Dictionary::new(); // empty: exercises the dictionary path
    let result = session.finish(Some(&dict)).unwrap();
    assert_eq!(result.chunk_count, 3);
    assert!(!result.raw.text.is_empty());
    assert_eq!(result.normalized.original, result.raw.text);
    assert_eq!(result.normalized.version, NORMALIZER_VERSION);

    // 4. Persist to history, then reload from a fresh handle.
    let dir = scratch("pipeline");
    let path = dir.join("history.jsonl");
    let mut history = TranscriptHistory::new(50)
        .with_persistence(&path)
        .unwrap();
    history
        .push(HistoryEntry {
            timestamp_ms: now_ms(),
            raw_text: result.raw.text.clone(),
            normalized_text: result.normalized.text.clone(),
            language: "te-en".to_string(),
        })
        .unwrap();

    let reloaded = TranscriptHistory::new(50).with_persistence(&path).unwrap();
    assert_eq!(reloaded.len(), 1);
    let entry = &reloaded.recent(1)[0];
    assert_eq!(entry.raw_text, result.raw.text);
    assert_eq!(entry.normalized_text, result.normalized.text);
    assert_eq!(entry.language, "te-en");

    std::fs::remove_dir_all(&dir).unwrap();
}

#[test]
fn cancellation_mid_session_aborts_pipeline() {
    let mut session = mock_session();
    // First chunk flows normally, with a live preview.
    let partial = session.feed_chunk(&synth_pcm(1, 1)).unwrap();
    assert!(partial.is_some(), "mock engine should offer a preview");

    // Cancel from the "UI thread"; the token is shared with the engine.
    session.cancel();

    // No further audio is transcribed, and the session cannot finish.
    assert!(matches!(
        session.feed_chunk(&synth_pcm(2, 1)).unwrap_err(),
        CoreError::Cancelled
    ));
    assert!(matches!(
        session.finish(None).unwrap_err(),
        CoreError::Cancelled
    ));
}

#[test]
fn cancellation_token_is_shared_and_idempotent() {
    let tok = CancellationToken::new();
    assert!(!tok.is_cancelled());
    let clone = tok.clone();
    tok.cancel();
    assert!(clone.is_cancelled());
    tok.cancel(); // idempotent, no panic
    assert!(tok.is_cancelled());
}

#[test]
fn buffer_limit_error_path_never_drops_silently() {
    let cfg = cfg16k();
    let mut buf = BoundedAudioBuffer::new(cfg, 1).unwrap(); // 1s ceiling
    buf.push_samples(&synth_pcm(0, 1)).unwrap();
    let err = buf.push_samples(&synth_pcm(1, 1)).unwrap_err();
    assert!(
        matches!(err, AudioError::BufferLimitExceeded { .. }),
        "expected BufferLimitExceeded, got {err}"
    );
    // The failed push wrote nothing: the original second is intact.
    assert_eq!(buf.len(), 16_000);
    assert_eq!(buf.buffered_seconds(), 1);
}

#[test]
fn local_only_blocks_cloud_provider_selection() {
    // local_only (the default) + Cloud engine: rejected at config validation.
    let mut bad = Config::default();
    bad.engine = EngineSelection::Cloud;
    assert!(matches!(
        bad.validate().unwrap_err(),
        ConfigError::CloudEngineWithLocalOnly
    ));
    // DictationSession runs the same validation before engine init, so no
    // session — and no audio path — exists for this configuration.
    let res: Result<(), CoreError> = DictationSession::new(bad, MockEngine::new()).map(|_| ());
    assert!(matches!(res.unwrap_err(), CoreError::Config(_)));

    // local_only + credentials: also rejected before any session starts.
    let mut bad2 = Config::default();
    bad2.provider_credentials = Some(ProviderCredentials {
        provider: "groq".to_string(),
        api_key: "runtime-only-key".to_string(),
    });
    let res2: Result<(), CoreError> =
        DictationSession::new(bad2, MockEngine::new()).map(|_| ());
    assert!(matches!(res2.unwrap_err(), CoreError::Config(_)));
}

#[test]
fn telugu_and_codeswitch_survive_normalization() {
    let opts = NormalizeOptions::default();

    // Telugu-only: no Latin rule can touch it; output must be identical.
    let telugu = "నమస్కారం ఇది ఒక పరీక్ష";
    let n = normalize(telugu, &opts, None);
    assert_eq!(n.text, telugu);
    assert!(is_telugu('న'));
    assert!(!is_telugu('h'));

    // Punctuation rules still apply *around* Telugu script, without
    // altering the script itself.
    let n2 = normalize("నమస్కారం , ఇది ఒక పరీక్ష .", &opts, None);
    assert_eq!(n2.text, "నమస్కారం, ఇది ఒక పరీక్ష.");

    // Mixed script through the real engine + session path.
    let cs_pcm = seed_for_hint(LanguageHint::CodeSwitch); // "hello నమస్కారం this is మిక్స్డ్"
    let te_pcm = seed_for_hint(LanguageHint::Telugu); // "నమస్కారం ఇది ఒక పరీక్ష"

    let mut session = mock_session();
    session.feed_chunk(&cs_pcm).unwrap();
    session.feed_chunk(&te_pcm).unwrap();
    let result = session.finish(None).unwrap();

    let text = &result.normalized.text;
    // Script preserved verbatim; Latin sentence capitalization applied.
    assert!(text.starts_with("Hello నమస్కారం this is మిక్స్డ్"), "got: {text}");
    for word in ["నమస్కారం", "ఇది", "ఒక", "పరీక్ష", "మిక్స్డ్"] {
        assert!(text.contains(word), "lost token {word} in: {text}");
    }
    // Raw engine output is untouched by normalization (reversibility).
    assert!(result.raw.text.contains("hello నమస్కారం"));
}
