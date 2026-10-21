//! Audio capture abstraction for Wahtuh Pho.
//!
//! This crate owns the PCM plumbing between the platform microphone (Android
//! `AudioRecord` via JNI, or a desktop source in tests) and the transcription
//! engine. It deliberately knows nothing about engines, models, or text.
//!
//! Key guarantees:
//! - [`PcmSample`] is `i16` mono/stereo interleaved PCM, the format both
//!   whisper.cpp and sherpa-onnx expect on the FFI boundary.
//! - [`Chunker`] slices a sample stream into fixed windows with overlap, so a
//!   word split across a window edge still gets full context in one chunk.
//! - [`BoundedAudioBuffer`] enforces a hard memory ceiling and returns
//!   [`AudioError::BufferLimitExceeded`] instead of silently dropping audio.

use std::collections::VecDeque;
use std::fmt;

/// 16-bit PCM sample, interleaved across channels.
pub type PcmSample = i16;

/// Sample rate every bundled engine expects. Engines resample internally, but
/// normalizing to 16 kHz here keeps chunk math and model input consistent.
pub const DEFAULT_SAMPLE_RATE: u32 = 16_000;

/// Sample rates we accept from a platform source.
const SUPPORTED_SAMPLE_RATES: [u32; 4] = [8_000, 16_000, 44_100, 48_000];

/// Audio format descriptor.
///
/// Invariants are checked in [`AudioConfig::new`]; a value of this type is
/// always usable for chunk math.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct AudioConfig {
    /// Samples per second, per channel.
    pub sample_rate: u32,
    /// 1 = mono, 2 = stereo interleaved.
    pub channels: u16,
}

impl AudioConfig {
    /// Build a config, rejecting unsupported rates and channel counts.
    pub fn new(sample_rate: u32, channels: u16) -> Result<Self, AudioError> {
        if !SUPPORTED_SAMPLE_RATES.contains(&sample_rate) {
            return Err(AudioError::InvalidSampleRate(sample_rate));
        }
        if channels == 0 || channels > 2 {
            return Err(AudioError::InvalidChannels(channels));
        }
        Ok(Self {
            sample_rate,
            channels,
        })
    }

    /// Total samples (all channels) per second.
    pub fn samples_per_second(&self) -> u64 {
        self.sample_rate as u64 * self.channels as u64
    }

    /// Bytes per second of `i16` PCM.
    pub fn bytes_per_second(&self) -> u64 {
        self.samples_per_second() * 2
    }

    /// Convert a mono slice to stereo-interleaved by duplicating samples.
    /// Handy when a model wants stereo but the mic gave us mono.
    pub fn mono_to_stereo(mono: &[PcmSample]) -> Vec<PcmSample> {
        let mut out = Vec::with_capacity(mono.len() * 2);
        for &s in mono {
            out.push(s);
            out.push(s);
        }
        out
    }
}

/// One window of audio handed to the engine.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AudioChunk {
    /// Interleaved PCM samples for this window.
    pub samples: Vec<PcmSample>,
    /// Offset of the first sample in the session's sample timeline.
    pub start_sample: u64,
    /// Format the samples are in.
    pub config: AudioConfig,
}

impl AudioChunk {
    /// Duration of this chunk in milliseconds.
    pub fn duration_ms(&self) -> u64 {
        let frames = self.samples.len() as u64 / self.config.channels as u64;
        frames * 1000 / self.config.sample_rate as u64
    }
}

/// Slices a stream of [`PcmSample`]s into overlapping windows.
///
/// `window_secs` is the window length, `overlap_secs` the overlap between
/// consecutive windows (`step = window - overlap`). A final short window is
/// emitted only if it holds at least one second of audio, avoiding a stub
/// chunk that would confuse the engine's VAD.
pub struct Chunker {
    config: AudioConfig,
    window_samples: usize,
    step_samples: usize,
    buf: VecDeque<PcmSample>,
    next_start: u64,
}

impl Chunker {
    /// Create a chunker. `overlap_secs` must be strictly less than
    /// `window_secs`.
    pub fn new(
        config: AudioConfig,
        window_secs: u64,
        overlap_secs: u64,
    ) -> Result<Self, AudioError> {
        if window_secs == 0 {
            return Err(AudioError::InvalidWindow(window_secs, overlap_secs));
        }
        if overlap_secs >= window_secs {
            return Err(AudioError::InvalidWindow(window_secs, overlap_secs));
        }
        let per_sec = config.samples_per_second() as usize;
        Ok(Self {
            config,
            window_samples: per_sec * window_secs as usize,
            step_samples: per_sec * (window_secs - overlap_secs) as usize,
            buf: VecDeque::new(),
            next_start: 0,
        })
    }

