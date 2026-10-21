//! Optional cloud provider adapters for Wahtuh Pho.
//!
//! These adapters exist for the **opt-in** cloud fallback path only. The
//! default product configuration is [`wahtuh_config::Config`] with
//! `local_only = true`, under which this crate's code paths are unreachable
//! (see `docs/ARCHITECTURE.md` — the local-only guarantee, and
//! `docs/PROVIDERS.md` for the per-provider setup guide).
//!
//! # Secrets policy (read this before touching this file)
//!
//! API keys are **runtime-only**: they arrive as `&str` arguments on
//! [`HttpRequest`] builders and [`TranscriptionRequest::new`] (from an env
//! var, the OS keychain, or a user-typed settings field) and are never stored
//! in source, never baked into the binary, and never written to disk by this
//! crate. They are never held in a struct field beyond the call that builds
//! the request, and [`HttpRequest`]'s `Debug` impl redacts key-bearing headers
//! so a stray log line cannot leak them. There is deliberately no `Default`
//! API key, no `from_env` magic inside the adapters, and no keychain access
//! here — the app layer owns secret storage (see `docs/PROVIDERS.md` for the
//! Android Keystore design).
//!
//! This is enforced by the `no_hardcoded_secrets` unit test below, which
//! scans this file's own source for key-like prefixes and fails the build if
//! one appears.
//!
//! # Transport policy
//!
//! This crate is **std-only** (no `reqwest`, no `serde`). Adapters validate
//! requests and produce a fully-formed [`HttpRequest`] — method, URL,
//! headers, body — but never execute it: [`Provider::transcribe`] returns
//! [`ProviderError::TransportNotConfigured`] until the app layer links a
//! transport. JSON bodies are built with [`escape_json_string`], multipart
//! bodies with the internal `MultipartBody` builder, and WAV payloads with
//! the internal PCM encoder. A real HTTP client would replace the transport
//! only; the typed request/response shapes stay stable.
//!
//! # Model verification
//!
//! [`Provider::supported_models`] lists **only model names verified against
//! current official provider documentation** (see `docs/PROVIDERS.md` for the
//! source URL + check date of every entry). If a doc page cannot be fetched,
//! the name stays out of the list — never invent model names.

use std::fmt;

/// A cloud transcription provider.
///
/// The common interface every adapter implements: a stable machine name, the
/// verified model allow-list, request validation, request building, and the
/// (transport-gated) transcribe entry point.
pub trait Provider {
    /// Stable machine name: `"groq"`, `"gemini"`, `"openai"`.
    fn name(&self) -> &'static str;

    /// Speech models this adapter knows how to call. **Only verified model
    /// names** (see module docs). Used to populate a settings dropdown and to
    /// reject unknown models in [`Provider::validate`].
    fn supported_models(&self) -> Vec<String>;

    /// Check that the request is well-formed for this provider: the API key
    /// is non-empty and the model is in [`Provider::supported_models`].
    /// Runs before any bytes are built, so a bad request fails fast without
    /// touching the network.
    fn validate(&self, req: &TranscriptionRequest) -> Result<(), ProviderError>;

    /// Build the exact HTTP request this provider documents (method, URL,
    /// headers, body) **without executing it**. The caller links a transport
    /// to send it; see [`HttpRequest`].
    fn build_request(&self, req: &TranscriptionRequest) -> Result<HttpRequest, ProviderError>;

    /// Transcribe PCM audio with this provider. The request carries the API
    /// key supplied at runtime by the caller.
    ///
    /// Note: this crate ships **no HTTP transport** (std-only, offline
    /// build). Adapters validate the request and return
    /// [`ProviderError::TransportNotConfigured`] until the app layer links a
    /// transport. This keeps the workspace compiling with zero network
    /// dependencies while the request/response types stay stable.
    fn transcribe(&self, req: &TranscriptionRequest) -> Result<TranscriptionResponse, ProviderError> {
        self.validate(req)?;
        Err(ProviderError::TransportNotConfigured)
    }
}

/// A provider transcription request. Construct with
/// [`TranscriptionRequest::new`], which rejects empty keys up front.
#[derive(Debug, Clone)]
pub struct TranscriptionRequest {
    /// 16-bit PCM audio, interleaved. Adapters resample/encode as needed.
    pub audio: Vec<i16>,
    pub sample_rate: u32,
    /// Model id as listed by [`Provider::supported_models`].
    pub model: String,
    /// Optional ISO-639-1 language hint (e.g. `"en"`, `"te"`). Passed to
    /// providers that accept a `language` parameter (Groq, OpenAI).
    pub language: Option<String>,
    /// Runtime-supplied API key. Never logged, never persisted by this crate.
    api_key: String,
}

impl TranscriptionRequest {
    /// Build a request. Fails with [`ProviderError::MissingKey`] when the key
    /// is empty or whitespace — fail fast instead of sending an
    /// unauthenticated request.
    pub fn new(
        audio: Vec<i16>,
        sample_rate: u32,
        model: impl Into<String>,
        api_key: impl Into<String>,
    ) -> Result<Self, ProviderError> {
        let api_key = api_key.into();
        if api_key.trim().is_empty() {
            return Err(ProviderError::MissingKey);
        }
        Ok(Self {
            audio,
            sample_rate,
            model: model.into(),
            language: None,
            api_key,
        })
    }

    /// Attach an ISO-639-1 language hint (e.g. `"en"` for English, `"te"` for
    /// Telugu). Improves accuracy on providers that accept it; ignored by
    /// those that don't.
    pub fn with_language(mut self, language: impl Into<String>) -> Self {
        self.language = Some(language.into());
        self
    }

    /// Borrow the key for building the Authorization header. `pub(crate)` on
    /// purpose: only adapters inside this crate may touch the key, and they
    /// must not log or store it.
    pub(crate) fn api_key(&self) -> &str {
        &self.api_key
    }
}

/// A provider transcription response.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TranscriptionResponse {
    /// The transcript text, verbatim as returned by the provider. Any
    /// AI cleanup/formatting is a separate, opt-in step — never applied here.
    pub text: String,
    /// BCP-47 language tag reported by the provider, if any.
    pub language: Option<String>,
}

/// Provider-layer failures.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ProviderError {
    /// No API key was supplied at runtime. Keys are runtime-only; this crate
    /// never invents or stores one.
    MissingKey,
    /// The requested model is not in [`Provider::supported_models`]. Carries
    /// the rejected model id.
    InvalidModel(String),
    /// The requested language code is not accepted by this adapter. Carries
    /// the rejected code. Adapters that place the language in the request
    /// URL (Deepgram) validate it to prevent query-string injection; others
    /// pass the hint through untouched.
    InvalidLanguage(String),
    /// The adapter is compiled but no HTTP transport is linked yet. Returned
    /// by [`Provider::transcribe`] in the std-only build; use
    /// [`Provider::build_request`] to get the request a transport would send.
    TransportNotConfigured,
    /// The provider answered with an HTTP error status (4xx/5xx, other than
    /// 429). Carries the status code; any message goes in
    /// [`ProviderError::ProviderMessage`].
    HttpError(u16),
    /// The provider rate-limited the request (HTTP 429). Back off and retry.
    RateLimited,
    /// The user cancelled the request. Produced by the app layer, not by the
    /// adapters themselves.
    Cancelled,
    /// A provider-supplied error message (already stripped of key material).
    /// Also used for malformed provider responses.
    ProviderMessage(String),
}

