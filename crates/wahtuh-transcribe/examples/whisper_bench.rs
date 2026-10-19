//! Benchmark harness for the whisper.cpp transcription backend.
//!
//! Usage: `whisper_bench <model.bin> <wav> [lang] [threads]`
//!
//! - `<model.bin>`: whisper ggml model file (loaded from disk only — no network).
//! - `<wav>`: 16-bit PCM mono 16 kHz WAV; anything else is rejected.
//! - `[lang]`: optional whisper language code, e.g. `en`, `te` (default: auto).
//! - `[threads]`: optional inference thread count (default: logical CPU count).
//!
//! Prints exactly ONE line of JSON to stdout:
//! `{"text": "...", "load_ms": N, "latency_ms": N, "segments": [{"t0_ms":..,"t1_ms":..,"text":".."}]}`
//! Model load time is measured separately from inference latency. Errors go to
//! stderr with a non-zero exit code.

use std::path::Path;
use std::time::Instant;

use wahtuh_transcribe::{CancellationToken, Engine, WhisperCppEngine};

fn main() {
    if let Err(why) = run() {
        eprintln!("whisper_bench: error: {why}");
        std::process::exit(1);
    }
}

fn run() -> Result<(), String> {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 3 {
        return Err(format!("usage: {} <model.bin> <wav> [lang] [threads]", args[0]));
    }
    let model_path = Path::new(&args[1]);
    let wav_path = Path::new(&args[2]);
    let language: Option<String> = args.get(3).filter(|s| !s.is_empty()).cloned();
    let threads: Option<usize> = args
        .get(4)
        .map(|s| s.parse::<usize>().map_err(|_| format!("bad thread count: {s:?}")))
        .transpose()?;

    if !model_path.is_file() {
        return Err(format!("model not found: {}", model_path.display()));
    }
    let samples = read_wav_i16_mono_16k(wav_path)?;

    let mut engine = WhisperCppEngine::with_model_and_language(model_path, language);
    if let Some(t) = threads {
        engine.set_threads(t);
    }

    let t0 = Instant::now();
    engine.init().map_err(|e| e.to_string())?;
    let load_ms = t0.elapsed().as_millis() as u64;

    let cancel = CancellationToken::new();
    let t1 = Instant::now();
    let transcript = engine
        .transcribe_chunk(&samples, &cancel)
        .map_err(|e| e.to_string())?;
    let latency_ms = t1.elapsed().as_millis() as u64;

    let segments: Vec<serde_json::Value> = transcript
        .segments
        .iter()
        .map(|s| {
            serde_json::json!({
                "t0_ms": s.start_ms,
                "t1_ms": s.end_ms,
                "text": s.text,
            })
        })
        .collect();
    let out = serde_json::json!({
        "text": transcript.text,
        "load_ms": load_ms,
        "latency_ms": latency_ms,
        "segments": segments,
    });
    println!(
        "{}",
        serde_json::to_string(&out).map_err(|e| e.to_string())?
    );
    Ok(())
}

/// Strict WAV reader: 16-bit PCM, mono, 16 kHz — anything else is rejected
/// with a clear error so benchmarks never silently resample.
fn read_wav_i16_mono_16k(path: &Path) -> Result<Vec<i16>, String> {
    let mut reader = hound::WavReader::open(path)
        .map_err(|e| format!("cannot open {}: {e}", path.display()))?;
    let spec = reader.spec();
    if spec.channels != 1 {
        return Err(format!(
            "{}: expected mono, got {} channels",
            path.display(),
            spec.channels
        ));
    }
    if spec.sample_rate != 16_000 {
        return Err(format!(
            "{}: expected 16000 Hz, got {} Hz",
            path.display(),
            spec.sample_rate
        ));
    }
    if spec.bits_per_sample != 16 {
        return Err(format!(
            "{}: expected 16-bit PCM, got {} bits per sample",
            path.display(),
            spec.bits_per_sample
        ));
    }
    reader
        .samples::<i16>()
        .collect::<Result<Vec<_>, _>>()
        .map_err(|e| format!("{}: failed to read samples: {e}", path.display()))
}
