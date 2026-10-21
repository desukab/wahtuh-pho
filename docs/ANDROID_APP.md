# Android app guide

## Screens

- **Home** — serif headline, primary dictation card, setup card, word/session stats, recent transcripts, local-only toggle.
- **Dictionary** — `from=to` entries, one per line, with a switch to disable all replacements.
- **Settings** — provider/language/engine, AI cleanup, punctuation/fillers, cloud key, recording retention, history/diagnostics, setup buttons, model preparation.
- **History** — entries grouped by Today/Yesterday/date, tap to copy, long-press raw.
- **Bubble** — cream circle with waveform; expands to a timer pill while recording; periwinkle while transcribing.
- **Keyboard** — QWERTY/numbers with Dictate and Setup.

## Text policy

1. Normalize whitespace.
2. Expand spoken punctuation (English/Telugu).
3. Remove fillers if enabled.
4. Apply the dictionary, recording every replacement.
5. Tidy spacing, sentence-case Latin starts, add terminal punctuation.
6. Store raw text, final text, language, engine, provider, latency, and edits.

No translation between languages and no invented words. Every change is reversible via the raw transcript.

## Insertion behavior

`InsertAccessibilityService` verifies the focused node is editable and not a password field, then tries `ACTION_SET_TEXT`; if that fails it copies to the clipboard and pastes. Clipboard fallback overwrites the clipboard. Wahtuh refuses to insert into its own package.

## Diagnostics

```bash
adb shell am start -n com.wahtuh.pho/.diag.DiagnosticsActivity --ez tests true
adb shell am start -n com.wahtuh.pho/.diag.DiagnosticsActivity --ez bench_en true
adb shell am start -n com.wahtuh.pho/.diag.DiagnosticsActivity --ez bench_te true
adb shell am start -n com.wahtuh.pho/.diag.DiagnosticsActivity --ez bench_auto true
adb shell am start -n com.wahtuh.pho/.diag.DiagnosticsActivity --ei mic 3
adb logcat -s WahtuhDiag:*
```

`DiagnosticsActivity` is exported so ADB can launch tests; it performs only local work.