impl ProviderError {
    /// Map an HTTP response to the error taxonomy. The future transport
    /// should use this so every adapter reports failures identically:
    /// 429 → [`ProviderError::RateLimited`], other error statuses →
    /// [`ProviderError::HttpError`] (with the body message, if any, in
    /// [`ProviderError::ProviderMessage`]).
    pub fn from_http_status(status: u16, body: &str) -> Self {
        if status == 429 {
            return ProviderError::RateLimited;
        }
        let detail = extract_text_field(body.as_bytes()).or_else(|| {
            let trimmed = body.trim();
            if trimmed.is_empty() {
                None
            } else {
                Some(trimmed.chars().take(500).collect())
            }
        });
        match detail {
            Some(msg) => ProviderError::ProviderMessage(format!("HTTP {status}: {msg}")),
            None => ProviderError::HttpError(status),
        }
    }
}

impl fmt::Display for ProviderError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ProviderError::MissingKey => {
                write!(f, "no API key supplied (keys are runtime-only; see module docs)")
            }
            ProviderError::InvalidModel(m) => write!(f, "unsupported model: {m}"),
            ProviderError::InvalidLanguage(l) => write!(f, "unsupported language: {l}"),
            ProviderError::TransportNotConfigured => write!(
                f,
                "no HTTP transport linked (std-only build); use Provider::build_request \
                 and send the HttpRequest with an app-layer transport"
            ),
            ProviderError::HttpError(status) => write!(f, "provider HTTP error: {status}"),
            ProviderError::RateLimited => write!(f, "provider rate limit hit (HTTP 429); back off and retry"),
            ProviderError::Cancelled => write!(f, "request cancelled"),
            ProviderError::ProviderMessage(msg) => write!(f, "provider error: {msg}"),
        }
    }
}

impl std::error::Error for ProviderError {}

/// A fully-formed HTTP request an adapter would send, built but never
/// executed by this crate. The app layer hands this to its transport
/// (e.g. `reqwest`) — this is the seam where the std-only build ends.
///
/// The `Authorization` / API-key headers carry the runtime key. The custom
/// [`fmt::Debug`] impl redacts those header values, so logging an
/// `HttpRequest` never leaks the key. The multipart/JSON bodies contain only
/// audio and parameters — never the key.
pub struct HttpRequest {
    /// e.g. `"POST"`.
    pub method: &'static str,
    /// Full request URL, including any model path segment.
    pub url: String,
    /// Header name/value pairs. Key-bearing headers are redacted in `Debug`.
    pub headers: Vec<(String, String)>,
    /// Request body bytes.
    pub body: Vec<u8>,
}

impl HttpRequest {
    /// Fetch a header value (case-insensitive name match).
    pub fn header(&self, name: &str) -> Option<&str> {
        self.headers
            .iter()
            .find(|(k, _)| k.eq_ignore_ascii_case(name))
            .map(|(_, v)| v.as_str())
    }
}

impl fmt::Debug for HttpRequest {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let redacted: Vec<(&str, &str)> = self
            .headers
            .iter()
            .map(|(k, v)| {
                let kl = k.to_ascii_lowercase();
                let shown = if kl == "authorization"
                    || kl.contains("api-key")
                    || kl.contains("apikey")
                {
                    "<redacted>"
                } else {
                    v
                };
                (k.as_str(), shown)
            })
            .collect();
        f.debug_struct("HttpRequest")
            .field("method", &self.method)
            .field("url", &self.url)
            .field("headers", &redacted)
            .field("body_len", &self.body.len())
            .finish()
    }
}

// ---------------------------------------------------------------------------
// std-only encoding plumbing (no serde, no base64 crate)
// ---------------------------------------------------------------------------

/// Escape a string for embedding in a JSON string value (std-only
/// replacement for `serde_json`). Handles quotes, backslashes, and control
/// characters per RFC 8259.
pub fn escape_json_string(s: &str) -> String {
    let mut out = String::with_capacity(s.len() + 2);
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => {
                out.push_str(&format!("\\u{:04x}", c as u32));
            }
            _ => out.push(c),
        }
    }
    out
}

/// Standard base64 (RFC 4648) encoder, std-only.
fn base64_encode(data: &[u8]) -> String {
    const ALPHABET: &[u8; 64] =
        b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut out = String::with_capacity(data.len().div_ceil(3) * 4);
    for chunk in data.chunks(3) {
        let mut n: u32 = 0;
        for (i, b) in chunk.iter().enumerate() {
            n |= (*b as u32) << (16 - 8 * i);
        }
        let pad = 3 - chunk.len();
        for i in 0..4 - pad {
            out.push(ALPHABET[((n >> (18 - 6 * i)) & 63) as usize] as char);
        }
        for _ in 0..pad {
            out.push('=');
        }
    }
    out
}

/// Encode interleaved 16-bit PCM as a mono WAV file (44-byte RIFF header +
/// little-endian samples). What the adapters actually upload.
fn encode_wav_pcm16(audio: &[i16], sample_rate: u32) -> Vec<u8> {
    let data_len = audio.len() * 2;
    let mut out = Vec::with_capacity(44 + data_len);
    out.extend_from_slice(b"RIFF");
    out.extend_from_slice(&(36 + data_len as u32).to_le_bytes());
    out.extend_from_slice(b"WAVE");
    out.extend_from_slice(b"fmt ");
    out.extend_from_slice(&16u32.to_le_bytes()); // fmt chunk size
    out.extend_from_slice(&1u16.to_le_bytes()); // PCM
    out.extend_from_slice(&1u16.to_le_bytes()); // mono
    out.extend_from_slice(&sample_rate.to_le_bytes());
    out.extend_from_slice(&(sample_rate * 2).to_le_bytes()); // byte rate
    out.extend_from_slice(&2u16.to_le_bytes()); // block align
    out.extend_from_slice(&16u16.to_le_bytes()); // bits per sample
    out.extend_from_slice(b"data");
    out.extend_from_slice(&(data_len as u32).to_le_bytes());
    for s in audio {
        out.extend_from_slice(&s.to_le_bytes());
    }
    out
}

fn skip_ws(bytes: &[u8], mut j: usize) -> usize {
    while j < bytes.len() && matches!(bytes[j], b' ' | b'\t' | b'\n' | b'\r') {
        j += 1;
    }
    j
}