    /// Feed raw samples into the chunker. Returns every complete window that
    /// became available. Call [`Chunker::flush`] at end of stream for the tail.
    pub fn push(&mut self, samples: &[PcmSample]) -> Vec<AudioChunk> {
        self.buf.extend(samples.iter().copied());
        let mut out = Vec::new();
        while self.buf.len() >= self.window_samples {
            let samples: Vec<PcmSample> = self.buf.iter().take(self.window_samples).copied().collect();
            out.push(AudioChunk {
                samples,
                start_sample: self.next_start,
                config: self.config,
            });
            for _ in 0..self.step_samples {
                self.buf.pop_front();
            }
            self.next_start += self.step_samples as u64;
        }
        out
    }

    /// Emit the trailing partial window (>= 1s of audio), if any.
    pub fn flush(&mut self) -> Option<AudioChunk> {
        let one_sec = self.config.samples_per_second() as usize;
        if self.buf.len() >= one_sec {
            let samples: Vec<PcmSample> = self.buf.drain(..).collect();
            let chunk = AudioChunk {
                samples,
                start_sample: self.next_start,
                config: self.config,
            };
            self.next_start += chunk.samples.len() as u64;
            Some(chunk)
        } else {
            self.buf.clear();
            None
        }
    }
}

/// Fixed-capacity FIFO. Used for jitter buffering between the mic callback
/// and the chunker; the oldest samples are evicted on overflow, which is the
/// *documented* policy here (unlike [`BoundedAudioBuffer`], where overflow is
/// an error).
pub struct RingBuffer<T> {
    buf: VecDeque<T>,
    capacity: usize,
}

impl<T> RingBuffer<T> {
    /// Capacity must be > 0.
    pub fn new(capacity: usize) -> Result<Self, AudioError> {
        if capacity == 0 {
            return Err(AudioError::InvalidCapacity);
        }
        Ok(Self {
            buf: VecDeque::with_capacity(capacity),
            capacity,
        })
    }

    /// Push, evicting the oldest element when full. Returns the evicted
    /// element, or `None` when there was room.
    pub fn push(&mut self, item: T) -> Option<T> {
        let evicted = if self.buf.len() == self.capacity {
            self.buf.pop_front()
        } else {
            None
        };
        self.buf.push_back(item);
        evicted
    }

    pub fn pop(&mut self) -> Option<T> {
        self.buf.pop_front()
    }

    pub fn len(&self) -> usize {
        self.buf.len()
    }

    pub fn is_empty(&self) -> bool {
        self.buf.is_empty()
    }

    pub fn is_full(&self) -> bool {
        self.buf.len() == self.capacity
    }
}

/// Accumulates session audio behind a hard memory ceiling.
///
/// Unlike [`RingBuffer`], exceeding the limit is an **error**, never a silent
/// drop: dictation must never quietly lose the user's speech. The caller
/// decides how to recover (warn the user, end the session, spill to disk).
pub struct BoundedAudioBuffer {
    config: AudioConfig,
    max_samples: usize,
    samples: VecDeque<PcmSample>,
}

impl BoundedAudioBuffer {
    /// `max_seconds` caps buffered audio; typical sessions use 5 minutes
    /// (Android) to 20 minutes (desktop) of 16 kHz mono = ~9.6–38 MB.
    pub fn new(config: AudioConfig, max_seconds: u64) -> Result<Self, AudioError> {
        if max_seconds == 0 {
            return Err(AudioError::InvalidCapacity);
        }
        let max_samples = config.samples_per_second() as usize * max_seconds as usize;
        Ok(Self {
            config,
            max_samples,
            samples: VecDeque::new(),
        })
    }

    /// Append samples. Fails with [`AudioError::BufferLimitExceeded`] if the
    /// buffer cannot hold them — nothing is partially written.
    pub fn push_samples(&mut self, samples: &[PcmSample]) -> Result<(), AudioError> {
        if self.samples.len() + samples.len() > self.max_samples {
            return Err(AudioError::BufferLimitExceeded {
                limit_seconds: self.max_samples as u64 / self.config.samples_per_second(),
                attempted_samples: samples.len(),
            });
        }
        self.samples.extend(samples.iter().copied());
        Ok(())
    }

