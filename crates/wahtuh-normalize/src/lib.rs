//! Text normalization for Wahtuh Pho.
//!
//! Raw engine output gets cleaned up here before it reaches the text field.
//! The pipeline, in order:
//!
//! 1. [`nfc_normalize`] — canonical composition for the Latin combining range
//!    (a documented NFC subset; see the function docs for the boundary).
//! 2. Punctuation/spacing fixes and sentence capitalization for Latin-script
//!    runs. Telugu-script runs (U+0C00–U+0C7F) and mixed-script ("Tenglish")
//!    tokens are **preserved, never stripped or transliterated**.
//! 3. Optional [`Dictionary`] whole-word replacements (user vocabulary).
//!
//! [`NormalizedText`] always keeps the pre-normalization string, so the
//! transform is reversible and the UI can offer "show raw transcript".

use std::collections::HashMap;

/// Version of the normalization pipeline. Bumped whenever a rule changes in a
/// way that alters output for the same input; stored on [`NormalizedText`] so
/// history entries can be interpreted later.
pub const NORMALIZER_VERSION: u32 = 1;

/// Current [`Dictionary`] schema version.
pub const DICTIONARY_VERSION: u32 = 1;

/// True for characters in the Telugu Unicode block (U+0C00–U+0C7F).
pub fn is_telugu(c: char) -> bool {
    ('\u{0C00}'..='\u{0C7F}').contains(&c)
}

/// True for any character in a non-Latin script block we treat as
/// "leave alone" (Telugu today; extend as languages are added).
pub fn is_non_latin_script(c: char) -> bool {
    is_telugu(c)
}

/// Canonical composition for Latin base + combining mark sequences.
///
/// This is an NFC **subset**, not full Unicode NFC: it composes a base Latin
/// letter (A–Z, a–z) followed by a combining mark in U+0300–U+036F into its
/// precomposed form via [`COMPOSITION_TABLE`]. Everything else — including
/// all Telugu conjuncts, which are already in NFC in practice — passes
/// through untouched.
///
/// Rationale for std-only: full NFC needs the Unicode composition tables
/// (~tens of KB). When the workspace vendors `unicode-normalization`, replace
/// this function with it and bump [`NORMALIZER_VERSION`]. The behavior
/// contract for the covered range matches NFC.
pub fn nfc_normalize(s: &str) -> String {
    let chars: Vec<char> = s.chars().collect();
    let mut out = String::with_capacity(s.len());
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        if i + 1 < chars.len() {
            let next = chars[i + 1];
            if ('\u{0300}'..='\u{036F}').contains(&next) {
                if let Some(composed) = compose_pair(c, next) {
                    out.push(composed);
                    i += 2;
                    continue;
                }
            }
        }
        out.push(c);
        i += 1;
    }
    out
}

fn compose_pair(base: char, mark: char) -> Option<char> {
    COMPOSITION_TABLE
        .iter()
        .find(|&&(b, m, _)| b == base && m == mark)
        .map(|&(_, _, composed)| composed)
}

/// (base, combining mark, precomposed) — canonical compositions for the Latin
/// letters dictation actually produces. Covers grave/acute/circumflex/tilde/
/// diaeresis/ring/cedilla/ogonek where a precomposed form exists.
const COMPOSITION_TABLE: [(char, char, char); 52] = [
    ('a', '\u{0300}', 'à'), ('a', '\u{0301}', 'á'), ('a', '\u{0302}', 'â'),
    ('a', '\u{0303}', 'ã'), ('a', '\u{0308}', 'ä'), ('a', '\u{030A}', 'å'),
    ('A', '\u{0300}', 'À'), ('A', '\u{0301}', 'Á'), ('A', '\u{0302}', 'Â'),
    ('A', '\u{0303}', 'Ã'), ('A', '\u{0308}', 'Ä'), ('A', '\u{030A}', 'Å'),
    ('c', '\u{0327}', 'ç'), ('C', '\u{0327}', 'Ç'),
    ('e', '\u{0300}', 'è'), ('e', '\u{0301}', 'é'), ('e', '\u{0302}', 'ê'),
    ('e', '\u{0308}', 'ë'),
    ('E', '\u{0300}', 'È'), ('E', '\u{0301}', 'É'), ('E', '\u{0302}', 'Ê'),
    ('E', '\u{0308}', 'Ë'),
    ('i', '\u{0300}', 'ì'), ('i', '\u{0301}', 'í'), ('i', '\u{0302}', 'î'),
    ('i', '\u{0308}', 'ï'),
    ('I', '\u{0300}', 'Ì'), ('I', '\u{0301}', 'Í'), ('I', '\u{0302}', 'Î'),
    ('I', '\u{0308}', 'Ï'),
    ('n', '\u{0303}', 'ñ'), ('N', '\u{0303}', 'Ñ'),
    ('o', '\u{0300}', 'ò'), ('o', '\u{0301}', 'ó'), ('o', '\u{0302}', 'ô'),
    ('o', '\u{0303}', 'õ'), ('o', '\u{0308}', 'ö'),
    ('O', '\u{0300}', 'Ò'), ('O', '\u{0301}', 'Ó'), ('O', '\u{0302}', 'Ô'),
    ('O', '\u{0303}', 'Õ'), ('O', '\u{0308}', 'Ö'),
    ('u', '\u{0300}', 'ù'), ('u', '\u{0301}', 'ú'), ('u', '\u{0302}', 'û'),
    ('u', '\u{0308}', 'ü'),
    ('U', '\u{0300}', 'Ù'), ('U', '\u{0301}', 'Ú'), ('U', '\u{0302}', 'Û'),
    ('U', '\u{0308}', 'Ü'),
    ('y', '\u{0301}', 'ý'), ('Y', '\u{0301}', 'Ý'),
];