/// Unescape a JSON string body (no surrounding quotes). Returns `None` on
/// malformed escapes. `\uXXXX` pairs outside the BMP (surrogate pairs) are
/// not combined — noted, acceptable for transcript text.
fn unescape_json_string(s: &str) -> Option<String> {
    let mut out = String::with_capacity(s.len());
    let mut chars = s.chars();
    while let Some(c) = chars.next() {
        if c != '\\' {
            out.push(c);
            continue;
        }
        match chars.next()? {
            '"' => out.push('"'),
            '\\' => out.push('\\'),
            '/' => out.push('/'),
            'n' => out.push('\n'),
            'r' => out.push('\r'),
            't' => out.push('\t'),
            'b' => out.push('\u{0008}'),
            'f' => out.push('\u{000C}'),
            'u' => {
                let hex: String = chars.by_ref().take(4).collect();
                if hex.len() != 4 {
                    return None;
                }
                let cp = u32::from_str_radix(&hex, 16).ok()?;
                out.push(char::from_u32(cp)?);
            }
            _ => return None,
        }
    }
    Some(out)
}

/// Extract the value of the first string-valued field with the given JSON
/// key (key must include its quotes, e.g. `"\"text\""`).
///
/// Both the Whisper-compatible `{"text": "..."}` shape (Groq, OpenAI with
/// `response_format=json`) and Gemini's
/// `candidates[0].content.parts[0].text` shape carry the transcript in a
/// `"text"` field, so one tolerant extractor serves all three adapters.
/// Deepgram instead uses `"transcript"` (see
/// [`extract_transcript_field`]). Returns `None` when no string-valued
/// field with that key is found.
fn extract_string_field(body: &[u8], field: &str) -> Option<String> {
    let text = std::str::from_utf8(body).ok()?;
    let bytes = text.as_bytes();
    let key = field.as_bytes();
    let mut i = 0;
    while i + key.len() <= bytes.len() {
        if &bytes[i..i + key.len()] == key {
            let mut j = skip_ws(bytes, i + key.len());
            if bytes.get(j) != Some(&b':') {
                i += 1;
                continue;
            }
            j = skip_ws(bytes, j + 1);
            if bytes.get(j) != Some(&b'"') {
                i += 1;
                continue;
            }
            // Scan to the closing quote, honoring backslash escapes.
            let mut k = j + 1;
            let mut escaped = false;
            while k < bytes.len() {
                let b = bytes[k];
                if escaped {
                    escaped = false;
                } else if b == b'\\' {
                    escaped = true;
                } else if b == b'"' {
                    break;
                }
                k += 1;
            }
            if k >= bytes.len() {
                return None;
            }
            return unescape_json_string(&text[j + 1..k]);
        }
        i += 1;
    }
    None
}

fn extract_text_field(body: &[u8]) -> Option<String> {
    extract_string_field(body, "\"text\"")
}

/// Extract Deepgram's transcript: the first `"transcript"` string field in
/// the `/v1/listen` response (`results.channels[].alternatives[].transcript`).
fn extract_transcript_field(body: &[u8]) -> Option<String> {
    extract_string_field(body, "\"transcript\"")
}

/// Parse a Whisper-compatible JSON transcription response
/// (`{"text": "..."}`) into the common response type.
fn parse_whisper_json_response(body: &[u8]) -> Result<TranscriptionResponse, ProviderError> {
    match extract_text_field(body) {
        Some(text) => Ok(TranscriptionResponse {
            text,
            language: None,
        }),
        None => Err(ProviderError::ProviderMessage(format!(
            "unrecognized transcription response ({} bytes, no \"text\" field)",
            body.len()
        ))),
    }
}

/// Minimal multipart/form-data builder (std-only). Field names and values
/// used here are adapter-controlled; the boundary is a fixed constant that
/// cannot collide with binary WAV data in practice.
struct MultipartBody {
    buf: Vec<u8>,
}

const MULTIPART_BOUNDARY: &str = "----wahtuh-pho-multipart-7f3a9c2e";

impl MultipartBody {
    fn new() -> Self {
        Self { buf: Vec::new() }
    }

    fn content_type() -> String {
        format!("multipart/form-data; boundary={MULTIPART_BOUNDARY}")
    }

    fn text_field(&mut self, name: &str, value: &str) {
        let head = format!(
            "--{MULTIPART_BOUNDARY}\r\n\
             Content-Disposition: form-data; name=\"{name}\"\r\n\
             \r\n"
        );
        self.buf.extend_from_slice(head.as_bytes());
        self.buf.extend_from_slice(value.as_bytes());
        self.buf.extend_from_slice(b"\r\n");
    }

    fn file_field(&mut self, name: &str, filename: &str, content_type: &str, data: &[u8]) {
        let head = format!(
            "--{MULTIPART_BOUNDARY}\r\n\
             Content-Disposition: form-data; name=\"{name}\"; filename=\"{filename}\"\r\n\
             Content-Type: {content_type}\r\n\
             \r\n"
        );
        self.buf.extend_from_slice(head.as_bytes());
        self.buf.extend_from_slice(data);
        self.buf.extend_from_slice(b"\r\n");
    }

    fn finish(mut self) -> Vec<u8> {
        let tail = format!("--{MULTIPART_BOUNDARY}--\r\n");
        self.buf.extend_from_slice(tail.as_bytes());
        self.buf
    }
}

/// Shared validation: key present + model in the adapter's verified
/// allow-list. Keeps the three adapters honest and identical.
fn validate_against(
    adapter: &dyn Provider,
    req: &TranscriptionRequest,
) -> Result<(), ProviderError> {
    // Re-checked here even though TranscriptionRequest::new rejects empty
    // keys: validate() is the documented gate, so it must hold on its own.
    if req.api_key().trim().is_empty() {
        return Err(ProviderError::MissingKey);
    }
    if !adapter.supported_models().iter().any(|m| m == &req.model) {
        return Err(ProviderError::InvalidModel(req.model.clone()));
    }
    Ok(())
}

// ---------------------------------------------------------------------------
// Groq
// ---------------------------------------------------------------------------

/// Typed Groq transcription request, derived from the common
/// [`TranscriptionRequest`] by [`GroqAdapter::to_typed`].
///
/// Groq exposes an OpenAI-compatible multipart endpoint; see
/// `docs/PROVIDERS.md` for the source documentation.
#[derive(Debug, Clone)]
pub struct GroqTranscribeRequest {
    /// Verified model id, e.g. `"whisper-large-v3-turbo"`.
    pub model: String,
    /// 16-bit PCM WAV payload (what gets uploaded).
    pub audio_wav: Vec<u8>,
    /// Optional ISO-639-1 language hint.
    pub language: Option<String>,
}

