//! Configuration for Wahtuh Pho.
//!
//! [`Config`] is the single place where the engine choice, language, and the
//! **local-only guarantee** live. `local_only` defaults to `true`, and
//! [`Config::validate`] makes the guarantee structural:
//!
//! - `local_only == true` + any provider credentials → error.
//! - `local_only == true` + [`EngineSelection::Cloud`] → error.
//! - Cloud adapters (`wahtuh-providers`) are not [`wahtuh_transcribe::Engine`]
//!   implementors, so `wahtuh-core` *cannot* route audio to them — there is
//!   no code path, not just a flag check (see `docs/ARCHITECTURE.md`).
//!
//! # Credential handling
//!
//! Provider credentials are `Option<ProviderCredentials>` in memory only.
//! [`Config::save`] **refuses** to write a config that carries credentials
//! ([`ConfigError::SecretsNotPersisted`]); the on-disk format has no field
//! for them at all. At runtime the app supplies keys via
//! [`Config::credentials_from_env`] (env var) or the OS keychain — never via
//! the config file.

use std::fmt;
use std::path::Path;

/// Which transcription backend to use.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EngineSelection {
    /// On-device engine via [`wahtuh_transcribe::LocalEngine`].
    Local,
    /// Deterministic stub for tests/demos.
    Mock,
    /// A cloud provider adapter. Unreachable when `local_only` is true.
    Cloud,
}

/// Dictation language.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Language {
    English,
    Telugu,
    /// Telugu–English code-switching ("Tenglish").
    CodeSwitch,
}

/// Provider credentials: kept in memory, never serialized by this crate.
///
/// There is deliberately no `Serialize`/`to_json` for this type (the crate is
/// std-only, so there is no derive to forget — the absence is structural).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ProviderCredentials {
    /// Matches [`wahtuh_providers::Provider::name`], e.g. `"groq"`.
    pub provider: String,
    /// Runtime-supplied API key. Never written to disk by this crate.
    pub api_key: String,
}

/// Application configuration.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Config {
    pub engine: EngineSelection,
    pub language: Language,
    /// When true (the default), cloud code paths are unreachable.
    pub local_only: bool,
    /// In-memory only. [`Config::save`] refuses to persist these.
    pub provider_credentials: Option<ProviderCredentials>,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            engine: EngineSelection::Local,
            language: Language::CodeSwitch,
            local_only: true,
            provider_credentials: None,
        }
    }
}

impl Config {
    /// Check the configuration for contradictions. Called by `wahtuh-core`
    /// before a session starts — a session never runs on an invalid config.
    pub fn validate(&self) -> Result<(), ConfigError> {
        if self.local_only {
            if self.provider_credentials.is_some() {
                return Err(ConfigError::CredentialsWithLocalOnly);
            }
            if self.engine == EngineSelection::Cloud {
                return Err(ConfigError::CloudEngineWithLocalOnly);
            }
        } else if self.engine == EngineSelection::Cloud && self.provider_credentials.is_none() {
            return Err(ConfigError::CloudEngineNeedsCredentials);
        }
        Ok(())
    }

    /// Explicit, reviewed opt-out of the local-only guarantee. The app layer
    /// must call this (not struct-update syntax on the field) so the decision
    /// is greppable and deliberate.
    pub fn allow_cloud(&mut self) {
        self.local_only = false;
    }

    /// Read credentials from an environment variable at runtime.
    ///
    /// Convention: `WAHTUH_<PROVIDER>_API_KEY`, uppercased, e.g.
    /// `WAHTUH_GROQ_API_KEY`. Returns [`ConfigError::MissingEnvCredentials`]
    /// when unset or empty.
    pub fn credentials_from_env(provider: &str) -> Result<ProviderCredentials, ConfigError> {
        let var = format!("WAHTUH_{}_API_KEY", provider.to_ascii_uppercase());
        match std::env::var(&var) {
            Ok(key) if !key.trim().is_empty() => Ok(ProviderCredentials {
                provider: provider.to_string(),
                api_key: key,
            }),
            _ => Err(ConfigError::MissingEnvCredentials(var)),
        }
    }