/// Knobs for [`normalize`]. All default to on except dictionary application,
/// which needs a dictionary to be supplied anyway.
#[derive(Debug, Clone, Copy)]
pub struct NormalizeOptions {
    pub fix_punctuation_spacing: bool,
    pub capitalize_sentences: bool,
    pub collapse_whitespace: bool,
}

impl Default for NormalizeOptions {
    fn default() -> Self {
        Self {
            fix_punctuation_spacing: true,
            capitalize_sentences: true,
            collapse_whitespace: true,
        }
    }
}

/// Output of [`normalize`]. `original` is the raw engine text, kept so the
/// transform is reversible (UI can show "raw" on demand, history stores both).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct NormalizedText {
    pub text: String,
    pub original: String,
    /// [`NORMALIZER_VERSION`] at the time of normalization.
    pub version: u32,
}

/// Normalize raw engine output.
///
/// Order: NFC-lite → whitespace collapse → punctuation spacing →
/// capitalization → dictionary. Non-Latin-script tokens are never altered by
/// the punctuation/capitalization rules (those only touch ASCII punctuation
/// and ASCII case), so Telugu and code-switched tokens survive verbatim.
pub fn normalize(
    raw: &str,
    opts: &NormalizeOptions,
    dict: Option<&Dictionary>,
) -> NormalizedText {
    let original = raw.to_string();
    let mut text = nfc_normalize(raw);

    if opts.collapse_whitespace {
        text = collapse_whitespace(&text);
    }
    if opts.fix_punctuation_spacing {
        text = fix_punctuation_spacing(&text);
    }
    if opts.capitalize_sentences {
        text = capitalize_sentences(&text);
    }
    if let Some(d) = dict {
        text = d.apply(&text);
    }

    NormalizedText {
        text,
        original,
        version: NORMALIZER_VERSION,
    }
}

fn collapse_whitespace(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut prev_space = false;
    for c in s.chars() {
        if c.is_whitespace() {
            if !prev_space {
                out.push(' ');
            }
            prev_space = true;
        } else {
            out.push(c);
            prev_space = false;
        }
    }
    out.trim().to_string()
}

/// - Remove spaces before `, . ! ? ; :` and closing brackets.
/// - Ensure one space after `, ; : ! ?` when followed by a word character.
/// - Never touch `.` inside a number (`3.14` stays `3.14`) or `:` in times
///   (`12:30` stays `12:30`).
fn fix_punctuation_spacing(s: &str) -> String {
    let chars: Vec<char> = s.chars().collect();
    let mut out = String::with_capacity(s.len());
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        let prev_is_digit = out.chars().last().is_some_and(|p| p.is_ascii_digit());
        let next = chars.get(i + 1).copied();

        // Drop a space that precedes closing punctuation.
        if c == ' ' && matches!(next, Some(',' | '.' | '!' | '?' | ';' | ':' | ')' | ']' | '}')) {
            // But keep the space if the punctuation is part of a number/time
            // like "3 . 14" — rare from an engine, but be conservative: only
            // drop when the char before the space is not a digit adjacent to
            // a digit after the punctuation.
            let after_next = chars.get(i + 2).copied();
            let prev = out.chars().last();
            let numeric_context = matches!(prev, Some(p) if p.is_ascii_digit())
                && matches!(after_next, Some(n) if n.is_ascii_digit());
            if !numeric_context {
                i += 1;
                continue;
            }
        }

        out.push(c);

        // Add a space after , ; : ! ? . when glued to a following word char.
        // Exceptions: '.' between digits (decimals like 3.14), ':' between
        // digits (times like 12:30), ',' between digits (thousands like 1,000).
        if matches!(c, ',' | ';' | ':' | '!' | '?' | '.') {
            if let Some(n) = next {
                let needs_space = n.is_alphanumeric() && !n.is_whitespace();
                let numeric_glue = prev_is_digit
                    && n.is_ascii_digit()
                    && matches!(c, ',' | ':' | '.');
                if needs_space && !numeric_glue {
                    out.push(' ');
                }
            }
        }
        i += 1;
    }
    out
}