impl GroqTranscribeRequest {
    /// Build the documented HTTP request. `api_key` is used only to set the
    /// `Authorization` header and is never stored.
    pub fn build_http(&self, api_key: &str) -> HttpRequest {
        let mut mp = MultipartBody::new();
        mp.file_field("file", "audio.wav", "audio/wav", &self.audio_wav);
        mp.text_field("model", &self.model);
        mp.text_field("response_format", "json");
        if let Some(lang) = &self.language {
            mp.text_field("language", lang);
        }
        HttpRequest {
            method: "POST",
            url: GroqAdapter::TRANSCRIBE_URL.to_string(),
            headers: vec![
                ("Authorization".to_string(), format!("Bearer {api_key}")),
                ("Content-Type".to_string(), MultipartBody::content_type()),
            ],
            body: mp.finish(),
        }
    }
}

/// Groq adapter (opt-in). Verified models below; see `docs/PROVIDERS.md` for
/// the documentation source and check date of each entry.
pub struct GroqAdapter;

impl GroqAdapter {
    /// `POST` endpoint per Groq's Speech-to-Text docs (OpenAI-compatible).
    pub const TRANSCRIBE_URL: &'static str =
        "https://api.groq.com/openai/v1/audio/transcriptions";

    /// Convert the common request into the typed Groq request (validates
    /// first). The WAV encoding is what would be uploaded.
    pub fn to_typed(
        &self,
        req: &TranscriptionRequest,
    ) -> Result<GroqTranscribeRequest, ProviderError> {
        self.validate(req)?;
        Ok(GroqTranscribeRequest {
            model: req.model.clone(),
            audio_wav: encode_wav_pcm16(&req.audio, req.sample_rate),
            language: req.language.clone(),
        })
    }

    /// Parse the provider's JSON transcription response.
    pub fn parse_response(&self, body: &[u8]) -> Result<TranscriptionResponse, ProviderError> {
        parse_whisper_json_response(body)
    }
}

impl Provider for GroqAdapter {
    fn name(&self) -> &'static str {
        "groq"
    }

    fn supported_models(&self) -> Vec<String> {
        vec![
            "whisper-large-v3".to_string(),
            "whisper-large-v3-turbo".to_string(),
        ]
    }

    fn validate(&self, req: &TranscriptionRequest) -> Result<(), ProviderError> {
        validate_against(self, req)
    }

    fn build_request(&self, req: &TranscriptionRequest) -> Result<HttpRequest, ProviderError> {
        Ok(self.to_typed(req)?.build_http(req.api_key()))
    }
}

// ---------------------------------------------------------------------------
// Google Gemini
// ---------------------------------------------------------------------------

/// Typed Gemini transcription request, derived from the common
/// [`TranscriptionRequest`] by [`GeminiAdapter::to_typed`].
///
/// Gemini has no dedicated speech-to-text endpoint: transcription is done via
/// `generateContent` with the audio supplied as inline data plus a text
/// prompt asking for a verbatim transcript. (That prompt deliberately asks
/// for verbatim output only — any AI cleanup/formatting is a separate,
/// opt-in step, never part of transcription.) For audio that would exceed
/// the inline-data request limit, the documented alternative is the Files
/// API; this adapter builds the inline form (dictation snippets are short).
#[derive(Debug, Clone)]
pub struct GeminiTranscribeRequest {
    /// Verified model id, e.g. `"gemini-3.8-flash"`.
    pub model: String,
    /// Base64-encoded WAV payload.
    pub audio_base64: String,
    /// MIME type of the audio payload.
    pub mime_type: &'static str,
    /// The transcription prompt sent alongside the audio.
    pub prompt: String,
}

impl GeminiTranscribeRequest {
    /// The prompt sent with every request. Asks for a verbatim transcript
    /// only — no commentary, no reformatting — so transcription stays
    /// separate from the optional AI-cleanup step.
    pub const PROMPT: &'static str = "Transcribe this audio recording verbatim. \
        Return only the transcript text: no commentary, no formatting, no speaker labels.";

    /// Build the documented HTTP request. `api_key` is used only to set the
    /// `x-goog-api-key` header and is never stored.
    pub fn build_http(&self, api_key: &str) -> HttpRequest {
        let body = format!(
            "{{\"contents\":[{{\"parts\":[{{\"text\":\"{}\"}},{{\"inlineData\":{{\"mimeType\":\"{}\",\"data\":\"{}\"}}}}]}}]}}",
            escape_json_string(&self.prompt),
            self.mime_type,
            self.audio_base64,
        );
        HttpRequest {
            method: "POST",
            url: format!(
                "https://generativelanguage.googleapis.com/v1beta/models/{}:generateContent",
                self.model
            ),
            headers: vec![
                ("x-goog-api-key".to_string(), api_key.to_string()),
                ("Content-Type".to_string(), "application/json".to_string()),
            ],
            body: body.into_bytes(),
        }
    }
}

/// Google Gemini adapter (opt-in). Verified models below; see
/// `docs/PROVIDERS.md` for the documentation source and check date of each
/// entry — including the caveat that Google is retiring 2.5-series access
/// for newly created API keys.
pub struct GeminiAdapter;

impl GeminiAdapter {
    /// Convert the common request into the typed Gemini request (validates
    /// first). The base64 WAV is what would be uploaded.
    pub fn to_typed(
        &self,
        req: &TranscriptionRequest,
    ) -> Result<GeminiTranscribeRequest, ProviderError> {
        self.validate(req)?;
        Ok(GeminiTranscribeRequest {
            model: req.model.clone(),
            audio_base64: base64_encode(&encode_wav_pcm16(&req.audio, req.sample_rate)),
            mime_type: "audio/wav",
            prompt: GeminiTranscribeRequest::PROMPT.to_string(),
        })
    }

    /// Parse the provider's `generateContent` response, extracting the
    /// transcript from `candidates[0].content.parts[0].text`.
    pub fn parse_response(&self, body: &[u8]) -> Result<TranscriptionResponse, ProviderError> {
        parse_whisper_json_response(body)
    }
}

impl Provider for GeminiAdapter {
    fn name(&self) -> &'static str {
        "gemini"
    }

    fn supported_models(&self) -> Vec<String> {
        vec![
            "gemini-3.8-flash".to_string(),
            "gemini-2.5-flash".to_string(),
        ]
    }

    fn validate(&self, req: &TranscriptionRequest) -> Result<(), ProviderError> {
        validate_against(self, req)
    }

    fn build_request(&self, req: &TranscriptionRequest) -> Result<HttpRequest, ProviderError> {
        Ok(self.to_typed(req)?.build_http(req.api_key()))
    }
}

// ---------------------------------------------------------------------------
// OpenAI
// ---------------------------------------------------------------------------

/// Typed OpenAI transcription request, derived from the common
/// [`TranscriptionRequest`] by [`OpenAiAdapter::to_typed`].
///
/// `POST https://api.openai.com/v1/audio/transcriptions`, multipart
/// form-data, per the OpenAI API reference.
#[derive(Debug, Clone)]
pub struct OpenAITranscribeRequest {
    /// Verified model id, e.g. `"gpt-4o-mini-transcribe"`.
    pub model: String,
    /// 16-bit PCM WAV payload (what gets uploaded).
    pub audio_wav: Vec<u8>,
    /// Optional ISO-639-1 language hint.
    pub language: Option<String>,
}