    /// Persist the config as JSON. The on-disk schema has **no credential
    /// fields**; saving a config that carries credentials is an error, not a
    /// silent strip, so callers notice the attempt.
    pub fn save(&self, path: &Path) -> Result<(), ConfigError> {
        if self.provider_credentials.is_some() {
            return Err(ConfigError::SecretsNotPersisted);
        }
        let json = self.to_json();
        std::fs::write(path, json).map_err(ConfigError::Io)?;
        Ok(())
    }

    /// Load a config written by [`Config::save`]. Unknown fields are ignored
    /// (forward compatibility); missing fields fall back to defaults.
    pub fn load(path: &Path) -> Result<Self, ConfigError> {
        let text = std::fs::read_to_string(path).map_err(ConfigError::Io)?;
        Self::from_json(&text)
    }

    fn to_json(&self) -> String {
        format!(
            "{{\n  \"engine\": \"{}\",\n  \"language\": \"{}\",\n  \"local_only\": {}\n}}\n",
            match self.engine {
                EngineSelection::Local => "local",
                EngineSelection::Mock => "mock",
                EngineSelection::Cloud => "cloud",
            },
            match self.language {
                Language::English => "en",
                Language::Telugu => "te",
                Language::CodeSwitch => "te-en",
            },
            self.local_only,
        )
    }

    /// Minimal JSON parser for the flat schema written by [`Config::to_json`].
    /// std-only by design; it parses exactly what we write plus graceful
    /// handling of unknown/missing fields.
    fn from_json(text: &str) -> Result<Self, ConfigError> {
        let mut cfg = Config::default();
        let body = text
            .trim()
            .strip_prefix('{')
            .and_then(|s| s.strip_suffix('}'))
            .ok_or_else(|| ConfigError::Parse("expected a JSON object".to_string()))?;

        for pair in split_top_level(body, ',') {
            let pair = pair.trim();
            if pair.is_empty() {
                continue;
            }
            let (k, v) = pair
                .split_once(':')
                .ok_or_else(|| ConfigError::Parse(format!("bad pair: {pair}")))?;
            let key = unquote(k.trim()).ok_or_else(|| ConfigError::Parse(format!("bad key: {k}")))?;
            let val = v.trim();
            match key.as_str() {
                "engine" => {
                    let v = unquote(val)
                        .ok_or_else(|| ConfigError::Parse(format!("bad engine value: {val}")))?;
                    cfg.engine = match v.as_str() {
                        "local" => EngineSelection::Local,
                        "mock" => EngineSelection::Mock,
                        "cloud" => EngineSelection::Cloud,
                        other => return Err(ConfigError::Parse(format!("unknown engine: {other}"))),
                    };
                }
                "language" => {
                    let v = unquote(val)
                        .ok_or_else(|| ConfigError::Parse(format!("bad language value: {val}")))?;
                    cfg.language = match v.as_str() {
                        "en" => Language::English,
                        "te" => Language::Telugu,
                        "te-en" => Language::CodeSwitch,
                        other => {
                            return Err(ConfigError::Parse(format!("unknown language: {other}")))
                        }
                    };
                }
                "local_only" => {
                    cfg.local_only = match val {
                        "true" => true,
                        "false" => false,
                        other => {
                            return Err(ConfigError::Parse(format!("bad local_only: {other}")))
                        }
                    };
                }
                // Unknown fields are ignored for forward compatibility.
                _ => {}
            }
        }
        cfg.validate()?;
        Ok(cfg)
    }
}