/// Uppercase the first ASCII letter of the string and the first ASCII letter
/// after each `.`, `!`, `?` sentence boundary. Non-ASCII (Telugu has no case)
/// is untouched.
fn capitalize_sentences(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut at_boundary = true;
    for c in s.chars() {
        if matches!(c, '.' | '!' | '?') {
            at_boundary = true;
            out.push(c);
        } else if c.is_whitespace() {
            out.push(c);
        } else if at_boundary && c.is_ascii_alphabetic() {
            out.push(c.to_ascii_uppercase());
            at_boundary = false;
        } else {
            // Any other letter (incl. non-Latin) ends the boundary run so we
            // don't capitalize mid-word later.
            if c.is_alphabetic() {
                at_boundary = false;
            }
            out.push(c);
        }
    }
    out
}

/// User vocabulary: spoken form → written form (e.g. "my company" →
/// "Wahtuh Labs"). Versioned so a future schema change can migrate.
///
/// # Versioning & migration
///
/// `version` is [`DICTIONARY_VERSION`]. [`Dictionary::load`] rejects unknown
/// versions with [`DictionaryError::UnsupportedVersion`] rather than
/// misreading them. When the schema changes: bump `DICTIONARY_VERSION`, add a
/// `migrate_vN_to_vN_plus_1` function, and chain them in
/// [`Dictionary::migrate`]. Migration is explicit and total — there is no
/// silent "best effort" path.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Dictionary {
    pub version: u32,
    entries: HashMap<String, String>,
}

impl Dictionary {
    pub fn new() -> Self {
        Self {
            version: DICTIONARY_VERSION,
            entries: HashMap::new(),
        }
    }

    /// Insert a mapping. Keys are matched case-insensitively for ASCII.
    pub fn insert(&mut self, spoken: impl Into<String>, written: impl Into<String>) {
        self.entries
            .insert(spoken.into().to_lowercase(), written.into());
    }

    pub fn remove(&mut self, spoken: &str) -> Option<String> {
        self.entries.remove(&spoken.to_lowercase())
    }

    pub fn len(&self) -> usize {
        self.entries.len()
    }

    pub fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }

    /// Apply whole-word replacements. Matching is on whitespace-delimited
    /// tokens, ASCII case-insensitive; Telugu tokens match exactly (no case
    /// folding exists for the script, and none is needed).
    pub fn apply(&self, text: &str) -> String {
        if self.entries.is_empty() {
            return text.to_string();
        }
        text.split_whitespace()
            .map(|tok| {
                // Split leading/trailing ASCII punctuation so "word," still matches.
                let (lead, core, trail) = split_affixes(tok);
                let key = core.to_lowercase();
                match self.entries.get(&key) {
                    Some(w) => format!("{lead}{w}{trail}"),
                    None => tok.to_string(),
                }
            })
            .collect::<Vec<_>>()
            .join(" ")
    }

    /// Validate a stored version before use. v1 is current; anything else is
    /// an error until a migration is written.
    pub fn migrate(from_version: u32) -> Result<(), DictionaryError> {
        if from_version == DICTIONARY_VERSION {
            Ok(())
        } else {
            Err(DictionaryError::UnsupportedVersion(from_version))
        }
    }
}

impl Default for Dictionary {
    fn default() -> Self {
        Self::new()
    }
}

fn split_affixes(tok: &str) -> (&str, &str, &str) {
    let bytes = tok.as_bytes();
    let mut start = 0;
    let mut end = bytes.len();
    while start < end && (bytes[start] as char).is_ascii_punctuation() {
        start += 1;
    }
    while end > start && (bytes[end - 1] as char).is_ascii_punctuation() {
        end -= 1;
    }
    (&tok[..start], &tok[start..end], &tok[end..])
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum DictionaryError {
    UnsupportedVersion(u32),
}

impl std::fmt::Display for DictionaryError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            DictionaryError::UnsupportedVersion(v) => {
                write!(f, "unsupported dictionary version {v} (current is {DICTIONARY_VERSION}); no migration exists")
            }
        }
    }
}

impl std::error::Error for DictionaryError {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn nfc_composes_latin_diacritics() {
        // e + COMBINING ACUTE ACCENT -> é (U+00E9), exactly what NFC does.
        assert_eq!(nfc_normalize("cafe\u{0301}"), "café");
        assert_eq!(nfc_normalize("nai\u{0308}ve"), "naïve");
        assert_eq!(nfc_normalize("plain"), "plain");
    }