impl OpenAITranscribeRequest {
    /// Build the documented HTTP request. `api_key` is used only to set the
    /// `Authorization` header and is never stored.
    pub fn build_http(&self, api_key: &str) -> HttpRequest {
        let mut mp = MultipartBody::new();
        mp.file_field("file", "audio.wav", "audio/wav", &self.audio_wav);
        mp.text_field("model", &self.model);
        mp.text_field("response_format", "json");
        if let Some(lang) = &self.language {
            mp.text_field("language", lang);
        }
        HttpRequest {
            method: "POST",
            url: OpenAiAdapter::TRANSCRIBE_URL.to_string(),
            headers: vec![
                ("Authorization".to_string(), format!("Bearer {api_key}")),
                ("Content-Type".to_string(), MultipartBody::content_type()),
            ],
            body: mp.finish(),
        }
    }
}

/// OpenAI adapter (opt-in). Verified models below; see `docs/PROVIDERS.md`
/// for the documentation source and check date of each entry.
pub struct OpenAiAdapter;

impl OpenAiAdapter {
    /// `POST` endpoint per the OpenAI API reference.
    pub const TRANSCRIBE_URL: &'static str = "https://api.openai.com/v1/audio/transcriptions";

    /// Convert the common request into the typed OpenAI request (validates
    /// first). The WAV encoding is what would be uploaded.
    pub fn to_typed(
        &self,
        req: &TranscriptionRequest,
    ) -> Result<OpenAITranscribeRequest, ProviderError> {
        self.validate(req)?;
        Ok(OpenAITranscribeRequest {
            model: req.model.clone(),
            audio_wav: encode_wav_pcm16(&req.audio, req.sample_rate),
            language: req.language.clone(),
        })
    }

    /// Parse the provider's JSON transcription response.
    pub fn parse_response(&self, body: &[u8]) -> Result<TranscriptionResponse, ProviderError> {
        parse_whisper_json_response(body)
    }
}

impl Provider for OpenAiAdapter {
    fn name(&self) -> &'static str {
        "openai"
    }

    fn supported_models(&self) -> Vec<String> {
        vec![
            "gpt-transcribe".to_string(),
            "gpt-4o-transcribe".to_string(),
            "gpt-4o-mini-transcribe".to_string(),
            "gpt-4o-mini-transcribe-2025-12-15".to_string(),
            "whisper-1".to_string(),
            "gpt-4o-transcribe-diarize".to_string(),
        ]
    }

    fn validate(&self, req: &TranscriptionRequest) -> Result<(), ProviderError> {
        validate_against(self, req)
    }

    fn build_request(&self, req: &TranscriptionRequest) -> Result<HttpRequest, ProviderError> {
        Ok(self.to_typed(req)?.build_http(req.api_key()))
    }
}

// ---------------------------------------------------------------------------
// Deepgram
// ---------------------------------------------------------------------------

/// Typed Deepgram transcription request, derived from the common
/// [`TranscriptionRequest`] by [`DeepgramAdapter::to_typed`].
///
/// `POST https://api.deepgram.com/v1/listen` with the raw WAV as the
/// request body (`Content-Type: audio/wav`), per Deepgram's pre-recorded
/// API docs. Authentication is `Authorization: Token <key>` — note this is
/// **not** the `Bearer` scheme the other adapters use.
#[derive(Debug, Clone)]
pub struct DeepgramTranscribeRequest {
    /// Verified model id, e.g. `"nova-3"`.
    pub model: String,
    /// 16-bit PCM WAV payload (what gets uploaded).
    pub audio_wav: Vec<u8>,
    /// Deepgram language code: ISO-639-1 (`"te"`, `"en"`) or `"multi"`
    /// for Deepgram's multilingual / code-switching mode.
    pub language: Option<String>,
}

impl DeepgramTranscribeRequest {
    /// Build the documented HTTP request. `api_key` is used only to set the
    /// `Authorization: Token <key>` header and is never stored.
    pub fn build_http(&self, api_key: &str) -> HttpRequest {
        let mut url = format!(
            "{}?model={}&smart_format=true",
            DeepgramAdapter::LISTEN_URL,
            self.model
        );
        if let Some(lang) = &self.language {
            // `lang` was validated against SUPPORTED_LANGUAGES in
            // `DeepgramAdapter::validate`, so plain concatenation cannot
            // inject extra query parameters.
            url.push_str("&language=");
            url.push_str(lang);
        }
        HttpRequest {
            method: "POST",
            url,
            headers: vec![
                ("Authorization".to_string(), format!("Token {api_key}")),
                ("Content-Type".to_string(), "audio/wav".to_string()),
            ],
            body: self.audio_wav.clone(),
        }
    }
}

/// Deepgram adapter (opt-in). Verified model below; see `docs/PROVIDERS.md`
/// for the documentation source and check date.
pub struct DeepgramAdapter;

impl DeepgramAdapter {
    /// `POST` endpoint per Deepgram's pre-recorded speech-to-text docs.
    pub const LISTEN_URL: &'static str = "https://api.deepgram.com/v1/listen";

    /// Language codes this adapter accepts. `"multi"` is Deepgram's
    /// multilingual mode (handles code-switching); the monolingual codes
    /// are the ones we benchmark. The allow-list doubles as query-string
    /// injection protection (see `DeepgramTranscribeRequest::build_http`).
    pub const SUPPORTED_LANGUAGES: &'static [&'static str] = &["te", "en", "multi"];

    /// Convert the common request into the typed Deepgram request
    /// (validates first). The WAV encoding is what would be uploaded.
    pub fn to_typed(
        &self,
        req: &TranscriptionRequest,
    ) -> Result<DeepgramTranscribeRequest, ProviderError> {
        self.validate(req)?;
        Ok(DeepgramTranscribeRequest {
            model: req.model.clone(),
            audio_wav: encode_wav_pcm16(&req.audio, req.sample_rate),
            language: req.language.clone(),
        })
    }

    /// Parse Deepgram's `/v1/listen` JSON response, extracting the
    /// transcript from `results.channels[].alternatives[].transcript`.
    pub fn parse_response(&self, body: &[u8]) -> Result<TranscriptionResponse, ProviderError> {
        match extract_transcript_field(body) {
            Some(text) => Ok(TranscriptionResponse {
                text,
                language: None,
            }),
            None => Err(ProviderError::ProviderMessage(format!(
                "unrecognized Deepgram response ({} bytes, no \"transcript\" field)",
                body.len()
            ))),
        }
    }
}