/// Split on `delim`, ignoring delimiters inside double-quoted strings.
fn split_top_level(s: &str, delim: char) -> Vec<&str> {
    let mut parts = Vec::new();
    let mut start = 0;
    let mut in_str = false;
    let mut escaped = false;
    for (i, c) in s.char_indices() {
        if escaped {
            escaped = false;
            continue;
        }
        match c {
            '\\' if in_str => escaped = true,
            '"' => in_str = !in_str,
            c if c == delim && !in_str => {
                parts.push(&s[start..i]);
                start = i + c.len_utf8();
            }
            _ => {}
        }
    }
    parts.push(&s[start..]);
    parts
}

/// Remove surrounding double quotes and unescape `\"`/`\\`. Returns `None`
/// for non-quoted input.
fn unquote(s: &str) -> Option<String> {
    let inner = s.strip_prefix('"')?.strip_suffix('"')?;
    let mut out = String::with_capacity(inner.len());
    let mut chars = inner.chars();
    while let Some(c) = chars.next() {
        if c == '\\' {
            match chars.next() {
                Some('n') => out.push('\n'),
                Some('t') => out.push('\t'),
                Some(c) => out.push(c),
                None => return None,
            }
        } else {
            out.push(c);
        }
    }
    Some(out)
}

/// Configuration failures.
#[derive(Debug)]
pub enum ConfigError {
    /// `local_only == true` but credentials were attached.
    CredentialsWithLocalOnly,
    /// `local_only == true` but the cloud engine was selected.
    CloudEngineWithLocalOnly,
    /// Cloud engine selected without credentials to call it with.
    CloudEngineNeedsCredentials,
    /// Refusal to persist secrets: the on-disk format has no credential
    /// fields, and we will not silently strip them.
    SecretsNotPersisted,
    /// Expected env var for credentials was unset or empty.
    MissingEnvCredentials(String),
    Parse(String),
    Io(std::io::Error),
}

impl fmt::Display for ConfigError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ConfigError::CredentialsWithLocalOnly => {
                write!(f, "local_only is set but provider credentials were attached")
            }
            ConfigError::CloudEngineWithLocalOnly => {
                write!(f, "local_only is set but the cloud engine was selected")
            }
            ConfigError::CloudEngineNeedsCredentials => {
                write!(f, "cloud engine selected but no provider credentials supplied")
            }
            ConfigError::SecretsNotPersisted => write!(
                f,
                "refusing to save: config carries credentials, which are never written to disk"
            ),
            ConfigError::MissingEnvCredentials(var) => {
                write!(f, "credential env var {var} is unset or empty")
            }
            ConfigError::Parse(msg) => write!(f, "config parse error: {msg}"),
            ConfigError::Io(e) => write!(f, "config I/O error: {e}"),
        }
    }
}