    #[test]
    fn nfc_leaves_telugu_and_unknown_sequences_alone() {
        let telugu = "నమస్కారం"; // contains virama conjuncts; must survive byte-identical
        assert_eq!(nfc_normalize(telugu), telugu);
        // Combining mark with no table entry passes through unchanged.
        assert_eq!(nfc_normalize("a\u{0315}"), "a\u{0315}");
    }

    #[test]
    fn punctuation_spacing_english() {
        let opts = NormalizeOptions::default();
        let n = normalize("hello ,world .this is  a test", &opts, None);
        assert_eq!(n.text, "Hello, world. This is a test");
        // Raw text preserved for reversibility.
        assert_eq!(n.original, "hello ,world .this is  a test");
        assert_eq!(n.version, NORMALIZER_VERSION);
    }

    #[test]
    fn numbers_and_times_not_broken() {
        let opts = NormalizeOptions::default();
        let n = normalize("it costs 3.14 at 12:30 , ok", &opts, None);
        assert!(n.text.contains("3.14"), "decimal broken: {}", n.text);
        assert!(n.text.contains("12:30"), "time broken: {}", n.text);
        // Thousands separators are left glued too.
        let n2 = normalize("population 1,000,000", &opts, None);
        assert!(n2.text.contains("1,000,000"), "thousands broken: {}", n2.text);
    }

    #[test]
    fn telugu_preserved_verbatim() {
        let opts = NormalizeOptions::default();
        let input = "నమస్కారం , ఇది ఒక పరీక్ష .";
        let n = normalize(input, &opts, None);
        // Telugu words must appear with identical spelling; punctuation rules
        // may adjust ASCII spacing around them but never the script itself.
        for word in ["నమస్కారం", "ఇది", "ఒక", "పరీక్ష"] {
            assert!(n.text.contains(word), "lost Telugu word {word}: {}", n.text);
        }
    }

    #[test]
    fn code_switch_mixed_tokens_preserved() {
        let opts = NormalizeOptions::default();
        let n = normalize("hello నమస్కారం world", &opts, None);
        assert!(n.text.contains("నమస్కారం"));
        // Sentence capitalization touches the Latin token's case, but the
        // token itself (and the Telugu token) survives verbatim otherwise.
        assert!(n.text.to_lowercase().contains("hello"));
        assert!(n.text.contains("world"));
    }

    #[test]
    fn dictionary_replaces_whole_words() {
        let mut d = Dictionary::new();
        d.insert("wahtu", "Wahtuh");
        d.insert("flow", "Flow");
        // Documented limit: keys are matched per whitespace-delimited token,
        // so multi-word spoken forms are not applied (no phrase matching yet).
        d.insert("my company", "Wahtuh Labs");
        let opts = NormalizeOptions {
            capitalize_sentences: false,
            ..Default::default()
        };
        let n = normalize("i love wahtu, and flow!", &opts, Some(&d));
        assert_eq!(n.text, "i love Wahtuh, and Flow!");
        assert_eq!(d.len(), 3);
    }

    #[test]
    fn dictionary_version_gate() {
        assert!(Dictionary::migrate(DICTIONARY_VERSION).is_ok());
        assert_eq!(
            Dictionary::migrate(99),
            Err(DictionaryError::UnsupportedVersion(99))
        );
    }

    #[test]
    fn dictionary_migration_gate_rejects_everything_but_current() {
        assert_eq!(Dictionary::new().version, DICTIONARY_VERSION);
        // Older, newer, and absurd versions are all rejected until an
        // explicit migration function is written for them. Unknown versions
        // are never guessed at.
        for v in [0u32, DICTIONARY_VERSION + 1, 7, u32::MAX] {
            if v == DICTIONARY_VERSION {
                continue; // future-proofing if the constant ever moves
            }
            assert_eq!(
                Dictionary::migrate(v),
                Err(DictionaryError::UnsupportedVersion(v)),
                "version {v} must be gated"
            );
        }
        assert!(Dictionary::migrate(DICTIONARY_VERSION).is_ok());
        // The error names the missing migration and the current version.
        let msg = Dictionary::migrate(DICTIONARY_VERSION + 1)
            .unwrap_err()
            .to_string();
        assert!(msg.contains("no migration"), "unhelpful message: {msg}");
        assert!(msg.contains(&DICTIONARY_VERSION.to_string()));
    }

    #[test]
    fn options_can_disable_rules() {
        let opts = NormalizeOptions {
            fix_punctuation_spacing: false,
            capitalize_sentences: false,
            collapse_whitespace: false,
        };
        let n = normalize("hello ,  world", &opts, None);
        assert_eq!(n.text, "hello ,  world");
    }

    #[test]
    fn empty_input_stays_empty() {
        let n = normalize("", &NormalizeOptions::default(), None);
        assert_eq!(n.text, "");
        assert_eq!(n.original, "");
    }
}