impl Provider for DeepgramAdapter {
    fn name(&self) -> &'static str {
        "deepgram"
    }

    fn supported_models(&self) -> Vec<String> {
        vec!["nova-3".to_string()]
    }

    fn validate(&self, req: &TranscriptionRequest) -> Result<(), ProviderError> {
        validate_against(self, req)?;
        if let Some(lang) = &req.language {
            if !Self::SUPPORTED_LANGUAGES.contains(&lang.as_str()) {
                return Err(ProviderError::InvalidLanguage(lang.clone()));
            }
        }
        Ok(())
    }

    fn build_request(&self, req: &TranscriptionRequest) -> Result<HttpRequest, ProviderError> {
        Ok(self.to_typed(req)?.build_http(req.api_key()))
    }
}

/// All adapters behind one enum for settings UIs.
pub fn all_providers() -> Vec<Box<dyn Provider>> {
    vec![
        Box::new(GroqAdapter),
        Box::new(GeminiAdapter),
        Box::new(OpenAiAdapter),
        Box::new(DeepgramAdapter),
    ]
}

#[cfg(test)]
mod tests {
    use super::*;

    fn audio() -> Vec<i16> {
        // 10 ms of 16 kHz audio stand-in.
        (0..160).map(|i| (i as f32 * 0.1).sin() as i16 * 1000).collect()
    }

    fn req_with_key() -> TranscriptionRequest {
        TranscriptionRequest::new(audio(), 16_000, "whisper-large-v3-turbo", "runtime-key").unwrap()
    }

    fn req_with_empty_key() -> TranscriptionRequest {
        // Tests live in a child module, so the private key field is
        // accessible here — this is the only way to exercise validate()'s
        // own key check, since new() rejects empty keys up front.
        TranscriptionRequest {
            audio: audio(),
            sample_rate: 16_000,
            model: "whisper-large-v3-turbo".to_string(),
            language: None,
            api_key: String::new(),
        }
    }

    #[test]
    fn request_rejects_missing_key() {
        assert_eq!(
            TranscriptionRequest::new(audio(), 16_000, "m", "").unwrap_err(),
            ProviderError::MissingKey
        );
        assert_eq!(
            TranscriptionRequest::new(audio(), 16_000, "m", "   ").unwrap_err(),
            ProviderError::MissingKey
        );
    }

    #[test]
    fn model_allow_lists_contain_only_verified_names() {
        // These lists are the verified allow-lists (sources + check dates in
        // docs/PROVIDERS.md). Any change here must come with a documentation
        // check — never add an unverified name.
        assert_eq!(
            GroqAdapter.supported_models(),
            vec!["whisper-large-v3", "whisper-large-v3-turbo"]
        );
        assert_eq!(
            GeminiAdapter.supported_models(),
            vec!["gemini-3.8-flash", "gemini-2.5-flash"]
        );
        assert_eq!(
            OpenAiAdapter.supported_models(),
            vec![
                "gpt-transcribe",
                "gpt-4o-transcribe",
                "gpt-4o-mini-transcribe",
                "gpt-4o-mini-transcribe-2025-12-15",
                "whisper-1",
                "gpt-4o-transcribe-diarize",
            ]
        );
        assert_eq!(DeepgramAdapter.supported_models(), vec!["nova-3"]);
    }

    #[test]
    fn validate_checks_key_and_model() {
        let adapter = GroqAdapter;

        // Empty key -> MissingKey, even before the model is considered.
        assert_eq!(
            adapter.validate(&req_with_empty_key()).unwrap_err(),
            ProviderError::MissingKey
        );

        // Unknown model -> InvalidModel carrying the rejected id.
        let bad =
            TranscriptionRequest::new(audio(), 16_000, "whisper-99", "k").unwrap();
        assert_eq!(
            adapter.validate(&bad).unwrap_err(),
            ProviderError::InvalidModel("whisper-99".to_string())
        );

        // Known model + key -> Ok.
        assert!(adapter.validate(&req_with_key()).is_ok());

        // The other adapters gate identically.
        let gemini_bad =
            TranscriptionRequest::new(audio(), 16_000, "gemini-1.0-ancient", "k").unwrap();
        assert!(matches!(
            GeminiAdapter.validate(&gemini_bad).unwrap_err(),
            ProviderError::InvalidModel(_)
        ));
        let openai_bad =
            TranscriptionRequest::new(audio(), 16_000, "whisper-2", "k").unwrap();
        assert!(matches!(
            OpenAiAdapter.validate(&openai_bad).unwrap_err(),
            ProviderError::InvalidModel(_)
        ));
    }

    #[test]
    fn transcribe_reports_no_transport_in_std_only_build() {
        for provider in all_providers() {
            let model = provider.supported_models()[0].clone();
            let req = TranscriptionRequest::new(audio(), 16_000, model, "runtime-key").unwrap();
            assert_eq!(
                provider.transcribe(&req).unwrap_err(),
                ProviderError::TransportNotConfigured
            );
        }
    }

    #[test]
    fn groq_build_request_matches_documented_shape() {
        let adapter = GroqAdapter;
        let http = adapter.build_request(&req_with_key()).unwrap();
        assert_eq!(http.method, "POST");
        assert_eq!(http.url, "https://api.groq.com/openai/v1/audio/transcriptions");
        assert_eq!(
            http.header("Authorization"),
            Some("Bearer runtime-key")
        );
        assert!(http
            .header("Content-Type")
            .unwrap()
            .starts_with("multipart/form-data; boundary="));
        let body = String::from_utf8_lossy(&http.body);
        assert!(body.contains("name=\"file\"; filename=\"audio.wav\""));
        assert!(body.contains("Content-Type: audio/wav"));
        assert!(body.contains("name=\"model\""));
        assert!(body.contains("whisper-large-v3-turbo"));
        assert!(body.contains("name=\"response_format\""));
        // The WAV payload bytes are embedded in the multipart body.
        let wav = encode_wav_pcm16(&audio(), 16_000);
        assert!(http
            .body
            .windows(wav.len())
            .any(|w| w == wav.as_slice()));
    }

    #[test]
    fn openai_build_request_matches_documented_shape() {
        let adapter = OpenAiAdapter;
        let req = TranscriptionRequest::new(audio(), 16_000, "gpt-4o-mini-transcribe", "k2")
            .unwrap()
            .with_language("te");
        let http = adapter.build_request(&req).unwrap();
        assert_eq!(http.method, "POST");
        assert_eq!(http.url, "https://api.openai.com/v1/audio/transcriptions");
        assert_eq!(http.header("Authorization"), Some("Bearer k2"));
        let body = String::from_utf8_lossy(&http.body);
        assert!(body.contains("gpt-4o-mini-transcribe"));
        assert!(body.contains("name=\"language\""));
        assert!(body.contains("\r\nte\r\n"));
    }