    /// Drain everything buffered, resetting the buffer.
    pub fn drain(&mut self) -> Vec<PcmSample> {
        self.samples.drain(..).collect()
    }

    pub fn len(&self) -> usize {
        self.samples.len()
    }

    pub fn is_empty(&self) -> bool {
        self.samples.is_empty()
    }

    /// Buffered audio duration in whole seconds.
    pub fn buffered_seconds(&self) -> u64 {
        self.samples.len() as u64 / self.config.samples_per_second()
    }
}

/// Errors from the audio layer.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum AudioError {
    InvalidSampleRate(u32),
    InvalidChannels(u16),
    InvalidWindow(u64 /* window_secs */, u64 /* overlap_secs */),
    InvalidCapacity,
    /// The hard memory ceiling was hit. Fields are for the caller to build a
    /// user-facing message; no audio was dropped silently.
    BufferLimitExceeded {
        limit_seconds: u64,
        attempted_samples: usize,
    },
}

impl fmt::Display for AudioError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AudioError::InvalidSampleRate(r) => write!(f, "unsupported sample rate: {r} Hz"),
            AudioError::InvalidChannels(c) => write!(f, "unsupported channel count: {c}"),
            AudioError::InvalidWindow(w, o) => {
                write!(f, "invalid chunk window: window={w}s overlap={o}s (need 0 < overlap < window)")
            }
            AudioError::InvalidCapacity => write!(f, "capacity must be greater than zero"),
            AudioError::BufferLimitExceeded {
                limit_seconds,
                attempted_samples,
            } => write!(
                f,
                "audio buffer limit of {limit_seconds}s exceeded (tried to add {attempted_samples} samples)"
            ),
        }
    }
}

impl std::error::Error for AudioError {}

#[cfg(test)]
mod tests {
    use super::*;

    fn mono16k() -> AudioConfig {
        AudioConfig::new(16_000, 1).unwrap()
    }

    #[test]
    fn config_rejects_bad_values() {
        assert_eq!(
            AudioConfig::new(11_025, 1),
            Err(AudioError::InvalidSampleRate(11_025))
        );
        assert_eq!(AudioConfig::new(16_000, 0), Err(AudioError::InvalidChannels(0)));
        assert_eq!(AudioConfig::new(16_000, 3), Err(AudioError::InvalidChannels(3)));
    }

    #[test]
    fn config_math() {
        let c = AudioConfig::new(16_000, 2).unwrap();
        assert_eq!(c.samples_per_second(), 32_000);
        assert_eq!(c.bytes_per_second(), 64_000);
    }

    #[test]
    fn mono_to_stereo_duplicates() {
        assert_eq!(AudioConfig::mono_to_stereo(&[1, -2]), vec![1, 1, -2, -2]);
    }

    #[test]
    fn chunker_windows_with_overlap() {
        let cfg = mono16k();
        let mut ch = Chunker::new(cfg, 30, 5).unwrap();
        // 60s of audio -> windows [0..30], [25..55], tail [50..60] on flush.
        let samples = vec![0i16; 60 * 16_000];
        let chunks = ch.push(&samples);
        assert_eq!(chunks.len(), 2);
        assert_eq!(chunks[0].start_sample, 0);
        assert_eq!(chunks[0].samples.len(), 30 * 16_000);
        assert_eq!(chunks[1].start_sample, 25 * 16_000);
        let tail = ch.flush().unwrap();
        assert_eq!(tail.start_sample, 50 * 16_000);
        assert_eq!(tail.samples.len(), 10 * 16_000);
        assert_eq!(tail.duration_ms(), 10_000);
    }

    #[test]
    fn chunker_rejects_bad_window() {
        let cfg = mono16k();
        assert!(Chunker::new(cfg, 30, 30).is_err());
        assert!(Chunker::new(cfg, 0, 0).is_err());
    }

    #[test]
    fn chunker_drops_tiny_tail() {
        // With overlap, the tail after the last full window always includes
        // the overlap region, so use overlap=0 here to isolate the <1s drop.
        let cfg = mono16k();
        let mut ch = Chunker::new(cfg, 30, 0).unwrap();
        let chunks = ch.push(&vec![0i16; 30 * 16_000 + 100]); // 30s + epsilon
        assert_eq!(chunks.len(), 1);
        assert!(ch.flush().is_none()); // < 1s tail is dropped, documented
    }