impl std::error::Error for ConfigError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            ConfigError::Io(e) => Some(e),
            _ => None,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_is_local_only() {
        let c = Config::default();
        assert!(c.local_only);
        assert_eq!(c.engine, EngineSelection::Local);
        assert!(c.provider_credentials.is_none());
        c.validate().unwrap();
    }

    #[test]
    fn local_only_rejects_cloud_combinations() {
        let mut c = Config::default();
        c.engine = EngineSelection::Cloud;
        assert!(matches!(
            c.validate().unwrap_err(),
            ConfigError::CloudEngineWithLocalOnly
        ));

        let mut c = Config::default();
        c.provider_credentials = Some(ProviderCredentials {
            provider: "groq".to_string(),
            api_key: "k".to_string(),
        });
        assert!(matches!(
            c.validate().unwrap_err(),
            ConfigError::CredentialsWithLocalOnly
        ));
    }

    #[test]
    fn cloud_path_needs_credentials_and_opt_out() {
        let mut c = Config::default();
        c.allow_cloud();
        c.engine = EngineSelection::Cloud;
        assert!(matches!(
            c.validate().unwrap_err(),
            ConfigError::CloudEngineNeedsCredentials
        ));
        c.provider_credentials = Some(ProviderCredentials {
            provider: "groq".to_string(),
            api_key: "k".to_string(),
        });
        c.validate().unwrap();
    }

    #[test]
    fn save_load_round_trip() {
        let dir = std::env::temp_dir();
        let path = dir.join("wahtuh_config_test.json");
        let mut c = Config::default();
        c.language = Language::Telugu;
        c.save(&path).unwrap();
        let loaded = Config::load(&path).unwrap();
        assert_eq!(loaded, c);
        std::fs::remove_file(&path).unwrap();
    }

    #[test]
    fn save_refuses_credentials() {
        let dir = std::env::temp_dir();
        let path = dir.join("wahtuh_config_secret_test.json");
        let mut c = Config::default();
        c.allow_cloud();
        c.provider_credentials = Some(ProviderCredentials {
            provider: "groq".to_string(),
            api_key: "sekrit".to_string(),
        });
        assert!(matches!(
            c.save(&path).unwrap_err(),
            ConfigError::SecretsNotPersisted
        ));
        assert!(!path.exists(), "no file must be written on refusal");
    }

    #[test]
    fn saved_file_contains_no_credential_fields() {
        let dir = std::env::temp_dir();
        let path = dir.join("wahtuh_config_nocreds_test.json");
        Config::default().save(&path).unwrap();
        let text = std::fs::read_to_string(&path).unwrap();
        for forbidden in ["api_key", "credential", "secret", "token"] {
            assert!(!text.contains(forbidden), "leaked field {forbidden}");
        }
        std::fs::remove_file(&path).unwrap();
    }

    #[test]
    fn load_tolerates_unknown_fields_and_rejects_garbage() {
        let dir = std::env::temp_dir();
        let path = dir.join("wahtuh_config_fwd_test.json");
        std::fs::write(&path, "{\"engine\": \"mock\", \"future\": 123, \"local_only\": true}").unwrap();
        let c = Config::load(&path).unwrap();
        assert_eq!(c.engine, EngineSelection::Mock);

        std::fs::write(&path, "not json").unwrap();
        assert!(Config::load(&path).is_err());
        std::fs::write(&path, "{\"engine\": \"nope\"}").unwrap();
        assert!(Config::load(&path).is_err());
        std::fs::remove_file(&path).unwrap();
    }

    #[test]
    fn save_refusal_targets_credentials_not_cloud_opt_out() {
        let dir = std::env::temp_dir();
        let path = dir.join("wahtuh_config_cloud_nocreds_test.json");
        let _ = std::fs::remove_file(&path);
        // Opting out of local-only without attaching credentials saves fine:
        // the refusal is about secrets, not about the cloud choice.
        let mut c = Config::default();
        c.allow_cloud();
        c.engine = EngineSelection::Mock;
        c.save(&path).unwrap();
        assert!(path.exists());
        std::fs::remove_file(&path).unwrap();

        // And the refusal holds even when local_only is already false:
        // credentials are never persisted, period.
        let mut c = Config::default();
        c.allow_cloud();
        c.provider_credentials = Some(ProviderCredentials {
            provider: "groq".to_string(),
            api_key: "sekrit".to_string(),
        });
        assert!(matches!(
            c.save(&path).unwrap_err(),
            ConfigError::SecretsNotPersisted
        ));
        assert!(!path.exists(), "no file must be written on refusal");
        let _ = std::fs::remove_file(&path);
    }

    #[test]
    fn credentials_from_env() {
        std::env::set_var("WAHTUH_GROQ_API_KEY", "test-key");
        let creds = Config::credentials_from_env("groq").unwrap();
        assert_eq!(creds.provider, "groq");
        assert_eq!(creds.api_key, "test-key");
        std::env::remove_var("WAHTUH_GROQ_API_KEY");
        assert!(matches!(
            Config::credentials_from_env("groq").unwrap_err(),
            ConfigError::MissingEnvCredentials(_)
        ));
    }
}