    #[test]
    fn gemini_build_request_matches_documented_shape() {
        let adapter = GeminiAdapter;
        let req =
            TranscriptionRequest::new(audio(), 16_000, "gemini-3.8-flash", "gemini-key").unwrap();
        let http = adapter.build_request(&req).unwrap();
        assert_eq!(http.method, "POST");
        assert_eq!(
            http.url,
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"
        );
        assert_eq!(http.header("x-goog-api-key"), Some("gemini-key"));
        assert_eq!(http.header("Content-Type"), Some("application/json"));
        let body = String::from_utf8_lossy(&http.body);
        assert!(body.contains("\"inlineData\""));
        assert!(body.contains("\"mimeType\":\"audio/wav\""));
        assert!(body.contains("\"data\":\""));
        // Base64 of the WAV payload is embedded.
        let b64 = base64_encode(&encode_wav_pcm16(&audio(), 16_000));
        assert!(body.contains(&b64));
        // The prompt asks for verbatim output only (cleanup stays separate).
        assert!(body.contains("verbatim"));
    }

    #[test]
    fn http_request_debug_redacts_key_material() {
        let http = GroqAdapter.build_request(&req_with_key()).unwrap();
        let debug = format!("{http:?}");
        assert!(
            !debug.contains("runtime-key"),
            "key leaked into Debug output: {debug}"
        );
        assert!(debug.contains("<redacted>"));
        // Non-secret headers stay visible.
        assert!(debug.contains("multipart/form-data"));

        let gemini = GeminiAdapter
            .build_request(
                &TranscriptionRequest::new(audio(), 16_000, "gemini-3.8-flash", "gemini-key")
                    .unwrap(),
            )
            .unwrap();
        let debug = format!("{gemini:?}");
        assert!(!debug.contains("gemini-key"), "key leaked: {debug}");
    }

    #[test]
    fn json_escaping_is_correct() {
        assert_eq!(escape_json_string("plain"), "plain");
        assert_eq!(escape_json_string("a\"b"), "a\\\"b");
        assert_eq!(escape_json_string("a\\b"), "a\\\\b");
        assert_eq!(escape_json_string("a\nb\rc\td"), "a\\nb\\rc\\td");
        assert_eq!(escape_json_string("bell\u{7}"), "bell\\u0007");
        assert_eq!(escape_json_string("nul\u{0}"), "nul\\u0000");
        // Round-trips through the extractor.
        let tricky = "say \"hi\"\nnewline\\backslash";
        let doc = format!("{{\"text\":\"{}\"}}", escape_json_string(tricky));
        assert_eq!(extract_text_field(doc.as_bytes()).as_deref(), Some(tricky));
    }

    #[test]
    fn base64_known_vectors() {
        assert_eq!(base64_encode(b""), "");
        assert_eq!(base64_encode(b"f"), "Zg==");
        assert_eq!(base64_encode(b"fo"), "Zm8=");
        assert_eq!(base64_encode(b"foo"), "Zm9v");
        assert_eq!(base64_encode(b"hello"), "aGVsbG8=");
    }

    #[test]
    fn wav_encoding_round_trip() {
        let samples: Vec<i16> = vec![0, 1, -1, 32767, -32768, 12345];
        let wav = encode_wav_pcm16(&samples, 16_000);
        assert_eq!(wav.len(), 44 + samples.len() * 2);
        assert_eq!(&wav[0..4], b"RIFF");
        assert_eq!(&wav[8..12], b"WAVE");
        assert_eq!(&wav[12..16], b"fmt ");
        assert_eq!(&wav[36..40], b"data");
        assert_eq!(
            u32::from_le_bytes(wav[24..28].try_into().unwrap()),
            16_000,
            "sample rate preserved"
        );
        let decoded: Vec<i16> = wav[44..]
            .chunks_exact(2)
            .map(|c| i16::from_le_bytes([c[0], c[1]]))
            .collect();
        assert_eq!(decoded, samples);
    }

    #[test]
    fn response_parsing_extracts_transcript() {
        // Whisper-compatible shape (Groq / OpenAI response_format=json).
        let body = br#"{"text":"hello world","language":"en"}"#;
        let resp = GroqAdapter.parse_response(body).unwrap();
        assert_eq!(resp.text, "hello world");

        // Escaped characters survive the round trip.
        let body = br#"{"text":"say \"hi\"\nbye"}"#;
        let resp = OpenAiAdapter.parse_response(body).unwrap();
        assert_eq!(resp.text, "say \"hi\"\nbye");

        // Gemini generateContent shape.
        let body = br#"{"candidates":[{"content":{"parts":[{"text":"namaste"}, {"text":"ignored second part"}]}}]}"#;
        let resp = GeminiAdapter.parse_response(body).unwrap();
        assert_eq!(resp.text, "namaste");

        // Garbage in -> typed error, and the error carries no key material.
        let err = GroqAdapter.parse_response(b"not json at all").unwrap_err();
        assert!(matches!(err, ProviderError::ProviderMessage(_)));
        assert!(!err.to_string().contains("runtime-key"));
    }