    #[test]
    fn chunker_flush_emits_usable_tail() {
        // With overlap, the tail carries the overlap region: no audio lost.
        let cfg = mono16k();
        let mut ch = Chunker::new(cfg, 30, 5).unwrap();
        let chunks = ch.push(&vec![0i16; 30 * 16_000 + 100]);
        assert_eq!(chunks.len(), 1);
        let tail = ch.flush().unwrap();
        assert_eq!(tail.start_sample, 25 * 16_000);
        assert_eq!(tail.samples.len(), 5 * 16_000 + 100);
    }

    #[test]
    fn ring_buffer_evicts_oldest() {
        let mut rb = RingBuffer::new(2).unwrap();
        assert_eq!(rb.push(1), None);
        assert_eq!(rb.push(2), None);
        assert!(rb.is_full());
        assert_eq!(rb.push(3), Some(1));
        assert_eq!(rb.pop(), Some(2));
        assert_eq!(rb.pop(), Some(3));
        assert!(rb.is_empty());
    }

    #[test]
    fn bounded_buffer_errors_instead_of_dropping() {
        let cfg = mono16k();
        let mut b = BoundedAudioBuffer::new(cfg, 2).unwrap(); // 2s = 32000 samples
        b.push_samples(&vec![0i16; 32_000]).unwrap();
        let err = b.push_samples(&vec![0i16; 1]).unwrap_err();
        assert_eq!(
            err,
            AudioError::BufferLimitExceeded {
                limit_seconds: 2,
                attempted_samples: 1
            }
        );
        // Failed push wrote nothing: the original 2s are intact.
        assert_eq!(b.len(), 32_000);
        assert_eq!(b.buffered_seconds(), 2);
        assert_eq!(b.drain().len(), 32_000);
        assert!(b.is_empty());
    }

    #[test]
    fn chunker_overlap_exact_on_boundaries() {
        // Exact-boundary feeding: exactly one window, then exactly one step
        // at a time. The overlap region of consecutive chunks must be
        // sample-identical, and no audio may be lost at the boundaries.
        let cfg = mono16k();
        let mut ch = Chunker::new(cfg, 4, 1).unwrap(); // 4s window, 3s step
        let ramp: Vec<i16> = (0..12 * 16_000).map(|i| (i % 1000) as i16).collect();

        let c1 = ch.push(&ramp[0..4 * 16_000]);
        assert_eq!(c1.len(), 1);
        assert_eq!(c1[0].start_sample, 0);
        assert_eq!(c1[0].samples.len(), 4 * 16_000);

        let c2 = ch.push(&ramp[4 * 16_000..7 * 16_000]);
        assert_eq!(c2.len(), 1);
        assert_eq!(c2[0].start_sample, 3 * 16_000);
        // Overlap: chunk2's first 1s == chunk1's last 1s, sample for sample.
        assert_eq!(&c2[0].samples[..16_000], &c1[0].samples[3 * 16_000..]);

        let c3 = ch.push(&ramp[7 * 16_000..10 * 16_000]);
        assert_eq!(c3.len(), 1);
        assert_eq!(c3[0].start_sample, 6 * 16_000);
        assert_eq!(&c3[0].samples[..16_000], &c2[0].samples[3 * 16_000..]);

        // Last 2s: no full window forms, so flush emits the [9..12] tail.
        assert!(ch.push(&ramp[10 * 16_000..]).is_empty());
        let tail = ch.flush().unwrap();
        assert_eq!(tail.start_sample, 9 * 16_000);
        assert_eq!(tail.samples, ramp[9 * 16_000..]); // zero loss at the edge
    }

    #[test]
    fn chunker_exact_window_retains_overlap_for_flush() {
        // Feeding exactly one window emits one chunk; the overlap region
        // stays buffered and comes back verbatim on flush.
        let cfg = mono16k();
        let mut ch = Chunker::new(cfg, 30, 5).unwrap();
        let audio: Vec<i16> = (0..30 * 16_000).map(|i| (i % 1000) as i16).collect();
        let chunks = ch.push(&audio);
        assert_eq!(chunks.len(), 1);
        assert_eq!(chunks[0].start_sample, 0);
        let tail = ch.flush().unwrap();
        assert_eq!(tail.start_sample, 25 * 16_000);
        assert_eq!(tail.samples, audio[25 * 16_000..]);
    }

    #[test]
    fn error_display_is_human_readable() {
        let e = AudioError::BufferLimitExceeded {
            limit_seconds: 300,
            attempted_samples: 5,
        };
        assert!(e.to_string().contains("300s"));
    }
}