    #[test]
    fn http_status_mapping() {
        assert_eq!(
            ProviderError::from_http_status(429, "slow down"),
            ProviderError::RateLimited
        );
        assert_eq!(
            ProviderError::from_http_status(500, ""),
            ProviderError::HttpError(500)
        );
        let err = ProviderError::from_http_status(401, r#"{"text":"bad key"}"#);
        assert!(matches!(err, ProviderError::ProviderMessage(_)));
        assert!(err.to_string().contains("401"));
        // MissingKey / InvalidModel / Cancelled display sanely.
        assert!(ProviderError::MissingKey.to_string().contains("runtime-only"));
        assert!(ProviderError::InvalidModel("x".into())
            .to_string()
            .contains('x'));
        assert!(ProviderError::Cancelled.to_string().contains("cancelled"));
    }

    /// The local-only guarantee, enforced structurally: a default
    /// [`wahtuh_config::Config`] (`local_only = true`) rejects both the
    /// cloud engine selection and attached provider credentials, so the
    /// adapters above are unreachable until the app layer explicitly opts
    /// out with `allow_cloud()`.
    #[test]
    fn local_only_gate_rejects_provider_selection() {
        use wahtuh_config::{Config, EngineSelection, ProviderCredentials};

        let mut cfg = Config::default();
        assert!(cfg.local_only, "default config must be local-only");
        cfg.engine = EngineSelection::Cloud;
        assert!(
            cfg.validate().is_err(),
            "local_only + Cloud engine must fail validation"
        );

        let mut cfg = Config::default();
        cfg.provider_credentials = Some(ProviderCredentials {
            provider: "groq".to_string(),
            api_key: "runtime-key".to_string(),
        });
        assert!(
            cfg.validate().is_err(),
            "local_only + credentials must fail validation"
        );

        // Opt-out is explicit and still requires credentials for cloud use.
        let mut cfg = Config::default();
        cfg.allow_cloud();
        cfg.engine = EngineSelection::Cloud;
        assert!(
            cfg.validate().is_err(),
            "cloud engine without credentials must still fail"
        );
    }

    #[test]
    fn all_providers_listed() {
        let names: Vec<&str> = all_providers().iter().map(|p| p.name()).collect();
        assert_eq!(names, vec!["groq", "gemini", "openai", "deepgram"]);
        // The trait's common interface works through the box.
        for p in all_providers() {
            assert!(!p.supported_models().is_empty());
            let req = TranscriptionRequest::new(
                audio(),
                16_000,
                p.supported_models()[0].clone(),
                "k",
            )
            .unwrap();
            assert!(p.validate(&req).is_ok());
            assert!(p.build_request(&req).is_ok());
        }
    }

    #[test]
    fn deepgram_build_request_matches_documented_shape() {
        let adapter = DeepgramAdapter;
        let req = TranscriptionRequest::new(audio(), 16_000, "nova-3", "dg-key")
            .unwrap()
            .with_language("te");
        let http = adapter.build_request(&req).unwrap();
        assert_eq!(http.method, "POST");
        assert_eq!(
            http.url,
            "https://api.deepgram.com/v1/listen?model=nova-3&smart_format=true&language=te"
        );
        // Deepgram uses `Token`, not `Bearer`.
        assert_eq!(http.header("Authorization"), Some("Token dg-key"));
        assert_eq!(http.header("Content-Type"), Some("audio/wav"));
        // The body is the raw WAV (not multipart, not JSON).
        assert_eq!(http.body, encode_wav_pcm16(&audio(), 16_000));

        // No language hint -> no language query param (Deepgram defaults).
        let req = TranscriptionRequest::new(audio(), 16_000, "nova-3", "dg-key").unwrap();
        let http = adapter.build_request(&req).unwrap();
        assert_eq!(
            http.url,
            "https://api.deepgram.com/v1/listen?model=nova-3&smart_format=true"
        );

        // The multilingual code-switching mode passes through.
        let req = TranscriptionRequest::new(audio(), 16_000, "nova-3", "dg-key")
            .unwrap()
            .with_language("multi");
        let http = adapter.build_request(&req).unwrap();
        assert!(http.url.ends_with("&language=multi"));
    }

    #[test]
    fn deepgram_validates_language_allow_list() {
        let adapter = DeepgramAdapter;
        for lang in ["te", "en", "multi"] {
            let req = TranscriptionRequest::new(audio(), 16_000, "nova-3", "k")
                .unwrap()
                .with_language(lang);
            assert!(adapter.validate(&req).is_ok(), "language {lang}");
        }
        // Unknown codes and injection attempts are rejected.
        for bad in ["fr", "TE", "", "te&model=nova-2", "en; rm -rf"] {
            let req = TranscriptionRequest::new(audio(), 16_000, "nova-3", "k")
                .unwrap()
                .with_language(bad);
            assert_eq!(
                adapter.validate(&req).unwrap_err(),
                ProviderError::InvalidLanguage(bad.to_string()),
                "language {bad:?}"
            );
        }
        assert!(ProviderError::InvalidLanguage("xx".into())
            .to_string()
            .contains("unsupported language"));
    }

    #[test]
    fn deepgram_response_parsing_extracts_transcript() {
        let body = br#"{"metadata":{"request_id":"r1"},"results":{"channels":[{"alternatives":[{"transcript":"hello world","confidence":0.99,"words":[]}]}]}}"#;
        let resp = DeepgramAdapter.parse_response(body).unwrap();
        assert_eq!(resp.text, "hello world");

        // Escaped characters survive.
        let body = br#"{"results":{"channels":[{"alternatives":[{"transcript":"say \"hi\"\nbye"}]}]}}"#;
        let resp = DeepgramAdapter.parse_response(body).unwrap();
        assert_eq!(resp.text, "say \"hi\"\nbye");

        // A Whisper-style "text" field is NOT enough — Deepgram's key is
        // "transcript", so this must fail loudly rather than misparse.
        let err = DeepgramAdapter
            .parse_response(br#"{"text":"wrong shape"}"#)
            .unwrap_err();
        assert!(matches!(err, ProviderError::ProviderMessage(_)));
        assert!(err.to_string().contains("transcript"));
    }

    #[test]
    fn deepgram_debug_redacts_token_header() {
        let req = TranscriptionRequest::new(audio(), 16_000, "nova-3", "dg-secret")
            .unwrap()
            .with_language("en");
        let http = DeepgramAdapter.build_request(&req).unwrap();
        let debug = format!("{http:?}");
        assert!(!debug.contains("dg-secret"), "key leaked: {debug}");
        assert!(debug.contains("<redacted>"));
    }

    /// Compile-time-adjacent guard: this file must never contain a hardcoded
    /// credential. It scans its own source; a key-like prefix fails the test.
    ///
    /// Note: the prefixes below are assembled with `concat!` so the test's
    /// *own* source doesn't self-trigger the scan. A real pasted key (a long
    /// contiguous provider-style key literal) would still be caught.
    #[test]
    fn no_hardcoded_secrets() {
        let src = include_str!("lib.rs");
        // Provider key prefixes. If a legit identifier ever trips this,
        // rename the identifier — never the test.
        for prefix in [concat!("g", "sk_"), concat!("AI", "za")] {
            assert!(
                !src.contains(prefix),
                "hardcoded credential prefix {prefix:?} found in providers source"
            );
        }
        // No string literal that looks like an OpenAI-style key ("sk-" followed
        // by a long alphanumeric run). Scan for the token and inspect context.
        let bytes = src.as_bytes();
        let mut i = 0;
        while i + 3 < bytes.len() {
            if &bytes[i..i + 3] == b"sk-" {
                let tail: String = bytes[i + 3..]
                    .iter()
                    .take_while(|b| b.is_ascii_alphanumeric())
                    .map(|b| *b as char)
                    .collect();
                assert!(
                    tail.len() < 20,
                    "key-like literal starting at {:?}... in providers source",
                    &src[i..i + 10.min(src.len() - i)]
                );
                i += 3 + tail.len();
            } else {
                i += 1;
            }
        }
        // Belt and braces: the runtime-only policy must be documented, and
        // the crate must not contain any suspiciously long quoted literal
        // that could be a pasted key.
        assert!(src.contains("runtime-only"));
        for line in src.lines() {
            // Exempt the base64 alphabet constant itself: it is a fixed
            // 64-character encoding table (the only b".." literal in this
            // file), not a credential.
            if line.trim_start().starts_with("b\"") {
                continue;
            }
            let quoted: Vec<&str> = line.split('"').collect();
            for (idx, part) in quoted.iter().enumerate() {
                // Odd indices are inside quotes.
                if idx % 2 == 1
                    && part.len() > 60
                    && part.chars().all(|c| c.is_ascii_alphanumeric() || "+/=".contains(c))
                {
                    panic!("suspicious key-like literal in providers source: {line}");
                }
            }
        }
    }
}
